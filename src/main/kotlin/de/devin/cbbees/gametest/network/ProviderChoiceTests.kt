package de.devin.cbbees.gametest.network

import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level

object ProviderChoiceTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private val STONE = ItemStack(Items.STONE)

    private fun stocked(level: Level, pos: BlockPos, priority: Int = 0) =
        StubPort(pos, level, priority, mutableListOf(ItemStack(Items.STONE, 64)))

    private fun networkOf(level: Level, vararg ports: StubPort) = BeeNetwork().apply {
        addComponent(StubHive(BlockPos(0, 0, 0), level))
        ports.forEach(::addComponent)
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun shortest_detour_wins_on_equal_priority(helper: GameTestHelper) = helper.unitTest {
        val near = stocked(helper.level, BlockPos(20, 0, 0))
        val far = stocked(helper.level, BlockPos(-20, 0, 0))
        val network = networkOf(helper.level, far, near)

        val provider = network.findAvailableProvider(STONE, from = BlockPos(0, 0, 0), to = BlockPos(30, 0, 0))
        assertEquals(near.id, provider?.id, "port on the way to the stop is chosen")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun priority_beats_distance(helper: GameTestHelper) = helper.unitTest {
        val near = stocked(helper.level, BlockPos(20, 0, 0))
        val important = stocked(helper.level, BlockPos(-20, 0, 0), priority = 1)
        val network = networkOf(helper.level, near, important)

        val provider = network.findAvailableProvider(STONE, from = BlockPos(0, 0, 0), to = BlockPos(30, 0, 0))
        assertEquals(important.id, provider?.id, "higher priority port is chosen even when farther away")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun without_positions_keeps_priority_order(helper: GameTestHelper) = helper.unitTest {
        val low = stocked(helper.level, BlockPos(5, 0, 0))
        val high = stocked(helper.level, BlockPos(40, 0, 0), priority = 2)
        val mid = stocked(helper.level, BlockPos(10, 0, 0), priority = 1)
        val network = networkOf(helper.level, low, high, mid)

        val order = network.findAvailableProviders(STONE).map { it.id }
        assertEquals(listOf(high.id, mid.id, low.id), order, "priority-only order")
    }
}
