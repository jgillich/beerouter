package btools.router

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File

class RoutingEngineTest {
    private var workingDir: File? = null

    @Before
    fun before() {
        val resulturl = this.javaClass.getResource("/testtrack0.gpx")
        Assert.assertNotNull("reference result not found: ", resulturl)
        val resultfile = File(resulturl!!.file)
        workingDir = resultfile.getParentFile()
    }

    @Test
    fun routeCrossingSegmentBorder() {
        val msg = calcRoute(8.720897, 50.002515, 8.723658, 49.997510, "testtrack", RoutingContext())
        // error message from router?
        Assert.assertNull("routing failed: $msg", msg)

        // if the track didn't change, we expect the first alternative also
        val a1 = File(workingDir, "testtrack1.gpx")
        a1.deleteOnExit()
        Assert.assertTrue("result content mismatch", a1.exists())
    }

    @Test
    fun routeDestinationPointFarOff() {
        try {
            calcRoute(8.720897, 50.002515, 16.723658, 49.997510, "notrack", RoutingContext())
            Assert.fail("IllegalArgumentException expected")
        } catch (e: IllegalArgumentException) {
            Assert.assertTrue("datafile not found", e.message!!.contains("not found"))
        }
    }

    @Test
    fun overrideParam() {
        val rctx = RoutingContext()
        rctx.keyValues = HashMap()
        rctx.keyValues!!.put("avoid_unsafe", "1.0")
        val msg = calcRoute(8.723037, 50.000491, 8.712737, 50.002899, "paramTrack", rctx)
        Assert.assertNull("routing failed: $msg", msg)

        val trackFile = File(workingDir, "paramTrack1.gpx")
        trackFile.deleteOnExit()
        Assert.assertTrue("result content mismatch", trackFile.exists())
    }

    private fun calcRoute(
        flon: Double,
        flat: Double,
        tlon: Double,
        tlat: Double,
        trackname: String?,
        rctx: RoutingContext
    ): String? {
        val wd = workingDir!!.absolutePath

        val wplist: MutableList<OsmNodeNamed> = ArrayList()
        var n = OsmNodeNamed()
        n.name = "from"
        n.iLon = 180000000 + (flon * 1000000 + 0.5).toInt()
        n.iLat = 90000000 + (flat * 1000000 + 0.5).toInt()
        wplist.add(n)

        n = OsmNodeNamed()
        n.name = "to"
        n.iLon = 180000000 + (tlon * 1000000 + 0.5).toInt()
        n.iLat = 90000000 + (tlat * 1000000 + 0.5).toInt()
        wplist.add(n)

        rctx.localFunction = "$wd/../../../../misc/profiles2/trekking.brf"

        val re = RoutingEngine(
            "$wd/$trackname",
            null,
            File(wd, "/../../../../brouter-map-creator/build/resources/test/tmp/segments"),
            wplist,
            rctx
        )

        re.doRun(0)

        return re.errorMessage
    }
}
