package de.devin.cbbees.compat.refinedstorage

import com.refinedmods.refinedstorage.common.api.configurationcard.ConfigurationCardTarget
import com.refinedmods.refinedstorage.common.support.AbstractDirectionalBlock
import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry
import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry.SafeNbtWriter
import de.devin.cbbees.content.schematics.BlockTransform
import de.devin.cbbees.content.schematics.BlockTransforms
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property

object RefinedStorageSchematics : BlockTransform {

    private const val NAMESPACE = "refinedstorage"
    private const val DIRECTION = "direction"

    fun register() {
        BlockTransforms.register(this)
        SafeNbtWriterRegistry.REGISTRY.registerProvider { type ->
            writer.takeIf { BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type)?.namespace == NAMESPACE }
        }
    }

    private val writer = SafeNbtWriter { blockEntity, tag, registries ->
        (blockEntity as? ConfigurationCardTarget)?.writeConfiguration(tag, registries)
    }

    override fun mirror(state: BlockState, mirror: Mirror) = turned(state, mirror::mirror)

    override fun rotate(state: BlockState, rotation: Rotation) = turned(state, rotation::rotate)

    private fun turned(state: BlockState, turn: (Direction) -> Direction): BlockState? {
        if (state.block !is AbstractDirectionalBlock<*>) return null
        val property = state.properties.firstOrNull { it.name == DIRECTION } ?: return state
        return withTurnedDirection(state, property, turn)
    }

    private fun <T : Comparable<T>> withTurnedDirection(state: BlockState, property: Property<T>, turn: (Direction) -> Direction): BlockState {
        val name = property.getName(state.getValue(property)).split('_').joinToString("_") { part ->
            Direction.byName(part)?.takeIf { it.axis.isHorizontal }?.let(turn)?.serializedName ?: part
        }
        return property.getValue(name).map { state.setValue(property, it) }.orElse(state)
    }
}
