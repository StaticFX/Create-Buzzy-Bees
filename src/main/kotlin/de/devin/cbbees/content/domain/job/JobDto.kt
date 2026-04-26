package de.devin.cbbees.content.domain.job

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/** Schematic placement metadata for client-side ghost block rendering. */
data class SchematicPlacement(
    val file: String,
    val anchor: BlockPos,
    val rotation: Rotation = Rotation.NONE,
    val mirror: Mirror = Mirror.NONE
)

data class ClientBatchInfo(
    val status: String,
    val target: BlockPos,
    val required: List<ItemStack>,
    val assignedBeeIds: List<UUID>,
    /** All ghost block positions and their block states for rendering. */
    val ghostBlocks: Map<BlockPos, BlockState> = emptyMap()
)

data class ClientJobInfo(
    val jobId: UUID,
    val name: String,          // short id label
    val status: String,        // JobStatus name
    val completed: Int,
    val total: Int,
    val reason: String?,       // null if not stuck
    val batches: List<ClientBatchInfo>,
    val schematicPlacement: SchematicPlacement? = null,
    val jobType: JobType = JobType.Construction
)

data class ClientNetworkInfo(
    val name: String,
    val activeBees: Int,
    val storedBees: Int,
    val maxBees: Int
)

data class HiveSnapshot(
    val networkInfo: ClientNetworkInfo,
    val jobs: List<ClientJobInfo>
)
