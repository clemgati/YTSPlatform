package com.yellowtrack.platform.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.data.auth.AuthFailure
import com.yellowtrack.platform.core.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Signing in, or starting a studio.
 *
 * Both are the same form with two extra fields, because they are the same decision from the
 * studio's side — "get me into my data" — and splitting them across two screens makes
 * somebody who guessed wrong start again.
 */
internal class SignInViewModel(
    private val auth: AuthRepository,
) : ViewModel() {
    private val state = MutableStateFlow(SignInUiState(isHardwareBacked = auth.isHardwareBacked))

    val uiState: StateFlow<SignInUiState> = state.asStateFlow()

    fun onEmailChanged(value: String) = edit { it.copy(email = value) }

    fun onPasswordChanged(value: String) = edit { it.copy(password = value) }

    fun onNameChanged(value: String) = edit { it.copy(name = value) }

    fun onStudioNameChanged(value: String) = edit { it.copy(studioName = value) }

    fun onCodeChanged(value: String) = edit { it.copy(code = value) }

    /** Keeps what has been typed, because the email is the same in all four. */
    fun onModeChanged(mode: SignInMode) {
        state.value = state.value.copy(mode = mode, error = null, notice = null)
    }

    fun submit() {
        val current = state.value
        if (!current.canSubmit) return

        state.value = current.copy(isWorking = true, error = null, notice = null)

        viewModelScope.launch {
            val outcome =
                runCatching {
                    when (current.mode) {
                        SignInMode.SignIn ->
                            auth.signIn(current.fields.email, current.fields.password)
                        SignInMode.SignUp ->
                            auth.signUp(
                                email = current.fields.email,
                                password = current.fields.password,
                                name = current.fields.name,
                                studioName = current.fields.studioName,
                            )
                        SignInMode.ForgotPassword ->
                            auth.requestPasswordReset(current.fields.email)
                        SignInMode.EnterCode ->
                            auth.resetPassword(
                                email = current.fields.email,
                                code = current.fields.code,
                                newPassword = current.fields.password,
                            )
                    }
                }

            state.value =
                if (outcome.isFailure) {
                    state.value.copy(isWorking = false, error = outcome.exceptionOrNull()?.readable())
                } else {
                    // Signing in and signing up set nothing here: the session changes and the
                    // shell swaps this screen out. The two reset steps stay on the screen and
                    // have to say what happened themselves.
                    when (current.mode) {
                        SignInMode.ForgotPassword ->
                            state.value.copy(
                                isWorking = false,
                                mode = SignInMode.EnterCode,
                                // Worded as the server answers it, because the server will
                                // not say whether the address had an account and neither can
                                // this.
                                notice = "If that address has an account, a code is on its way. It expires in an hour.",
                                fields = state.value.fields.copy(password = ""),
                            )
                        SignInMode.EnterCode ->
                            state.value.copy(
                                isWorking = false,
                                mode = SignInMode.SignIn,
                                notice =
                                    "Your password is changed. Every device has been signed out, " +
                                        "including this one.",
                                fields = state.value.fields.copy(password = "", code = ""),
                            )
                        else -> state.value.copy(isWorking = false)
                    }
                }
        }
    }

    /**
     * Anything not an [AuthFailure] is a bug rather than a refusal.
     *
     * Reported as itself rather than as "email or password is wrong", which would send a
     * studio round a password reset that was never going to help.
     */
    private fun Throwable.readable(): String =
        when (this) {
            is AuthFailure -> message ?: "That did not work."
            else -> "Something went wrong here rather than at the server. ${message.orEmpty()}".trim()
        }

    private fun edit(change: (SignInFields) -> SignInFields) {
        state.value = state.value.copy(fields = change(state.value.fields), error = null)
    }
}
