package de.devin.cbbees.gametest.inventory

import de.devin.cbbees.gametest.dsl.unitTest
import de.devin.cbbees.util.extractExactly
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.items.ItemStackHandler

/**
 * Tests for [extractExactly], which backs `removeItemStack` on Logistics and Cargo Ports.
 */
object ItemHandlerExtensionTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun handlerOf(vararg stacks: ItemStack) = ItemStackHandler(stacks.size).apply {
        stacks.forEachIndexed { slot, stack -> setStackInSlot(slot, stack) }
    }

    private fun ItemStackHandler.count() = (0 until slots).sumOf { getStackInSlot(it).count }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun extractExactly_spansMultipleSlots(helper: GameTestHelper) = helper.unitTest {
        val handler = handlerOf(ItemStack(Items.DIRT, 32), ItemStack(Items.STONE, 5), ItemStack(Items.DIRT, 32))

        assertTrue(handler.extractExactly(ItemStack(Items.DIRT, 64)), "should extract 64 across two slots")
        assertEquals(5, handler.count(), "only the stone should remain")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun extractExactly_insufficient_extractsNothing(helper: GameTestHelper) = helper.unitTest {
        val handler = handlerOf(ItemStack(Items.DIRT, 10), ItemStack(Items.DIRT, 20))

        assertFalse(handler.extractExactly(ItemStack(Items.DIRT, 64)), "should fail with only 30 available")
        assertEquals(30, handler.count(), "nothing should be extracted on failure")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun extractExactly_ignoresOtherItems(helper: GameTestHelper) = helper.unitTest {
        val handler = handlerOf(ItemStack(Items.STONE, 64))

        assertFalse(handler.extractExactly(ItemStack(Items.DIRT, 1)), "should not match a different item")
        assertEquals(64, handler.count(), "stone should be untouched")
    }
}
