package de.devin.cbbees.content.apiary

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState

class MechanicalApiaryRenderer(context: BlockEntityRendererProvider.Context) :
    KineticBlockEntityRenderer<MechanicalApiaryBlockEntity>(context) {

    override fun getRotatedModel(be: MechanicalApiaryBlockEntity, state: BlockState): SuperByteBuffer =
        CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, Direction.DOWN)
}
