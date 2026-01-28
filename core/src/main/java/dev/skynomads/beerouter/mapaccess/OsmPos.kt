/**
 * Interface for a position (OsmNode or OsmPath)
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import org.maplibre.spatialk.geojson.Position


interface OsmPos {
    val position: Position

    val sElev: Short

    val elev: Double

    fun calcDistance(p: OsmPos): Int

    val idFromPos: Long

    @Deprecated("use position instead")
    val iLat: Int
        get() = position.latitude.toIntLatitude()

    @Deprecated("use position instead")
    val iLon: Int
        get() = position.longitude.toIntLongitude()
}
