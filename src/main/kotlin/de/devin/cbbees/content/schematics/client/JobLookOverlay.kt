package de.devin.cbbees.content.schematics.client

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.equipment.goggles.GogglesItem
import com.simibubi.create.foundation.gui.RemovedGuiUtils
import com.simibubi.create.infrastructure.config.AllConfigs
import de.devin.cbbees.config.CBBeesClientConfig
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.gui.element.BoxElement
import net.minecraft.ChatFormatting
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sign

/** Shows a goggle-style panel with a job's progress while the player looks at its bounding box within reach. */
@ClientSide
object JobLookOverlay {

    private const val FADE_TICKS = 5f

    private var hovered: UUID? = null
    private var hoverTicks = 0

    fun tick() {
        val jobId = lookedAtJob()
        hoverTicks = if (jobId != null && jobId == hovered) hoverTicks + 1 else 0
        hovered = jobId
    }

    private fun lookedAtJob(): UUID? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        if (mc.screen != null || !CBBeesClientConfig.showConstructionGhosts.get()) return null

        val eye = player.getEyePosition(1f)
        val look = player.lookAngle
        val (jobId, distance) = ConstructionRenderer.findJobAtRayWithDist(eye, look, player.blockInteractionRange()) ?: return null

        val blocked = level.clip(ClipContext(eye, eye.add(look.scale(distance)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        if (blocked.type == HitResult.Type.BLOCK && blocked.location.distanceTo(eye) < distance) return null

        val target = mc.hitResult as? BlockHitResult
        if (target != null && target.type == HitResult.Type.BLOCK && GogglesItem.isWearingGoggles(player)
            && level.getBlockEntity(target.blockPos) is IHaveGoggleInformation
        ) return null

        return jobId
    }

    fun render(graphics: GuiGraphics, deltaTracker: DeltaTracker) {
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui) return
        val job = hovered?.let(ConstructionRenderer::getJobInfo) ?: return
        val lines = lines(job)

        val textWidth = lines.maxOf { mc.font.width(it) }
        val height = 8 + if (lines.size > 1) 2 + (lines.size - 1) * 10 else 0
        val cfg = AllConfigs.client()
        val x = minOf(graphics.guiWidth() / 2 + cfg.overlayOffsetX.get(), graphics.guiWidth() - textWidth - 20)
        val y = minOf(graphics.guiHeight() / 2 + cfg.overlayOffsetY.get(), graphics.guiHeight() - height - 20)

        val fade = Mth.clamp((hoverTicks + deltaTracker.getGameTimeDeltaPartialTick(false)) / FADE_TICKS, 0f, 1f)
        val background = BoxElement.COLOR_VANILLA_BACKGROUND.scaleAlpha(.75f * fade)
        val borderTop = BoxElement.COLOR_VANILLA_BORDER.first.copy().scaleAlpha(fade)
        val borderBottom = BoxElement.COLOR_VANILLA_BORDER.second.copy().scaleAlpha(fade)

        val pose = graphics.pose()
        pose.pushPose()
        pose.translate((1 - fade).toDouble().pow(3) * sign(cfg.overlayOffsetX.get() + .5) * 8, 0.0, 0.0)
        RemovedGuiUtils.drawHoveringText(
            graphics, lines, x, y, graphics.guiWidth(), graphics.guiHeight(), -1,
            background.rgb, borderTop.rgb, borderBottom.rgb, mc.font
        )
        pose.popPose()
    }

    private fun lines(job: ClientJobInfo): List<Component> = buildList {
        add(job.displayName().copy().withStyle(ChatFormatting.GOLD))
        val player = Minecraft.getInstance().player
        if (player != null && job.ownerId != null && !job.isOwnedBy(player.uuid))
            add(Component.translatable("cbbees.job_overlay.owner", job.ownerName ?: "?").withStyle(ChatFormatting.GRAY))
        val percent = if (job.total == 0) 100 else job.completed * 100 / job.total
        add(Component.translatable("cbbees.job_overlay.progress", job.completed, job.total, percent).withStyle(ChatFormatting.GRAY))
        add(Component.translatable("cbbees.job_overlay.bees", job.activeBees).withStyle(ChatFormatting.GRAY))
        job.reason?.let { reason ->
            val text = if (reason.startsWith("cbbees.")) Component.translatable(reason) else Component.translatable("cbbees.job_overlay.missing_materials")
            add(text.withStyle(ChatFormatting.RED))
        }
        add(Component.translatable("cbbees.job_overlay.details").withStyle(ChatFormatting.DARK_GRAY))
    }
}
