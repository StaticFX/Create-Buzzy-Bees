package de.devin.cbbees.content.schematics

import de.devin.cbbees.api.schematic.SchematicSubstitute
import de.devin.cbbees.api.schematic.Substitute
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

object SchematicSubstitutes {

    private val substitutes = mutableListOf<SchematicSubstitute>()

    fun register(substitute: SchematicSubstitute) {
        substitutes += substitute
    }

    fun of(pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Substitute? =
        substitutes.firstNotNullOfOrNull { it.substitute(pos, state, blockEntity) }
}
