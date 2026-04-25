package de.devin.cbbees.content.drone.client

import de.devin.cbbees.items.AllItems
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

@ClientSide
object DroneViewClientState {

    var active: Boolean = false
        private set
    var droneEntityId: Int = -1
        private set
    var maxRange: Float = 0f
        private set
    private var pendingEntityId: Int = -1
    private var pendingMaxRange: Float = 0f
    private var pendingRetries: Int = 0

    fun activate(entityId: Int, range: Float) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return

        val entity = level.getEntity(entityId)
        if (entity != null) {
            active = true
            droneEntityId = entityId
            maxRange = range
            pendingEntityId = -1
            mc.setCameraEntity(entity)
        } else {
            // Entity not yet synced — retry in tick
            pendingEntityId = entityId
            pendingMaxRange = range
            pendingRetries = 0
        }
    }

    fun deactivate() {
        val mc = Minecraft.getInstance()
        active = false
        droneEntityId = -1
        maxRange = 0f
        pendingEntityId = -1
        pendingRetries = 0
        mc.player?.let { mc.setCameraEntity(it) }
    }

    fun tick() {
        val mc = Minecraft.getInstance()

        // Retry pending entity lookup
        if (pendingEntityId != -1) {
            pendingRetries++
            val entity = mc.level?.getEntity(pendingEntityId)
            if (entity != null) {
                active = true
                droneEntityId = pendingEntityId
                maxRange = pendingMaxRange
                pendingEntityId = -1
                mc.setCameraEntity(entity)
            } else if (pendingRetries > 100) {
                // Give up after ~5 seconds
                pendingEntityId = -1
                pendingRetries = 0
            }
            return
        }

        // Validate drone is still alive
        if (active) {
            val drone = mc.level?.getEntity(droneEntityId)
            if (drone == null || !drone.isAlive) {
                deactivate()
            }
        }
    }

    fun reset() {
        active = false
        droneEntityId = -1
        maxRange = 0f
        pendingEntityId = -1
        pendingRetries = 0
    }

    /**
     * Finds the Construction Planner: main hand first, then any inventory slot
     * if drone view is active. Returns [ItemStack.EMPTY] if not found.
     */
    @JvmStatic
    fun findActivePlanner(player: Player): ItemStack {
        val mainHand = player.mainHandItem
        if (AllItems.CONSTRUCTION_PLANNER.isIn(mainHand)) return mainHand
        if (active) {
            val inv = player.inventory
            for (i in 0 until inv.containerSize) {
                val stack = inv.getItem(i)
                if (AllItems.CONSTRUCTION_PLANNER.isIn(stack)) return stack
            }
        }
        return ItemStack.EMPTY
    }
}
