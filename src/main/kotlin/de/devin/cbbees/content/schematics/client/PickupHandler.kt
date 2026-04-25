package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.StartPickupPacket
import net.minecraft.client.Minecraft

/**
 * Client-side handler for the Pickup Planner tool.
 * Delegates all selection logic to [AreaSelectionHandler].
 */
object PickupHandler {

    private val handler = AreaSelectionHandler(
        keyPrefix = "cbbees.pickup",
        isActive = ::isActive,
        createPacket = { pos1, pos2 -> StartPickupPacket(pos1, pos2) },
        createProgram = { pos1, pos2 -> SchematicProgram.Pickup(pos1, pos2) },
    )

    fun onScroll(delta: Double) = handler.onScroll(delta)
    fun onMouseInput(button: Int, pressed: Boolean) = handler.onMouseInput(button, pressed)
    fun onKeyInput(key: Int, pressed: Boolean) = handler.onKeyInput(key, pressed)
    fun tick() = handler.tick()
    fun discard() = handler.discard()

    fun isActive(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return AllItems.PICKUP_PLANNER.isIn(player.mainHandItem)
    }
}
