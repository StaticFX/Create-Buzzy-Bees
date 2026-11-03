package de.devin.cbbees.content.domain.job

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.BoundingBox
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

data class ClientJobInfo(
    val jobId: UUID,
    val name: String,          // short id label
    val status: String,        // JobStatus name
    val completed: Int,
    val total: Int,
    /** Why the job is stuck, or null if it isn't. */
    val stall: Stall?,
    /** Bees currently working on this job. */
    val beeIds: List<UUID>,
    /** Items the unfinished tasks still need, merged by item. */
    val materials: List<ItemStack>,
    /** Area covered by the job's targets, for its outline. */
    val bounds: BoundingBox?,
    val schematicPlacement: SchematicPlacement? = null,
    val jobType: JobType = JobType.Construction,
    val ownerId: UUID? = null,
    /** The owner's name, for display only. */
    val ownerName: String? = null
) {
    fun isOwnedBy(playerId: UUID): Boolean = ownerId == playerId

    /** Only the owner can cancel a job; jobs without an owner can be cancelled by anyone. */
    fun isCancellableBy(playerId: UUID): Boolean = ownerId == null || isOwnedBy(playerId)

    fun displayName(): Component = jobDisplayName(schematicPlacement, jobType)

    val activeBees: Int get() = beeIds.size
}

/** The schematic's file name, or the job type for jobs without a schematic. */
fun jobDisplayName(placement: SchematicPlacement?, jobType: JobType): Component = placement
    ?.let { Component.literal(it.file.substringAfterLast('/').removeSuffix(".nbt")) }
    ?: Component.translatable(jobType.translationKey)

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
