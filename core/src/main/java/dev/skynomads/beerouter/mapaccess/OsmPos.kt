/**
 * Interface for a position (OsmNode or OsmPath)
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess


interface OsmPos {
    val iLat: Int

    val iLon: Int

    val sElev: Short

    val elev: Double

    fun calcDistance(p: OsmPos): Int

    val idFromPos: Long
}
