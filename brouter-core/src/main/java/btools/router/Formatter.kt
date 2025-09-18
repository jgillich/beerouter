package btools.router

import java.io.BufferedWriter
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

abstract class Formatter {
    var rc: RoutingContext? = null

    internal constructor(rc: RoutingContext) {
        this.rc = rc
    }

    /**
     * writes the track in gpx-format to a file
     *
     * @param filename the filename to write to
     * @param t        the track to write
     */
    @Throws(Exception::class)
    fun write(filename: String, t: OsmTrack) {
        val bw = BufferedWriter(FileWriter(filename))
        bw.write(format(t))
        bw.close()
    }

    @Throws(Exception::class)
    open fun read(filename: String): OsmTrack? {
        return null
    }

    /**
     * writes the track in a selected output format to a string
     *
     * @param t the track to format
     * @return the formatted string
     */
    abstract fun format(t: OsmTrack): String?


    companion object {
        const val MESSAGES_HEADER: String =
            "Longitude\tLatitude\tElevation\tDistance\tCostPerKm\tElevCost\tTurnCost\tNodeCost\tInitialCost\tWayTags\tNodeTags\tTime\tEnergy"

        fun formatILon(ilon: Int): String {
            return formatPos(ilon - 180000000)
        }

        fun formatILat(ilat: Int): String {
            return formatPos(ilat - 90000000)
        }

        private fun formatPos(p: Int): String {
            var p = p
            val negative = p < 0
            if (negative) p = -p
            val ac = CharArray(12)
            var i = 11
            while (p != 0 || i > 3) {
                ac[i--] = ('0'.code + (p % 10)).toChar()
                p /= 10
                if (i == 5) ac[i--] = '.'
            }
            if (negative) ac[i--] = '-'
            return String(ac, i + 1, 11 - i)
        }

        fun getFormattedTime2(s: Int): String {
            var seconds = (s + 0.5).toInt()
            val hours = seconds / 3600
            val minutes = (seconds - hours * 3600) / 60
            seconds = seconds - hours * 3600 - minutes * 60
            var time = ""
            if (hours != 0) time = "" + hours + "h "
            if (minutes != 0) time = time + minutes + "m "
            if (seconds != 0) time = time + seconds + "s"
            return time
        }

        fun getFormattedEnergy(energy: Int): String {
            return format1(energy / 3600000.0) + "kwh"
        }

        private fun format1(n: Double): String {
            val s = "" + (n * 10 + 0.5).toLong()
            val len = s.length
            return s.substring(0, len - 1) + "." + s[len - 1]
        }


        const val dateformat: String = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

        fun getFormattedTime3(time: Float): String {
            val TIMESTAMP_FORMAT = SimpleDateFormat(dateformat, Locale.US)
            TIMESTAMP_FORMAT.timeZone = TimeZone.getTimeZone("UTC")
            // yyyy-mm-ddThh:mm:ss.SSSZ
            val d = Date((time * 1000f).toLong())
            return TIMESTAMP_FORMAT.format(d)
        }
    }
}
