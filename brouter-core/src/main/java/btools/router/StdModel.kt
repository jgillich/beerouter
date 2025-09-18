/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package btools.router

import btools.expressions.BExpressionContext
import btools.expressions.BExpressionContextNode
import btools.expressions.BExpressionContextWay

internal class StdModel : OsmPathModel() {
    override fun createPrePath(): OsmPrePath? {
        return null
    }

    override fun createPath(): OsmPath {
        return StdPath()
    }

    protected var ctxWay: BExpressionContextWay? = null
    protected var ctxNode: BExpressionContextNode? = null


    override fun init(
        expctxWay: BExpressionContextWay?,
        expctxNode: BExpressionContextNode?,
        keyValues: MutableMap<String?, String?>
    ) {
        ctxWay = expctxWay
        ctxNode = expctxNode

        val expctxGlobal: BExpressionContext? = expctxWay // just one of them...
    }
}
