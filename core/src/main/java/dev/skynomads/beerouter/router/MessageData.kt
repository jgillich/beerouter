/**
 * Information on matched way point
 *
 * @author ab
 */
package dev.skynomads.beerouter.router


class MessageData : Cloneable {
    var linkdist: Int = 0
    var linkelevationcost: Int = 0
    var linkturncost: Int = 0
    var linknodecost: Int = 0
    var linkinitcost: Int = 0

    var costfactor: Float = 0f
    var priorityclassifier: Int = 0
    var classifiermask: Int = 0
    var turnangle: Float = 0f
    var wayKeyValues: String? = null
    var nodeKeyValues: String? = null

    var lon: Int = 0
    var lat: Int = 0
    var ele: Short = 0

    var time: Float = 0f
    var energy: Float = 0f

    // speed profile
    var vmaxExplicit: Int = -1
    var vmax: Int = -1
    var vmin: Int = -1
    var vnode0: Int = 999
    var vnode1: Int = 999
    var extraTime: Int = 0

    fun toMessage(): String? {
        if (wayKeyValues == null) {
            return null
        }

        val iCost = (costfactor * 1000 + 0.5f).toInt()
        return ((lon - 180000000).toString() + "\t"
                + (lat - 90000000) + "\t"
                + ele / 4 + "\t"
                + linkdist + "\t"
                + iCost + "\t"
                + linkelevationcost
                + "\t" + linkturncost
                + "\t" + linknodecost
                + "\t" + linkinitcost
                + "\t" + wayKeyValues
                + "\t" + (if (nodeKeyValues == null) "" else nodeKeyValues)
                + "\t" + (time.toInt())
                + "\t" + (energy.toInt()))
    }

    fun add(d: MessageData) {
        linkdist += d.linkdist
        linkelevationcost += d.linkelevationcost
        linkturncost += d.linkturncost
        linknodecost += d.linknodecost
        linkinitcost += d.linkinitcost
    }

    fun copy(): MessageData? {
        try {
            return clone() as MessageData?
        } catch (e: CloneNotSupportedException) {
            throw RuntimeException(e)
        }
    }

    override fun toString(): String {
        return "dist=" + linkdist + " prio=" + this.priorityclassifier + " turn=" + turnangle
    }

    val isBadOneway: Boolean
        get() = (classifiermask and 1) != 0

    val isGoodOneway: Boolean
        get() = (classifiermask and 2) != 0

    val isRoundabout: Boolean
        get() = (classifiermask and 4) != 0

    val isLinktType: Boolean
        get() = (classifiermask and 8) != 0

    val isGoodForCars: Boolean
        get() = (classifiermask and 16) != 0
}
