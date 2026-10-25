package de.devin.cbbees.content.planner.client

import de.devin.cbbees.content.planner.AllPlanners
import de.devin.cbbees.content.planner.Planner
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent

@ClientSide
object PlannerClientEvents {

    private val controllers: Map<Planner, PlannerController> = mapOf(
        AllPlanners.DECONSTRUCTION to AreaPlanners.DECONSTRUCTION,
        AllPlanners.PICKUP to AreaPlanners.PICKUP,
        AllPlanners.COPY to AreaPlanners.COPY,
    )

    private var current: PlannerController? = null

    private fun held(): PlannerController? {
        val stack = Minecraft.getInstance().player?.mainHandItem ?: return null
        val planner = AllPlanners.of(stack) ?: return null
        return controllers[planner]?.takeIf { it.handles(stack) }
    }

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val next = held()
        if (next !== current) {
            current?.onDeactivated(next)
            current = next
        }
        current?.tick()
    }

    @SubscribeEvent
    @JvmStatic
    fun onMouseInput(event: InputEvent.MouseButton.Pre) {
        if (current?.onMouseButton(event.button, event.action) == true) event.isCanceled = true
    }

    @SubscribeEvent
    @JvmStatic
    fun onMouseScroll(event: InputEvent.MouseScrollingEvent) {
        if (current?.onScroll(event.scrollDeltaY) == true) event.isCanceled = true
    }

    @SubscribeEvent
    @JvmStatic
    fun onKeyInput(event: InputEvent.Key) {
        current?.onKey(event.key, event.action)
    }

    fun renderHud(graphics: GuiGraphics, deltaTracker: DeltaTracker) {
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.screen != null) return
        current?.renderHud(graphics, deltaTracker)
    }
}
