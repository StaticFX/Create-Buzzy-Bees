package de.devin.cbbees.content.domain.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.logistics.ReservablePort
import de.devin.cbbees.content.domain.logistics.TransportPort
import de.devin.cbbees.content.domain.network.topology.DefaultAnchorTopology
import de.devin.cbbees.content.domain.network.topology.NetworkTopology
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.*

class BeeNetwork(
    val id: UUID = UUID.randomUUID(),
    private val topology: NetworkTopology = DefaultAnchorTopology
) {
    val name: String = id.toString().substring(0, 4).uppercase()
    val color: Int = (id.hashCode() and 0x7F7F7F) or 0x808080

    var level: net.minecraft.world.level.Level? = null

    private val _components = mutableSetOf<INetworkComponent>()
    val components: MutableSet<INetworkComponent> get() = _components

    /**
     * One-directional links to other networks. Used by portable beehive networks to
     * access ports from nearby block-based networks without joining them as a component.
     * The linked network's ports are visible through [ports] filtered by hive work range.
     */
    private val _linkedNetworks = mutableSetOf<BeeNetwork>()

    fun linkNetwork(other: BeeNetwork) {
        _linkedNetworks.add(other)
    }

    fun unlinkNetwork(other: BeeNetwork) {
        _linkedNetworks.remove(other)
    }

    fun clearLinks() {
        _linkedNetworks.clear()
    }

    private var _hives: List<BeeHive>? = null
    private var _ownPorts: List<LogisticsPort>? = null
    private var _ownTransportPorts: List<TransportPort>? = null
    private var _ownReservablePorts: List<ReservablePort>? = null
    private var _ownTransportPortsByPos: Map<BlockPos, TransportPort>? = null

    private fun invalidateComponentCaches() {
        _hives = null
        _ownPorts = null
        _ownTransportPorts = null
        _ownReservablePorts = null
        _ownTransportPortsByPos = null
    }

    /**
     * Removes components whose block entity no longer exists at their position in the world.
     * Guards against ghost components that weren't properly unregistered.
     *
     * Protected by a tick-based guard so it runs at most once per tick per network,
     * avoiding hundreds of redundant [net.minecraft.world.level.Level.getBlockEntity] calls.
     */
    private var lastPurgeTick: Long = -1

    fun purgeStaleComponents(currentTick: Long = -1L) {
        if (currentTick >= 0 && currentTick == lastPurgeTick) return
        if (currentTick >= 0) lastPurgeTick = currentTick

        val removed = _components.removeAll { comp ->
            val be = comp as? BlockEntity ?: return@removeAll false
            if (be.isRemoved) return@removeAll true
            val worldBe = be.level?.getBlockEntity(be.blockPos)
            if (worldBe !== be) return@removeAll true
            if (!topology.isAnchor(comp)) {
                val inRange = _components.any { other ->
                    topology.isAnchor(other) && topology.isLogisticsRange(other, comp.pos)
                }
                if (!inRange) return@removeAll true
            }
            false
        }
        if (removed) {
            CreateBuzzyBeez.LOGGER.warn("[NET] Purged stale/out-of-range component(s) from network $id")
            invalidateComponentCaches()
        }
    }

    /** Own hives only — dispatch is always from own hives, never from linked networks. */
    val hives: List<BeeHive> get() = _hives ?: components.filterIsInstance<BeeHive>().also { _hives = it }

    /** Own ports (cached). */
    val ownPorts: List<LogisticsPort>
        get() = _ownPorts ?: components.filterIsInstance<LogisticsPort>().also { _ownPorts = it }
    val ownTransportPorts: List<TransportPort>
        get() = _ownTransportPorts ?: components.filterIsInstance<TransportPort>().also { _ownTransportPorts = it }
    val ownReservablePorts: List<ReservablePort>
        get() = _ownReservablePorts ?: components.filterIsInstance<ReservablePort>().also { _ownReservablePorts = it }

    /**
     * All visible ports: own ports + reachable ports from linked networks.
     * Linked ports are filtered by hive work range (not cached, since the player moves).
     * For networks without links this returns the cached [ownPorts] directly.
     */
    val ports: List<LogisticsPort>
        get() {
            if (_linkedNetworks.isEmpty()) return ownPorts
            return ownPorts + _linkedNetworks.flatMap { linked ->
                linked.ownPorts.filter { port -> hives.any { it.isInWorkRange(port.pos) } }
            }
        }

    val transportPorts: List<TransportPort>
        get() {
            if (_linkedNetworks.isEmpty()) return ownTransportPorts
            return ownTransportPorts + _linkedNetworks.flatMap { linked ->
                linked.ownTransportPorts.filter { port -> hives.any { it.isInWorkRange(port.pos) } }
            }
        }

    val transportPortsByPos: Map<BlockPos, TransportPort>
        get() {
            if (_linkedNetworks.isEmpty()) return _ownTransportPortsByPos ?: ownTransportPorts.associateBy { it.pos }
                .also { _ownTransportPortsByPos = it }
            return transportPorts.associateBy { it.pos }
        }

    val reservablePorts: List<ReservablePort>
        get() {
            if (_linkedNetworks.isEmpty()) return ownReservablePorts
            return ownReservablePorts + _linkedNetworks.flatMap { linked ->
                linked.ownReservablePorts.filter { port -> hives.any { it.isInWorkRange(port.pos) } }
            }
        }

    /**
     * The aggregate operational range of all anchors in this network.
     */
    fun isInRange(pos: BlockPos): Boolean {
        return components.any { topology.isAnchor(it) && topology.isOperationalRange(it, pos) }
    }

    /**
     * Checks if any anchor is within range for logistics attachment.
     * Considers both block-based anchors (mechanical beehives) and portable beehives.
     */
    fun isInLogisticsRange(pos: BlockPos): Boolean {
        return components.any { c ->
            (c is BlockEntity || c is PortableBeeHive) && topology.isAnchor(c) && topology.isLogisticsRange(c, pos)
        }
    }

    fun findProvider(stack: ItemStack): LogisticsPort? {
        return ports.filter { it.isValidForPickup() && it.testFilter(stack) && it.hasItemStack(stack) }
            .maxByOrNull { it.priority() }
    }

    fun findDropOff(stack: ItemStack, beeHiveId: UUID? = null): LogisticsPort? {
        return ports.filter {
            it.isValidForDropOff()
                    && (stack.isEmpty || it.testFilter(stack))
                    && (beeHiveId == null || it !is PortableBeeHive || it.id == beeHiveId)
        }.maxByOrNull { it.priority() }
    }

    fun findAvailableProvider(stack: ItemStack, excludeBeeId: UUID? = null): LogisticsPort? {
        return findAvailableProviders(stack, excludeBeeId).firstOrNull()
    }

    fun findAvailableProviders(stack: ItemStack, excludeBeeId: UUID? = null): List<LogisticsPort> {
        return ports.filter {
            it.isValidForPickup() && it.testFilter(stack) && it.hasAvailableItemStack(
                stack,
                excludeBeeId
            )
        }
            .sortedByDescending { it.priority() }
    }

    fun releaseReservations(beeId: UUID) {
        reservablePorts.forEach { it.releaseReservation(beeId) }
    }

    fun cleanupReservations(currentTick: Long) {
        reservablePorts.forEach { it.cleanupReservations(currentTick) }
    }

    fun clearReservations() {
        reservablePorts.forEach { it.clearReservations() }
    }

    fun dispatchBatch(batch: TaskBatch): Boolean {
        val candidates = hives.filter {
            topology.isOperationalRange(it, batch.targetPosition) &&
                    it.getAvailableBeeCount() > 0
        }.sortedBy { it.pos.distSqr(batch.targetPosition) }

        for (hive in candidates) {
            if (hive.acceptBatch(batch)) {
                CreateBuzzyBeez.LOGGER.debug("[DispatchBatch] Accepted by ${hive.javaClass.simpleName} at ${hive.pos}")
                return true
            }
        }
        return false
    }

    fun canConnect(component: INetworkComponent): Boolean {
        if (components.isEmpty()) {
            CreateBuzzyBeez.LOGGER.info("[NET] canConnect: network $id is EMPTY → true")
            return true
        }
        val firstComp = components.first()
        if (component.world != firstComp.world) return false

        val isAnchor = topology.isAnchor(component)
        if (isAnchor) {
            if (components.none { topology.isAnchor(it) }) return true
            return components.any { other -> topology.isAnchor(other) && topology.canConnectAnchors(component, other) }
        }

        val inRange = isInLogisticsRange(component.pos)
        CreateBuzzyBeez.LOGGER.info("[NET] canConnect: isInLogisticsRange=$inRange for port at ${component.pos}")
        return inRange
    }

    private fun anchorsConnected(c1: INetworkComponent, c2: INetworkComponent): Boolean =
        topology.canConnectAnchors(c1, c2)

    fun addComponent(component: INetworkComponent) {
        if (components.add(component)) {
            invalidateComponentCaches()
            if (level == null) level = component.world
            component.networkId = id
            component.sync()
        }
    }

    /**
     * Adds a component without setting its networkId or syncing.
     * Used on the client where the networkId is already set by the server.
     */
    fun addComponentClient(component: INetworkComponent) {
        if (components.add(component)) {
            invalidateComponentCaches()
            if (level == null) level = component.world
        }
    }

    fun removeComponent(component: INetworkComponent) {
        if (components.remove(component)) {
            invalidateComponentCaches()
        }
    }

    fun merge(other: BeeNetwork) {
        other.components.forEach { addComponent(it) }
        other.components.clear()
        other.invalidateComponentCaches()
    }

    /**
     * Performs a graph traversal to detect if the network has split.
     */
    fun split(): List<BeeNetwork> {
        val anchors = components.filter { topology.isAnchor(it) }.toMutableList()
        if (anchors.isEmpty()) return emptyList()

        val newNetworks = mutableListOf<BeeNetwork>()

        while (anchors.isNotEmpty()) {
            val start = anchors.removeAt(0)
            val group = mutableSetOf(start)
            val toProcess = mutableListOf(start)

            while (toProcess.isNotEmpty()) {
                val current = toProcess.removeAt(0)
                val neighbors = anchors.filter { anchorsConnected(current, it) }
                for (neighbor in neighbors) {
                    group.add(neighbor)
                    anchors.remove(neighbor)
                    toProcess.add(neighbor)
                }
            }

            if (newNetworks.isEmpty() && group.size == components.count { topology.isAnchor(it) }) {
                val nonAnchors = components.filter { !topology.isAnchor(it) }
                for (c in nonAnchors) {
                    if (!group.any { topology.isOperationalRange(it, c.pos) }) {
                        components.remove(c)
                        c.networkId = UUID.randomUUID()
                        c.sync()
                    }
                }
                return listOf(this)
            }

            val newNetwork = if (newNetworks.isEmpty()) this else BeeNetwork()
            newNetwork.components.clear()
            group.forEach { newNetwork.addComponent(it) }

            val remaining = components.filter { !topology.isAnchor(it) && !newNetwork.components.contains(it) }
            for (c in remaining) {
                if (group.any { topology.isOperationalRange(it, c.pos) }) {
                    newNetwork.addComponent(c)
                }
            }

            newNetworks.add(newNetwork)
        }

        return newNetworks
    }
}
