package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.MatchedWaypoint
import dev.skynomads.beerouter.mapaccess.NodesCache
import dev.skynomads.beerouter.mapaccess.OsmLink
import dev.skynomads.beerouter.mapaccess.OsmNode
import dev.skynomads.beerouter.mapaccess.OsmNodePairSet
import dev.skynomads.beerouter.mapaccess.OsmPos
import dev.skynomads.beerouter.router.OsmTrack.OsmPathElementHolder
import dev.skynomads.beerouter.util.CheapAngleMeter.Companion.getDifferenceFromDirection
import dev.skynomads.beerouter.util.CheapAngleMeter.Companion.getDirection
import dev.skynomads.beerouter.util.CheapRuler.destination
import dev.skynomads.beerouter.util.CompactLongMap
import dev.skynomads.beerouter.util.SortedHeap
import dev.skynomads.beerouter.util.StackSampler
import kotlinx.coroutines.ensureActive
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Collections
import java.util.SortedSet
import java.util.TreeSet
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class RoutingEngine(val routingContext: RoutingContext) : Thread() {
    private var logger: Logger = LoggerFactory.getLogger(RoutingEngine::class.java)

    private var nodesCache: NodesCache? = null
    private val openSet = SortedHeap<OsmPath?>()

    var extraWaypoints: MutableList<OsmNodeNamed> = mutableListOf()
    protected var matchedWaypoints: MutableList<MatchedWaypoint> = mutableListOf()
    var linksProcessed: Int = 0
        private set

    private var nodeLimit = 0 // used for target island search
    private val MAXNODES_ISLAND_CHECK = 500
    private val islandNodePairs = OsmNodePairSet(MAXNODES_ISLAND_CHECK)
    private val useNodePoints = false // use the start/end nodes  instead of crosspoint

    private val MAX_STEPS_CHECK = 500

    private val ROUNDTRIP_DEFAULT_DIRECTIONADD = 45

    private val MAX_DYNAMIC_RANGE = 60000

    private var stackSampler: StackSampler? = null
    var airDistanceCostFactor: Double = 0.0
    var lastAirDistanceCostFactor: Double = 0.0

    private var guideTrack: OsmTrack? = null

    private var matchPath: OsmPathElement? = null

    var boundary: SearchBoundary? = null

    private var extract: Array<Any?>? = null

    private val directWeaving = true //!Boolean.getBoolean("disableDirectWeaving")

    suspend fun doRouting(waypoints: List<OsmNodeNamed>): OsmTrack? {
        val waypoints = waypoints.toMutableList()
        try {
            if (routingContext.allowSamewayback) {
                if (waypoints.size == 2) {
                    val onn =
                        OsmNodeNamed(OsmNode(waypoints[0].iLon, waypoints[0].iLat))
                    onn.name = "to"
                    waypoints.add(onn)
                } else {
                    waypoints[waypoints.size - 1].name =
                        "via" + (waypoints.size - 1) + "_center"
                    val newpoints: MutableList<OsmNodeNamed> = ArrayList()
                    for (i in waypoints.size - 2 downTo 0) {
                        // System.out.println("back " + waypoints.get(i));
                        val onn =
                            OsmNodeNamed(OsmNode(waypoints[i].iLon, waypoints[i].iLat))
                        onn.name = "via"
                        newpoints.add(onn)
                    }
                    newpoints[newpoints.size - 1].name = "to"
                    waypoints.addAll(newpoints)
                }
            }

            val nsections = waypoints.size - 1
            val refTracks = arrayOfNulls<OsmTrack>(nsections) // used ways for alternatives
            val lastTracks = arrayOfNulls<OsmTrack>(nsections)
            var track: OsmTrack?
            var i = 0
            while (true) {
                track = findTrack(waypoints, refTracks, lastTracks)!!

                // we are only looking for info
                if (routingContext.ai != null) return null

                track.name = "brouter_" + routingContext.profile.name + "_" + i

                if (i == min(3, max(0, routingContext.alternativeIdx))) {
                    logger.debug("gpx={}", FormatGpx(routingContext).format(track))
                } else {
                    i++
                    continue
                }
                break
            }
            return track
        } finally {
            logger.info("expression cache stats={}", routingContext.way.cacheStats())

            if (nodesCache != null) {
                logger.info("NodesCache status before close={}", nodesCache!!.formatStatus())
                nodesCache!!.close()
                nodesCache = null
            }
            openSet.clear()

            if (stackSampler != null) {
                try {
                    stackSampler!!.close()
                } catch (_: Exception) {
                }
                stackSampler = null
            }
        }
    }

    suspend fun doGetInfo(waypoints: List<OsmNodeNamed>): OsmTrack? {
        routingContext.freeNoWays()

        val wpt1 = MatchedWaypoint()
        wpt1.waypoint = waypoints[0]
        wpt1.name = "wpt_info"
        val listOne: MutableList<MatchedWaypoint> = ArrayList()
        listOne.add(wpt1)
        matchWaypointsToNodes(listOne)

        resetCache(true)
        nodesCache!!.nodesMap.cleanupMode = 0

        val start1 = nodesCache!!.getGraphNode(listOne[0].node1!!)
        nodesCache!!.obtainNonHollowNode(start1)

        guideTrack = OsmTrack()
        guideTrack!!.addNode(
            OsmPathElement.Companion.create(
                wpt1.node2!!.iLon,
                wpt1.node2!!.iLat,
                0.toShort(),
                null
            )
        )
        guideTrack!!.addNode(
            OsmPathElement.Companion.create(
                wpt1.node1!!.iLon,
                wpt1.node1!!.iLat,
                0.toShort(),
                null
            )
        )

        matchedWaypoints = ArrayList()
        val wp1 = MatchedWaypoint()
        wp1.crosspoint = OsmNode(wpt1.node1!!.iLon, wpt1.node1!!.iLat)
        wp1.node1 = OsmNode(wpt1.node1!!.iLon, wpt1.node1!!.iLat)
        wp1.node2 = OsmNode(wpt1.node2!!.iLon, wpt1.node2!!.iLat)
        matchedWaypoints.add(wp1)
        val wp2 = MatchedWaypoint()
        wp2.crosspoint = OsmNode(wpt1.node2!!.iLon, wpt1.node2!!.iLat)
        wp2.node1 = OsmNode(wpt1.node1!!.iLon, wpt1.node1!!.iLat)
        wp2.node2 = OsmNode(wpt1.node2!!.iLon, wpt1.node2!!.iLat)
        matchedWaypoints.add(wp2)

        val t = findTrack(wp1, wp2, null, null, false)
        if (t == null) {
            return null
        }

        t.matchedWaypoints = matchedWaypoints
        t.name = "getinfo"

        // find nearest point
        var mindist = 99999
        var minIdx = -1
        for (i in t.nodes.indices) {
            val ope = t.nodes[i]
            val dist = ope.calcDistance(listOne[0].crosspoint!!)
            if (mindist > dist) {
                mindist = dist
                minIdx = i
            }
        }
        var otherIdx: Int
        otherIdx = if (minIdx == t.nodes.size - 1) {
            minIdx - 1
        } else {
            minIdx + 1
        }
        val otherdist = t.nodes[otherIdx].calcDistance(listOne[0].crosspoint!!)
        val minSElev = t.nodes[minIdx].sElev.toInt()
        val otherSElev = t.nodes[otherIdx].sElev.toInt()
        var diffSElev: Int
        diffSElev = otherSElev - minSElev
        val diff = mindist.toDouble() / (mindist + otherdist) * diffSElev


        val n = OsmNodeNamed(listOne[0].crosspoint!!)
        n.name = wpt1.name
        n.sElev =
            if (minIdx != -1) (minSElev + diff.toInt()).toShort() else Short.Companion.MIN_VALUE

        n.nodeDescription =
            (if (start1 != null && start1.firstlink != null) start1.firstlink!!.descriptionBitmap else null)
        t.pois.add(n)
        //t.message = "get_info";
        //t.messageList.add(t.message);
        t.matchedWaypoints = listOne
        t.exportWaypoints = routingContext.exportWaypoints

        return t
    }

    suspend fun doRoundTrip(waypoints: List<OsmNodeNamed>): OsmTrack? {
        val waypoints = waypoints.toMutableList()
        routingContext.global.useDynamicDistance = true
        val searchRadius =
            (if (routingContext.roundTripDistance == null) 1500 else routingContext.roundTripDistance)!!.toDouble()
        var direction =
            (if (routingContext.startDirection == null) -1 else routingContext.startDirection)!!.toDouble()
        (if (routingContext.roundTripDirectionAdd == null) ROUNDTRIP_DEFAULT_DIRECTIONADD else routingContext.roundTripDirectionAdd)!!.toDouble()
        if (direction == -1.0) direction =
            getRandomDirectionFromData(waypoints[0], searchRadius).toDouble()

        if (routingContext.allowSamewayback) {
            val pos = destination(
                waypoints[0].iLon,
                waypoints[0].iLat,
                searchRadius,
                direction
            )
            val wpt2 = MatchedWaypoint()
            wpt2.waypoint = OsmNode(pos[0], pos[1])
            wpt2.name = "rt1_$direction"

            val onn = OsmNodeNamed(OsmNode(pos[0], pos[1]))
            onn.name = "rt1"
            waypoints.add(onn)
        } else {
            buildPointsFromCircle(
                waypoints,
                direction,
                searchRadius,
                routingContext.roundTripPoints ?: 5
            )
        }

        routingContext.global.waypointCatchingRange = 250.0

        return doRouting(waypoints)
    }

    fun buildPointsFromCircle(
        waypoints: MutableList<OsmNodeNamed>,
        startAngle: Double,
        searchRadius: Double,
        points: Int
    ) {
        //startAngle -= 90;
        for (i in 1..<points) {
            val anAngle = 90 - (180.0 * i / points)
            val pos = destination(
                waypoints[0].iLon,
                waypoints[0].iLat,
                searchRadius,
                startAngle - anAngle
            )
            val onn = OsmNodeNamed(OsmNode(pos[0], pos[1]))
            onn.name = "rt$i"
            waypoints.add(onn)
        }

        val onn = OsmNodeNamed(waypoints[0])
        onn.name = "to_rt"
        waypoints.add(onn)
    }

    fun getRandomDirectionFromData(wp: OsmNodeNamed, searchRadius: Double): Int {
        val start = System.currentTimeMillis()

        var preferredRandomType: Int
        val consider_elevation =
            routingContext.way.getVariableValue("consider_elevation", 0f) == 1f
        val consider_forest =
            routingContext.way.getVariableValue("consider_forest", 0f) == 1f
        val consider_river = routingContext.way.getVariableValue("consider_river", 0f) == 1f
        preferredRandomType = if (consider_elevation) {
            AreaInfo.Companion.RESULT_TYPE_ELEV50
        } else if (consider_forest) {
            AreaInfo.Companion.RESULT_TYPE_GREEN
        } else if (consider_river) {
            AreaInfo.Companion.RESULT_TYPE_RIVER
        } else {
            return (Math.random() * 360).toInt()
        }

        val wpt1 = MatchedWaypoint()
        wpt1.waypoint = wp
        wpt1.name = "info"
        wpt1.radius = searchRadius * 1.5

        val ais: MutableList<AreaInfo> = ArrayList()
        val areareader = AreaReader()
        if (routingContext.rawAreaPath != null) {
            val fai = File(routingContext.rawAreaPath)
            if (fai.exists()) {
                areareader.readAreaInfo(fai, wpt1, ais)
            }
        }

        if (ais.isEmpty()) {
            val listStart: MutableList<MatchedWaypoint> = ArrayList()
            listStart.add(wpt1)

            val wpliststart: MutableList<OsmNodeNamed> = ArrayList()
            wpliststart.add(wp)

            val listOne: MutableList<OsmNodeNamed> = ArrayList()

            run {
                var a = 45
                while (a < 360) {
                    val pos = destination(wp.iLon, wp.iLat, searchRadius * 1.5, a.toDouble())
                    val onn = OsmNodeNamed(OsmNode(pos[0], pos[1]))
                    onn.name = "via$a"
                    listOne.add(onn)

                    val wpt = MatchedWaypoint()
                    wpt.waypoint = onn
                    wpt.name = onn.name
                    listStart.add(wpt)
                    a += 90
                }
            }

            val rc = RoutingContext(routingContext.profile, routingContext.segmentDir)
            val re = RoutingEngine(rc)
            rc.global.useDynamicDistance = true
            re.matchWaypointsToNodes(listStart)
            re.resetCache(true)

            val numForest = rc.way.getLookupKey("estimated_forest_class")
            val numRiver = rc.way.getLookupKey("estimated_river_class")

            val start1 = re.nodesCache!!.getStartNode(listStart[0].node1!!.idFromPos)

            val elev =
                (start1?.elev ?: 0.0) // listOne.get(0).crosspoint.getElev();

            var maxlon = Int.Companion.MIN_VALUE
            var minlon = Int.Companion.MAX_VALUE
            var maxlat = Int.Companion.MIN_VALUE
            var minlat = Int.Companion.MAX_VALUE
            for (on in listOne) {
                maxlon = max(on.iLon, maxlon)
                minlon = min(on.iLon, minlon)
                maxlat = max(on.iLat, maxlat)
                minlat = min(on.iLat, minlat)
            }
            val searchRect = OsmNogoPolygon(true)
            searchRect.addVertex(maxlon, maxlat)
            searchRect.addVertex(maxlon, minlat)
            searchRect.addVertex(minlon, minlat)
            searchRect.addVertex(minlon, maxlat)

            for (a in 0..3) {
                rc.ai = AreaInfo(a * 90 + 90)
                rc.ai!!.elevStart = elev
                rc.ai!!.numForest = numForest
                rc.ai!!.numRiver = numRiver

                rc.ai!!.polygon = OsmNogoPolygon(true)
                rc.ai!!.polygon!!.addVertex(wp.iLon, wp.iLat)
                rc.ai!!.polygon!!.addVertex(listOne[a].iLon, listOne[a].iLat)
                if (a == 3) rc.ai!!.polygon!!.addVertex(listOne[0].iLon, listOne[0].iLat)
                else rc.ai!!.polygon!!.addVertex(listOne[a + 1].iLon, listOne[a + 1].iLat)

                ais.add(rc.ai!!)
            }

            var maxscale = abs(searchRect.points[2].x - searchRect.points[0].x)
            maxscale = max(1, (maxscale / 31250f / 2).roundToInt() + 1)

            areareader.getDirectAllData(
                rc.segmentDir,
                rc,
                wp,
                maxscale,
                rc.way,
                searchRect,
                ais
            )

            if (routingContext.rawAreaPath != null) {
                try {
                    wpt1.radius = searchRadius * 1.5
                    areareader.writeAreaInfo(routingContext.rawAreaPath!!, wpt1, ais)
                } catch (e: Exception) {
                }
            }
            rc.ai = null
        }

        logger.info(
            "round trip execution time={} seconds",
            (System.currentTimeMillis() - start) / 1000.0
        )

        // for (AreaInfo ai: ais) {
        //  System.out.println("\n" + ai.toString());
        //}
        when (preferredRandomType) {
            AreaInfo.Companion.RESULT_TYPE_ELEV50 -> Collections.sort(
                ais,
                Comparator<AreaInfo> { o1, o2 -> o2.elev50Weight - o1.elev50Weight })

            AreaInfo.Companion.RESULT_TYPE_GREEN -> Collections.sort(
                ais,
                Comparator<AreaInfo> { o1, o2 -> o2.green - o1.green })

            AreaInfo.Companion.RESULT_TYPE_RIVER -> Collections.sort(
                ais,
                Comparator<AreaInfo> { o1, o2 -> o2.river - o1.river })

            else -> return (Math.random() * 360).toInt()
        }

        val angle = ais[0].direction
        return angle - 30 + (Math.random() * 60).toInt()
    }


    private fun postElevationCheck(track: OsmTrack) {
        var lastPt: OsmPathElement? = null
        var startPt: OsmPathElement? = null
        var lastElev = Short.Companion.MIN_VALUE
        var startElev = Short.Companion.MIN_VALUE
        var endElev = Short.Companion.MIN_VALUE
        var startIdx = 0
        var endIdx: Int
        var dist = 0
        val ourSize = track.nodes.size
        for (idx in 0..<ourSize) {
            val n = track.nodes[idx]
            if (n.sElev == Short.Companion.MIN_VALUE && lastElev != Short.Companion.MIN_VALUE && idx < ourSize - 1) {
                // start one point before entry point to get better elevation results
                if (idx > 1) startElev = track.nodes[idx - 2].sElev
                if (startElev == Short.Companion.MIN_VALUE) startElev = lastElev
                startIdx = idx
                startPt = lastPt
                dist = 0
                if (lastPt != null) dist += n.calcDistance(lastPt)
            } else if (n.sElev != Short.Companion.MIN_VALUE && lastElev == Short.Companion.MIN_VALUE && startElev != Short.Companion.MIN_VALUE) {
                // end one point behind exit point to get better elevation results
                if (idx + 1 < track.nodes.size) endElev = track.nodes[idx + 1].sElev
                if (endElev == Short.Companion.MIN_VALUE) endElev = n.sElev
                endIdx = idx
                var tmpPt = track.nodes[if (startIdx > 1) startIdx - 2 else startIdx - 1]
                val diffElev = endElev - startElev
                dist += tmpPt.calcDistance(startPt!!)
                dist += n.calcDistance(lastPt!!)
                var distRest = dist
                var incline = diffElev / (dist / 100.0)
                var lastMsg = ""
                var tmpincline = 0.0
                var startincline = 0.0
                var selev = track.nodes[startIdx - 2].sElev.toDouble()
                var hasInclineTags = false
                for (i in startIdx - 1..<endIdx + 1) {
                    val tmp = track.nodes[i]
                    if (tmp.message != null) {
                        val md = tmp.message!!.copy()
                        val msg = md!!.wayKeyValues
                        if (msg != lastMsg) {
                            val revers = msg!!.contains("reversedirection=yes")
                            var pos = msg.indexOf("incline=")
                            if (pos != -1) {
                                hasInclineTags = true
                                var s = msg.substring(pos + 8)
                                pos = s.indexOf(" ")
                                if (pos != -1) s = s.substring(0, pos)

                                if (!s.isEmpty()) {
                                    try {
                                        var ind = s.indexOf("%")
                                        if (ind != -1) s = s.substring(0, ind)
                                        ind = s.indexOf("°")
                                        if (ind != -1) s = s.substring(0, ind)
                                        tmpincline = s.trim { it <= ' ' }.toDouble()
                                        if (revers) tmpincline *= -1.0
                                    } catch (e: NumberFormatException) {
                                        tmpincline = 0.0
                                    }
                                }
                            } else {
                                tmpincline = 0.0
                            }
                            if (startincline == 0.0) {
                                startincline = tmpincline
                            } else if (startincline < 0 && tmpincline > 0) {
                                // for the way up find the exit point
                                val diff = endElev - selev
                                tmpincline = diff / (distRest / 100.0)
                            }
                        }
                        lastMsg = msg
                    }
                    val tmpdist = tmp.calcDistance(tmpPt)
                    distRest -= tmpdist
                    if (hasInclineTags) incline = tmpincline
                    selev = (selev + (tmpdist / 100.0 * incline))
                    tmp.sElev = selev.toInt().toShort()
                    tmp.message!!.ele = selev.toInt().toShort()
                    tmpPt = tmp
                }
                dist = 0
            } else if (n.sElev != Short.Companion.MIN_VALUE && lastElev == Short.Companion.MIN_VALUE && startIdx == 0) {
                // fill at start
                for (i in 0..<idx) {
                    track.nodes[i].sElev = n.sElev
                }
            } else if (n.sElev == Short.Companion.MIN_VALUE && idx == track.nodes.size - 1) {
                // fill at end
                startIdx = idx
                for (i in startIdx..<track.nodes.size) {
                    track.nodes[i].sElev = (lastElev)
                }
            } else if (n.sElev == Short.Companion.MIN_VALUE) {
                if (lastPt != null) dist += n.calcDistance(lastPt)
            }
            lastElev = n.sElev
            lastPt = n
        }
    }

    suspend fun doSearch(waypoints: List<OsmNodeNamed>) {
        val seedPoint = MatchedWaypoint()
        seedPoint.waypoint = waypoints[0]
        val listOne: MutableList<MatchedWaypoint> = ArrayList()
        listOne.add(seedPoint)
        matchWaypointsToNodes(listOne)

        findTrack(seedPoint, null, null, null, false)

        openSet.clear()
    }

    private suspend fun findTrack(
        waypoints: MutableList<OsmNodeNamed>,
        refTracks: Array<OsmTrack?>,
        lastTracks: Array<OsmTrack?>
    ): OsmTrack? {
        while (true) {
            try {
                return tryFindTrack(waypoints, refTracks, lastTracks)
            } catch (rie: RoutingIslandException) {
                if (routingContext.global.useDynamicDistance) {
                    for (mwp in matchedWaypoints) {
                        if (mwp.name!!.contains("_add")) {
                            val n1 = mwp.node1!!.idFromPos
                            val n2 = mwp.node2!!.idFromPos
                            islandNodePairs.addTempPair(n1, n2)
                        }
                    }
                }
                islandNodePairs.freezeTempPairs()
                nodesCache!!.clean(true)
                matchedWaypoints = mutableListOf()
            }
        }
    }

    private suspend fun tryFindTrack(
        waypoints: MutableList<OsmNodeNamed>,
        refTracks: Array<OsmTrack?>,
        lastTracks: Array<OsmTrack?>
    ): OsmTrack? {
        var refTracks = refTracks
        var lastTracks = lastTracks
        val totaltrack = OsmTrack()
        var nUnmatched = waypoints.size
        var hasDirectRouting = false

        if (useNodePoints && extraWaypoints.isNotEmpty()) {
            // add extra waypoints from the last broken round
            for (wp in extraWaypoints) {
                if (wp.direct) hasDirectRouting = true
                if (wp.name!!.startsWith("from")) {
                    waypoints.add(1, wp)
                    waypoints[0].direct = true
                    nUnmatched++
                } else {
                    waypoints.add(waypoints.size - 1, wp)
                    waypoints[waypoints.size - 2].direct = true
                    nUnmatched++
                }
            }
            extraWaypoints = mutableListOf()
        }
        if (lastTracks.size < waypoints.size - 1) {
            refTracks = arrayOfNulls(waypoints.size - 1) // used ways for alternatives
            lastTracks = arrayOfNulls(waypoints.size - 1)
            hasDirectRouting = true
        }
        for (wp in waypoints) {
            logger.info("wp={} {}", wp, if (wp.direct) "direct" else "")
            if (wp.direct) hasDirectRouting = true
        }

        // check for a track for that target
        var nearbyTrack: OsmTrack? = null
        if (!hasDirectRouting && lastTracks[waypoints.size - 2] == null) {
            val debugInfo = if (logger.isInfoEnabled) StringBuilder() else null
            nearbyTrack = OsmTrack.Companion.readBinary(
                routingContext.rawTrackPath,
                waypoints[waypoints.size - 1],
                routingContext.nogoChecksums,
                routingContext.profileTimestamp,
                debugInfo
            )
            if (nearbyTrack != null) {
                nUnmatched--
            }
            if (logger.isInfoEnabled) {
                val found = nearbyTrack != null
                val dirty = found && nearbyTrack.isDirty
                logger.info("read referenceTrack, found={} dirty={} {}", found, dirty, debugInfo)
            }
        }

        if (matchedWaypoints.isEmpty()) { // could exist from the previous alternative level
            for (i in 0..<nUnmatched) {
                val mwp = MatchedWaypoint()
                mwp.waypoint = waypoints[i]
                mwp.name = waypoints[i].name
                mwp.direct = waypoints[i].direct
                matchedWaypoints.add(mwp)
            }
            val startSize = matchedWaypoints.size
            matchWaypointsToNodes(matchedWaypoints)
            if (startSize < matchedWaypoints.size) {
                refTracks =
                    arrayOfNulls(matchedWaypoints.size - 1) // used ways for alternatives
                lastTracks = arrayOfNulls(matchedWaypoints.size - 1)
                hasDirectRouting = true
            }

            for (mwp in matchedWaypoints) {
                if (matchedWaypoints.size != nUnmatched) logger.info(
                    "new wp={} {}{}",
                    mwp.waypoint,
                    mwp.crosspoint,
                    if (mwp.direct) " direct" else ""
                )
            }

            routingContext.checkMatchedWaypointAgainstNogos(matchedWaypoints)

            // detect target islands: restricted search in inverse direction
            routingContext.inverseDirection = !routingContext.global.inverseRouting
            airDistanceCostFactor = 0.0
            for (i in 0..<matchedWaypoints.size - 1) {
                nodeLimit = MAXNODES_ISLAND_CHECK
                if (matchedWaypoints[i].direct) continue
                if (routingContext.global.inverseRouting) {
                    val seg = findTrack(
                        matchedWaypoints[i],
                        matchedWaypoints[i + 1],
                        null,
                        null,
                        false
                    )
                    require(!(seg == null && nodeLimit > 0)) { "start island detected for section $i" }
                } else {
                    val seg = findTrack(
                        matchedWaypoints[i + 1],
                        matchedWaypoints[i],
                        null,
                        null,
                        false
                    )
                    require(!(seg == null && nodeLimit > 0)) { "target island detected for section $i" }
                }
            }
            routingContext.inverseDirection = false
            nodeLimit = 0

            if (nearbyTrack != null) {
                matchedWaypoints.add(nearbyTrack.endPoint!!)
            }
        } else {
            if (lastTracks.size < matchedWaypoints.size - 1) {
                refTracks =
                    arrayOfNulls(matchedWaypoints.size - 1) // used ways for alternatives
                lastTracks = arrayOfNulls(matchedWaypoints.size - 1)
                hasDirectRouting = true
            }
        }


        routingContext.global.hasDirectRouting = hasDirectRouting

        OsmPath.Companion.seg = 1 // set segment counter
        for (i in 0..<matchedWaypoints.size - 1) {
            if (lastTracks[i] != null) {
                if (refTracks[i] == null) refTracks[i] = OsmTrack()
                refTracks[i]!!.addNodes(lastTracks[i]!!)
            }

            val seg: OsmTrack?
            val wptIndex: Int
            if (routingContext.global.inverseRouting) {
                routingContext.inverseDirection = true
                seg = searchTrack(
                    matchedWaypoints[i + 1],
                    matchedWaypoints[i],
                    null,
                    refTracks[i]
                )
                routingContext.inverseDirection = false
                wptIndex = i + 1
            } else {
                seg = searchTrack(
                    matchedWaypoints[i],
                    matchedWaypoints[i + 1],
                    if (i == matchedWaypoints.size - 2) nearbyTrack else null,
                    refTracks[i]
                )
                wptIndex = i
                if (routingContext.global.continueStraight) {
                    if (i < matchedWaypoints.size - 2) {
                        val lastPoint =
                            if (seg!!.containsNode(matchedWaypoints[i + 1].node1!!)) matchedWaypoints[i + 1].node1 else matchedWaypoints[i + 1].node2
                        val nogo = OsmNodeNamed(lastPoint!!)
                        nogo.radius = 5.0
                        nogo.name = "nogo" + (i + 1)
                        nogo.nogoWeight = 9999.0
                        nogo.isNogo = true
                        routingContext.nogopoints.add(nogo)
                    }
                }
            }
            if (seg == null) return null

            if (routingContext.ai != null) return null

            var changed = false
            if (routingContext.global.correctMisplacedViaPoints && !matchedWaypoints[i].direct && !routingContext.allowSamewayback) {
                changed = snapPathConnection(
                    totaltrack,
                    seg,
                    if (routingContext.global.inverseRouting) matchedWaypoints[i + 1] else matchedWaypoints[i]
                )
            }
            if (wptIndex > 0) matchedWaypoints[wptIndex].indexInTrack =
                totaltrack.nodes.size - 1

            totaltrack.appendTrack(seg)
            lastTracks[i] = seg
        }

        postElevationCheck(totaltrack)

        recalcTrack(totaltrack)

        matchedWaypoints[matchedWaypoints.size - 1].indexInTrack = totaltrack.nodes.size - 1
        totaltrack.matchedWaypoints = matchedWaypoints
        totaltrack.processVoiceHints(routingContext)
        totaltrack.prepareSpeedProfile(routingContext)

        totaltrack.showTime = routingContext.global.showTime
        totaltrack.params = routingContext.keyValues

        if (routingContext.poipoints.isNotEmpty()) totaltrack.pois = routingContext.poipoints

        return totaltrack
    }

    suspend fun getExtraSegment(start: OsmPathElement, end: OsmPathElement): OsmTrack? {
        val wptlist: MutableList<MatchedWaypoint?> = ArrayList()
        val wpt1 = MatchedWaypoint()
        wpt1.waypoint = OsmNode(start.iLon, start.iLat)
        wpt1.name = "wptx1"
        wpt1.crosspoint = OsmNode(start.iLon, start.iLat)
        wpt1.node1 = OsmNode(start.iLon, start.iLat)
        wpt1.node2 = OsmNode(end.iLon, end.iLat)
        wptlist.add(wpt1)
        val wpt2 = MatchedWaypoint()
        wpt2.waypoint = OsmNode(end.iLon, end.iLat)
        wpt2.name = "wptx2"
        wpt2.crosspoint = OsmNode(end.iLon, end.iLat)
        wpt2.node2 = OsmNode(start.iLon, start.iLat)
        wpt2.node1 = OsmNode(end.iLon, end.iLat)
        wptlist.add(wpt2)

        val mwp1 = wptlist[0]
        val mwp2 = wptlist[1]

        var mid: OsmTrack?

        val corr = routingContext.global.correctMisplacedViaPoints
        routingContext.global.correctMisplacedViaPoints = false

        guideTrack = OsmTrack()
        guideTrack!!.addNode(start)
        guideTrack!!.addNode(end)

        mid = findTrack(mwp1, mwp2, null, null, false)

        guideTrack = null
        routingContext.global.correctMisplacedViaPoints = corr

        return mid
    }

    private suspend fun snapRoundaboutConnection(
        tt: OsmTrack,
        t: OsmTrack,
        indexStart: Int,
        indexEnd: Int,
        indexMeeting: Int,
        startWp: MatchedWaypoint
    ): Int {
        var indexEnd = indexEnd
        val indexMeetingBack = (if (indexMeeting == -1) tt.nodes.size - 1 else indexMeeting)
        var indexMeetingFore = 0
        var indexStartBack = indexStart
        var indexStartFore = 0

        val ptStart = tt.nodes[indexStartBack]
        val ptMeeting = tt.nodes[indexMeetingBack]
        val ptEnd = t.nodes[indexEnd]

        val bMeetingIsOnRoundabout = ptMeeting.message!!.isRoundabout
        var bMeetsRoundaboutStart = false
        var i = 0
        while (i < indexEnd) {
            val n = t.nodes[i]
            if (n.positionEquals(ptStart)) {
                indexStartFore = i
                bMeetsRoundaboutStart = true
            }
            if (n.positionEquals(ptMeeting)) {
                indexMeetingFore = i
            }

            i++
        }

        if (!bMeetsRoundaboutStart && bMeetingIsOnRoundabout) {
            indexEnd = indexMeetingFore
        }
        if (bMeetsRoundaboutStart && bMeetingIsOnRoundabout) {
            indexEnd = indexStartFore
        }

        val removeList: MutableList<OsmPathElement?> = ArrayList()
        if (!bMeetsRoundaboutStart) {
            indexStartBack = indexMeetingBack
            while (!tt.nodes[indexStartBack].message!!.isRoundabout) {
                indexStartBack--
                if (indexStartBack == 2) break
            }
        }

        i = indexStartBack + 1
        while (i < tt.nodes.size) {
            val n = tt.nodes[i]
            val detours = tt.getFromDetourMap(n.idFromPos)
            var h = detours
            while (h != null) {
                h = h.nextHolder
            }
            removeList.add(n)
            i++
        }

        var ttend: OsmPathElement? = null
        var ttend_next: OsmPathElement? = null
        if (!bMeetingIsOnRoundabout && !bMeetsRoundaboutStart) {
            ttend = tt.nodes[indexStartBack]
            ttend_next = tt.nodes[indexStartBack + 1]
            tt.getFromDetourMap(ttend.idFromPos)

            tt.registerDetourForId(ttend.idFromPos, null)
        }

        for (e in removeList) {
            tt.nodes.remove(e)
        }
        removeList.clear()


        i = 0
        while (i < indexEnd) {
            val n = t.nodes[i]
            if (n.positionEquals(if (bMeetsRoundaboutStart) ptStart else ptEnd)) break
            if (!bMeetingIsOnRoundabout && !bMeetsRoundaboutStart && n.message!!.isRoundabout) break

            val detours = t.getFromDetourMap(n.idFromPos)
            var h = detours
            while (h != null) {
                h = h.nextHolder
            }
            removeList.add(n)
            i++
        }

        // time hold
        var atime = 0f
        var aenergy = 0f
        var acost = 0
        if (i > 1) {
            atime = t.nodes[i].time
            aenergy = t.nodes[i].energy
            acost = t.nodes[i].cost
        }

        for (e in removeList) {
            t.nodes.remove(e)
        }
        removeList.clear()

        if (atime > 0f) {
            for (e in t.nodes) {
                e.time = e.time - atime
                e.energy = e.energy - aenergy
                e.cost = e.cost - acost
            }
        }

        if (!bMeetingIsOnRoundabout && !bMeetsRoundaboutStart) {
            val tstart = t.nodes[0]
            t.getFromDetourMap(tstart.idFromPos)
            val ttend_detours = tt.getFromDetourMap(ttend!!.idFromPos)

            val mid = getExtraSegment(ttend, ttend_detours!!.node!!)
            val tt_end = tt.nodes[tt.nodes.size - 1]

            val last_cost = tt_end.cost
            var tmp_cost = 0

            if (mid != null) {
                var start = false
                for (e in mid.nodes) {
                    if (start) {
                        if (e.positionEquals(ttend_detours.node!!)) {
                            tmp_cost = e.cost
                            break
                        }
                        e.cost = last_cost + e.cost
                        tt.nodes.add(e)
                    }
                    if (e.positionEquals(tt_end)) start = true
                }
            }

            ttend_detours.node!!.cost = last_cost + tmp_cost
            tt.nodes.add(ttend_detours.node!!)
            t.nodes.add(0, ttend_detours.node!!)
        }

        tt.cost = tt.nodes[tt.nodes.size - 1].cost
        t.cost = t.nodes[t.nodes.size - 1].cost

        startWp.correctedpoint = OsmNode(ptStart.iLon, ptStart.iLat)

        return (t.nodes.size)
    }

    // check for way back on way point
    private suspend fun snapPathConnection(
        tt: OsmTrack,
        t: OsmTrack,
        startWp: MatchedWaypoint
    ): Boolean {
        if (!startWp.name!!.startsWith("via") && !startWp.name!!.startsWith("rt")) return false

        val ourSize = tt.nodes.size
        if (ourSize > 0) {
            tt.nodes[ourSize - 1]
            for (node in routingContext.poipoints) {
                val lon0 = tt.nodes[ourSize - 2].iLon
                val lat0 = tt.nodes[ourSize - 2].iLat
                val lon1: Int = startWp.crosspoint!!.iLon
                val lat1 = startWp.crosspoint!!.iLat
                val lon2: Int = node.iLon
                val lat2 = node.iLat
                routingContext.anglemeter.calcAngle(lon0, lat0, lon1, lat1, lon2, lat2)
                val dist = node.calcDistance(startWp.crosspoint!!)
                if (dist < routingContext.global.waypointCatchingRange) return false
            }
            val removeBackList: MutableList<OsmPathElement?> = ArrayList()
            val removeForeList: MutableList<OsmPathElement?> = ArrayList()
            val removeVoiceHintList: MutableList<Int> = ArrayList()
            var last: OsmPathElement? = null
            val lastJunctions = CompactLongMap<OsmPathElementHolder?>()
            var newJunction: OsmPathElement?
            var newTarget: OsmPathElement? = null
            var tmpback: OsmPathElement?
            var tmpfore: OsmPathElement?
            var tmpStart: OsmPathElement? = null
            var indexback = ourSize - 1
            var indexfore = 0
            val stop = (if (indexback - MAX_STEPS_CHECK > 1) indexback - MAX_STEPS_CHECK else 1)
            var wayDistance = 0.0
            var nextDist: Double
            var bCheckRoundAbout = false
            var bBackRoundAbout = false
            var bForeRoundAbout = false
            var indexBackFound = 0
            var indexForeFound = 0
            var differentLanePoints = 0
            var indexMeeting = -1
            while (indexback >= 1 && indexback >= stop && indexfore < t.nodes.size) {
                tmpback = tt.nodes[indexback]
                tmpfore = t.nodes[indexfore]
                if (!bBackRoundAbout && tmpback.message != null && tmpback.message!!.isRoundabout) {
                    bBackRoundAbout = true
                    indexBackFound = indexfore
                }
                if (!bForeRoundAbout && tmpfore.message != null && tmpfore.message!!.isRoundabout ||
                    (tmpback.positionEquals(tmpfore) && tmpback.message!!.isRoundabout)
                ) {
                    bForeRoundAbout = true
                    indexForeFound = indexfore
                }
                if (indexfore == 0) {
                    tmpStart = t.nodes[0]
                } else {
                    val dirback =
                        getDirection(tmpStart!!.iLon, tmpStart.iLat, tmpback.iLon, tmpback.iLat)
                    val dirfore =
                        getDirection(tmpStart.iLon, tmpStart.iLat, tmpfore.iLon, tmpfore.iLat)
                    val dirdiff = getDifferenceFromDirection(dirback, dirfore)
                    // walking wrong direction
                    if (dirdiff > 60 && !bBackRoundAbout && !bForeRoundAbout) break
                }
                // seems no roundabout, only on one end
                if (bBackRoundAbout != bForeRoundAbout && indexfore - abs(indexForeFound - indexBackFound) > 8) break
                if (!tmpback.positionEquals(tmpfore)) differentLanePoints++
                if (tmpback.positionEquals(tmpfore)) indexMeeting = indexback
                bCheckRoundAbout = bBackRoundAbout && bForeRoundAbout
                if (bCheckRoundAbout) break
                indexback--
                indexfore++
            }
            //System.out.println("snap round result " + indexback + ": " + bBackRoundAbout + " - " + indexfore + "; " + bForeRoundAbout + " pts " + differentLanePoints);
            if (bCheckRoundAbout) {
                tmpback = tt.nodes[--indexback]
                while (tmpback!!.message != null && tmpback.message!!.isRoundabout) {
                    tmpback = tt.nodes[--indexback]
                }

                var ifore = ++indexfore
                var testfore = t.nodes[ifore]
                while (ifore < t.nodes.size && testfore.message != null && testfore.message!!.isRoundabout) {
                    testfore = t.nodes[ifore]
                    ifore++
                }

                snapRoundaboutConnection(tt, t, indexback, --ifore, indexMeeting, startWp)

                // remove filled arrays
                removeVoiceHintList.clear()
                removeBackList.clear()
                removeForeList.clear()
                return true
            }
            indexback = ourSize - 1
            indexfore = 0
            while (indexback >= 1 && indexback >= stop && indexfore < t.nodes.size) {
                var junctions = 0
                tmpback = tt.nodes[indexback]
                tmpfore = t.nodes[indexfore]
                if (tmpback.message != null && tmpback.message!!.isRoundabout) {
                    bCheckRoundAbout = true
                }
                if (tmpfore.message != null && tmpfore.message!!.isRoundabout) {
                    bCheckRoundAbout = true
                }
                run {
                    val dist = tmpback.calcDistance(tmpfore)
                    val detours = tt.getFromDetourMap(tmpback.idFromPos)
                    var h = detours
                    while (h != null) {
                        junctions++
                        lastJunctions.put(h.node!!.idFromPos, h)
                        h = h.nextHolder
                    }

                    if (dist == 1 && indexfore > 0) {
                        if (indexfore == 1) {
                            removeBackList.add(tt.nodes[tt.nodes.size - 1]) // last and first should be equal, so drop only on second also equal
                            removeForeList.add(t.nodes[0])
                            removeBackList.add(tmpback)
                            removeForeList.add(tmpfore)
                            removeVoiceHintList.add(tt.nodes.size - 1)
                            removeVoiceHintList.add(indexback)
                        } else {
                            removeBackList.add(tmpback)
                            removeForeList.add(tmpfore)
                            removeVoiceHintList.add(indexback)
                        }
                        nextDist = t.nodes[indexfore - 1].calcDistance(tmpfore).toDouble()
                        wayDistance += nextDist
                    }
                    if (dist > 1 || indexback == 1) {
                        if (removeBackList.isNotEmpty()) {
                            // recover last - should be the cross point
                            removeBackList.remove(removeBackList[removeBackList.size - 1])
                            removeForeList.remove(removeForeList[removeForeList.size - 1])
                            break
                        } else {
                            return false
                        }
                    }
                    indexback--
                    indexfore++
                    if (routingContext.global.correctMisplacedViaPointsDistance > 0 &&
                        wayDistance > routingContext.global.correctMisplacedViaPointsDistance
                    ) {
                        removeVoiceHintList.clear()
                        removeBackList.clear()
                        removeForeList.clear()
                        return false
                    }
                }
            }


            // time hold
            var atime = 0f
            var aenergy = 0f
            var acost = 0
            if (removeForeList.size > 1) {
                atime = t.nodes[indexfore - 1].time
                aenergy = t.nodes[indexfore - 1].energy
                acost = t.nodes[indexfore - 1].cost
            }

            for (e in removeBackList) {
                tt.nodes.remove(e)
            }
            for (e in removeForeList) {
                t.nodes.remove(e)
            }
            for (e in removeVoiceHintList) {
                tt.removeVoiceHint(e)
            }
            removeVoiceHintList.clear()
            removeBackList.clear()
            removeForeList.clear()

            if (atime > 0f) {
                for (e in t.nodes) {
                    e.time = e.time - atime
                    e.energy = e.energy - aenergy
                    e.cost = e.cost - acost
                }
            }

            if (t.nodes.size < 2) return true
            if (tt.nodes.isEmpty()) return true
            last = if (tt.nodes.size == 1) {
                tt.nodes[0]
            } else {
                tt.nodes[tt.nodes.size - 2]
            }
            newJunction = t.nodes[0]
            newTarget = t.nodes[1]

            tt.cost = tt.nodes[tt.nodes.size - 1].cost
            t.cost = t.nodes[t.nodes.size - 1].cost

            // fill to correctedpoint
            startWp.correctedpoint = OsmNode(newJunction.iLon, newJunction.iLat)

            return true
        }
        return false
    }

    private fun recalcTrack(t: OsmTrack) {
        var totaldist = 0
        var totaltime = 0
        var lasttime = 0f
        var lastenergy = 0f
        var speed_min = 9999f
        val directMap: MutableMap<Int?, Int?> = HashMap()
        var tmptime: Float
        var speed: Float
        var dist: Int
        var angle: Double

        var ascend = 0.0
        var ehb = 0.0
        val ourSize = t.nodes.size

        var ele_start = Short.Companion.MIN_VALUE
        var ele_end = Short.Companion.MIN_VALUE
        val eleFactor = if (routingContext.global.inverseRouting) 0.25 else -0.25

        for (i in 0..<ourSize) {
            val n = t.nodes[i]
            if (n.message == null) n.message = MessageData()
            var nLast: OsmPathElement? = null
            when (i) {
                0 -> {
                    angle = 0.0
                    dist = 0
                }

                1 -> {
                    angle = 0.0
                    nLast = t.nodes[0]
                    dist = nLast.calcDistance(n)
                }

                else -> {
                    val lon0 = t.nodes[i - 2].iLon
                    val lat0 = t.nodes[i - 2].iLat
                    val lon1 = t.nodes[i - 1].iLon
                    val lat1 = t.nodes[i - 1].iLat
                    val lon2 = t.nodes[i].iLon
                    val lat2 = t.nodes[i].iLat
                    angle = routingContext.anglemeter.calcAngle(lon0, lat0, lon1, lat1, lon2, lat2)
                    nLast = t.nodes[i - 1]
                    dist = nLast.calcDistance(n)
                }
            }
            n.message!!.linkdist = dist
            n.message!!.turnangle = angle.toFloat()
            totaldist += dist
            totaltime = (totaltime + n.time).toInt()
            tmptime = (n.time - lasttime)
            if (dist > 0) {
                speed = dist / tmptime * 3.6f
                speed_min = min(speed_min, speed)
            }
            if (tmptime == 1f) { // no time used here
                directMap.put(i, dist)
            }

            lastenergy = n.energy
            lasttime = n.time

            val ele = n.sElev
            if (ele != Short.Companion.MIN_VALUE) ele_end = ele
            if (ele_start == Short.Companion.MIN_VALUE) ele_start = ele

            if (nLast != null) {
                val ele_last = nLast.sElev
                if (ele_last != Short.Companion.MIN_VALUE) {
                    ehb = ehb + (ele_last - ele) * eleFactor
                }
                val filter = elevationFilter(n)
                if (ehb > 0) {
                    ascend += ehb
                    ehb = 0.0
                } else if (ehb < filter) {
                    ehb = filter
                }
            }
        }

        t.ascend = ascend.toInt()
        t.plainAscend = ((ele_start - ele_end) * eleFactor + 0.5).toInt()

        t.distance = totaldist

        //t.energy = totalenergy;
        val keys: SortedSet<Int> = TreeSet(directMap.keys)
        for (key in keys) {
            val value: Int = directMap[key]!!
            val addTime = (value / (speed_min / 3.6f))

            var addEnergy = 0.0
            if (key > 0) {
                val GRAVITY = 9.81 // in meters per second^(-2)
                val incline =
                    (if (t.nodes[key - 1].sElev == Short.Companion.MIN_VALUE || t.nodes[key].sElev == Short.Companion.MIN_VALUE) 0.0 else (t.nodes[key - 1].elev - t.nodes[key].elev) / value)
                val f_roll =
                    routingContext.global.totalMass * GRAVITY * (routingContext.global.defaultC_r + incline)
                val spd = speed_min / 3.6
                addEnergy = value * (routingContext.global.S_C_x * spd * spd + f_roll)
            }
            for (j in key..<ourSize) {
                val n = t.nodes[j]
                n.time = n.time + addTime
                n.energy = n.energy + addEnergy.toFloat()
            }
        }
        t.energy = t.nodes[t.nodes.size - 1].energy.toInt()

        logger.info("track total distance={} ascend={}", t.distance, t.ascend)
    }

    /**
     * find the elevation type for position
     * to determine the filter value
     *
     * @param n the point
     * @return the filter value for 1sec / 3sec elevation source
     */
    fun elevationFilter(n: OsmPos): Double {
        if (nodesCache != null) {
            val r = nodesCache!!.getElevationType(n.iLon, n.iLat)
            if (r == 1) return -5.0
        }
        return -10.0
    }

    // geometric position matching finding the nearest routable way-section
    private fun matchWaypointsToNodes(unmatchedWaypoints: MutableList<MatchedWaypoint>) {
        resetCache(false)
        val useDynamicDistance = routingContext.global.useDynamicDistance
        val bAddBeeline = routingContext.global.buildBeelineOnRange
        var range = routingContext.global.waypointCatchingRange
        var ok = nodesCache!!.matchWaypointsToNodes(unmatchedWaypoints, range, islandNodePairs)
        if (!ok && useDynamicDistance) {
            logger.info("second check for way points")
            resetCache(false)
            range = -MAX_DYNAMIC_RANGE.toDouble()
            val tmp: MutableList<MatchedWaypoint> = ArrayList()
            for (mwp in unmatchedWaypoints) {
                if (mwp.crosspoint == null || mwp.radius >= routingContext.global.waypointCatchingRange) tmp.add(
                    mwp
                )
            }
            ok = nodesCache!!.matchWaypointsToNodes(tmp, range, islandNodePairs)
        }
        if (!ok) {
            for (mwp in unmatchedWaypoints) {
                requireNotNull(mwp.crosspoint) { mwp.name + "-position not mapped in existing datafile" }
            }
        }
        // add beeline points when not already done
        if (useDynamicDistance && !useNodePoints && bAddBeeline) {
            val waypoints: MutableList<MatchedWaypoint> = ArrayList()
            for (i in unmatchedWaypoints.indices) {
                val wp = unmatchedWaypoints[i]
                if (wp.waypoint!!.calcDistance(wp.crosspoint!!) > routingContext.global.waypointCatchingRange) {
                    val nmw = MatchedWaypoint()
                    if (i == 0) {
                        var onn = OsmNodeNamed(wp.waypoint!!)
                        onn.name = "from"
                        nmw.waypoint = onn
                        nmw.name = onn.name
                        nmw.crosspoint = OsmNode(wp.waypoint!!.iLon, wp.waypoint!!.iLat)
                        nmw.direct = true
                        onn = OsmNodeNamed(wp.crosspoint!!)
                        onn.name = wp.name + "_add"
                        wp.waypoint = onn
                        waypoints.add(nmw)
                        wp.name = wp.name + "_add"
                        waypoints.add(wp)
                    } else {
                        val onn = OsmNodeNamed(wp.crosspoint!!)
                        onn.name = wp.name + "_add"
                        nmw.waypoint = onn
                        nmw.crosspoint = OsmNode(wp.crosspoint!!.iLon, wp.crosspoint!!.iLat)
                        nmw.node1 = OsmNode(wp.node1!!.iLon, wp.node1!!.iLat)
                        nmw.node2 = OsmNode(wp.node2!!.iLon, wp.node2!!.iLat)
                        nmw.direct = true

                        if (wp.name != null) nmw.name = wp.name
                        waypoints.add(nmw)
                        wp.name = wp.name + "_add"
                        waypoints.add(wp)
                        if (wp.name!!.startsWith("via")) {
                            wp.direct = true
                            val emw = MatchedWaypoint()
                            val onn2 = OsmNodeNamed(wp.crosspoint!!)
                            onn2.name = wp.name + "_2"
                            emw.name = onn2.name
                            emw.waypoint = onn2
                            emw.crosspoint = OsmNode(nmw.crosspoint!!.iLon, nmw.crosspoint!!.iLat)
                            emw.node1 = OsmNode(nmw.node1!!.iLon, nmw.node1!!.iLat)
                            emw.node2 = OsmNode(nmw.node2!!.iLon, nmw.node2!!.iLat)
                            emw.direct = false
                            waypoints.add(emw)
                        }
                        wp.crosspoint = OsmNode(wp.waypoint!!.iLon, wp.waypoint!!.iLat)
                    }
                } else {
                    waypoints.add(wp)
                }
            }
            unmatchedWaypoints.clear()
            unmatchedWaypoints.addAll(waypoints)
        }
    }

    private suspend fun searchTrack(
        startWp: MatchedWaypoint,
        endWp: MatchedWaypoint,
        nearbyTrack: OsmTrack?,
        refTrack: OsmTrack?
    ): OsmTrack? {
        // remove nogos with waypoints inside
        try {
            val calcBeeline = startWp.direct

            if (!calcBeeline) return searchRoutedTrack(startWp, endWp, nearbyTrack, refTrack)

            // we want a beeline-segment
            var path = routingContext.createPath(OsmLink(null, startWp.crosspoint))
            path = routingContext.createPath(
                path,
                OsmLink(startWp.crosspoint, endWp.crosspoint),
                null,
                false
            )
            return compileTrack(path, false)
        } finally {
            routingContext.restoreNogoList()
        }
    }

    private suspend fun searchRoutedTrack(
        startWp: MatchedWaypoint?,
        endWp: MatchedWaypoint?,
        nearbyTrack: OsmTrack?,
        refTrack: OsmTrack?
    ): OsmTrack? {
        var track: OsmTrack? = null
        val airDistanceCostFactors = doubleArrayOf(
            routingContext.global.pass1coefficient,
            routingContext.global.pass2coefficient
        )
        var isDirty = false
        var dirtyMessage: IllegalArgumentException? = null

        if (nearbyTrack != null) {
            airDistanceCostFactor = 0.0
            try {
                track = findTrack(startWp, endWp, nearbyTrack, refTrack, true)
            } catch (iae: IllegalArgumentException) {
                // fast partial recalcs: if that timed out, but we had a match,
                // build the concatenation from the partial and the nearby track
                if (matchPath != null) {
                    track = mergeTrack(matchPath!!, nearbyTrack)
                    isDirty = true
                    dirtyMessage = iae
                    logger.info("using fast partial recalc")
                }
            }
        }

        if (track == null) {
            for (cfi in airDistanceCostFactors.indices) {
                if (cfi > 0) lastAirDistanceCostFactor = airDistanceCostFactors[cfi - 1]
                airDistanceCostFactor = airDistanceCostFactors[cfi]

                if (airDistanceCostFactor < 0.0) {
                    continue
                }

                var t: OsmTrack?
                t = findTrack(
                    startWp,
                    endWp,
                    track,
                    refTrack,
                    false
                )
                if (routingContext.ai != null) return t

                if (t == null && track != null && matchPath != null) {
                    // ups, didn't find it, use a merge
                    t = mergeTrack(matchPath!!, track)
                    logger.info("using sloppy merge cause pass1 didn't reach destination")
                }
                if (t != null) {
                    track = t
                } else {
                    throw IllegalArgumentException("no track found at pass=$cfi")
                }
            }
        }
        requireNotNull(track) { "no track found" }

        val wasClean = nearbyTrack != null && !nearbyTrack.isDirty
        if (refTrack == null && !(wasClean && isDirty)) { // do not overwrite a clean with a dirty track
            logger.info("supplying new reference track, dirty={}", isDirty)
            track.endPoint = endWp
            track.nogoChecksums = routingContext.nogoChecksums
            track.profileTimestamp = routingContext.profileTimestamp
            track.isDirty = isDirty
        }

        if (!wasClean && isDirty) {
            throw dirtyMessage!!
        }

        // final run for verbose log info and detail nodes
        airDistanceCostFactor = 0.0
        lastAirDistanceCostFactor = 0.0
        guideTrack = track
        try {
            val tt = findTrack(startWp, endWp, null, refTrack, false)
            requireNotNull(tt) { "error re-tracking track" }
            return tt
        } finally {
            guideTrack = null
        }
    }


    private fun resetCache(detailed: Boolean) {
        if (nodesCache != null) {
            logger.info("NodesCache status={} before reset", nodesCache!!.formatStatus())
        }
        val maxmem = routingContext.memoryclass * 1024L * 1024L // in MB

        nodesCache = NodesCache(
            routingContext.segmentDir,
            routingContext.way,
            routingContext.global.forceSecondaryData,
            maxmem,
            nodesCache,
            detailed
        )
        islandNodePairs.clearTempPairs()
    }

    private fun getStartPath(
        n1: OsmNode,
        n2: OsmNode?,
        mwp: MatchedWaypoint,
        endPos: OsmNodeNamed?,
        sameSegmentSearch: Boolean
    ): OsmPath? {
        if (endPos != null) {
            endPos.radius = 1.5
        }
        val p = getStartPath(n1, n2, OsmNodeNamed(mwp.crosspoint!!), endPos, sameSegmentSearch)

        // special case: start+end on same segment
        if (p != null && p.cost >= 0 && sameSegmentSearch && endPos != null && endPos.radius < 1.5) {
            p.treedepth = 0 // hack: mark for the final-check
        }
        return p
    }


    private fun getStartPath(
        n1: OsmNode,
        n2: OsmNode?,
        wp: OsmNodeNamed,
        endPos: OsmNodeNamed?,
        sameSegmentSearch: Boolean
    ): OsmPath? {
        try {
            routingContext.setWaypoint(wp, if (sameSegmentSearch) endPos else null, false)
            var bestPath: OsmPath? = null
            var bestLink: OsmLink? = null
            val startLink = OsmLink(null, n1)
            val startPath = routingContext.createPath(startLink)
            startLink.addLinkHolder(startPath, null)
            var minradius = 1e10
            var link = n1.firstlink
            while (link != null) {
                val nextNode = link.getTarget(n1)
                if (nextNode!!.isHollow) {
                    link = link.getNext(n1)
                    continue  // border node?
                }
                if (nextNode.firstlink == null) {
                    link = link.getNext(n1)
                    continue  // don't care about dead ends
                }
                if (nextNode === n1) {
                    link = link.getNext(n1)
                    continue  // ?
                }
                if (nextNode !== n2) {
                    link = link.getNext(n1)
                    continue  // just that link
                }

                wp.radius = 1.5
                val testPath = routingContext.createPath(startPath, link, null, guideTrack != null)
                testPath.airdistance = if (endPos == null) 0 else nextNode.calcDistance(endPos)
                if (wp.radius < minradius) {
                    bestPath = testPath
                    minradius = wp.radius
                    bestLink = link
                }
                link = link.getNext(n1)
            }
            bestLink?.addLinkHolder(bestPath!!, n1)
            if (bestPath != null) bestPath.treedepth = 1

            return bestPath
        } finally {
            routingContext.unsetWaypoint()
        }
    }

    private suspend fun findTrack(
        startWp: MatchedWaypoint?,
        endWp: MatchedWaypoint?,
        costCuttingTrack: OsmTrack?,
        refTrack: OsmTrack?,
        fastPartialRecalc: Boolean
    ): OsmTrack? {
        try {
            val wpts2: MutableList<OsmNode> = ArrayList()
            if (startWp != null) wpts2.add(startWp.waypoint!!)
            if (endWp != null) wpts2.add(endWp.waypoint!!)
            routingContext.cleanNogoList(wpts2)

            val detailed = guideTrack != null
            resetCache(detailed)
            nodesCache!!.nodesMap.cleanupMode =
                if (detailed) 0 else (if (routingContext.global.considerTurnRestrictions) 2 else 1)
            return _findTrack(
                startWp!!,
                endWp,
                costCuttingTrack,
                refTrack,
                fastPartialRecalc
            )
        } finally {
            routingContext.restoreNogoList()
            nodesCache!!.clean(false) // clean only non-virgin caches
        }
    }


    private suspend fun _findTrack(
        startWp: MatchedWaypoint,
        endWp: MatchedWaypoint?,
        costCuttingTrack: OsmTrack?,
        refTrack: OsmTrack?,
        fastPartialRecalc: Boolean
    ): OsmTrack? {
        var fastPartialRecalc = fastPartialRecalc
        val verbose = guideTrack != null

        var maxTotalCost = if (guideTrack != null) guideTrack!!.cost + 5000 else 1000000000
        var firstMatchCost = 1000000000

        logger.info("findtrack with airDistanceCostFactor={}", airDistanceCostFactor)
        if (costCuttingTrack != null) logger.info("costCuttingTrack.cost={}", costCuttingTrack.cost)

        matchPath = null
        var nodesVisited = 0

        val startNodeId1 = startWp.node1!!.idFromPos
        val startNodeId2 = startWp.node2!!.idFromPos
        val endNodeId1 = if (endWp == null) -1L else endWp.node1!!.idFromPos
        val endNodeId2 = if (endWp == null) -1L else endWp.node2!!.idFromPos
        var end1: OsmNode?
        var end2: OsmNode?
        var endPos: OsmNodeNamed? = null

        var sameSegmentSearch = false
        val start1 = nodesCache!!.getGraphNode(startWp.node1!!)
        val start2 = nodesCache!!.getGraphNode(startWp.node2!!)
        if (endWp != null) {
            end1 = nodesCache!!.getGraphNode(endWp.node1!!)
            end2 = nodesCache!!.getGraphNode(endWp.node2!!)
            nodesCache!!.nodesMap.endNode1 = end1
            nodesCache!!.nodesMap.endNode2 = end2
            endPos = OsmNodeNamed(endWp.crosspoint!!)
            sameSegmentSearch =
                (start1 === end1 && start2 === end2) || (start1 === end2 && start2 === end1)
        }
        if (!nodesCache!!.obtainNonHollowNode(start1)) {
            return null
        }
        nodesCache!!.expandHollowLinkTargets(start1)
        if (!nodesCache!!.obtainNonHollowNode(start2)) {
            return null
        }
        nodesCache!!.expandHollowLinkTargets(start2)


        routingContext.startDirectionValid =
            routingContext.forceUseStartDirection || fastPartialRecalc
        routingContext.startDirectionValid =
            routingContext.startDirectionValid and (routingContext.startDirection != null && !routingContext.inverseDirection)
        if (routingContext.startDirectionValid) {
            logger.info("using start direction " + routingContext.startDirection)
        }

        val startPath1 = getStartPath(start1, start2, startWp, endPos, sameSegmentSearch)
        val startPath2 = getStartPath(start2, start1, startWp, endPos, sameSegmentSearch)

        // check for an INITIAL match with the cost-cutting-track
        if (costCuttingTrack != null) {
            val pe1 = costCuttingTrack.getLink(startNodeId1, startNodeId2)
            if (pe1 != null) {
                logger.info("initialMatch pe1.cost={}", pe1.cost)
                var c = startPath1!!.cost - pe1.cost
                if (c < 0) c = 0
                if (c < firstMatchCost) firstMatchCost = c
            }

            val pe2 = costCuttingTrack.getLink(startNodeId2, startNodeId1)
            if (pe2 != null) {
                logger.info("initialMatch pe2.cost={}", pe2.cost)
                var c = startPath2!!.cost - pe2.cost
                if (c < 0) c = 0
                if (c < firstMatchCost) firstMatchCost = c
            }

            if (firstMatchCost < 1000000000) logger.info(
                "firstMatchCost from initial match={}",
                firstMatchCost
            )
        }

        if (startPath1 == null) return null
        if (startPath2 == null) return null

        synchronized(openSet) {
            openSet.clear()
            addToOpenset(startPath1)
            addToOpenset(startPath2)
        }
        val openBorderList: MutableList<OsmPath> = ArrayList(4096)
        var memoryPanicMode = false
        var needNonPanicProcessing = false

        while (true) {
            coroutineContext.ensureActive()

            synchronized(openSet) {
                val path: OsmPath? = openSet.popLowestKeyValue()
                if (path == null) {
                    if (openBorderList.isEmpty()) {
                        break
                    }
                    for (p in openBorderList) {
                        openSet.add(p.cost + (p.airdistance * airDistanceCostFactor).toInt(), p)
                    }
                    openBorderList.clear()
                    memoryPanicMode = false
                    needNonPanicProcessing = true
                    continue
                }

                if (path.airdistance == -1) {
                    continue
                }

                if (directWeaving && nodesCache!!.hasHollowLinkTargets(path.targetNode!!)) {
                    if (!memoryPanicMode) {
                        if (!nodesCache!!.nodesMap.isInMemoryBounds(openSet.size, false)) {
                            val nodesBefore = nodesCache!!.nodesMap.nodesCreated
                            val pathsBefore = openSet.size

                            nodesCache!!.nodesMap.collectOutreachers()
                            while (true) {
                                val p3: OsmPath? = openSet.popLowestKeyValue()
                                if (p3 == null) break
                                if (p3.airdistance != -1 && nodesCache!!.nodesMap.canEscape(p3.targetNode!!)) {
                                    openBorderList.add(p3)
                                }
                            }
                            nodesCache!!.nodesMap.clearTemp()
                            for (p in openBorderList) {
                                openSet.add(
                                    p.cost + (p.airdistance * airDistanceCostFactor).toInt(),
                                    p
                                )
                            }
                            openBorderList.clear()
                            logger.info("collected, nodes/paths before=" + nodesBefore + "/" + pathsBefore + " after=" + nodesCache!!.nodesMap.nodesCreated + "/" + openSet.size + " maxTotalCost=" + maxTotalCost)
                            if (!nodesCache!!.nodesMap.isInMemoryBounds(openSet.size, true)) {
                                require(!(maxTotalCost < 1000000000 || needNonPanicProcessing || fastPartialRecalc)) { "memory limit reached" }
                                memoryPanicMode = true
                                logger.info("************************ memory limit reached, enabled memory panic mode *************************")
                            }
                        }
                    }
                    if (memoryPanicMode) {
                        openBorderList.add(path)
                        continue
                    }
                }
                needNonPanicProcessing = false


                if (fastPartialRecalc && matchPath != null && path.cost > 30L * firstMatchCost && !costCuttingTrack!!.isDirty) {
                    logger.info(
                        "early exit: firstMatchCost={} path.cost={}",
                        firstMatchCost,
                        path.cost
                    )

                    // use an early exit, unless there's a realistc chance to complete within the timeout
//                    if (path.cost > maxTotalCost / 2 && System.currentTimeMillis() - startTime < maxRunningTime / 3) {
                    logger.info("early exit supressed, running for completion, resetting timeout")
//                    startTime = System.currentTimeMillis()
                    fastPartialRecalc = false
//                    } else {
//                        throw IllegalArgumentException("early exit for a close recalc")
//                    }
                }

                if (nodeLimit > 0) { // check node-limit for target island search
                    if (--nodeLimit == 0) {
                        return null
                    }
                }

                nodesVisited++
                linksProcessed++

                val currentLink = path.link!!
                val sourceNode = path.sourceNode!!
                val currentNode = path.targetNode!!

                if (currentLink.isLinkUnused) {
                    continue
                }

                val currentNodeId = currentNode.idFromPos
                val sourceNodeId = sourceNode.idFromPos

                if (!path.didEnterDestinationArea()) {
                    islandNodePairs.addTempPair(sourceNodeId, currentNodeId)
                }

                if (path.treedepth != 1) {
                    if (path.treedepth == 0) { // hack: sameSegment Paths marked treedepth=0 to pass above check
                        path.treedepth = 1
                    }

                    if ((sourceNodeId == endNodeId1 && currentNodeId == endNodeId2)
                        || (sourceNodeId == endNodeId2 && currentNodeId == endNodeId1)
                    ) {
                        // track found, compile
                        logger.info("found track at cost " + path.cost + " nodesVisited = " + nodesVisited)
                        val t = compileTrack(path, verbose)
                        t.showspeed = routingContext.global.showspeed
                        t.showSpeedProfile = routingContext.global.showSpeedProfile
                        return t
                    }

                    // check for a match with the cost-cutting-track
                    if (costCuttingTrack != null) {
                        val pe = costCuttingTrack.getLink(sourceNodeId, currentNodeId)
                        if (pe != null) {
                            // remember first match cost for fast termination of partial recalcs
                            var parentcost =
                                if (path.originElement == null) 0 else path.originElement!!.cost

                            // hitting start-element of costCuttingTrack?
                            val c = path.cost - parentcost - pe.cost
                            if (c > 0) parentcost += c

                            if (parentcost < firstMatchCost) firstMatchCost = parentcost

                            val costEstimate = (path.cost
                                    + path.elevationCorrection()
                                    + (costCuttingTrack.cost - pe.cost))
                            if (costEstimate <= maxTotalCost) {
                                matchPath = OsmPathElement.Companion.create(path)
                            }
                            if (costEstimate < maxTotalCost) {
                                logger.info("maxcost $maxTotalCost -> $costEstimate")
                                maxTotalCost = costEstimate
                            }
                        }
                    }
                }

                val firstLinkHolder = currentLink.getFirstLinkHolder(sourceNode)
                var linkHolder = firstLinkHolder
                while (linkHolder != null) {
                    (linkHolder as OsmPath).airdistance =
                        -1 // invalidate the entry in the open set;
                    linkHolder = linkHolder.nextForLink
                }

                if (path.treedepth > 1) {
                    val isBidir = currentLink.isBidirectional
                    sourceNode.unlinkLink(currentLink)

                    // if the counterlink is alive and does not yet have a path, remove it
                    if (isBidir && currentLink.getFirstLinkHolder(currentNode) == null && !routingContext.global.considerTurnRestrictions) {
                        currentNode.unlinkLink(currentLink)
                    }
                }

                // recheck cutoff before doing expensive stuff
                val addDiff = 100
                if (path.cost + path.airdistance > maxTotalCost + addDiff) {
                    continue
                }

                nodesCache!!.nodesMap.currentMaxCost = maxTotalCost
                nodesCache!!.nodesMap.currentPathCost = path.cost
                nodesCache!!.nodesMap.destination = endPos

                routingContext.firstPrePath = null

                run {
                    var link = currentNode.firstlink
                    while (link != null) {
                        val nextNode = link.getTarget(currentNode)

                        if (!nodesCache!!.obtainNonHollowNode(nextNode!!)) {
                            link = link.getNext(currentNode)
                            continue  // border node?
                        }
                        if (nextNode.firstlink == null) {
                            link = link.getNext(currentNode)
                            continue  // don't care about dead ends
                        }
                        if (nextNode === sourceNode) {
                            link = link.getNext(currentNode)
                            continue  // border node?
                        }

                        val prePath = routingContext.createPrePath(path, link)
                        if (prePath != null) {
                            prePath.next = routingContext.firstPrePath
                            routingContext.firstPrePath = prePath
                        }
                        link = link.getNext(currentNode)
                    }
                }

                var link = currentNode.firstlink
                while (link != null) {
                    val nextNode = link.getTarget(currentNode)

                    if (!nodesCache!!.obtainNonHollowNode(nextNode!!)) {
                        link = link.getNext(currentNode)
                        continue  // border node?
                    }
                    if (nextNode.firstlink == null) {
                        link = link.getNext(currentNode)
                        continue  // don't care about dead ends
                    }
                    if (nextNode === sourceNode) {
                        link = link.getNext(currentNode)
                        continue  // border node?
                    }

                    if (guideTrack != null) {
                        val gidx = path.treedepth + 1
                        if (gidx >= guideTrack!!.nodes.size) {
                            link = link.getNext(currentNode)
                            continue
                        }
                        val guideNode =
                            guideTrack!!.nodes[if (routingContext.global.inverseRouting) guideTrack!!.nodes.size - 1 - gidx else gidx]
                        val nextId = nextNode.idFromPos
                        if (nextId != guideNode.idFromPos) {
                            // not along the guide-track, discard, but register for voice-hint processing
                            if (routingContext.global.turnInstructionMode > 0) {
                                val detour = routingContext.createPath(path, link, refTrack, true)
                                if (detour.cost >= 0.0 && nextId != startNodeId1 && nextId != startNodeId2) {
                                    guideTrack!!.registerDetourForId(
                                        currentNode.idFromPos,
                                        OsmPathElement.Companion.create(detour)
                                    )
                                }
                            }
                            link = link.getNext(currentNode)
                            continue
                        }
                    }

                    var bestPath: OsmPath? = null

                    var isFinalLink = false
                    val targetNodeId = nextNode.idFromPos
                    if (currentNodeId == endNodeId1 || currentNodeId == endNodeId2) {
                        if (targetNodeId == endNodeId1 || targetNodeId == endNodeId2) {
                            isFinalLink = true
                        }
                    }

                    var linkHolder = firstLinkHolder
                    while (linkHolder != null) {
                        val otherPath = linkHolder as OsmPath
                        try {
                            if (isFinalLink) {
                                endPos!!.radius =
                                    1.5 // 1.5 meters is the upper limit that will not change the unit-test result..
                                routingContext.setWaypoint(endPos, true)
                            }
                            val testPath = routingContext.createPath(
                                otherPath,
                                link,
                                refTrack,
                                guideTrack != null
                            )
                            if (testPath.cost >= 0 && (bestPath == null || testPath.cost < bestPath.cost) &&
                                (testPath.sourceNode!!.idFromPos != testPath.targetNode!!.idFromPos)
                            ) {
                                bestPath = testPath
                            }
                        } finally {
                            if (isFinalLink) {
                                routingContext.unsetWaypoint()
                            }
                        }
                        linkHolder = linkHolder.nextForLink
                    }
                    if (bestPath != null) {
                        bestPath.airdistance =
                            if (isFinalLink) 0 else nextNode.calcDistance(endPos!!)

                        val inRadius =
                            boundary == null || boundary!!.isInBoundary(nextNode, bestPath.cost)

                        if (inRadius && (isFinalLink || bestPath.cost + bestPath.airdistance <= (if (lastAirDistanceCostFactor != 0.0) maxTotalCost * lastAirDistanceCostFactor else maxTotalCost.toDouble()) + addDiff)) {
                            // add only if this may beat an existing path for that link
                            var dominator = link.getFirstLinkHolder(currentNode)
                            while (dominator != null) {
                                val dp = dominator as OsmPath
                                if (dp.airdistance != -1 && bestPath.definitlyWorseThan(dp)) {
                                    break
                                }
                                dominator = dominator.nextForLink
                            }

                            if (dominator == null) {
                                bestPath.treedepth = path.treedepth + 1
                                link.addLinkHolder(bestPath, currentNode)
                                addToOpenset(bestPath)
                            }
                        }
                    }
                    link = link.getNext(currentNode)
                }
            }
        }

        if (nodesVisited < MAXNODES_ISLAND_CHECK && islandNodePairs.freezeCount < 5) {
            throw RoutingIslandException()
        }

        return null
    }

    private fun addToOpenset(path: OsmPath) {
        if (path.cost >= 0) {
            openSet.add(path.cost + (path.airdistance * airDistanceCostFactor).toInt(), path)
        }
    }

    private fun compileTrack(path: OsmPath, verbose: Boolean): OsmTrack {
        var element: OsmPathElement? = OsmPathElement.Companion.create(path)

        // for final track, cut endnode
        if (guideTrack != null && element!!.origin != null) {
            element = element.origin
        }

        val totalTime = element!!.time
        val totalEnergy = element.energy

        val track = OsmTrack()
        track.cost = path.cost
        track.energy = path.totalEnergy.toInt()

        var distance = 0

        if (routingContext.global.inverseRouting) -0.25 else 0.25
        while (element != null) {
            if (guideTrack != null && element.message == null) {
                element.message = MessageData()
            }
            val nextElement = element.origin
            // ignore double element
            if (nextElement != null && nextElement.positionEquals(element)) {
                element = nextElement
                continue
            }
            if (routingContext.global.inverseRouting) {
                element.time = totalTime - element.time
                element.energy = totalEnergy - element.energy
                track.nodes.add(element)
            } else {
                track.nodes.add(0, element)
            }

            if (nextElement != null) {
                distance += element.calcDistance(nextElement)
            }
            element = nextElement
        }
        track.distance = distance
        logger.info("track total distance={}", track.distance)
        track.buildMap()

        // for final track..
        if (guideTrack != null) {
            track.copyDetours(guideTrack!!)
        }
        return track
    }

    private fun mergeTrack(match: OsmPathElement, oldTrack: OsmTrack): OsmTrack {
        logger.info("merging match={} with oldTrack={}", match.cost, oldTrack.cost)
        var element: OsmPathElement? = match
        val track = OsmTrack()
        track.cost = oldTrack.cost

        while (element != null) {
            track.addNode(element)
            element = element.origin
        }
        var lastId: Long = 0
        val id1 = match.idFromPos
        val id0 = if (match.origin == null) 0 else match.origin!!.idFromPos
        var appending = false
        for (n in oldTrack.nodes) {
            if (appending) {
                track.nodes.add(n)
            }

            val id = n.idFromPos
            if (id == id1 && lastId == id0) {
                appending = true
            }
            lastId = id
        }


        track.buildMap()
        return track
    }

    val pathPeak: Int
        get() {
            synchronized(openSet) {
                return openSet.peakSize
            }
        }

    fun getOpenSet(): IntArray {
        if (extract == null) {
            extract = arrayOfNulls(500)
        }

        synchronized(openSet) {
            if (guideTrack != null) {
                val nodes = guideTrack!!.nodes
                val res = IntArray(nodes.size * 2)
                var i = 0
                for (n in nodes) {
                    res[i++] = n.iLon
                    res[i++] = n.iLat
                }
                return res
            }
            val size: Int = openSet.getExtract(extract!!)
            val res = IntArray(size * 2)
            var i = 0
            var j = 0
            while (i < size) {
                val p = extract!![i] as OsmPath
                extract!![i] = null
                val n = p.targetNode!!
                res[j++] = n.iLon
                res[j++] = n.iLat
                i++
            }
            return res
        }
    }
}
