/**
 * Container for a track
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.MatchedWaypoint
import dev.skynomads.beerouter.mapaccess.MatchedWaypoint.Companion.readFromStream
import dev.skynomads.beerouter.mapaccess.OsmPos
import dev.skynomads.beerouter.util.CompactLongMap
import dev.skynomads.beerouter.util.FrozenLongMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs

class OsmTrack {
    var endPoint: MatchedWaypoint? = null
    lateinit var nogoChecksums: LongArray
    var profileTimestamp: Long = 0
    var isDirty: Boolean = false

    var showspeed: Boolean = false
    var showSpeedProfile: Boolean = false
    var showTime: Boolean = false

    var params: MutableMap<String, String>? = null

    var pois: MutableList<OsmNodeNamed> = ArrayList()

    class OsmPathElementHolder {
        var node: OsmPathElement? = null
        var nextHolder: OsmPathElementHolder? = null
    }

    var nodes: MutableList<OsmPathElement> = ArrayList()

    private var nodesMap: CompactLongMap<OsmPathElementHolder?>? = null

    private var detourMap: CompactLongMap<OsmPathElementHolder?>? = null

    var voiceHints: VoiceHintList = VoiceHintList()

    var message: String? = null
    var messageList: MutableList<String?>? = null

    var name: String = "unset"

    var matchedWaypoints: MutableList<MatchedWaypoint> = mutableListOf()
    var exportWaypoints: Boolean = false
    var exportCorrectedWaypoints: Boolean = false

    fun addNode(node: OsmPathElement?) {
        nodes.add(0, node!!)
    }

    fun registerDetourForId(id: Long, detour: OsmPathElement?) {
        if (detourMap == null) {
            detourMap = CompactLongMap()
        }
        val nh = OsmPathElementHolder()
        nh.node = detour
        var h = detourMap!!.get(id)
        if (h != null) {
            while (h!!.nextHolder != null) {
                h = h.nextHolder
            }
            h.nextHolder = nh
        } else {
            detourMap!!.fastPut(id, nh)
        }
    }

    fun copyDetours(source: OsmTrack) {
        detourMap =
            if (source.detourMap == null) null else FrozenLongMap<OsmPathElementHolder?>(source.detourMap!!)
    }

    fun addDetours(source: OsmTrack) {
        if (detourMap != null) {
            val tmpDetourMap = CompactLongMap<OsmPathElementHolder?>()

            val oldlist: MutableList<*> = (detourMap as FrozenLongMap<*>).valueList
            val oldidlist = (detourMap as FrozenLongMap<*>).keyArray
            for (i in oldidlist.indices) {
                val id = oldidlist[i]
                val v = detourMap!!.get(id)

                tmpDetourMap.put(id, v)
            }

            if (source.detourMap != null) {
                val idlist = (source.detourMap as FrozenLongMap<*>).keyArray
                for (i in idlist.indices) {
                    val id = idlist[i]
                    val v = source.detourMap!!.get(id)
                    if (!tmpDetourMap.contains(id) && source.nodesMap!!.contains(id)) {
                        tmpDetourMap.put(id, v)
                    }
                }
            }
            detourMap = FrozenLongMap<OsmPathElementHolder?>(tmpDetourMap)
        }
    }

    var lastorigin: OsmPathElement? = null

    fun appendDetours(source: OsmTrack) {
        if (detourMap == null) {
            detourMap =
                if (source.detourMap == null) null else CompactLongMap()
        }
        if (source.detourMap != null) {
            val pos = nodes.size - source.nodes.size + 1
            var origin: OsmPathElement? = null
            if (pos > 0) origin = nodes[pos]
            for (node in source.nodes) {
                val id = node.idFromPos
                val nh = OsmPathElementHolder()
                if (node.origin == null && lastorigin != null) node.origin = lastorigin
                nh.node = node
                lastorigin = node
                var h = detourMap!!.get(id)
                if (h != null) {
                    while (h!!.nextHolder != null) {
                        h = h.nextHolder
                    }
                    h.nextHolder = nh
                } else {
                    detourMap!!.fastPut(id, nh)
                }
            }
        }
    }

    fun buildMap() {
        nodesMap = CompactLongMap()
        for (node in nodes) {
            val id = node.idFromPos
            val nh = OsmPathElementHolder()
            nh.node = node
            var h = nodesMap!!.get(id)
            if (h != null) {
                while (h!!.nextHolder != null) {
                    h = h.nextHolder
                }
                h.nextHolder = nh
            } else {
                nodesMap!!.fastPut(id, nh)
            }
        }
        nodesMap = FrozenLongMap<OsmPathElementHolder?>(nodesMap!!)
    }

    fun aggregateMessages(): MutableList<String?> {
        val res: MutableList<String?> = ArrayList()
        var current: MessageData? = null
        for (n in nodes) {
            if (n.message != null && n.message!!.wayKeyValues != null) {
                val md = n.message!!.copy()
                if (current != null) {
                    if (current.nodeKeyValues != null || current.wayKeyValues != md!!.wayKeyValues) {
                        res.add(current.toMessage())
                    } else {
                        md.add(current)
                    }
                }
                current = md
            }
        }
        if (current != null) {
            res.add(current.toMessage())
        }
        return res
    }

    fun aggregateSpeedProfile(): MutableList<String?> {
        val res: MutableList<String?> = ArrayList()
        var vmax = -1
        var vmaxe = -1
        var vmin = -1
        var extraTime = 0
        for (i in nodes.size - 1 downTo 1) {
            val n = nodes[i]
            val m = n.message
            val vnode = getVNode(i)
            if (m != null && (vmax != m.vmax || vmin != m.vmin || vmaxe != m.vmaxExplicit || vnode < m.vmax || extraTime != m.extraTime)) {
                vmax = m.vmax
                vmin = m.vmin
                vmaxe = m.vmaxExplicit
                extraTime = m.extraTime
                res.add("$i,$vmaxe,$vmax,$vmin,$vnode,$extraTime")
            }
        }
        return res
    }


    /**
     * writes the track in binary-format to a file
     *
     * @param filename the filename to write to
     */
    @Throws(Exception::class)
    fun writeBinary(filename: String) {
        val dos = DataOutputStream(BufferedOutputStream(FileOutputStream(filename)))

        endPoint!!.writeToStream(dos)
        dos.writeInt(nodes.size)
        for (node in nodes) {
            node.writeToStream(dos)
        }
        dos.writeLong(nogoChecksums[0])
        dos.writeLong(nogoChecksums[1])
        dos.writeLong(nogoChecksums[2])
        dos.writeBoolean(isDirty)
        dos.writeLong(profileTimestamp)
        dos.close()
    }

    fun addNodes(t: OsmTrack) {
        for (n in t.nodes) addNode(n)
        buildMap()
    }

    fun containsNode(node: OsmPos): Boolean {
        return nodesMap!!.contains(node.idFromPos)
    }

    fun getLink(n1: Long, n2: Long): OsmPathElement? {
        var h = nodesMap!!.get(n2)
        while (h != null) {
            val e1 = h.node!!.origin
            if (e1 != null && e1.idFromPos == n1) {
                return h.node
            }
            h = h.nextHolder
        }
        return null
    }

    fun appendTrack(t: OsmTrack) {
        var i: Int

        val ourSize = nodes.size
        if (ourSize > 0 && t.nodes.size > 1) {
            val olde = nodes[ourSize - 1]
            t.nodes[1].origin = olde
        }
        val t0 = if (ourSize > 0) nodes[ourSize - 1].time else 0f
        val e0 = if (ourSize > 0) nodes[ourSize - 1].energy else 0f
        val c0 = if (ourSize > 0) nodes[ourSize - 1].cost else 0
        i = 0
        while (i < t.nodes.size) {
            val e = t.nodes[i]
            if (i == 0 && ourSize > 0 && nodes[ourSize - 1].sElev == Short.Companion.MIN_VALUE) nodes[ourSize - 1].sElev =
                e.sElev
            if (i > 0 || ourSize == 0) {
                e.time = e.time + t0
                e.energy = e.energy + e0
                e.cost = e.cost + c0
                if (e.message != null) {
                    if (!(e.message!!.lon == e.iLon && e.message!!.lat == e.iLat)) {
                        e.message!!.lon = e.iLon
                        e.message!!.lat = e.iLat
                    }
                }
                nodes.add(e)
            }
            i++
        }

        if (t.voiceHints != null) {
            if (ourSize > 0) {
                for (hint in t.voiceHints.list) {
                    hint.indexInTrack = hint.indexInTrack + ourSize - 1
                }
            }
            if (voiceHints == null) {
                voiceHints = t.voiceHints
            } else {
                voiceHints.list.addAll(t.voiceHints.list)
            }
        } else {
            if (detourMap == null) {
                //copyDetours( t );
                detourMap = t.detourMap
            } else {
                addDetours(t)
            }
        }

        distance += t.distance
        ascend += t.ascend
        plainAscend += t.plainAscend
        cost += t.cost
        energy = nodes[nodes.size - 1].energy.toInt()

        showspeed = showspeed or t.showspeed
        showSpeedProfile = showSpeedProfile or t.showSpeedProfile
    }

    var distance: Int = 0
    var ascend: Int = 0
    var plainAscend: Int = 0
    var cost: Int = 0
    var energy: Int = 0
    var iternity: MutableList<String?>? = null

    fun getVoiceHint(i: Int): VoiceHint? {
        if (voiceHints == null) return null
        for (hint in voiceHints.list) {
            if (hint.indexInTrack == i) {
                return hint
            }
        }
        return null
    }

    fun getMatchedWaypoint(idx: Int): MatchedWaypoint? {
        if (matchedWaypoints == null) return null
        for (wp in matchedWaypoints) {
            if (idx == wp.indexInTrack) {
                return wp
            }
        }
        return null
    }

    private fun getVNode(i: Int): Int {
        val m1 = if (i + 1 < nodes.size) nodes[i + 1].message else null
        val m0 = if (i < nodes.size) nodes[i].message else null
        val vnode0 = m1?.vnode0 ?: 999
        val vnode1 = m0?.vnode1 ?: 999
        return if (vnode0 < vnode1) vnode0 else vnode1
    }

    val totalSeconds: Int
        get() {
            val s =
                if (nodes.size < 2) 0f else nodes[nodes.size - 1].time - nodes[0].time
            return (s + 0.5).toInt()
        }

    fun equalsTrack(t: OsmTrack): Boolean {
        if (nodes.size != t.nodes.size) return false
        for (i in nodes.indices) {
            val e1 = nodes[i]
            val e2 = t.nodes[i]
            if (e1.iLon != e2.iLon || e1.iLat != e2.iLat) return false
        }
        return true
    }

    fun getFromDetourMap(id: Long): OsmPathElementHolder? {
        if (detourMap == null) return null
        return detourMap!!.get(id)
    }

    fun prepareSpeedProfile(rc: RoutingContext?) {
        // sendSpeedProfile = rc.keyValues != null && rc.keyValues.containsKey( "vmax" );
    }

    fun processVoiceHints(rc: RoutingContext) {
        voiceHints = VoiceHintList()
        voiceHints.setTransportMode(rc.carMode, rc.bikeMode)
        voiceHints.turnInstructionMode = rc.turnInstructionMode

        if (detourMap == null && !rc.hasDirectRouting) {
            // only when no direct way points
            return
        }
        var nodeNr = nodes.size - 1
        var i = nodeNr
        var node = nodes[nodeNr]
        while (node != null) {
            node = node.origin!!
        }

        i = 0

        node = nodes[nodeNr]
        val inputs: MutableList<VoiceHint> = ArrayList()
        while (node != null) {
            if (node.origin != null) {
                if (nodeNr == nodes.size - 1) {
                    val input = VoiceHint()
                    inputs.add(0, input)
                    input.ilat = node.iLat
                    input.ilon = node.iLon
                    input.selev = node.sElev
                    input.goodWay = node.message
                    input.oldWay = node.message
                    input.indexInTrack = nodes.size - 1
                    input.command = VoiceHint.Companion.END
                }
                val input = VoiceHint()
                inputs.add(input)
                input.ilat = node.origin!!.iLat
                input.ilon = node.origin!!.iLon
                input.selev = node.origin!!.sElev
                input.indexInTrack = --nodeNr
                input.goodWay = node.message
                input.oldWay =
                    if (node.origin!!.message == null) node.message else node.origin!!.message
                if (rc.turnInstructionMode == 8 || rc.turnInstructionMode == 4 || rc.turnInstructionMode == 2 || rc.turnInstructionMode == 9) {
                    val mwpt = getMatchedWaypoint(nodeNr)
                    if (mwpt != null && mwpt.direct) {
                        input.command = VoiceHint.Companion.BL
                        input.angle =
                            (if (nodeNr == 0) node.origin!!.message!!.turnangle else node.message!!.turnangle)
                        input.distanceToNext = node.calcDistance(node.origin!!).toDouble()
                    }
                }
                if (detourMap != null) {
                    val detours = detourMap!!.get(node.origin!!.idFromPos)
                    if (nodeNr >= 0 && detours != null) {
                        var h: OsmPathElementHolder? = detours
                        while (h != null) {
                            val e = h.node
                            input.addBadWay(startSection(e, node.origin!!))
                            h = h.nextHolder
                        }
                    }
                }
                /* else if (nodeNr == 0 && detours != null) {
          OsmPathElementHolder h = detours;
          OsmPathElement e = h.node;
          input.addBadWay(startSection(e, e));
        } */
            }
            node = node.origin!!
        }

        val transportMode = voiceHints.transportMode()
        val vproc = VoiceHintProcessor(
            rc.turnInstructionCatchingRange,
            rc.turnInstructionRoundabouts,
            transportMode
        )
        val results = vproc.process(inputs)

        val minDistance = this.minDistance.toDouble()
        val resultsLast = vproc.postProcess(results, rc.turnInstructionCatchingRange, minDistance)
        for (hint in resultsLast) {
            voiceHints.list.add(hint)
        }
    }

    val minDistance: Int
        get() {
            if (voiceHints != null) {
                return when (voiceHints.transportMode()) {
                    VoiceHintList.Companion.TRANS_MODE_CAR -> 20
                    VoiceHintList.Companion.TRANS_MODE_FOOT -> 3
                    VoiceHintList.Companion.TRANS_MODE_BIKE -> 5
                    else -> 5
                }
            }
            return 2
        }

    fun getVoiceHintTime(i: Int): Float {
        if (voiceHints.list.isEmpty()) {
            return 0f
        }
        if (i < voiceHints.list.size) {
            return voiceHints.list[i].time
        }
        if (nodes.isEmpty()) {
            return 0f
        }
        return nodes[nodes.size - 1].time
    }

    fun removeVoiceHint(i: Int) {
        if (voiceHints != null) {
            var remove: VoiceHint? = null
            for (vh in voiceHints.list) {
                if (vh.indexInTrack == i) remove = vh
            }
            if (remove != null) voiceHints.list.remove(remove)
        }
    }

    private fun startSection(element: OsmPathElement?, root: OsmPathElement): MessageData? {
        var e = element
        var cnt = 0
        while (e != null && e.origin != null) {
            if (e.origin!!.iLat == root.iLat && e.origin!!.iLon == root.iLon) {
                return e.message
            }
            e = e.origin
            require(cnt++ != 1000000) { "ups: $root->$element" }
        }
        return null
    }

    companion object {
        const val version: String = "1.7.8"
        const val versionDate: String = "12072025"

        // csv-header-line
        private const val MESSAGES_HEADER =
            "Longitude\tLatitude\tElevation\tDistance\tCostPerKm\tElevCost\tTurnCost\tNodeCost\tInitialCost\tWayTags\tNodeTags\tTime\tEnergy"

        fun readBinary(
            filename: String?,
            newEp: OsmNodeNamed,
            nogoChecksums: LongArray,
            profileChecksum: Long,
            debugInfo: StringBuilder?
        ): OsmTrack? {
            var t: OsmTrack? = null
            if (filename != null) {
                val f = File(filename)
                if (f.exists()) {
                    try {
                        val dis = DataInputStream(BufferedInputStream(FileInputStream(f)))
                        val ep = readFromStream(dis)
                        val dlon: Int = ep.waypoint!!.iLon - newEp.iLon
                        val dlat = ep.waypoint!!.iLat - newEp.iLat
                        val targetMatch = dlon < 20 && dlon > -20 && dlat < 20 && dlat > -20
                        debugInfo?.append("target-delta = $dlon/$dlat targetMatch=$targetMatch")
                        if (targetMatch) {
                            t = OsmTrack()
                            t.endPoint = ep
                            val n = dis.readInt()
                            var last_pe: OsmPathElement? = null
                            for (i in 0..<n) {
                                val pe: OsmPathElement =
                                    OsmPathElement.Companion.readFromStream(dis)
                                pe.origin = last_pe
                                last_pe = pe
                                t.nodes.add(pe)
                            }
                            t.cost = last_pe!!.cost
                            t.buildMap()

                            // check cheecksums, too
                            val al = LongArray(3)
                            var pchecksum: Long = 0
                            try {
                                al[0] = dis.readLong()
                                al[1] = dis.readLong()
                                al[2] = dis.readLong()
                            } catch (eof: EOFException) { /* kind of expected */
                            }
                            try {
                                t.isDirty = dis.readBoolean()
                            } catch (eof: EOFException) { /* kind of expected */
                            }
                            try {
                                pchecksum = dis.readLong()
                            } catch (eof: EOFException) { /* kind of expected */
                            }
                            val nogoCheckOk =
                                abs(al[0] - nogoChecksums[0]) <= 20 && abs(al[1] - nogoChecksums[1]) <= 20 && abs(
                                    al[2] - nogoChecksums[2]
                                ) <= 20
                            val profileCheckOk = pchecksum == profileChecksum

                            if (debugInfo != null) {
                                debugInfo.append(" nogoCheckOk=$nogoCheckOk profileCheckOk=$profileCheckOk")
                                debugInfo.append(
                                    " al=" + formatLongs(al) + " nogoChecksums=" + formatLongs(
                                        nogoChecksums
                                    )
                                )
                            }
                            if (!(nogoCheckOk && profileCheckOk)) return null
                        }
                        dis.close()
                    } catch (e: Exception) {
                        debugInfo?.append("Error reading rawTrack: $e")
                    }
                }
            }
            return t
        }

        private fun formatLongs(al: LongArray): String {
            val sb = StringBuilder()
            sb.append('{')
            for (l in al) {
                sb.append(l)
                sb.append(' ')
            }
            sb.append('}')
            return sb.toString()
        }
    }
}
