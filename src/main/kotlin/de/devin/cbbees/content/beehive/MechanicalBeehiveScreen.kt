package de.devin.cbbees.content.beehive

import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen
import com.simibubi.create.foundation.gui.widget.IconButton
import de.devin.cbbees.content.beehive.client.ClientJobCache
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.content.domain.job.HiveSnapshot
import de.devin.cbbees.content.domain.network.client.JobHighlightHandler
import de.devin.cbbees.network.CancelJobPacket
import de.devin.cbbees.network.RequestHiveJobsPacket
import net.createmod.catnip.lang.Lang
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor

class MechanicalBeehiveScreen(
    menu: MechanicalBeehiveMenu,
    playerInventory: Inventory,
    title: Component
) : AbstractSimiContainerScreen<MechanicalBeehiveMenu>(menu, playerInventory, title) {

    private var snapshot: HiveSnapshot? = null
    private var refreshTicks = 0

    override fun init() {
        setWindowSize(256, 180)
        super.init()
        // Ask server for the latest snapshot
        PacketDistributor.sendToServer(RequestHiveJobsPacket(menu.content.blockPos))
        refreshFromCache()
    }

    private fun refreshFromCache() {
        snapshot = ClientJobCache.get(menu.content.blockPos)
        clearWidgets()

        val x = leftPos
        val y = topPos

        // Refresh Button
        addRenderableWidget(IconButton(x + imageWidth - 25, y + 20, AllIcons.I_REFRESH).apply {
            setToolTip(Component.literal("Manual Refresh"))
            withCallback<IconButton>(Runnable {
                PacketDistributor.sendToServer(RequestHiveJobsPacket(menu.content.blockPos))
            })
        })

        val snapshot = snapshot ?: return
        var rowY = topPos + 40
        val startX = leftPos + 12

        snapshot.jobs.forEach { job ->
            addRenderableWidget(JobRow(startX, rowY, job).apply { setRowWidth(imageWidth - 24) })
            rowY += 26
        }
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos

        // Main Background (Darker Create gray)
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF707070.toInt())
        guiGraphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFFC6C6C6.toInt())

        // Title & Network Stats (on top of FILTER)
        guiGraphics.drawString(font, title, x + 10, y + 6, 0x592424, false)

        snapshot?.networkInfo?.let { ni ->
            val netName = "Network: ${ni.name}"
            guiGraphics.drawString(font, netName, x + 130, y + 6, 0x592424, false)

            val beeStats = "Bees: ${ni.activeBees}/${ni.maxBees} Active, ${ni.storedBees} Stored"
            guiGraphics.drawString(font, beeStats, x + 10, y + 24, 0x404040, false)
        }

        // Terminal area background
        guiGraphics.fill(x + 10, y + 38, x + imageWidth - 10, y + imageHeight - 10, 0xCC000000.toInt())

        if (snapshot?.jobs?.isEmpty() == true) {
            guiGraphics.drawCenteredString(
                font,
                "No ongoing jobs found in network",
                x + imageWidth / 2,
                y + 80,
                0x909090
            )
        }
    }

    override fun containerTick() {
        super.containerTick()
        refreshTicks++
        if (refreshTicks >= 10) {
            refreshTicks = 0
            PacketDistributor.sendToServer(RequestHiveJobsPacket(menu.content.blockPos))
        }
        val latest = ClientJobCache.get(menu.content.blockPos)
        if (latest != snapshot) refreshFromCache()
    }

    inner class JobRow(
        x: Int,
        y: Int,
        private val job: ClientJobInfo
    ) : IconButton(x, y, AllIcons.I_NONE) {
        private val cancelBtn = IconButton(x + 215, y - 2, AllIcons.I_TRASH)
        private val hiliteBtn = IconButton(x + 195, y - 2, AllIcons.I_CONFIRM)
        private var w = 230

        init {
            cancelBtn.withCallback<IconButton>(Runnable {
                PacketDistributor.sendToServer(CancelJobPacket(job.jobId))
            })
            hiliteBtn.withCallback<IconButton>(Runnable {
                val beeIds = job.batches.flatMap { it.assignedBeeIds }.distinct()
                menu.content.networkId?.let { JobHighlightHandler.toggle(it, job.jobId, beeIds, true) }
            })
            addRenderableWidget(cancelBtn)
            addRenderableWidget(hiliteBtn)
        }

        fun setRowWidth(width: Int) {
            this.w = width
        }

        override fun renderWidget(gg: GuiGraphics, mouseX: Int, mouseY: Int, pt: Float) {
            // Job Name - Terminal Green
            val name = "> Job ${job.name}"
            gg.drawString(font, name, x + 4, y - 2, 0xFF44FF44.toInt(), false)

            // Progress Bar
            val pct = if (job.total == 0) 1f else job.completed.toFloat() / job.total
            val barW = w - 100
            val barX = x + 10
            val barY = y + 10

            // Bar border
            gg.fill(barX - 1, barY - 1, barX + barW + 1, barY + 5, 0xFFAAAAAA.toInt())
            gg.fill(barX, barY, barX + barW, barY + 4, 0xFF000000.toInt())
            // Bar fill (Terminal Green)
            val filled = (barW * pct).toInt()
            gg.fill(barX, barY, barX + filled, barY + 4, 0xFF00FF00.toInt())

            // Progress Text
            val progressText = "[${job.completed}/${job.total}]"
            gg.drawString(font, progressText, barX + barW + 8, barY - 2, 0xFF44FF44.toInt(), false)

            // Status & Reason
            val statusColor = if (job.reason != null) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
            val statusText = if (job.reason != null) "! STUCK: ${job.reason}" else "  Status: ${job.status}"
            gg.drawString(font, statusText, x + 4, y + 15, statusColor, false)

            cancelBtn.render(gg, mouseX, mouseY, pt)
            hiliteBtn.render(gg, mouseX, mouseY, pt)
        }
    }
}
