package de.devin.cbbees.content.domain.job.client

import de.devin.cbbees.network.JobProgressPacket
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

/**
 * Client-side handler for [JobProgressPacket]. Looks up an existing
 * [JobProgressToast] for the job (by jobId) and updates it in place, or creates
 * a new toast if none exists. Multiple concurrent jobs each get their own toast
 * which stack in the vanilla toast queue.
 */
@OnlyIn(Dist.CLIENT)
object JobProgressClient {

    fun apply(packet: JobProgressPacket) {
        val toasts = Minecraft.getInstance().toasts
        val existing = toasts.getToast(JobProgressToast::class.java, packet.jobId)
        if (existing != null) {
            existing.applyUpdate(packet)
        } else {
            val toast = JobProgressToast(packet.jobId)
            toast.applyUpdate(packet)
            toasts.addToast(toast)
        }
    }
}
