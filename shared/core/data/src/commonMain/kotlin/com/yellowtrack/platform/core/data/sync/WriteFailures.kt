package com.yellowtrack.platform.core.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Why the last write did not happen, for a screen to say so.
 *
 * ADR 0012 made writes able to fail: they are sent to the server and awaited, so "you are
 * offline" became an outcome rather than something that could not occur. Every ViewModel that
 * writes therefore needs the same three lines, and this is them written once — the fourth
 * hand-copied version is where one quietly forgets the `onFailure` and a form closes on
 * nothing.
 *
 * Takes a scope rather than a ViewModel so it can live beside [WriteFailed] instead of
 * dragging lifecycle into a module that has no other use for it.
 */
class WriteFailures {
    private val state = MutableStateFlow<String?>(null)

    /** Null when nothing has failed since the screen last read it. */
    val message: StateFlow<String?> = state.asStateFlow()

    /** Cleared once shown, so a refusal does not outlive the attempt that caused it. */
    fun dismiss() {
        state.value = null
    }

    /**
     * Runs a write and reports a refusal.
     *
     * Anything that is not a [WriteFailed] is rethrown. A bug dressed up as a network problem
     * is worse than a crash: the crash gets fixed, and a studio told "you are offline" while
     * connected goes looking at its router.
     */
    fun launchWrite(
        scope: CoroutineScope,
        block: suspend () -> Unit,
    ) {
        scope.launch {
            runCatching { block() }
                .onFailure { failure ->
                    if (failure !is WriteFailed) throw failure
                    state.value = failure.message
                }
        }
    }
}
