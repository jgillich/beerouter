package dev.skynomads.beerouter.util

interface IByteArrayUnifier {
    fun unify(ab: ByteArray, offset: Int, len: Int): ByteArray?
}
