package de.devin.cbbees.icons

import com.mojang.blaze3d.systems.RenderSystem
import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

@OnlyIn(Dist.CLIENT)
class AllCCRGuiTextures(
    sheet: String,
    val startX: Int,
    val startY: Int,
    val width: Int,
    val height: Int,
    private val sheetSize: Int = SHEET_SIZE,
) {

    val location: ResourceLocation = CreateBuzzyBeez.asResource("textures/gui/$sheet.png")

    fun render(graphics: GuiGraphics, x: Int, y: Int) = render(graphics, x, y, width, height)

    fun render(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        RenderSystem.enableBlend()
        graphics.blit(location, x, y, startX.toFloat(), startY.toFloat(), width, height, sheetSize, sheetSize)
        RenderSystem.disableBlend()
    }

    fun renderNineSlice(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, borderX: Int, borderY: Int = borderX) {
        val centerW = width - borderX * 2
        val centerH = height - borderY * 2
        val columns = listOf(Triple(x, startX, borderX), Triple(x + borderX, startX + borderX, w - borderX * 2), Triple(x + w - borderX, startX + width - borderX, borderX))
        val rows = listOf(Triple(y, startY, borderY), Triple(y + borderY, startY + borderY, h - borderY * 2), Triple(y + h - borderY, startY + height - borderY, borderY))
        rows.forEachIndexed { row, (ty, v, th) ->
            columns.forEachIndexed { column, (tx, u, tw) ->
                tile(graphics, tx, ty, tw, th, u, v, if (column == 1) centerW else tw, if (row == 1) centerH else th)
            }
        }
    }

    fun renderTiled(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) = tile(graphics, x, y, w, h, startX, startY, width, height)

    private fun tile(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, u: Int, v: Int, tileW: Int, tileH: Int) {
        if (w <= 0 || h <= 0 || tileW <= 0 || tileH <= 0) return
        RenderSystem.enableBlend()
        for (ty in y until y + h step tileH) {
            for (tx in x until x + w step tileW) {
                graphics.blit(location, tx, ty, u.toFloat(), v.toFloat(), minOf(tileW, x + w - tx), minOf(tileH, y + h - ty), sheetSize, sheetSize)
            }
        }
        RenderSystem.disableBlend()
    }

    companion object {
        const val SHEET_SIZE = 256

        val WAGGLE_PORT = AllCCRGuiTextures("waggle_port", 0, 0, 200, 58)

        val JOB_DETAIL_TOP = AllCCRGuiTextures("job_detail", 0, 0, 201, 24)
        val JOB_DETAIL_MIDDLE = AllCCRGuiTextures("job_detail", 0, 24, 200, 8)
        val JOB_DETAIL_BOTTOM = AllCCRGuiTextures("job_detail", 0, 32, 200, 32)
        val JOB_DETAIL_BAR = AllCCRGuiTextures("job_detail", 0, 70, 184, 6)
        val JOB_DETAIL_BAR_PROGRESS = AllCCRGuiTextures("job_detail", 0, 78, 184, 6)
        val JOB_DETAIL_BAR_STUCK = AllCCRGuiTextures("job_detail", 0, 86, 184, 6)
        val JOB_DETAIL_SLOT = AllCCRGuiTextures("job_detail", 0, 94, 18, 18)

        fun renderStretched(graphics: GuiGraphics, x: Int, y: Int, height: Int, top: AllCCRGuiTextures, middle: AllCCRGuiTextures, bottom: AllCCRGuiTextures) {
            top.render(graphics, x, y)
            var ty = y + top.height
            val end = y + height - bottom.height
            while (ty < end) {
                middle.render(graphics, x, ty, middle.width, minOf(middle.height, end - ty))
                ty += middle.height
            }
            bottom.render(graphics, x, end)
        }

        val SCHEMATIC_DEPLOYER = AllCCRGuiTextures("schematic_deployer", 0, 0, 244, 210)

        val SCHEMATIC_ZAPPER = AllCCRGuiTextures("schematic_zapper", 0, 0, 234, 103)
        val SCHEMATIC_ZAPPER_INACTIVE_PARAM = AllCCRGuiTextures("schematic_zapper", 0, 110, 18, 18)

        private const val PLANNER = "construction_planner"
        private const val PLANNER_SHEET = 512

        val PLANNER_PANEL = AllCCRGuiTextures(PLANNER, 40, 0, 16, 16, PLANNER_SHEET)
        val PLANNER_VIEWPORT = AllCCRGuiTextures(PLANNER, 64, 0, 16, 16, PLANNER_SHEET)
        val PLANNER_SEARCH = AllCCRGuiTextures(PLANNER, 88, 0, 16, 12, PLANNER_SHEET)
        val PLANNER_SLOT = AllCCRGuiTextures(PLANNER, 112, 0, 18, 18, PLANNER_SHEET)
        val PLANNER_SCROLL_TRACK = AllCCRGuiTextures(PLANNER, 136, 0, 4, 16, PLANNER_SHEET)
        val PLANNER_ROW_HOVER = AllCCRGuiTextures(PLANNER, 0, 232, 112, 18, PLANNER_SHEET)
        val PLANNER_ROW_SELECTED = AllCCRGuiTextures(PLANNER, 0, 250, 112, 18, PLANNER_SHEET)
        val PLANNER_ICON_GROUP = AllCCRGuiTextures(PLANNER, 116, 232, 12, 12, PLANNER_SHEET)
        val PLANNER_ICON_PARENT = AllCCRGuiTextures(PLANNER, 128, 232, 12, 12, PLANNER_SHEET)
        val PLANNER_SCROLL_THUMB = AllCCRGuiTextures(PLANNER, 156, 232, 4, 15, PLANNER_SHEET)
        val PLANNER_ICON_BUTTON = AllCCRGuiTextures(PLANNER, 0, 272, 12, 12, PLANNER_SHEET)
        val PLANNER_ICON_BUTTON_HOVER = AllCCRGuiTextures(PLANNER, 12, 272, 12, 12, PLANNER_SHEET)
        val PLANNER_ICON_BUTTON_DISABLED = AllCCRGuiTextures(PLANNER, 24, 272, 12, 12, PLANNER_SHEET)
        val PLANNER_GLYPH_RENAME = AllCCRGuiTextures(PLANNER, 40, 272, 12, 12, PLANNER_SHEET)
        val PLANNER_GLYPH_DELETE = AllCCRGuiTextures(PLANNER, 52, 272, 12, 12, PLANNER_SHEET)
        val PLANNER_GLYPH_BACK = AllCCRGuiTextures(PLANNER, 64, 272, 12, 12, PLANNER_SHEET)
        val PLANNER_GLYPH_RESET_VIEW = AllCCRGuiTextures(PLANNER, 76, 272, 12, 12, PLANNER_SHEET)
        val PLANNER_BUTTON = AllCCRGuiTextures(PLANNER, 0, 288, 72, 16, PLANNER_SHEET)
        val PLANNER_BUTTON_HOVER = AllCCRGuiTextures(PLANNER, 0, 304, 72, 16, PLANNER_SHEET)
        val PLANNER_BUTTON_DISABLED = AllCCRGuiTextures(PLANNER, 0, 320, 72, 16, PLANNER_SHEET)

        val DIALOG = AllCCRGuiTextures("schematic_dialog", 0, 0, 32, 32)
        val DIALOG_DIVIDER = AllCCRGuiTextures("schematic_dialog", 40, 0, 16, 2)
        val DIALOG_WARNING = AllCCRGuiTextures("schematic_dialog", 60, 0, 16, 16)
    }
}
