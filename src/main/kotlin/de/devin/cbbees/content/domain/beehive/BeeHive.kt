package de.devin.cbbees.content.domain.beehive

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.domain.network.INetworkComponent
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.world.entity.Entity
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * Interface for any entity that can contribute bees to work on jobs.
 *
 * A BeeSource represents a location from which bees can be deployed to work on tasks.
 * Multiple BeeSource instances can contribute bees to the same job, allowing for
 * cooperative work between beehives and backpacks.
 */
interface BeeHive : INetworkComponent {
    /**
     * Unique identifier for this bee source.
     */
    override val id: UUID

    /**
     * The world/level this source exists in.
     */
    override val world: Level

    /**
     * The position of this source in the world.
     */
    override val pos: BlockPos

    /**
     * Gets the number of bees currently available in this source.
     * This is the count of bees that can be deployed for work.
     */
    fun getAvailableBeeCount(): Int

    /**
     * Gets the bee context (upgrades, stats) for this source.
     */
    fun getBeeContext(): BeeContext

    /**
     * Attempts to consume a bee from this source for deployment.
     * @return the consumed bee ItemStack, or ItemStack.EMPTY if no bees available.
     */
    fun consumeBee(): ItemStack

    /**
     * Attempts to return a bee to this source.
     * @param item the bee item to return.
     * @return true if the bee was successfully returned, false if the source is full.
     */
    fun returnBee(item: ItemStack): Boolean

    /**
     * Gets the maximum range at which bees from this source can work.
     * Jobs outside this range cannot be worked by bees from this source.
     */
    fun getWorkRange(): Double {
        return getBeeContext().workRange
    }

    /**
     * Checks if a position is within the work range of this source.
     */
    fun isInRange(pos: BlockPos): Boolean {
        val dx = Math.abs(pos.x - this.pos.x)
        val dz = Math.abs(pos.z - this.pos.z)
        val range = getWorkRange().toInt()
        return dx <= range && dz <= range
    }

    /**
     * Gets the maximum number of bees this source can contribute to a single job.
     * This is determined by upgrades and the source's capacity.
     */
    fun getMaxContributionBees(): Int {
        return getBeeContext().maxContributedBees
    }

    /**
     * Attempts to accept a task batch for processing by this BeeHive.
     */
    fun acceptBatch(batch: TaskBatch): Boolean

    /**
     * Returns a walk target for this beehive.
     */
    fun walkTarget(): WalkTarget

    /**
     * Gets the number of bees currently active from this source.
     */
    fun getActiveBeeCount(): Int

    /**
     * Marks the given task as completed by the given bee.
     * @return optionally next task batch to be processed, or null if all tasks are completed.
     */
    fun notifyTaskCompleted(task: BeeTask, beeId: java.util.UUID): TaskBatch?

    /**
     * Called when a bee arrives at the hive to recharge its spring.
     * Handles any fuel consumption and returns the recharge duration in ticks.
     *
     * @param ctx the bee context providing spring efficiency and fuel multipliers
     * @return recharge duration in ticks
     */
    fun rechargeSpring(ctx: BeeContext): Int {
        val baseTicks = CBBeesConfig.springRechargeTicks.get()
        return (baseTicks / ctx.springEfficiency).toInt().coerceAtLeast(20)
    }

    /**
     * Called when a bee returns to the hive, to charge fuel proportional to the
     * spring deficit. No-op for block-based hives (kinetic power is free).
     *
     * @param springDeficit fraction of spring that needs refilling (0.0–1.0)
     * @param ctx the bee context providing fuel multipliers
     */
    fun chargeReturnFuel(springDeficit: Float, ctx: BeeContext) {}

    /**
     * Called when a bee from this source is spawned.
     */
    fun onBeeSpawned(bee: Entity) {}

    /**
     * Called when a bee from this source is removed/returned.
     */
    fun onBeeRemoved(bee: Entity) {}

    override fun isAnchor(): Boolean = true

    override fun getNetworkingRange(): Double = Math.max(getWorkRange(), 8.0)

    override fun isInWorkRange(pos: BlockPos): Boolean = isInRange(pos)
}
