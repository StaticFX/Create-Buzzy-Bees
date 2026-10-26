package de.devin.cbbees.blocks

import com.simibubi.create.AllItems
import com.simibubi.create.AllBlocks as CreateAllBlocks
import com.simibubi.create.api.stress.BlockStressValues
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem
import com.simibubi.create.foundation.data.SharedProperties
import com.tterrag.registrate.providers.RegistrateRecipeProvider
import com.tterrag.registrate.util.entry.BlockEntry
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.apiary.MechanicalApiaryBlock
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlock
import de.devin.cbbees.content.deployer.SchematicDeployerBlock
import de.devin.cbbees.content.logistics.dock.DockPortBlock
import de.devin.cbbees.content.logistics.ports.LogisticPortBlock
import de.devin.cbbees.content.logistics.ports.PortBlock
import de.devin.cbbees.content.logistics.transport.TransportPortBlock
import de.devin.cbbees.content.logistics.waggle.WagglePortBlock
import de.devin.cbbees.datagen.PortBlockStates
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.tags.BlockTags
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

object AllBlocks {
    fun register() {}

    val MECHANICAL_BEEHIVE: BlockEntry<MechanicalBeehiveBlock> = CreateBuzzyBeez.REGISTRATE.block(
        "mechanical_beehive"
    ) { MechanicalBeehiveBlock(it) }
        .initialProperties { Blocks.IRON_BLOCK }
        .properties { p -> p.noOcclusion() }
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate { c, p ->
            p.simpleBlock(
                c.getEntry(),
                p.models().getExistingFile(p.modLoc("block/mechanical_beehive/block"))
            )
        }
        .onRegister { block -> BlockStressValues.IMPACTS.register(block) { 128.0 } }
        .item()
        .model { c, p -> p.withExistingParent(c.name, p.modLoc("block/mechanical_beehive/item")) }
        .build()
        .register()

    val LOGISTICS_PORT = CreateBuzzyBeez.REGISTRATE.block("logistics_port", ::LogisticPortBlock)
        .initialProperties(SharedProperties::softMetal)
        .properties { it.noOcclusion() } // Important: so it doesn't "cull" the chest behind it
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate { c, p -> PortBlockStates.generate(c, p, PortBlock.PORT_TYPE) }
        .item()
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get(), 2)
                .define('W', AllItems.TRANSMITTER.get())
                .define('V', CreateAllBlocks.BRASS_FUNNEL.get())
                .define('B', CreateAllBlocks.BRASS_CASING.get())
                .pattern("W")
                .pattern("V")
                .pattern("B")
                .unlockedBy("has_brass_casing", RegistrateRecipeProvider.has(CreateAllBlocks.BRASS_CASING.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .model { _, _ -> } // Hand-written item model in resources
        .build()
        .register()

    val SCHEMATIC_DEPLOYER: BlockEntry<SchematicDeployerBlock> = CreateBuzzyBeez.REGISTRATE
        .block("schematic_deployer") { SchematicDeployerBlock(it) }
        .initialProperties { Blocks.IRON_BLOCK }
        .properties { p -> p.noOcclusion() }
        .blockstate { _, _ -> } // Hand-written blockstate in resources
        .item()
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, c.get(), 2)
                .define('B', AllItems.BRASS_INGOT.get())
                .define('E', AllItems.ELECTRON_TUBE.get())
                .define('C', CreateAllBlocks.BRASS_CASING.get())
                .define('R', net.minecraft.world.item.Items.REDSTONE)
                .pattern("BEB")
                .pattern("RCR")
                .pattern("BBB")
                .unlockedBy("has_brass_casing", RegistrateRecipeProvider.has(CreateAllBlocks.BRASS_CASING.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .model { _, _ -> } // Hand-written item model in resources
        .build()
        .register()

    val CARGO_PORT = CreateBuzzyBeez.REGISTRATE.block("cargo_port", ::TransportPortBlock)
        .initialProperties(SharedProperties::softMetal)
        .properties { it.noOcclusion() }
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate { c, p -> PortBlockStates.generate(c, p, TransportPortBlock.TRANSPORT_MODE) }
        .item()
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get(), 2)
                .define('W', AllItems.TRANSMITTER.get())
                .define('V', CreateAllBlocks.ITEM_VAULT.get())
                .define('B', CreateAllBlocks.COPPER_CASING.get())
                .pattern("W")
                .pattern("V")
                .pattern("B")
                .unlockedBy("has_brass_casing", RegistrateRecipeProvider.has(CreateAllBlocks.BRASS_CASING.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .model { _, _ -> } // Hand-written item model in resources
        .build()
        .register()

    val DOCK_PORT = CreateBuzzyBeez.REGISTRATE.block("dock_port", ::DockPortBlock)
        .initialProperties(SharedProperties::softMetal)
        .properties { it.noOcclusion() }
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate { c, p -> PortBlockStates.generate(c, p, PortBlock.PORT_TYPE) }
        .item()
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get(), 2)
                .define('W', AllItems.TRANSMITTER.get())
                .define('V', CreateAllBlocks.FLUID_TANK.get())
                .define('B', CreateAllBlocks.COPPER_CASING.get())
                .pattern("W")
                .pattern("V")
                .pattern("B")
                .unlockedBy("has_copper_casing", RegistrateRecipeProvider.has(CreateAllBlocks.COPPER_CASING.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .model { _, _ -> } // Hand-written item model in resources
        .build()
        .register()

    val WAGGLE_PORT: BlockEntry<WagglePortBlock> = CreateBuzzyBeez.REGISTRATE.block("waggle_port", ::WagglePortBlock)
        .initialProperties(SharedProperties::softMetal)
        .properties { it.noOcclusion() }
        .tag(BlockTags.MINEABLE_WITH_PICKAXE)
        .blockstate { c, p -> p.horizontalBlock(c.get(), p.models().getExistingFile(p.modLoc("block/waggle_port"))) }
        .item(::LogisticallyLinkedBlockItem)
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', AllItems.TRANSMITTER.get())
                .define('L', CreateAllBlocks.STOCK_LINK.get())
                .define('H', Items.HONEYCOMB)
                .define('B', CreateAllBlocks.BRASS_CASING.get())
                .pattern(" W ")
                .pattern("HLH")
                .pattern(" B ")
                .unlockedBy("has_stock_link", RegistrateRecipeProvider.has(CreateAllBlocks.STOCK_LINK.get()))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .model { c, p -> p.withExistingParent(c.name, p.modLoc("block/waggle_port")) }
        .build()
        .register()

    val MECHANICAL_APIARY: BlockEntry<MechanicalApiaryBlock> = CreateBuzzyBeez.REGISTRATE
        .block("mechanical_apiary", ::MechanicalApiaryBlock)
        .initialProperties(SharedProperties::wooden)
        .properties { it.noOcclusion() }
        .tag(BlockTags.MINEABLE_WITH_AXE)
        .blockstate { c, p ->
            p.horizontalBlock(
                c.get(),
                p.models().getExistingFile(p.modLoc("block/mechanical_apiary"))
            )
        }
        .onRegister { block -> BlockStressValues.IMPACTS.register(block) { 4.0 } }
        .item()
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('A', AllItems.ANDESITE_ALLOY.get())
                .define('H', Items.BEEHIVE)
                .define('C', CreateAllBlocks.ANDESITE_CASING.get())
                .define('S', CreateAllBlocks.SHAFT.get())
                .pattern("AHA")
                .pattern("ACA")
                .pattern(" S ")
                .unlockedBy("has_beehive", RegistrateRecipeProvider.has(Items.BEEHIVE))
                .save(p, CreateBuzzyBeez.asResource("crafting/" + c.name))
        }
        .model { c, p -> p.withExistingParent(c.name, p.modLoc("block/mechanical_apiary")) }
        .build()
        .register()
}
