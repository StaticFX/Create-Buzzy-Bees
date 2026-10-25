package de.devin.cbbees.content.zapper

import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.equipment.zapper.terrainzapper.PlacementOptions
import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainTools
import de.devin.cbbees.content.zapper.shape.ShapeAim
import de.devin.cbbees.content.zapper.shape.ZapperShapes
import de.devin.cbbees.registry.AllDataComponents as CBBeesDataComponents
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
        val settings = stack.get(CBBeesDataComponents.ZAPPER_SHAPE.get()) ?: return null
        val shape = ZapperShapes.byId(settings.shape)
        val placement = stack.getOrDefault(AllDataComponents.SHAPER_PLACEMENT_OPTIONS, PlacementOptions.Merged)
        val tool = stack.getOrDefault(AllDataComponents.SHAPER_TOOL, TerrainTools.Fill)

        val aim = ShapeAim(hit.blockPos, hit.direction, player.direction, placement, tool)
        val placed = shape.place(level, aim, settings)
        return BrushShot(placed.origin, placed.positions.toMutableList(), hit.direction, shape.redirect(tool))
    }
}
