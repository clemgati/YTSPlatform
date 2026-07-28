package com.yellowtrack.platform.core.model.contract

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * What a commercial client may do with the images, where, and for how long.
 *
 * This is where commercial photography earns its money, and where inexperienced
 * photographers give it away: an unlimited, perpetual, worldwide, exclusive licence
 * granted for a day rate is the single most expensive mistake in the field, because it
 * forecloses every future fee from the same work.
 *
 * A bounded licence also creates recurring revenue. A one-year web-only licence is a
 * renewal conversation twelve months later — one almost nobody has, because nobody is
 * reminded. [expiresOn] exists so the platform can.
 */
@Serializable
data class UsageLicense(
    val media: List<LicenseMedium>,
    /** "United Kingdom", "North America", "Worldwide". */
    val territory: String,
    /** Null means perpetual — which should be priced as such, not granted by default. */
    val durationMonths: Int? = null,
    /** Exclusivity stops the studio licensing the same images elsewhere. Price it. */
    val isExclusive: Boolean = false,
    val startsOn: LocalDate? = null,
    val notes: String? = null,
) {
    val isPerpetual: Boolean get() = durationMonths == null

    /** When the licence lapses, and therefore when to raise a renewal. */
    fun expiresOn(): LocalDate? =
        if (startsOn != null && durationMonths != null) {
            startsOn.plus(durationMonths, DateTimeUnit.MONTH)
        } else {
            null
        }

    fun isExpired(on: LocalDate): Boolean = expiresOn()?.let { on > it } == true
}

@Serializable
enum class LicenseMedium {
    /** The client's own website and owned channels. */
    Web,

    /** Organic social posts. Distinct from [PaidSocial], which reaches far further. */
    Social,

    PaidSocial,
    Print,
    Packaging,

    /** Billboards, transit, and other out-of-home. Priced well above web. */
    OutOfHome,

    Broadcast,

    /** Internal decks and training material. Usually the cheapest grant. */
    Internal,

    /** Resale or redistribution to third parties. Rarely granted without a large fee. */
    Resale,
}
