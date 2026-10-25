package de.devin.cbbees.gametest.copy

import com.mojang.authlib.GameProfile
import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.schematics.SchematicItem
import de.devin.cbbees.content.planner.AllPlanners
import de.devin.cbbees.content.planner.MasterPlannerItem
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.PlannerClipboard
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.content.domain.history.JobHistory
import de.devin.cbbees.network.CopyAreaPacket
import de.devin.cbbees.network.PrintSchematicPacket
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.neoforged.neoforge.common.util.FakePlayerFactory
import java.util.UUID

object CopyPlannerTests {

    private const val EMPTY = "cbbees:gametest/empty3x3"

    private val PROFILE = GameProfile(UUID.fromString("6c0f6f43-3b3a-4c1e-9d55-0b1f5a1c0c0e"), "cbbees-copy-test")

    private fun GameTestHelper.playerHolding(stack: ItemStack): ServerPlayer =
        FakePlayerFactory.get(level, PROFILE).also { it.setItemInHand(InteractionHand.MAIN_HAND, stack) }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun copy_fillsClipboardWhereItWasCopied(helper: GameTestHelper) {
        val first = helper.absolutePos(BlockPos(0, 1, 0))
        val second = helper.absolutePos(BlockPos(1, 2, 1))
        helper.level.setBlockAndUpdate(first, Blocks.STONE.defaultBlockState())
        helper.level.setBlockAndUpdate(second, Blocks.OAK_PLANKS.defaultBlockState())

        val stack = AllItems.COPY_PLANNER.asStack()
        val player = helper.playerHolding(stack)
        helper.assertTrue(CopyAreaPacket(second, first, cut = false).execute(player), "Copy should succeed")

        helper.assertTrue(stack.get(AllDataComponents.SCHEMATIC_FILE) == PlannerClipboard.FILE, "Clipboard file not set")
        helper.assertTrue(stack.get(AllDataComponents.SCHEMATIC_ANCHOR) == first, "Clipboard should be anchored at the min corner")
        helper.assertTrue(stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false), "Clipboard should be deployed")
        helper.assertTrue(stack.get(AllDataComponents.SCHEMATIC_BOUNDS) == BlockPos(2, 2, 2), "Bounds were ${stack.get(AllDataComponents.SCHEMATIC_BOUNDS)}")

        val template = SchematicItem.loadSchematic(helper.level, stack)
        val blocks = template.filterBlocks(BlockPos.ZERO, StructurePlaceSettings(), Blocks.STONE) +
            template.filterBlocks(BlockPos.ZERO, StructurePlaceSettings(), Blocks.OAK_PLANKS)
        helper.assertTrue(blocks.map { it.pos }.toSet() == setOf(BlockPos.ZERO, BlockPos(1, 1, 1)), "Copied blocks were ${blocks.map { it.pos }}")
        helper.assertTrue(helper.level.getBlockState(first).`is`(Blocks.STONE), "Copy must not touch the world")
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun copy_requiresCopyPlanner(helper: GameTestHelper) {
        val stack = AllItems.CONSTRUCTION_PLANNER.asStack()
        val pos = helper.absolutePos(BlockPos(0, 1, 0))
        helper.assertFalse(CopyAreaPacket(pos, pos, cut = false).execute(helper.playerHolding(stack)), "Copy should be refused")
        helper.assertFalse(ConstructionPlannerItem.hasSchematic(stack), "Planner should stay empty")
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun copy_worksFromMasterPlanner(helper: GameTestHelper) {
        val stack = AllItems.MASTER_PLANNER.asStack()
        while (MasterPlannerItem.currentPlanner(stack) != AllPlanners.COPY) MasterPlannerItem.cycle(stack, forward = true)
        val pos = helper.absolutePos(BlockPos(0, 1, 0))
        helper.level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState())

        helper.assertTrue(CopyAreaPacket(pos, pos, cut = false).execute(helper.playerHolding(stack)), "Copy should succeed")
        helper.assertTrue(AllPlanners.schematicPlannerOf(stack) == AllPlanners.COPY, "Master-Planner should place as the Copy Planner")
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun print_placesInstantlyRecordsUndoAndKeepsClipboard(helper: GameTestHelper) {
        val source = helper.absolutePos(BlockPos(0, 1, 0))
        val target = helper.absolutePos(BlockPos(2, 1, 2))
        helper.level.setBlockAndUpdate(source, Blocks.STONE.defaultBlockState())
        helper.level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState())

        val stack = AllItems.COPY_PLANNER.asStack()
        val player = helper.playerHolding(stack).apply { setGameMode(GameType.CREATIVE) }
        val history = JobHistory.of(player)
        while (history.popUndo() != null) Unit
        CopyAreaPacket(source, source, cut = false).execute(player)

        helper.assertTrue(PrintSchematicPacket(target, Rotation.NONE, Mirror.NONE).execute(player), "Print should succeed")
        helper.assertTrue(helper.level.getBlockState(target).`is`(Blocks.STONE), "Print should place the clipboard instantly")
        helper.assertTrue(ConstructionPlannerItem.hasSchematic(stack), "Copy Planner should keep its clipboard after printing")
        val entry = history.popUndo()
        helper.assertTrue(entry?.changes?.keys == setOf(target), "Print should record one undoable change, got ${entry?.changes?.keys}")
        player.setGameMode(GameType.SURVIVAL)
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun print_requiresCreative(helper: GameTestHelper) {
        val stack = AllItems.COPY_PLANNER.asStack()
        val player = helper.playerHolding(stack).apply { setGameMode(GameType.SURVIVAL) }
        val pos = helper.absolutePos(BlockPos(0, 1, 0))
        CopyAreaPacket(pos, pos, cut = false).execute(player)
        helper.assertFalse(PrintSchematicPacket(pos, Rotation.NONE, Mirror.NONE).execute(player), "Survival players cannot print")
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun paste_keepsClipboardButConstructionClearsSchematic(helper: GameTestHelper) {
        val copy = AllItems.COPY_PLANNER.asStack().apply { set(AllDataComponents.SCHEMATIC_FILE, PlannerClipboard.FILE) }
        val construction = AllItems.CONSTRUCTION_PLANNER.asStack().apply { set(AllDataComponents.SCHEMATIC_FILE, "house.nbt") }

        ConstructionPlannerItem.releaseSchematic(copy)
        ConstructionPlannerItem.releaseSchematic(construction)

        helper.assertTrue(ConstructionPlannerItem.hasSchematic(copy), "Copy Planner should keep its clipboard")
        helper.assertFalse(ConstructionPlannerItem.hasSchematic(construction), "Construction Planner should be cleared")
        helper.succeed()
    }
}
