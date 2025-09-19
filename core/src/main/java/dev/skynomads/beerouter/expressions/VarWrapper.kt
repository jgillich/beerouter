package dev.skynomads.beerouter.expressions

import dev.skynomads.beerouter.util.LruMapNode

class VarWrapper : LruMapNode() {
    var vars: FloatArray? = null

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
