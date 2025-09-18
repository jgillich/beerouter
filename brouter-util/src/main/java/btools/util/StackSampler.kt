package btools.util

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.concurrent.Volatile

class StackSampler(logfile: File, private val interval: Int) : Thread() {
    private val df: DateFormat = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss,SSS",
        Locale.Builder().setLanguage("en").setRegion("US").build()
    )
    private var bw: BufferedWriter? = null
    private val rand = Random()

    private var flushCnt = 0

    @Volatile
    private var stopped = false

    init {
        try {
            bw = BufferedWriter(OutputStreamWriter(FileOutputStream(logfile, true)))
        } catch (e: Exception) {
            printError("StackSampler: " + e.message)
        }
    }

    protected fun printError(msg: String?) {
        println(msg)
    }

    override fun run() {
        while (!stopped) {
            dumpThreads()
        }
        if (bw != null) {
            try {
                bw!!.close()
            } catch (e: Exception) {
            }
        }
    }

    fun dumpThreads() {
        try {
            val wait1 = rand.nextInt(interval)
            val wait2 = interval - wait1
            sleep(wait1.toLong())
            val sb = StringBuilder(df.format(Date()) + " THREADDUMP\n")
            val allThreads = getAllStackTraces()
            for (e in allThreads.entries) {
                val t: Thread = e.key!!
                if (t === currentThread()) {
                    continue  // not me
                }

                val stack: Array<StackTraceElement> = e.value
                if (!matchesFilter(stack)) {
                    continue
                }

                sb.append(" (ID=").append(t.id).append(" \"").append(t.name).append("\" ")
                    .append(t.state).append("\n")
                for (line in stack) {
                    sb.append("    ").append(line.toString()).append("\n")
                }
                sb.append("\n")
            }
            bw!!.write(sb.toString())
            if (flushCnt++ >= 0) {
                flushCnt = 0
                bw!!.flush()
            }
            sleep(wait2.toLong())
        } catch (e: Exception) {
            // ignore
        }
    }

    fun close() {
        stopped = true
        interrupt()
    }

    private fun matchesFilter(stack: Array<StackTraceElement>): Boolean {
        var positiveMatch = false
        for (e in stack) {
            val s = e.toString()
            if (s.indexOf("btools") >= 0) {
                positiveMatch = true
            }
            if (s.indexOf("Thread.sleep") >= 0 || s.indexOf("PlainSocketImpl.socketAccept") >= 0) {
                return false
            }
        }
        return positiveMatch
    }

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            println("StackSampler...")
            val clazz = Class.forName(args[0])
            val args2 = arrayOfNulls<String>(args.size - 1)
            for (i in 1..<args.size) {
                args2[i - 1] = args[i]
            }
            val t = StackSampler(File("stacks.log"), 1000)
            t.start()
            try {
                clazz.getMethod("main", Array<String>::class.java)
                    .invoke(null, *arrayOf<Any>(args2))
            } finally {
                t.close()
            }
        }
    }
}
