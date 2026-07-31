package com.yellowtrack.platform.server.sync

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.solar.GeoCoordinates
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.time.Instant

/**
 * The three entities in the 0.7.0 slice, and how each crosses between a Postgres row and
 * the shared model.
 *
 * ADR 0008 scoped synchronisation to `Client`, `Project` and `Session` before the other
 * eighteen, because sync is the one feature here whose bugs are invisible and proving the
 * mechanism against real conflicts on three entities is worth more than building it
 * eighteen times before finding out.
 *
 * The types are `core:model`'s own, which is the whole argument of ADR 0007: adding a field
 * to `Session` is a compile error here rather than a field that quietly stops crossing the
 * wire.
 *
 * ## Sync operates on rows, not on aggregates
 *
 * `Client` carries a `contacts` list, and that list is *not* what this synchronises.
 * Contacts live in `contact` and `client_contact`, which are their own rows with their own
 * ids, and ADR 0008 decision 5 has child collections reconcile by union on those ids rather
 * than travelling inside their parent — precisely so a stale parent cannot discard a child
 * recorded elsewhere.
 *
 * So a pushed `Client` must arrive with no contacts, and [SyncedEntity.Clients] refuses one
 * that does not. Silently dropping them would be the more convenient choice and would mean
 * a device believing it had uploaded something it had not.
 */
sealed interface SyncedEntity<T> {
    /** Matches `sync_conflict.entity_table`, and the table this reads and writes. */
    val table: String

    fun identify(entity: T): String

    fun studioOf(entity: T): String

    fun versionOf(entity: T): Int

    fun deletedAtOf(entity: T): Long?

    fun read(rows: ResultSet): T

    /** Serialises for `sync_conflict`, where both sides of a discarded edit are kept. */
    fun encode(entity: T): String

    /**
     * Writes [entity], overwriting any existing row.
     *
     * [version] is passed separately because reconciliation decides it: a push that
     * conflicts still has to leave the row on a version *ahead* of both sides, or two
     * devices sitting on the same number would conflict with each other forever.
     */
    fun upsert(
        connection: Connection,
        entity: T,
        version: Int,
    )

    object Clients : SyncedEntity<Client> {
        override val table = "client"

        override fun identify(entity: Client) = entity.id.value

        override fun studioOf(entity: Client) = entity.studioId.value

        override fun versionOf(entity: Client) = entity.audit.version

        override fun deletedAtOf(entity: Client) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Client =
            Client(
                id = ClientId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                accountName = rows.getString("account_name"),
                accountType = enumOrDefault(rows.getString("account_type"), ClientAccountType.Individual),
                // Deliberately empty: contacts are their own rows and their own sync units.
                contacts = emptyList(),
                notes = rows.getString("notes"),
                tags = decodeTags(rows.getString("tags")),
                audit = rows.audit(),
            )

        override fun encode(entity: Client) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Client,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO client(id, studio_id, account_name, account_type, notes, tags,
                                       created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        account_name = EXCLUDED.account_name,
                        account_type = EXCLUDED.account_type,
                        notes        = EXCLUDED.notes,
                        tags         = EXCLUDED.tags,
                        updated_at   = EXCLUDED.updated_at,
                        deleted_at   = EXCLUDED.deleted_at,
                        version      = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.accountName)
                    statement.setString(4, entity.accountType.name)
                    statement.setString(5, entity.notes)
                    statement.setString(6, payloadJson.encodeToString(entity.tags))
                    statement.setLong(7, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(8, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(9, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(10, version)
                    statement.executeUpdate()
                }
        }

        /** Why a `Client` with contacts is refused rather than quietly stripped. */
        fun rejectionReason(entity: Client): String? =
            if (entity.contacts.isEmpty()) {
                null
            } else {
                "contacts do not travel inside a client. They are their own rows and reconcile by " +
                    "union on their own ids, and are not yet in the synchronised slice"
            }
    }

    object Projects : SyncedEntity<Project> {
        override val table = "project"

        override fun identify(entity: Project) = entity.id.value

        override fun studioOf(entity: Project) = entity.studioId.value

        override fun versionOf(entity: Project) = entity.audit.version

        override fun deletedAtOf(entity: Project) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Project =
            Project(
                id = ProjectId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                clientId = ClientId(rows.getString("client_id")),
                name = rows.getString("name"),
                serviceLine = enumOrDefault(rows.getString("service_line"), ServiceLine.Other),
                status = enumOrDefault(rows.getString("status"), ProjectStatus.Enquiry),
                serviceTemplateId = rows.getString("service_template_id")?.let(::ServiceTemplateId),
                contractValue =
                    moneyOf(
                        rows.getNullableLong("contract_value_minor"),
                        rows.getString("contract_currency"),
                    ),
                enquiredAt = rows.getNullableLong("enquired_at")?.let(Instant::fromEpochMilliseconds),
                bookedAt = rows.getNullableLong("booked_at")?.let(Instant::fromEpochMilliseconds),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Project) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Project,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                        service_template_id, contract_value_minor, contract_currency,
                                        enquired_at, booked_at, notes,
                                        created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        client_id            = EXCLUDED.client_id,
                        name                 = EXCLUDED.name,
                        service_line         = EXCLUDED.service_line,
                        status               = EXCLUDED.status,
                        service_template_id  = EXCLUDED.service_template_id,
                        contract_value_minor = EXCLUDED.contract_value_minor,
                        contract_currency    = EXCLUDED.contract_currency,
                        enquired_at          = EXCLUDED.enquired_at,
                        booked_at            = EXCLUDED.booked_at,
                        notes                = EXCLUDED.notes,
                        updated_at           = EXCLUDED.updated_at,
                        deleted_at           = EXCLUDED.deleted_at,
                        version              = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.clientId.value)
                    statement.setString(4, entity.name)
                    statement.setString(5, entity.serviceLine.name)
                    statement.setString(6, entity.status.name)
                    statement.setString(7, entity.serviceTemplateId?.value)
                    statement.setNullableLong(8, entity.contractValue?.minorUnits)
                    statement.setString(9, entity.contractValue?.currency?.code)
                    statement.setNullableLong(10, entity.enquiredAt?.toEpochMilliseconds())
                    statement.setNullableLong(11, entity.bookedAt?.toEpochMilliseconds())
                    statement.setString(12, entity.notes)
                    statement.setLong(13, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(14, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(15, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(16, version)
                    statement.executeUpdate()
                }
        }
    }

    object Sessions : SyncedEntity<Session> {
        override val table = "session"

        override fun identify(entity: Session) = entity.id.value

        override fun studioOf(entity: Session) = entity.studioId.value

        override fun versionOf(entity: Session) = entity.audit.version

        override fun deletedAtOf(entity: Session) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Session =
            Session(
                id = SessionId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                projectId = ProjectId(rows.getString("project_id")),
                title = rows.getString("title"),
                kind = enumOrDefault(rows.getString("kind"), SessionKind.Shoot),
                status = enumOrDefault(rows.getString("status"), SessionStatus.Scheduled),
                startsAt = Instant.fromEpochMilliseconds(rows.getLong("starts_at")),
                endsAt = Instant.fromEpochMilliseconds(rows.getLong("ends_at")),
                timeZoneId = rows.getString("time_zone_id"),
                locationName = rows.getString("location_name"),
                locationAddress = rows.getString("location_address"),
                // Both or neither: half a coordinate is not a place, and a stray latitude
                // would put the shoot on the Greenwich meridian.
                coordinates =
                    rows.getNullableDouble("latitude")?.let { latitude ->
                        rows.getNullableDouble("longitude")?.let { longitude ->
                            GeoCoordinates(latitude = latitude, longitude = longitude)
                        }
                    },
                callTime = rows.getNullableLong("call_time")?.let(Instant::fromEpochMilliseconds),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Session) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Session,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                        starts_at, ends_at, time_zone_id, location_name,
                                        location_address, call_time, notes,
                                        created_at, updated_at, deleted_at, version,
                                        latitude, longitude)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        project_id       = EXCLUDED.project_id,
                        title            = EXCLUDED.title,
                        kind             = EXCLUDED.kind,
                        status           = EXCLUDED.status,
                        starts_at        = EXCLUDED.starts_at,
                        ends_at          = EXCLUDED.ends_at,
                        time_zone_id     = EXCLUDED.time_zone_id,
                        location_name    = EXCLUDED.location_name,
                        location_address = EXCLUDED.location_address,
                        call_time        = EXCLUDED.call_time,
                        notes            = EXCLUDED.notes,
                        updated_at       = EXCLUDED.updated_at,
                        deleted_at       = EXCLUDED.deleted_at,
                        version          = EXCLUDED.version,
                        latitude         = EXCLUDED.latitude,
                        longitude        = EXCLUDED.longitude
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.projectId.value)
                    statement.setString(4, entity.title)
                    statement.setString(5, entity.kind.name)
                    statement.setString(6, entity.status.name)
                    statement.setLong(7, entity.startsAt.toEpochMilliseconds())
                    statement.setLong(8, entity.endsAt.toEpochMilliseconds())
                    statement.setString(9, entity.timeZoneId)
                    statement.setString(10, entity.locationName)
                    statement.setString(11, entity.locationAddress)
                    statement.setNullableLong(12, entity.callTime?.toEpochMilliseconds())
                    statement.setString(13, entity.notes)
                    statement.setLong(14, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(15, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(16, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(17, version)
                    statement.setNullableDouble(18, entity.coordinates?.latitude)
                    statement.setNullableDouble(19, entity.coordinates?.longitude)
                    statement.executeUpdate()
                }
        }
    }

    companion object {
        /** In the order a device must apply them, so a child never lands before its parent. */
        val all: List<SyncedEntity<*>> = listOf(Clients, Projects, Sessions)
    }
}

/**
 * The JSON the conflict payloads are written in.
 *
 * Separate from `apiJson` on purpose: this one is not a wire format anybody negotiates, it
 * is a record kept so a studio can read back the work reconciliation threw away, possibly
 * months later and possibly on a newer build. Unknown keys are tolerated for that reason.
 */
internal val payloadJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

private fun ResultSet.audit(): AuditMetadata =
    AuditMetadata(
        createdAt = Instant.fromEpochMilliseconds(getLong("created_at")),
        updatedAt = Instant.fromEpochMilliseconds(getLong("updated_at")),
        deletedAt = getNullableLong("deleted_at")?.let(Instant::fromEpochMilliseconds),
        version = getLong("version").toInt(),
    )

/**
 * Reads an enum stored by name, falling back rather than throwing.
 *
 * A row written by a newer build must not crash an older one. Once devices sync that stops
 * being hypothetical, and a crash on read is far worse than a stale value.
 */
private inline fun <reified T : Enum<T>> enumOrDefault(
    name: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == name } ?: default

/**
 * Money spans two columns and is present only when both are. A row with an amount and no
 * currency is malformed, and is read as absent rather than silently given a default one.
 */
private fun moneyOf(
    minorUnits: Long?,
    currency: String?,
): Money? = if (minorUnits != null && currency != null) Money(minorUnits, CurrencyCode(currency)) else null

private fun decodeTags(raw: String?): List<String> =
    raw?.let { runCatching { payloadJson.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()

// JDBC returns 0 for a null numeric and expects a separate question about it, which is a
// trap worth wrapping once rather than remembering at thirty call sites.
internal fun ResultSet.getNullableLong(column: String): Long? = getLong(column).takeUnless { wasNull() }

internal fun ResultSet.getNullableDouble(column: String): Double? = getDouble(column).takeUnless { wasNull() }

internal fun PreparedStatement.setNullableLong(
    index: Int,
    value: Long?,
) = if (value == null) setNull(index, java.sql.Types.BIGINT) else setLong(index, value)

internal fun PreparedStatement.setNullableDouble(
    index: Int,
    value: Double?,
) = if (value == null) setNull(index, java.sql.Types.DOUBLE) else setDouble(index, value)
