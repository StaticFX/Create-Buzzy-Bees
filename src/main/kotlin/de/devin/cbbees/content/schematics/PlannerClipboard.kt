package de.devin.cbbees.content.schematics

import com.simibubi.create.content.schematics.SchematicExport
import com.simibubi.create.content.schematics.SchematicExport.SchematicExportResult
import com.simibubi.create.foundation.utility.CreatePaths
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.nio.file.Path

object PlannerClipboard {

    private const val FOLDER = "cbbees"
    private const val NAME = "clipboard.nbt"
    const val FILE = "$FOLDER/$NAME"

    fun save(schematicsDir: Path, level: Level, first: BlockPos, second: BlockPos): SchematicExportResult? =
        SchematicExport.saveSchematic(schematicsDir.resolve(FOLDER), NAME, true, level, first, second)

    fun save(player: ServerPlayer, first: BlockPos, second: BlockPos): SchematicExportResult? {
        val baseDir = CreatePaths.UPLOADED_SCHEMATICS_DIR
        val playerDir = baseDir.resolve(player.gameProfile.name).normalize()
        if (!playerDir.startsWith(baseDir)) return null
        return save(playerDir, player.level(), first, second)
    }

    fun fill(stack: ItemStack, player: ServerPlayer, origin: BlockPos) =
        ConstructionPlannerItem.deploy(stack, FILE, player.gameProfile.name, origin, player.level())
}
