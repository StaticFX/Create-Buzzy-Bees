package de.devin.cbbees.gametest.dsl

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/** Assert an [ItemStack] matches the expected item and count. */
fun UnitTestScope.assertItemStack(
    stack: ItemStack,
    expectedItem: Item,
    expectedCount: Int,
    label: String = "ItemStack",
) {
    if (!stack.`is`(expectedItem)) {
        fail("$label: expected ${expectedItem.description.string}, got ${stack.item.description.string}")
    }
    if (stack.count != expectedCount) {
        fail("$label: expected count $expectedCount, got ${stack.count}")
    }
}

fun UnitTestScope.assertStackEmpty(stack: ItemStack, label: String = "remainder") {
    if (!stack.isEmpty) {
        fail("$label should be empty, but has ${stack.count} ${stack.item.description.string}")
    }
}

fun UnitTestScope.assertLongArrayEquals(
    expected: LongArray,
    actual: LongArray,
    label: String = "LongArray",
) {
    if (expected.size != actual.size) {
        fail("$label: size mismatch - expected ${expected.size}, got ${actual.size}")
    }
    for (i in expected.indices) {
        if (expected[i] != actual[i]) {
            fail("$label[$i]: expected ${expected[i]}, got ${actual[i]}")
        }
    }
}
