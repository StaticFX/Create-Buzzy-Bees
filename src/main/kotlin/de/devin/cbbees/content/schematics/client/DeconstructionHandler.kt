package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.StartDeconstructionPacket
import net.minecraft.client.Minecraft

/**
 * Client-side handler for the Deconstruction Planner tool.
 * Delegates all selection logic to [AreaSelectionHandler].
 */
object DeconstructionHandler {

    private val handler = AreaSelectionHandler(
        keyPrefix = "cbbees.deconstruction",
        isActive = ::isActive,
        createPacket = { pos1, pos2 -> StartDeconstructionPacket(pos1, pos2) },
        createProgram = { pos1, pos2 -> SchematicProgram.Deconstruction(pos1, pos2) },
    )

    fun mouseScrolled(delta: Double) = handler.onScroll(delta)
    fun onMouseInput(button: Int, pressed: Boolean) = handler.onMouseInput(button, pressed)
    fun onKeyInput(key: Int, pressed: Boolean) = handler.onKeyInput(key, pressed)
    fun tick() = handler.tick()
    fun discard() = handler.discard()

    fun isActive(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return AllItems.DECONSTRUCTION_PLANNER.isIn(player.mainHandItem)
    }
}
