package de.devin.cbbees.config

import net.neoforged.neoforge.common.ModConfigSpec

object CBBeesClientConfig {
    val SPEC: ModConfigSpec

    val showConstructionGhosts: ModConfigSpec.BooleanValue
    val showSchematicPreview: ModConfigSpec.BooleanValue
    val showBeehiveRange: ModConfigSpec.BooleanValue
    val showBeeTargetLines: ModConfigSpec.BooleanValue
    val ghostBlockOpacity: ModConfigSpec.DoubleValue
    val renderGhostBlockEntities: ModConfigSpec.BooleanValue
    val maxGhostBlockEntities: ModConfigSpec.IntValue
    val beeShadowDistance: ModConfigSpec.IntValue
    val beeItemRenderDistance: ModConfigSpec.IntValue

    init {
        val builder = ModConfigSpec.Builder()

        builder.comment("Rendering & Performance Toggles")
            .push("rendering")

        showConstructionGhosts = builder
            .comment("Show ghost blocks for active construction jobs")
            .define("showConstructionGhosts", true)

        showSchematicPreview = builder
            .comment("Show ghost block preview while browsing schematics")
            .define("showSchematicPreview", true)

        showBeehiveRange = builder
            .comment("Show beehive range overlay when looking at a hive")
            .define("showBeehiveRange", true)

        showBeeTargetLines = builder
            .comment("Show bee target lines when wearing goggles")
            .define("showBeeTargetLines", true)

        ghostBlockOpacity = builder
            .comment("Opacity for all ghost block rendering (0.05 = nearly invisible, 1.0 = fully opaque)")
            .defineInRange("ghostBlockOpacity", 0.5, 0.05, 1.0)

        renderGhostBlockEntities = builder
            .comment("Render block entities (chests, signs, gearboxes, etc.) in ghost previews. Disable for better performance on large schematics.")
            .define("renderGhostBlockEntities", false)

        maxGhostBlockEntities = builder
            .comment("Maximum number of block entities to render per ghost preview. Lower values improve performance.")
            .defineInRange("maxGhostBlockEntities", 64, 1, 1024)

        beeShadowDistance = builder
            .comment("Maximum distance (blocks) at which bee shadows are rendered. Lower for better FPS with many bees.")
            .defineInRange("beeShadowDistance", 16, 4, 32)

        beeItemRenderDistance = builder
            .comment("Maximum distance (blocks) at which carried items on bumble bees are rendered.")
            .defineInRange("beeItemRenderDistance", 24, 8, 64)

        builder.pop()

        SPEC = builder.build()
    }

    private fun <T> safeGet(value: ModConfigSpec.ConfigValue<T>, fallback: T): T {
        return runCatching { value.get() }.getOrDefault(fallback)
    }

    fun showConstructionGhostsSafe(): Boolean = safeGet(showConstructionGhosts, true)
    fun showSchematicPreviewSafe(): Boolean = safeGet(showSchematicPreview, true)
    fun showBeehiveRangeSafe(): Boolean = safeGet(showBeehiveRange, true)
    fun showBeeTargetLinesSafe(): Boolean = safeGet(showBeeTargetLines, true)
    fun ghostBlockOpacitySafe(): Double = safeGet(ghostBlockOpacity, 0.5)
    fun renderGhostBlockEntitiesSafe(): Boolean = safeGet(renderGhostBlockEntities, false)
    fun maxGhostBlockEntitiesSafe(): Int = safeGet(maxGhostBlockEntities, 64)
    fun beeShadowDistanceSafe(): Int = safeGet(beeShadowDistance, 16)
    fun beeItemRenderDistanceSafe(): Int = safeGet(beeItemRenderDistance, 24)

}
