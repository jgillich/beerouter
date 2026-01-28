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
    var wayTags: Map<String, String>? = null
    var nodeTags: Map<String, String>? = null

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

    fun add(d: MessageData) {
        linkdist += d.linkdist
        linkelevationcost += d.linkelevationcost
        linkturncost += d.linkturncost
        linknodecost += d.linknodecost
        linkinitcost += d.linkinitcost
    }

    fun copy(): MessageData? {
        return try {
            clone() as MessageData?
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
