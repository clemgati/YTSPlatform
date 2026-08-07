package com.yellowtrack.platform.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.StudioProfileRepository
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
import com.yellowtrack.platform.core.data.sync.SyncStatus
import com.yellowtrack.platform.core.data.sync.Synchroniser
import com.yellowtrack.platform.core.data.sync.WriteFailed
import com.yellowtrack.platform.core.export.Document
import com.yellowtrack.platform.core.export.DocumentFormat
import com.yellowtrack.platform.core.export.DocumentSink
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
    private val synchroniser: Synchroniser,
    private val auth: AuthRepository,
    private val documentSink: DocumentSink,
    private val studioContext: StudioContext,
    private val clock: AppClock,
) : ViewModel() {
    private val savedNote = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            profileRepository.observeProfile(),
            savedNote,
            synchroniser.status,
            auth.session,
        ) { profile, note, syncStatus, session ->
            SettingsUiState(
                content =
                    UiState.Success(
                        SettingsContent(
                            profile = profile.toFields(),
                            canIssueDocuments = profile?.canIssueDocuments == true,
                            gaps = profile?.documentGaps.orEmpty(),
                            savedNote = note,
                            sync = syncStatus.toSummary(),
                            account = (session as? SessionState.SignedIn)?.toSummary(),
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

            // The profile writes through the server now, so this can fail for a reason that
            // is not the studio's fault and is worth saying out loud. It goes on the same
            // line the confirmation uses, because "that did not save" and "that did not
            // send" are one sentence to whoever pressed the button.
            //
            // Ordered so the confirmation is only ever written after the write returned.
            // Setting it first and correcting it on failure is the shape that produced a
            // "Saved." on a document that never left.
            val failure =
                runCatching { profileRepository.saveProfile(profile) }
                    .exceptionOrNull()

            if (failure != null) {
                if (failure !is WriteFailed) throw failure
                savedNote.value = failure.message
                return@launch
            }

            savedNote.value =
                if (profile.canIssueDocuments) {
                    "Saved. Your documents will carry these details."
                } else {
                    "Saved, but without a name nothing can be sent out."
                }
        }
    }

    fun syncNow() {
        synchroniser.syncNow()
    }

    /**
     * Ends the session on this device, and on the server.
     *
     * The shell swaps itself for the sign-in screen when the session goes, so there is
     * nothing to navigate. Work already written stays in the local database: signing out is
     * not a way to discard anything, and anything not yet uploaded goes up on the next sign
     * in rather than being lost here.
     */
    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    /**
     * Writes the studio's whole record to a file it keeps.
     *
     * Reported through the same note as everything else on this screen, because the only
     * thing worth saying is where the file went — and on a phone, that it was offered to the
     * share sheet as well, since a file saved somewhere a studio cannot find is not a copy
     * of anything.
     */
    fun exportStudio() {
        viewModelScope.launch {
            savedNote.value = "Collecting everything…"

            runCatching { auth.exportStudio() }
                .onSuccess { content ->
                    val file =
                        Document(
                            baseName = "yellowtrack-export",
                            format = DocumentFormat.Json,
                            content = content,
                        )

                    val saved =
                        runCatching {
                            if (documentSink.canShare) documentSink.share(file) else documentSink.save(file)
                        }

                    savedNote.value =
                        saved.fold(
                            onSuccess = { "Saved to ${it.location}" },
                            // The download arrived and the disk refused it. Said as its own
                            // failure, because "could not export" would send somebody
                            // looking at the server.
                            onFailure = { "Everything was collected, but it could not be written: ${it.message}" },
                        )
                }.onFailure { savedNote.value = it.message ?: "That could not be downloaded." }
        }
    }

    /**
     * Deletes the studio, after the password.
     *
     * Nothing is reported on success and nothing needs to be: the session is gone with the
     * studio, and the shell swaps itself for the sign-in screen the moment it notices. A
     * message written to a screen that is about to be replaced is a message nobody reads.
     */
    fun deleteAccount(
        password: String,
        onRefused: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { auth.deleteAccount(password) }
                .onFailure { onRefused(it.message ?: "That could not be done.") }
        }
    }

    private fun SessionState.SignedIn.toSummary(): AccountSummary =
        AccountSummary(
            email = session.email,
            studioName = session.studioName,
            isHardwareBacked = auth.isHardwareBacked,
        )

    private fun SyncStatus.toSummary(): SyncSummary =
        when (this) {
            SyncStatus.Idle -> SyncSummary()
            SyncStatus.Working -> SyncSummary(isWorking = true)
            is SyncStatus.Succeeded ->
                SyncSummary(
                    lastResult =
                        if (report.isQuiet) {
                            "Up to date."
                        } else {
                            "Sent ${report.uploaded}, received ${report.downloaded}."
                        },
                    notReconciled = report.notReconciledByServer,
                )
            // Named rather than swallowed. A device that quietly stopped reconciling looks
            // identical to one with nothing to reconcile.
            is SyncStatus.Failed -> SyncSummary(lastResult = reason, isFailure = true)
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
