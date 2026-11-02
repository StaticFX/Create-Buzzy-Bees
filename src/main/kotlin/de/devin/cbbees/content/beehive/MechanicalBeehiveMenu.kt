package de.devin.cbbees.content.beehive

import de.devin.cbbees.blocks.AllBlocks
import de.devin.cbbees.registry.AllMenuTypes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack

class MechanicalBeehiveMenu(
    containerId: Int,
    val content: MechanicalBeehiveBlockEntity
) : AbstractContainerMenu(AllMenuTypes.MECHANICAL_BEEHIVE.get(), containerId) {

    // No slots – inventory is automation-only now

    override fun stillValid(player: Player): Boolean = AbstractContainerMenu.stillValid(
        ContainerLevelAccess.create(content.world, content.pos),
        player,
        AllBlocks.MECHANICAL_BEEHIVE.get()
    )

    // Disallow shift-clicks from doing anything
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY
}
