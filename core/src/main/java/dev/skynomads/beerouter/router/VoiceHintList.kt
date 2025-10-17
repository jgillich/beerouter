/**
 * Container for a voice hint
 * (both input- and result data for voice hint processing)
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

public class VoiceHintList(
    val list: MutableList<VoiceHint> = ArrayList()
) : MutableList<VoiceHint> by list {
    var transportMode: Int = TRANS_MODE_BIKE
        private set

    var turnInstructionMode: Int = 0

    fun setTransportMode(isCar: Boolean, isBike: Boolean) {
        transportMode =
            if (isCar) TRANS_MODE_CAR else (if (isBike) TRANS_MODE_BIKE else TRANS_MODE_FOOT)
    }

    fun getTransportMode(): String {
        return when (transportMode) {
            TRANS_MODE_FOOT -> "foot"
            TRANS_MODE_CAR -> "car"
            TRANS_MODE_BIKE -> "bike"
            else -> "bike"
        }
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
