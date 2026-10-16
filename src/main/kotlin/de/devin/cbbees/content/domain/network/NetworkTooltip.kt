package de.devin.cbbees.content.domain.network

import net.createmod.catnip.lang.Lang
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

fun INetworkComponent.addNetworkTooltip(tooltip: MutableList<Component>, indent: Int = 0) {
    val value = if (isOrphaned) {
        Lang.builder("cbbees").translate("gui.goggles.network.none").style(ChatFormatting.RED)
    } else {
        Lang.builder("cbbees").text(network().name).style(ChatFormatting.GOLD)
    }
    Lang.builder("cbbees").translate("gui.goggles.beehive.network")
        .style(ChatFormatting.GRAY)
        .add(value)
        .forGoggles(tooltip, indent)
}
