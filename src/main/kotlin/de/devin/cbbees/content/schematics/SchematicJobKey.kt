package de.devin.cbbees.content.schematics

import net.minecraft.nbt.CompoundTag
import java.util.UUID

/**
 * Data class representing a unique schematic construction job.
 * Uses schematic file name and anchor position to identify duplicates.
 */
data class SchematicJobKey(
    val playerUuid: UUID,
    val schematicFile: String,
    val anchorX: Int,
    val anchorY: Int,
    val anchorZ: Int
) {
    fun save(): CompoundTag {
        val tag = CompoundTag()
        tag.putUUID("PlayerUuid", playerUuid)
        tag.putString("SchematicFile", schematicFile)
        tag.putInt("AnchorX", anchorX)
        tag.putInt("AnchorY", anchorY)
        tag.putInt("AnchorZ", anchorZ)
        return tag
    }

    companion object {
        fun load(tag: CompoundTag): SchematicJobKey {
            return SchematicJobKey(
                tag.getUUID("PlayerUuid"),
                tag.getString("SchematicFile"),
                tag.getInt("AnchorX"),
                tag.getInt("AnchorY"),
                tag.getInt("AnchorZ")
            )
        }
    }
}
