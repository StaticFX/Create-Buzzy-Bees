package de.devin.cbbees.registry

import com.mojang.blaze3d.platform.InputConstants
import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.client.KeyMapping
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import org.lwjgl.glfw.GLFW

/**
 * Registry for all keybindings in the mod.
 */
object AllKeys {

    /**
     * Keybinding to start construction or deconstruction.
     * Default key: R
     */
    val START_ACTION: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.start_action",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Keybinding to stop all bee tasks.
     * Default key: BACKSPACE
     */
    val STOP_ACTION: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.stop_action",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_BACKSPACE,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Keybinding to open the full-screen schematic browser.
     * Default key: B
     */
    val OPEN_SCHEMATIC_BROWSER: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.open_schematic_browser",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Keybinding to rotate the schematic ghost preview.
     * Default key: COMMA
     */
    val ROTATE_PREVIEW: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.rotate_preview",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_COMMA,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Keybinding to mirror the schematic ghost preview.
     * Default key: PERIOD
     */
    val MIRROR_PREVIEW: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.mirror_preview",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_PERIOD,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Keybinding to program a schematic into a Programmed Schematic item.
     * Default key: P
     */
    val PROGRAM_ACTION: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.program_action",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_P,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Modifier key for schematic browsing — hold to scroll schematics in the HUD,
     * toggle deconstruction mode, etc. Rebindable in Controls settings.
     * Default key: LEFT ALT
     */
    val SCHEMATIC_MODIFIER: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.schematic_modifier",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Modifier key for free-aim selection and AABB resizing in area planners.
     * Default key: LEFT CTRL
     */
    val FREE_AIM: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.free_aim",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Keybinding to toggle drone view.
     * Default key: V
     */
    val DRONE_VIEW: KeyMapping = KeyMapping(
        "key.${CreateBuzzyBeez.ID}.drone_view",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.${CreateBuzzyBeez.ID}"
    )

    /**
     * Registers all keybindings with the game.
     * Called from RegisterKeyMappingsEvent.
     */
    fun register(event: RegisterKeyMappingsEvent) {
        event.register(START_ACTION)
        event.register(STOP_ACTION)
        event.register(OPEN_SCHEMATIC_BROWSER)
        event.register(ROTATE_PREVIEW)
        event.register(MIRROR_PREVIEW)
        event.register(DRONE_VIEW)
        event.register(PROGRAM_ACTION)
        event.register(SCHEMATIC_MODIFIER)
        event.register(FREE_AIM)
    }

}
