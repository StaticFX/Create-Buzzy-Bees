package de.devin.cbbees.content.logistics.storage

import com.simibubi.create.content.logistics.BigItemStack
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.job.MissingMaterials
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.logistics.waggle.WagglePortBlockEntity
import de.devin.cbbees.util.ServerSide
import net.minecraft.world.item.Item
import java.util.UUID

/**
 * Asks storage networks behind pickup ports to craft the materials that bee jobs are missing.
 * The crafted items land in the network, where the same ports hand them to the bees.
 *
 * Crafting shares the network's [BeeNetwork.pendingRequests] with Waggle Ports: items already
 * ordered are not crafted, and a network with a Waggle Port gives it one scan to order from
 * existing stock before anything is crafted.
 */
@ServerSide
object AutocraftRequester {

    private val SOURCE: UUID = UUID.nameUUIDFromBytes("cbbees:autocraft".toByteArray())

    private var nextScanTick = 0L
    private val retryAt = mutableMapOf<Pair<UUID, Item>, Long>()
    private val missingSince = mutableMapOf<Pair<UUID, Item>, Long>()

    fun clear() {
        nextScanTick = 0L
        retryAt.clear()
        missingSince.clear()
    }

    fun tick(gameTime: Long) {
        if (!CBBeesConfig.autocraftMissing.get() || gameTime < nextScanTick) return
        nextScanTick = gameTime + CBBeesConfig.autocraftInterval.get()
        retryAt.values.removeIf { it <= gameTime }
        ServerBeeNetworkManager.getNetworks().forEach { request(it, gameTime) }
    }

    fun request(network: BeeNetwork, gameTime: Long): List<BigItemStack> {
        val crafters = network.ports
            .filter { it.isValidForPickup() }
            .distinct()
            .mapNotNull { port -> port.storage()?.takeIf { it.canCraft }?.let { port to it } }
        if (crafters.isEmpty()) return emptyList()

        val pending = network.pendingRequests
        pending.clear(SOURCE)
        val missing = pending.plan(MissingMaterials.forNetwork(network), { Int.MAX_VALUE }, gameTime)
        val due = missing.filter { isDue(network, it, gameTime) }

        missingSince.keys.removeIf { (id, item) -> id == network.id && missing.none { it.stack.item == item } }

        val byPort = mutableMapOf<LogisticsPort, MutableList<BigItemStack>>()
        val requested = mutableListOf<BigItemStack>()
        for (entry in due) {
            val (result, port) = craft(crafters, entry)
            when (result) {
                CraftResult.CRAFTING -> byPort.getOrPut(port!!, ::mutableListOf) += entry
                CraftResult.PLANNING -> {}
                CraftResult.UNAVAILABLE -> {
                    retryAt[network.id to entry.stack.item] = gameTime + CBBeesConfig.autocraftRetryDelay.get()
                    continue
                }
            }
            requested += entry
        }
        crafters.forEach { (port, _) -> port.showCrafting(byPort[port].orEmpty()) }
        pending.record(SOURCE, requested, gameTime + CBBeesConfig.autocraftInterval.get() * 2L)
        return byPort.values.flatten()
    }

    /** A network already crafting the item keeps it; otherwise each network is asked in turn until one takes it. */
    private fun craft(crafters: List<Pair<LogisticsPort, PortStorage>>, entry: BigItemStack): Pair<CraftResult, LogisticsPort?> {
        crafters.firstOrNull { (_, storage) -> storage.isCrafting(entry.stack) }?.let { return CraftResult.CRAFTING to it.first }
        for ((port, storage) in crafters) {
            val result = storage.craft(entry.stack, entry.count)
            if (result != CraftResult.UNAVAILABLE) return result to port
        }
        return CraftResult.UNAVAILABLE to null
    }

    private fun isDue(network: BeeNetwork, entry: BigItemStack, gameTime: Long): Boolean {
        val key = network.id to entry.stack.item
        if (key in retryAt) return false
        if (network.components.none { it is WagglePortBlockEntity }) return true
        val since = missingSince.getOrPut(key) { gameTime }
        return gameTime - since >= CBBeesConfig.waggleRequestInterval.get()
    }
}
