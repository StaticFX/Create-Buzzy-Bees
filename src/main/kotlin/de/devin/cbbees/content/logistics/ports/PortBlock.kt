package de.devin.cbbees.content.logistics.ports

import com.simibubi.create.content.equipment.wrench.IWrenchable
import com.simibubi.create.foundation.block.IBE
import com.simibubi.create.foundation.block.ProperWaterloggedBlock
import com.simibubi.create.foundation.block.ProperWaterloggedBlock.WATERLOGGED
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import de.devin.cbbees.shapes.AllShapes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A port attached to a storage block, switchable between extracting and inserting with a wrench.
 */
abstract class PortBlock<T : SmartBlockEntity>(properties: Properties) :
    FaceAttachedHorizontalDirectionalBlock(properties),
    ProperWaterloggedBlock,
    IBE<T>,
    IWrenchable {

    companion object {
        val PORT_STATE: EnumProperty<PortState> = EnumProperty.create("port_state", PortState::class.java)
        val PORT_TYPE: EnumProperty<PortType> = EnumProperty.create("port_type", PortType::class.java)

        /**
         * Determines which direction the port is "pointing" into the attached storage.
         * If placed on a wall, it's the opposite of its horizontal facing.
         * If placed on a floor, it's DOWN.
         * If placed on a ceiling, it's UP.
         */
        fun getConnectedDirection(state: BlockState): Direction {
            return when (state.getValue(FACE)) {
                AttachFace.FLOOR -> Direction.UP
                AttachFace.CEILING -> Direction.DOWN
                else -> state.getValue(FACING)
            }
        }

        /** X rotation of the port models for the face the port is attached to. */
        fun modelRotationX(face: AttachFace): Int = when (face) {
            AttachFace.FLOOR -> 0
            AttachFace.CEILING -> 180
            AttachFace.WALL -> 270
        }
    }

    /** Whether the block at [pos] offers the storage this port works with, seen from [side]. */
    protected abstract fun hasStorage(level: Level, pos: BlockPos, side: Direction): Boolean

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(WATERLOGGED, false)
                .setValue(PORT_STATE, PortState.INVALID)
                .setValue(PORT_TYPE, PortType.EXTRACT)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, FACE, WATERLOGGED, PORT_STATE, PORT_TYPE)
    }

    private fun isAttachedToStorage(level: Level, pos: BlockPos, state: BlockState): Boolean {
        val direction = getConnectedDirection(state)
        return hasStorage(level, pos.relative(direction.opposite), direction)
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val direction = getConnectedDirection(state).opposite
        val neighborPos = pos.relative(direction)
        return level.getBlockState(neighborPos).isFaceSturdy(level, neighborPos, direction.opposite)
                || level.getBlockEntity(neighborPos) != null
    }

    public override fun getFluidState(pState: BlockState): FluidState = fluidState(pState)

    public override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape = AllShapes.LOGISTICS_PORT.get(getConnectedDirection(pState))

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        neighborBlock: Block,
        neighborPos: BlockPos,
        movedByPiston: Boolean
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston)
        if (level.isClientSide) return
        if (neighborPos != pos.relative(getConnectedDirection(state).opposite)) return

        val targetState = if (isAttachedToStorage(level, pos, state)) PortState.VALID else PortState.INVALID
        if (state.getValue(PORT_STATE) != targetState) {
            level.setBlock(pos, state.setValue(PORT_STATE, targetState), 3)
        }
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        var state = super.getStateForPlacement(context) ?: return null
        if (state.getValue(FACE) == AttachFace.CEILING) {
            state = state.setValue(FACING, state.getValue(FACING).opposite)
        }
        val portState = if (isAttachedToStorage(context.level, context.clickedPos, state)) PortState.VALID else PortState.INVALID
        return withWater(state.setValue(PORT_STATE, portState), context)
    }

    override fun onWrenched(state: BlockState?, context: UseOnContext?): InteractionResult? {
        if (state == null || context == null) return InteractionResult.PASS
        val newType = if (state.getValue(PORT_TYPE) == PortType.EXTRACT) PortType.INSERT else PortType.EXTRACT
        context.level.setBlock(context.clickedPos, state.setValue(PORT_TYPE, newType), 3)
        return InteractionResult.SUCCESS
    }
}
