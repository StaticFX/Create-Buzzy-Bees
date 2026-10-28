package de.devin.cbbees.gametest.history

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem
import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.domain.action.impl.PlaceBeltAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.history.BlockSnapshot
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.upgrades.BeeContext
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RotatedPillarBlock
import net.neoforged.neoforge.fluids.FluidStack
import java.util.UUID

object BeltHistoryTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private class StubWorker(private val level: Level, items: List<ItemStack>) : BeeWorker {
        private val inventory = items.map { it.copy() }.toMutableList()
        override val uuid: UUID = UUID.randomUUID()
        override val networkId: UUID = UUID.randomUUID()
        override var carriedFluid: FluidStack = FluidStack.EMPTY
        override fun blockPosition(): BlockPos = BlockPos.ZERO
        override fun level() = level
        override fun addToInventory(stack: ItemStack): ItemStack = stack.also { inventory += it.copy() }.let { ItemStack.EMPTY }
        override fun removeFromInventory(stack: ItemStack, count: Int) {
            var left = count
            inventory.filter { ItemStack.isSameItem(it, stack) }.forEach { val taken = minOf(left, it.count); it.shrink(taken); left -= taken }
            inventory.removeIf { it.isEmpty }
        }
        override fun getInventoryContents(): List<ItemStack> = inventory
        override fun isInventoryFull() = false
        override fun isInventoryEmpty() = inventory.isEmpty()
        override fun consumeSpring(baseDrain: Double) = true
        override fun getBeeContext() = BeeContext()
        override fun getOwnerPlayer(): Player? = null
        override fun network(): BeeNetwork? = null
        override fun getWorkerX() = 0.0
        override fun getWorkerY() = 0.0
        override fun getWorkerZ() = 0.0
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun removingABeltSegment_capturesAndRestoresTheWholeChain(helper: GameTestHelper) = helper.unitTest {
        val level = helper.level
        val start = helper.absolutePos(BlockPos(0, 1, 1))
        val end = helper.absolutePos(BlockPos(2, 1, 1))
        val middle = start.relative(Direction.EAST)
        val shaft = AllBlocks.SHAFT.defaultState.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z)
        listOf(start, end).forEach { level.setBlockAndUpdate(it, shaft) }
        BeltConnectorItem.createBelts(level, start, end)
        val chain = BeltBlock.getBeltChain(level, start)
        assertEquals(3, chain.size, "belt was built")

        val remove = RemoveBlockAction(middle)
        assertEquals(chain.toSet(), remove.changedPositions(level).toSet(), "removing one segment changes the whole chain")
        val restore = remove.inversion(level, middle, BlockSnapshot.capture(level, middle))
        assertTrue(restore is PlaceBeltAction, "belt removal is undone by placing the whole belt")

        chain.forEach { level.setBlockAndUpdate(it, Blocks.AIR.defaultBlockState()) }
        val placeBelt = restore as PlaceBeltAction
        assertTrue(placeBelt.execute(level, StubWorker(level, placeBelt.requiredItems), BeeContext()), "belt placement succeeds")

        assertEquals(chain, BeltBlock.getBeltChain(level, start), "the same chain is back")
    }
}
