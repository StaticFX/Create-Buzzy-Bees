package de.devin.cbbees.gametest.deconstruction

import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object BreakSpeedTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun ticks(helper: GameTestHelper, block: Block) =
        RemoveBlockAction.diamondToolTicks(helper.level, BlockPos.ZERO, block.defaultBlockState())

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun diamondSpeed_matchesVanillaMining(helper: GameTestHelper) = helper.unitTest {
        assertEquals(6.0, ticks(helper, Blocks.STONE), "stone with a diamond pickaxe")
        assertEquals(188.0, ticks(helper, Blocks.OBSIDIAN), "obsidian with a diamond pickaxe")
        assertEquals(2.0, ticks(helper, Blocks.DIRT), "dirt with a diamond shovel")
        assertEquals(0.0, ticks(helper, Blocks.SHORT_GRASS), "grass breaks instantly")
    }
}
