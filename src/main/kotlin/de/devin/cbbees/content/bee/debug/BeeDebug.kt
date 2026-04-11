package de.devin.cbbees.content.bee.debug

import de.devin.cbbees.content.bee.MechanicalBeelike
import de.devin.cbbees.network.BeeDebugSyncPacket
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/**
 * Tracks which players have bee debug mode enabled.
 * When enabled, bee behaviors send chat messages describing what each bee is doing.
 */
object BeeDebug {

    private val enabledPlayers = mutableSetOf<UUID>()

    fun toggle(player: ServerPlayer): Boolean {
        val enabled = if (enabledPlayers.remove(player.uuid)) false
        else { enabledPlayers.add(player.uuid); true }
        PacketDistributor.sendToPlayer(player, BeeDebugSyncPacket(enabled))
        return enabled
    }

    fun isEnabled(player: ServerPlayer): Boolean = player.uuid in enabledPlayers

    fun clear() = enabledPlayers.clear()

    /**
     * Sends a debug message about any [MechanicalBeelike] to all nearby debug-enabled players.
     * Automatically includes spring tension and uses the bee's debug label.
     */
    fun log(bee: MechanicalBeelike, message: String) {
        if (enabledPlayers.isEmpty()) return
        val entity = bee as? Mob ?: return
        val springPct = (bee.springTension * 100).toInt()
        logForEntity(entity, bee.debugLabel, "Spring: $springPct% | $message")
    }

    /**
     * Sends a debug message about any mob entity to all nearby players with debug enabled.
     */
    fun logForEntity(entity: Mob, label: String, message: String) {
        if (enabledPlayers.isEmpty()) return
        val level = entity.level()
        if (level.isClientSide) return

        val server = level.server ?: return
        val shortId = entity.uuid.toString().substring(0, 4)

        val text = Component.literal("[$label $shortId] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(message).withStyle(ChatFormatting.GRAY))

        for (uuid in enabledPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            if (player.level() == level && player.blockPosition().closerThan(entity.blockPosition(), 128.0)) {
                player.sendSystemMessage(text)
            }
        }
    }

    /**
     * Sends a debug message about a block at a given position to all nearby debug-enabled players.
     */
    fun logAtPos(level: net.minecraft.world.level.Level, pos: net.minecraft.core.BlockPos, label: String, message: String) {
        if (enabledPlayers.isEmpty()) return
        if (level.isClientSide) return

        val server = level.server ?: return

        val text = Component.literal("[$label ${pos.x},${pos.y},${pos.z}] ")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(message).withStyle(ChatFormatting.GRAY))

        for (uuid in enabledPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            if (player.level() == level && player.blockPosition().closerThan(pos, 128.0)) {
                player.sendSystemMessage(text)
            }
        }
    }
}
