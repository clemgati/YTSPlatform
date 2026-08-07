package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
    /**
     * How many rows this device is still holding, as it changes.
     *
     * The trigger the timer cannot be: work that has not left the device is work only that
     * device has, and waiting out an interval chosen for idleness is the wrong response to
     * somebody having just typed something. Empty by default, so anything constructing this
     * without a database keeps the old behaviour.
     */
    private val pendingWork: Flow<Long> = emptyFlow(),
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
            var failures = 0

            while (true) {
                if (auth.session.value is SessionState.SignedIn) {
                    failures = if (runOnce()) 0 else failures + 1
                }

                delay(backoffFrom(failures))
            }
        }
    }

    /**
     * Reconciles shortly after a write, rather than at the next interval.
     *
     * Debounced, because saving a form writes several rows and one reconciliation covers
     * them. The delay is what makes this a nudge rather than a second timer: a studio
     * correcting a typo three times in a row produces one upload.
     *
     * Only ever brings a run *forward*. The periodic loop is unchanged and still runs, so
     * this cannot become the only thing keeping a device current.
     */
    fun startSyncOnWrite() {
        scope.launch {
            pendingWork
                .map { it > 0 }
                .distinctUntilChanged()
                .filter { it }
                .collectLatest {
                    // Cancelled and restarted by the next write, which is the debounce.
                    delay(AFTER_WRITE_DELAY)
                    runOnce()
                }
        }
    }

    /**
     * How long to wait before trying again, given consecutive failures.
     *
     * A device at a venue with no signal used to retry every five minutes all day, which
     * costs battery to learn something it already knew. Doubling up to an hour keeps a
     * recovered connection noticed within the hour while a dead one is asked about rarely.
     *
     * Reset by any success, so an intermittent connection does not accumulate a penalty.
     */
    private fun backoffFrom(failures: Int): Duration =
        when (failures) {
            0 -> interval
            else -> minOf(interval * (1 shl minOf(failures, MAX_DOUBLINGS)), MAX_INTERVAL)
        }

    /** Whether it reconciled. False for "not now" as well as for failure — see [backoffFrom]. */
    private suspend fun runOnce(): Boolean {
        if (auth.session.value !is SessionState.SignedIn) return false
        // Already running. Not a failure: the run in progress is doing this one's work.
        if (!inFlight.tryLock()) return true

        try {
            state.value = SyncStatus.Working
            val report = reconcile()
            state.value = SyncStatus.Succeeded(clock.now().toEpochMilliseconds(), report)
            return true
        } catch (_: SyncUnauthorised) {
            // The token is genuinely no longer good — a password reset, or this device
            // signed out from elsewhere. Reporting "sync failed" forever would leave a
            // studio staring at an error whose remedy is a sign-in nobody offered them.
            state.value = SyncStatus.Idle
            auth.signOut()
            // Not a failure to back off from. There is nothing to retry until somebody
            // signs in, and the loop above stops running while signed out anyway.
            return true
        } catch (error: Throwable) {
            state.value =
                SyncStatus.Failed(clock.now().toEpochMilliseconds(), error.message ?: "Could not synchronise.")
            return false
        } finally {
            inFlight.unlock()
        }
    }

    /**
     * Visible so the timings can be asserted rather than guessed at. A test that hard-codes
     * ten seconds passes for a while after somebody changes the constant.
     */
    internal companion object {
        /**
         * Often enough that a second device feels current, rarely enough to be invisible on
         * a phone's battery. A guess, and the first thing to revisit once anybody has used
         * this on a shoot day.
         */
        val DEFAULT_INTERVAL: Duration = 5.minutes

        /** Long enough to cover a form being filled in, short enough to feel immediate. */
        val AFTER_WRITE_DELAY: Duration = 10.seconds

        /** The ceiling a failing device backs off to. */
        val MAX_INTERVAL: Duration = 1.hours

        /** Four doublings reaches the ceiling from five minutes; more would only overflow. */
        const val MAX_DOUBLINGS = 4
    }
}
