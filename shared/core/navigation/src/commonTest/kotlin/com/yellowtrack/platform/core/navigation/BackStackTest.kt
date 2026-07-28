package com.yellowtrack.platform.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BackStackTest {
    @Test
    fun `a new stack has its root as the current entry`() {
        val stack = BackStack.of("dashboard")

        assertEquals("dashboard", stack.current)
        assertEquals("dashboard", stack.root)
        assertEquals(1, stack.depth)
        assertFalse(stack.canNavigateBack)
    }

    @Test
    fun `pushing makes the new destination current and enables back`() {
        val stack = BackStack.of("clients").push("client-details")

        assertEquals("client-details", stack.current)
        assertEquals("clients", stack.root)
        assertTrue(stack.canNavigateBack)
    }

    @Test
    fun `pushing the current destination again is a no-op`() {
        val stack = BackStack.of("clients").push("details")

        assertSame(stack, stack.push("details"), "re-pushing the current entry must not grow the stack")
    }

    @Test
    fun `popping returns to the previous entry`() {
        val stack = BackStack.of("clients").push("details").pop()

        assertEquals("clients", stack.current)
        assertFalse(stack.canNavigateBack)
    }

    @Test
    fun `popping the root leaves the stack unchanged`() {
        val root = BackStack.of("dashboard")

        assertSame(root, root.pop(), "the stack must never become empty")
    }

    @Test
    fun `resetting discards history`() {
        val stack = BackStack.of("clients").push("details").resetTo("sessions")

        assertEquals("sessions", stack.current)
        assertEquals(1, stack.depth)
        assertFalse(stack.canNavigateBack)
    }

    @Test
    fun `popping to root keeps only the root`() {
        val stack =
            BackStack
                .of("clients")
                .push("details")
                .push("edit")
                .popToRoot()

        assertEquals("clients", stack.current)
        assertEquals(1, stack.depth)
    }

    @Test
    fun `pushing returns a new stack and leaves the original untouched`() {
        val original = BackStack.of("clients")
        val pushed = original.push("details")

        assertEquals(1, original.depth, "back stacks are immutable")
        assertEquals(2, pushed.depth)
    }

    @Test
    fun `stacks with the same entries are equal`() {
        assertEquals(
            BackStack.of("clients").push("details"),
            BackStack.of("clients").push("details"),
        )
    }
}
