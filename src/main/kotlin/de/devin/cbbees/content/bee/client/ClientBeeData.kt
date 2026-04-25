package de.devin.cbbees.content.bee.client

import de.devin.cbbees.content.bee.server.BeeType
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Client-side bee representation for rendering. Updated by [de.devin.cbbees.network.BeeSyncPacket].
 *
 * Uses velocity-based prediction between sync updates for smooth visual movement.
 * On each sync, calculates velocity from position delta and extrapolates forward.
 */
data class ClientBeeData(
    val id: UUID,
    val type: BeeType,
    var x: Double,
    var y: Double,
    var z: Double,
    var yRot: Float,
    var hasItem: Boolean,
) {
    /** Previous synced position (for velocity calculation). */
    private var prevSyncX: Double = x
    private var prevSyncY: Double = y
    private var prevSyncZ: Double = z

    /** Estimated velocity (blocks per tick). */
    var velX: Double = 0.0; private set
    var velY: Double = 0.0; private set
    var velZ: Double = 0.0; private set

    /** Render position — updated every frame with velocity prediction. */
    var renderX: Double = x; private set
    var renderY: Double = y; private set
    var renderZ: Double = z; private set
    private var prevRenderX: Double = x
    private var prevRenderY: Double = y
    private var prevRenderZ: Double = z

    var lastUpdateTick: Long = 0

    /** Interpolated position for rendering with sub-tick smoothness. */
    fun lerpPos(partialTick: Float) = Vec3(
        prevRenderX + (renderX - prevRenderX) * partialTick,
        prevRenderY + (renderY - prevRenderY) * partialTick,
        prevRenderZ + (renderZ - prevRenderZ) * partialTick,
    )

    /** Called when a sync packet arrives with new authoritative position. */
    fun applyUpdate(newX: Double, newY: Double, newZ: Double, newYRot: Float, newHasItem: Boolean, tick: Long) {
        // Calculate velocity from position delta (sync arrives every 2 ticks)
        velX = (newX - prevSyncX) * 0.5
        velY = (newY - prevSyncY) * 0.5
        velZ = (newZ - prevSyncZ) * 0.5

        prevSyncX = x; prevSyncY = y; prevSyncZ = z
        x = newX; y = newY; z = newZ
        yRot = newYRot
        hasItem = newHasItem
        lastUpdateTick = tick

        // Snap render position to the authoritative position
        renderX = newX; renderY = newY; renderZ = newZ
    }

    /** Called every client tick to predict position between sync updates. */
    fun tickClient() {
        prevRenderX = renderX
        prevRenderY = renderY
        prevRenderZ = renderZ

        // Extrapolate using velocity
        renderX += velX
        renderY += velY
        renderZ += velZ
    }
}
