package com.yellowtrack.platform.core.model.invoice

import com.yellowtrack.platform.core.common.id.uuidV7
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class InvoiceId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): InvoiceId = InvoiceId(uuidV7().toString())
    }
}

@Serializable
@JvmInline
value class PaymentId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): PaymentId = PaymentId(uuidV7().toString())
    }
}
