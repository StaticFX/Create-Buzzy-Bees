package de.devin.cbbees.content.domain

import de.devin.cbbees.content.bee.BeeSeparation
import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.MechanicalBumbleBeeItem
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TransportTask
import de.devin.cbbees.util.ServerSide
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.items.ItemHandlerHelper
import java.util.*

/**
 * Server-side singleton that scans BeeNetworks for transport demand
 * and dispatches TransportTasks to hives with available Mechanical Bumble Bees.
 *
 * Provider and Requester ports are matched by their frequency pair.
 * Within a frequency group, requesters are sorted by priority (higher first).
 */
@ServerSide
object TransportDispatcher : SavedData() {

    private var scanCounter = 0
    private const val SCAN_INTERVAL = 4

    fun clear() {
        scanCounter = 0
    }

    fun tick(gameTime: Long = 0L) {
        scanCounter++
        if (scanCounter < SCAN_INTERVAL) return
        scanCounter = 0

        scanAndDispatch()
    }

    private fun scanAndDispatch() {
        val allNetworks = ServerBeeNetworkManager.getNetworks()
        if (allNetworks.isEmpty()) return

        for (network in allNetworks) {
            val providerPorts = network.transportPorts.filter { it.isValidProvider() }
            val requesterPorts = network.transportPorts.filter { it.isValidRequester() }

            if (providerPorts.isEmpty() || requesterPorts.isEmpty()) continue

            val hivesWithBumbles = network.hives
                .filterIsInstance<MechanicalBeehiveBlockEntity>()
                .filter { it.getAvailableBeeCountOfType(MechanicalBumbleBeeItem::class.java) > 0 }

            if (hivesWithBumbles.isEmpty()) continue

            // Pre-group requesters by frequency key for O(1) matching per provider
            val requestersByFreq = requesterPorts.groupBy { it.linkBehaviour.networkKey }

            for (provider in providerPorts) {
                val handler = provider.getItemHandler(provider.world) ?: continue

                // Find a requester that matches this provider's frequency and has space
                val matchingRequesters = requestersByFreq[provider.linkBehaviour.networkKey] ?: continue
                val requester = matchingRequesters
                    .filter { it.hasInventorySpace() }
                    .maxByOrNull { it.priority() } ?: continue

                // Collect up to INVENTORY_SIZE distinct stacks for a single trip,
                // only items the requester can actually accept
                val requesterHandler = requester.getItemHandler(requester.world)
                val itemsToTransport = mutableListOf<ItemStack>()
                for (slot in 0 until handler.slots) {
                    if (itemsToTransport.size >= MechanicalBumbleBeeEntity.INVENTORY_SIZE) break

                    val stack = handler.getStackInSlot(slot)
                    if (stack.isEmpty) continue
                    if (!provider.hasAvailableItemStack(stack)) continue

                    // Check if the requester can accept this item (simulate insert)
                    if (requesterHandler != null) {
                        val simulated = ItemHandlerHelper.insertItemStacked(requesterHandler, stack.copy(), true)
                        if (simulated.count == stack.count) continue // no space at all
                    }

                    itemsToTransport.add(stack.copy())
                }

                if (itemsToTransport.isEmpty()) continue

                val task = TransportTask(
                    sourcePos = provider.pos,
                    targetPos = requester.pos,
                    items = itemsToTransport
                )

                // Use minByOrNull instead of sortedBy.firstOrNull to avoid full sort
                val hive = hivesWithBumbles
                    .filter { it.getAvailableBeeCountOfType(MechanicalBumbleBeeItem::class.java) > 0 }
                    .minByOrNull { it.pos.distSqr(provider.pos) }
                    ?: break // no more bumble bees available in any hive

                val beeItem = hive.consumeBeeOfType(MechanicalBumbleBeeItem::class.java)
                if (beeItem.isEmpty) continue

                val bee = spawnMechanicalBumbleBee(hive, task)
                if (bee != null) {
                    provider.reserve(bee.id, task.items, hive.world.gameTime)
                }
            }
        }
    }

    private fun spawnMechanicalBumbleBee(
        hive: MechanicalBeehiveBlockEntity,
        task: TransportTask
    ): ServerBeeData? {
        val spawnPos = Vec3.atCenterOf(hive.pos.above()).add(BeeSeparation.spawnOffset(hive.world.random))

        return ServerBeeManager.spawnTransportBee(
            hive = hive,
            task = task,
            networkId = hive.network().id,
            spawnPos = spawnPos,
        )
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        return tag
    }
}
