package de.devin.cbbees.content.logistics.dock

import com.mojang.serialization.MapCodec
import de.devin.cbbees.content.logistics.ports.PortBlock
import de.devin.cbbees.registry.AllBlockEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.capabilities.Capabilities

/**
 * Port that connects bees to an attached fluid tank.
 */
class DockPortBlock(properties: Properties) : PortBlock<DockPortBlockEntity>(properties) {

    companion object {
        val CODEC: MapCodec<DockPortBlock> = simpleCodec(::DockPortBlock)
    }

    override fun codec(): MapCodec<DockPortBlock> = CODEC

    override fun hasStorage(level: Level, pos: BlockPos, side: Direction): Boolean =
        level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side) != null

    override fun getBlockEntityClass(): Class<DockPortBlockEntity> = DockPortBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out DockPortBlockEntity> = AllBlockEntityTypes.DOCK_PORT.get()
}
