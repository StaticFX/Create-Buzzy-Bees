package de.devin.cbbees.config

import net.neoforged.neoforge.common.ModConfigSpec

object CBBeesClientConfig {
    val SPEC: ModConfigSpec

    val showConstructionGhosts: ModConfigSpec.BooleanValue
    val showSchematicPreview: ModConfigSpec.BooleanValue
    val showBeehiveRange: ModConfigSpec.BooleanValue
    val ghostBlockOpacity: ModConfigSpec.DoubleValue
    val renderGhostBlockEntities: ModConfigSpec.BooleanValue
    val maxGhostBlockEntities: ModConfigSpec.IntValue
    val beeRenderDistance: ModConfigSpec.IntValue
    val beeItemRenderDistance: ModConfigSpec.IntValue
    val maxRenderedBees: ModConfigSpec.IntValue
    val keepSelectionBetweenPlanners: ModConfigSpec.BooleanValue

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

        ghostBlockOpacity = builder
            .comment("Opacity for all ghost block rendering (0.05 = nearly invisible, 1.0 = fully opaque)")
            .defineInRange("ghostBlockOpacity", 0.5, 0.05, 1.0)

        renderGhostBlockEntities = builder
            .comment("Render block entities (chests, signs, gearboxes, etc.) in ghost previews. Disable for better performance on large schematics.")
            .define("renderGhostBlockEntities", false)

        maxGhostBlockEntities = builder
            .comment("Maximum number of block entities to render per ghost preview, including belts. Lower values improve performance.")
            .defineInRange("maxGhostBlockEntities", 256, 1, 1024)

        beeRenderDistance = builder
            .comment("Maximum distance (blocks) at which bees are rendered. Lower for better FPS with many bees.")
            .defineInRange("beeRenderDistance", 64, 16, 256)

        beeItemRenderDistance = builder
            .comment("Maximum distance (blocks) at which carried items on bumble bees are rendered.")
            .defineInRange("beeItemRenderDistance", 24, 8, 64)

        maxRenderedBees = builder
            .comment("Maximum number of bees rendered at once. When more are in view, only the nearest ones are drawn. Lower for better FPS with many bees.")
            .defineInRange("maxRenderedBees", 512, 0, 8192)

        builder.pop()

        builder.comment("Planners").push("planners")

        keepSelectionBetweenPlanners = builder
            .comment("Keep the area selection when you switch to another item or planner and back")
            .define("keepSelectionBetweenPlanners", true)

        builder.pop()

        SPEC = builder.build()
    }
}
