package de.devin.cbbees.content.domain.network

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.util.UUID

interface INetworkComponent {
    val id: UUID
    val world: Level
    val pos: BlockPos
    var networkId: UUID?

    val isOrphaned: Boolean get() = networkId == null

    fun orphan() {
        networkId = null
        sync()
    }

    fun network(): BeeNetwork {
        return if (world.isClientSide) {
            networkId?.let(ClientBeeNetworkManager::getNetwork) ?: BeeNetwork()
        } else {
            ServerBeeNetworkManager.getNetworkFor(this)
                ?: run {
                    ServerBeeNetworkManager.registerComponent(this)
                    ServerBeeNetworkManager.getNetworkFor(this)
                } ?: networkId?.let(::BeeNetwork) ?: BeeNetwork()
        }
    }

    /**
     * Helper method to handle re-grouping on the client when the networkId changes.
     */
    fun onNetworkIdChanged(old: UUID?, new: UUID?) {
        val level = (this as? SmartBlockEntity)?.level ?: return
        if (level.isClientSide) {
            old?.let { ClientBeeNetworkManager.getNetwork(it).removeComponent(this) }
            new?.let { ClientBeeNetworkManager.getNetwork(it).addComponentClient(this) }
        }
    }

    /**
     * Anchors form the backbone of the network and define its reachable area.
     */
    fun isAnchor(): Boolean

    /**
     * The distance at which this component can connect to other anchors.
     */
    fun getNetworkingRange(): Double

    /**
     * Checks if a position is within the functional work area of this component.
     */
    fun isInWorkRange(pos: BlockPos): Boolean

    fun addToNetwork(level: Level) {
        if (!level.isClientSide) {
            ServerBeeNetworkManager.registerComponent(this)
        } else {
            networkId?.let { ClientBeeNetworkManager.getNetwork(it).addComponentClient(this) }
        }
    }

    fun removeFromNetwork(level: Level) {
        if (level.isClientSide) {
            ClientBeeNetworkManager.removeComponent(this)
        } else {
            ServerBeeNetworkManager.unregisterComponent(this)
        }
    }

    /**
     * Syncs component data to clients.
     */
    fun sync() {
        if (this is SmartBlockEntity) {
            this.setChanged()
            this.sendData()
        }
    }
}
