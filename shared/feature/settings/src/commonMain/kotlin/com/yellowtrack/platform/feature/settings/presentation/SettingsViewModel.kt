package com.yellowtrack.platform.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.StudioProfileRepository
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.studio.StudioProfile
import com.yellowtrack.platform.core.ui.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The studio's own details, which every document it sends carries.
 *
 * This screen has said since 0.1.0 that there was nothing to configure. There was: an
 * invoice with no name on it is not an invoice, and nothing held the name.
 */
internal class SettingsViewModel(
    private val profileRepository: StudioProfileRepository,
    private val studioContext: StudioContext,
    private val clock: AppClock,
) : ViewModel() {
    private val savedNote = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            profileRepository.observeProfile(),
            savedNote,
        ) { profile, note ->
            SettingsUiState(
                content =
                    UiState.Success(
                        SettingsContent(
                            profile = profile.toFields(),
                            canIssueDocuments = profile?.canIssueDocuments == true,
                            gaps = profile?.documentGaps.orEmpty(),
                            savedNote = note,
                        ),
                    ),
            )
        }.catch { throwable ->
            emit(
                SettingsUiState(
                    content = UiState.Error(throwable.message ?: "Unable to load your studio details."),
                ),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState(content = UiState.Loading),
        )

    /**
     * Saves the details, creating the profile on first use.
     *
     * Blank fields are stored as null rather than as empty strings, so that "not set" is
     * one state rather than two — the document builders ask whether a field is there, and
     * an empty string would answer yes.
     */
    fun save(fields: StudioProfileFields) {
        viewModelScope.launch {
            val now = clock.now()
            val existing = profileRepository.getProfile()

            val profile =
                (existing ?: StudioProfile.empty(studioContext.studioId, AuditMetadata.createdAt(now))).copy(
                    name = fields.name.trim(),
                    address =
                        fields.address
                            .trimIndent()
                            .trim()
                            .ifBlank { null },
                    email = fields.email.trim().ifBlank { null },
                    phone = fields.phone.trim().ifBlank { null },
                    website = fields.website.trim().ifBlank { null },
                    taxNumber = fields.taxNumber.trim().ifBlank { null },
                    paymentInstructions =
                        fields.paymentInstructions
                            .trimIndent()
                            .trim()
                            .ifBlank { null },
                    documentFooter = fields.documentFooter.trim().ifBlank { null },
                    currency = fields.currency,
                    audit = existing?.audit?.touched(now) ?: AuditMetadata.createdAt(now),
                )

            profileRepository.saveProfile(profile)

            savedNote.value =
                if (profile.canIssueDocuments) {
                    "Saved. Your documents will carry these details."
                } else {
                    "Saved, but without a name nothing can be sent out."
                }
        }
    }

    private fun StudioProfile?.toFields(): StudioProfileFields =
        StudioProfileFields(
            name = this?.name.orEmpty(),
            address = this?.address.orEmpty(),
            email = this?.email.orEmpty(),
            phone = this?.phone.orEmpty(),
            website = this?.website.orEmpty(),
            taxNumber = this?.taxNumber.orEmpty(),
            paymentInstructions = this?.paymentInstructions.orEmpty(),
            documentFooter = this?.documentFooter.orEmpty(),
            currency = this?.currency ?: CurrencyCode.USD,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
