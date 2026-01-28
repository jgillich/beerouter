package dev.skynomads.beerouter.codec

import dev.skynomads.beerouter.util.BitCoderContext

/**
 * Container for some re-usable databuffers for the decoder
 */
class DataBuffers
/**
 * construct a set of databuffers except
 * for 'iobuffer', where the given array is used
 */ @JvmOverloads constructor(var iobuffer: ByteArray? = ByteArray(65636)) {
    var tagbuf1: ByteArray = ByteArray(256)
    var bctx1: BitCoderContext = BitCoderContext(tagbuf1)
    var bbuf1: ByteArray = ByteArray(65636)
    var ibuf1: IntArray = IntArray(4096)
    var ibuf2: IntArray = IntArray(2048)
    var ibuf3: IntArray = IntArray(2048)
    var alon: IntArray = IntArray(2048)
    var alat: IntArray = IntArray(2048)
}
