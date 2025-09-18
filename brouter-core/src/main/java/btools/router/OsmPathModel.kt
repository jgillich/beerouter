/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package btools.router

import btools.expressions.BExpressionContextNode
import btools.expressions.BExpressionContextWay

abstract class OsmPathModel {
    abstract fun createPrePath(): OsmPrePath?

    abstract fun createPath(): OsmPath

    abstract fun init(
        expctxWay: BExpressionContextWay?,
        expctxNode: BExpressionContextNode?,
        keyValues: MutableMap<String?, String?>
    )
}
