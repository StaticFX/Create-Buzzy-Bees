package de.devin.cbbees.content.apiary

import com.simibubi.create.content.kinetics.base.KineticBlock
import com.simibubi.create.foundation.block.IBE
import com.simibubi.create.foundation.fluid.FluidHelper
import de.devin.cbbees.registry.AllBlockEntityTypes
import net.createmod.catnip.data.Iterate
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.neoforge.items.ItemHandlerHelper

class MechanicalApiaryBlock(properties: Properties) : KineticBlock(properties), IBE<MechanicalApiaryBlockEntity> {

    init {
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder.add(FACING))
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun hasShaftTowards(world: LevelReader, pos: BlockPos, state: BlockState, face: Direction) =
        face == Direction.DOWN

    override fun getRotationAxis(state: BlockState): Axis = Axis.Y

    override fun useItemOn(
        stack: ItemStack, state: BlockState, level: Level, pos: BlockPos,
        player: Player, hand: InteractionHand, hitResult: BlockHitResult
    ): ItemInteractionResult {
        val be = getBlockEntity(level, pos) ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

        if (!stack.isEmpty) {
            if (FluidHelper.tryFillItemFromBE(level, player, hand, stack, be)) return ItemInteractionResult.SUCCESS
            if (level.isClientSide) return ItemInteractionResult.SUCCESS
            val remainder = ItemHandlerHelper.insertItemStacked(be.itemCapability, stack.copy(), false)
            if (remainder.count == stack.count) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            player.setItemInHand(hand, remainder)
            return ItemInteractionResult.SUCCESS
        }

        if (level.isClientSide) return ItemInteractionResult.SUCCESS
        for (slot in 0 until be.inputInv.slots) {
            player.inventory.placeItemBackInInventory(be.inputInv.getStackInSlot(slot))
            be.inputInv.setStackInSlot(slot, ItemStack.EMPTY)
        }
        be.sendData()
        return ItemInteractionResult.SUCCESS
    }

    override fun updateEntityAfterFallOn(level: BlockGetter, entity: Entity) {
        super.updateEntityAfterFallOn(level, entity)
        if (entity.level().isClientSide || entity !is ItemEntity || !entity.isAlive) return

        val be = Iterate.hereAndBelow(entity.blockPosition()).firstNotNullOfOrNull { getBlockEntity(level, it) } ?: return
        val remainder = ItemHandlerHelper.insertItemStacked(be.itemCapability, entity.item, false)
        if (remainder.isEmpty) entity.discard() else entity.item = remainder
    }

    override fun isPathfindable(state: BlockState, pathComputationType: PathComputationType) = false

    override fun getBlockEntityClass() = MechanicalApiaryBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out MechanicalApiaryBlockEntity> =
        AllBlockEntityTypes.MECHANICAL_APIARY.get()

    companion object {
        val FACING = HorizontalDirectionalBlock.FACING
    }
}
