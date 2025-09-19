/**
 * Simple version of OsmPath just to get angle and priority of first segment
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.OsmLink
import dev.skynomads.beerouter.mapaccess.OsmNode

abstract class OsmPrePath {
    protected var sourceNode: OsmNode? = null
    protected var targetNode: OsmNode? = null
    protected var link: OsmLink? = null

    var next: OsmPrePath? = null

    fun init(origin: OsmPath, link: OsmLink, rc: RoutingContext) {
        this.link = link
        this.sourceNode = origin.targetNode!!
        this.targetNode = link.getTarget(sourceNode)
        initPrePath(origin, rc)
    }

    protected abstract fun initPrePath(origin: OsmPath, rc: RoutingContext)
}
