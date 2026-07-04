package de.devin.cbbees.content.beehive.client

import com.simibubi.create.AllItems
import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.cbbees.config.CBBeesClientConfig
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import de.devin.cbbees.util.ClientSide

/**
 * Handles rendering the range of a Mechanical Beehive when looked at by the player.
 */
@ClientSide
object BeehiveRangeHandler {
    private const val SINGLE_HIVE_SLOT = "beehive_range_single"
    private const val RANGE_COLOR = 0xFFD700 // Gold color for bees

    /** Slot keys used in the previous tick's render — stale ones get removed. */
    private val activeSlotKeys = mutableSetOf<String>()

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        if (!CBBeesClientConfig.showBeehiveRangeSafe()) {
            clearAllSlots()
            return
        }

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (mc.screen != null) {
            clearAllSlots()
            return
        }

        // Only show range indicators while holding a wrench
        val holdingWrench = AllItems.WRENCH.isIn(player.mainHandItem) || AllItems.WRENCH.isIn(player.offhandItem)
        if (!holdingWrench) {
            clearAllSlots()
            return
        }

        // Raycast to see if we are looking at a Network Component
        val trace = RaycastHelper.rayTraceRange(level, player, 20.0)
        if (trace != null && trace.type == HitResult.Type.BLOCK) {
            val be = level.getBlockEntity(trace.blockPos)
            if (be is MechanicalBeehiveBlockEntity) {
                val newSlotKeys = mutableSetOf<String>()
                renderRange(be, newSlotKeys)
                // Remove slots that were active last frame but no longer needed
                for (staleKey in activeSlotKeys - newSlotKeys) {
                    Outliner.getInstance().remove(staleKey)
                }
                activeSlotKeys.clear()
                activeSlotKeys.addAll(newSlotKeys)
                return
            }
        }

        // Not looking at a beehive — clear any leftover slots
        clearAllSlots()
    }

    private fun clearAllSlots() {
        if (activeSlotKeys.isNotEmpty()) {
            for (key in activeSlotKeys) {
                Outliner.getInstance().remove(key)
            }
            activeSlotKeys.clear()
        }
    }


    private fun renderRange(be: MechanicalBeehiveBlockEntity, slotKeys: MutableSet<String>) {
        val networkId = be.networkId
        val network = ClientBeeNetworkManager.getNetwork(networkId)
        val y = be.blockPos.y.toDouble()
        if (network.hives.isEmpty()) {
            renderSingleHiveRange(be, slotKeys)
        } else {
            renderNetworkRange(network, y, slotKeys)
        }
    }

    private fun renderNetworkRange(network: de.devin.cbbees.content.domain.network.BeeNetwork, y: Double, slotKeys: MutableSet<String>) {
        val hives = network.hives
        if (hives.isEmpty()) return

        // 1. Collect all squares (x1, z1, x2, z2)
        val squares = hives.map { hive ->
            val pos = hive.pos
            val range = hive.getWorkRange()
            Square(
                pos.x.toDouble() - range,
                pos.z.toDouble() - range,
                pos.x.toDouble() + range + 1.0,
                pos.z.toDouble() + range + 1.0
            )
        }

        // 2. Identify all boundary X and Z coordinates
        val xCoords = squares.flatMap { listOf(it.x1, it.x2) }.distinct().sorted()
        val zCoords = squares.flatMap { listOf(it.z1, it.z2) }.distinct().sorted()

        // 3. Create a grid and identify active cells
        val activeCells = Array(xCoords.size - 1) { BooleanArray(zCoords.size - 1) }
        for (i in 0 until xCoords.size - 1) {
            for (j in 0 until zCoords.size - 1) {
                val midX = (xCoords[i] + xCoords[i + 1]) / 2.0
                val midZ = (zCoords[j] + zCoords[j + 1]) / 2.0
                activeCells[i][j] = squares.any { it.contains(midX, midZ) }
            }
        }

        val color = network.color

        // 4. Render the interior (checkerboard) using non-overlapping rectangles
        // We use a simple greedy merging algorithm for rectangles to reduce outliner calls
        val visited = Array(xCoords.size - 1) { BooleanArray(zCoords.size - 1) }
        for (i in 0 until xCoords.size - 1) {
            for (j in 0 until zCoords.size - 1) {
                if (activeCells[i][j] && !visited[i][j]) {
                    // Try to expand as far as possible in X, then in Z
                    var endI = i
                    while (endI + 1 < xCoords.size - 1 && activeCells[endI + 1][j] && !visited[endI + 1][j]) {
                        endI++
                    }

                    var endJ = j
                    while (endJ + 1 < zCoords.size - 1) {
                        var allActive = true
                        for (k in i..endI) {
                            if (!activeCells[k][endJ + 1] || visited[k][endJ + 1]) {
                                allActive = false
                                break
                            }
                        }
                        if (allActive) endJ++ else break
                    }

                    for (k in i..endI) {
                        for (l in j..endJ) {
                            visited[k][l] = true
                        }
                    }

                    val box = AABB(
                        xCoords[i], y, zCoords[j],
                        xCoords[endI + 1], y + 0.05, zCoords[endJ + 1]
                    )
                    val slot = "network_interior_${network.id}_${i}_${j}"
                    slotKeys.add(slot)
                    Outliner.getInstance()
                        .chaseAABB(slot, box)
                        .colored(color)
                        .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
                        .lineWidth(0f) // No lines for the interior boxes
                }
            }
        }

        // 5. Render the outline boundary segments
        // Vertical edges
        for (i in 0..<xCoords.size) {
            var startJ = -1
            for (j in 0 until zCoords.size - 1) {
                val leftActive = if (i > 0) activeCells[i - 1][j] else false
                val rightActive = if (i < xCoords.size - 1) activeCells[i][j] else false

                if (leftActive != rightActive) {
                    if (startJ == -1) startJ = j
                } else {
                    if (startJ != -1) {
                        renderBoundaryLine(
                            xCoords[i],
                            y,
                            zCoords[startJ],
                            xCoords[i],
                            y,
                            zCoords[j],
                            "network_v_${network.id}_${i}_${startJ}",
                            color,
                            slotKeys
                        )
                        startJ = -1
                    }
                }
            }
            if (startJ != -1) {
                renderBoundaryLine(
                    xCoords[i],
                    y,
                    zCoords[startJ],
                    xCoords[i],
                    y,
                    zCoords[zCoords.size - 1],
                    "network_v_${network.id}_${i}_${startJ}",
                    color,
                    slotKeys
                )
            }
        }

        // Horizontal edges
        for (j in 0..<zCoords.size) {
            var startI = -1
            for (i in 0 until xCoords.size - 1) {
                val upActive = if (j > 0) activeCells[i][j - 1] else false
                val downActive = if (j < zCoords.size - 1) activeCells[i][j] else false

                if (upActive != downActive) {
                    if (startI == -1) startI = i
                } else {
                    if (startI != -1) {
                        renderBoundaryLine(
                            xCoords[startI],
                            y,
                            zCoords[j],
                            xCoords[i],
                            y,
                            zCoords[j],
                            "network_h_${network.id}_${startI}_${j}",
                            color,
                            slotKeys
                        )
                        startI = -1
                    }
                }
            }
            if (startI != -1) {
                renderBoundaryLine(
                    xCoords[startI],
                    y,
                    zCoords[j],
                    xCoords[xCoords.size - 1],
                    y,
                    zCoords[j],
                    "network_h_${network.id}_${startI}_${j}",
                    color,
                    slotKeys
                )
            }
        }
    }

    private fun renderBoundaryLine(
        x1: Double,
        y1: Double,
        z1: Double,
        x2: Double,
        y2: Double,
        z2: Double,
        slot: String,
        color: Int,
        slotKeys: MutableSet<String>
    ) {
        slotKeys.add(slot)
        // Render a thin AABB as a line
        val box = AABB(
            x1.coerceAtMost(x2) - 0.05, y1 + 0.01, z1.coerceAtMost(z2) - 0.05,
            x1.coerceAtLeast(x2) + 0.05, y1 + 0.07, z1.coerceAtLeast(z2) + 0.05
        )
        Outliner.getInstance()
            .chaseAABB(slot, box)
            .colored(color)
            .lineWidth(1 / 16f)
    }

    private data class Square(val x1: Double, val z1: Double, val x2: Double, val z2: Double) {
        fun contains(x: Double, z: Double) = x >= x1 && x <= x2 && z >= z1 && z <= z2
    }

    private fun renderSingleHiveRange(be: MechanicalBeehiveBlockEntity, slotKeys: MutableSet<String>) {
        val maxRange = be.getWorkRange()
        if (maxRange <= 0) return

        val center = be.blockPos
        val box = AABB(
            (center.x - maxRange),
            center.y.toDouble(),
            (center.z - maxRange),
            (center.x + maxRange + 1),
            center.y.toDouble() + 0.05,
            (center.z + maxRange + 1)
        )

        slotKeys.add(SINGLE_HIVE_SLOT)
        Outliner.getInstance()
            .chaseAABB(SINGLE_HIVE_SLOT, box)
            .colored(RANGE_COLOR)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(1 / 16f)
    }
}
