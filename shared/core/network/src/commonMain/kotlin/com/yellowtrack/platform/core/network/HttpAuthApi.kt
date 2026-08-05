package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.data.auth.AuthApi
import com.yellowtrack.platform.core.data.auth.AuthFailure
import com.yellowtrack.platform.core.data.auth.StoredSession
import com.yellowtrack.platform.core.model.auth.DeleteAccountRequest
import com.yellowtrack.platform.core.model.auth.DeleteAccountResponse
import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.core.model.auth.ForgotPasswordRequest
import com.yellowtrack.platform.core.model.auth.PendingDeletionResponse
import com.yellowtrack.platform.core.model.auth.ResetPasswordRequest
import com.yellowtrack.platform.core.model.auth.RestoreAccountRequest
import com.yellowtrack.platform.core.model.auth.SessionResponse
import com.yellowtrack.platform.core.model.auth.SignInRequest
import com.yellowtrack.platform.core.model.auth.SignUpRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Signing in, over HTTP.
 *
 * The failures are translated here rather than passed up as status codes, because the
 * difference between "that password is wrong" and "there is no signal" is the whole of what
 * a sign-in screen needs to say, and a screen should not be reading HTTP.
 */
class HttpAuthApi(
    private val client: HttpClient,
    private val baseUrl: String,
) : AuthApi {
    override suspend fun signIn(
        email: String,
        password: String,
    ): StoredSession =
        reaching {
            client.post("$baseUrl/auth/sign-in") {
                contentType(ContentType.Application.Json)
                setBody(SignInRequest(email = email.trim(), password = password))
            }
        }.toSession()

    override suspend fun signUp(
        email: String,
        password: String,
        name: String,
        studioName: String,
    ): StoredSession =
        reaching {
            client.post("$baseUrl/auth/sign-up") {
                contentType(ContentType.Application.Json)
                setBody(
                    SignUpRequest(
                        email = email.trim(),
                        password = password,
                        name = name.trim(),
                        studioName = studioName.trim(),
                    ),
                )
            }
        }.toSession()

    override suspend fun requestPasswordReset(email: String) {
        reaching {
            client.post("$baseUrl/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest(email.trim()))
            }
        }
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ) {
        reaching {
            client.post("$baseUrl/auth/reset-password") {
                contentType(ContentType.Application.Json)
                setBody(
                    ResetPasswordRequest(
                        email = email.trim(),
                        // Typed off a screen, so capitals and stray spaces are the reader's
                        // problem to have and not the studio's.
                        code = code.trim().uppercase(),
                        newPassword = newPassword,
                    ),
                )
            }
        }
    }

    override suspend fun restoreAccount(
        email: String,
        password: String,
    ): StoredSession =
        reaching {
            client.post("$baseUrl/auth/restore-account") {
                contentType(ContentType.Application.Json)
                setBody(RestoreAccountRequest(email.trim(), password))
            }
        }.toSession()

    override suspend fun signOut(token: String) {
        reaching {
            client.post("$baseUrl/auth/sign-out") { bearerAuth(token) }
        }
    }

    /**
     * The export, as the text the server sent.
     *
     * `bodyAsText` rather than a decode. The body is the studio's file and this only carries
     * it to a disk — parsing it here would mean teaching the client every entity a second
     * time, and quietly dropping whichever ones it had not been taught.
     */
    override suspend fun exportStudio(token: String): String =
        readable {
            reaching {
                client.get("$baseUrl/auth/export") { bearerAuth(token) }
            }.bodyAsText()
        }

    override suspend fun deleteAccount(
        token: String,
        password: String,
    ): Long =
        readable {
            reaching {
                client.post("$baseUrl/auth/delete-account") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(DeleteAccountRequest(password))
                }
            }.body<DeleteAccountResponse>().purgeAfter
        }

    /**
     * Separates "could not ask" from "was told no".
     *
     * Anything thrown by the client — no route to host, DNS, a timeout — is the connection
     * rather than the credentials, and telling a photographer their password is wrong when
     * the venue has no signal is both untrue and the sort of thing that gets a password
     * reset nobody needed.
     */
    private suspend fun reaching(request: suspend () -> HttpResponse): HttpResponse {
        val response =
            try {
                request()
            } catch (cancellation: CancellationException) {
                // Not a failure. Swallowing it reported "could not reach the server" to
                // somebody who had simply navigated away, and left the coroutine looking
                // as though it had finished normally.
                throw cancellation
            } catch (_: Throwable) {
                // Throwable rather than Exception: a browser refusing a request for its own
                // reasons does not always surface as one, and what escaped instead was
                // reported as "something went wrong here rather than at the server" — which
                // is precisely the opposite of what had happened.
                throw AuthFailure.Unreachable
            }

        if (response.status.isSuccess()) return response

        throw when (response.status) {
            HttpStatusCode.Unauthorized -> AuthFailure.BadCredentials
            HttpStatusCode.Conflict -> AuthFailure.EmailAlreadyRegistered
            // The password was right and the studio is on its way out. Its own case because
            // the screen has something to offer rather than something to apologise for.
            HttpStatusCode.Forbidden ->
                runCatching { response.body<PendingDeletionResponse>() }
                    .fold(
                        onSuccess = { AuthFailure.PendingDeletion(it.purgeAfter) },
                        onFailure = { AuthFailure.Rejected(response.status.description) },
                    )
            // The server's validation messages are written for a person to read, so they
            // are passed through rather than replaced with something vaguer.
            HttpStatusCode.BadRequest -> AuthFailure.Rejected(response.reason())
            else -> AuthFailure.Rejected("The server could not complete that (${response.status.value}).")
        }
    }

    private suspend fun HttpResponse.reason(): String =
        runCatching { body<ErrorResponse>().error }
            .getOrElse { "That request could not be completed." }

    /**
     * Reading a successful answer, which can still fail.
     *
     * A body this application cannot parse is usually version skew — a server older than
     * the client, answering in a shape it no longer knows. That is worth saying, because
     * the alternative is a studio told that something went wrong on their own device when
     * the fix is a deployment.
     */
    private suspend fun <T> readable(read: suspend () -> T): T =
        try {
            read()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            throw AuthFailure.Rejected(
                "The server answered in a way this app could not read. It may be running an " +
                    "older version.",
            )
        }

    private suspend fun HttpResponse.toSession(): StoredSession =
        readable { body<SessionResponse>() }.let { response ->
            StoredSession(
                token = response.token,
                expiresAt = response.expiresAt,
                accountId = response.accountId,
                email = response.email,
                name = response.name,
                studioId = response.studioId,
                studioName = response.studioName,
            )
        }
}
