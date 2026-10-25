package de.devin.cbbees.content.planner

import com.simibubi.create.AllSoundEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

object PlannerSounds {

    fun reset(player: ServerPlayer) {
        AllSoundEvents.CLIPBOARD_ERASE.playOnServer(player.level(), player.blockPosition(), 0.5f, 1f)
    }

    fun programmed(player: ServerPlayer) {
        val level = player.level()
        val pos = player.blockPosition()
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.15f, 0.78f)
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.2f, 1.5f)
        AllSoundEvents.STEAM.playOnServer(level, pos, 0.4f, 0.5f)
    }
}
