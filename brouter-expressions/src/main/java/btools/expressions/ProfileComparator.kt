package btools.expressions

import java.io.File
import java.util.Random

object ProfileComparator {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 4) {
            println("usage: java ProfileComparator <lookup-file> <profile1> <profile2> <nsamples>")
            return
        }

        val lookupFile = File(args[0])
        val profile1File = File(args[1])
        val profile2File = File(args[2])
        val nsamples = args[3].toInt()
        testContext(lookupFile, profile1File, profile2File, nsamples, false)
        testContext(lookupFile, profile1File, profile2File, nsamples, true)
    }

    private fun testContext(
        lookupFile: File?,
        profile1File: File,
        profile2File: File,
        nsamples: Int,
        nodeContext: Boolean
    ) {
        // read lookup.dat + profiles
        val meta1 = BExpressionMetaData()
        val meta2 = BExpressionMetaData()
        val expctx1 =
            if (nodeContext) BExpressionContextNode(meta1) else BExpressionContextWay(meta1)
        val expctx2 =
            if (nodeContext) BExpressionContextNode(meta2) else BExpressionContextWay(meta2)

        // if same profiles, compare different optimization levels
        if (profile1File.getName() == profile2File.getName()) {
            expctx2.skipConstantExpressionOptimizations = true
        }

        meta1.readMetaData(lookupFile!!)
        meta2.readMetaData(lookupFile)
        expctx1.parseFile(profile1File, "global")
        println("usedTags1=" + expctx1.usedTagList())
        expctx2.parseFile(profile2File, "global")
        println("usedTags2=" + expctx2.usedTagList())

        println("nodeContext=" + nodeContext + " nodeCount1=" + expctx1.expressionNodeCount + " nodeCount2=" + expctx2.expressionNodeCount)

        val rnd = Random()
        for (i in 0..<nsamples) {
            val data = expctx1.generateRandomValues(rnd)
            expctx1.evaluate(data)
            expctx2.evaluate(data)

            expctx1.assertAllVariablesEqual(expctx2)
        }
    }
}
