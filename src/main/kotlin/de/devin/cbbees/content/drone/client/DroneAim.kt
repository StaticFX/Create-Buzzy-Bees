package de.devin.cbbees.content.drone.client

import de.devin.cbbees.content.schematics.client.SchematicFaceAim
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

@ClientSide
object DroneAim {
    private const val REACH = 75.0

    class Result(val schematicFace: Direction?, val target: BlockPos?)

    @JvmStatic
    fun current(ignoreBlocks: Boolean, range: Int): Result? {
        if (!DroneViewClientState.active) return null
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val drone = mc.level?.getEntity(DroneViewClientState.droneEntityId) ?: return null

        val from = drone.position()
        val to = from.add(0.0, -REACH, 0.0)
        val target = if (ignoreBlocks) BlockPos.containing(from.add(0.0, -range.toDouble(), 0.0)) else groundBelow(player, from, to)
        return Result(SchematicFaceAim.along(from, to), target)
    }

    private fun groundBelow(player: Player, from: Vec3, to: Vec3): BlockPos? {
        val level = player.level()
        val hit = level.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        if (hit.type != HitResult.Type.BLOCK) return null
        val pos = BlockPos.containing(hit.location)
        val onTop = hit.direction.axis.isVertical && !level.getBlockState(pos).canBeReplaced()
        return if (onTop) pos.relative(hit.direction) else pos
    }
}
