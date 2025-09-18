package btools.router

import btools.util.CheapRuler
import btools.util.CheapRuler.destination
import btools.util.CheapRuler.distance
import org.junit.Assert
import org.junit.Test

class OsmNodeNamedTest {
    @Test
    fun testDistanceWithinRadius() {
        // Segment ends
        var lon1: Int
        var lat1: Int
        var lon2: Int
        var lat2: Int
        // Circle definition
        val node = OsmNodeNamed()
        // Center
        node.iLon = toOsmLon(2.334243)
        node.iLat = toOsmLat(48.824017)
        // Radius
        node.radius = 30.0

        // Check distance within radius is correctly computed if the segment passes through the center
        lon1 = toOsmLon(2.332559)
        lat1 = toOsmLat(48.823822)
        lon2 = toOsmLon(2.335018)
        lat2 = toOsmLat(48.824105)
        var totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        Assert.assertEquals(
            "Works for segment aligned with the nogo center",
            2 * node.radius,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.01 * (2 * node.radius)
        )

        // Check distance within radius is correctly computed for a given circle
        node.iLon = toOsmLon(2.33438)
        node.iLat = toOsmLat(48.824275)
        Assert.assertEquals(
            "Works for a segment with no particular properties",
            27.5,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 27.5
        )

        // Check distance within radius is the same if we reverse start and end point
        Assert.assertEquals(
            "Works if we switch firs and last point",
            node.distanceWithinRadius(lon2, lat2, lon1, lat1, totalSegmentLength),
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.01
        )

        // Check distance within radius is correctly computed if a point is inside the circle
        lon2 = toOsmLon(2.334495)
        lat2 = toOsmLat(48.824045)
        totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        Assert.assertEquals(
            "Works if last point is within the circle",
            17.0,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 17
        )

        lon1 = toOsmLon(2.334495)
        lat1 = toOsmLat(48.824045)
        lon2 = toOsmLon(2.335018)
        lat2 = toOsmLat(48.824105)
        totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        Assert.assertEquals(
            "Works if first point is within the circle",
            9.0,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 9
        )

        lon1 = toOsmLon(2.33427)
        lat1 = toOsmLat(48.82402)
        lon2 = toOsmLon(2.334587)
        lat2 = toOsmLat(48.824061)
        totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        Assert.assertEquals(
            "Works if both points are within the circle",
            25.0,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 25
        )

        // Check distance within radius is correctly computed if both points are on
        // the same side of the center.
        // Note: the only such case possible is with one point outside and one
        // point within the circle, as we expect the segment to have a non-empty
        // intersection with the circle.
        lon1 = toOsmLon(2.332559)
        lat1 = toOsmLat(48.823822)
        lon2 = toOsmLon(2.33431)
        lat2 = toOsmLat(48.824027)
        totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        Assert.assertEquals(
            "Works if both points are on the same side of the circle center",
            5.0,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 5
        )
    }

    @Test
    fun testDestination() {
        // Segment ends
        var lon1: Int
        var lat1: Int
        var lon2: Int
        var lat2: Int
        // Circle definition
        val node = OsmNodeNamed()
        // Center
        node.iLon = toOsmLon(0.0)
        node.iLat = toOsmLat(0.0)
        val startDist = 1000.0

        var i = 0
        while (i <= 360) {
            val pos = destination(node.iLon, node.iLat, startDist, i.toDouble())
            val dist = distance(node.iLon, node.iLat, pos[0], pos[1])
            Assert.assertTrue(
                "pos " + pos[0] + " " + pos[1] + " distance (" + dist + ") should be around (" + startDist + ")",
                dist - 1 < startDist && dist + 1 > startDist
            )
            i += 45
        }
    }

    companion object {
        fun toOsmLon(lon: Double): Int {
            return ((lon + 180.0) / CheapRuler.ILATLNG_TO_LATLNG + 0.5).toInt()
        }

        fun toOsmLat(lat: Double): Int {
            return ((lat + 90.0) / CheapRuler.ILATLNG_TO_LATLNG + 0.5).toInt()
        }
    }
}
