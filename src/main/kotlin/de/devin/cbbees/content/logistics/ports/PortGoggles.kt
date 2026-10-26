package de.devin.cbbees.content.logistics.ports

import net.createmod.catnip.lang.Lang
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.state.BlockState

/** Explains a red bulb: the block the port is attached to offers no storage it can use. */
fun MutableList<Component>.addInvalidPortTooltip(state: BlockState, reasonKey: String) {
    if (state.getValue(PortBlock.PORT_STATE) != PortState.INVALID) return
    Lang.builder("cbbees").translate(reasonKey).style(ChatFormatting.RED).forGoggles(this, 1)
}
