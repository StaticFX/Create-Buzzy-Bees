package de.devin.cbbees.gametest.zapper

import de.devin.cbbees.content.zapper.FlattenPlanner
import de.devin.cbbees.content.zapper.ShapingTools
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

object ZapperPlanningTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private val STONE = Blocks.STONE.defaultBlockState()
    private val DIRT = Blocks.DIRT.defaultBlockState()
    private val AIR = Blocks.AIR.defaultBlockState()

    private fun GameTestHelper.fill(state: BlockState, vararg relative: BlockPos): List<BlockPos> =
        relative.map { setBlock(it, state); absolutePos(it) }

    private fun GameTestHelper.layer(y: Int, state: BlockState): List<BlockPos> =
        fill(state, *(0..2).flatMap { x -> (0..2).map { z -> BlockPos(x, y, z) } }.toTypedArray())

    private fun GameTestHelper.changes(edits: Map<BlockPos, BlockState>) =
        edits.filter { (pos, state) -> level.getBlockState(pos) != state }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun fill_onlyTargetsReplaceable(helper: GameTestHelper) = helper.unitTest {
        val solid = helper.fill(STONE, BlockPos(0, 1, 0))
        val empty = helper.fill(AIR, BlockPos(1, 1, 0))

        val edits = ShapingTools.fill.plan(helper.level, solid + empty, Direction.UP, DIRT)

        assertEquals(empty.associateWith { DIRT }, edits, "fill edits")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun replace_onlyTargetsSolid(helper: GameTestHelper) = helper.unitTest {
        val solid = helper.fill(STONE, BlockPos(0, 1, 0))
        val empty = helper.fill(AIR, BlockPos(1, 1, 0))

        val edits = ShapingTools.replace.plan(helper.level, solid + empty, Direction.UP, DIRT)

        assertEquals(solid.associateWith { DIRT }, edits, "replace edits")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun overlay_placesAboveSolidOnly(helper: GameTestHelper) = helper.unitTest {
        val solid = helper.fill(STONE, BlockPos(0, 1, 0))
        val painted = helper.fill(DIRT, BlockPos(1, 1, 0))
        helper.fill(AIR, BlockPos(0, 2, 0), BlockPos(1, 2, 0))

        val edits = ShapingTools.overlay.plan(helper.level, solid + painted, Direction.UP, DIRT)

        assertEquals(mapOf(solid.single().above() to DIRT), edits, "overlay edits")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun flatten_flatSurface_changesNothing(helper: GameTestHelper) = helper.unitTest {
        val ground = helper.layer(1, STONE)
        helper.layer(2, AIR)

        val edits = FlattenPlanner.plan(helper.level, ground, Direction.UP)

        assertEquals(emptyMap<BlockPos, BlockState>(), helper.changes(edits), "flatten changes")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun flatten_singleBump_isRemoved(helper: GameTestHelper) = helper.unitTest {
        val ground = helper.layer(1, STONE)
        helper.layer(2, AIR)
        helper.layer(3, AIR)
        val bump = helper.fill(STONE, BlockPos(1, 2, 1))

        val edits = FlattenPlanner.plan(helper.level, ground + bump, Direction.UP)

        assertEquals(bump.associateWith { AIR }, helper.changes(edits), "flatten changes")
    }
}
