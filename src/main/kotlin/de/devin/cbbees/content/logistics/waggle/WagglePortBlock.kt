package de.devin.cbbees.content.logistics.waggle

import com.mojang.serialization.MapCodec
import com.simibubi.create.content.equipment.wrench.IWrenchable
import com.simibubi.create.foundation.block.IBE
import de.devin.cbbees.content.logistics.waggle.client.WagglePortScreen
import de.devin.cbbees.registry.AllBlockEntityTypes
import net.createmod.catnip.gui.ScreenOpener
import net.createmod.catnip.platform.CatnipServices
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult

class WagglePortBlock(properties: Properties) : HorizontalDirectionalBlock(properties), IBE<WagglePortBlockEntity>, IWrenchable {

    companion object {
        val CODEC: MapCodec<WagglePortBlock> = simpleCodec(::WagglePortBlock)
    }

    override fun codec(): MapCodec<WagglePortBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level.isClientSide) {
            val address = getBlockEntity(level, pos)?.address ?: return InteractionResult.PASS
            CatnipServices.PLATFORM.executeOnClientOnly { Runnable { ScreenOpener.open(WagglePortScreen(pos, address)) } }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun getBlockEntityClass(): Class<WagglePortBlockEntity> = WagglePortBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out WagglePortBlockEntity> = AllBlockEntityTypes.WAGGLE_PORT.get()
}
