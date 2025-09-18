package btools.router

import java.io.BufferedWriter
import java.io.StringWriter

class FormatCsv(rc: RoutingContext) : Formatter(rc) {
    override fun format(t: OsmTrack): String? {
        try {
            val sw = StringWriter()
            val bw = BufferedWriter(sw)
            writeMessages(bw, t)
            return sw.toString()
        } catch (ex: Exception) {
            return "Error: " + ex.message
        }
    }

    @Throws(Exception::class)
    fun writeMessages(bw: BufferedWriter?, t: OsmTrack) {
        dumpLine(bw, MESSAGES_HEADER)
        for (m in t.aggregateMessages()) {
            dumpLine(bw, m!!)
        }
        bw?.close()
    }

    @Throws(Exception::class)
    private fun dumpLine(bw: BufferedWriter?, s: String) {
        if (bw == null) {
            println(s)
        } else {
            bw.write(s)
            bw.write("\n")
        }
    }
}
