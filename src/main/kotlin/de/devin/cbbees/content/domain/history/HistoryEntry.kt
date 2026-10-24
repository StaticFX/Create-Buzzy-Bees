package de.devin.cbbees.content.domain.history

import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.action.BeeActionSerializer
import de.devin.cbbees.content.domain.job.JobType
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import java.util.UUID

class HistoryEntry(
    val jobId: UUID,
    val jobType: JobType,
    val dimension: ResourceKey<Level>,
    val changes: MutableMap<BlockPos, BlockChange> = linkedMapOf(),
) {
    val isFull: Boolean get() = changes.size >= MAX_CHANGES

    fun record(pos: BlockPos, before: BlockSnapshot, after: BlockSnapshot, undo: BeeAction = before.restoreAt(pos)) {
        val existing = changes[pos]
        val merged = BlockChange(existing?.before ?: before, after, existing?.undo ?: undo)
        if (merged.before == merged.after) changes.remove(pos)
        else if (pos in changes || !isFull) changes[pos.immutable()] = merged
    }

    fun save(registries: HolderLookup.Provider): CompoundTag = CompoundTag().apply {
        putUUID("JobId", jobId)
        putString("JobType", jobType.id)
        putString("Dimension", dimension.location().toString())
        put("Changes", ListTag().apply {
            changes.forEach { (pos, change) ->
                add(CompoundTag().apply {
                    put("Pos", NbtUtils.writeBlockPos(pos))
                    put("Before", change.before.save())
                    put("After", change.after.save())
                    put("Undo", BeeActionSerializer.save(change.undo, registries))
                })
            }
        })
    }

    companion object {
        const val MAX_CHANGES = 50_000

        fun load(tag: CompoundTag, registries: HolderLookup.Provider): HistoryEntry? {
            val jobType = JobType.entries.firstOrNull { it.id == tag.getString("JobType") } ?: return null
            val dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension")))
            val entry = HistoryEntry(tag.getUUID("JobId"), jobType, dimension)
            tag.getList("Changes", Tag.TAG_COMPOUND.toInt()).forEach { element ->
                val change = element as CompoundTag
                val pos = NbtUtils.readBlockPos(change, "Pos").orElse(null) ?: return@forEach
                val before = BlockSnapshot.load(change.getCompound("Before"), registries)
                val undo = BeeActionSerializer.load(change.getCompound("Undo"), registries) ?: before.restoreAt(pos)
                entry.changes[pos] = BlockChange(before, BlockSnapshot.load(change.getCompound("After"), registries), undo)
            }
            return entry
        }
    }
}

data class BlockChange(val before: BlockSnapshot, val after: BlockSnapshot, val undo: BeeAction)
