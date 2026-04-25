package de.devin.cbbees.content.schematics

import com.simibubi.create.AllBlocks
import com.simibubi.create.api.contraption.BlockMovementChecks
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.belt.BeltPart
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf

/**
 * Classifies blocks for schematic placement ordering, mirroring Create's SchematiCannon behavior.
 *
 * Create uses a three-pass system:
 * 1. **Normal blocks** (bottom-up): Solid support blocks placed first.
 * 2. **Deferred blocks**: Brittle/dependent blocks placed after all supports exist.
 * 3. **Entities**: Placed last (not yet handled).
 *
 * This classifier adapts that approach for parallel bee placement by assigning
 * priority offsets that guarantee all normal blocks complete before deferred blocks begin.
 *
 * Unlike Create's SchematiCannon which uses [BeltConnectorItem.createBelts] for belt
 * construction, we now mirror that approach by letting the controller segment trigger a
 * full belt placement task. Middle belt segments are ignored during task generation; the
 * belt connector creates the full chain once the controller task runs.
 *
 * @see com.simibubi.create.content.schematics.SchematicPrinter.shouldDeferBlock
 * @see com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity.shouldIgnoreBlockState
 */
object BlockPlacementClassifier {

    /**
     * Priority offset for normal (first-pass) blocks.
     * Range: [NORMAL_BLOCK_OFFSET + 1, NORMAL_BLOCK_OFFSET + 256].
     */
    const val NORMAL_BLOCK_OFFSET = 512

    /**
     * Priority offset for deferred (second-pass) blocks.
     * Range: [DEFERRED_BLOCK_OFFSET + 1, DEFERRED_BLOCK_OFFSET + 256].
     * Guarantees all deferred blocks are placed after all normal blocks.
     */
    const val DEFERRED_BLOCK_OFFSET = 0

    /**
     * Priority offset for removing dependent/brittle blocks first.
     * These must be removed before their support blocks to avoid
     * items dropping on the ground from broken attachments.
     */
    const val REMOVAL_DEPENDENT_OFFSET = 512

    /**
     * Priority offset for removing normal (solid/support) blocks second.
     */
    const val REMOVAL_NORMAL_OFFSET = 0

    /**
     * Determines if a block should be deferred to a second pass.
     * Deferred blocks depend on adjacent solid blocks for support.
     *
     * Includes:
     * - Create's belts (need support shafts and structure placed first)
     * - Create's belt tunnels (need belt placed first, otherwise destroyed on belt placement)
     * - Create's gantry carriages (need shafts)
     * - Create's mechanical arms (need base)
     * - Brittle blocks: torches, ladders, signs, pressure plates, buttons/levers,
     *   rails, redstone components, carpets, hanging blocks, belt funnels, etc.
     *
     * @see com.simibubi.create.content.schematics.SchematicPrinter.shouldDeferBlock
     * @see com.simibubi.create.api.contraption.BlockMovementChecks.isBrittle
     */
    fun shouldDeferBlock(state: BlockState): Boolean {
        return AllBlocks.BELT.has(state)
                || AllBlocks.ANDESITE_TUNNEL.has(state)
                || AllBlocks.BRASS_TUNNEL.has(state)
                || AllBlocks.GANTRY_CARRIAGE.has(state)
                || AllBlocks.MECHANICAL_ARM.has(state)
                || BlockMovementChecks.isBrittle(state)
    }

    /**
     * Determines if a block should be completely skipped during task generation.
     * These are secondary parts of multi-block structures — their primary block
     * handles placement of the full structure via [Block.setPlacedBy].
     *
     * Skipped blocks:
     * - Structure voids (schematic markers)
     * - Upper halves of double blocks (doors, tall flowers) — lower half calls setPlacedBy
     * - Head parts of beds — foot part calls setPlacedBy
     * - Piston heads — piston base handles extension
     *
     * Note: Belt middle segments are skipped — the controller segment handles creating the
     * whole belt via [BeltConnectorItem.createBelts].
     *
     * @see com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity.shouldIgnoreBlockState
     */
    fun shouldSkipBlock(state: BlockState): Boolean {
        if (state.block == Blocks.STRUCTURE_VOID) return true

        // Skip fluid blocks (water, lava, modded fluids)
        if (state.block is LiquidBlock) return true
        if (!state.fluidState.isEmpty) return true

        if (AllBlocks.BELT.has(state) && state.hasProperty(BeltBlock.PART)
            && state.getValue(BeltBlock.PART) == BeltPart.MIDDLE
        )
            return true

        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
        )
            return true

        if (state.hasProperty(BlockStateProperties.BED_PART)
            && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD
        )
            return true

        if (state.block is PistonHeadBlock) return true

        return false
    }

    /**
     * Calculates placement priority incorporating the two-pass system.
     * Higher values are processed first.
     *
     * Normal blocks get priorities in [513, 768], deferred blocks in [1, 256].
     * This guarantees all normal blocks are placed before any deferred block,
     * with lower Y-positions placed first within each pass.
     */
    fun calculatePriority(pos: BlockPos, state: BlockState): Int {
        val yPriority = 256 - pos.y
        val passOffset = if (shouldDeferBlock(state)) DEFERRED_BLOCK_OFFSET else NORMAL_BLOCK_OFFSET
        return passOffset + yPriority
    }

    /**
     * Determines the removal phase for deconstruction ordering.
     * Phase 0 (first): brittle/dependent blocks (tunnels, torches, rails, etc.)
     * Phase 1: support blocks (belts, gantry carriages, mechanical arms)
     * Phase 2 (last): normal blocks
     */
    fun removalPhase(state: BlockState): Int {
        if (BlockMovementChecks.isBrittle(state)
            || AllBlocks.ANDESITE_TUNNEL.has(state)
            || AllBlocks.BRASS_TUNNEL.has(state)
        ) return 0
        if (AllBlocks.BELT.has(state)
            || AllBlocks.GANTRY_CARRIAGE.has(state)
            || AllBlocks.MECHANICAL_ARM.has(state)
        ) return 1
        return 2
    }

    fun calculateRemovalPriority(pos: BlockPos, state: BlockState, maxY: Int): Int {
        val yPriority = pos.y - maxY + 256
        val passOffset = if (shouldDeferBlock(state)) REMOVAL_DEPENDENT_OFFSET else REMOVAL_NORMAL_OFFSET
        return passOffset + yPriority
    }
}
