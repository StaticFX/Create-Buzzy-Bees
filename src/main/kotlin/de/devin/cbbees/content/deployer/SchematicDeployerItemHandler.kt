package de.devin.cbbees.content.deployer

import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/**
 * Single-slot IItemHandler for the Schematic Deployer, enabling hopper/pipe automation.
 * Only accepts items with a SCHEMATIC_PROGRAM data component.
 */
class SchematicDeployerItemHandler(
    private val be: SchematicDeployerBlockEntity
) : IItemHandler {

    override fun getSlots(): Int = 1

    override fun getStackInSlot(slot: Int): ItemStack {
        if (slot != 0) return ItemStack.EMPTY
        return be.heldItem
    }

    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
        if (slot != 0) return stack
        if (!be.heldItem.isEmpty) return stack
        if (!stack.has(AllDataComponents.SCHEMATIC_PROGRAM)) return stack

        if (!simulate) {
            be.heldItem = stack.copyWithCount(1)
            be.resetSettings()
            be.setChanged()
            be.sendData()
        }

        val remainder = stack.copy()
        remainder.shrink(1)
        return remainder
    }

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        if (slot != 0) return ItemStack.EMPTY
        if (be.heldItem.isEmpty) return ItemStack.EMPTY

        val extracted = be.heldItem.copy()
        if (!simulate) {
            be.heldItem = ItemStack.EMPTY
            be.resetSettings()
            be.setChanged()
            be.sendData()
        }
        return extracted
    }

    override fun getSlotLimit(slot: Int): Int = 1

    override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
        return slot == 0 && stack.has(AllDataComponents.SCHEMATIC_PROGRAM)
    }
}
