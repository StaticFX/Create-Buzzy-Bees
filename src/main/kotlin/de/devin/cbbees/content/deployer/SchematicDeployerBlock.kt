package de.devin.cbbees.content.deployer

import com.mojang.serialization.MapCodec
import com.simibubi.create.content.equipment.wrench.IWrenchable
import com.simibubi.create.foundation.block.IBE
import de.devin.cbbees.registry.AllBlockEntityTypes
import de.devin.cbbees.registry.AllDataComponents
import de.devin.cbbees.content.deployer.client.SchematicDeployerScreen
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.Containers
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class SchematicDeployerBlock(properties: Properties) :
    HorizontalDirectionalBlock(properties),
    IBE<SchematicDeployerBlockEntity>,
    IWrenchable {

    companion object {
        val CODEC: MapCodec<SchematicDeployerBlock> = simpleCodec(::SchematicDeployerBlock)
        val POWERED: BooleanProperty = BlockStateProperties.POWERED
        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 18.0, 16.0)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, POWERED)
    }

    override fun codec(): MapCodec<SchematicDeployerBlock> = CODEC

    override fun getBlockEntityClass(): Class<SchematicDeployerBlockEntity> =
        SchematicDeployerBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out SchematicDeployerBlockEntity> =
        AllBlockEntityTypes.SCHEMATIC_DEPLOYER.get()

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        return defaultBlockState()
            .setValue(FACING, context.horizontalDirection.opposite)
            .setValue(POWERED, context.level.hasNeighborSignal(context.clickedPos))
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        neighborBlock: Block,
        neighborPos: BlockPos,
        movedByPiston: Boolean
    ) {
        if (level.isClientSide) return

        val wasPowered = state.getValue(POWERED)
        val isPowered = level.hasNeighborSignal(pos)

        if (wasPowered != isPowered) {
            level.setBlock(pos, state.setValue(POWERED, isPowered), 3)

            // Rising edge: deploy
            if (!wasPowered && isPowered) {
                withBlockEntityDo(level, pos) { be -> be.deploy() }
            }
        }
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): ItemInteractionResult {
        val be = getBlockEntity(level, pos) ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

        // Insert programmed schematic
        if (!stack.isEmpty && stack.has(AllDataComponents.SCHEMATIC_PROGRAM) && be.heldItem.isEmpty) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS
            be.heldItem = stack.copyWithCount(1)
            be.resetSettings()
            stack.shrink(1)
            be.setChanged()
            be.sendData()
            return ItemInteractionResult.SUCCESS
        }

        // Fall through to useWithoutItem for GUI opening / extraction
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        val be = getBlockEntity(level, pos) ?: return InteractionResult.PASS

        // Shift+right-click to extract (server only)
        if (player.isShiftKeyDown && !be.heldItem.isEmpty) {
            if (level.isClientSide) return InteractionResult.SUCCESS
            if (!player.inventory.add(be.heldItem.copy())) {
                player.drop(be.heldItem.copy(), false)
            }
            be.heldItem = ItemStack.EMPTY
            be.resetSettings()
            be.setChanged()
            be.sendData()
            return InteractionResult.SUCCESS
        }

        // Right-click without item: open GUI (when deployer has a schematic)
        if (!player.isShiftKeyDown && !be.heldItem.isEmpty) {
            if (level.isClientSide) {
                openScreen(be)
            }
            return InteractionResult.SUCCESS
        }

        return InteractionResult.PASS
    }

    private fun openScreen(be: SchematicDeployerBlockEntity) {
        Minecraft.getInstance().setScreen(SchematicDeployerScreen(be))
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean = true

    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int {
        return getBlockEntity(level, pos)?.getComparatorOutput() ?: 0
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block)) {
            val be = getBlockEntity(level, pos)
            if (be != null && !be.heldItem.isEmpty) {
                Containers.dropItemStack(level, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), be.heldItem)
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }
}
