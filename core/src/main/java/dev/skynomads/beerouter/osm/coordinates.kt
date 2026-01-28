package dev.skynomads.beerouter.osm

import kotlin.math.roundToInt

fun Double.toIntLongitude(): Int {
    return ((this + 180.0) / 1e-6).roundToInt()
}

fun Double.toIntLatitude(): Int {
    return ((this + 90.0) / 1e-6).roundToInt()
}

fun Int.toDoubleLongitude(): Double {
    return (this * 1e-6) - 180.0
}

fun Int.toDoubleLatitude(): Double {
    return (this * 1e-6) - 90.0
}
