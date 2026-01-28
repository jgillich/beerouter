package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.codec.MicroCache
import dev.skynomads.beerouter.codec.MicroCache2
import dev.skynomads.beerouter.osm.toDoubleLatitude
import dev.skynomads.beerouter.osm.toDoubleLongitude
import dev.skynomads.beerouter.util.CheapRuler.distance
import dev.skynomads.beerouter.util.IByteArrayUnifier
import org.maplibre.spatialk.geojson.Position
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Container for an osm node
 *
 * @author ab
 */
open class OsmNode : OsmLink, OsmPos {
    /**
     * The position
     */
    override var position: Position =
        Position(0.0, 0.0)

    /**
     * The elevation
     */
    override var sElev: Short = Short.MIN_VALUE

    override val elev: Double
        get() = sElev / 4.0

    /**
     * The node-tags, if any
     */
    var nodeDescription: ByteArray? = null

    var firstRestriction: TurnRestriction? = null

    var visitID: Int = 0

    fun addTurnRestriction(tr: TurnRestriction) {
        tr.next = firstRestriction
        firstRestriction = tr
    }

    /**
     * The links to other nodes
     */
    var firstlink: OsmLink? = null

    constructor()

    constructor(ilon: Int, ilat: Int) {
        this.position = Position(
            ilon.toDoubleLongitude(),
            ilat.toDoubleLatitude()
        )
    }

    constructor(id: Long) {
        val lon = (id shr 32).toInt()
        val lat = (id and 0xffffffffL).toInt()
        this.position = Position(
            lon.toDoubleLongitude(),
            lat.toDoubleLatitude()
        )
    }

    constructor(position: Position) {
        this.position = position
    }


    fun addLink(link: OsmLink, isReverse: Boolean, tn: OsmNode) {
        require(link !== firstlink) { "UUUUPS" }

        if (isReverse) {
            link.n1 = tn
            link.n2 = this
            link.next = tn.firstlink
            link.previous = firstlink
            tn.firstlink = link
            firstlink = link
        } else {
            link.n1 = this
            link.n2 = tn
            link.next = firstlink
            link.previous = tn.firstlink
            tn.firstlink = link
            firstlink = link
        }
    }

    override fun calcDistance(p: OsmPos): Int {
        return max(
            1,
            distance(iLon, iLat, p.iLon, p.iLat).roundToInt()
        )
    }

    override fun toString(): String {
        return "n_" + (iLon - 180000000) + "_" + (iLat - 90000000)
    }

    fun parseNodeBody(mc: MicroCache, hollowNodes: OsmNodesMap, expCtxWay: IByteArrayUnifier) {
        if (mc is MicroCache2) {
            parseNodeBody2(mc, hollowNodes, expCtxWay)
        } else throw IllegalArgumentException("unknown cache version: " + mc.javaClass)
    }

    fun parseNodeBody2(mc: MicroCache2, hollowNodes: OsmNodesMap, expCtxWay: IByteArrayUnifier) {
        val abUnifier = hollowNodes.byteArrayUnifier

        // read turn restrictions
        while (mc.readBoolean()) {
            val tr = TurnRestriction()
            tr.exceptions = mc.readShort()
            tr.isPositive = mc.readBoolean()
            tr.fromLon = mc.readInt()
            tr.fromLat = mc.readInt()
            tr.toLon = mc.readInt()
            tr.toLat = mc.readInt()
            addTurnRestriction(tr)
        }

        sElev = mc.readShort()
        val nodeDescSize = mc.readVarLengthUnsigned()
        nodeDescription = if (nodeDescSize == 0) null else mc.readUnified(nodeDescSize, abUnifier)

        while (mc.hasMoreData()) {
            // read link data
            val endPointer = mc.endPointer
            val linklon = iLon + mc.readVarLengthSigned()
            val linklat = iLat + mc.readVarLengthSigned()
            val sizecode = mc.readVarLengthUnsigned()
            val isReverse = (sizecode and 1) != 0
            var description: ByteArray? = null
            val descSize = sizecode shr 1
            if (descSize > 0) {
                description = mc.readUnified(descSize, expCtxWay)
            }
            val geometry = mc.readDataUntil(endPointer)

            addLink(linklon, linklat, description, geometry, hollowNodes, isReverse)
        }
        hollowNodes.remove(this)
    }

    fun addLink(
        linklon: Int,
        linklat: Int,
        description: ByteArray?,
        geometry: ByteArray?,
        hollowNodes: OsmNodesMap,
        isReverse: Boolean
    ) {
        if (linklon == iLon && linklat == iLat) {
            return  // skip self-ref
        }

        var tn: OsmNode? = null // find the target node
        var link: OsmLink? = null

        // ...in our known links
        var l = firstlink
        while (l != null) {
            val t = l.getTarget(this)
            if (t!!.iLon == linklon && t.iLat == linklat) {
                tn = t
                if (isReverse || (l.descriptionBitmap == null && !l.isReverse(this))) {
                    link = l // the correct one that needs our data
                    break
                }
            }
            l = l.getNext(this)
        }
        if (tn == null) { // .. not found, then check the hollow nodes
            tn = hollowNodes.get(linklon, linklat) // target node
            if (tn == null) { // node not yet known, create a new hollow proxy
                tn = OsmNode(linklon, linklat)
                tn.setHollow()
                hollowNodes.put(tn)
                addLink(
                    tn.also { link = it },
                    isReverse,
                    tn
                ) // technical inheritance: link instance in node
            }
        }
        if (link == null) {
            addLink(OsmLink().also { link = it }, isReverse, tn)
        }
        if (!isReverse) {
            link!!.descriptionBitmap = description
            link.geometry = geometry
        }
    }


    val isHollow: Boolean
        get() = sElev.toInt() == -12345

    fun setHollow() {
        sElev = -12345
    }

    override val idFromPos: Long
        get() = (iLon.toLong()) shl 32 or iLat.toLong()

    fun vanish() {
        if (!this.isHollow) {
            var l = firstlink
            while (l != null) {
                val target = l.getTarget(this)
                val nextLink = l.getNext(this)
                if (!target!!.isHollow) {
                    unlinkLink(l)
                    if (!l.isLinkUnused) {
                        target.unlinkLink(l)
                    }
                }
                l = nextLink
            }
        }
    }

    fun unlinkLink(link: OsmLink) {
        val n = link.clear(this)

        if (link === firstlink) {
            firstlink = n
            return
        }
        var l = firstlink
        while (l != null) {
            // if ( l.isReverse( this ) )
            if (l.n1 !== this && l.n1 != null) { // isReverse inline
                val nl = l.previous
                if (nl === link) {
                    l.previous = n
                    return
                }
                l = nl
            } else if (l.n2 !== this && l.n2 != null) {
                val nl = l.next
                if (nl === link) {
                    l.next = n
                    return
                }
                l = nl
            } else {
                throw IllegalArgumentException("unlinkLink: unknown source")
            }
        }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OsmNode) return false
        return other.iLon == iLon && other.iLat == iLat
    }

    override fun hashCode(): Int {
        return iLon + iLat
    }
}
