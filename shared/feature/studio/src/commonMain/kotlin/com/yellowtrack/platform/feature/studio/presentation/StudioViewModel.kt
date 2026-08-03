package com.yellowtrack.platform.feature.studio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.GearRepository
import com.yellowtrack.platform.core.data.LightingRecipeRepository
import com.yellowtrack.platform.core.data.StorageVolumeRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.StudioProfileRepository
import com.yellowtrack.platform.core.data.currency
import com.yellowtrack.platform.core.data.observeCurrency
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.LightSetup
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.media.VolumeStatus
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.studio.presentation.mapper.buildInventory
import com.yellowtrack.platform.feature.studio.presentation.mapper.buildRegister
import com.yellowtrack.platform.feature.studio.presentation.mapper.toItem
import com.yellowtrack.platform.feature.studio.presentation.model.NewGearItem
import com.yellowtrack.platform.feature.studio.presentation.model.NewLightingRecipe
import com.yellowtrack.platform.feature.studio.presentation.model.NewVolume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * The studio's own things: what it owns and how it lit the last one that worked.
 *
 * Neither half is client-facing, which is exactly why they get neglected — and why an
 * uninsured body and a lighting set-up rebuilt from memory both cost real money.
 */
internal class StudioViewModel(
    private val gearRepository: GearRepository,
    private val recipeRepository: LightingRecipeRepository,
    private val volumeRepository: StorageVolumeRepository,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val studioProfileRepository: StudioProfileRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StudioUiState> =
        retryTrigger
            .flatMapLatest {
                val now = clock.now()

                combine(
                    gearRepository.observeGear(),
                    recipeRepository.observeRecipes(),
                    studioProfileRepository.observeCurrency(),
                    volumeRepository.observeVolumes(),
                    volumeRepository.observeCopyCounts(),
                ) { gear, recipes, currency, volumes, copyCounts ->
                    StudioUiState(
                        content =
                            UiState.Success(
                                StudioContent(
                                    inventory = buildInventory(gear, now, timeZone, currency),
                                    recipes = recipes.map { it.toItem() },
                                    register = buildRegister(volumes, copyCounts, now, timeZone),
                                    today = now.toLocalDateTime(timeZone).date,
                                    currency = currency,
                                ),
                            ),
                    )
                }.catch { throwable ->
                    emit(
                        StudioUiState(
                            content = UiState.Error(throwable.message ?: "Unable to load the studio."),
                        ),
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = StudioUiState(content = UiState.Loading),
            )

    fun retry() {
        retryTrigger.value += 1
    }

    /**
     * Records a piece of gear, or corrects one already recorded.
     *
     * One path for both, because a correction is the same item stated better. Gear could be
     * added and removed but never edited, so a mistyped serial number — the field an
     * insurer needs and the one nobody checks twice — could only be fixed by deleting the
     * item and entering it again, losing its service history in the process.
     *
     * With [existingId] the stored item is loaded and its audit touched, so the version
     * rises and the change reaches the other devices. Everything the form does not show is
     * carried across from it untouched.
     */
    fun saveGearItem(
        form: NewGearItem,
        existingId: GearItemId? = null,
    ) {
        viewModelScope.launch {
            if (form.name.isBlank()) return@launch

            val now = clock.now()
            val existing = existingId?.let { gearRepository.getGearItem(it) }

            gearRepository.saveGearItem(
                GearItem(
                    id = existing?.id ?: GearItemId.new(),
                    studioId = studioContext.studioId,
                    name = form.name.trim(),
                    category = form.category,
                    status = form.status,
                    serialNumber = form.serialNumber?.trim()?.ifBlank { null },
                    purchasePrice = form.purchasePrice?.let { parseMoney(it, studioProfileRepository.currency()) },
                    purchasedOn = form.purchasedOn?.toLocalDateOrNull(),
                    // Stored as an instant because a service is an event, but entered as a
                    // date because nobody remembers the hour they dropped a body off.
                    //
                    // The stored instant is kept when the date has not moved. Marking gear
                    // serviced records a real time; rebuilding it from the form's date
                    // would drag that back to midnight every time somebody corrected the
                    // name, quietly rewriting a fact nobody was editing.
                    lastServicedAt =
                        form.lastServicedOn?.toLocalDateOrNull()?.let { entered ->
                            val stored = existing?.lastServicedAt
                            when (entered) {
                                stored?.toLocalDateTime(timeZone)?.date -> stored
                                else -> entered.atStartOfDayIn(timeZone)
                            }
                        },
                    notes = form.notes?.trim()?.ifBlank { null },
                    audit = existing?.audit?.touched(now) ?: AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /** Marks gear serviced today, which is the only service date anyone records in practice. */
    fun markServiced(gearItemId: GearItemId) {
        viewModelScope.launch {
            val existing = gearRepository.getGearItem(gearItemId) ?: return@launch
            val now = clock.now()

            gearRepository.saveGearItem(existing.copy(lastServicedAt = now, audit = existing.audit.touched(now)))
        }
    }

    fun deleteGearItem(gearItemId: GearItemId) {
        viewModelScope.launch { gearRepository.deleteGearItem(gearItemId) }
    }

    /**
     * Writes down a lighting set-up, or corrects one already written.
     *
     * The lights are replaced wholesale rather than reconciled, unlike a client's contacts.
     * They can be, because the form shows every one of them: a recipe is its lights, and
     * there is nothing off-screen for a rebuild to discard.
     */
    fun saveRecipe(
        form: NewLightingRecipe,
        existingId: LightingRecipeId? = null,
    ) {
        viewModelScope.launch {
            if (form.name.isBlank()) return@launch
            val existing = existingId?.let { recipeRepository.getRecipe(it) }
            val now = clock.now()

            recipeRepository.saveRecipe(
                LightingRecipe(
                    id = existing?.id ?: LightingRecipeId.new(),
                    studioId = studioContext.studioId,
                    name = form.name.trim(),
                    lights =
                        form.lights.map { light ->
                            LightSetup(
                                role = light.role,
                                instrument = light.instrument.trim(),
                                modifier = light.modifier?.trim()?.ifBlank { null },
                                power = light.power?.trim()?.ifBlank { null },
                                position = light.position?.trim()?.ifBlank { null },
                                distance = light.distance?.trim()?.ifBlank { null },
                            )
                        },
                    notes = form.notes?.trim()?.ifBlank { null },
                    audit = existing?.audit?.touched(now) ?: AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Records a drive, or corrects one already recorded.
     *
     * What the form does not show is carried across. `lastCheckedAt` in particular: it is
     * set by a different button entirely, and it is the only thing separating a backup a
     * studio has from one it believes it has — so relabelling a drive must not quietly say
     * nobody has ever read it.
     */
    fun saveVolume(
        form: NewVolume,
        existingId: StorageVolumeId? = null,
    ) {
        viewModelScope.launch {
            if (form.label.isBlank()) return@launch

            val now = clock.now()
            val existing = existingId?.let { volumeRepository.getVolume(it) }

            volumeRepository.saveVolume(
                StorageVolume(
                    id = existing?.id ?: StorageVolumeId.new(),
                    studioId = studioContext.studioId,
                    label = form.label.trim(),
                    kind = form.kind,
                    // Both carried across rather than taken from the form, which shows
                    // neither. Failing a drive and reading a drive are separate actions on
                    // the register, and a relabel that quietly revived a dead drive would
                    // put every shoot with a copy on it back to counting it.
                    status = existing?.status ?: form.status,
                    isOffsite = form.isOffsite,
                    lastCheckedAt = existing?.lastCheckedAt,
                    notes = form.notes?.trim()?.ifBlank { null },
                    audit = existing?.audit?.touched(now) ?: AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Records that someone opened the drive and found it readable.
     *
     * The only thing that distinguishes a backup a studio has from one it believes it has,
     * and the same act the session page records per copy.
     */
    fun markVolumeChecked(volumeId: StorageVolumeId) {
        viewModelScope.launch {
            val existing = volumeRepository.getVolume(volumeId) ?: return@launch
            val now = clock.now()

            volumeRepository.saveVolume(existing.copy(lastCheckedAt = now, audit = existing.audit.touched(now)))
        }
    }

    /**
     * Marks a drive as failed.
     *
     * Every shoot with a copy on it immediately reports one fewer copy, which is the whole
     * reason the register exists.
     */
    fun setVolumeStatus(
        volumeId: StorageVolumeId,
        status: VolumeStatus,
    ) {
        viewModelScope.launch {
            val existing = volumeRepository.getVolume(volumeId) ?: return@launch

            volumeRepository.saveVolume(existing.copy(status = status, audit = existing.audit.touched(clock.now())))
        }
    }

    fun deleteVolume(volumeId: StorageVolumeId) {
        viewModelScope.launch { volumeRepository.deleteVolume(volumeId) }
    }

    fun deleteRecipe(recipeId: LightingRecipeId) {
        viewModelScope.launch { recipeRepository.deleteRecipe(recipeId) }
    }

    private fun String.toLocalDateOrNull(): LocalDate? =
        trim().takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
