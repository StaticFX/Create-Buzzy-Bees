package de.devin.cbbees.gametest.dsl

import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper

@DslMarker
annotation class GameTestDsl

/**
 * Scope for unit tests that don't need world interaction.
 *
 * Provides assertion methods and automatic succeed/fail lifecycle.
 * If the block completes without throwing, [GameTestHelper.succeed]
 * is called automatically.
 */
@GameTestDsl
class UnitTestScope(val helper: GameTestHelper) {

    fun fail(message: String): Nothing {
        throw GameTestAssertException(message)
    }

    fun assertEquals(expected: Any?, actual: Any?, label: String = "") {
        if (expected != actual) {
            val prefix = if (label.isNotEmpty()) "$label: " else ""
            fail("${prefix}expected $expected, got $actual")
        }
    }

    fun assertNotEquals(unexpected: Any?, actual: Any?, label: String = "") {
        if (unexpected == actual) {
            val prefix = if (label.isNotEmpty()) "$label: " else ""
            fail("${prefix}expected value different from $unexpected")
        }
    }

    fun assertTrue(condition: Boolean, message: String = "Expected true") {
        if (!condition) fail(message)
    }

    fun assertFalse(condition: Boolean, message: String = "Expected false") {
        if (condition) fail(message)
    }

    fun assertNotNull(value: Any?, label: String = "value"): Any {
        if (value == null) fail("$label should not be null")
        return value
    }

    fun <T> assertSize(expected: Int, collection: Collection<T>, label: String = "collection"): Collection<T> {
        if (collection.size != expected) {
            fail("$label: expected size $expected, got ${collection.size}")
        }
        return collection
    }
}

/**
 * Runs a unit test with automatic succeed/fail handling.
 *
 * Usage:
 * ```kotlin
 * @GameTest(template = EMPTY, timeoutTicks = 20)
 * @JvmStatic
 * fun myTest(helper: GameTestHelper) = helper.unitTest {
 *     assertEquals(10L, FlightPlan.travelTicks(pos1, pos2, 1.0f), "travel ticks")
 * }
 * ```
 */
inline fun GameTestHelper.unitTest(block: UnitTestScope.() -> Unit) {
    try {
        UnitTestScope(this).block()
        succeed()
    } catch (e: GameTestAssertException) {
        throw e
    } catch (e: Exception) {
        throw GameTestAssertException("Unexpected error: ${e.message}")
    }
}
