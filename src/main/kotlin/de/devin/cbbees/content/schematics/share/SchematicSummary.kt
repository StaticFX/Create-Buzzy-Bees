package de.devin.cbbees.content.schematics.share

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

data class SchematicSummary(val sizeX: Int, val sizeY: Int, val sizeZ: Int, val materials: Map<Block, Int>) {

    val blockCount: Int get() = materials.values.sum()

    companion object {
        private val IGNORED = setOf(Blocks.AIR, Blocks.STRUCTURE_VOID)

        fun read(tag: CompoundTag): SchematicSummary {
            val size = tag.getList("size", Tag.TAG_INT.toInt())
            val palette = tag.getList("palette", Tag.TAG_COMPOUND.toInt()).filterIsInstance<CompoundTag>().map {
                ResourceLocation.tryParse(it.getString("Name"))?.let(BuiltInRegistries.BLOCK::get) ?: Blocks.AIR
            }
            val materials = tag.getList("blocks", Tag.TAG_COMPOUND.toInt()).filterIsInstance<CompoundTag>()
                .mapNotNull { palette.getOrNull(it.getInt("state")) }
                .filter { it !in IGNORED }
                .groupingBy { it }
                .eachCount()
            return SchematicSummary(size.getInt(0), size.getInt(1), size.getInt(2), materials)
        }
    }
}
