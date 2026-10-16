package de.devin.cbbees.compat.sable

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.function.Consumer

object SubLevelRenderSpace : RenderSpace {

    override fun draw(poseStack: PoseStack, anchor: BlockPos, camera: Vec3, partialTick: Float, render: Consumer<Vec3>) {
        val frame = CompanionSubLevels.clientFrame(anchor, partialTick) ?: return render.accept(camera)
        poseStack.pushPose()
        render.accept(frame.applyTo(poseStack, camera))
        poseStack.popPose()
    }

    override fun toLocal(anchor: BlockPos, world: Vec3, partialTick: Float): Vec3 =
        CompanionSubLevels.clientFrame(anchor, partialTick)?.unproject(world) ?: world
}
