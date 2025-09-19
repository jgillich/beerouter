package btools.router

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File

class RoutingEngineTest {
    private var workingDir: File? = null

    fun createRoutingContext(): RoutingContext {
        val wd = workingDir!!.absolutePath

        return RoutingContext(
            File("$wd/../../../../misc/profiles2/trekking.brf"),
            File(wd, "/../../../../brouter-map-creator/build/resources/test/tmp/segments"),
        )
    }

    @Before
    fun before() {
        val resulturl = this.javaClass.getResource("/testtrack0.gpx")
        Assert.assertNotNull("reference result not found: ", resulturl)
        val resultfile = File(resulturl!!.file)
        workingDir = resultfile.getParentFile()
    }

    @Test
    fun routeCrossingSegmentBorder() {
        calcRoute(8.720897, 50.002515, 8.723658, 49.997510, "testtrack", createRoutingContext())
    }

    @Test
    fun routeDestinationPointFarOff() {
        try {
            calcRoute(8.720897, 50.002515, 16.723658, 49.997510, "notrack", createRoutingContext())
            Assert.fail("IllegalArgumentException expected")
        } catch (e: IllegalArgumentException) {
            Assert.assertTrue("datafile not found", e.message!!.contains("not found"))
        }
    }

    @Test
    fun overrideParam() {
        val rctx = createRoutingContext()
        rctx.keyValues = HashMap()
        rctx.keyValues!!.put("avoid_unsafe", "1.0")
        calcRoute(8.723037, 50.000491, 8.712737, 50.002899, "paramTrack", rctx)
    }

    private fun calcRoute(
        flon: Double,
        flat: Double,
        tlon: Double,
        tlat: Double,
        trackname: String?,
        rctx: RoutingContext
    ) {

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


        val re = RoutingEngine(
            wplist,
            rctx
        )

        re.doRun(0)

    }
}
