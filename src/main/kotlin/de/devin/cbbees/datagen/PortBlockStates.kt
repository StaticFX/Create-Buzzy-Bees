package de.devin.cbbees.datagen

import com.tterrag.registrate.providers.DataGenContext
import com.tterrag.registrate.providers.RegistrateBlockstateProvider
import de.devin.cbbees.content.logistics.ports.PortBlock
import de.devin.cbbees.content.logistics.ports.PortState
import net.minecraft.core.Direction
import net.minecraft.util.StringRepresentable
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.EnumProperty

/**
 * Multipart blockstate shared by all ports: a base model per [modeProperty] value and a bulb model
 * per [PortBlock.PORT_STATE], both rotated to the face the port is attached to.
 * Models are read from `block/<port name>/base_<mode>` and `block/<port name>/bulb_<state>`.
 */
object PortBlockStates {

    fun <T, E> generate(
        context: DataGenContext<Block, T>,
        provider: RegistrateBlockstateProvider,
        modeProperty: EnumProperty<E>,
    ) where T : Block, E : Enum<E>, E : StringRepresentable {
        val builder = provider.getMultipartBuilder(context.get())
        fun model(name: String) = provider.models().getExistingFile(provider.modLoc("block/${context.name}/$name"))

        for (face in AttachFace.entries) {
            for (facing in Direction.Plane.HORIZONTAL) {
                val rotationX = PortBlock.modelRotationX(face)
                val rotationY = facing.toYRot().toInt()

                for (mode in modeProperty.possibleValues) {
                    builder.part().modelFile(model("base_${mode.serializedName}"))
                        .rotationX(rotationX).rotationY(rotationY).addModel()
                        .condition(FACE, face).condition(FACING, facing).condition(modeProperty, mode).end()
                }
                for (state in PortState.entries) {
                    builder.part().modelFile(model("bulb_${state.serializedName}"))
                        .rotationX(rotationX).rotationY(rotationY).addModel()
                        .condition(FACE, face).condition(FACING, facing).condition(PortBlock.PORT_STATE, state).end()
                }
            }
        }
    }
}
