package com.yellowtrack.platform.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

/**
 * The API in front of Postgres.
 *
 * Clients cannot reach the database directly — there is no Postgres driver for iOS or
 * wasm, and exposing a database to client applications would be wrong regardless. See
 * `docs/adr/0007-ktor-server-over-cloud-postgres.md`.
 *
 * Deliberately almost empty. This module exists at this point in the milestone to prove
 * one thing: that `core:model` compiles and serialises on a JVM server, which is the bet
 * the whole architecture rests on. Endpoints arrive with the schema and accounts.
 */
fun main() {
    embeddedServer(
        factory = Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT,
        host = "127.0.0.1",
        module = Application::module,
    ).start(wait = true)
}

/**
 * Bound to the loopback address on purpose: Apache terminates TLS and proxies to
 * localhost, so the JAR has no business listening on a public interface.
 */
private const val DEFAULT_PORT = 8080

fun Application.module() {
    install(ContentNegotiation) {
        json(apiJson)
    }

    routing {
        get("/health") {
            call.respond(Health(status = "ok"))
        }
    }
}

/** Answers the proxy's health check, and nothing more. */
@Serializable
data class Health(
    val status: String,
)
