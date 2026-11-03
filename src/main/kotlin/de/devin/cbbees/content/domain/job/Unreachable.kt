package de.devin.cbbees.content.domain.job

import com.simibubi.create.infrastructure.config.AllConfigs
import de.devin.cbbees.compat.sable.toWorld
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.network.BeeNetwork
import io.netty.buffer.ByteBuf
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.util.Optional
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * The parts of a job no hive can reach ([BeeJob.parkedBatches]), grouped into at most [MAX_AREAS]
 * boxes, and how far the nearest hive falls short of covering them.
 *
 * @property shortfall blocks of range the best placed hive is missing, or null without a hive in the level
 * @property neededRpm speed that hive would need, or null when even the maximum speed isn't enough
 */
data class Unreachable(
    val areas: List<BoundingBox>,
    val blocks: Int,
    val shortfall: Int?,
    val neededRpm: Int?,
) {

    fun describe(): List<Component> = buildList {
        add(
            if (shortfall == null) Component.translatable("cbbees.unreachable.blocks", blocks).withStyle(ChatFormatting.RED)
            else Component.translatable("cbbees.unreachable.beyond", blocks, shortfall).withStyle(ChatFormatting.RED)
        )
        when {
            neededRpm != null -> add(Component.translatable("cbbees.unreachable.rpm", neededRpm).withStyle(ChatFormatting.GRAY))
            shortfall == null -> add(Component.translatable("cbbees.unreachable.no_hive").withStyle(ChatFormatting.GRAY))
        }
    }

    companion object {
        const val MAX_AREAS = 32
        private const val FIRST_CELL = 16
        private const val LAST_CELL = 4096

        private val areaCache = WeakHashMap<BeeJob, Pair<Int, List<BoundingBox>>>()

        private val BOX_CODEC: StreamCodec<ByteBuf, BoundingBox> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, { BlockPos(it.minX(), it.minY(), it.minZ()) },
            BlockPos.STREAM_CODEC, { BlockPos(it.maxX(), it.maxY(), it.maxZ()) },
            BoundingBox::fromCorners,
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, Unreachable> = StreamCodec.composite(
            BOX_CODEC.apply(ByteBufCodecs.list(MAX_AREAS)), Unreachable::areas,
            ByteBufCodecs.VAR_INT, Unreachable::blocks,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), { Optional.ofNullable(it.shortfall) },
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), { Optional.ofNullable(it.neededRpm) },
        ) { areas, blocks, shortfall, rpm -> Unreachable(areas, blocks, shortfall.orElse(null), rpm.orElse(null)) }

        /** What [job] can't reach with the hives of [networks], or null when it can reach everything. */
        fun of(job: BeeJob, networks: List<BeeNetwork>): Unreachable? {
            val parked = job.parkedBatches
            if (parked.isEmpty()) return null
            val areas = areaCache[job]?.takeIf { it.first == job.parkedVersion }?.second
                ?: group(parked.map { it.targetPosition }).also { areaCache[job] = job.parkedVersion to it }

            val level = job.level
            val best = networks.filter { it.level == level }.flatMap { it.hives }
                .map { hive -> hive to level.toWorld(hive.pos).let { neededRange(it.x, it.z, areas) } }
                .minByOrNull { (_, needed) -> needed }

            val shortfall = best?.let { (hive, needed) -> (needed - hive.getWorkRange().toInt()).coerceAtLeast(0) }
            val neededRpm = best?.let { (hive, needed) -> (hive as? MechanicalBeehiveBlockEntity)?.let { rpmFor(needed) } }
            return Unreachable(areas, parked.size, shortfall, neededRpm)
        }

        /** Boxes around [positions], one per grid cell, growing the cells until there are at most [MAX_AREAS]. */
        private fun group(positions: List<BlockPos>): List<BoundingBox> {
            var cell = FIRST_CELL
            while (true) {
                val cells = positions.groupBy { Math.floorDiv(it.x, cell) to Math.floorDiv(it.z, cell) }
                if (cells.size <= MAX_AREAS || cell >= LAST_CELL) {
                    return mergeTouching(cells.values.mapNotNull { BoundingBox.encapsulatingPositions(it).orElse(null) }).take(MAX_AREAS)
                }
                cell *= 2
            }
        }

        /** Joins boxes that touch or overlap, so a region split by the grid shows as one box. */
        private fun mergeTouching(boxes: List<BoundingBox>): List<BoundingBox> {
            val merged = boxes.toMutableList()
            do {
                var joined = false
                outer@ for (i in merged.indices) for (j in i + 1 until merged.size) {
                    if (touches(merged[i], merged[j])) {
                        merged[i] = union(merged[i], merged[j])
                        merged.removeAt(j)
                        joined = true
                        break@outer
                    }
                }
            } while (joined)
            return merged
        }

        private fun touches(a: BoundingBox, b: BoundingBox) =
            a.minX() <= b.maxX() + 1 && b.minX() <= a.maxX() + 1 &&
                a.minY() <= b.maxY() + 1 && b.minY() <= a.maxY() + 1 &&
                a.minZ() <= b.maxZ() + 1 && b.minZ() <= a.maxZ() + 1

        private fun union(a: BoundingBox, b: BoundingBox) = BoundingBox(
            minOf(a.minX(), b.minX()), minOf(a.minY(), b.minY()), minOf(a.minZ(), b.minZ()),
            maxOf(a.maxX(), b.maxX()), maxOf(a.maxY(), b.maxY()), maxOf(a.maxZ(), b.maxZ()),
        )

        /** Range a hive at ([x], [z]) needs to cover every area; hives reach a square around themselves. */
        private fun neededRange(x: Double, z: Double, areas: List<BoundingBox>): Int = areas.maxOf { box ->
            val dx = max(abs(box.minX() + 0.5 - x), abs(box.maxX() + 0.5 - x))
            val dz = max(abs(box.minZ() + 0.5 - z), abs(box.maxZ() + 0.5 - z))
            ceil(max(dx, dz)).toInt()
        }

        /** Speed a mechanical hive needs for [range], or null above the maximum rotation speed. */
        private fun rpmFor(range: Int): Int? {
            val rpm = ceil((range - CBBeesConfig.hiveBaseRange.get()) / CBBeesConfig.hiveRangePerRpm.get()).toInt().coerceAtLeast(1)
            return rpm.takeIf { it <= AllConfigs.server().kinetics.maxRotationSpeed.get() }
        }
    }
}
