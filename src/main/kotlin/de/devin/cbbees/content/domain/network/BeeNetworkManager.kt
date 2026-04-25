package de.devin.cbbees.content.domain.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.network.topology.DefaultAnchorTopology
import de.devin.cbbees.content.domain.network.topology.NetworkTopology
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.*
import de.devin.cbbees.util.ServerSide
import de.devin.cbbees.util.ClientSide

@ServerSide
object ServerBeeNetworkManager {
    private val networks = mutableListOf<BeeNetwork>()
    private var isScanning = false

    private val networkById = mutableMapOf<UUID, BeeNetwork>()
    private val componentToNetwork = mutableMapOf<INetworkComponent, BeeNetwork>()
    private val hiveById = mutableMapOf<UUID, BeeHive>()

    private var topology: NetworkTopology = DefaultAnchorTopology

    fun setTopology(topology: NetworkTopology) {
        this.topology = topology
    }

    fun getNetworks(): List<BeeNetwork> = networks

    /**
     * Rebuilds all O(1) lookup indexes from the authoritative [networks] list.
     * Called after structural mutations (register, unregister, merge, split, purge).
     */
    fun rebuildIndexes() {
        networkById.clear()
        componentToNetwork.clear()
        hiveById.clear()
        for (network in networks) {
            networkById[network.id] = network
            for (component in network.components) {
                componentToNetwork[component] = network
                if (component is BeeHive) {
                    hiveById[component.id] = component
                }
            }
        }
    }

    fun clear() {
        val size = networks.size
        networks.clear()
        networkById.clear()
        componentToNetwork.clear()
        hiveById.clear()
        CreateBuzzyBeez.LOGGER.info("Cleared $size networks")
    }

    fun registerComponent(component: INetworkComponent) {
        if (component.world.isClientSide) return

        if (getNetworkFor(component) != null) return

        CreateBuzzyBeez.LOGGER.info("[NET] registerComponent: ${component.javaClass.simpleName} at ${component.pos}, isAnchor=${component.isAnchor()}, networkId=${component.networkId}")

        val nearbyNetworks = networks.filter { it.canConnect(component) }.toMutableList()
        CreateBuzzyBeez.LOGGER.info("[NET]   canConnect matched ${nearbyNetworks.size} network(s)")
        for (net in nearbyNetworks) {
            CreateBuzzyBeez.LOGGER.info("[NET]     - network ${net.id} (${net.components.size} components)")
        }

        // Anchors (beehives) can reconnect by saved networkId during world load,
        // even when no spatial neighbor is found yet. Non-anchors (ports) must
        // always pass the spatial range check — they get picked up by
        // scanAndJoinNearbyComponents when a beehive registers.
        if (component.isAnchor()) {
            val idNetwork = networkById[component.networkId]
            if (idNetwork != null && !nearbyNetworks.contains(idNetwork)) {
                if (idNetwork.level == null || idNetwork.level == component.world) {
                    nearbyNetworks.add(idNetwork)
                    CreateBuzzyBeez.LOGGER.info("[NET]   anchor ID-match added network ${idNetwork.id}")
                }
            }
        }

        // Non-anchor components (logistics ports) cannot create their own network.
        // They must join an existing network that has an anchor (mechanical beehive).
        if (nearbyNetworks.isEmpty() && !component.isAnchor()) {
            // Assign a unique networkId so orphaned ports with stale saved IDs
            // don't accidentally appear grouped on the client.
            component.networkId = UUID.randomUUID()
            component.sync()
            CreateBuzzyBeez.LOGGER.info("[NET]   REJECTED: no network with anchor for ${component.javaClass.simpleName} at ${component.pos}")
            return
        }

        val targetNetwork: BeeNetwork

        if (nearbyNetworks.isEmpty()) {
            targetNetwork = BeeNetwork(component.networkId, topology)
            networks.add(targetNetwork)
            CreateBuzzyBeez.LOGGER.info("[NET]   CREATED new network ${targetNetwork.id}")
        } else {
            targetNetwork = nearbyNetworks.first()
            if (nearbyNetworks.size > 1) {
                nearbyNetworks.drop(1).forEach { other ->
                    targetNetwork.merge(other)
                    networks.remove(other)
                }
                CreateBuzzyBeez.LOGGER.info("[NET]   MERGED ${nearbyNetworks.size} networks into ${targetNetwork.id}")
            } else {
                CreateBuzzyBeez.LOGGER.info("[NET]   JOINED existing network ${targetNetwork.id}")
            }
        }

        targetNetwork.addComponent(component)

        // If the registered component is an anchor, it might pick up nearby orphaned components
        if (component.isAnchor() && !isScanning) {
            isScanning = true
            try {
                scanAndJoinNearbyComponents(
                    targetNetwork,
                    component.world,
                    component.pos,
                    component.getNetworkingRange()
                )
            } finally {
                isScanning = false
            }
        }

        rebuildIndexes()
    }

    private fun scanAndJoinNearbyComponents(network: BeeNetwork, level: Level, pos: BlockPos, range: Double) {
        val r = range.toInt()
        CreateBuzzyBeez.LOGGER.info("[NET] scanAndJoin: from $pos, range=$range (r=$r)")
        val minX = (pos.x - r) shr 4
        val maxX = (pos.x + r) shr 4
        val minZ = (pos.z - r) shr 4
        val maxZ = (pos.z + r) shr 4

        for (cx in minX..maxX) {
            for (cz in minZ..maxZ) {
                val chunk = level.getChunkSource().getChunk(cx, cz, false) ?: continue
                for (be in chunk.blockEntities.values) {
                    if (be is INetworkComponent && be !in network.components) {
                        val connects = network.canConnect(be)
                        CreateBuzzyBeez.LOGGER.info("[NET]   scan found ${be.javaClass.simpleName} at ${be.blockPos}, canConnect=$connects")
                        if (connects) {
                            val other = getNetworkFor(be)
                            if (other != null && other != network) {
                                network.merge(other)
                                networks.remove(other)
                                CreateBuzzyBeez.LOGGER.info("[NET]   scan: merged network ${other.id}")
                            } else {
                                network.addComponent(be)
                                CreateBuzzyBeez.LOGGER.info("[NET]   scan: added ${be.javaClass.simpleName} at ${be.blockPos}")
                            }
                        }
                    }
                }
            }
        }
    }

    fun unregisterComponent(component: INetworkComponent) {
        if (component.world.isClientSide) return

        val network = getNetworkFor(component) ?: return
        network.removeComponent(component)

        if (network.components.isEmpty()) {
            networks.remove(network)
            rebuildIndexes()
            return
        }

        val splitResults = network.split()
        if (splitResults.isEmpty()) {
            val remaining = network.components.toList()
            remaining.forEach {
                it.networkId = UUID.randomUUID()
                it.sync()
            }
            networks.remove(network)
        } else if (splitResults.size > 1) {
            splitResults.forEach { result ->
                if (!networks.contains(result)) {
                    networks.add(result)
                }
            }
        }

        rebuildIndexes()
    }

    fun registerWorker(worker: BeeHive) = registerComponent(worker)

    fun unregisterWorker(worker: BeeHive) = unregisterComponent(worker)

    fun unregisterWorker(id: UUID) {
        networks.toList().forEach { net ->
            net.components.find { it.id == id }?.let { unregisterComponent(it) }
        }
    }

    fun registerPort(port: LogisticsPort) = registerComponent(port)

    /** Adds a pre-built network directly. Used by gametests to bypass spatial scanning. */
    fun addNetwork(network: BeeNetwork) {
        networks.add(network)
        rebuildIndexes()
    }

    fun unregisterPort(port: LogisticsPort) = unregisterComponent(port)

    fun getNetworkAt(level: Level, pos: BlockPos): BeeNetwork? {
        return networks.find { it.level == level && it.isInRange(pos) }
    }

    fun getNetworkFor(component: INetworkComponent): BeeNetwork? {
        return componentToNetwork[component]
            ?: networks.find { it.components.contains(component) }
    }

    fun getNetwork(id: UUID): BeeNetwork? {
        return networkById[id]
    }

    fun findHive(id: UUID): BeeHive? {
        return hiveById[id]
    }

    fun getNetwork(id: UUID, level: Level): BeeNetwork? {
        val net = networkById[id] ?: return null
        return if (net.level == null || net.level == level) net else null
    }

    fun findProviderFor(level: Level, stack: ItemStack, startPos: BlockPos): LogisticsPort? {
        val network = getNetworkAt(level, startPos)
        return network?.findProvider(stack)
    }

    fun findPortableHive(playerId: UUID): PortableBeeHive? {
        return hiveById.values.filterIsInstance<PortableBeeHive>().find { it.player.uuid == playerId }
    }

    /**
     * Ensures the portable beehive always has its own isolated network.
     * Uses a deterministic UUID derived from the player's UUID for stability across reconnects.
     *
     * When the player is within range of a block-based network, the portable hive
     * joins that network so its bees can access block-based logistics ports.
     * The portable hive's own network is maintained separately.
     */
    fun reconnectPortableHive(hive: PortableBeeHive) {
        val playerPos = hive.player.blockPosition()
        val playerLevel = hive.player.level()

        val currentNetwork = getNetworkFor(hive)

        val blockNetwork = networks.find { net ->
            net.level == playerLevel &&
                net.isInRange(playerPos) &&
                net.components.any { it.isAnchor() && it !is PortableBeeHive }
        }

        if (blockNetwork != null) {
            if (currentNetwork != blockNetwork) {
                if (currentNetwork != null) {
                    currentNetwork.removeComponent(hive)
                    if (currentNetwork.components.isEmpty()) {
                        networks.remove(currentNetwork)
                    }
                }
                blockNetwork.addComponent(hive)
                rebuildIndexes()
                CreateBuzzyBeez.LOGGER.debug("Reconnected portable hive for ${hive.player.name.string} to block network ${blockNetwork.id}")
            }
        } else {
            if (currentNetwork != null && currentNetwork.components.any { it.isAnchor() && it !is PortableBeeHive }) {
                currentNetwork.removeComponent(hive)
                if (currentNetwork.components.isEmpty()) {
                    networks.remove(currentNetwork)
                }
                hive.networkId = stableNetworkId(hive.player.uuid)
                registerComponent(hive)
                CreateBuzzyBeez.LOGGER.debug("Detached portable hive for ${hive.player.name.string} into isolated network")
            } else if (currentNetwork == null) {
                hive.networkId = stableNetworkId(hive.player.uuid)
                registerComponent(hive)
            }
        }
    }

    /**
     * Generates a deterministic UUID from a player UUID for portable network IDs.
     * Uses nameUUIDFromBytes to produce the same result on every call.
     */
    fun stableNetworkId(playerUuid: UUID): UUID {
        return UUID.nameUUIDFromBytes("portable-network:$playerUuid".toByteArray())
    }
}

@ClientSide
object ClientBeeNetworkManager {
    private val networks = mutableListOf<BeeNetwork>()

    /**
     * Authoritative map of network UUID → component positions from the server.
     * Used to verify client-side grouping and detect desync.
     */
    private val serverSnapshot = mutableMapOf<UUID, List<BlockPos>>()

    fun getNetworks(): List<BeeNetwork> = networks

    fun clear() {
        val size = networks.size
        networks.clear()
        serverSnapshot.clear()
        CreateBuzzyBeez.LOGGER.info("Cleared $size client networks")
    }

    fun getNetwork(id: UUID): BeeNetwork {
        return networks.find { it.id == id } ?: run {
            val net = BeeNetwork(id)
            networks.add(net)
            net
        }
    }

    fun removeComponent(component: INetworkComponent) {
        component.network().removeComponent(component)
        if (component.network().components.isEmpty()) {
            networks.remove(component.network())
        }
    }

    /**
     * Applies an authoritative snapshot from the server. Reassigns any client-side
     * components that are in the wrong network, and removes stale networks.
     */
    fun applyServerSnapshot(snapshot: Map<UUID, List<BlockPos>>) {
        serverSnapshot.clear()
        serverSnapshot.putAll(snapshot)

        val posToNetwork = mutableMapOf<BlockPos, UUID>()
        for ((netId, positions) in snapshot) {
            for (pos in positions) {
                posToNetwork[pos] = netId
            }
        }

        val allComponents = networks.flatMap { it.components }.toList()
        val trackedPositions = allComponents.map { it.pos }.toMutableSet()

        for (component in allComponents) {
            val correctNetworkId = posToNetwork[component.pos]
            if (correctNetworkId == null) {
                val net = networks.find { it.components.contains(component) }
                net?.removeComponent(component)
            } else if (correctNetworkId != component.networkId) {
                val oldNet = networks.find { it.components.contains(component) }
                oldNet?.removeComponent(component)
                component.networkId = correctNetworkId
                getNetwork(correctNetworkId).addComponentClient(component)
            }
        }

        // Discover block entities at snapshot positions that aren't in any client network.
        // This catches components whose networkId never changed (so onNetworkIdChanged
        // never fired) and that were never added via onLoad().
        val level = net.minecraft.client.Minecraft.getInstance().level
        if (level != null) {
            for ((netId, positions) in snapshot) {
                for (pos in positions) {
                    if (pos in trackedPositions) continue
                    val be = level.getBlockEntity(pos) as? INetworkComponent ?: continue
                    be.networkId = netId
                    getNetwork(netId).addComponentClient(be)
                }
            }
        }

        networks.removeAll { it.components.isEmpty() }
    }

    /**
     * Returns the server-authoritative network ID for a position, if known.
     */
    fun getServerNetworkId(pos: BlockPos): UUID? {
        for ((netId, positions) in serverSnapshot) {
            if (pos in positions) return netId
        }
        return null
    }
}
