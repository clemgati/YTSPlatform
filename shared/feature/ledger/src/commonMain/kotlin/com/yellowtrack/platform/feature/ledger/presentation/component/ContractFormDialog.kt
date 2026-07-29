package com.yellowtrack.platform.feature.ledger.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.designsystem.component.YTChipField
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.contract.LicenseMedium
import com.yellowtrack.platform.feature.ledger.presentation.model.NewContract
import com.yellowtrack.platform.feature.ledger.presentation.model.NewUsageLicense
import com.yellowtrack.platform.feature.ledger.presentation.model.ProjectOption
import kotlinx.datetime.LocalDate

/**
 * Draws up the agreement behind a booking.
 *
 * The terms open filled in. A photographer asked to compose a cancellation clause inside a
 * dialog leaves it blank, and the blank clause is the one that loses the argument six
 * months later — so the form states an ordinary position and invites editing rather than
 * authorship.
 *
 * Licensing is folded away by default. Weddings and portraits license nothing, and putting
 * eight commercial terms in front of every booking would teach the studio to skip the
 * screen. Commercial work opens it deliberately, which is also when the money is decided.
 */
@Composable
internal fun ContractFormDialog(
    today: LocalDate,
    currency: CurrencyCode,
    projects: List<ProjectOption>,
    onSave: (NewContract) -> Unit,
    onDismiss: () -> Unit,
) {
    val bookings = remember(projects) { projects.filter { it.id != null } }

    var title by remember { mutableStateOf("") }
    var retainer by remember { mutableStateOf("") }
    var refundable by remember { mutableStateOf(false) }
    var turnaroundDays by remember { mutableStateOf("") }
    var revisionRounds by remember { mutableStateOf("") }
    var cancellationTerms by remember { mutableStateOf(DEFAULT_CANCELLATION_TERMS) }
    var rescheduleTerms by remember { mutableStateOf(DEFAULT_RESCHEDULE_TERMS) }
    var weatherClause by remember { mutableStateOf(DEFAULT_WEATHER_CLAUSE) }
    var sendNow by remember { mutableStateOf(true) }
    var selectedProject by remember(bookings) { mutableStateOf(bookings.firstOrNull()) }

    var licensing by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf(emptySet<LicenseMedium>()) }
    var territory by remember { mutableStateOf(DEFAULT_TERRITORY) }
    var durationMonths by remember { mutableStateOf(DEFAULT_LICENSE_MONTHS.toString()) }
    var exclusive by remember { mutableStateOf(false) }
    var licenseStartsOn by remember { mutableStateOf(today.toString()) }

    val retainerValid = retainer.isBlank() || parseMoney(retainer, currency)?.isPositive == true
    val turnaroundValid = turnaroundDays.isPositiveCountOrBlank
    val revisionsValid = revisionRounds.isPositiveCountOrBlank
    val durationValid = durationMonths.isPositiveCountOrBlank
    val startsOnValid = licenseStartsOn.isBlank() || runCatching { LocalDate.parse(licenseStartsOn) }.isSuccess
    val licenseValid = !licensing || (media.isNotEmpty() && territory.isNotBlank() && durationValid && startsOnValid)
    val booking = selectedProject

    YTFormDialog(
        title = "Draw up a contract",
        confirmLabel = if (sendNow) "Save and send" else "Save",
        supportingText =
            if (bookings.isEmpty()) {
                "A contract is drawn against a booking, and there are none yet."
            } else {
                null
            },
        confirmEnabled =
            booking?.id != null &&
                title.isNotBlank() &&
                retainerValid &&
                turnaroundValid &&
                revisionsValid &&
                licenseValid,
        onConfirm = {
            val projectId = booking?.id ?: return@YTFormDialog

            onSave(
                NewContract(
                    projectId = projectId,
                    title = title.trim(),
                    retainerAmount = retainer.trim(),
                    isRetainerRefundable = refundable,
                    turnaroundDays = turnaroundDays.trim(),
                    revisionRounds = revisionRounds.trim(),
                    cancellationTerms = cancellationTerms.trim().ifBlank { null },
                    rescheduleTerms = rescheduleTerms.trim().ifBlank { null },
                    weatherClause = weatherClause.trim().ifBlank { null },
                    license =
                        if (licensing) {
                            NewUsageLicense(
                                media = media,
                                territory = territory.trim(),
                                durationMonths = durationMonths.trim(),
                                isExclusive = exclusive,
                                startsOn = licenseStartsOn.trim(),
                            )
                        } else {
                            null
                        },
                    sendNow = sendNow,
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        if (booking != null) {
            YTDropdownField(
                label = "For",
                selected = booking,
                options = bookings,
                optionLabel = ProjectOption::label,
                onSelect = { selectedProject = it },
            )
        }

        YTTextField(
            value = title,
            onValueChange = { title = it },
            label = "Title",
            placeholder = "Johnson Wedding Agreement",
        )

        YTTextField(
            value = retainer,
            onValueChange = { retainer = it },
            label = "Retainer (${currency.code})",
            keyboardType = KeyboardType.Decimal,
            help =
                if (retainer.isBlank()) {
                    "With no retainer, nothing but goodwill holds the date."
                } else {
                    null
                },
            errorMessage = if (!retainerValid) "Enter an amount such as 2000.00" else null,
        )

        CheckboxRow(
            checked = refundable,
            onCheckedChange = { refundable = it },
            label = "Retainer is refundable",
        )

        if (refundable) {
            Text(
                text = "A refundable retainer compensates for nothing: the date was still turned away.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.error,
            )
        }

        YTTextField(
            value = turnaroundDays,
            onValueChange = { turnaroundDays = it },
            label = "Turnaround (days)",
            keyboardType = KeyboardType.Number,
            help = "What the client will hold you to.",
            errorMessage = if (!turnaroundValid) "Enter a whole number of days" else null,
        )

        YTTextField(
            value = revisionRounds,
            onValueChange = { revisionRounds = it },
            label = "Revision rounds",
            keyboardType = KeyboardType.Number,
            help = "What stops an edit consuming an unbounded amount of unpaid time.",
            errorMessage = if (!revisionsValid) "Enter a whole number of rounds" else null,
        )

        YTTextField(
            value = cancellationTerms,
            onValueChange = { cancellationTerms = it },
            label = "If they cancel",
            singleLine = false,
        )

        YTTextField(
            value = rescheduleTerms,
            onValueChange = { rescheduleTerms = it },
            label = "If they move the date",
            singleLine = false,
        )

        YTTextField(
            value = weatherClause,
            onValueChange = { weatherClause = it },
            label = "If the weather turns",
            singleLine = false,
        )

        HorizontalDivider(color = YTTheme.colors.outlineVariant)

        CheckboxRow(
            checked = licensing,
            onCheckedChange = { licensing = it },
            label = "License the images to the client",
        )

        if (licensing) {
            YTChipField(
                label = "They may use them in",
                options = LicenseMedium.entries,
                selected = media,
                optionLabel = { it.displayName },
                onToggle = { medium ->
                    media = if (medium in media) media - medium else media + medium
                },
                errorMessage = if (media.isEmpty()) "Choose at least one, or turn licensing off" else null,
            )

            YTTextField(
                value = territory,
                onValueChange = { territory = it },
                label = "Where",
                placeholder = DEFAULT_TERRITORY,
                errorMessage = if (territory.isBlank()) "State a territory, however broad" else null,
            )

            YTTextField(
                value = durationMonths,
                onValueChange = { durationMonths = it },
                label = "For how many months",
                keyboardType = KeyboardType.Number,
                help = if (durationMonths.isBlank()) null else "Renewable when it lapses, which is revenue.",
                errorMessage = if (!durationValid) "Enter a whole number of months" else null,
            )

            if (durationMonths.isBlank()) {
                Text(
                    text =
                        "A perpetual licence forecloses every future fee from this work. " +
                            "Grant it only at a price that reflects that.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.error,
                )
            }

            YTTextField(
                value = licenseStartsOn,
                onValueChange = { licenseStartsOn = it },
                label = "Starting",
                placeholder = today.toString(),
                errorMessage = if (!startsOnValid) "Use the form $today" else null,
            )

            CheckboxRow(
                checked = exclusive,
                onCheckedChange = { exclusive = it },
                label = "Exclusive",
            )

            if (exclusive) {
                Text(
                    text = "Exclusivity stops you licensing these images to anyone else. Price it.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.error,
                )
            }
        }

        HorizontalDivider(color = YTTheme.colors.outlineVariant)

        CheckboxRow(
            checked = sendNow,
            onCheckedChange = { sendNow = it },
            label = "Send it now",
        )
    }
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurface,
        )
    }
}

/** Blank is a term left unstated, which is allowed; anything else must be a real count. */
private val String.isPositiveCountOrBlank: Boolean
    get() = isBlank() || (toIntOrNull()?.let { it > 0 } == true)

private val LicenseMedium.displayName: String
    get() =
        when (this) {
            LicenseMedium.Web -> "Web"
            LicenseMedium.Social -> "Social"
            LicenseMedium.PaidSocial -> "Paid social"
            LicenseMedium.Print -> "Print"
            LicenseMedium.Packaging -> "Packaging"
            LicenseMedium.OutOfHome -> "Out of home"
            LicenseMedium.Broadcast -> "Broadcast"
            LicenseMedium.Internal -> "Internal"
            LicenseMedium.Resale -> "Resale"
        }

/**
 * A year, which is long enough to be worth buying and short enough to come back around.
 *
 * The renewal conversation twelve months from now is one almost nobody has, because nobody
 * is reminded. A dated licence is what makes the reminder possible.
 */
private const val DEFAULT_LICENSE_MONTHS = 12

private const val DEFAULT_TERRITORY = "United Kingdom"

private const val DEFAULT_CANCELLATION_TERMS =
    "The retainer is non-refundable. Cancellation within 30 days of the date is charged in full."

private const val DEFAULT_RESCHEDULE_TERMS =
    "One reschedule is permitted with 30 days' notice, subject to availability on the new date."

private const val DEFAULT_WEATHER_CLAUSE =
    "Outdoor coverage may move to the agreed backup location, or reschedule once at no charge."
