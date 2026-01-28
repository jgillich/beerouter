/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.expressions.BExpressionContextNode
import dev.skynomads.beerouter.expressions.BExpressionContextWay

abstract class OsmPathModel {
    abstract fun createPrePath(): OsmPrePath?

    abstract fun createPath(): OsmPath

    abstract fun init(
        expctxWay: BExpressionContextWay?,
        expctxNode: BExpressionContextNode?,
        keyValues: MutableMap<String, String>
    )
}
