package de.devin.cbbees.gametest.compat

import appeng.api.parts.PartHelper
import appeng.api.util.AEColor
import appeng.blockentity.networking.CableBusBlockEntity
import appeng.core.definitions.AEItems
import appeng.core.definitions.AEParts
import appeng.items.parts.FacadeItem
import appeng.parts.automation.ImportBusPart
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import com.simibubi.create.foundation.utility.BlockHelper
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object AE2SchematicTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private val cable get() = AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)

    private fun buildBus(helper: GameTestHelper): CableBusBlockEntity {
        val pos = helper.absolutePos(BlockPos(1, 1, 1))
        PartHelper.setPart(helper.level, pos, null, null, cable)
        val bus = PartHelper.setPart(helper.level, pos, Direction.WEST, null, AEParts.IMPORT_BUS.get())!!
        bus.upgrades.addItems(ItemStack(AEItems.SPEED_CARD))
        val facade = AEItems.FACADE.get().createFacadeForItemUnchecked(ItemStack(Items.STONE))
        PartHelper.getPartHost(helper.level, pos)!!.facadeContainer.addFacade(FacadeItem.createFacade(facade, Direction.UP))
        return helper.level.getBlockEntity(pos) as CableBusBlockEntity
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun cableBus_needsItsCablePartsAndFacades(helper: GameTestHelper) = helper.unitTest {
        val bus = buildBus(helper)
        val required = ItemRequirement.of(bus.blockState, bus).requiredItems.map { it.stack.item }
        assertFalse(ItemRequirement.of(bus.blockState, bus).isInvalid, "the cable bus can be built")
        assertEquals(setOf(cable, AEParts.IMPORT_BUS.asItem(), AEItems.FACADE.asItem()), required.toSet(), "required items")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    @JvmStatic
    fun cableBus_copiesPartsButNotUpgrades(helper: GameTestHelper) {
        val bus = buildBus(helper)
        val data = BlockHelper.prepareBlockEntityData(helper.level, bus.blockState, bus)
        helper.assertTrue(data != null, "the cable bus keeps safe data")
        helper.assertTrue(data!!.getCompound("west").getString("id").isNotEmpty(), "the bus is kept")
        helper.assertFalse(data.getCompound("west").contains("upgrades"), "upgrade cards are not copied")
        helper.assertFalse(data.getCompound("west").contains("gn"), "the owner is not copied")

        val target = helper.absolutePos(BlockPos(1, 1, 0))
        BlockHelper.placeSchematicBlock(helper.level, bus.blockState, target, ItemStack.EMPTY, data)
        helper.runAfterDelay(5) {
            val copy = helper.level.getBlockEntity(target) as? CableBusBlockEntity
            helper.assertTrue(copy != null, "a cable bus was placed")
            helper.assertTrue(copy!!.getPart(null)?.partItem == cable, "the cable came along")
            val copiedBus = copy.getPart(Direction.WEST) as? ImportBusPart
            helper.assertTrue(copiedBus != null, "the import bus came along")
            helper.assertTrue(copiedBus!!.upgrades.isEmpty, "the import bus has no upgrades")
            helper.assertTrue(copy.facadeContainer.getFacade(Direction.UP) != null, "the facade came along")
            helper.succeed()
        }
    }
}
