package de.devin.cbbees.content.zapper.shape

import com.simibubi.create.content.equipment.zapper.terrainzapper.DynamicBrush
import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainTools
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

class FloodShape(override val id: String, private val surface: Boolean, range: ShapeParam) : ZapperShape {

    override val params = listOf(range)
    override val tools =
        if (surface) listOf(TerrainTools.Overlay, TerrainTools.Replace, TerrainTools.Clear)
        else listOf(TerrainTools.Replace, TerrainTools.Clear)
    override val hasPlacementOptions = false
    override val hasConnectivityOptions = true
    override val canBeHollow = false
    override val dependsOnWorld = true

    override fun redirect(tool: TerrainTools) = if (tool == TerrainTools.Overlay) TerrainTools.Place else tool

    override fun place(level: Level, aim: ShapeAim, settings: ShapeSettings): PlacedShape {
        val clamped = settings.clampedTo(this)
        val brush = DynamicBrush(surface).apply {
            set(clamped.param(0), if (clamped.diagonals) 0 else 1, if (clamped.fuzzy) 0 else 1)
        }
        val positions = ArrayList<BlockPos>()
        brush.addToGlobalPositions(level, aim.target, aim.face, positions, aim.tool)
        return PlacedShape(aim.target, positions)
    }
}
