package de.devin.cbbees.gametest.planner

import de.devin.cbbees.content.planner.AllPlanners
import de.devin.cbbees.content.planner.MasterPlannerItem
import de.devin.cbbees.items.AllItems
import com.mojang.authlib.GameProfile
import com.simibubi.create.AllDataComponents
import com.simibubi.create.Create
import com.simibubi.create.AllItems as CreateItems
import de.devin.cbbees.content.planner.SchematicAndQuillPlanner
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.common.util.FakePlayerFactory
import java.util.UUID

object MasterPlannerTests {

    private const val TEMPLATE = "cbbees:gametest/construction/cbbees_construction"

    @GameTest(template = TEMPLATE)
    @JvmStatic
    fun masterPlanner_cyclesThroughAllPlanners(helper: GameTestHelper) {
        val stack = AllItems.MASTER_PLANNER.asStack()
        helper.assertTrue(MasterPlannerItem.currentPlanner(stack) == AllPlanners.CONSTRUCTION, "Should start as the Construction Planner")

        val visited = AllPlanners.ALL.map { MasterPlannerItem.cycle(stack, forward = true) }
        helper.assertTrue(visited == AllPlanners.ALL.drop(1) + AllPlanners.ALL.first(), "Forward cycle order was $visited")

        helper.assertTrue(MasterPlannerItem.cycle(stack, forward = false) == AllPlanners.ALL.last(), "Backward cycle should wrap around")
        helper.succeed()
    }

    @GameTest(template = TEMPLATE)
    @JvmStatic
    fun masterPlanner_actsOnlyAsCurrentPlanner(helper: GameTestHelper) {
        val stack = AllItems.MASTER_PLANNER.asStack()
        MasterPlannerItem.cycle(stack, forward = true)

        helper.assertTrue(AllPlanners.DECONSTRUCTION.isIn(stack), "Should act as the Deconstruction Planner")
        helper.assertFalse(AllPlanners.CONSTRUCTION.isIn(stack), "Should not act as the Construction Planner")
        helper.assertFalse(AllPlanners.PICKUP.isIn(stack), "Should not act as the Pickup Planner")
        helper.assertFalse(AllPlanners.COPY.isIn(stack), "Should not act as the Copy Planner")
        helper.assertTrue(AllPlanners.PICKUP.isIn(AllItems.PICKUP_PLANNER.asStack()), "Standalone planners still count")
        helper.succeed()
    }

    @GameTest(template = "cbbees:gametest/empty3x3")
    @JvmStatic
    fun masterPlanner_savesSchematicLikeTheSchematicAndQuill(helper: GameTestHelper) {
        val stack = AllItems.MASTER_PLANNER.asStack()
        MasterPlannerItem.select(stack, AllPlanners.SCHEMATIC_AND_QUILL)
        helper.assertTrue(SchematicAndQuillPlanner.isHeld(stack), "Master-Planner should count as the Schematic and Quill")
        helper.assertTrue(SchematicAndQuillPlanner.isHeld(CreateItems.SCHEMATIC_AND_QUILL.asStack()), "The real Schematic and Quill still counts")

        val origin = helper.absolutePos(BlockPos(0, 1, 0))
        helper.level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState())
        val player = FakePlayerFactory.get(helper.level, GameProfile(UUID.fromString("0d3c1f2e-5a6b-4c7d-8e9f-a0b1c2d3e4f5"), "cbbees-quill-test"))
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)

        Create.SCHEMATIC_RECEIVER.handleInstantSchematic(player, "quill_test.nbt", helper.level, origin, BlockPos(1, 1, 1))

        helper.assertTrue(player.mainHandItem === stack, "Master-Planner must not be replaced by a schematic")
        helper.assertTrue(MasterPlannerItem.currentPlanner(stack) == AllPlanners.CONSTRUCTION, "Should switch to the Construction Planner")
        helper.assertTrue(stack.get(AllDataComponents.SCHEMATIC_FILE) == "quill_test.nbt", "Saved schematic should be loaded")
        helper.assertTrue(stack.get(AllDataComponents.SCHEMATIC_ANCHOR) == origin, "Schematic should be placed where it was saved")
        helper.succeed()
    }
}
