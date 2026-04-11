package de.devin.cbbees.content.schematics.client

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

/**
 * Tracks which custom Construction Planner tool is currently "equipped".
 *
 * Because Create's [com.simibubi.create.content.schematics.client.tools.ToolType]
 * is a Java enum we cannot extend, custom tools live outside the normal equip flow.
 * This state is checked by the SchematicHandler mouse-input mixin to intercept RMB.
 */
@OnlyIn(Dist.CLIENT)
object ConstructionToolState {
    enum class CustomTool { NONE, CONSTRUCT, UNSELECT, PROGRAM }

    @JvmStatic
    var activeTool = CustomTool.NONE
}
