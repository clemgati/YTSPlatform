package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import kotlin.time.Instant

/** Instants are stored as epoch milliseconds, which sorts and compares correctly in SQL. */
internal fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

internal fun Long?.toInstantOrNull(): Instant? = this?.let(Instant::fromEpochMilliseconds)

internal fun Instant.toEpochMillis(): Long = toEpochMilliseconds()

internal fun Instant?.toEpochMillisOrNull(): Long? = this?.toEpochMilliseconds()

internal fun auditOf(
    createdAt: Long,
    updatedAt: Long,
    deletedAt: Long?,
    version: Long,
): AuditMetadata =
    AuditMetadata(
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
        deletedAt = deletedAt.toInstantOrNull(),
        version = version.toInt(),
    )

/**
 * Money spans two columns — minor units and currency — and is present only when both are.
 * A row with an amount but no currency is malformed, and is treated as absent rather than
 * silently assigned a default currency.
 */
internal fun moneyOf(
    minorUnits: Long?,
    currency: String?,
): Money? =
    if (minorUnits != null && currency != null) {
        Money(minorUnits, CurrencyCode(currency))
    } else {
        null
    }

/**
 * Reads an enum stored by name, falling back rather than throwing.
 *
 * A row written by a newer version of the app must not crash an older one — that is a
 * real scenario once devices sync, and a crash on read is far worse than a stale value.
 */
internal inline fun <reified T : Enum<T>> enumOrDefault(
    name: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == name } ?: default
