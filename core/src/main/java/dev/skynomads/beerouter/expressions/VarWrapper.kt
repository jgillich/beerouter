package dev.skynomads.beerouter.expressions

class VarWrapper {
    var vars: FloatArray? = null

    @JvmField
    var hash: Int = 0

    override fun hashCode(): Int {
        return hash
    }

    override fun equals(o: Any?): Boolean {
        val n = o as VarWrapper
        if (hash != n.hash) {
            return false
        }
        return vars.contentEquals(n.vars)
    }
}
