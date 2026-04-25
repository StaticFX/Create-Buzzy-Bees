package de.devin.cbbees.gametest.inventory

import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.gametest.dsl.assertItemStack
import de.devin.cbbees.gametest.dsl.assertStackEmpty
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

object BeeInventoryTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun createBee(type: BeeType = BeeType.CONSTRUCTION) = ServerBeeData(
        id = UUID.randomUUID(), type = type, networkId = UUID.randomUUID(),
    )

    // ── Basic add / query ──

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_startsEmpty(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee()
        assertTrue(bee.isInventoryEmpty(), "should be empty")
        assertSize(0, bee.getInventoryContents(), "contents")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_addItem_returnsEmptyWhenFits(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee()
        val remainder = bee.addToInventory(ItemStack(Items.COBBLESTONE, 16))
        assertStackEmpty(remainder)
        assertFalse(bee.isInventoryEmpty(), "should not be empty after add")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_addItem_contentsMatchAdded(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee()
        bee.addToInventory(ItemStack(Items.COBBLESTONE, 16))
        val contents = bee.getInventoryContents()
        assertSize(1, contents, "stacks")
        assertItemStack(contents[0], Items.COBBLESTONE, 16)
    }

    // ── Slot capacity ──

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_constructionBee_has4Slots(helper: GameTestHelper) = helper.unitTest {
        assertEquals(4, createBee(BeeType.CONSTRUCTION).inventory.containerSize, "construction slots")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_transportBee_has3Slots(helper: GameTestHelper) = helper.unitTest {
        assertEquals(3, createBee(BeeType.TRANSPORT).inventory.containerSize, "transport slots")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_fullAfterFillingAllSlots(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee(BeeType.CONSTRUCTION)
        repeat(4) { bee.addToInventory(ItemStack(Items.COBBLESTONE, 64)) }
        assertTrue(bee.isInventoryFull(), "should be full")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_overflowReturnsRemainder(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee(BeeType.CONSTRUCTION)
        repeat(4) { bee.addToInventory(ItemStack(Items.COBBLESTONE, 64)) }
        val remainder = bee.addToInventory(ItemStack(Items.COBBLESTONE, 32))
        assertEquals(32, remainder.count, "overflow count")
    }

    // ── Remove ──

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_removeAll_emptiesInventory(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee()
        bee.addToInventory(ItemStack(Items.COBBLESTONE, 32))
        bee.removeFromInventory(ItemStack(Items.COBBLESTONE), 32)
        assertTrue(bee.isInventoryEmpty(), "should be empty after remove all")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_removePartial_leavesRemainder(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee()
        bee.addToInventory(ItemStack(Items.COBBLESTONE, 32))
        bee.removeFromInventory(ItemStack(Items.COBBLESTONE), 10)
        val contents = bee.getInventoryContents()
        assertSize(1, contents, "stacks")
        assertEquals(22, contents[0].count, "remaining count")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_removeWrongItem_doesNothing(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee()
        bee.addToInventory(ItemStack(Items.COBBLESTONE, 32))
        bee.removeFromInventory(ItemStack(Items.DIRT), 32)
        assertEquals(32, bee.getInventoryContents()[0].count, "unchanged count")
    }

    // ── Multiple item types ──

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_multipleTypes_useSeparateSlots(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee(BeeType.CONSTRUCTION)
        bee.addToInventory(ItemStack(Items.COBBLESTONE, 16))
        bee.addToInventory(ItemStack(Items.OAK_PLANKS, 8))
        bee.addToInventory(ItemStack(Items.IRON_INGOT, 4))
        assertSize(3, bee.getInventoryContents(), "stacks")
        assertFalse(bee.isInventoryFull(), "3/4 slots should not be full")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_sameItem_stacksInSameSlot(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee()
        bee.addToInventory(ItemStack(Items.COBBLESTONE, 16))
        bee.addToInventory(ItemStack(Items.COBBLESTONE, 16))
        val contents = bee.getInventoryContents()
        assertSize(1, contents, "should stack")
        assertEquals(32, contents[0].count, "stacked count")
    }

    // ── Edge cases ──

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_partialStack_notFull(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee(BeeType.CONSTRUCTION)
        bee.addToInventory(ItemStack(Items.COBBLESTONE, 32))
        bee.addToInventory(ItemStack(Items.DIRT, 32))
        bee.addToInventory(ItemStack(Items.OAK_PLANKS, 32))
        bee.addToInventory(ItemStack(Items.IRON_INGOT, 32))
        assertFalse(bee.isInventoryFull(), "partial stacks should not be full")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun inventory_removeAcrossSlots(helper: GameTestHelper) = helper.unitTest {
        val bee = createBee()
        bee.addToInventory(ItemStack(Items.COBBLESTONE, 64))
        bee.addToInventory(ItemStack(Items.COBBLESTONE, 64))
        bee.removeFromInventory(ItemStack(Items.COBBLESTONE), 96)
        val total = bee.getInventoryContents().sumOf { it.count }
        assertEquals(32, total, "128 - 96 = 32")
    }
}
