package dev.skynomads.beerouter.router

import be.yellowduck.gpx.GPX
import java.net.URL
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class RoutingTest {
    val root = File(System.getProperty("user.dir"))

    val segmentFile = File(
        File(System.getProperty("java.io.tmpdir")), "E5_N45.rd5"
    )

    fun createWaypoint(name: String, lat: Double, lon: Double): OsmNodeNamed {
        val n = OsmNodeNamed()
        n.name = name
        n.iLon = 180000000 + (lon * 1000000 + 0.5).toInt()
        n.iLat = 90000000 + (lat * 1000000 + 0.5).toInt()
        return n
    }

    @Before
    fun before() {
        if (!segmentFile.exists()) {
            val url = URL("https://brouter.de/brouter/segments4/E5_N45.rd5")
            url.openStream()
                .use { Files.copy(it, segmentFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
    }

    @Test
    fun routeEqualsBrouter() {
        //brouter.de/brouter-web/#map=13/48.5010/8.9417/standard&lonlats=9.053596,48.520326;8.933679,48.47696

        val profile = File(root, "../misc/profiles2/trekking.brf")

        Assert.assertTrue("profile exists ${segmentFile.path}", profile.exists())

        val ctx = RoutingContext(
            profile,
            segmentFile.getParentFile()
        )

        val engine = RoutingEngine(

            waypoints = mutableListOf(
                createWaypoint(
                    "Tübingen",
                    48.5203263,
                    9.053596
                ),
                createWaypoint(
                    "Rottenburg",
                    48.47696,
                    8.9336788
                )
            ),
            ctx,
        )

        val track = engine.doRouting(0)
        Assert.assertNotNull(track)
        val expected = GPX.parse(javaClass.getResource("/Tübingen-Rottenburg-Trekking.gpx")!!)
        val actual = GPX.parse(FormatGpx(ctx).format(track!!)!!.trim().byteInputStream())
        Assert.assertEquals("distance", expected.distance, actual.distance)

        expected.tracks.forEachIndexed { idx, expected ->
            val actual = actual.tracks[idx]
            Assert.assertEquals("segments", expected.segments.size, actual.segments.size)

            expected.segments.forEachIndexed { idx, expected ->
                val actual = actual.segments[idx]
                Assert.assertEquals("points", expected.points.size, actual.points.size)

                expected.points.forEachIndexed { idx, expected ->
                    val actual = actual.points[idx]

                    Assert.assertEquals("point", expected.lat, actual.lat, 0.0)
                    Assert.assertEquals("point", expected.lon, actual.lon, 0.0)
                    Assert.assertEquals("point", expected.ele, actual.ele, 0.0)
                }
            }

        }
    }
}
