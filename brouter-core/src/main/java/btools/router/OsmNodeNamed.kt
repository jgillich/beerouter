/**
 * Container for an osm node
 *
 * @author ab
 */
package btools.router

import btools.mapaccess.OsmNode
import btools.util.CheapRuler.distance
import btools.util.CheapRuler.getLonLatToMeterScales
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.collections.minus
import kotlin.collections.plus
import kotlin.div
import kotlin.math.sqrt
import kotlin.plus
import kotlin.sequences.minus
import kotlin.sequences.plus
import kotlin.text.indexOf
import kotlin.text.plus
import kotlin.text.substring
import kotlin.text.toDouble
import kotlin.text.toInt
import kotlin.times

open class OsmNodeNamed : OsmNode {
    @JvmField
    var name: String? = null

    @JvmField
    var radius: Double = 0.0 // radius of nogopoint (in meters)
    var nogoWeight: Double = 0.0 // weight for nogopoint
    var isNogo: Boolean = false

    @JvmField
    var direct: Boolean = false // mark direct routing

    constructor()

    constructor(n: OsmNode) : super(n.iLon, n.iLat)

    override fun toString(): String {
        return if (nogoWeight.isNaN()) "$iLon,$iLat,$name"
        else "$iLon,$iLat,$name,$nogoWeight"
    }

    fun distanceWithinRadius(
        lon1: Int,
        lat1: Int,
        lon2: Int,
        lat2: Int,
        totalSegmentLength: kotlin.Double
    ): kotlin.Double {
        var lon1 = lon1
        var lat1 = lat1
        var lon2 = lon2
        var lat2 = lat2
        val lonlat2m = getLonLatToMeterScales((lat1 + lat2) shr 1)

        var isFirstPointWithinCircle = distance(lon1, lat1, iLon, iLat) < radius
        var isLastPointWithinCircle = distance(lon2, lat2, iLon, iLat) < radius
        // First point is within the circle
        if (isFirstPointWithinCircle) {
            // Last point is within the circle
            if (isLastPointWithinCircle) {
                return totalSegmentLength
            }
            // Last point is not within the circle
            // Just swap points and go on with first first point not within the
            // circle now.
            // Swap longitudes
            var tmp = lon2
            lon2 = lon1
            lon1 = tmp
            // Swap latitudes
            tmp = lat2
            lat2 = lat1
            lat1 = tmp
            // Fix boolean values
            isLastPointWithinCircle = isFirstPointWithinCircle
            isFirstPointWithinCircle = false
        }
        // Distance between the initial point and projection of center of
        // the circle on the current segment.
        val initialToProject: kotlin.Double =
            (((lon2 - lon1) * (iLon - lon1) * lonlat2m!![0] * lonlat2m[0]
                    + (lat2 - lat1) * (iLat - lat1) * lonlat2m[1] * lonlat2m[1]
                    ) / totalSegmentLength)
        // Distance between the initial point and the center of the circle.
        val initialToCenter = distance(iLon, iLat, lon1, lat1)
        // Half length of the segment within the circle
        val halfDistanceWithin = sqrt(
            radius * radius - (initialToCenter * initialToCenter -
                    initialToProject * initialToProject
                    )
        )
        // Last point is within the circle
        if (isLastPointWithinCircle) {
            return halfDistanceWithin + (totalSegmentLength - initialToProject)
        }
        return 2 * halfDistanceWithin
    }

    companion object {
        fun decodeNogo(s: String): OsmNodeNamed {
            val n = OsmNodeNamed()
            val idx1 = s.indexOf(',')
            n.iLon = s.substring(0, idx1).toInt()
            val idx2 = s.indexOf(',', idx1 + 1)
            n.iLat = s.substring(idx1 + 1, idx2).toInt()
            val idx3 = s.indexOf(',', idx2 + 1)
            if (idx3 == -1) {
                n.name = s.substring(idx2 + 1)
                n.nogoWeight = kotlin.Double.Companion.NaN
            } else {
                n.name = s.substring(idx2 + 1, idx3)
                n.nogoWeight = s.substring(idx3 + 1).toDouble()
            }
            n.isNogo = true
            return n
        }
    }
}
