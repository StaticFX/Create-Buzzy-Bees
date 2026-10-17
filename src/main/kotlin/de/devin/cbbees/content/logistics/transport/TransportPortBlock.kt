package de.devin.cbbees.content.logistics.transport

import com.mojang.serialization.MapCodec
import com.simibubi.create.content.equipment.wrench.IWrenchable
import com.simibubi.create.foundation.block.IBE
import com.simibubi.create.foundation.block.ProperWaterloggedBlock
import com.simibubi.create.foundation.block.ProperWaterloggedBlock.WATERLOGGED
import de.devin.cbbees.content.logistics.ports.PortBlock
import de.devin.cbbees.content.logistics.ports.PortState
import de.devin.cbbees.registry.AllBlockEntityTypes
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
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import net.neoforged.neoforge.capabilities.Capabilities

class TransportPortBlock(properties: Properties) :
    FaceAttachedHorizontalDirectionalBlock(properties),
    ProperWaterloggedBlock,
    IBE<TransportPortBlockEntity>,
    IWrenchable {

    companion object {
        val CODEC: MapCodec<TransportPortBlock> = simpleCodec(::TransportPortBlock)
        val PORT_STATE = PortBlock.PORT_STATE
        val TRANSPORT_MODE = EnumProperty.create("transport_mode", TransportMode::class.java)

        fun getConnectedDirection(state: BlockState): Direction {
            val face = state.getValue(FACE)
            return when (face) {
                AttachFace.FLOOR -> Direction.UP
                AttachFace.CEILING -> Direction.DOWN
                else -> state.getValue(FACING)
            }
        }
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(WATERLOGGED, false)
                .setValue(PORT_STATE, PortState.INVALID)
                .setValue(TRANSPORT_MODE, TransportMode.PROVIDER)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, FACE, WATERLOGGED, PORT_STATE, TRANSPORT_MODE)
    }

    override fun codec(): MapCodec<TransportPortBlock> = CODEC

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val direction = getConnectedDirection(state).opposite
        val neighborPos = pos.relative(direction)
        val neighborState = level.getBlockState(neighborPos)

        return neighborState.isFaceSturdy(level, neighborPos, direction.opposite)
                || level.getBlockEntity(neighborPos) != null
    }

    public override fun getFluidState(pState: BlockState): FluidState = fluidState(pState)

    public override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        return de.devin.cbbees.shapes.AllShapes.LOGISTICS_PORT.get(getConnectedDirection(pState))
    }

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

        val connectedPos = pos.relative(getConnectedDirection(state).opposite)

        if (neighborPos == connectedPos) {
            val hasInventory =
                level.getCapability(Capabilities.ItemHandler.BLOCK, connectedPos, getConnectedDirection(state)) != null
            val targetState = if (hasInventory) PortState.VALID else PortState.INVALID

            if (state.getValue(PORT_STATE) != targetState) {
                level.setBlock(pos, state.setValue(PORT_STATE, targetState), 3)
            }
        }
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        var state = super.getStateForPlacement(context) ?: return null
        if (state.getValue(FACE) == AttachFace.CEILING) {
            state = state.setValue(FACING, state.getValue(FACING).opposite)
        }

        val level = context.level
        val pos = context.clickedPos
        val connectedPos = pos.relative(getConnectedDirection(state).opposite)

        val hasInventory =
            level.getCapability(Capabilities.ItemHandler.BLOCK, connectedPos, getConnectedDirection(state)) != null
        state = state.setValue(PORT_STATE, if (hasInventory) PortState.VALID else PortState.INVALID)

        return withWater(state, context)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            super.onRemove(state, level, pos, newState, isMoving)
        }
    }

    override fun getBlockEntityClass(): Class<TransportPortBlockEntity> = TransportPortBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out TransportPortBlockEntity> =
        AllBlockEntityTypes.TRANSPORT_PORT.get()

    override fun onWrenched(state: BlockState?, context: UseOnContext?): InteractionResult? {
        if (state == null || context == null) return InteractionResult.PASS

        val level = context.level
        val pos = context.clickedPos

        val currentMode = state.getValue(TRANSPORT_MODE)
        val newMode = if (currentMode == TransportMode.PROVIDER) TransportMode.REQUESTER else TransportMode.PROVIDER

        level.setBlock(pos, state.setValue(TRANSPORT_MODE, newMode), 3)

        return InteractionResult.SUCCESS
    }
}
