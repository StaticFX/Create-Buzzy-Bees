package de.devin.cbbees.content.logistics.storage

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level

/**
 * Resolves the [PortStorage] behind a block. Registered providers are asked in order,
 * plain item handlers are the fallback.
 */
object PortStorages {

    private val providers = mutableListOf<PortStorageProvider>(CreativeCrateStorage)

    fun register(provider: PortStorageProvider) {
        providers += provider
    }

    fun find(level: Level, pos: BlockPos, side: Direction): PortStorage? =
        providers.firstNotNullOfOrNull { it.find(level, pos, side) } ?: ItemHandlerStorage.find(level, pos, side)
}
