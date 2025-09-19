// context for simple expression
// context means:
// - the local variables
// - the local variable names
// - the lookup-input variables
package dev.skynomads.beerouter.expressions

import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class BExpressionMetaData {
    var lookupVersion: Short = -1
    var lookupMinorVersion: Short = -1
    var minAppVersion: Short = -1

    private val listeners: MutableMap<String?, BExpressionContext> =
        HashMap()

    fun registerListener(context: String?, ctx: BExpressionContext?) {
        listeners.put(context, ctx!!)
    }

    fun readMetaData(lookupsFile: File) {
        try {
            val br = BufferedReader(FileReader(lookupsFile))

            var ctx: BExpressionContext? = null

            while (true) {
                var line = br.readLine()
                if (line == null) break
                line = line.trim { it <= ' ' }
                if (line.isEmpty() || line.startsWith("#")) {
                    continue
                }
                if (line.startsWith(CONTEXT_TAG)) {
                    ctx = listeners[line.substring(CONTEXT_TAG.length)]
                    continue
                }
                if (line.startsWith(VERSION_TAG)) {
                    lookupVersion = line.substring(VERSION_TAG.length).toShort()
                    continue
                }
                if (line.startsWith(MINOR_VERSION_TAG)) {
                    lookupMinorVersion = line.substring(MINOR_VERSION_TAG.length).toShort()
                    continue
                }
                if (line.startsWith(MIN_APP_VERSION_TAG)) {
                    minAppVersion = line.substring(MIN_APP_VERSION_TAG.length).toShort()
                    continue
                }
                if (line.startsWith(VARLENGTH_TAG)) { // tag removed...
                    continue
                }
                ctx?.parseMetaLine(line)
            }
            br.close()

            for (c in listeners.values) {
                c.finishMetaParsing()
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private const val CONTEXT_TAG = "---context:"
        private const val VERSION_TAG = "---lookupversion:"
        private const val MINOR_VERSION_TAG = "---minorversion:"
        private const val VARLENGTH_TAG = "---readvarlength"
        private const val MIN_APP_VERSION_TAG = "---minappversion:"
    }
}
