package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.osm.toDoubleLatitude
import dev.skynomads.beerouter.osm.toDoubleLongitude
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import org.junit.Assert
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

class CoordinateConversionTest {

    @Test
    fun testCoordinateConversionAccuracy() {
        // Test various coordinate values to ensure round-trip conversion works
        val testCases = listOf(
            Pair(0, 0),           // Origin
            Pair(1000000, 1000000), // Small positive values
            Pair(-1000000, -1000000), // Small negative values
            Pair(180000000, 90000000), // Max longitude/latitude
            Pair(-180000000, -90000000), // Min longitude/latitude
            Pair(45000000, 45000000), // Mid-range values
            Pair(12345678, 87654321), // Random values
        )

        for ((lon, lat) in testCases) {
            // Test integer to double to integer conversion using original formula
            val doubleLon = lon.toDoubleLongitude()
            val doubleLat = lat.toDoubleLatitude()

            val backToIntLon = doubleLon.toIntLongitude()
            val backToIntLat = doubleLat.toIntLatitude()

            // Allow for small rounding errors (typically within 1 unit due to floating point precision)
            Assert.assertEquals(
                "Longitude conversion should be accurate for $lon",
                lon,
                backToIntLon,
            )
            Assert.assertEquals(
                "Latitude conversion should be accurate for $lat",
                lat,
                backToIntLat,
            )
        }
    }

    @Test
    fun testPositionBasedConversion() {
        // Test the conversion using Position-based approach
        val testCases = listOf(
            Pair(0, 0),
            Pair(1000000, 1000000),
            Pair(-1000000, -1000000),
            Pair(180000000, 90000000),
            Pair(-180000000, -90000000),
            Pair(45000000, 45000000),
            Pair(12345678, 87654321),
        )

        for ((lon, lat) in testCases) {
            // Create an OsmNode with Position coordinates using conversion functions
            val position = Position(
                lon.toDoubleLongitude(),
                lat.toDoubleLatitude()
            )
            val node = OsmNode(position)

            // Check that the derived iLat/iLon properties match the original values (with tolerance for precision)
            Assert.assertEquals("iLon should match original", lon, node.iLon)
            Assert.assertEquals("iLat should match original", lat, node.iLat)

            // Check that the position is calculated correctly
            val expectedLon = lon.toDoubleLongitude()
            val expectedLat = lat.toDoubleLatitude()

            Assert.assertEquals(
                "Longitude should be calculated correctly",
                expectedLon,
                node.position.longitude,
                1e-6
            )
            Assert.assertEquals(
                "Latitude should be calculated correctly",
                expectedLat,
                node.position.latitude,
                1e-6
            )
        }
    }

    @Test
    fun testPositionToIntegerConversion() {
        // Test conversion from Position coordinates back to integers
        val testCases = listOf(
            Pair(0.0, 0.0),
            Pair(1.0, 1.0),
            Pair(-1.0, -1.0),
            Pair(180.0, 90.0),
            Pair(-180.0, -90.0),
            Pair(45.5, 45.5),
            Pair(12.345678, 87.987654),
        )

        for ((lon, lat) in testCases) {
            // Create an OsmNode with Position coordinates
            val node = OsmNode()
            node.position = Position(lon, lat)

            // Check that the derived iLat/iLon properties are calculated correctly using conversion functions
            val expectedILon = lon.toIntLongitude()
            val expectedILat = lat.toIntLatitude()

            Assert.assertEquals(
                "iLon should be calculated correctly from longitude $lon",
                expectedILon,
                node.iLon,
            )
            Assert.assertEquals(
                "iLat should be calculated correctly from latitude $lat",
                expectedILat,
                node.iLat,
            )
        }
    }

    @Test
    fun testExtremeCoordinateValues() {
        // Test extreme values to ensure conversion works across the full range
        val extremeCases = listOf(
            Pair(-180000000, -90000000), // Minimum possible values
            Pair(180000000, 90000000),   // Maximum possible values
            Pair(0, 90000000),           // North pole
            Pair(0, -90000000),          // South pole
            Pair(180000000, 0),          // Date line
            Pair(-180000000, 0),         // Date line opposite
        )

        for ((lon, lat) in extremeCases) {
            val position = Position(
                lon.toDoubleLongitude(),
                lat.toDoubleLatitude()
            )
            val node = OsmNode(position)

            Assert.assertEquals("iLon should match original for extreme value", lon, node.iLon)
            Assert.assertEquals("iLat should match original for extreme value", lat, node.iLat)
        }
    }

    @Test
    fun testConversionSymmetry() {
        // Test that conversion is symmetric (int -> Position -> int gives same result)
        val testCases = listOf(
            Pair(0, 0),
            Pair(1000000, 1000000),
            Pair(-1000000, -1000000),
            Pair(180000000, 90000000),
            Pair(-180000000, -90000000),
            Pair(45000000, 45000000),
        )

        for ((originalLon, originalLat) in testCases) {
            // Step 1: Create node with Position coordinates from integer values using conversion functions
            val position = Position(
                originalLon.toDoubleLongitude(),
                originalLat.toDoubleLatitude()
            )
            val node = OsmNode(position)

            // Step 2: Extract the Position coordinates
            val positionLon = node.position.longitude
            val positionLat = node.position.latitude

            // Step 3: Convert Position coordinates back to integer coordinates using conversion functions
            val resultLon = positionLon.toIntLongitude()
            val resultLat = positionLat.toIntLatitude()

            // Step 4: Check that the integer coordinates match the original (with tolerance for precision)
            Assert.assertEquals(
                "Longitude should be preserved through round-trip",
                originalLon,
                resultLon,
            )
            Assert.assertEquals(
                "Latitude should be preserved through round-trip",
                originalLat,
                resultLat,
            )
        }
    }
}
