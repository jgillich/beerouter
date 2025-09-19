/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess


internal class NodesList {
    var node: OsmNode? = null
    var next: NodesList? = null
}
