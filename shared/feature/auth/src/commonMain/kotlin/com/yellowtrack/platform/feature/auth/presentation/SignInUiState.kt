package com.yellowtrack.platform.feature.auth.presentation

import com.yellowtrack.platform.core.model.auth.EmailAddress

/** Whether the form is asking somebody to sign in or to start a studio. */
internal enum class SignInMode {
    SignIn,
    SignUp,

    /** Asking for a code. Email only. */
    ForgotPassword,

    /** Typing the code in, with the new password. */
    EnterCode,
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
    val code: String = "",
)

internal data class SignInUiState(
    val mode: SignInMode = SignInMode.SignIn,
    val fields: SignInFields = SignInFields(),
    val isWorking: Boolean = false,
    /** What went wrong, in the words the studio should read. Null when nothing has. */
    val error: String? = null,
    /**
     * Something that went right and needs saying.
     *
     * Separate from [error] because the reset flow has two moments that are neither a
     * failure nor a screen change — "a code is on its way" and "your password is changed" —
     * and colouring either of them as an error would be a lie.
     */
    val notice: String? = null,
    /**
     * Whether the device protects the token with a key it cannot extract.
     *
     * Shown rather than hidden. On a browser it is false and that is worth a studio
     * knowing before it signs in on a shared machine.
     */
    val isHardwareBacked: Boolean = true,
    /**
     * When this studio will be purged, if signing in found it on its way out.
     *
     * Non-null replaces the form with the offer to bring it back. Kept apart from [error]
     * because it is not one: the password was right, and the screen has something to give
     * rather than something to refuse.
     */
    val pendingDeletion: Long? = null,
) {
    /**
     * The address this was probably meant to be, if it looks like a slip.
     *
     * Derived rather than stored, so it cannot describe an address that is no longer in the
     * field. Null almost always — it only speaks for a near-miss of a domain people
     * actually use.
     *
     * Offered, never enforced, and never applied on the studio's behalf. `@gmail.ocm` is a
     * well-formed address and the server rightly takes it; the only thing wrong with it is
     * a guess about intent, and the studio is the one who knows. It does not gate
     * [canSubmit] for the same reason.
     */
    val emailSuggestion: String?
        get() = EmailAddress.suggestion(fields.email)

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
                when (mode) {
                    SignInMode.SignIn -> fields.password.isNotBlank()
                    SignInMode.SignUp ->
                        // The shape is required here and nowhere else. Sign-up is where an
                        // address becomes the only route back into an account, and the
                        // server refuses a malformed one anyway, so submitting is a wasted
                        // round trip. On the other three the address already belongs to
                        // somebody: accounts were created before this rule existed, and
                        // enforcing it on sign-in would lock out whoever holds one.
                        EmailAddress.isPlausible(fields.email) &&
                            fields.password.isNotBlank() &&
                            fields.name.isNotBlank() &&
                            fields.studioName.isNotBlank()
                    // An address is all the server is being asked for.
                    SignInMode.ForgotPassword -> true
                    SignInMode.EnterCode -> fields.code.isNotBlank() && fields.password.isNotBlank()
                }
}
