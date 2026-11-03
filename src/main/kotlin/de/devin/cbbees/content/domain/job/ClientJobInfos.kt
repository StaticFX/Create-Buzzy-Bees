package de.devin.cbbees.content.domain.job

import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.task.TaskStatus
import de.devin.cbbees.network.HiveJobsSyncPacket
import de.devin.cbbees.util.ItemStackKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.levelgen.structure.BoundingBox

/**
 * Builds the client view of this job. It is the same for every viewer, so callers that
 * sync many players build it once per job and share it.
 *
 * @param stall why the job is stuck, or null
 */
fun BeeJob.toClientInfo(server: MinecraftServer, stall: Stall?) = ClientJobInfo(
    jobId = jobId,
    name = jobId.toString().substring(0, 6).uppercase(),
    status = status.name,
    completed = tasks.count { it.status == TaskStatus.COMPLETED },
    total = tasks.size,
    stall = stall,
    beeIds = workingBatches.mapNotNull { it.assignedBeeId }.distinct(),
    materials = remainingMaterials(),
    bounds = BoundingBox.encapsulatingPositions(batches.map { it.targetPosition }).orElse(null),
    schematicPlacement = schematicPlacement,
    jobType = jobType,
    ownerId = ownerId,
    ownerName = ownerId?.let { HiveJobsSyncPacket.playerName(server, it) },
)

private fun BeeJob.remainingMaterials(): List<ItemStack> = tasks.asSequence()
    .filter { it.status != TaskStatus.COMPLETED }
    .mapNotNull { it.action as? ItemConsumingAction }
    .flatMap { it.requiredItems }
    .filterNot(ItemStack::isEmpty)
    .groupingBy(::ItemStackKey)
    .fold(0) { total, stack -> total + stack.count }
    .map { (key, count) -> key.stack.copyWithCount(count) }
