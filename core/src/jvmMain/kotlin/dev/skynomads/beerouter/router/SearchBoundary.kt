/**
 * static helper class for handling datafiles
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.OsmNode

internal class SearchBoundary(n: OsmNode, private val radius: Int, var direction: Int) {
    private val minlon0: Int
    private val minlat0: Int
    private val maxlon0: Int
    private val maxlat0: Int

    private val minlon: Int
    private val minlat: Int
    private val maxlon: Int
    private val maxlat: Int
    private val p: OsmNode = OsmNode(n.iLon, n.iLat)

    /**
     * @param radius Search radius in meters.
     */
    init {

        val lon: Int = (n.iLon / 5000000) * 5000000
        val lat = (n.iLat / 5000000) * 5000000

        minlon0 = lon - 5000000
        minlat0 = lat - 5000000
        maxlon0 = lon + 10000000
        maxlat0 = lat + 10000000

        minlon = lon - 1000000
        minlat = lat - 1000000
        maxlon = lon + 6000000
        maxlat = lat + 6000000
    }

    fun isInBoundary(n: OsmNode, cost: Int): Boolean {
        if (radius > 0) {
            return n.calcDistance(p) < radius
        }
        if (cost == 0) {
            return n.iLon in (minlon0 + 1)..<maxlon0 && n.iLat > minlat0 && n.iLat < maxlat0
        }
        return n.iLon in (minlon + 1)..<maxlon && n.iLat > minlat && n.iLat < maxlat
    }

    fun getBoundaryDistance(n: OsmNode): Int {
        return when (direction) {
            0 -> n.calcDistance(OsmNode(n.iLon, minlat))
            1 -> n.calcDistance(OsmNode(minlon, n.iLat))
            2 -> n.calcDistance(OsmNode(n.iLon, maxlat))
            3 -> n.calcDistance(OsmNode(maxlon, n.iLat))
            else -> throw IllegalArgumentException("undefined direction: $direction")
        }
    }

    companion object {
        fun getFileName(n: OsmNode): String {
            val lon: Int = (n.iLon / 5000000) * 5000000
            val lat = (n.iLat / 5000000) * 5000000

            val dlon = lon / 1000000 - 180
            val dlat = lat / 1000000 - 90

            val slon = if (dlon < 0) "W" + (-dlon) else "E$dlon"
            val slat = if (dlat < 0) "S" + (-dlat) else "N$dlat"
            return slon + "_" + slat + ".trf"
        }
    }
}
