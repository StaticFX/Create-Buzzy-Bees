package de.devin.cbbees.content.domain.job

import de.devin.cbbees.util.ServerSide
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/** Tells a job's owner when the job is finished, wherever they are. */
@ServerSide
object JobNotifications {

    fun finished(job: BeeJob) {
        val owner = job.ownerId?.let { job.level.server?.playerList?.getPlayer(it) } ?: return
        owner.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1f)
        owner.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.5f)
        owner.displayClientMessage(
            Component.translatable("cbbees.job.finished", job.displayName().copy().withStyle(ChatFormatting.GOLD)),
            true
        )
    }
}
