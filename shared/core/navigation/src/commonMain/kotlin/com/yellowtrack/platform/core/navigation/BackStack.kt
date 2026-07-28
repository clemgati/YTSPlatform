package com.yellowtrack.platform.core.navigation

/**
 * An immutable navigation back stack.
 *
 * Independent of Compose and of any platform navigation framework, per ADR 0005, so that
 * navigation behaviour can be tested as plain Kotlin.
 *
 * The stack is never empty: it always has a root, which is what makes [current] total
 * rather than nullable.
 */
class BackStack<T : Any> private constructor(
    val entries: List<T>,
) {
    init {
        require(entries.isNotEmpty()) { "A back stack always has a root entry" }
    }

    val current: T get() = entries.last()

    val root: T get() = entries.first()

    val canNavigateBack: Boolean get() = entries.size > 1

    val depth: Int get() = entries.size

    /** Pushes a destination. Pushing the current destination again is a no-op. */
    fun push(destination: T): BackStack<T> =
        if (destination == current) {
            this
        } else {
            BackStack(entries + destination)
        }

    /** Pops the top entry. Popping the root returns the same stack rather than emptying it. */
    fun pop(): BackStack<T> =
        if (canNavigateBack) {
            BackStack(entries.dropLast(1))
        } else {
            this
        }

    /** Discards all history and starts again at [destination]. */
    fun resetTo(destination: T): BackStack<T> = BackStack(listOf(destination))

    /** Drops everything above the root, keeping it. */
    fun popToRoot(): BackStack<T> = BackStack(listOf(root))

    override fun equals(other: Any?): Boolean = this === other || (other is BackStack<*> && entries == other.entries)

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "BackStack(${entries.joinToString(" > ")})"

    companion object {
        fun <T : Any> of(root: T): BackStack<T> = BackStack(listOf(root))
    }
}
