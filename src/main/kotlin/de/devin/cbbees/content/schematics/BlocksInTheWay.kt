package de.devin.cbbees.content.schematics

import de.devin.cbbees.mixin.StructureTemplateAccessor
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate

object BlocksInTheWay {

    fun find(level: Level, template: StructureTemplate, anchor: BlockPos, settings: StructurePlaceSettings): List<BlockPos> {
        val palette = (template as StructureTemplateAccessor).`cbbees$getPalettes`().firstOrNull() ?: return emptyList()
        return palette.blocks()
            .filterNot { it.state.isAir }
            .mapNotNull { info ->
                val pos = StructureTemplate.calculateRelativePosition(settings, info.pos).offset(anchor)
                val existing = level.getBlockState(pos)
                pos.takeIf { BuildModes.isOccupied(existing) && !existing.`is`(info.state.block) }
            }
    }
}
