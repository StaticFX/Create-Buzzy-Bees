package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.icons.AllCCRGuiTextures
import de.devin.cbbees.network.CancelJobPacket
import de.devin.cbbees.network.RequestPlayerJobsPacket
import net.createmod.catnip.gui.AbstractSimiScreen
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/**
 * Deployer-style overlay panel showing job details.
 * Matches the visual style of the Schematic Deployer screen.
 */
class JobDetailScreen(private val jobId: UUID) : AbstractSimiScreen(Component.translatable("gui.cbbees.job_detail.title")) {

    companion object {
        private const val PANEL_W = 200
        private const val LINE_H = 11
        private const val MARGIN = 8
        private const val BAR_H = 6
        private const val SLOT = 18
        private const val SLOTS_PER_ROW = 10
        private const val MAX_SLOT_ROWS = 2

        private const val GOLD = 0xFFD4AA00.toInt()
        private const val WHITE = 0xFFFFFF
        private const val GRAY = 0x999999
        private const val RED = 0xFF5555
    }

    private var job: ClientJobInfo? = null
    private var refreshTicks = 0
    private var cancelButton: IconButton? = null
    private var hoveredMaterial: ItemStack? = null

    override fun init() {
        setWindowSize(PANEL_W, computePanelHeight())
        super.init()
        PacketDistributor.sendToServer(RequestPlayerJobsPacket())
        refreshJob()
    }

    private fun refreshJob() {
        job = ConstructionRenderer.getJobInfo(jobId)
        cancelButton?.let { removeWidget(it) }

        if (job != null) {
            setWindowSize(PANEL_W, computePanelHeight())
            guiLeft = (width - windowWidth) / 2
            guiTop = (height - windowHeight) / 2
            cancelButton = IconButton(guiLeft + windowWidth - 25, guiTop + windowHeight - 25, AllIcons.I_TRASH).also {
                it.setToolTip(Component.translatable("gui.cbbees.job_detail.cancel"))
                addRenderableWidget(it)
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        cancelButton?.let { btn ->
            if (btn.isMouseOver(mouseX, mouseY)) {
                PacketDistributor.sendToServer(CancelJobPacket(jobId))
                onClose()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun renderWindow(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val x = guiLeft
        val y = guiTop
        val w = windowWidth
        val h = windowHeight
        hoveredMaterial = null

        AllCCRGuiTextures.renderStretched(
            graphics, x, y, h,
            AllCCRGuiTextures.JOB_DETAIL_TOP, AllCCRGuiTextures.JOB_DETAIL_MIDDLE, AllCCRGuiTextures.JOB_DETAIL_BOTTOM
        )

        val j = job
        if (j == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.cbbees.job_detail.not_found"),
                x + w / 2, y + 3, GRAY)
            return
        }

        val typeName = Component.translatable(j.jobType.translationKey).string
        graphics.drawCenteredString(font, "$typeName #${j.name}", x + w / 2, y + 3, GOLD)

        val innerW = w - MARGIN * 2
        var ty = y + 22

        val pct = if (j.total == 0) 1f else j.completed.toFloat() / j.total
        val isStuck = j.reason != null
        AllCCRGuiTextures.JOB_DETAIL_BAR.render(graphics, x + MARGIN, ty, innerW, BAR_H)
        val bar = if (isStuck) AllCCRGuiTextures.JOB_DETAIL_BAR_STUCK else AllCCRGuiTextures.JOB_DETAIL_BAR_PROGRESS
        bar.render(graphics, x + MARGIN, ty, (innerW * pct).toInt(), BAR_H)
        ty += BAR_H + 3

        graphics.drawString(font, "${j.completed}/${j.total} tasks  ${(pct * 100).toInt()}%",
            x + MARGIN, ty, WHITE, false)
        ty += LINE_H

        val activeBees = j.batches
            .filter { it.status == "IN_PROGRESS" || it.status == "PICKED" }
            .flatMap { it.assignedBeeIds }.distinct().size
        graphics.drawString(font, "Bees: $activeBees active", x + MARGIN, ty, GRAY, false)
        ty += LINE_H + 3

        if (isStuck) {
            val reason = j.reason!!
            val reasonText = if (reason.startsWith("cbbees.")) {
                Component.translatable(reason).string
            } else reason

            val lines = font.splitter.splitLines(reasonText, innerW,
                net.minecraft.network.chat.Style.EMPTY)
            for ((i, line) in lines.withIndex()) {
                if (i >= 3) {
                    graphics.drawString(font, "...", x + MARGIN, ty, RED, false)
                    ty += LINE_H
                    break
                }
                graphics.drawString(font, line.string, x + MARGIN, ty, RED, false)
                ty += LINE_H
            }
            ty += 2
        }

        val materials = materials(j)
        if (materials.isNotEmpty()) {
            graphics.drawString(font, "Materials:", x + MARGIN, ty, GOLD, false)
            ty += LINE_H
            materials.take(SLOTS_PER_ROW * MAX_SLOT_ROWS).forEachIndexed { i, stack ->
                val sx = x + MARGIN + (i % SLOTS_PER_ROW) * SLOT
                val sy = ty + (i / SLOTS_PER_ROW) * SLOT
                AllCCRGuiTextures.JOB_DETAIL_SLOT.render(graphics, sx, sy)
                graphics.renderItem(stack, sx + 1, sy + 1)
                graphics.renderItemDecorations(font, stack, sx + 1, sy + 1, shortCount(stack.count))
                if (mouseX in sx until sx + SLOT && mouseY in sy until sy + SLOT) hoveredMaterial = stack
            }
            val hidden = materials.size - SLOTS_PER_ROW * MAX_SLOT_ROWS
            if (hidden > 0) graphics.drawString(font, "+$hidden more", x + MARGIN, ty + MAX_SLOT_ROWS * SLOT + 2, GRAY, false)
        }
    }

    override fun renderWindowForeground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        super.renderWindowForeground(graphics, mouseX, mouseY, partialTicks)
        hoveredMaterial?.let { graphics.renderTooltip(font, it, mouseX, mouseY) }
    }

    private fun materials(j: ClientJobInfo): List<ItemStack> {
        val merged = mutableListOf<ItemStack>()
        j.batches.asSequence()
            .filter { it.status != "COMPLETED" }
            .flatMap { it.required }
            .filterNot(ItemStack::isEmpty)
            .forEach { stack ->
                val existing = merged.firstOrNull { ItemStack.isSameItemSameComponents(it, stack) }
                if (existing != null) existing.grow(stack.count) else merged += stack.copy()
            }
        return merged
    }

    private fun shortCount(count: Int): String = if (count >= 1000) "${count / 1000}k" else count.toString()

    private fun computePanelHeight(): Int {
        val j = job ?: return 60
        var h = 22 // title bar
        h += BAR_H + 3 // progress
        h += LINE_H // stats
        h += LINE_H + 3 // bees

        if (j.reason != null) {
            val reason = if (j.reason.startsWith("cbbees."))
                Component.translatable(j.reason).string else j.reason
            val lines = font.splitter.splitLines(reason, PANEL_W - MARGIN * 2,
                net.minecraft.network.chat.Style.EMPTY)
            h += minOf(lines.size, 3) * LINE_H + 2
            if (lines.size > 3) h += LINE_H
        }

        val materials = materials(j).size
        if (materials > 0) {
            h += LINE_H
            h += minOf((materials + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW, MAX_SLOT_ROWS) * SLOT + 2
            if (materials > SLOTS_PER_ROW * MAX_SLOT_ROWS) h += LINE_H
        }

        h += 28 // cancel button
        return h
    }

    override fun tick() {
        super.tick()
        refreshTicks++
        if (refreshTicks >= 10) {
            refreshTicks = 0
            PacketDistributor.sendToServer(RequestPlayerJobsPacket())
        }
        val latest = ConstructionRenderer.getJobInfo(jobId)
        if (latest != job) refreshJob()
        if (latest == null && job != null) onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
