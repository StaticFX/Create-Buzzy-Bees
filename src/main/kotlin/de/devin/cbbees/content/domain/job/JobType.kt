package de.devin.cbbees.content.domain.job

import com.simibubi.create.AllBlocks
import de.devin.cbbees.content.domain.action.impl.PlaceBeltAction
import de.devin.cbbees.content.domain.action.impl.PlaceBlockAction
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

/**
 * Sealed interface representing the type of a bee job.
 * Each implementation owns its type-specific behavior: display name, client batch sync strategy, etc.
 * Adding a new planner type requires adding a new [JobType] implementation here.
 */
sealed interface JobType {
    val id: String
    val translationKey: String

    /**
     * Package server-side batches into client DTOs for network sync.
     * Construction sends a single summary batch; others send per-batch details with ghost blocks.
     */
    fun collectClientBatches(job: BeeJob): List<ClientBatchInfo>

    companion object {
        val entries: List<JobType> = listOf(Construction, Deconstruction, Pickup)

        fun fromOrdinal(ordinal: Int): JobType = entries[ordinal]

        fun ordinalOf(type: JobType): Int = entries.indexOf(type)
    }

    data object Construction : JobType {
        override val id = "construction"
        override val translationKey = "cbbees.job_type.construction"

        override fun collectClientBatches(job: BeeJob): List<ClientBatchInfo> {
            return listOf(
                ClientBatchInfo(
                    status = "SUMMARY",
                    target = job.centerPos,
                    required = emptyList(),
                    assignedBeeIds = emptyList(),
                    ghostBlocks = emptyMap()
                )
            )
        }
    }

    data object Deconstruction : JobType {
        override val id = "deconstruction"
        override val translationKey = "cbbees.job_type.deconstruction"

        override fun collectClientBatches(job: BeeJob) = perBatchSync(job)
    }

    data object Pickup : JobType {
        override val id = "pickup"
        override val translationKey = "cbbees.job_type.pickup"

        override fun collectClientBatches(job: BeeJob) = perBatchSync(job)
    }
}

private fun perBatchSync(job: BeeJob): List<ClientBatchInfo> {
    return job.batches.map { b ->
        ClientBatchInfo(
            status = b.status.name,
            target = b.targetPosition,
            required = emptyList(),
            assignedBeeIds = emptyList(),
            ghostBlocks = collectGhostBlocks(b)
        )
    }
}

/**
 * Collect all ghost block positions and states from a batch.
 * Handles both regular [PlaceBlockAction] and [PlaceBeltAction] (belt chain + shafts).
 */
fun collectGhostBlocks(batch: TaskBatch): Map<BlockPos, BlockState> {
    if (batch.status == TaskStatus.COMPLETED) return emptyMap()

    val ghosts = mutableMapOf<BlockPos, BlockState>()
    for (task in batch.tasks) {
        when (val action = task.action) {
            is PlaceBlockAction -> ghosts[action.pos] = action.blockState
            is PlaceBeltAction -> {
                action.chain.forEachIndexed { index, pos ->
                    if (!ghosts.containsKey(pos)) {
                        val state = action.chainStates.getOrNull(index)
                            ?: AllBlocks.BELT.defaultState
                        ghosts[pos] = state
                    }
                }
            }
        }
    }
    return ghosts
}
