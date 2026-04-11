package de.devin.cbbees.content.drone.client

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

/**
 * Renders the drone's max range as a circle on the ground, centered on the player.
 * Uses Create's [Outliner] line segments to form a circle with [SEGMENTS] edges.
 */
@ClientSide
object DroneRangeRenderer {

    private const val SEGMENTS = 64
    private const val SLOT_PREFIX = "drone_range_"
    private const val RANGE_COLOR = 0x9933FF // Purple to match drone theme

    private var activeSegments = 0

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        if (!DroneViewClientState.active) {
            clearSegments()
            return
        }

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val drone = mc.level?.getEntity(DroneViewClientState.droneEntityId) as? MechanicalBeeEntity ?: return

        val maxRange = DroneViewClientState.maxRange.toDouble()
        if (maxRange <= 0) {
            clearSegments()
            return
        }

        val centerX = player.x
        val centerZ = player.z
        // Draw the circle at the drone's Y level (slightly above ground so it's visible from above)
        val y = drone.y - MechanicalBeeEntity.DRONE_ALTITUDE + 1.0

        val outliner = Outliner.getInstance()
        val angleStep = 2.0 * Math.PI / SEGMENTS

        for (i in 0 until SEGMENTS) {
            val angle1 = i * angleStep
            val angle2 = (i + 1) * angleStep

            val start = Vec3(
                centerX + maxRange * Math.cos(angle1),
                y,
                centerZ + maxRange * Math.sin(angle1)
            )
            val end = Vec3(
                centerX + maxRange * Math.cos(angle2),
                y,
                centerZ + maxRange * Math.sin(angle2)
            )

            outliner.showLine("$SLOT_PREFIX$i", start, end)
                .colored(RANGE_COLOR)
                .lineWidth(1 / 8f)
        }

        // Clear any excess segments from a previous render with more segments
        if (activeSegments > SEGMENTS) {
            for (i in SEGMENTS until activeSegments) {
                outliner.remove("$SLOT_PREFIX$i")
            }
        }
        activeSegments = SEGMENTS
    }

    private fun clearSegments() {
        if (activeSegments > 0) {
            val outliner = Outliner.getInstance()
            for (i in 0 until activeSegments) {
                outliner.remove("$SLOT_PREFIX$i")
            }
            activeSegments = 0
        }
    }
}
