/**
 * Container for routig configs
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.expressions.BExpressionContextNode
import dev.skynomads.beerouter.expressions.BExpressionContextWay
import dev.skynomads.beerouter.expressions.BExpressionMetaData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class ProfileCache {
    private var expctxWay: BExpressionContextWay? = null
    private var expctxNode: BExpressionContextNode? = null
    private var lastProfileFile: File? = null
    private var lastProfileTimestamp: Long = 0
    private var profilesBusy = false
    private var lastUseTime: Long = 0

    companion object {
        var logger: Logger = LoggerFactory.getLogger(ProfileCache::class.java)

        private var apc = arrayOfNulls<ProfileCache>(1)

        @Synchronized
        fun setSize(size: Int) {
            apc = arrayOfNulls(size)
        }

        @Synchronized
        fun parseProfile(rc: RoutingContext): Boolean {
            rc.profileTimestamp = rc.profile.lastModified() + rc.keyValueChecksum shl 24
            apc = arrayOfNulls(apc.size)

            var lru: ProfileCache? = null
            var unusedSlot = -1

            // check for re-use
            for (i in apc.indices) {
                val pc: ProfileCache? = apc[i]

                if (pc != null) {
                    if ((!pc.profilesBusy) && rc.profile == pc.lastProfileFile) {
                        if (rc.profileTimestamp == pc.lastProfileTimestamp) {
                            rc.expctxWay = pc.expctxWay
                            rc.expctxNode = pc.expctxNode
                            rc.readGlobalConfig()
                            pc.profilesBusy = true
                            return true
                        }
                        lru = pc // name-match but timestamp-mismatch -> we overide this one
                        unusedSlot = -1
                        break
                    }
                    if (lru == null || lru.lastUseTime > pc.lastUseTime) {
                        lru = pc
                    }
                } else if (unusedSlot < 0) {
                    unusedSlot = i
                }
            }

            val meta = BExpressionMetaData()

            rc.expctxWay = BExpressionContextWay(rc.memoryclass * 512, meta)
            rc.expctxNode = BExpressionContextNode(0, meta)
            rc.expctxNode!!.setForeignContext(rc.expctxWay!!)

            meta.readMetaData(rc.lookupFile)

            rc.expctxWay!!.parseFile(rc.profile, "global", rc.keyValues)
            rc.expctxNode!!.parseFile(rc.profile, "global", rc.keyValues)

            rc.readGlobalConfig()

            if (rc.processUnusedTags) {
                rc.expctxWay!!.setAllTagsUsed()
            }

            if (lru == null || unusedSlot >= 0) {
                lru = ProfileCache()
                if (unusedSlot >= 0) {
                    apc[unusedSlot] = lru
                    logger.debug(
                        "adding new profile at idx={} for file={}",
                        unusedSlot,
                        rc.profile
                    )
                }
            }

            if (lru.lastProfileFile != null) {
                logger.debug(
                    "replacing profile of age={} sec {}->{}",
                    (System.currentTimeMillis() - lru.lastUseTime) / 1000L,
                    lru.lastProfileFile,
                    rc.profile
                )
            }

            lru.lastProfileTimestamp = rc.profileTimestamp
            lru.lastProfileFile = rc.profile
            lru.expctxWay = rc.expctxWay
            lru.expctxNode = rc.expctxNode
            lru.profilesBusy = true
            lru.lastUseTime = System.currentTimeMillis()
            return false
        }

        @Synchronized
        fun releaseProfile(rc: RoutingContext) {
            for (i in apc.indices) {
                val pc: ProfileCache? = apc[i]

                if (pc != null) {
                    // only the thread that holds the cached instance can release it
                    if (rc.expctxWay == pc.expctxWay && rc.expctxNode == pc.expctxNode) {
                        pc.profilesBusy = false
                        break
                    }
                }
            }
            rc.expctxWay = null
            rc.expctxNode = null
        }
    }
}
