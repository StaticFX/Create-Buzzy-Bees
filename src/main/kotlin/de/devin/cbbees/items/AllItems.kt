package de.devin.cbbees.items

import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem
import com.tterrag.registrate.providers.RegistrateRecipeProvider
import com.tterrag.registrate.util.entry.ItemEntry
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.bee.MechanicalBeeItem
import de.devin.cbbees.content.bee.MechanicalBumbleBeeItem
import de.devin.cbbees.content.deployer.ProgrammedSchematicItem
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.DeconstructionPlannerItem
import de.devin.cbbees.content.schematics.PickupPlannerItem
import de.devin.cbbees.content.upgrades.*
import de.devin.cbbees.items.AllItems.UPGRADE_TEMPLATE
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.Rarity


object AllItems {
    fun register() {}

    // Portable Beehive - wearable in Curios "back" slot
    val PORTABLE_BEEHIVE: ItemEntry<PortableBeehiveItem> = CreateBuzzyBeez.REGISTRATE
        .item("portable_beehive") { props ->
            PortableBeehiveItem(props)
        }.recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', AllItems.BRASS_INGOT.get())
                .define('V', de.devin.cbbees.blocks.AllBlocks.MECHANICAL_BEEHIVE.get())
                .define('B', Items.GLASS_BOTTLE.asItem())
                .define('C', AllBlocks.BRASS_CASING.asItem())
                .pattern("CVC")
                .pattern("WBW")
                .pattern(" W ")
                .unlockedBy("has_brass_casing", RegistrateRecipeProvider.has(AllBlocks.BRASS_CASING.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .model { _, _ -> } // Hand-written model in resources
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Mechanical Bee - goes in beehive/backpack bee slots (stackable, consumed on deploy)
    val MECHANICAL_BEE: ItemEntry<MechanicalBeeItem> = CreateBuzzyBeez.REGISTRATE
        .item("mechanical_bee") { props ->
            MechanicalBeeItem(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .properties {
            it.stacksTo(MechanicalBeeItem.MAX_STACK_SIZE)
                .rarity(Rarity.UNCOMMON)
        }
        .register()

    // Mechanical Bumble Bee - logistics transport bee
    val MECHANICAL_BUMBLE_BEE: ItemEntry<MechanicalBumbleBeeItem> = CreateBuzzyBeez.REGISTRATE
        .item("mechanical_bumble_bee") { props ->
            MechanicalBumbleBeeItem(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .properties {
            it.stacksTo(MechanicalBumbleBeeItem.MAX_STACK_SIZE)
                .rarity(Rarity.UNCOMMON)
        }
        .register()

    // Construction Planner - select and deploy schematics for bee construction
    val CONSTRUCTION_PLANNER: ItemEntry<ConstructionPlannerItem> = CreateBuzzyBeez.REGISTRATE
        .item("construction_planner") { props ->
            ConstructionPlannerItem(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('B', AllItems.BRASS_INGOT.get())
                .define('E', AllItems.ELECTRON_TUBE.get())
                .define('P', Items.PAPER)
                .define('H', Items.HONEYCOMB)
                .pattern(" E ")
                .pattern("BPB")
                .pattern(" H ")
                .unlockedBy("has_electron_tube", RegistrateRecipeProvider.has(AllItems.ELECTRON_TUBE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Pickup Planner - select areas for item collection by bumble bees
    val PICKUP_PLANNER: ItemEntry<PickupPlannerItem> = CreateBuzzyBeez.REGISTRATE
        .item("pickup_planner") { props -> PickupPlannerItem(props) }
        .model { _, _ -> }
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('B', AllItems.BRASS_INGOT.get())
                .define('E', AllItems.ELECTRON_TUBE.get())
                .define('P', Items.PAPER)
                .define('H', Items.HOPPER)
                .pattern(" E ")
                .pattern("BPB")
                .pattern(" H ")
                .unlockedBy("has_electron_tube", RegistrateRecipeProvider.has(AllItems.ELECTRON_TUBE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Deconstruction Planner - select areas for removal
    val DECONSTRUCTION_PLANNER: ItemEntry<DeconstructionPlannerItem> = CreateBuzzyBeez.REGISTRATE
        .item("deconstruction_planner") { props ->
            DeconstructionPlannerItem(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('B', AllItems.BRASS_INGOT.get())
                .define('E', AllItems.ELECTRON_TUBE.get())
                .define('P', Items.PAPER)
                .define('S', Items.SHEARS)
                .pattern(" E ")
                .pattern("BPB")
                .pattern(" S ")
                .unlockedBy("has_electron_tube", RegistrateRecipeProvider.has(AllItems.ELECTRON_TUBE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Programmed Schematic - stores a schematic program for automated deployment
    val PROGRAMMED_SCHEMATIC: ItemEntry<ProgrammedSchematicItem> = CreateBuzzyBeez.REGISTRATE
        .item("programmed_schematic") { props ->
            ProgrammedSchematicItem(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // ===== Backpack Upgrades =====

    val UPGRADE_TEMPLATE: ItemEntry<UpgradeTemplateItem> = CreateBuzzyBeez.REGISTRATE
        .item("upgrade_template") { props ->
            UpgradeTemplateItem(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', AllItems.ANDESITE_ALLOY.get())
                .define('V', Items.HONEYCOMB)
                .pattern(" W ")
                .pattern("WVW")
                .pattern(" V ")
                .unlockedBy("has_honey_comb", RegistrateRecipeProvider.has(Items.HONEYCOMB))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .register()

    // Rapid Wings - +25% bee speed (max 4)
    val RAPID_WINGS: ItemEntry<RapidWingsUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("rapid_wings") { props ->
            RapidWingsUpgrade(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', Items.SUGAR)
                .define('B', AllItems.PROPELLER.get())
                .pattern(" B ")
                .pattern("VWV")
                .pattern(" B ")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Swarm Intelligence - +2 concurrent bees (max 3)
    val SWARM_INTELLIGENCE: ItemEntry<SwarmIntelligenceUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("swarm_intelligence") { props ->
            SwarmIntelligenceUpgrade(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', AllItems.PRECISION_MECHANISM)
                .define('B', AllItems.ELECTRON_TUBE.get())
                .pattern(" V ")
                .pattern("BWB")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Honey Efficiency - Higher capacity and break speed
    val HONEY_EFFICIENCY: ItemEntry<HoneyEfficiencyUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("honey_efficiency") { props ->
            HoneyEfficiencyUpgrade(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', Items.HONEY_BOTTLE)
                .define('B', AllItems.PROPELLER.get())
                .pattern(" B ")
                .pattern("VWV")
                .pattern(" V ")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Soft Touch - Deconstruction preserves blocks
    val SOFT_TOUCH: ItemEntry<SoftTouchUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("soft_touch") { props ->
            SoftTouchUpgrade(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', Items.FEATHER)
                .define('B', AllItems.STURDY_SHEET)
                .pattern(" V ")
                .pattern("VWV")
                .pattern("BBB")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // Drop Items - Deconstruction bees drop items instead of picking them up
    val DROP_ITEMS: ItemEntry<DropItemsUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("drop_items") { props ->
            DropItemsUpgrade(props)
        }
        .model { _, _ -> } // Hand-written model in resources
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', Items.DROPPER)
                .define('B', AllItems.PROPELLER.get())
                .pattern(" B ")
                .pattern("VWV")
                .pattern(" B ")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Honey Tank - increases honey capacity
    val HONEY_TANK: ItemEntry<HoneyTankUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("honey_tank") { props ->
            HoneyTankUpgrade(props)
        }
        .model { _, _ -> }
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', Items.HONEY_BLOCK)
                .define('B', AllItems.COPPER_SHEET)
                .pattern("BVB")
                .pattern("VWV")
                .pattern("BVB")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Drone View - enables top-down drone camera
    val DRONE_VIEW: ItemEntry<DroneViewUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("drone_view") { props ->
            DroneViewUpgrade(props)
        }
        .model { _, _ -> }
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', Items.SPYGLASS)
                .define('B', Items.ENDER_PEARL)
                .pattern(" V ")
                .pattern("BWB")
                .pattern(" B ")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.RARE) }
        .register()

    // Drone Range - extends drone view radius
    val DRONE_RANGE: ItemEntry<DroneRangeUpgrade> = CreateBuzzyBeez.REGISTRATE
        .item("drone_range") { props ->
            DroneRangeUpgrade(props)
        }
        .model { _, _ -> }
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', UPGRADE_TEMPLATE.get())
                .define('V', Items.ENDER_EYE)
                .define('B', AllItems.COPPER_SHEET)
                .pattern(" V ")
                .pattern("BWB")
                .pattern(" B ")
                .unlockedBy("has_upgrade_base", RegistrateRecipeProvider.has(UPGRADE_TEMPLATE.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .properties { it.stacksTo(1).rarity(Rarity.UNCOMMON) }
        .register()

    // Mechanical Bee Chassis - output of sequenced assembly, glued into mechanical bee
    val MECHANICAL_BEE_CHASSIS: ItemEntry<Item> = CreateBuzzyBeez.REGISTRATE
        .item("mechanical_bee_chassis") { props -> Item(props) }
        .model { _, _ -> }
        .register()

    // Transitional item used during sequenced assembly
    val INCOMPLETE_MECHANICAL_BEE = CreateBuzzyBeez.REGISTRATE
        .item("incomplete_mechanical_bee") { props -> SequencedAssemblyItem(props) }
        .register()

    // Mechanical Bumble Bee Chassis - output of sequenced assembly, glued into mechanical bumble bee
    val MECHANICAL_BUMBLE_BEE_CHASSIS: ItemEntry<Item> = CreateBuzzyBeez.REGISTRATE
        .item("mechanical_bumble_bee_chassis") { props -> Item(props) }
        .model { _, _ -> }
        .register()

    // Transitional item used during bumble bee sequenced assembly
    val INCOMPLETE_MECHANICAL_BUMBLE_BEE = CreateBuzzyBeez.REGISTRATE
        .item("incomplete_mechanical_bumble_bee") { props -> SequencedAssemblyItem(props) }
        .register()

    val INCOMPLETE_MECHANICAL_BEEHIVE = CreateBuzzyBeez.REGISTRATE
        .item("incomplete_mechanical_beehive") { props -> SequencedAssemblyItem(props) }
        .register()

}
