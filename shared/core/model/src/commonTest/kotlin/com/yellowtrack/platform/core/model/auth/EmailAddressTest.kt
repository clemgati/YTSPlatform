package com.yellowtrack.platform.core.model.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmailAddressTest {
    @Test
    fun `accepts ordinary addresses`() {
        listOf(
            "someone@gmail.com",
            "first.last@studio.co.uk",
            "a@b.io",
            "name+tag@gmail.com",
            "under_score@long-domain-name.photography",
        ).forEach { assertTrue(EmailAddress.isPlausible(it), "should accept $it") }
    }

    @Test
    fun `rejects what cannot be delivered to`() {
        listOf(
            "",
            "   ",
            "no-at-sign.com",
            "@nolocal.com",
            "two@at@signs.com",
            "trailing@dot.",
            "@",
            "spaces in@gmail.com",
            "someone@gmail com",
            "nodot@localhost",
            "double@dots..com",
            "short@tld.x",
            "numeric@tld.12",
        ).forEach { assertFalse(EmailAddress.isPlausible(it), "should reject $it") }
    }

    /** The fault this was written for: an account created against a domain that does not exist. */
    @Test
    fun `suggests the domain a typo was reaching for`() {
        assertEquals("clement@gmail.com", EmailAddress.suggestion("clement@gmail.ocm"))
        assertEquals("clement@gmail.com", EmailAddress.suggestion("clement@gmail.con"))
        assertEquals("clement@gmail.com", EmailAddress.suggestion("clement@gmial.com"))
        assertEquals("clement@hotmail.com", EmailAddress.suggestion("clement@hotmial.com"))
        assertEquals("clement@outlook.com", EmailAddress.suggestion("clement@outlok.com"))
        assertEquals("clement@icloud.com", EmailAddress.suggestion("clement@iclould.com"))
    }

    @Test
    fun `says nothing about an address that is already right`() {
        listOf(
            "someone@gmail.com",
            "someone@hotmail.co.uk",
            "someone@proton.me",
            "someone@me.com",
        ).forEach { assertNull(EmailAddress.suggestion(it), "should not correct $it") }
    }

    /**
     * The expensive mistake. A studio's own domain is not guessable and correcting it would
     * send the only route back into an account somewhere the studio does not read.
     */
    @Test
    fun `never corrects a domain of its own`() {
        listOf(
            "hello@yellowtrackstudios.com",
            "hello@okaforphotography.co.uk",
            "hello@studio.photography",
            // A real provider one edit from gmail.com. Only the exact-match check saves it.
            "hello@mail.com",
        ).forEach { assertNull(EmailAddress.suggestion(it), "should not correct $it") }
    }

    /** A regional variant is a different address rather than a misspelling of the .com. */
    @Test
    fun `does not correct across countries`() {
        assertNull(EmailAddress.suggestion("someone@hotmail.co.uk"))
        assertNull(EmailAddress.suggestion("someone@yahoo.co.uk"))
    }

    /**
     * The field is read on every keystroke. Every prefix of the right answer is also a
     * near-miss of it, and questioning somebody on the way to typing it correctly is worse
     * than saying nothing.
     */
    @Test
    fun `stays quiet while the right answer is still being typed`() {
        assertNull(EmailAddress.suggestion("clement@gmail.co"))
        assertNull(EmailAddress.suggestion("clement@outlook.c"))
        assertNull(EmailAddress.suggestion("clement@hotmail.c"))
        // But a wrong ending is not a prefix of anything and is still worth asking about.
        assertEquals("clement@gmail.com", EmailAddress.suggestion("clement@gmail.ocm"))
    }

    @Test
    fun `has nothing to suggest for something that is not an address`() {
        assertNull(EmailAddress.suggestion("not an address"))
        assertNull(EmailAddress.suggestion(""))
        assertNull(EmailAddress.suggestion("gmail.ocm"))
    }

    @Test
    fun `ignores surrounding space and case as the server does`() {
        assertTrue(EmailAddress.isPlausible("  someone@gmail.com  "))
        assertEquals("clement@gmail.com", EmailAddress.suggestion("  Clement@Gmail.OCM  "))
    }
}
