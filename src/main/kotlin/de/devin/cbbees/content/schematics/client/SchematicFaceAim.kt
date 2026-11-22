package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.cbbees.compat.sable.SableCompat
import de.devin.cbbees.compat.sable.subLevelId
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.math.VecHelper
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * Picks the face of the deployed schematic that a ray hits, which Create's move and mirror tools act on.
 *
 * The ray is traced in the schematic's own space: when the schematic sits on a sub-level, both ends are
 * moved into it first, so the ship's rotation is applied to the direction as well as the origin.
 */
@ClientSide
object SchematicFaceAim {
    private const val REACH = 70.0

    /** Whether the deployed schematic is anchored on a sub-level. */
    @JvmStatic
    fun onSubLevel(player: Player): Boolean {
        val handler = CreateClient.SCHEMATIC_HANDLER
        return handler.isDeployed && player.level().subLevelId(handler.transformation.anchor) != null
    }

    /** The face under the player's crosshair. */
    @JvmStatic
    fun underCrosshair(player: Player): Direction? {
        val eye = player.eyePosition
        return along(eye, RaycastHelper.getTraceTarget(player, REACH, eye))
    }

    /** The face hit on the way from [from] to [to], both in world space. */
    @JvmStatic
    fun along(from: Vec3, to: Vec3): Direction? {
        val handler = CreateClient.SCHEMATIC_HANDLER
        if (!handler.isDeployed) return null
        val transform = handler.transformation
        val bounds = handler.bounds
        val pt = AnimationTickHolder.getPartialTicks()
        val start = transform.toLocalSpace(SableCompat.render.toLocal(transform.anchor, from, pt))
        val end = transform.toLocalSpace(SableCompat.render.toLocal(transform.anchor, to, pt))
        val hit = RaycastHelper.rayTraceUntil(start, end) { bounds.contains(VecHelper.getCenterOf(it)) }
        return if (hit.missed()) null else hit.facing
    }
}
