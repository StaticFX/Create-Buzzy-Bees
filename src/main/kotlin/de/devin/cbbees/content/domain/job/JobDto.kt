package de.devin.cbbees.content.domain.job

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
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
) {
    fun save(): CompoundTag {
        val tag = CompoundTag()
        tag.putString("File", file)
        tag.putInt("AnchorX", anchor.x)
        tag.putInt("AnchorY", anchor.y)
        tag.putInt("AnchorZ", anchor.z)
        tag.putString("Rotation", rotation.name)
        tag.putString("Mirror", mirror.name)
        return tag
    }

    companion object {
        fun load(tag: CompoundTag): SchematicPlacement {
            return SchematicPlacement(
                tag.getString("File"),
                BlockPos(tag.getInt("AnchorX"), tag.getInt("AnchorY"), tag.getInt("AnchorZ")),
                Rotation.valueOf(tag.getString("Rotation")),
                Mirror.valueOf(tag.getString("Mirror"))
            )
        }
    }
}

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
