package de.devin.cbbees.content.zapper

import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainTools
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

fun interface ShapingTool {
    fun plan(level: Level, positions: List<BlockPos>, face: Direction, paint: BlockState): Map<BlockPos, BlockState>
}

object ShapingTools {

    val fill = ShapingTool { level, positions, _, paint ->
        positions.filter { TerrainTools.isReplaceable(level.getBlockState(it)) }.associateWith { paint }
    }

    val place = ShapingTool { _, positions, _, paint ->
        positions.associateWith { paint }
    }

    val replace = ShapingTool { level, positions, _, paint ->
        positions.filterNot { TerrainTools.isReplaceable(level.getBlockState(it)) }.associateWith { paint }
    }

    val clear = ShapingTool { _, positions, _, _ ->
        positions.associateWith { Blocks.AIR.defaultBlockState() }
    }

    val overlay = ShapingTool { level, positions, _, paint ->
        positions
            .filter { level.getBlockState(it).let { state -> !TerrainTools.isReplaceable(state) && state != paint } }
            .map { it.above() }
            .filter { TerrainTools.isReplaceable(level.getBlockState(it)) }
            .associateWith { paint }
    }

    val flatten = ShapingTool { level, positions, face, _ ->
        FlattenPlanner.plan(level, positions, face)
    }

    fun of(tool: TerrainTools): ShapingTool = when (tool) {
        TerrainTools.Fill -> fill
        TerrainTools.Place -> place
        TerrainTools.Replace -> replace
        TerrainTools.Clear -> clear
        TerrainTools.Overlay -> overlay
        TerrainTools.Flatten -> flatten
    }
}
