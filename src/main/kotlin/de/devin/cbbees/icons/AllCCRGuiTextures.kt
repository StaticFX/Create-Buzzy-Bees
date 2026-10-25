package de.devin.cbbees.icons

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

@OnlyIn(Dist.CLIENT)
class AllCCRGuiTextures(sheet: String, val startX: Int, val startY: Int, val width: Int, val height: Int) {

    val location: ResourceLocation = CreateBuzzyBeez.asResource("textures/gui/$sheet.png")

    fun render(graphics: GuiGraphics, x: Int, y: Int) {
        graphics.blit(location, x, y, startX.toFloat(), startY.toFloat(), width, height, SHEET_SIZE, SHEET_SIZE)
    }

    fun render(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        graphics.blit(location, x, y, startX.toFloat(), startY.toFloat(), width, height, SHEET_SIZE, SHEET_SIZE)
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
    }
}
