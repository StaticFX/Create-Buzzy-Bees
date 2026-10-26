package de.devin.cbbees.content.logistics.ports

import de.devin.cbbees.CreateBuzzyBeez
import dev.engine_room.flywheel.lib.model.baked.PartialModel

object PortPartialModels {

    val LOGISTICS_BUSY_GLOW: PartialModel = partial("logistics_port", "glow_busy")
    val CARGO_BUSY_GLOW: PartialModel = partial("cargo_port", "glow_busy")
    val DOCK_BUSY_GLOW: PartialModel = partial("dock_port", "glow_busy")
    val CRAFTING_BULB: PartialModel = partial("logistics_port", "crafting_bulb")
    val CRAFTING_GLOW: PartialModel = partial("logistics_port", "crafting_glow")

    private fun partial(port: String, name: String) = PartialModel.of(CreateBuzzyBeez.asResource("block/$port/$name"))

    fun init() {}
}
