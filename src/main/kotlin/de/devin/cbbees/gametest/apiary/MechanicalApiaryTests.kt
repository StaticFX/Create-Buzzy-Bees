package de.devin.cbbees.gametest.apiary

import com.simibubi.create.content.kinetics.mixer.CompactingRecipe
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.blocks.AllBlocks
import de.devin.cbbees.content.apiary.MechanicalApiaryBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.ItemHandlerHelper

object MechanicalApiaryTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"
    private val APIARY_POS = BlockPos(1, 1, 1)

    private fun GameTestHelper.placeApiary(): MechanicalApiaryBlockEntity {
        setBlock(APIARY_POS, AllBlocks.MECHANICAL_APIARY.defaultState)
        return getBlockEntity(APIARY_POS)
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    @JvmStatic
    fun producesHoneyFromBonemealAndFlowers(helper: GameTestHelper) {
        val apiary = helper.placeApiary()
        helper.runAfterDelay(2) { apiary.setSpeed(256f) }
        ItemHandlerHelper.insertItemStacked(apiary.itemCapability, ItemStack(Items.BONE_MEAL, 4), false)
        ItemHandlerHelper.insertItemStacked(apiary.itemCapability, ItemStack(Items.DANDELION, 4), false)

        helper.succeedWhen {
            val amount = apiary.tank.primaryHandler.fluidAmount
            helper.assertTrue(amount == 200, "Expected 200mB honey, has ${amount}mB")
            helper.assertTrue(
                (0 until apiary.inputInv.slots).all { apiary.inputInv.getStackInSlot(it).isEmpty },
                "Inputs should be fully consumed"
            )
        }
    }

    @GameTest(template = TEMPLATE)
    @JvmStatic
    fun bonemealCannotFillBothSlots(helper: GameTestHelper) {
        val apiary = helper.placeApiary()

        val remainder = ItemHandlerHelper.insertItemStacked(apiary.itemCapability, ItemStack(Items.BONE_MEAL, 128), false)
        helper.assertTrue(remainder.count == 64, "Second stack of bonemeal should be rejected, remainder ${remainder.count}")

        val flowers = ItemHandlerHelper.insertItemStacked(apiary.itemCapability, ItemStack(Items.POPPY, 8), false)
        helper.assertTrue(flowers.isEmpty, "Flowers should still fit after bonemeal")

        val stone = ItemHandlerHelper.insertItemStacked(apiary.itemCapability, ItemStack(Items.STONE), false)
        helper.assertTrue(!stone.isEmpty, "Non-recipe items should be rejected")
        helper.succeed()
    }

    @GameTest(template = TEMPLATE)
    @JvmStatic
    fun honeyOnlyExposedOnBack(helper: GameTestHelper) {
        helper.placeApiary()
        val pos = helper.absolutePos(APIARY_POS)
        for (side in Direction.entries) {
            val handler = helper.level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side)
            helper.assertTrue((handler != null) == (side == Direction.SOUTH), "Unexpected fluid access on $side")
        }
        helper.assertTrue(helper.level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null, "Bottles need sideless access")
        helper.succeed()
    }

    @GameTest(template = TEMPLATE)
    @JvmStatic
    fun honeycombCompactingRecipeLoads(helper: GameTestHelper) {
        val recipe = helper.level.recipeManager.byKey(CreateBuzzyBeez.asResource("compacting/honeycomb"))
        helper.assertTrue(recipe.isPresent, "Missing honeycomb compacting recipe")
        val value = recipe.get().value()
        helper.assertTrue(value is CompactingRecipe, "Wrong recipe type")
        helper.assertTrue(value.getResultItem(helper.level.registryAccess()).`is`(Items.HONEYCOMB), "Should output honeycomb")
        helper.succeed()
    }
}
