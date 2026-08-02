package de.devin.cbbees.content.domain.task

import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.action.BeeActionSerializer
import de.devin.cbbees.content.domain.action.impl.DropOffItemsAction
import de.devin.cbbees.content.domain.action.impl.PickupItemsAction
import de.devin.cbbees.content.domain.action.impl.PlaceBeltAction
import de.devin.cbbees.content.domain.action.impl.PlaceBlockAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.job.BeeJob
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * Represents a single task for a single bee.
 *
 * @property action The action to perform.
 * @property job The job this task belongs to.
 * @property priority The priority of this task (higher values are processed first).
 */
data class BeeTask(
    val action: BeeAction,
    val job: BeeJob,
    val priority: Int = 0,
) {
    var status: TaskStatus = TaskStatus.PENDING
    var assignedBeeId: UUID? = null
    var assignedNetworkId: UUID? = null

    var requirement: (task: BeeTask) -> Boolean = { true }

    val targetPos: BlockPos get() = action.pos
    val jobId: UUID get() = job.jobId

    fun assignToBee(beeId: UUID) {
        status = TaskStatus.IN_PROGRESS
        assignedBeeId = beeId
    }

    /**
     * Mark this task as completed
     */
    fun complete() {
        status = TaskStatus.COMPLETED
        job.checkCompletion()
    }

    /**
     * Mark this task as failed
     */
    fun fail() {
        status = TaskStatus.FAILED
        assignedBeeId = null
    }

    /**
     * Release this task back to the pending pool
     */
    fun release() {
        status = TaskStatus.PENDING
        assignedBeeId = null
    }

    /**
     * Cancel this task permanently
     */
    fun cancel() {
        status = TaskStatus.CANCELLED
        assignedBeeId = null
        job.checkCompletion()
    }

    fun save(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        tag.put("Action", BeeActionSerializer.save(action, registries))
        tag.putInt("Priority", priority)
        tag.putString("Status", status.name)
        return tag
    }

    companion object {
        fun load(tag: CompoundTag, registries: HolderLookup.Provider, job: BeeJob): BeeTask? {
            val action = BeeActionSerializer.load(tag.getCompound("Action"), registries) ?: return null
            val priority = tag.getInt("Priority")
            val task = BeeTask(action, job, priority)
            task.status = TaskStatus.valueOf(tag.getString("Status"))
            return task
        }

        /**
         * Create a placement task
         */
        fun place(
            pos: BlockPos,
            state: BlockState,
            items: List<ItemStack>,
            priority: Int = 0,
            tag: CompoundTag? = null,
            job: BeeJob
        ): BeeTask {
            val action = PlaceBlockAction(pos, state, tag, items)
            return BeeTask(action, job, priority)
        }

        /**
         * Create a belt placement task using Create's BeltConnector flow.
         */
        fun belt(
            controllerPos: BlockPos,
            endPos: BlockPos,
            chain: List<BlockPos>,
            chainStates: List<BlockState>,
            casings: List<BeltBlockEntity.CasingType>,
            covers: List<Boolean>,
            items: List<ItemStack>,
            priority: Int = 0,
            job: BeeJob
        ): BeeTask {
            val action = PlaceBeltAction(controllerPos, endPos, chain, chainStates, casings, covers, items)
            return BeeTask(action, job, priority)
        }

        /**
         * Create a removal task
         */
        fun remove(pos: BlockPos, priority: Int = 0, job: BeeJob): BeeTask {
            return BeeTask(RemoveBlockAction(pos), job, priority)
        }

        fun dropOff(fallbackPos: BlockPos, priority: Int = 0, job: BeeJob): BeeTask {
            return BeeTask(DropOffItemsAction(fallbackPos), job, priority)
        }

        fun pickup(pos: BlockPos, priority: Int = 0, job: BeeJob): BeeTask {
            return BeeTask(PickupItemsAction(pos), job, priority)
        }
    }
}
