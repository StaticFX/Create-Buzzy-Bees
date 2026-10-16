package de.devin.cbbees.compat.sable

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.function.Consumer

interface RenderSpace {
    fun draw(poseStack: PoseStack, anchor: BlockPos, camera: Vec3, partialTick: Float, render: Consumer<Vec3>)
    fun toLocal(anchor: BlockPos, world: Vec3, partialTick: Float): Vec3
}

object FlatRenderSpace : RenderSpace {
    override fun draw(poseStack: PoseStack, anchor: BlockPos, camera: Vec3, partialTick: Float, render: Consumer<Vec3>) =
        render.accept(camera)

    override fun toLocal(anchor: BlockPos, world: Vec3, partialTick: Float): Vec3 = world
}
