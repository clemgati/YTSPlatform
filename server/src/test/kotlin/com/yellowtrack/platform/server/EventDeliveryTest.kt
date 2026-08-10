package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.auth.Accounts
import com.yellowtrack.platform.server.event.Delivered
import com.yellowtrack.platform.server.event.DeliveryRefused
import com.yellowtrack.platform.server.event.EventDelivery
import com.yellowtrack.platform.server.event.EventGalleries
import com.yellowtrack.platform.server.event.Events
import com.yellowtrack.platform.server.mail.Email
import com.yellowtrack.platform.server.mail.Mailer
import com.yellowtrack.platform.server.storage.ObjectStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Handing a sitting to the person in it, and refusing to before somebody has looked.
 *
 * The property this whole feature rests on is that photographs of one person do not reach
 * another. Everything here is a way that could happen.
 */
class EventDeliveryTest {
    // -- What must be true before anything is sent -------------------------------------------

    /**
     * ADR 0013: photographs are held until the slot is closed.
     *
     * An open slot is one the photographer is still shooting into, so its contents are not
     * yet photographs of one person — they are photographs of whoever has sat down since it
     * opened.
     */
    @Test
    fun `an open sitting cannot be delivered`() {
        val world = World()
        world.photograph()

        assertFailsWith<DeliveryRefused.StillOpen> { world.deliver() }
        assertTrue(world.mailer.sent.isEmpty(), "an open sitting was mailed")
    }

    @Test
    fun `a sitting with no photographs is not delivered`() {
        val world = World()
        world.closeSitting()

        assertFailsWith<DeliveryRefused.NothingToSend> { world.deliver() }
        assertTrue(world.mailer.sent.isEmpty())
    }

    /** A studio with no address of its own leaves a reply going nowhere it can read. */
    @Test
    fun `a studio with no email address cannot deliver`() {
        val world = World(studioEmail = null)
        world.photograph()
        world.closeSitting()

        assertFailsWith<DeliveryRefused.NoStudioEmail> { world.deliver() }
        assertTrue(world.mailer.sent.isEmpty())
    }

    /** A deployment that cannot send mail must not claim to have delivered anything. */
    @Test
    fun `a server that cannot send mail refuses rather than pretending`() {
        val world = World()
        world.photograph()
        world.closeSitting()

        val unsendable = world.deliveryWithoutMail()

        assertFailsWith<DeliveryRefused.NotConfigured> { unsendable.deliver(world.studioId, world.slotId) }
        assertNull(world.deliveredAt(), "an unsendable delivery was marked as done")
    }

    /**
     * A specific refusal must not be hidden behind a general one.
     *
     * `deliver` checked the mailer first, so on a deployment with no mail every refusal came
     * back "this server cannot send mail" — including for a sitting the studio could simply
     * close. One of those is fixable by the person reading it and the other is not.
     */
    @Test
    fun `an open sitting says so even when the server cannot send mail`() {
        val world = World()
        world.photograph()

        val unsendable = world.deliveryWithoutMail()

        assertFailsWith<DeliveryRefused.StillOpen> { unsendable.deliver(world.studioId, world.slotId) }
    }

    // -- Sending ------------------------------------------------------------------------------

    @Test
    fun `a closed sitting is mailed to the person in it`() {
        val world = World()
        world.photograph()
        world.photograph()
        world.closeSitting()

        val delivered = world.deliver()

        assertEquals("guest@example.test", delivered.email)
        assertEquals(2, delivered.photographs)
        assertTrue(delivered.sentNow)

        val email = world.mailer.sent.single()
        assertEquals("guest@example.test", email.to)
        assertTrue("Harbour Awards 2026" in email.subject, email.subject)
        assertEquals("ada@harbourline.test", email.replyTo, "a reply must reach the photographer")
        assertNotNull(world.deliveredAt())
    }

    /** The link is the message. Without it the email is a notification of nothing. */
    @Test
    fun `the email carries a link to their own gallery`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.deliver()

        val email = world.mailer.sent.single()
        val token = world.galleryToken()

        assertNotNull(token, "no gallery was issued")
        assertTrue("/gallery/$token" in email.body, email.body)
        assertTrue("/gallery/$token" in email.html.orEmpty(), email.html.orEmpty())
    }

    /**
     * Tapping twice must not mail twice.
     *
     * A studio going down a list of sittings will re-tap one, and the person on the other end
     * has no way to tell a duplicate from a second set of photographs.
     */
    @Test
    fun `delivering twice sends one email`() {
        val world = World()
        world.photograph()
        world.closeSitting()

        val first = world.deliver()
        val second = world.deliver()

        assertTrue(first.sentNow)
        assertFalse(second.sentNow, "the second delivery claimed to have sent something")
        assertEquals(1, world.mailer.sent.size)
    }

    /**
     * The ordering rule, and the reverse of the one in `StoredObjects`.
     *
     * There the row went before the bytes, because an object with no row is unfindable. Here
     * the mark goes after the send, because the irreversible half is the email: a delivery
     * marked and not sent is invisible, nothing retries, and somebody goes home with nothing.
     */
    @Test
    fun `a send that fails leaves the sitting undelivered`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.mailer.refuse = true

        assertFailsWith<DeliveryRefused.Failed> { world.deliver() }

        assertNull(world.deliveredAt(), "a failed send was marked as delivered")

        // And it can be sent once the mail server comes back.
        world.mailer.refuse = false
        assertTrue(world.deliver().sentNow, "a failed delivery could not be retried")
    }

    /** One person photographed twice at one event gets one link, not two half-galleries. */
    @Test
    fun `a second sitting reuses the same gallery`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.deliver()
        val first = world.galleryToken()

        world.newSitting()
        world.photograph()
        world.closeSitting()
        world.deliver()

        assertEquals(first, world.galleryToken(), "a second sitting issued a second link")
        assertEquals(2, world.mailer.sent.size, "the second sitting should still be announced")
    }

    // -- What the message has to be, rather than merely say ------------------------------------

    /**
     * Somebody must be able to stop this without composing a reply.
     *
     * A mailto rather than a one-click URL: One-Click promises an HTTPS endpoint that
     * unsubscribes without confirmation, and there is none. Announcing the header without
     * the route behind it would be worse than not offering it — a provider would call it and
     * get nothing.
     */
    @Test
    fun `the delivery offers a way to unsubscribe that reaches the studio`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.deliver()

        val email = world.mailer.sent.single()
        val unsubscribe = email.headers["List-Unsubscribe"]

        assertNotNull(unsubscribe, "no List-Unsubscribe header")
        assertTrue("ada@harbourline.test" in unsubscribe, "it should reach the studio: $unsubscribe")
        assertTrue(unsubscribe.startsWith("<") && unsubscribe.endsWith(">"), unsubscribe)
    }

    /**
     * The message has to be a message.
     *
     * The first version was three lines and one link, from a domain the recipient had never
     * corresponded with, pointing at a different domain — and it went to spam with SPF, DKIM
     * and DMARC all passing. Naming the event, the studio, and where the address came from is
     * what makes it read as what it is, to a filter and to a person who signed up three hours
     * ago and may not remember.
     */
    @Test
    fun `the delivery says where the address came from and who took the photographs`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.deliver()

        val email = world.mailer.sent.single()

        listOf(email.body, email.html.orEmpty()).forEach { part ->
            assertTrue("Harbour Awards 2026" in part, "the event is not named: $part")
            assertTrue("Harbourline Photography" in part, "the studio is not named: $part")
            assertTrue("scanned a code" in part, "it does not say where the address came from: $part")
            assertTrue("did not sign up" in part, "it offers no way out: $part")
        }
    }

    /**
     * The sender is on the domain the link points at.
     *
     * One name for a guest to recognise instead of two, and the mismatch was the strongest
     * spam signal in the first message this product ever sent.
     */
    @Test
    fun `the delivery leaves from the address it was configured with`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.deliver()

        val email = world.mailer.sent.single()

        assertEquals("events@yellowtrackstudios.com", email.fromAddress)
        assertEquals("Harbourline Photography", email.fromName)
    }

    /**
     * One person, two cameras, one gallery.
     *
     * A guest photographed at a headshot bay and again at a group station is one person with
     * two sittings. Nothing binds a registration to a single station — slots are per station,
     * and the constraint is one *open* slot per station rather than one per person — so this
     * works, and it needs to: the photographs belong to them either way and must arrive
     * together rather than as two links to two half-galleries.
     */
    @Test
    fun `somebody photographed at two stations gets one gallery holding both`() {
        val world = World()

        // Bay 1.
        world.photograph()
        world.closeSitting()
        world.deliver()
        val gallery = world.galleryToken()!!
        assertEquals(
            1,
            world
                .galleries()
                .photographs(gallery)!!
                .photographs.size,
        )

        // The same person, at a different camera.
        world.newStationFor("Bay 2", "Camera B")
        world.photograph()
        world.photograph()
        world.closeSitting()
        world.deliver()

        assertEquals(gallery, world.galleryToken(), "a second station issued a second link")
        assertEquals(
            3,
            world
                .galleries()
                .photographs(gallery)!!
                .photographs.size,
            "the two stations' photographs did not arrive together",
        )
    }

    // -- The gallery --------------------------------------------------------------------------

    @Test
    fun `the gallery shows the delivered photographs`() {
        val world = World()
        world.photograph()
        world.photograph()
        world.closeSitting()
        world.deliver()

        val gallery = assertNotNull(world.galleries().photographs(world.galleryToken()!!))

        assertEquals("Harbour Awards 2026", gallery.eventName)
        assertEquals(2, gallery.photographs.size)
    }

    /**
     * The promise this feature is built on, checked at the gallery rather than at the email.
     *
     * A token is not entitlement to photographs the studio has not released. If the hold were
     * enforced only when sending, anybody with a link from a previous sitting would see the
     * next one appear as it was shot — and nobody would be watching for it.
     */
    @Test
    fun `photographs of a sitting that was never delivered are not in the gallery`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.deliver()
        val token = world.galleryToken()!!
        assertEquals(
            1,
            world
                .galleries()
                .photographs(token)!!
                .photographs.size,
        )

        // A second sitting, closed but never released.
        world.newSitting()
        world.photograph()
        world.photograph()
        world.closeSitting()

        assertEquals(
            1,
            world
                .galleries()
                .photographs(token)!!
                .photographs.size,
            "an undelivered sitting was visible to somebody holding an older link",
        )
    }

    /**
     * Closing is the photographer's act; releasing is the studio's.
     *
     * Written against `delivered_at` rather than against the gallery, because the previous
     * version of this test asserted only that no gallery token existed yet — which is true
     * before any delivery for any reason at all, and would have stayed true if closing had
     * started marking sittings delivered.
     */
    @Test
    fun `closing a sitting does not mark it delivered`() {
        val world = World()
        world.photograph()
        world.closeSitting()

        assertNull(world.deliveredAt(), "closing a sitting delivered it")
        assertNull(world.galleryToken(), "closing a sitting issued a gallery")
    }

    @Test
    fun `an unknown gallery token shows nothing`() {
        assertNull(World().galleries().photographs("not-a-real-token"))
    }

    /**
     * One person's link must never show another's photographs, even at the same event.
     *
     * The registration is the boundary, and it is the one thing this feature exists to get
     * right.
     */
    @Test
    fun `one guest's link does not show another guest's photographs`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.deliver()
        val theirs = world.galleryToken()!!

        world.newRegistration("other@example.test")
        world.photograph()
        world.photograph()
        world.closeSitting()
        world.deliver()

        assertEquals(
            1,
            world
                .galleries()
                .photographs(theirs)!!
                .photographs.size,
            "somebody saw another guest's photographs",
        )
    }

    /** A deployment with no bucket cannot sign anything, and must not show an empty gallery. */
    @Test
    fun `a gallery with no storage is unavailable rather than empty`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.deliver()

        val unsigned = EventGalleries(TestDatabase.database, ObjectStore.Unconfigured)

        assertNull(
            unsigned.photographs(world.galleryToken()!!),
            "an unsignable gallery read as 'no photographs of you'",
        )
    }

    /**
     * A profile that exists with no address in it.
     *
     * Distinct from having no profile at all, and the mutation that showed this was missing:
     * the earlier test removed the whole row, so it never reached the check on the address
     * itself. A studio that has opened Settings and left the field blank is the commoner
     * case of the two.
     */
    @Test
    fun `a studio whose profile has a blank address cannot deliver`() {
        val world = World(studioEmail = "   ")
        world.photograph()
        world.closeSitting()

        assertFailsWith<DeliveryRefused.NoStudioEmail> { world.deliver() }
        assertTrue(world.mailer.sent.isEmpty())
    }

    /**
     * Withdrawing a link is the only remedy once it is in an inbox.
     *
     * Somebody asks to be forgotten, or the wrong sitting reached the wrong person. Nothing
     * can recall the email, so the link must stop working.
     */
    @Test
    fun `a withdrawn gallery stops working`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.deliver()
        val token = world.galleryToken()!!
        assertNotNull(world.galleries().photographs(token), "the gallery should work before it is withdrawn")

        world.galleries().revoke(world.studioId, world.registrationId)

        assertNull(world.galleries().photographs(token), "a withdrawn link still showed photographs")
    }

    /** One studio must not be able to withdraw another studio's gallery. */
    @Test
    fun `one studio cannot withdraw another studio's gallery`() {
        val world = World()
        world.photograph()
        world.closeSitting()
        world.deliver()
        val token = world.galleryToken()!!

        val other = World()
        other.galleries().revoke(other.studioId, world.registrationId)

        assertNotNull(
            world.galleries().photographs(token),
            "another studio withdrew a gallery it cannot see",
        )
    }

    // -- Fixtures -------------------------------------------------------------------------------

    private class RecordingMailer : Mailer {
        val sent = mutableListOf<Email>()
        var refuse = false

        override fun send(email: Email) {
            if (refuse) throw IllegalStateException("the mail server is unreachable")
            sent += email
        }
    }

    private class SigningStore : ObjectStore {
        override fun put(
            key: String,
            contentType: String,
            bytes: ByteArray,
        ) = Unit

        override fun temporaryUrl(
            key: String,
            validFor: Duration,
        ): String = "https://example.invalid/$key"

        override fun delete(keys: List<String>): Set<String> = keys.toSet()
    }

    private inner class World(
        studioEmail: String? = "ada@harbourline.test",
    ) {
        val mailer = RecordingMailer()
        val studioId: String
        private val events = Events(TestDatabase.database)
        private val eventId: String
        private var currentRegistrationId: String
        private var stationId: String
        var slotId: String
            private set

        /** The camera photographs currently arrive from — it moves when the station does. */
        private var currentSource: String = "Camera A"

        val registrationId: String get() = currentRegistrationId

        init {
            val signedIn =
                Accounts(TestDatabase.database).signUp(
                    "ada-${UUID.randomUUID()}@harbourline.test",
                    "a long enough password",
                    "Ada Okafor",
                    "Harbourline Photography",
                )
            studioId = signedIn.studioId

            // The studio's own details live in `studio_profile`, which is where a reply
            // address comes from. A studio that has never opened Settings has no row here at
            // all, which `studioEmail = null` stands in for.
            if (studioEmail != null) {
                TestDatabase.database.inStudio(studioId) { connection ->
                    connection
                        .prepareStatement(
                            """
                            INSERT INTO studio_profile(id, studio_id, name, email, created_at, updated_at, version)
                            VALUES (?, ?, 'Harbourline Photography', ?, 0, 0, 1)
                            """.trimIndent(),
                        ).use { statement ->
                            statement.setString(1, UUID.randomUUID().toString())
                            statement.setString(2, studioId)
                            statement.setString(3, studioEmail)
                            statement.executeUpdate()
                        }
                }
            }

            eventId = events.createEvent(studioId, "Harbour Awards 2026")
            currentRegistrationId = events.register(studioId, eventId, "guest@example.test", "Ada Guest")
            stationId = events.openStation(studioId, eventId, "Bay 1", "Camera A")
            slotId = events.advanceSlot(studioId, stationId, currentRegistrationId)
        }

        fun delivery() =
            EventDelivery(
                database = TestDatabase.database,
                mailer = mailer,
                fromAddress = "events@yellowtrackstudios.com",
                photosUrl = "https://yellowtrackphotos.test",
            )

        fun deliveryWithoutMail() =
            EventDelivery(
                database = TestDatabase.database,
                mailer = null,
                fromAddress = "events@yellowtrackstudios.com",
            )

        fun galleries() = EventGalleries(TestDatabase.database, SigningStore())

        fun deliver(): Delivered = delivery().deliver(studioId, slotId)

        /** A photograph in the currently open slot. */
        fun photograph() {
            val objectId = UUID.randomUUID().toString()

            TestDatabase.database.inStudio(studioId) { connection ->
                connection
                    .prepareStatement(
                        """
                        INSERT INTO stored_object(id, studio_id, object_key, content_type, size_bytes, created_at)
                        VALUES (?, ?, ?, 'image/jpeg', 1, 0)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, objectId)
                        statement.setString(2, studioId)
                        statement.setString(3, "$studioId/$objectId")
                        statement.executeUpdate()
                    }
            }

            events.recordPhotograph(studioId, eventId, currentSource, objectId, System.currentTimeMillis())
        }

        fun closeSitting() {
            events.closeStation(studioId, stationId)
        }

        /** A different camera entirely, with the same person in front of it. */
        fun newStationFor(
            name: String,
            camera: String,
        ) {
            currentSource = camera
            stationId = events.openStation(studioId, eventId, name, camera)
            slotId = events.advanceSlot(studioId, stationId, currentRegistrationId)
        }

        /** Reopens the station and advances to the same person again. */
        fun newSitting() {
            stationId = events.openStation(studioId, eventId, "Bay 1", "Camera A")
            slotId = events.advanceSlot(studioId, stationId, currentRegistrationId)
        }

        /** A different person, at the same event, on a new station. */
        fun newRegistration(email: String) {
            currentRegistrationId = events.register(studioId, eventId, email, null)
            stationId = events.openStation(studioId, eventId, "Bay 1", "Camera A")
            slotId = events.advanceSlot(studioId, stationId, currentRegistrationId)
        }

        fun deliveredAt(): Long? =
            TestDatabase.database.inStudio(studioId) { connection ->
                connection.prepareStatement("SELECT delivered_at FROM event_slot WHERE id = ?").use { statement ->
                    statement.setString(1, slotId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) rows.getLong(1).takeUnless { rows.wasNull() } else null
                    }
                }
            }

        fun galleryToken(): String? =
            TestDatabase.database.unscoped { connection ->
                connection
                    .prepareStatement(
                        "SELECT token FROM event_gallery WHERE registration_id = ? AND revoked_at IS NULL",
                    ).use { statement ->
                        statement.setString(1, currentRegistrationId)
                        statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                    }
            }
    }
}
