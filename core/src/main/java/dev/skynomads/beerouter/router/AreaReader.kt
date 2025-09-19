package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.codec.DataBuffers
import dev.skynomads.beerouter.expressions.BExpressionContextWay
import dev.skynomads.beerouter.mapaccess.MatchedWaypoint
import dev.skynomads.beerouter.mapaccess.MatchedWaypoint.Companion.readFromStream
import dev.skynomads.beerouter.mapaccess.NodesCache
import dev.skynomads.beerouter.mapaccess.OsmFile
import dev.skynomads.beerouter.mapaccess.OsmLink
import dev.skynomads.beerouter.mapaccess.OsmNode
import dev.skynomads.beerouter.mapaccess.OsmNodesMap
import dev.skynomads.beerouter.mapaccess.PhysicalFile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.roundToInt

class AreaReader {
    private val logger: Logger = LoggerFactory.getLogger(AreaReader::class.java)

    var segmentFolder: File? = null

    fun getDirectAllData(
        folder: File,
        rc: RoutingContext,
        wp: OsmNodeNamed,
        maxscale: Int,
        expctxWay: BExpressionContextWay,
        searchRect: OsmNogoPolygon,
        ais: MutableList<AreaInfo>
    ) {
        this.segmentFolder = folder

        val div = 32
        val cellsize = 1000000 / div
        val scale = maxscale
        var count = 0
        var used = 0
        val checkBorder = maxscale > 7

        val tiles: MutableMap<Long?, String?> = TreeMap<Long?, String?>()
        for (idxLat in -scale..scale) {
            for (idxLon in -scale..scale) {
                if (ignoreCenter(maxscale, idxLon, idxLat)) continue
                val tmplon: Int = wp.iLon + cellsize * idxLon
                val tmplat = wp.iLat + cellsize * idxLat
                val lonDegree = tmplon / 1000000
                val latDegree = tmplat / 1000000
                val lonMod5 = lonDegree % 5
                val latMod5 = latDegree % 5

                var lon = lonDegree - 180 - lonMod5
                val slon = if (lon < 0) "W" + (-lon) else "E$lon"
                var lat = latDegree - 90 - latMod5
                val slat = if (lat < 0) "S" + (-lat) else "N$lat"
                val filenameBase = slon + "_" + slat

                val lonIdx = tmplon / cellsize
                val latIdx = tmplat / cellsize
                val subIdx = (latIdx - div * latDegree) * div + (lonIdx - div * lonDegree)

                val subLonIdx = (lonIdx - div * lonDegree)
                val subLatIdx = (latIdx - div * latDegree)

                val dataRect = OsmNogoPolygon(true)
                lon = lonDegree * 1000000
                lat = latDegree * 1000000
                var tmplon2 = lon + cellsize * (subLonIdx)
                var tmplat2 = lat + cellsize * (subLatIdx)
                dataRect.addVertex(tmplon2, tmplat2)

                tmplon2 = lon + cellsize * (subLonIdx + 1)
                tmplat2 = lat + cellsize * (subLatIdx)
                dataRect.addVertex(tmplon2, tmplat2)

                tmplon2 = lon + cellsize * (subLonIdx + 1)
                tmplat2 = lat + cellsize * (subLatIdx + 1)
                dataRect.addVertex(tmplon2, tmplat2)

                tmplon2 = lon + cellsize * (subLonIdx)
                tmplat2 = lat + cellsize * (subLatIdx + 1)
                dataRect.addVertex(tmplon2, tmplat2)

                var intersects = checkBorder && dataRect.intersects(
                    searchRect.points[0].x,
                    searchRect.points[0].y,
                    searchRect.points[2].x,
                    searchRect.points[2].y
                )
                if (!intersects && checkBorder) intersects = dataRect.intersects(
                    searchRect.points[1].x,
                    searchRect.points[1].y,
                    searchRect.points[2].x,
                    searchRect.points[3].y
                )
                if (intersects) {
                    continue
                }

                intersects = searchRect.intersects(
                    dataRect.points[0].x,
                    dataRect.points[0].y,
                    dataRect.points[2].x,
                    dataRect.points[2].y
                )
                if (!intersects) intersects = searchRect.intersects(
                    dataRect.points[1].x,
                    dataRect.points[1].y,
                    dataRect.points[3].x,
                    dataRect.points[3].y
                )
                if (!intersects) intersects = containsRect(
                    searchRect,
                    dataRect.points[0].x,
                    dataRect.points[0].y,
                    dataRect.points[2].x,
                    dataRect.points[2].y
                )

                if (!intersects) {
                    continue
                }

                tiles.put((tmplon.toLong()) shl 32 or tmplat.toLong(), filenameBase)
                count++
            }
        }

        val list: MutableList<MutableMap.MutableEntry<Long?, String?>> =
            ArrayList(tiles.entries)
        Collections.sort(
            list,
            Comparator<MutableMap.MutableEntry<Long?, String?>> { e1, e2 -> e1.value!!.compareTo(e2.value!!) })

        val maxmem = rc.memoryclass * 1024L * 1024L // in MB
        val nodesCache =
            NodesCache(segmentFolder!!, expctxWay, rc.forceSecondaryData, maxmem, null, false)
        var pf: PhysicalFile? = null
        var lastFilenameBase = ""
        var dataBuffers: DataBuffers? = null
        try {
            for (entry in list) {
                val n = OsmNode(entry.key!!)
                // System.out.println("areareader set " + n.getILon() + "_" + n.getILat() + " " + entry.getValue());
                val filenameBase: String = entry.value!!
                if (filenameBase != lastFilenameBase) {
                    pf?.close()
                    lastFilenameBase = filenameBase
                    val file = File(segmentFolder, "$filenameBase.rd5")
                    dataBuffers = DataBuffers()

                    pf = PhysicalFile(file, dataBuffers, -1, -1)
                }
                if (getDirectData(pf!!, dataBuffers!!, n.iLon, n.iLat, rc, expctxWay, ais)) used++
            }
        } catch (e: Exception) {
            logger.error("error after used={} / count={}", used, count, e)
            ais.clear()
        } finally {
            if (pf != null) try {
                pf.close()
            } catch (ee: Exception) {
            }
            nodesCache.close()
        }
    }

    fun getDirectData(
        pf: PhysicalFile,
        dataBuffers: DataBuffers,
        inlon: Int,
        inlat: Int,
        rc: RoutingContext?,
        expctxWay: BExpressionContextWay,
        ais: MutableList<AreaInfo>
    ): Boolean {
        val lonDegree = inlon / 1000000
        val latDegree = inlat / 1000000

        val nodesMap = OsmNodesMap()

        try {
            val div = pf.divisor

            val osmf = OsmFile(pf, lonDegree, latDegree, dataBuffers)
            if (osmf.hasData()) {
                val cellsize = 1000000 / div
                val tmplon = inlon
                val tmplat = inlat
                val lonIdx = tmplon / cellsize
                val latIdx = tmplat / cellsize

                val segment =
                    osmf.createMicroCache(lonIdx, latIdx, dataBuffers, expctxWay, null, true, null)

                if (segment != null) {
                    val size = segment.size
                    for (i in 0..<size) {
                        val id = segment.getIdForIndex(i)
                        val node = OsmNode(id)
                        if (segment.getAndClear(id)) {
                            node.parseNodeBody(segment, nodesMap, expctxWay)
                            if (node.firstlink is OsmLink) {
                                var link = node.firstlink
                                while (link != null) {
                                    val nextNode = link.getTarget(node)
                                    if (nextNode!!.firstlink == null) {
                                        link = link.getNext(node)
                                        continue  // don't care about dead ends
                                    }
                                    if (nextNode.firstlink!!.descriptionBitmap == null) {
                                        link = link.getNext(node)
                                        continue
                                    }

                                    for (ai in ais) {
                                        if (ai.polygon!!.isWithin(
                                                node.iLon.toLong(),
                                                node.iLat.toLong()
                                            )
                                        ) {
                                            ai.checkAreaInfo(
                                                expctxWay,
                                                node.elev,
                                                nextNode.firstlink!!.descriptionBitmap!!
                                            )
                                            break
                                        }
                                    }
                                    break
                                    link = link.getNext(node)
                                }
                            }
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            System.err.println("AreaReader: " + e.message)
        }
        return false
    }

    fun ignoreCenter(maxscale: Int, idxLon: Int, idxLat: Int): Boolean {
        val centerScale = (maxscale * .2).roundToInt() - 1
        if (centerScale < 0) return false
        return idxLon >= -centerScale && idxLon <= centerScale && idxLat >= -centerScale && idxLat <= centerScale
    }

    /*
    in this case the polygon is 'only' a rectangle
  */
    fun containsRect(searchRect: OsmNogoPolygon, p1x: Int, p1y: Int, p2x: Int, p2y: Int): Boolean {
        return searchRect.isWithin(p1x.toLong(), p1y.toLong()) &&
                searchRect.isWithin(p2x.toLong(), p2y.toLong())
    }

    @Throws(Exception::class)
    fun writeAreaInfo(filename: String, wp: MatchedWaypoint, ais: MutableList<AreaInfo>) {
        val dos = DataOutputStream(BufferedOutputStream(FileOutputStream(filename)))

        wp.writeToStream(dos)
        for (ai in ais) {
            dos.writeInt(ai.direction)
            dos.writeDouble(ai.elevStart)
            dos.writeInt(ai.ways)
            dos.writeInt(ai.greenWays)
            dos.writeInt(ai.riverWays)
            dos.writeInt(ai.elev50)
        }
        dos.close()
    }

    fun readAreaInfo(fai: File, wp: MatchedWaypoint, ais: MutableList<AreaInfo>) {
        var dis: DataInputStream? = null
        var ep: MatchedWaypoint?
        try {
            dis = DataInputStream(BufferedInputStream(FileInputStream(fai)))
            ep = readFromStream(dis)
            if (abs(ep.waypoint!!.iLon - wp.waypoint!!.iLon) > 500 &&
                abs(ep.waypoint!!.iLat - wp.waypoint!!.iLat) > 500
            ) {
                return
            }
            if (abs(ep.radius - wp.radius) > 500) {
                return
            }
            for (i in 0..3) {
                val direction = dis.readInt()
                val ai = AreaInfo(direction)
                ai.elevStart = dis.readDouble()
                ai.ways = dis.readInt()
                ai.greenWays = dis.readInt()
                ai.riverWays = dis.readInt()
                ai.elev50 = dis.readInt()
                ais.add(ai)
            }
        } catch (e: IOException) {
            ais.clear()
        } finally {
            if (dis != null) {
                try {
                    dis.close()
                } catch (e: IOException) {
                }
            }
        }
    }
}
