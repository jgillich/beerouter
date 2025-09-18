package btools.router

import org.junit.Assert
import org.junit.Test
import java.io.UnsupportedEncodingException

class RouteParamTest {
    @Test
    fun readWpts() {
        var data = "1.0,1.2;2.0,2.2"
        val rpc = RoutingParamCollector()
        var map = rpc.getWayPointList(data)

        Assert.assertEquals("result content 1 ", 2, map.size.toLong())

        data = "1.0,1.1|2.0,2.2|3.0,3.3"
        map = rpc.getWayPointList(data)

        Assert.assertEquals("result content 2 ", 3, map.size.toLong())

        data = "1.0,1.2,Name;2.0,2.2"
        map = rpc.getWayPointList(data)

        Assert.assertEquals("result content 3 ", "Name", map[0]!!.name)

        data = "1.0,1.2,d;2.0,2.2"
        map = rpc.getWayPointList(data)

        Assert.assertTrue("result content 4 ", map[0]!!.direct)
    }

    @Test
    @Throws(UnsupportedEncodingException::class)
    fun readUrlParams() {
        val url = "lonlats=1,1;2,2&profile=test&more=1"
        val rpc = RoutingParamCollector()
        val map = rpc.getUrlParams(url)

        Assert.assertEquals("result content ", 3, map.size.toLong())
    }

    @Test
    @Throws(UnsupportedEncodingException::class)
    fun readParamsFromList() {
        val params: MutableMap<String?, String?> = HashMap()
        params.put("timode", "3")
        val rc = RoutingContext()
        val rpc = RoutingParamCollector()
        rpc.setParams(rc, mutableListOf(), params)

        Assert.assertEquals("result content timode ", 3, rc.turnInstructionMode.toLong())
    }
}
