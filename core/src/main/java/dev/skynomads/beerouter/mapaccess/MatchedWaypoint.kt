/**
 * Information on matched way point
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.osm.toDoubleLatitude
import dev.skynomads.beerouter.osm.toDoubleLongitude
import org.maplibre.spatialk.geojson.Position
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

class MatchedWaypoint {
    enum class Type {
        /** route next to this point */
        SHAPING,

        /** visit this point  */
        MEETING,

        /** from this point go direct to next = beeline routing  */
        DIRECT
    }

    @JvmField
    var node1: OsmNode? = null

    @JvmField
    var node2: OsmNode? = null

    @JvmField
    var crosspoint: OsmNode? = null

    @JvmField
    var waypoint: OsmNode? = null

    @JvmField
    var correctedpoint: OsmNode? = null

    @JvmField
    var name: String? = null // waypoint name used in error messages

    @JvmField
    var radius: Double = 0.0 // distance in meter between waypoint and crosspoint

    @JvmField
    var type: Type = Type.SHAPING

    @JvmField
    var indexInTrack: Int = 0
    var directionToNext: Double = -1.0
    var directionDiff: Double = 361.0

    var wayNearest: MutableList<MatchedWaypoint> = ArrayList<MatchedWaypoint>()
    var hasUpdate: Boolean = false

    @Throws(IOException::class)
    fun writeToStream(dos: DataOutput) {
        dos.writeInt(node1!!.iLat)
        dos.writeInt(node1!!.iLon)
        dos.writeInt(node2!!.iLat)
        dos.writeInt(node2!!.iLon)
        dos.writeInt(crosspoint!!.iLat)
        dos.writeInt(crosspoint!!.iLon)
        dos.writeInt(waypoint!!.iLat)
        dos.writeInt(waypoint!!.iLon)
        dos.writeDouble(radius)
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFromStream(dis: DataInput): MatchedWaypoint {
            val mwp = MatchedWaypoint()
            mwp.node1 = OsmNode()
            mwp.node2 = OsmNode()
            mwp.crosspoint = OsmNode()
            mwp.waypoint = OsmNode()

            val node1Lat = dis.readInt()
            val node1Lon = dis.readInt()
            val node2Lat = dis.readInt()
            val node2Lon = dis.readInt()
            val crosspointLat = dis.readInt()
            val crosspointLon = dis.readInt()
            val waypointLat = dis.readInt()
            val waypointLon = dis.readInt()

            mwp.node1!!.position = Position(
                node1Lon.toDoubleLongitude(),
                node1Lat.toDoubleLatitude(),
                0.0
            )
            mwp.node2!!.position = Position(
                node2Lon.toDoubleLongitude(),
                node2Lat.toDoubleLatitude(),
                0.0
            )
            mwp.crosspoint!!.position = Position(
                crosspointLon.toDoubleLongitude(),
                crosspointLat.toDoubleLatitude(),
                0.0
            )
            mwp.waypoint!!.position = Position(
                waypointLon.toDoubleLongitude(),
                waypointLat.toDoubleLatitude(),
                0.0
            )
            mwp.radius = dis.readDouble()
            return mwp
        }
    }
}
