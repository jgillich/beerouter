/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.util.ByteDataReader

class GeometryDecoder {
    private val r = ByteDataReader()
    private val cachedNodes: Array<OsmTransferNode>
    private val nCachedNodes = 128

    // result-cache
    private var firstTransferNode: OsmTransferNode? = null
    private var lastReverse = false
    private var lastGeometry: ByteArray = ByteArray(0)

    init {
        // create some caches
        cachedNodes = (0 until nCachedNodes).map { OsmTransferNode() }.toTypedArray()
    }

    fun decodeGeometry(
        geometry: ByteArray,
        sourceNode: OsmNode?,
        targetNode: OsmNode,
        reverseLink: Boolean
    ): OsmTransferNode? {
        if ((lastGeometry.contentEquals(geometry)) && (lastReverse == reverseLink)) {
            return firstTransferNode
        }

        firstTransferNode = null
        var lastTransferNode: OsmTransferNode? = null
        val startnode: OsmNode = (if (reverseLink) targetNode else sourceNode)!!
        r.reset(geometry)
        var olon = startnode.iLon
        var olat = startnode.iLat
        var oselev = startnode.sElev.toInt()
        var idx = 0
        while (r.hasMoreData()) {
            val trans = if (idx < nCachedNodes) cachedNodes[idx++] else OsmTransferNode()
            trans.ilon = olon + r.readVarLengthSigned()
            trans.ilat = olat + r.readVarLengthSigned()
            trans.selev = (oselev + r.readVarLengthSigned()).toShort()
            olon = trans.ilon
            olat = trans.ilat
            oselev = trans.selev.toInt()
            if (reverseLink) { // reverse chaining
                trans.next = firstTransferNode
                firstTransferNode = trans
            } else {
                trans.next = null
                if (lastTransferNode == null) {
                    firstTransferNode = trans
                } else {
                    lastTransferNode.next = trans
                }
                lastTransferNode = trans
            }
        }

        lastReverse = reverseLink
        lastGeometry = geometry

        return firstTransferNode
    }
}
