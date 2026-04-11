package de.devin.cbbees.content.domain.job.client

import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.network.JobProgressPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.ToastComponent
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.util.UUID

/**
 * Vanilla-style toast that displays the live calculation progress of a single job.
 *
 * Uses [jobId] as the toast token, so [JobProgressClient] can look up and mutate
 * an existing toast in-place via [ToastComponent.getToast] instead of replacing it
 * each tick. Multiple concurrent jobs each get their own toast and stack in the
 * vanilla toast queue automatically.
 *
 * @see JobProgressClient
 * @see JobCalculationProgress
 */
@OnlyIn(Dist.CLIENT)
class JobProgressToast(private val jobId: UUID) : Toast {

    private var labelKey: String = ""
    private var phase: JobCalculationProgress.Phase = JobCalculationProgress.Phase.STARTED
    private var processedBlocks: Int = 0
    private var expectedBlocks: Int = 1
    private var resultKey: String = ""
    private var resultCount: Int = 0

    /** Wall-clock millis when phase entered a terminal state. Drives fade-out timing. */
    private var terminalAtMillis: Long = 0L

    override fun width(): Int = WIDTH
    override fun height(): Int = HEIGHT
    override fun getToken(): Any = jobId

    fun applyUpdate(packet: JobProgressPacket) {
        labelKey = packet.labelKey
        processedBlocks = packet.processedBlocks
        expectedBlocks = packet.expectedBlocks.coerceAtLeast(1)
        resultKey = packet.resultKey
        resultCount = packet.resultCount
        val previousPhase = phase
        phase = packet.phase
        if (phase != previousPhase &&
            (phase == JobCalculationProgress.Phase.COMPLETED || phase == JobCalculationProgress.Phase.FAILED)
        ) {
            terminalAtMillis = System.currentTimeMillis()
        }
    }

    override fun render(
        graphics: GuiGraphics,
        toastComponent: ToastComponent,
        timeSinceLastVisible: Long,
    ): Toast.Visibility {
        // Background — vanilla system toast sprite (160x32). Our width is 160 to match.
        graphics.blitSprite(BACKGROUND_SPRITE, 0, 0, WIDTH, HEIGHT)

        val font = Minecraft.getInstance().font

        if (phase == JobCalculationProgress.Phase.COMPLETED && resultKey.isNotEmpty()) {
            // Row 1: checkmark + result label
            val msg = Component.literal("§a✓ §f").append(Component.translatable(resultKey, resultCount))
            graphics.drawString(font, msg, 8, 9, TITLE_COLOR, false)

            // Row 2: count detail
            val unit = if (labelKey.contains("item") || labelKey.contains("pickup")) "items" else "blocks"
            val detail = Component.literal("${formatBlocks(expectedBlocks)} $unit")
            drawScaled(graphics, font, detail, 8, 19, DETAIL_COLOR, SMALL_SCALE)
        } else {
            // Row 1: title
            val title: Component = Component.translatable(labelKey)
            graphics.drawString(font, title, 8, 7, TITLE_COLOR, false)

            // Row 2: block counts
            val pct = (processedBlocks.toLong() * 100 / expectedBlocks.coerceAtLeast(1)).toInt().coerceIn(0, 100)
            val counts = Component.literal("${formatBlocks(processedBlocks)} / ${formatBlocks(expectedBlocks)} ($pct%)")
            drawScaled(graphics, font, counts, 8, 19, DETAIL_COLOR, SMALL_SCALE)
        }

        // Visibility logic — keep showing until calculation finishes + fade delay,
        // or fail-safe at the absolute timeout in case the server stops sending updates.
        return when (phase) {
            JobCalculationProgress.Phase.STARTED, JobCalculationProgress.Phase.IN_PROGRESS ->
                if (timeSinceLastVisible >= STALE_TIMEOUT_MS) Toast.Visibility.HIDE else Toast.Visibility.SHOW

            JobCalculationProgress.Phase.COMPLETED, JobCalculationProgress.Phase.FAILED -> {
                val elapsed = System.currentTimeMillis() - terminalAtMillis
                if (elapsed >= TERMINAL_FADE_MS) Toast.Visibility.HIDE else Toast.Visibility.SHOW
            }
        }
    }

    private fun formatBlocks(n: Int): String = "%,d".format(n)

    private fun drawScaled(
        graphics: GuiGraphics,
        font: Font,
        text: Component,
        x: Int,
        y: Int,
        color: Int,
        scale: Float
    ) {
        graphics.pose().pushPose()
        graphics.pose().scale(scale, scale, 1f)
        graphics.drawString(font, text, (x / scale).toInt(), (y / scale).toInt(), color, false)
        graphics.pose().popPose()
    }

    companion object {
        private const val WIDTH = 160
        private const val HEIGHT = 32
        private const val BAR_WIDTH = 144
        private const val BAR_HEIGHT = 4

        private const val SMALL_SCALE = 0.8f

        private const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        private const val DETAIL_COLOR = 0xFFAAAAAA.toInt()
        private const val BAR_BG_COLOR = 0xFF333333.toInt()
        private const val BAR_FILL_ACTIVE = 0xFFFFCC00.toInt() // bee yellow
        private const val BAR_FILL_DONE = 0xFF55DD55.toInt()
        private const val BAR_FILL_FAIL = 0xFFDD5555.toInt()

        /** Auto-hide if no progress packets arrive for this long (server hung / left dim). */
        private const val STALE_TIMEOUT_MS = 60_000L

        /** How long to keep the COMPLETED/FAILED toast visible before fading out. */
        private const val TERMINAL_FADE_MS = 4_000L

        private val BACKGROUND_SPRITE: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath("cbbees", "toast/background")
    }
}
