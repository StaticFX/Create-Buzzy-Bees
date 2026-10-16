package de.devin.cbbees.content.domain.network.topology

import de.devin.cbbees.compat.sable.toWorld
import de.devin.cbbees.content.domain.network.INetworkComponent
import net.minecraft.core.BlockPos
import kotlin.math.abs

/**
 * Default topology that mirrors the current behavior:
 * - Anchors are components where INetworkComponent.isAnchor() returns true
 * - Anchor connectivity uses getNetworkingRange() in axis-aligned X/Z distance
 * - Operational range uses isInWorkRange(pos)
 * - Logistics range uses getNetworkingRange() with axis-aligned X/Z distance
 */
object DefaultAnchorTopology : NetworkTopology {
    override fun isAnchor(component: INetworkComponent): Boolean = component.isAnchor()

    override fun canConnectAnchors(a: INetworkComponent, b: INetworkComponent): Boolean {
        val posA = a.world.toWorld(a.pos)
        val posB = b.world.toWorld(b.pos)
        val dx = abs(posA.x - posB.x)
        val dz = abs(posA.z - posB.z)
        val combined = a.getNetworkingRange() + b.getNetworkingRange()
        return dx <= combined && dz <= combined
    }

    override fun isOperationalRange(anchor: INetworkComponent, pos: BlockPos): Boolean =
        anchor.isInWorkRange(pos)

    override fun isLogisticsRange(anchor: INetworkComponent, pos: BlockPos): Boolean {
        return anchor.isInWorkRange(pos)
    }
}