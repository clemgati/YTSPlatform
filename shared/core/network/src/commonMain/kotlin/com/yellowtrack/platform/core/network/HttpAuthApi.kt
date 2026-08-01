package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.data.auth.AuthApi
import com.yellowtrack.platform.core.data.auth.AuthFailure
import com.yellowtrack.platform.core.data.auth.StoredSession
import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.core.model.auth.ForgotPasswordRequest
import com.yellowtrack.platform.core.model.auth.ResetPasswordRequest
import com.yellowtrack.platform.core.model.auth.SessionResponse
import com.yellowtrack.platform.core.model.auth.SignInRequest
import com.yellowtrack.platform.core.model.auth.SignUpRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

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

    override suspend fun signOut(token: String) {
        reaching {
            client.post("$baseUrl/auth/sign-out") { bearerAuth(token) }
        }
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
            } catch (_: Exception) {
                throw AuthFailure.Unreachable
            }

        if (response.status.isSuccess()) return response

        throw when (response.status) {
            HttpStatusCode.Unauthorized -> AuthFailure.BadCredentials
            HttpStatusCode.Conflict -> AuthFailure.EmailAlreadyRegistered
            // The server's validation messages are written for a person to read, so they
            // are passed through rather than replaced with something vaguer.
            HttpStatusCode.BadRequest -> AuthFailure.Rejected(response.reason())
            else -> AuthFailure.Rejected("The server could not complete that (${response.status.value}).")
        }
    }

    private suspend fun HttpResponse.reason(): String =
        runCatching { body<ErrorResponse>().error }
            .getOrElse { "That request could not be completed." }

    private suspend fun HttpResponse.toSession(): StoredSession =
        body<SessionResponse>().let { response ->
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
