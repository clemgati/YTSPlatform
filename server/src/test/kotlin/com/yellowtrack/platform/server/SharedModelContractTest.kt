package com.yellowtrack.platform.server

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The bet ADR 0007 rests on: one definition of every entity, compiled into both sides.
 *
 * If the domain model could not cross this boundary the argument for a Kotlin server over
 * a Node one would collapse, so it is worth proving before anything is built on top of it
 * rather than discovering it during the first real endpoint.
 *
 * These entities are chosen for the things that usually break: inline value classes,
 * money as minor units plus a currency, instants, enums, nested lists, and nullable
 * fields that mean something when null.
 */
class SharedModelContractTest {
    private val now = Instant.fromEpochMilliseconds(1_781_100_000_000)
    private val studioId = StudioId("00000000-0000-7000-8000-000000000001")

    private fun session() =
        Session(
            id = SessionId("session-1"),
            studioId = studioId,
            projectId = ProjectId("project-1"),
            title = "Wedding day",
            kind = SessionKind.Shoot,
            status = SessionStatus.Confirmed,
            startsAt = now,
            endsAt = now + 9.days,
            timeZoneId = "Europe/London",
            locationName = "Thornbury Manor",
            audit = AuditMetadata.createdAt(now),
        )

    private fun invoice() =
        Invoice(
            id = InvoiceId("invoice-1"),
            studioId = studioId,
            projectId = ProjectId("project-1"),
            number = "INV-004",
            kind = InvoiceKind.Balance,
            status = InvoiceStatus.Sent,
            currency = CurrencyCode.GBP,
            lines =
                listOf(
                    LineItem("Wedding coverage", Money(400_000L, CurrencyCode.GBP)),
                    LineItem("Album", Money(120_000L, CurrencyCode.GBP), taxRateBasisPoints = 2_000),
                ),
            issuedAt = now,
            dueAt = now + 14.days,
            audit = AuditMetadata.createdAt(now),
        )

    // --- The model crosses the wire ---------------------------------------------------

    @Test
    fun `a session survives the server's json unchanged`() {
        val original = session()

        val decoded = apiJson.decodeFromString<Session>(apiJson.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `an invoice keeps its money to the minor unit`() {
        val original = invoice()

        val decoded = apiJson.decodeFromString<Invoice>(apiJson.encodeToString(original))

        assertEquals(original, decoded)
        assertEquals(
            original.total,
            decoded.total,
            "the total is computed from the lines, so a lost line or a rounded price shows here",
        )
        assertEquals(CurrencyCode.GBP, decoded.currency, "not everyone charges in dollars")
    }

    @Test
    fun `an inline value class travels as its value rather than as an object`() {
        val encoded = apiJson.encodeToString(session())

        assertTrue(
            encoded.contains("\"id\":\"session-1\""),
            "a value class wrapped in an object would double the size of every identifier: $encoded",
        )
    }

    @Test
    fun `null means null rather than absent`() {
        val encoded = apiJson.encodeToString(session())

        assertTrue(
            encoded.contains("\"deletedAt\":null"),
            "a dropped null is re-defaulted by the reader, and a tombstone that vanishes is a row that comes back",
        )
    }

    @Test
    fun `a field the reader has never heard of does not break it`() {
        // The shape a client one build behind sees during a rolling deploy.
        val fromNewerServer =
            Json
                .parseToJsonElement(apiJson.encodeToString(session()))
                .toString()
                .removeSuffix("}") + ""","somethingAddedLater":42}"""

        val decoded = apiJson.decodeFromString<Session>(fromNewerServer)

        assertEquals(session(), decoded)
    }

    // --- And through the server itself --------------------------------------------------

    @Test
    fun `the pipeline serialises a domain entity, not just the test's json instance`() =
        testApplication {
            application {
                module(TestDatabase.database)
                routing {
                    get("/a-session") { call.respond(session()) }
                }
            }

            val body = client.get("/a-session").bodyAsText()

            assertEquals(
                session(),
                apiJson.decodeFromString<Session>(body),
                "content negotiation has to be wired to the same json the model was written for",
            )
        }

    @Test
    fun `the health endpoint answers the proxy`() =
        testApplication {
            application { module(TestDatabase.database) }

            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(Health("ok"), apiJson.decodeFromString<Health>(response.bodyAsText()))
        }
}
