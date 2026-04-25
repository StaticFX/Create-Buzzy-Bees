package de.devin.cbbees.content.upgrades

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

/**
 * A single upgrade placed on the grid at a specific position and rotation.
 */
data class UpgradePlacement(
    val type: UpgradeType,
    val x: Int,
    val y: Int,
    val rotation: Int
) {
    companion object {
        val CODEC: Codec<UpgradePlacement> = RecordCodecBuilder.create { builder ->
            builder.group(
                Codec.STRING.fieldOf("type").forGetter { it.type.name },
                Codec.INT.fieldOf("x").forGetter { it.x },
                Codec.INT.fieldOf("y").forGetter { it.y },
                Codec.INT.fieldOf("rotation").forGetter { it.rotation }
            ).apply(builder) { typeName, x, y, rotation ->
                UpgradePlacement(UpgradeType.valueOf(typeName), x, y, rotation)
            }
        }

        val STREAM_CODEC: StreamCodec<ByteBuf, UpgradePlacement> = StreamCodec.of(
            { buf, p ->
                buf.writeInt(p.type.ordinal)
                buf.writeInt(p.x)
                buf.writeInt(p.y)
                buf.writeInt(p.rotation)
            },
            { buf ->
                UpgradePlacement(
                    UpgradeType.entries[buf.readInt()],
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
                )
            }
        )
    }
}

/**
 * 5x4 grid that holds shaped upgrade placements.
 */
class UpgradeGrid {

    val placements: MutableList<UpgradePlacement> = mutableListOf()

    @Transient
    var occupied: Array<Array<UpgradeType?>> = Array(ROWS) { arrayOfNulls(COLS) }
        private set

    fun canPlace(type: UpgradeType, x: Int, y: Int, rotation: Int): Boolean {
        val shape = type.shape.rotated(rotation)
        for ((dx, dy) in shape.cells) {
            val cx = x + dx
            val cy = y + dy
            if (cx < 0 || cx >= COLS || cy < 0 || cy >= ROWS) return false
            if (occupied[cy][cx] != null) return false
        }
        // Check max count for this upgrade type
        val currentCount = placements.count { it.type == type }
        if (currentCount >= type.maxStackInBackpack) return false
        return true
    }

    fun place(type: UpgradeType, x: Int, y: Int, rotation: Int): Boolean {
        if (!canPlace(type, x, y, rotation)) return false
        val placement = UpgradePlacement(type, x, y, rotation)
        placements.add(placement)
        val shape = type.shape.rotated(rotation)
        for ((dx, dy) in shape.cells) {
            occupied[y + dy][x + dx] = type
        }
        return true
    }

    /**
     * Removes the placement covering cell (x, y).
     * @return the removed placement, or null if cell was empty
     */
    fun removeAt(x: Int, y: Int): UpgradePlacement? {
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS) return null
        val occupant = occupied[y][x] ?: return null

        // Find the placement that covers this cell
        val placement = placements.find { p ->
            val shape = p.type.shape.rotated(p.rotation)
            shape.cells.any { (dx, dy) -> p.x + dx == x && p.y + dy == y }
        } ?: return null

        placements.remove(placement)
        // Clear occupied cells for this placement
        val shape = placement.type.shape.rotated(placement.rotation)
        for ((dx, dy) in shape.cells) {
            occupied[placement.y + dy][placement.x + dx] = null
        }
        return placement
    }

    fun getUpgradeCounts(): Map<UpgradeType, Int> {
        val counts = mutableMapOf<UpgradeType, Int>()
        for (p in placements) {
            counts[p.type] = (counts[p.type] ?: 0) + 1
        }
        return counts
    }

    /**
     * Recomputes [occupied] from [placements]. Call after deserialization.
     */
    fun rebuild() {
        occupied = Array(ROWS) { arrayOfNulls(COLS) }
        for (p in placements) {
            val shape = p.type.shape.rotated(p.rotation)
            for ((dx, dy) in shape.cells) {
                val cx = p.x + dx
                val cy = p.y + dy
                if (cx in 0 until COLS && cy in 0 until ROWS) {
                    occupied[cy][cx] = p.type
                }
            }
        }
    }

    fun copy(): UpgradeGrid {
        val grid = UpgradeGrid()
        for (p in placements) {
            grid.placements.add(p.copy())
        }
        grid.rebuild()
        return grid
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UpgradeGrid) return false
        return placements == other.placements
    }

    override fun hashCode(): Int = placements.hashCode()

    companion object {
        const val COLS = 6
        const val ROWS = 5

        val CODEC: Codec<UpgradeGrid> = UpgradePlacement.CODEC.listOf().xmap(
            { list ->
                val grid = UpgradeGrid()
                list.forEach { grid.placements.add(it) }
                grid.rebuild()
                grid
            },
            { grid -> grid.placements.toList() }
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, UpgradeGrid> = StreamCodec.of(
            { buf, grid ->
                buf.writeInt(grid.placements.size)
                for (p in grid.placements) {
                    UpgradePlacement.STREAM_CODEC.encode(buf, p)
                }
            },
            { buf ->
                val grid = UpgradeGrid()
                val count = buf.readInt()
                repeat(count) {
                    grid.placements.add(UpgradePlacement.STREAM_CODEC.decode(buf))
                }
                grid.rebuild()
                grid
            }
        )
    }
}
