package de.devin.cbbees.content.zapper.shape

import com.simibubi.create.content.equipment.zapper.terrainzapper.PlacementOptions
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.structure.BoundingBox

abstract class SolidShape(
    override val id: String,
    override val params: List<ShapeParam>,
    private val oriented: Boolean = false,
) : ZapperShape {

    protected class Frame(val p: IntArray, val axis: Direction.Axis)

    protected abstract fun Frame.bounds(): BoundingBox

    protected abstract fun Frame.inside(x: Int, y: Int, z: Int): Boolean

    protected open fun Frame.visible(x: Int, y: Int, z: Int): Boolean = true

    private data class Key(val params: List<Int>, val hollow: Boolean, val axis: Direction.Axis)

    private val cache = object : LinkedHashMap<Key, List<BlockPos>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, List<BlockPos>>) = size > CACHE_SIZE
    }

    fun offsets(settings: ShapeSettings, facing: Direction = Direction.NORTH): List<BlockPos> {
        val axis = if (oriented) facing.axis.takeIf { it.isHorizontal } ?: Direction.Axis.Z else Direction.Axis.Y
        val clamped = settings.clampedTo(this)
        val key = Key(clamped.params, clamped.hollow, axis)
        return synchronized(cache) { cache.getOrPut(key) { compute(Frame(clamped.params.toIntArray(), axis), clamped.hollow) } }
    }

    private fun compute(frame: Frame, hollow: Boolean): List<BlockPos> = with(frame) {
        val box = bounds()
        val result = ArrayList<BlockPos>()
        for (x in box.minX()..box.maxX()) for (y in box.minY()..box.maxY()) for (z in box.minZ()..box.maxZ()) {
            if (!inside(x, y, z) || !visible(x, y, z)) continue
            if (hollow && NEIGHBORS.all { inside(x + it.stepX, y + it.stepY, z + it.stepZ) }) continue
            result += BlockPos(x, y, z)
        }
        result
    }

    override fun place(level: Level, aim: ShapeAim, settings: ShapeSettings): PlacedShape {
        val offsets = offsets(settings, aim.facing)
        val origin = aim.target.relative(aim.face, shift(offsets, aim.face, aim.placement))
        return PlacedShape(origin, offsets.map(origin::offset))
    }

    private fun shift(offsets: List<BlockPos>, face: Direction, placement: PlacementOptions): Int {
        if (offsets.isEmpty()) return 0
        val depths = offsets.map { it.x * face.stepX + it.y * face.stepY + it.z * face.stepZ }
        return when (placement) {
            PlacementOptions.Merged -> 0
            PlacementOptions.Attached -> 1 - depths.min()
            PlacementOptions.Inserted -> -depths.max()
        }
    }

    protected companion object {
        const val CACHE_SIZE = 8
        val NEIGHBORS = Direction.entries

        fun sq(v: Double) = v * v

        fun centered(size: Int) = -(size - 1) / 2..size / 2
    }
}
