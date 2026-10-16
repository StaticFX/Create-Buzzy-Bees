package de.devin.cbbees.content.zapper

import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainTools
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState

object FlattenPlanner {

    private val KERNEL = arrayOf(
        floatArrayOf(0.003765f, 0.015019f, 0.023792f, 0.015019f, 0.003765f),
        floatArrayOf(0.015019f, 0.059912f, 0.094907f, 0.059912f, 0.015019f),
        floatArrayOf(0.023792f, 0.094907f, 0.150342f, 0.094907f, 0.023792f),
        floatArrayOf(0.015019f, 0.059912f, 0.094907f, 0.059912f, 0.015019f),
        floatArrayOf(0.003765f, 0.015019f, 0.023792f, 0.015019f, 0.003765f),
    )

    private const val MAX_STEPS = 1000

    fun plan(level: Level, positions: List<BlockPos>, facing: Direction): Map<BlockPos, BlockState> {
        val edits = LinkedHashMap<BlockPos, BlockState>()
        fun stateAt(pos: BlockPos) = edits[pos] ?: level.getBlockState(pos)

        val surfaces = mutableListOf<BlockPos>()
        val heightMap = HashMap<Pair<Int, Int>, Int>()
        val offset = facing.axisDirection.step

        var minEntry = Int.MAX_VALUE
        var maxEntry = Int.MIN_VALUE
        var minCoord1 = Int.MAX_VALUE
        var minCoord2 = Int.MAX_VALUE
        var maxCoord1 = Int.MIN_VALUE
        var maxCoord2 = Int.MIN_VALUE

        for (target in positions) {
            val coords = coordsOf(target, facing)
            minCoord1 = minOf(minCoord1, coords.first)
            minCoord2 = minOf(minCoord2, coords.second)
            maxCoord1 = maxOf(maxCoord1, coords.first)
            maxCoord2 = maxOf(maxCoord2, coords.second)

            if (TerrainTools.isReplaceable(stateAt(target))) {
                heightMap.putIfAbsent(coords, Int.MIN_VALUE)
                continue
            }

            val surface = target.relative(facing)
            if (!TerrainTools.isReplaceable(stateAt(surface))) {
                if (heightMap[coords].let { it == null || it == Int.MIN_VALUE }) heightMap[coords] = Int.MAX_VALUE
                continue
            }

            surfaces.add(surface)
            val coordinate = facing.axis.choose(surface.x, surface.y, surface.z)
            val current = heightMap[coords]
            if (current == null || current == Int.MAX_VALUE || current == Int.MIN_VALUE || current * offset < coordinate * offset) {
                heightMap[coords] = coordinate
                maxEntry = maxOf(maxEntry, coordinate)
                minEntry = minOf(minEntry, coordinate)
            }
        }

        if (surfaces.isEmpty()) return edits

        val heights = Array(maxCoord1 - minCoord1 + 1) { i ->
            IntArray(maxCoord2 - minCoord2 + 1) { j ->
                when (val height = heightMap[Pair(minCoord1 + i, minCoord2 + j)]) {
                    null -> Int.MIN_VALUE
                    Int.MAX_VALUE -> if (offset == 1) maxEntry + 2 else minEntry - 2
                    Int.MIN_VALUE -> if (offset == 1) minEntry - 2 else maxEntry + 2
                    else -> height
                }
            }
        }
        val smoothed = smooth(heights)

        for (surface in surfaces) {
            val coords = coordsOf(surface, facing)
            var surfaceCoord = facing.axis.choose(surface.x, surface.y, surface.z) * offset
            val targetCoord = smoothed[coords.first - minCoord1][coords.second - minCoord2] * offset
            if (surfaceCoord == targetCoord) continue

            var pos = surface
            val state = stateAt(pos)
            var steps = MAX_STEPS

            while (surfaceCoord > targetCoord) {
                val below = pos.relative(facing.opposite)
                edits[below] = state
                edits[pos] = state.fluidState.createLegacyBlock()
                pos = below
                surfaceCoord--
                if (steps-- <= 0) break
            }

            while (surfaceCoord < targetCoord) {
                val above = pos.relative(facing)
                if (state.block !is LiquidBlock) edits[above] = state
                edits[pos] = stateAt(pos.relative(facing.opposite))
                pos = above
                surfaceCoord++
                if (steps-- <= 0) break
            }
        }

        return edits
    }

    private fun smooth(values: Array<IntArray>): Array<IntArray> = Array(values.size) { i ->
        IntArray(values[i].size) { j ->
            val value = values[i][j]
            var sum = 0f
            for (di in -2..2) {
                for (dj in -2..2) {
                    val ti = i + di
                    val tj = j + dj
                    var ref = if (ti !in values.indices || tj !in values[0].indices) value else values[ti][tj]
                    if (ref == Int.MIN_VALUE) ref = value
                    sum += KERNEL[di + 2][dj + 2] * ref
                }
            }
            Mth.floor(sum + .5f)
        }
    }

    private fun coordsOf(pos: BlockPos, facing: Direction): Pair<Int, Int> = when (facing.axis) {
        Direction.Axis.X -> Pair(pos.z, pos.y)
        Direction.Axis.Y -> Pair(pos.x, pos.z)
        Direction.Axis.Z -> Pair(pos.x, pos.y)
    }
}
