package btools.expressions

import java.io.File

class IntegrityCheckProfile {
    fun integrityTestProfiles(lookupFile: File, profileDir: File) {
        val files = profileDir.listFiles()

        if (files == null) {
            System.err.println("no files " + profileDir)
            return
        }
        if (!lookupFile.exists()) {
            System.err.println("no lookup file " + lookupFile)
            return
        }

        for (f in files) {
            if (f.getName().endsWith(".brf")) {
                val meta = BExpressionMetaData()
                val expctxWay: BExpressionContext = BExpressionContextWay(meta)
                val expctxNode: BExpressionContext = BExpressionContextNode(meta)
                meta.readMetaData(lookupFile)
                expctxNode.setForeignContext(expctxWay)
                expctxWay.parseFile(f, "global")
                expctxNode.parseFile(f, "global")
                println("test " + meta.lookupVersion + "." + meta.lookupMinorVersion + " " + f)
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size != 2) {
                println("usage: java IntegrityCheckProfile <lookup-file> <profile-folder>")
                return
            }

            val test = IntegrityCheckProfile()
            try {
                val lookupFile = File(args[0])
                val profileDir = File(args[1])
                test.integrityTestProfiles(lookupFile, profileDir)
            } catch (e: Exception) {
                System.err.println(e.message)
            }
        }
    }
}
