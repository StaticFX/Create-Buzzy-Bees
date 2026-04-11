package de.devin.cbbees.content.bee.client

import com.simibubi.create.content.equipment.goggles.GogglesItem
import de.devin.cbbees.config.CBBeesClientConfig
import de.devin.cbbees.content.bee.NetworkedBee
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

/**
 * Renders a line from each mechanical bee to its current target when
 * the local player is wearing engineer's goggles and looking at the bee,
 * or for all bees when debug mode is enabled.
 */
@ClientSide
object BeeTargetLineHandler {

    private const val LINE_COLOR = 0xFFD700 // Gold
    private const val MAX_DIST_SQ = 64.0 * 64.0 // 64-block radius

    /** Set by [de.devin.cbbees.network.BeeDebugSyncPacket] from the server. */
    @JvmStatic
    var debugEnabled = false

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        // Update pause state for wall-clock freeze, tick rotation smoothing
        val paused = Minecraft.getInstance().isPaused
        BeeClientTracker.setPaused(paused)
        if (!paused) BeeClientTracker.tickClient()

        if (!CBBeesClientConfig.showBeeTargetLines.get()) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        mc.level ?: return
        if (mc.screen != null) return

        if (!GogglesItem.isWearingGoggles(player)) return

        val lookedAtEntity = mc.crosshairPickEntity
        val playerPos = player.position()

        // Use the tracked bee set instead of scanning all entities
        for (bee in BeeClientTracker.getBees()) {
            val entity = bee as? Entity ?: continue
            if (entity.distanceToSqr(playerPos) > MAX_DIST_SQ) continue

            val target = bee.getTargetPos() ?: continue
            if (!debugEnabled && entity != lookedAtEntity) continue

            val start = entity.position().add(0.0, (entity.bbHeight / 2).toDouble(), 0.0)
            val end = Vec3.atCenterOf(target)

            val network = bee.network()
            val color = network?.color ?: LINE_COLOR

            Outliner.getInstance()
                .showLine("bee_target_${entity.id}", start, end)
                .colored(color)
                .lineWidth(1 / 16f)

            Outliner.getInstance()
                .chaseAABB("bee_target_block_${entity.id}", AABB(target))
                .colored(color)
                .lineWidth(1 / 16f)
        }
    }
}
