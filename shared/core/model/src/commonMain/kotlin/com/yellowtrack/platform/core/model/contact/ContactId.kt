package com.yellowtrack.platform.core.model.contact

import com.yellowtrack.platform.core.common.id.uuidV7
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

/** Identifies a person. */
@Serializable
@JvmInline
value class ContactId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): ContactId = ContactId(uuidV7().toString())
    }
}
