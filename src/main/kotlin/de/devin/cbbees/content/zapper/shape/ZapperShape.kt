package de.devin.cbbees.content.zapper.shape

import com.simibubi.create.content.equipment.zapper.terrainzapper.PlacementOptions
import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainTools
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level

class ShapeParam(val key: String, val min: Int, val default: Int, val max: () -> Int) {
    val label: Component get() = Component.translatable("gui.cbbees.zapper.param.$key")
}

class ShapeAim(
    val target: BlockPos,
    val face: Direction,
    val facing: Direction,
    val placement: PlacementOptions,
    val tool: TerrainTools,
)

class PlacedShape(val origin: BlockPos, val positions: List<BlockPos>)

interface ZapperShape {
    val id: String
    val params: List<ShapeParam>
    val tools: List<TerrainTools> get() = TerrainTools.entries
    val hasPlacementOptions: Boolean get() = true
    val hasConnectivityOptions: Boolean get() = false
    val canBeHollow: Boolean get() = true
    val dependsOnWorld: Boolean get() = false

    val name: Component get() = Component.translatable("gui.cbbees.zapper.shape.$id")

    fun redirect(tool: TerrainTools): TerrainTools = tool

    fun place(level: Level, aim: ShapeAim, settings: ShapeSettings): PlacedShape
}
