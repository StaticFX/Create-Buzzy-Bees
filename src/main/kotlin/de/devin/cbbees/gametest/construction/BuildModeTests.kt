package de.devin.cbbees.gametest.construction

import com.mojang.authlib.GameProfile
import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.schematics.SchematicItem
import de.devin.cbbees.content.domain.action.impl.PlaceBlockAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.schematics.BlocksInTheWay
import de.devin.cbbees.content.schematics.BuildMode
import de.devin.cbbees.content.schematics.BuildModes
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.CopyAreaPacket
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.neoforged.neoforge.common.util.FakePlayerFactory
import java.util.UUID

object BuildModeTests {

    private const val EMPTY = "cbbees:gametest/empty3x3"

    private val PROFILE = GameProfile(UUID.fromString("0b6f3c1e-5d2a-4f7e-9c1b-3a8d2e6f4b71"), "cbbees-build-mode-test")

    private class Site(val stoneSpot: BlockPos, val emptySpot: BlockPos, val batches: List<TaskBatch>) {
        fun removes(pos: BlockPos) = batches.any { batch -> batch.tasks.any { (it.action as? RemoveBlockAction)?.pos == pos } }
        fun places(pos: BlockPos) = batches.firstOrNull { batch -> batch.tasks.any { (it.action as? PlaceBlockAction)?.pos == pos } }
        fun removalPhase(pos: BlockPos) = batches.first { batch -> batch.tasks.any { (it.action as? RemoveBlockAction)?.pos == pos } }.phase
    }

    private fun GameTestHelper.copyStoneOntoGlass(): ItemStack {
        setBlock(BlockPos(0, 1, 0), Blocks.STONE)
        setBlock(BlockPos(0, 1, 1), Blocks.GLASS)
        setBlock(BlockPos(1, 1, 1), Blocks.GLASS)

        val stack = AllItems.COPY_PLANNER.asStack()
        val player = FakePlayerFactory.get(level, PROFILE).also { it.setItemInHand(InteractionHand.MAIN_HAND, stack) }
        assertTrue(CopyAreaPacket(absolutePos(BlockPos(0, 1, 0)), absolutePos(BlockPos(1, 1, 0)), cut = false).execute(player), "Copy should succeed")
        stack.set(AllDataComponents.SCHEMATIC_ANCHOR, absolutePos(BlockPos(0, 1, 1)))
        return stack
    }

    private fun GameTestHelper.buildOverBlocks(mode: BuildMode): Site {
        val stack = copyStoneOntoGlass()
        val target = absolutePos(BlockPos(0, 1, 1))

        val job = BeeJob(UUID.randomUUID(), target, level, JobType.Construction)
        val bridge = SchematicCreateBridge(level, mode)
        assertTrue(bridge.loadSchematic(stack), "Schematic should load")
        return Site(target, target.east(), bridge.generateBuildTasks(job))
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun replace_breaksBlocksInTheWayBeforeBuilding(helper: GameTestHelper) {
        val site = helper.buildOverBlocks(BuildModes.REPLACE)
        val place = site.places(site.stoneSpot)
        helper.assertTrue(place != null, "Stone should be placed")
        helper.assertTrue(site.removes(site.stoneSpot), "Glass in the way should be broken first")
        helper.assertTrue(site.removalPhase(site.stoneSpot) < place!!.phase, "Breaking must happen before building")
        helper.assertFalse(site.removes(site.emptySpot), "Replace should leave blocks where the schematic is empty")
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun keep_skipsSpotsThatAreTaken(helper: GameTestHelper) {
        val site = helper.buildOverBlocks(BuildModes.KEEP)
        helper.assertTrue(site.places(site.stoneSpot) == null, "Keep should not build over glass")
        helper.assertTrue(site.batches.none { batch -> batch.tasks.any { it.action is RemoveBlockAction } }, "Keep should never break blocks")
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun clear_alsoClearsWhereTheSchematicIsEmpty(helper: GameTestHelper) {
        val site = helper.buildOverBlocks(BuildModes.CLEAR)
        helper.assertTrue(site.places(site.stoneSpot) != null, "Stone should be placed")
        helper.assertTrue(site.removes(site.stoneSpot), "Glass under the stone should be broken")
        helper.assertTrue(site.removes(site.emptySpot), "Glass where the schematic is empty should be broken")
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun inTheWay_onlyCountsBlocksTheBuildWouldReplace(helper: GameTestHelper) {
        val stack = helper.copyStoneOntoGlass()
        val target = helper.absolutePos(BlockPos(0, 1, 1))
        val template = SchematicItem.loadSchematic(helper.level, stack)

        val blocked = BlocksInTheWay.find(helper.level, template, target, StructurePlaceSettings())
        helper.assertTrue(blocked == listOf(target), "Only the glass under the stone is in the way, got $blocked")

        helper.setBlock(BlockPos(0, 1, 1), Blocks.STONE)
        val matching = BlocksInTheWay.find(helper.level, template, target, StructurePlaceSettings())
        helper.assertTrue(matching.isEmpty(), "A matching block is not in the way, got $matching")
        helper.succeed()
    }
}
