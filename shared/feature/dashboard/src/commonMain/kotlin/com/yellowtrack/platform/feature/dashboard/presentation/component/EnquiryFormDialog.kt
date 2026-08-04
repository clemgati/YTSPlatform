package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.model.lead.LeadSource
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.feature.dashboard.presentation.model.NewEnquiry

/**
 * Captures an enquiry in the few seconds after it arrives.
 *
 * Only the name is required. An enquiry half-recorded now is worth more than a complete
 * one recorded never, and everything else can be filled in when the studio replies.
 */
@Composable
internal fun EnquiryFormDialog(
    onSave: (NewEnquiry) -> Unit,
    onDismiss: () -> Unit,
    /** The enquiry being corrected, or null when this is a new one. */
    initial: NewEnquiry? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var source by remember { mutableStateOf(initial?.source ?: LeadSource.Instagram) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var serviceLine by remember { mutableStateOf(initial?.serviceLine ?: ServiceLine.Wedding) }
    var budgetLow by remember { mutableStateOf(initial?.budgetLow.orEmpty()) }
    var budgetHigh by remember { mutableStateOf(initial?.budgetHigh.orEmpty()) }
    var referredBy by remember { mutableStateOf(initial?.referredBy.orEmpty()) }

    YTFormDialog(
        title = if (initial == null) "New enquiry" else "Correct this enquiry",
        supportingText = "Only a name is needed now. The rest can wait until you reply.",
        confirmLabel = "Save enquiry",
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            onSave(
                NewEnquiry(
                    name = name.trim(),
                    source = source,
                    email = email.trim().ifBlank { null },
                    phone = phone.trim().ifBlank { null },
                    serviceLine = serviceLine,
                    budgetLow = budgetLow.trim().ifBlank { null },
                    budgetHigh = budgetHigh.trim().ifBlank { null },
                    referredBy = referredBy.trim().ifBlank { null },
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            placeholder = "Priya & Tom — June wedding",
        )

        YTDropdownField(
            label = "Where did they come from?",
            selected = source,
            options = LeadSource.entries,
            optionLabel = LeadSource::label,
            onSelect = { source = it },
            help = "Attribution is how you learn which marketing actually books work.",
        )

        YTDropdownField(
            label = "Kind of work",
            selected = serviceLine,
            options = ServiceLine.entries,
            optionLabel = { it.name },
            onSelect = { serviceLine = it },
        )

        YTTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardType = KeyboardType.Email,
        )

        YTTextField(
            value = phone,
            onValueChange = { phone = it },
            label = "Phone",
            keyboardType = KeyboardType.Phone,
        )

        YTTextField(
            value = budgetLow,
            onValueChange = { budgetLow = it },
            label = "Budget from",
            keyboardType = KeyboardType.Decimal,
        )

        YTTextField(
            value = budgetHigh,
            onValueChange = { budgetHigh = it },
            label = "Budget to",
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        )

        YTTextField(
            value = referredBy,
            onValueChange = { referredBy = it },
            label = "Referred by",
            help = "Worth recording — referrers are the cheapest source of work you have.",
            imeAction = ImeAction.Done,
        )
    }
}

private val LeadSource.label: String
    get() =
        when (this) {
            LeadSource.Instagram -> "Instagram"
            LeadSource.TikTok -> "TikTok"
            LeadSource.Website -> "Website"
            LeadSource.GoogleSearch -> "Google search"
            LeadSource.ClientReferral -> "Past client referral"
            LeadSource.VendorReferral -> "Vendor or planner referral"
            LeadSource.RepeatClient -> "Repeat client"
            LeadSource.Directory -> "Directory or paid listing"
            LeadSource.WalkIn -> "Walk-in"
            LeadSource.Other -> "Other"
        }
