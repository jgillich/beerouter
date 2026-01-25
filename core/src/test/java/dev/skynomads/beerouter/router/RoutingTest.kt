package dev.skynomads.beerouter.router

import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

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

        val waypoints = listOf(
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
        )

        val btrack = routeBrouter(waypoints)

        val beetrack = runBlocking {
            routeBeerouter(waypoints)
        }

        assertNotNull(btrack)
        assertNotNull(beetrack)

        assertEquals(btrack.nodes.size, beetrack.nodes.size)

        btrack.nodes.forEachIndexed { idx, node ->
            assertEquals(node.iLon, beetrack.nodes[idx].iLon)
            assertEquals(node.iLat, beetrack.nodes[idx].iLat)
            assertEquals(node.sElev, beetrack.nodes[idx].sElev)

            val hint = btrack.getVoiceHint(idx)
            if (hint != null) {
                val actual = beetrack.getVoiceHint(idx)
                assertNotNull(actual)
                assertEquals(hint.cruiserCommandString, actual.cruiserCommandString)
            }
        }

        assertEquals(btrack.nodes.mapIndexed { idx, node ->
            btrack.getVoiceHint(idx)
        }.filterNotNull().size, beetrack.voiceHints.size)


        //val expected = GPX.parse(javaClass.getResource("/Tübingen-Rottenburg-Trekking.gpx")!!)
        //val actual = GPX.parse(FormatGpx().format(beetrack)!!.trim().byteInputStream())
        //Assert.assertEquals("distance", expected.distance, actual.distance)
        //
        //expected.tracks.forEachIndexed { idx, expected ->
        //    val actual = actual.tracks[idx]
        //    Assert.assertEquals("segments", expected.segments.size, actual.segments.size)
        //
        //    expected.segments.forEachIndexed { idx, expected ->
        //        val actual = actual.segments[idx]
        //        Assert.assertEquals("points", expected.points.size, actual.points.size)
        //
        //        expected.points.forEachIndexed { idx, expected ->
        //            val actual = actual.points[idx]
        //
        //            Assert.assertEquals("point", expected.lat, actual.lat, 0.0)
        //            Assert.assertEquals("point", expected.lon, actual.lon, 0.0)
        //            Assert.assertEquals("point", expected.ele, actual.ele, 0.0)
        //        }
        //    }
        //}
    }

    fun routeBrouter(waypoints: List<OsmNodeNamed>): btools.router.OsmTrack {
        val profile = File(root, "../misc/profiles2/trekking.brf")
        Assert.assertTrue("profile exists ${segmentFile.path}", profile.exists())

        val rc = btools.router.RoutingContext().apply {
            turnInstructionMode = 2
            localFunction = profile.path
        }

        val cr = btools.router.RoutingEngine(
            null,
            null,
            segmentFile.parentFile,
            waypoints.map {
                btools.router.OsmNodeNamed().apply {
                    name = it.name
                    ilon = it.iLon
                    ilat = it.iLat
                }
            },
            rc
        )

        cr.doRouting(60.seconds.inWholeMilliseconds)

        return cr.foundTrack
    }

    suspend fun routeBeerouter(waypoints: List<OsmNodeNamed>): OsmTrack? {
        val profile = File(root, "../misc/profiles2/trekking.brf")
        Assert.assertTrue("profile exists ${segmentFile.path}", profile.exists())


        val ctx = RoutingContext(
            profile,
            segmentFile.getParentFile()
        ).apply {
            global.turnInstructionMode = 2
        }

        val engine = RoutingEngine(
            ctx
        )

        return engine.doRouting(waypoints)
    }
}
