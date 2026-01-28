package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.OsmPos
import dev.skynomads.beerouter.osm.toDoubleLatitude
import dev.skynomads.beerouter.osm.toDoubleLongitude
import dev.skynomads.beerouter.util.CheapRuler.distance
import org.maplibre.spatialk.geojson.Position
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
class OsmPathElement protected constructor() : OsmPos {
    // interface OsmPos
    override var position: Position = Position(0.0, 0.0)

    override var sElev: Short = 0 // longitude

    public var message: MessageData? = null // description

    var cost: Int = 0

    override val elev: Double
        get() = this.sElev / 4.0

    var time: Float
        get() = if (message == null) 0f else message!!.time
        set(t) {
            if (message != null) {
                message!!.time = t
            }
        }

    var energy: Float
        get() = if (message == null) 0f else message!!.energy
        set(e) {
            if (message != null) {
                message!!.energy = e
            }
        }

    fun setAngle(e: Float) {
        if (message != null) {
            message!!.turnangle = e
        }
    }

    override val idFromPos: Long
        get() = (iLon.toLong()) shl 32 or iLat.toLong()

    override fun calcDistance(p: OsmPos): Int {
        return max(
            1.0, distance(
                this.iLon,
                this.iLat, p.iLon, p.iLat
            ).roundToInt().toDouble()
        ).toInt()
    }

    var origin: OsmPathElement? = null

    override fun toString(): String {
        return iLon.toString() + "_" + this.iLat
    }

    fun positionEquals(e: OsmPathElement): Boolean {
        return this.iLat == e.iLat && this.iLon == e.iLon
    }

    @Throws(IOException::class)
    fun writeToStream(dos: DataOutput) {
        dos.writeInt(this.iLat)
        dos.writeInt(this.iLon)
        dos.writeShort(sElev.toInt())
        dos.writeInt(cost)
    }

    companion object {
        // construct a path element from a path
        internal fun create(path: OsmPath): OsmPathElement {
            val n = path.targetNode!!
            val pe: OsmPathElement = create(n.iLon, n.iLat, n.sElev, path.originElement)
            pe.cost = path.cost
            pe.message = path.message
            return pe
        }

        fun create(ilon: Int, ilat: Int, selev: Short, origin: OsmPathElement?): OsmPathElement {
            val pe = OsmPathElement()
            pe.position = Position(
                ilon.toDoubleLongitude(),
                ilat.toDoubleLatitude()
            )
            pe.sElev = selev
            pe.origin = origin
            return pe
        }

        @Throws(IOException::class)
        fun readFromStream(dis: DataInput): OsmPathElement {
            val pe = OsmPathElement()
            val lat = dis.readInt()
            val lon = dis.readInt()
            pe.position = Position(
                lon.toDoubleLongitude(),
                lat.toDoubleLatitude()
            )
            pe.sElev = dis.readShort()
            pe.cost = dis.readInt()
            return pe
        }
    }
}
