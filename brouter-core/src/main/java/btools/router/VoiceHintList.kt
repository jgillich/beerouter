/**
 * Container for a voice hint
 * (both input- and result data for voice hint processing)
 *
 * @author ab
 */
package btools.router

class VoiceHintList {
    private var transportMode: Int = TRANS_MODE_BIKE
    var turnInstructionMode: Int = 0
    var list: MutableList<VoiceHint> = ArrayList()

    fun setTransportMode(isCar: Boolean, isBike: Boolean) {
        transportMode =
            if (isCar) TRANS_MODE_CAR else (if (isBike) TRANS_MODE_BIKE else TRANS_MODE_FOOT)
    }

    fun setTransportMode(mode: Int) {
        transportMode = mode
    }

    fun getTransportMode(): String {
        val ret: String
        ret = when (transportMode) {
            TRANS_MODE_FOOT -> "foot"
            TRANS_MODE_CAR -> "car"
            TRANS_MODE_BIKE -> "bike"
            else -> "bike"
        }
        return ret
    }

    fun transportMode(): Int {
        return transportMode
    }

    val locusRouteType: Int
        get() {
            if (transportMode == TRANS_MODE_CAR) {
                return 0
            }
            if (transportMode == TRANS_MODE_BIKE) {
                return 5
            }
            return 3 // foot
        }

    companion object {
        const val TRANS_MODE_NONE: Int = 0
        const val TRANS_MODE_FOOT: Int = 1
        const val TRANS_MODE_BIKE: Int = 2
        const val TRANS_MODE_CAR: Int = 3
    }
}
