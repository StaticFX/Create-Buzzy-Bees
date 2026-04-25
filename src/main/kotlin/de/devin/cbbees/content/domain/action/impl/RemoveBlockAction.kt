package de.devin.cbbees.content.domain.action.impl

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block

class RemoveBlockAction(override val pos: BlockPos) : BeeAction {

    override fun getWorkTicks(context: BeeContext): Int = 5

    override fun onTick(worker: BeeWorker, tick: Int) {
        val level = worker.level()
        if (level is ServerLevel) {
            level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                worker.getWorkerX(), worker.getWorkerY(), worker.getWorkerZ(),
                2, 0.2, 0.2, 0.2, 0.05
            )
        }
    }

    override fun execute(level: Level, worker: BeeWorker, context: BeeContext): Boolean {
        if (level !is ServerLevel) return false
        if (level.getBlockEntity(pos) is BeeHive) return true

        if (context.dropItemsEnabled) {
            level.destroyBlock(pos, true)
            return true
        }

        if (CBBeesConfig.beePickupItems.get()) {
            val state = level.getBlockState(pos)
            val blockEntity = level.getBlockEntity(pos)
            val drops = if (context.silkTouchEnabled) {
                listOf(ItemStack(state.block.asItem()))
            } else {
                Block.getDrops(state, level, pos, blockEntity)
            }

            val extraDrops = mutableListOf<ItemStack>()
            if (blockEntity is net.minecraft.world.Container) {
                (0 until blockEntity.containerSize)
                    .map { blockEntity.getItem(it) }
                    .filter { !it.isEmpty }
                    .forEach { extraDrops.add(it.copy()) }
                blockEntity.clearContent()
            }
            if (blockEntity is CopycatBlockEntity) {
                blockEntity.consumedItem.takeIf { !it.isEmpty }?.let { extraDrops.add(it.copy()) }
                blockEntity.clearContent()
            }

            level.destroyBlock(pos, false)
            val hasPort = worker.network()?.findDropOff(ItemStack.EMPTY, worker.hiveId) != null
            (drops + extraDrops).forEach { drop ->
                if (hasPort) {
                    // Port available — pick up into inventory for later deposit
                    val remainder = worker.addToInventory(drop)
                    if (!remainder.isEmpty) {
                        // Inventory full — drop overflow on ground
                        level.addFreshEntity(ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, remainder))
                    }
                } else {
                    // No port — drop everything on ground immediately
                    level.addFreshEntity(ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, drop))
                }
            }
        } else {
            level.destroyBlock(pos, true)
        }
        return true
    }

    override fun shouldReturnAfter(context: BeeContext): Boolean =
        !context.dropItemsEnabled && CBBeesConfig.beePickupItems.get()

    override fun getDescription() = "Removing block at (${pos.x}, ${pos.y}, ${pos.z})"
}
