package com.yellowtrack.platform.core.common.solar

import kotlinx.serialization.Serializable

/**
 * A point on the earth, in decimal degrees.
 *
 * North and east are positive, which is the convention every mapping tool exports and the
 * one the solar maths below assumes. Getting the sign of longitude wrong moves the sun by
 * hours rather than minutes, so the constructor refuses values that cannot be coordinates
 * rather than letting a transposed pair quietly produce a plausible-looking sunset.
 */
@Serializable
data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90, was $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180, was $longitude" }
    }
}
