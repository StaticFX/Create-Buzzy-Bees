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
    private const val NETWORK_RANGE_SLOT = "beehive_range_network"
    private const val RANGE_COLOR = 0xFFD700 // Gold color for bees
    private const val NETWORK_RANGE_COLOR = 0xDDEEFF

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
        renderSelectedHiveRange(be, slotKeys)
        renderSameNetworkRange(be, slotKeys)
    }

    private fun renderSelectedHiveRange(be: MechanicalBeehiveBlockEntity, slotKeys: MutableSet<String>) {
        val maxRange = be.getWorkRange()
        if (maxRange <= 0) return

        renderRangeBox(SINGLE_HIVE_SLOT, rangeBox(be.blockPos, maxRange), RANGE_COLOR, 1 / 16f, slotKeys)
    }

    private fun renderSameNetworkRange(be: MechanicalBeehiveBlockEntity, slotKeys: MutableSet<String>) {
        val network = ClientBeeNetworkManager.getNetwork(be.networkId)
        val hives = network.hives
        if (hives.size <= 1) return

        val boxes = hives
            .mapNotNull { hive ->
                val range = hive.getWorkRange()
                if (range <= 0) null else rangeBox(hive.pos, range)
            }
        if (boxes.isEmpty()) return

        val networkBox = AABB(
            boxes.minOf { it.minX },
            boxes.minOf { it.minY },
            boxes.minOf { it.minZ },
            boxes.maxOf { it.maxX },
            boxes.maxOf { it.maxY },
            boxes.maxOf { it.maxZ }
        )

        renderRangeBox(NETWORK_RANGE_SLOT, networkBox, NETWORK_RANGE_COLOR, 1 / 24f, slotKeys)
    }

    private fun rangeBox(center: net.minecraft.core.BlockPos, range: Double): AABB {
        return AABB(
            center.x - range,
            center.y.toDouble() - range,
            center.z - range,
            center.x + range + 1,
            center.y.toDouble() + range + 1,
            center.z + range + 1
        )
    }

    private fun renderRangeBox(
        slot: String,
        box: AABB,
        color: Int,
        lineWidth: Float,
        slotKeys: MutableSet<String>
    ) {
        slotKeys.add(slot)
        Outliner.getInstance()
            .chaseAABB(slot, box)
            .colored(color)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(lineWidth)
    }
}
