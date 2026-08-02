package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.model.sync.SyncConflict
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * One field the two versions disagreed about.
 *
 * @param label the field, in words a photographer would use, not `account_name`.
 */
data class ConflictDifference(
    val label: String,
    val discarded: String,
    val kept: String,
)

/**
 * Works out what actually differed between the version that lost and the one that won.
 *
 * The payloads are whole entities, and almost every field in them will be identical — the
 * two devices agreed about everything except the thing one of them changed. Showing a
 * studio two JSON documents and inviting them to spot the difference is technically
 * complete and practically useless, so this narrows it to the fields that moved.
 *
 * Falls back to nothing rather than throwing on a payload it cannot read: a conflict that
 * fails to render is still a conflict the studio should be told about, and the screen says
 * so in that case rather than disappearing.
 */
fun SyncConflict.differences(): List<ConflictDifference> {
    val losing = losingPayload.asFields() ?: return emptyList()
    val winning = winningPayload.asFields() ?: return emptyList()

    return (losing.keys + winning.keys)
        .filterNot { it in IGNORED }
        .sorted()
        .mapNotNull { key ->
            val before = losing[key].orEmpty()
            val after = winning[key].orEmpty()

            if (before == after) {
                null
            } else {
                ConflictDifference(label = key.humanised(), discarded = before, kept = after)
            }
        }
}

/**
 * Fields that always differ and never matter to the reader.
 *
 * `version` and `updatedAt` move on every write by definition, so listing them would bury
 * the one field the studio actually changed under two it did not.
 */
private val IGNORED = setOf("version", "updatedAt", "createdAt", "audit", "id", "studioId")

/**
 * Flattens the entity's own fields plus its audit block one level down.
 *
 * Only scalars are read. A nested object or list that differs is reported by the screen as
 * "something else changed" rather than rendered, because rendering arbitrary structure
 * legibly is a much larger job than this screen is worth.
 */
private fun String.asFields(): Map<String, String>? =
    runCatching {
        val root = lenientJson.parseToJsonElement(this).jsonObject
        buildMap {
            root.forEach { (key, value) ->
                if (key == "audit") {
                    (value as? JsonObject)?.forEach { (auditKey, auditValue) ->
                        if (auditKey !in IGNORED) put(auditKey, auditValue.readable())
                    }
                } else {
                    put(key, value.readable())
                }
            }
        }
    }.getOrNull()

private fun kotlinx.serialization.json.JsonElement.readable(): String =
    when (this) {
        is JsonPrimitive -> if (isString) content else content
        else -> "(changed)"
    }

/**
 * `accountName` reads as "Account name", which is how the field is labelled on the form.
 *
 * Sentence case rather than title case, because every other label in the application is
 * sentence case and a screen that mixes the two looks like two screens.
 */
private fun String.humanised(): String =
    replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .lowercase()
        .replaceFirstChar { it.uppercase() }

private val lenientJson = Json { ignoreUnknownKeys = true }
