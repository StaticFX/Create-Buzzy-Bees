package de.devin.cbbees.content.drone

import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.upgrades.UpgradeType
import de.devin.cbbees.network.DroneViewSyncPacket
import de.devin.cbbees.registry.AllEntityTypes
import de.devin.cbbees.util.ServerSide
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import top.theillusivec4.curios.api.CuriosApi
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ServerSide
object DroneViewManager {

    /** playerUUID -> droneEntityUUID */
    private val activeDrones = ConcurrentHashMap<UUID, UUID>()

    fun toggleDrone(player: ServerPlayer) {
        if (activeDrones.containsKey(player.uuid)) {
            deactivateDrone(player)
        } else {
            activateDrone(player)
        }
    }

    private fun activateDrone(player: ServerPlayer) {
        val backpack = findBackpack(player) ?: return

        val beehiveItem = backpack.item as PortableBeehiveItem

        // Check upgrade
        if (beehiveItem.getUpgradeCount(backpack, UpgradeType.DRONE_VIEW) <= 0) return

        // Check bees
        if (beehiveItem.getTotalBeeCount(backpack) <= 0) return

        // Consume bee
        beehiveItem.consumeBee(backpack)

        // Calculate max range from upgrades
        val beeContext = UpgradeType.fromBackpack(backpack)

        // Spawn drone entity
        val level = player.serverLevel()
        val drone = MechanicalBeeEntity(AllEntityTypes.MECHANICAL_BEE.get(), level)
        val targetY = (player.y + MechanicalBeeEntity.DRONE_ALTITUDE).coerceAtMost(level.maxBuildHeight.toDouble() - 1.0)
        drone.moveTo(player.x, targetY, player.z, 0f, 90f)
        drone.setOwner(player.uuid)
        drone.isDrone = true
        drone.droneMaxRange = beeContext.droneRange
        drone.setNoGravity(true)
        level.addFreshEntity(drone)

        activeDrones[player.uuid] = drone.uuid

        // Sync to client (include max range so HUD can display it)
        PacketDistributor.sendToPlayer(player, DroneViewSyncPacket(drone.id, beeContext.droneRange.toFloat()))

        player.displayClientMessage(Component.translatable("cbbees.drone_view.activated"), true)
    }

    private fun deactivateDrone(player: ServerPlayer) {
        val droneUUID = activeDrones.remove(player.uuid) ?: return

        val level = player.serverLevel()
        val entity = level.getEntity(droneUUID)

        if (entity is MechanicalBeeEntity) {
            entity.discard()
        }

        // Return bee to backpack
        val backpack = findBackpack(player)
        if (backpack != null) {
            val beehiveItem = backpack.item as PortableBeehiveItem
            beehiveItem.addBee(backpack, ItemStack(de.devin.cbbees.items.AllItems.MECHANICAL_BEE.get(), 1))
        }

        // Sync to client
        PacketDistributor.sendToPlayer(player, DroneViewSyncPacket(-1, 0f))

        player.displayClientMessage(Component.translatable("cbbees.drone_view.deactivated"), true)
    }

    fun despawnDrone(player: ServerPlayer) {
        val droneUUID = activeDrones.remove(player.uuid) ?: return

        val level = player.serverLevel()
        val entity = level.getEntity(droneUUID)

        if (entity is MechanicalBeeEntity) {
            entity.discard()
        }

        // Return bee to backpack
        val backpack = findBackpack(player)
        if (backpack != null) {
            val beehiveItem = backpack.item as PortableBeehiveItem
            beehiveItem.addBee(backpack, ItemStack(de.devin.cbbees.items.AllItems.MECHANICAL_BEE.get(), 1))
        }
    }

    fun isActive(player: ServerPlayer): Boolean = activeDrones.containsKey(player.uuid)

    fun getDroneUUID(player: ServerPlayer): UUID? = activeDrones[player.uuid]

    fun findBackpack(player: ServerPlayer): ItemStack? {
        // Check Curios back slot
        val curios = CuriosApi.getCuriosHelper().findFirstCurio(player) { it.item is PortableBeehiveItem }
        if (curios.isPresent) return curios.get().stack()

        // Check chestplate armor slot
        val armorStack = player.inventory.armor[2]
        if (armorStack.item is PortableBeehiveItem) return armorStack

        return null
    }

    fun clear() {
        for ((playerId, droneUUID) in activeDrones.toMap()) {
            val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() ?: break
            val player = server.playerList.getPlayer(playerId)
            if (player != null) {
                despawnDrone(player)
            } else {
                val entity = server.overworld().getEntity(droneUUID)
                (entity as? MechanicalBeeEntity)?.discard()
            }
        }
        activeDrones.clear()
    }

    /**
     * Called periodically to validate active drones (e.g., backpack still equipped).
     */
    fun validateDrones() {
        val toRemove = mutableListOf<UUID>()
        for ((playerId, _) in activeDrones) {
            val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() ?: continue
            val player = server.playerList.getPlayer(playerId)
            if (player == null) {
                toRemove.add(playerId)
                continue
            }
            if (findBackpack(player) == null) {
                despawnDrone(player)
            }
        }
        toRemove.forEach { activeDrones.remove(it) }
    }
}
