package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import dev.skynomads.beerouter.util.CheapRuler.destination
import dev.skynomads.beerouter.util.CheapRuler.distance
import org.junit.Assert
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

class OsmNodeNamedTest {
    @Test
    fun testDistanceWithinRadius() {
        var lat1 = 0
        var lat2 = 0
        var lon1 = 0
        var lon2 = 0
        // Circle definition
        val node = OsmNodeNamed()
        // Center
        node.position = Position(2.334243, 48.824017)
        // Radius
        node.radius = 30.0

        // Check distance within radius is correctly computed if the segment passes through the center
        lon1 = 2.332559.toIntLongitude()
        lat1 = 48.823822.toIntLatitude()
        // Segment ends
        lon2 = 2.335018.toIntLongitude()
        lat2 = 48.824105.toIntLatitude()
        var totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        Assert.assertEquals(
            "Works for segment aligned with the nogo center",
            2 * node.radius,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.01 * (2 * node.radius)
        )

        // Check distance within radius is correctly computed for a given circle
        node.position = Position(2.33438, 48.824275)
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
        lon2 = 2.334495.toIntLongitude()
        lat2 = 48.824045.toIntLatitude()
        totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        Assert.assertEquals(
            "Works if last point is within the circle",
            17.0,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 17
        )

        lon1 = 2.334495.toIntLongitude()
        lat1 = 48.824045.toIntLatitude()
        lon2 = 2.335018.toIntLongitude()
        lat2 = 48.824105.toIntLatitude()
        totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        Assert.assertEquals(
            "Works if first point is within the circle",
            9.0,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 9
        )

        lon1 = 2.33427.toIntLongitude()
        lat1 = 48.82402.toIntLatitude()
        lon2 = 2.334587.toIntLongitude()
        lat2 = 48.824061.toIntLatitude()
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
        lon1 = 2.332559.toIntLongitude()
        lat1 = 48.823822.toIntLatitude()
        lon2 = 2.33431.toIntLongitude()
        lat2 = 48.824027.toIntLatitude()
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
        node.position = Position(0.0, 0.0)
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
}
