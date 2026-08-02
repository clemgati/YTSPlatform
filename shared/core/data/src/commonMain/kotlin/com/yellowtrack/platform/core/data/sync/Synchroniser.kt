package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** What synchronisation is doing, for anything that wants to show it. */
sealed interface SyncStatus {
    data object Idle : SyncStatus

    data object Working : SyncStatus

    data class Succeeded(
        val at: Long,
        val report: SyncReport,
    ) : SyncStatus

    /**
     * Kept as a value rather than thrown.
     *
     * A failed sync is the normal state of an application used at wedding venues, and
     * something that crashed on it would be unusable. The outbox still holds the work.
     */
    data class Failed(
        val at: Long,
        val reason: String,
    ) : SyncStatus
}

/**
 * Decides *when* to reconcile. [SyncEngine] decides what happens when it does.
 *
 * Split because the two are answerable separately: the engine's correctness is provable
 * without a clock, and this is a scheduling policy that will change as soon as anybody
 * observes real usage.
 */
class Synchroniser(
    /**
     * What to run, rather than the thing that runs it.
     *
     * A function instead of a `SyncEngine`, because scheduling has no business knowing how
     * reconciliation works — and because taking the class would make anything that wants to
     * test the *timing* build a database first.
     */
    private val reconcile: suspend () -> SyncReport,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
    private val clock: AppClock = AppClock.System,
    private val interval: Duration = DEFAULT_INTERVAL,
) {
    private val state = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    val status: StateFlow<SyncStatus> = state.asStateFlow()

    /**
     * One at a time.
     *
     * The periodic run and a studio pressing the button can arrive together, and two drains
     * over one outbox would upload the same rows twice — the second finding them already
     * applied and conflicting with the first over work nobody else touched.
     */
    private val inFlight = Mutex()

    /**
     * Reconciles now, if signed in.
     *
     * Never throws. A caller pressing a button is not the right place to handle "the venue
     * has no signal", and every failure leaves the outbox intact for the next attempt.
     */
    fun syncNow() {
        scope.launch { runOnce() }
    }

    /**
     * Reconciles on a timer for as long as [scope] lives.
     *
     * Started after sign-in rather than at launch, because a device that is not signed in
     * has nowhere to sync to and would spend the interval discovering that.
     */
    fun startPeriodicSync() {
        scope.launch {
            while (true) {
                if (auth.session.value is SessionState.SignedIn) runOnce()
                delay(interval)
            }
        }
    }

    private suspend fun runOnce() {
        if (auth.session.value !is SessionState.SignedIn) return
        if (!inFlight.tryLock()) return

        try {
            state.value = SyncStatus.Working
            val report = reconcile()
            state.value = SyncStatus.Succeeded(clock.now().toEpochMilliseconds(), report)
        } catch (_: SyncUnauthorised) {
            // The token is genuinely no longer good — a password reset, or this device
            // signed out from elsewhere. Reporting "sync failed" forever would leave a
            // studio staring at an error whose remedy is a sign-in nobody offered them.
            state.value = SyncStatus.Idle
            auth.signOut()
        } catch (error: Throwable) {
            state.value =
                SyncStatus.Failed(clock.now().toEpochMilliseconds(), error.message ?: "Could not synchronise.")
        } finally {
            inFlight.unlock()
        }
    }

    private companion object {
        /**
         * Often enough that a second device feels current, rarely enough to be invisible on
         * a phone's battery. A guess, and the first thing to revisit once anybody has used
         * this on a shoot day.
         */
        val DEFAULT_INTERVAL: Duration = 5.minutes
    }
}
