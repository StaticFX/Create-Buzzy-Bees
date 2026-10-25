package de.devin.cbbees.content.planner.client

import com.simibubi.create.foundation.gui.AllIcons
import de.devin.cbbees.config.CBBeesClientConfig
import de.devin.cbbees.network.StopTasksPacket
import de.devin.cbbees.registry.AllKeys
import net.minecraft.client.DeltaTracker
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

class AreaTool(
    key: String,
    icon: AllIcons,
    color: Int,
    val shortcut: KeyMapping? = null,
    val apply: (BlockPos, BlockPos) -> Unit,
) : PlannerTool(key, icon, color)

class AreaPlannerController(
    tools: List<AreaTool>,
    private val handles: (ItemStack) -> Boolean = { true },
) : PlannerController {

    private val toolbar = PlannerToolbar(tools)
    private val shortcuts = tools.filter { it.shortcut != null }

    val selectedTool: AreaTool get() = toolbar.selected

    override fun handles(stack: ItemStack) = handles.invoke(stack)

    override fun tick() {
        toolbar.update()
        AreaSelector.tick(toolbar.selected.color)
    }

    override fun onDeactivated(next: PlannerController?) {
        toolbar.reset()
        if (next !is AreaPlannerController || !CBBeesClientConfig.keepSelectionBetweenPlanners.get()) AreaSelection.discard()
    }

    override fun onScroll(delta: Double) = toolbar.onScroll(delta) || AreaSelector.onScroll(delta)

    override fun onMouseButton(button: Int, action: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || action != GLFW.GLFW_PRESS) return false
        val sneaking = Minecraft.getInstance().player?.isShiftKeyDown == true
        if (AreaSelection.isComplete() && !sneaking) return apply(toolbar.selected)
        return AreaSelector.onRightClick()
    }

    override fun onKey(key: Int, action: Int) {
        if (toolbar.onToolMenuKey(key, action) || action != GLFW.GLFW_PRESS) return
        when {
            AllKeys.START_ACTION.matches(key, 0) -> apply(toolbar.selected)
            AllKeys.STOP_ACTION.matches(key, 0) -> PacketDistributor.sendToServer(StopTasksPacket.INSTANCE)
            else -> shortcuts.firstOrNull { it.shortcut!!.matches(key, 0) }?.let(::apply)
        }
    }

    override fun renderHud(graphics: GuiGraphics, deltaTracker: DeltaTracker) {
        toolbar.render(graphics, listOf(areaInfo(), controls()))
    }

    private fun apply(tool: AreaTool): Boolean {
        val first = AreaSelection.firstPos ?: return false
        val second = AreaSelection.secondPos ?: return false
        tool.apply(first, second)
        AreaSelection.discard()
        return true
    }

    private fun areaInfo(): Component {
        val first = AreaSelection.firstPos ?: return Component.translatable("gui.cbbees.area.no_corners")
        val second = AreaSelection.secondPos ?: return Component.translatable("cbbees.area.first_pos")
        return Component.translatable("cbbees.area.dimensions", abs(first.x - second.x) + 1, abs(first.y - second.y) + 1, abs(first.z - second.z) + 1)
    }

    private fun controls(): Component =
        if (AreaSelection.isComplete()) Component.translatable("gui.cbbees.area.hint_ready", AllKeys.START_ACTION.translatedKeyMessage)
        else Component.translatable("gui.cbbees.area.hint_select")
}
