package de.devin.cbbees.content.logistics.ports

import com.mojang.serialization.MapCodec
import de.devin.cbbees.content.logistics.storage.PortStorages
import de.devin.cbbees.registry.AllBlockEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class LogisticPortBlock(properties: Properties) : PortBlock<LogisticPortBlockEntity>(properties) {

    companion object {
        val CODEC: MapCodec<LogisticPortBlock> = simpleCodec(::LogisticPortBlock)
    }

    override fun codec(): MapCodec<LogisticPortBlock> = CODEC

    override fun hasStorage(level: Level, pos: BlockPos, side: Direction): Boolean =
        PortStorages.find(level, pos, side) != null

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            super.onRemove(state, level, pos, newState, isMoving)
        }
    }

    override fun getBlockEntityClass(): Class<LogisticPortBlockEntity> = LogisticPortBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out LogisticPortBlockEntity> = AllBlockEntityTypes.LOGISTICS_PORT.get()
}
