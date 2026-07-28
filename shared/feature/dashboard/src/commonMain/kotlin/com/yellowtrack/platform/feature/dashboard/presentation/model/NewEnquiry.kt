package com.yellowtrack.platform.feature.dashboard.presentation.model

import com.yellowtrack.platform.core.model.lead.LeadSource
import com.yellowtrack.platform.core.model.service.ServiceLine

/**
 * What the enquiry form collected, before validation.
 *
 * Amounts stay as text here: parsing them is the ViewModel's job, so the rule about what
 * counts as a valid amount lives in one place rather than in every form.
 */
internal data class NewEnquiry(
    val name: String,
    val source: LeadSource,
    val serviceLine: ServiceLine,
    val email: String? = null,
    val phone: String? = null,
    val budgetLow: String? = null,
    val budgetHigh: String? = null,
    val referredBy: String? = null,
)
