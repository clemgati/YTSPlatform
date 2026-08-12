package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.event.Events
import com.yellowtrack.platform.server.event.Routed
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * Whose photograph is this?
 *
 * ADR 0013 decision 4 turns on one question — was a slot open on the source this arrived
 * from — and everything else in the event model exists to answer it. Getting it wrong sends
 * one person's headshot to another, which is a privacy incident rather than a bug, so the
 * cases below are the ones that decide whether the feature is safe to have.
 */
class EventRoutingTest {
    /** The roaming default. Nobody is at a station, so the photograph belongs to the event. */
    @Test
    fun `a photograph with no station open belongs to the gallery`() {
        val world = world()

        val routed = world.events.recordPhotograph(world.studio, world.event, CAMERA_A, world.object1, capturedAt = 100)

        assertIs<Routed.ToGallery>(routed, "with nothing open, a photograph is the event's")
    }

    /** The station case. Somebody holds the slot, so the photograph is theirs. */
    @Test
    fun `a photograph taken during a slot belongs to that person`() {
        val world = world()
        val station = world.events.openStation(world.studio, world.event, "Headshots", CAMERA_A)
        val ada = world.events.register(world.studio, world.event, "ada@example.com").id
        val slot = world.events.advanceSlot(world.studio, station, ada)

        val routed = world.events.recordPhotograph(world.studio, world.event, CAMERA_A, world.object1, capturedAt = 200)

        val toSlot = assertIs<Routed.ToSlot>(routed)
        assertEquals(slot, toSlot.slotId)
        assertEquals(ada, toSlot.registrationId, "it should belong to whoever was in front of the camera")
    }

    /**
     * The case that makes a second photographer safe.
     *
     * A wedding with two photographers has one of them running formal groups at a station
     * while the other roams. Without binding a station to its source, the roaming
     * photographer's candids are swallowed into whoever is holding the first one's slot —
     * and a guest receives a stranger's family portrait.
     */
    @Test
    fun `another camera is not swallowed by an open slot`() {
        val world = world()
        val station = world.events.openStation(world.studio, world.event, "Formals", CAMERA_A)
        val ada = world.events.register(world.studio, world.event, "ada@example.com").id
        world.events.advanceSlot(world.studio, station, ada)

        val roaming =
            world.events.recordPhotograph(
                world.studio,
                world.event,
                CAMERA_B,
                world.object2,
                capturedAt = 300,
            )

        assertIs<Routed.ToGallery>(
            roaming,
            "a candid from the second photographer must not land in the first one's slot",
        )
    }

    /**
     * Routing is by when the shutter fired, not when the file turned up.
     *
     * A folder watcher notices a file some time after the photograph was taken, and on a
     * laptop that woke up late that can be minutes — long enough for the photographer to have
     * advanced past the person actually in the frame.
     */
    @Test
    fun `a late upload belongs to whoever was in front of the camera at the time`() {
        val world = world()
        val station = world.events.openStation(world.studio, world.event, "Headshots", CAMERA_A)
        val ada = world.events.register(world.studio, world.event, "ada@example.com").id
        val adaSlot = world.events.advanceSlot(world.studio, station, ada)

        // Ada is photographed, then the photographer moves on to Grace.
        world.clock = 500
        val grace = world.events.register(world.studio, world.event, "grace@example.com").id
        val graceSlot = world.events.advanceSlot(world.studio, station, grace)
        world.clock = 900

        // Ada's frame only reaches the server now, long after she left the chair.
        val routed = world.events.recordPhotograph(world.studio, world.event, CAMERA_A, world.object1, capturedAt = 300)

        val toSlot = assertIs<Routed.ToSlot>(routed)
        assertEquals(adaSlot, toSlot.slotId, "captured during Ada's slot, so it is Ada's however late it arrived")
        assertNotEquals(graceSlot, toSlot.slotId)
    }

    /** Closing the station returns its source to the gallery. */
    @Test
    fun `photographs after the station closes belong to the gallery again`() {
        val world = world()
        val station = world.events.openStation(world.studio, world.event, "Headshots", CAMERA_A)
        val ada = world.events.register(world.studio, world.event, "ada@example.com").id
        world.events.advanceSlot(world.studio, station, ada)
        world.clock = 400
        world.events.closeStation(world.studio, station)
        world.clock = 500

        val routed = world.events.recordPhotograph(world.studio, world.event, CAMERA_A, world.object2, capturedAt = 500)

        assertIs<Routed.ToGallery>(routed, "the formals are over and the photographer is back in the room")
    }

    /** Somebody who scans the QR code twice is one person, not two half-galleries. */
    @Test
    fun `registering twice with the same address is the same registration`() {
        val world = world()

        val first = world.events.register(world.studio, world.event, "ada@example.com").id
        val second = world.events.register(world.studio, world.event, "ada@example.com", name = "Ada").id

        assertEquals(first, second)
    }

    // -- Fixtures --------------------------------------------------------------------------

    /**
     * Held in an object rather than captured as a local.
     *
     * The first version of this fixture gave the service `now = { clock }` over a local `var`
     * and then exposed a separate `clock` on the world. Moving the test's clock moved nothing
     * the service could see, so the two tests about time would have passed without testing
     * anything.
     */
    private class Clock {
        var value: Long = 0
    }

    private class World(
        val studio: String,
        val event: String,
        val events: Events,
        val object1: String,
        val object2: String,
        private val time: Clock,
    ) {
        var clock: Long
            get() = time.value
            set(value) {
                time.value = value
            }
    }

    private fun world(): World {
        val studio = "studio-${UUID.randomUUID()}"
        val clock = Clock()
        val events = Events(TestDatabase.database, now = { clock.value }, newId = { UUID.randomUUID().toString() })

        TestDatabase.connection().use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO studio(id, name, created_at, updated_at, version) VALUES (?, ?, 0, 0, 1)",
                ).use { statement ->
                    statement.setString(1, studio)
                    statement.setString(2, "Events fixture")
                    statement.executeUpdate()
                }
        }

        val objects = (1..2).map { storedObject(studio) }
        val event = events.createEvent(studio, "A day of headshots")

        return World(studio, event, events, objects[0], objects[1], clock)
    }

    /** A row in `stored_object`, since `event_photo` references one. */
    private fun storedObject(studioId: String): String {
        val id = UUID.randomUUID().toString()

        TestDatabase.connection().use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO stored_object(id, studio_id, object_key, content_type, size_bytes, created_at)
                    VALUES (?, ?, ?, 'image/jpeg', 1, 0)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, studioId)
                    statement.setString(3, "$studioId/${UUID.randomUUID()}.jpg")
                    statement.executeUpdate()
                }
        }

        return id
    }

    private companion object {
        const val CAMERA_A = "watched-folder-a"
        const val CAMERA_B = "watched-folder-b"
    }
}
