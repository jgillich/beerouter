package dev.skynomads.beerouter.expressions

class CacheNode {
    var ab: ByteArray? = null
    var vars: FloatArray? = null

    @JvmField
    var hash: Int = 0

    override fun hashCode(): Int {
        return hash
    }

    override fun equals(o: Any?): Boolean {
        val n = o as CacheNode
        if (hash != n.hash) {
            return false
        }
        if (ab == null) {
            return true // hack: null = crc match only
        }
        return ab.contentEquals(n.ab)
    }
}
