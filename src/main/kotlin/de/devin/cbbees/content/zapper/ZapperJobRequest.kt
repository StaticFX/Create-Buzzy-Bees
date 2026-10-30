package de.devin.cbbees.content.zapper

import com.simibubi.create.content.schematics.requirement.ItemRequirement
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.BeeJobRequest
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.schematics.BlockPlacementClassifier
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState

class ZapperJobRequest(
    private val origin: BlockPos,
    private val edits: Map<BlockPos, BlockState>,
    private val paint: BlockState,
    private val paintData: CompoundTag?
) : BeeJobRequest() {

    private lateinit var changes: Map<BlockPos, BlockState>

    override fun jobType() = JobType.Shaping
    override fun completionKey() = "cbbees.shaping.started"

    override fun createUniquenessKey(player: ServerPlayer) =
        SchematicJobKey(player.uuid, "zapper", origin.x, origin.y, origin.z)

    override fun validate(player: ServerPlayer): Boolean {
        val level = player.level()
        changes = edits.filter { (pos, target) -> level.canShape(pos, target) }
        if (changes.isNotEmpty()) return true

        player.displayClientMessage(Component.translatable("cbbees.zapper.nothing_to_do"), true)
        return false
    }

    override fun generateTasks(
        player: ServerPlayer,
        job: BeeJob,
        server: MinecraftServer,
        onComplete: (List<TaskBatch>, BlockPos) -> Unit
    ) {
        val level = player.level()
        val maxY = changes.keys.maxOf { it.y }
        val removals = mutableListOf<TaskBatch>()
        val placements = mutableListOf<TaskBatch>()

        changes.forEach { (pos, target) ->
            val current = level.getBlockState(pos)
            if (current.needsRemoval()) removals += SchematicCreateBridge.removalBatch(pos, current, maxY, job)
            if (!target.isAir) placements += placementBatch(pos, target, job)
        }

        onComplete(removals + placements, origin)
    }

    private fun placementBatch(pos: BlockPos, state: BlockState, job: BeeJob): TaskBatch {
        val items = ItemRequirement.of(state, null).requiredItems.map { it.stack.copy() }
        val task = BeeTask.place(
            pos = pos,
            state = state,
            items = items,
            priority = BlockPlacementClassifier.calculatePriority(pos, state),
            tag = paintData?.takeIf { state == paint }?.copy(),
            job = job
        )
        return TaskBatch(listOf(task), job, pos, PLACEMENT_PHASE)
    }

    private fun Level.canShape(pos: BlockPos, target: BlockState): Boolean {
        if (isOutsideBuildHeight(pos)) return false
        if (getBlockEntity(pos) is BeeHive) return false

        val current = getBlockState(pos)
        if (current == target) return false
        if (current.getDestroySpeed(this, pos) < 0) return false
        if (target.isAir) return current.needsRemoval()
        return !ItemRequirement.of(target, null).isInvalid
    }

    private fun BlockState.needsRemoval() = !isAir && block !is LiquidBlock

    companion object {
        private const val PLACEMENT_PHASE = 3
    }
}
