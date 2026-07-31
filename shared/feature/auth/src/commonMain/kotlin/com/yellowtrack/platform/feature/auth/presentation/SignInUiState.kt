package com.yellowtrack.platform.feature.auth.presentation

/** Whether the form is asking somebody to sign in or to start a studio. */
internal enum class SignInMode {
    SignIn,
    SignUp,
}

/**
 * The form, as text.
 *
 * Held as strings rather than as a request object for the same reason the Settings form is:
 * a half-typed email is not a valid one, and forcing it through validation on every
 * keystroke means either rejecting the keystroke or storing something invalid.
 */
internal data class SignInFields(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val studioName: String = "",
)

internal data class SignInUiState(
    val mode: SignInMode = SignInMode.SignIn,
    val fields: SignInFields = SignInFields(),
    val isWorking: Boolean = false,
    /** What went wrong, in the words the studio should read. Null when nothing has. */
    val error: String? = null,
    /**
     * Whether the device protects the token with a key it cannot extract.
     *
     * Shown rather than hidden. On a browser it is false and that is worth a studio
     * knowing before it signs in on a shared machine.
     */
    val isHardwareBacked: Boolean = true,
) {
    /**
     * Whether the form can be submitted.
     *
     * Deliberately permissive: the server is the authority on whether a credential is
     * acceptable, and a client that guesses at those rules will eventually disagree with it.
     * This only stops requests that cannot possibly succeed.
     */
    val canSubmit: Boolean
        get() =
            !isWorking &&
                fields.email.isNotBlank() &&
                fields.password.isNotBlank() &&
                (mode == SignInMode.SignIn || (fields.name.isNotBlank() && fields.studioName.isNotBlank()))
}
