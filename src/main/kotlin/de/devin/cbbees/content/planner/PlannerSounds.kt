package de.devin.cbbees.content.planner

import com.simibubi.create.AllSoundEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

object PlannerSounds {

    fun reset(player: ServerPlayer) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.75f, 1f)
    }

    fun programmed(player: ServerPlayer) {
        AllSoundEvents.CONFIRM.playOnServer(player.level(), player.blockPosition())
    }
}
