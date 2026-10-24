package de.devin.cbbees.content.domain.network.topology

import de.devin.cbbees.content.domain.network.INetworkComponent
import net.minecraft.core.BlockPos

/**
 * Abstraction for network connectivity and range rules.
 *
 * Implementations define how components connect into a network graph and
 * how operational and logistics ranges are computed.
 */
interface NetworkTopology {
    /** Whether the given component acts as an anchor/backbone in the network graph. */
    fun isAnchor(component: INetworkComponent): Boolean

    /** Whether two anchor components can connect directly. */
    fun canConnectAnchors(a: INetworkComponent, b: INetworkComponent): Boolean

    /** Whether the given position is within the functional/working range of the anchor. */
    fun isOperationalRange(anchor: INetworkComponent, pos: BlockPos): Boolean

    /** Whether the given position is within the logistics attachment range of the anchor. */
    fun isLogisticsRange(anchor: INetworkComponent, pos: BlockPos): Boolean

    /** Whether two components may ever end up in the same network, regardless of range. */
    fun canShareNetwork(a: INetworkComponent, b: INetworkComponent): Boolean = true
}