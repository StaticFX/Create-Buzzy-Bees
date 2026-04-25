package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.network.CancelJobPacket
import de.devin.cbbees.network.RequestPlayerJobsPacket
import net.createmod.catnip.gui.AbstractSimiScreen
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
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

        private const val BG_PANEL = 0xCC2A2A2A.toInt()
        private const val BG_TITLE = 0xFF3A3A3A.toInt()
        private const val BORDER = 0xFF555555.toInt()
        private const val GOLD = 0xFFD4AA00.toInt()
        private const val WHITE = 0xFFFFFF
        private const val GRAY = 0x999999
        private const val RED = 0xFF5555
        private const val GREEN_BAR = 0xFF55AA55.toInt()
        private const val RED_BAR = 0xFFAA3333.toInt()
        private const val BAR_BG = 0xFF333333.toInt()
    }

    private var job: ClientJobInfo? = null
    private var refreshTicks = 0
    private var cancelButton: IconButton? = null

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

        graphics.fill(x, y, x + w, y + h, BG_PANEL)
        graphics.fill(x, y, x + w, y + 1, BORDER)
        graphics.fill(x, y + h - 1, x + w, y + h, BORDER)
        graphics.fill(x, y, x + 1, y + h, BORDER)
        graphics.fill(x + w - 1, y, x + w, y + h, BORDER)

        graphics.fill(x + 1, y + 1, x + w - 1, y + 18, BG_TITLE)

        val j = job
        if (j == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.cbbees.job_detail.not_found"),
                x + w / 2, y + 5, GRAY)
            return
        }

        val jobType = if (j.schematicPlacement != null) "Construction" else "Deconstruction"
        graphics.drawCenteredString(font, "$jobType #${j.name}", x + w / 2, y + 5, GOLD)

        val innerW = w - MARGIN * 2
        var ty = y + 22

        val pct = if (j.total == 0) 1f else j.completed.toFloat() / j.total
        val isStuck = j.reason != null
        graphics.fill(x + MARGIN, ty, x + MARGIN + innerW, ty + BAR_H, BAR_BG)
        val filled = (innerW * pct).toInt()
        graphics.fill(x + MARGIN, ty, x + MARGIN + filled, ty + BAR_H, if (isStuck) RED_BAR else GREEN_BAR)
        ty += BAR_H + 3

        graphics.drawString(font, "${j.completed}/${j.total} tasks  ${(pct * 100).toInt()}%",
            x + MARGIN, ty, WHITE, false)
        ty += LINE_H

        val activeBees = j.batches.flatMap { it.assignedBeeIds }.distinct().size
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

        val allRequired = j.batches
            .filter { it.status != "COMPLETED" }
            .flatMap { it.required }
            .filter { !it.isEmpty }
        if (allRequired.isNotEmpty()) {
            graphics.drawString(font, "Materials:", x + MARGIN, ty, GOLD, false)
            ty += LINE_H

            val aggregated = mutableMapOf<String, Int>()
            for (stack in allRequired) {
                val name = stack.hoverName.string
                aggregated[name] = (aggregated[name] ?: 0) + stack.count
            }

            var shown = 0
            for ((name, count) in aggregated) {
                if (shown >= 4) {
                    graphics.drawString(font, "  +${aggregated.size - 4} more...", x + MARGIN, ty, GRAY, false)
                    break
                }
                val text = "  ${count}x $name"
                val trimmed = if (font.width(text) > innerW)
                    font.plainSubstrByWidth(text, innerW - font.width("...")) + "..."
                else text
                graphics.drawString(font, trimmed, x + MARGIN, ty, WHITE, false)
                ty += LINE_H
                shown++
            }
        }
    }

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

        val required = j.batches.filter { it.status != "COMPLETED" }.flatMap { it.required }.filter { !it.isEmpty }
        if (required.isNotEmpty()) {
            val unique = required.map { it.hoverName.string }.distinct().size
            h += LINE_H // "Materials:"
            h += minOf(unique, 4) * LINE_H
            if (unique > 4) h += LINE_H
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
