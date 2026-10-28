package de.devin.cbbees.content.domain.built

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
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.util.UUID

class BuiltSchematic(
    val id: UUID,
    val ownerId: UUID,
    val dimension: ResourceKey<Level>,
    val name: String,
    val blocks: Map<BlockPos, BlockState>,
) {
    val bounds: BoundingBox = BoundingBox.encapsulatingPositions(blocks.keys).orElseThrow()

    fun isIntact(level: Level, pos: BlockPos): Boolean = level.getBlockState(pos).block == blocks[pos]?.block

    fun hasRemainingBlocks(level: Level): Boolean = blocks.keys.any { isIntact(level, it) }

    fun remainingBlocks(level: Level): List<BlockPos> = blocks.keys.filter { isIntact(level, it) }

    fun save(): CompoundTag = CompoundTag().apply {
        putUUID("Id", this@BuiltSchematic.id)
        putUUID("Owner", ownerId)
        putString("Dimension", dimension.location().toString())
        putString("Name", name)
        val palette = blocks.values.distinct()
        val paletteIndex = palette.withIndex().associate { (index, state) -> state to index }
        put("Palette", ListTag().apply { palette.forEach { add(NbtUtils.writeBlockState(it)) } })
        putLongArray("Positions", blocks.keys.map { it.asLong() }.toLongArray())
        putIntArray("States", blocks.values.map { paletteIndex.getValue(it) }.toIntArray())
    }

    companion object {
        fun load(tag: CompoundTag, registries: HolderLookup.Provider): BuiltSchematic? {
            val blockLookup = registries.lookupOrThrow(Registries.BLOCK)
            val palette = tag.getList("Palette", Tag.TAG_COMPOUND.toInt()).map { NbtUtils.readBlockState(blockLookup, it as CompoundTag) }
            val positions = tag.getLongArray("Positions")
            val states = tag.getIntArray("States")
            if (positions.isEmpty() || positions.size != states.size) return null
            val blocks = positions.indices.associate { BlockPos.of(positions[it]) to palette[states[it]] }
            return BuiltSchematic(
                tag.getUUID("Id"),
                tag.getUUID("Owner"),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                tag.getString("Name"),
                blocks,
            )
        }
    }
}
