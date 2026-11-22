package de.devin.cbbees.compat.immersiveengineering

import blusunrize.immersiveengineering.api.IEProperties
import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler
import blusunrize.immersiveengineering.api.multiblocks.TemplateMultiblock
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE
import blusunrize.immersiveengineering.api.multiblocks.blocks.registry.MultiblockPartBlock
import blusunrize.immersiveengineering.api.utils.DirectionUtils
import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries
import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries.BlockRequirement
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import de.devin.cbbees.api.CBBeesApi
import de.devin.cbbees.api.schematic.BlockTransform
import de.devin.cbbees.api.schematic.SchematicSubstitute
import de.devin.cbbees.api.schematic.Substitute
import net.createmod.catnip.levelWrappers.WrappedLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Builds formed IE multiblocks from schematics.
 *
 * A formed multiblock is one [MultiblockPartBlock] at every position, which has no item. Bees place the block
 * each part was formed from instead, and once everything is placed the multiblock is formed again at its trigger
 * block, the same as hitting it with the Engineer's Hammer.
 *
 * IE parts don't turn with the schematic on their own, so their facing and mirroring are turned here; each part
 * keeps its position in the multiblock's template, which is how its original block is found.
 */
object MultiblockSchematics : BlockTransform, SchematicSubstitute {

    private val multiblocks: Map<Block, TemplateMultiblock> by lazy {
        MultiblockHandler.getMultiblocks().filterIsInstance<TemplateMultiblock>().associateBy { it.block }
    }

    fun register() {
        CBBeesApi.registerBlockTransform(this)
        CBBeesApi.registerSchematicSubstitute(this)
        SchematicRequirementRegistries.BLOCKS.registerProvider { block -> requirement.takeIf { block is MultiblockPartBlock<*> } }
    }

    private val requirement = BlockRequirement { state, blockEntity ->
        partOf(state, blockEntity)?.originalBlock()?.let { ItemRequirement.of(it, null) } ?: ItemRequirement.INVALID
    }

    override fun substitute(pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Substitute? {
        val part = partOf(state, blockEntity) ?: return null
        val original = part.originalBlock() ?: return null
        val form = if (part.isMaster) FormMultiblockAction(part.triggerFrom(pos), part.multiblock.uniqueName, part.facing.opposite) else null
        return Substitute(original, form)
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState? {
        if (state.block !is MultiblockPartBlock<*>) return null
        return state.setValue(IEProperties.FACING_HORIZONTAL, rotation.rotate(state.getValue(IEProperties.FACING_HORIZONTAL)))
    }

    /** Mirroring a multiblock mirrors its front and flips whether its template is mirrored. */
    override fun mirror(state: BlockState, mirror: Mirror): BlockState? {
        if (state.block !is MultiblockPartBlock<*> || mirror == Mirror.NONE) return null
        val turned = state.setValue(IEProperties.FACING_HORIZONTAL, mirror.mirror(state.getValue(IEProperties.FACING_HORIZONTAL)))
        return if (turned.hasProperty(IEProperties.MIRRORED)) turned.cycle(IEProperties.MIRRORED) else turned
    }

    private fun partOf(state: BlockState, blockEntity: BlockEntity?): Part? {
        val multiblock = multiblocks[state.block] ?: return null
        val level = blockEntity?.level ?: return null
        val isMaster = !state.getValue(IEProperties.MULTIBLOCKSLAVE)
        val posInMB = if (isMaster) multiblock.masterFromOriginOffset
        else (blockEntity as? IMultiblockBE<*>)?.helper?.positionInMB ?: return null
        val mirrored = state.getOptionalValue(IEProperties.MIRRORED).orElse(false)
        return Part(multiblock, level, posInMB, isMaster, state.getValue(IEProperties.FACING_HORIZONTAL), mirrored)
    }

    private class Part(
        val multiblock: TemplateMultiblock,
        private val level: Level,
        private val posInMB: BlockPos,
        val isMaster: Boolean,
        val facing: Direction,
        private val mirrored: Boolean,
    ) {
        /** The block this part was formed from, turned like the multiblock. Mirrors IE's disassembly. */
        fun originalBlock(): BlockState? {
            val rotation = DirectionUtils.getRotationBetweenFacings(Direction.NORTH, facing) ?: return null
            val original = multiblock.getStructure(templateLevel()).firstOrNull { it.pos() == posInMB }?.state() ?: return null
            return original.mirror(if (mirrored) Mirror.FRONT_BACK else Mirror.NONE).rotate(rotation)
        }

        /** The trigger block of the multiblock whose master sits at [masterPos]. */
        fun triggerFrom(masterPos: BlockPos): BlockPos {
            val origin = masterPos.subtract(offset(multiblock.masterFromOriginOffset))
            return origin.offset(offset(multiblock.triggerOffset))
        }

        private fun offset(posInMB: BlockPos) = TemplateMultiblock.getAbsoluteOffset(posInMB, mirrored, facing)

        /** IE reads templates from the server's structure manager, or from its synced copies on the client. */
        private fun templateLevel(): Level =
            if (level is WrappedLevel && !level.isClientSide) level.level else level
    }
}
