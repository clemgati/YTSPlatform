package com.yellowtrack.platform.core.model.service

import kotlinx.serialization.Serializable

/**
 * A kind of work the studio sells.
 *
 * These differ in duration, crew, deliverables, turnaround, and pricing shape — but not
 * in schema. A [ServiceTemplate] carries the differences as data, so that adding a
 * business line does not mean adding a feature module.
 */
@Serializable
enum class ServiceLine {
    Wedding,
    Event,
    Portrait,
    Branding,
    Headshot,
    Commercial,
    Video,
    RealEstate,
    Product,
    Other,
}
