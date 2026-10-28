package de.devin.cbbees.content.domain.built

import de.devin.cbbees.content.domain.action.impl.PlaceBeltAction
import de.devin.cbbees.content.domain.action.impl.PlaceBlockAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

object BuiltSchematics {

    const val VISIBLE_RANGE = 128.0

    fun data(server: MinecraftServer) = BuiltSchematicsSavedData.get(server)

    fun record(job: BeeJob) {
        if (job.jobType != JobType.Construction) return
        val placement = job.schematicPlacement ?: return
        val ownerId = job.ownerId ?: return
        val server = job.level.server ?: return
        val blocks = job.tasks
            .filter { it.status == TaskStatus.COMPLETED && (it.action is PlaceBlockAction || it.action is PlaceBeltAction) }
            .flatMap { it.action.changedPositions }
            .associateWith { job.level.getBlockState(it) }
            .filterValues { !it.isAir }
        if (blocks.isEmpty()) return
        val name = placement.file.substringAfterLast('/').removeSuffix(".nbt")
        data(server).add(BuiltSchematic(job.jobId, ownerId, job.dimensionKey, name, blocks))
    }

    fun nearby(player: ServerPlayer): List<BuiltSchematic> {
        val data = data(player.server)
        val level = player.level()
        val nearby = data.all.filter { it.dimension == level.dimension() && it.isNear(player.position()) }
        val gone = nearby.filterNot { it.hasRemainingBlocks(level) }
        gone.forEach { data.remove(it.id) }
        return nearby - gone.toSet()
    }

    fun BuiltSchematic.isNear(position: Vec3): Boolean = bounds.center.closerToCenterThan(position, VISIBLE_RANGE)
}
