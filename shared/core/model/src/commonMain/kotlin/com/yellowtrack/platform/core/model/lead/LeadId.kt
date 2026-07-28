package com.yellowtrack.platform.core.model.lead

import com.yellowtrack.platform.core.common.id.uuidV7
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

/** Identifies an enquiry. */
@Serializable
@JvmInline
value class LeadId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): LeadId = LeadId(uuidV7().toString())
    }
}
