package de.devin.cbbees.content.planner.client

import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.foundation.gui.RemovedGuiUtils
import com.simibubi.create.foundation.utility.RaycastHelper
import com.simibubi.create.infrastructure.config.AllConfigs
import de.devin.cbbees.content.domain.built.client.ClientBuiltSchematic
import de.devin.cbbees.content.domain.built.client.ClientBuiltSchematics
import de.devin.cbbees.icons.AllCCRIcons
import de.devin.cbbees.network.RemoveBuildPacket
import net.createmod.catnip.gui.element.BoxElement
import net.createmod.catnip.outliner.Outliner
import net.createmod.catnip.theme.Color
import net.minecraft.ChatFormatting
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.neoforged.neoforge.network.PacketDistributor

class BuildSnapTool : AreaPlannerTool("remove_build", AllCCRIcons.DECONSTRUCT_SELECTION, 0xc56868) {

    private var target: ClientBuiltSchematic? = null

    private val ownsTarget: Boolean
        get() = Minecraft.getInstance().player?.let { player -> target?.isOwnedBy(player.uuid) } == true

    override fun tick() {
        target = lookedAtBuild()
        highlightRemovableBuilds()
        val build = target ?: return
        Outliner.getInstance()
            .chaseAABB(OUTLINE_SLOT, AABB.of(build.bounds))
            .colored(if (ownsTarget) color else FOREIGN_COLOR)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(1 / 16f)
    }

    private fun highlightRemovableBuilds() {
        val player = Minecraft.getInstance().player ?: return
        ClientBuiltSchematics.builds
            .filter { it.isOwnedBy(player.uuid) && it != target }
            .forEach {
                Outliner.getInstance()
                    .chaseAABB("$OUTLINE_SLOT:${it.id}", AABB.of(it.bounds))
                    .colored(REMOVABLE_COLOR)
                    .lineWidth(1 / 16f)
            }
    }

    private fun lookedAtBuild(): ClientBuiltSchematic? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val hit = RaycastHelper.rayTraceRange(player.level(), player, TARGET_RANGE) ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        return ClientBuiltSchematics.at(hit.blockPos)
    }

    override fun onRightClick(sneaking: Boolean) = start()

    override fun start(): Boolean {
        val build = target ?: return false
        if (!ownsTarget) {
            Minecraft.getInstance().player?.displayClientMessage(Component.translatable("cbbees.remove_build.not_owner"), true)
            return true
        }
        PacketDistributor.sendToServer(RemoveBuildPacket(build.id))
        return true
    }

    override fun hints(): List<Component> = listOf(Component.translatable("gui.cbbees.remove_build.hint"))

    override fun renderHud(graphics: GuiGraphics, deltaTracker: DeltaTracker) {
        val build = target ?: return
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.screen != null) return

        val lines = buildList {
            add(Component.literal(build.name).withStyle(ChatFormatting.GOLD))
            if (ownsTarget) add(Component.translatable("gui.cbbees.remove_build.remove").withStyle(ChatFormatting.GRAY))
            else add(Component.translatable("cbbees.job_overlay.owner", build.ownerName ?: "?").withStyle(ChatFormatting.GRAY))
        }
        val cfg = AllConfigs.client()
        val textWidth = lines.maxOf { mc.font.width(it) }
        val x = minOf(graphics.guiWidth() / 2 + cfg.overlayOffsetX.get(), graphics.guiWidth() - textWidth - 20)
        val y = minOf(graphics.guiHeight() / 2 + cfg.overlayOffsetY.get(), graphics.guiHeight() - 40)
        RemovedGuiUtils.drawHoveringText(
            graphics, lines, x, y, graphics.guiWidth(), graphics.guiHeight(), -1,
            BoxElement.COLOR_VANILLA_BACKGROUND.scaleAlpha(.75f).rgb,
            BoxElement.COLOR_VANILLA_BORDER.first.rgb, BoxElement.COLOR_VANILLA_BORDER.second.rgb, mc.font
        )
    }

    private companion object {
        const val OUTLINE_SLOT = "remove_build"
        const val FOREIGN_COLOR = 0x888888
        const val TARGET_RANGE = 75.0
        val REMOVABLE_COLOR = Color(0x70E89090, true)
    }
}
