package de.devin.cbbees.content.domain.action.impl

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.history.BlockSnapshot
import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BucketPickup
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.wrappers.BucketPickupHandlerWrapper
import kotlin.math.ceil

class RemoveBlockAction(override val pos: BlockPos) : BeeAction {

    override fun getWorkTicks(level: Level, context: BeeContext): Int {
        if (CBBeesConfig.beeInstantBreaking.get()) return INSTANT_WORK_TICKS
        val ticks = diamondToolTicks(level, pos, level.getBlockState(pos)) * context.breakSpeedMultiplier.coerceAtLeast(0.0)
        return ceil(ticks).toInt().coerceAtLeast(INSTANT_WORK_TICKS)
    }

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

        pickUpFluid(level, worker, context)
        if (level.getBlockState(pos).isAir) return true

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

    private fun pickUpFluid(level: ServerLevel, worker: BeeWorker, context: BeeContext) {
        val state = level.getBlockState(pos)
        val block = state.block as? BucketPickup ?: return
        if (!state.fluidState.isSource) return

        val fluid = BucketPickupHandlerWrapper(null, block, level, pos)
            .drain(ServerBeeData.FLUID_CAPACITY, FluidAction.EXECUTE)
        val keepFluid = !context.dropItemsEnabled && CBBeesConfig.beePickupItems.get()
        if (keepFluid && worker.carriedFluid.isEmpty && worker.network()?.findFluidDropOff(fluid) != null) {
            worker.carriedFluid = fluid
        }
    }

    override fun shouldReturnAfter(context: BeeContext): Boolean =
        !context.dropItemsEnabled && CBBeesConfig.beePickupItems.get()

    override fun inversion(pos: BlockPos, before: BlockSnapshot) = before.restoreAt(pos)

    override fun changedPositions(level: Level): List<BlockPos> =
        PlaceBeltAction.chainAt(level, pos).ifEmpty { changedPositions }

    override fun inversion(level: Level, pos: BlockPos, before: BlockSnapshot): BeeAction =
        PlaceBeltAction.capture(level, pos) ?: before.restoreAt(pos)

    override fun getDescription() = "Removing block at (${pos.x}, ${pos.y}, ${pos.z})"

    fun save(): CompoundTag {
        val tag = CompoundTag()
        tag.putInt("X", pos.x)
        tag.putInt("Y", pos.y)
        tag.putInt("Z", pos.z)
        return tag
    }

    companion object {
        private const val INSTANT_WORK_TICKS = 5

        private val diamondTools by lazy {
            listOf(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.SHEARS).map(::ItemStack)
        }

        fun diamondToolTicks(level: Level, pos: BlockPos, state: BlockState): Double {
            val hardness = state.getDestroySpeed(level, pos)
            if (hardness <= 0f) return 0.0
            val tool = diamondTools.maxBy { it.getDestroySpeed(state) }
            val harvestable = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state)
            val progressPerTick = tool.getDestroySpeed(state) / hardness / if (harvestable) 30f else 100f
            return if (progressPerTick >= 1f) 0.0 else ceil(1.0 / progressPerTick)
        }

        fun load(tag: CompoundTag): RemoveBlockAction {
            return RemoveBlockAction(BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")))
        }
    }
}
