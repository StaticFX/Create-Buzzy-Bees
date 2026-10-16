package de.devin.cbbees.content.zapper

import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.equipment.zapper.terrainzapper.PlacementOptions
import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainBrushes
import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainTools
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

data class BrushShot(
    val origin: BlockPos,
    val positions: MutableList<BlockPos>,
    val face: Direction,
    val tool: TerrainTools
)

object ZapperBrush {

    const val RANGE = 128

    fun raycast(player: Player): BlockHitResult? {
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(RANGE.toDouble()))
        return player.level()
            .clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
            .takeIf { it.type != HitResult.Type.MISS }
    }

    fun aim(stack: ItemStack, level: Level, player: Player, hit: BlockHitResult): BrushShot? {
        val params = stack.get(AllDataComponents.SHAPER_BRUSH_PARAMS) ?: return null
        val brush = stack.getOrDefault(AllDataComponents.SHAPER_BRUSH, TerrainBrushes.Cuboid).get()
        val placement = stack.getOrDefault(AllDataComponents.SHAPER_PLACEMENT_OPTIONS, PlacementOptions.Merged)
        val tool = stack.getOrDefault(AllDataComponents.SHAPER_TOOL, TerrainTools.Fill)

        brush.set(params.x, params.y, params.z)
        val origin = hit.blockPos.offset(brush.getOffset(player.lookAngle, hit.direction, placement))
        val positions = mutableListOf<BlockPos>()
        brush.addToGlobalPositions(level, origin, hit.direction, positions, tool)
        return BrushShot(origin, positions, hit.direction, brush.redirectTool(tool))
    }
}
