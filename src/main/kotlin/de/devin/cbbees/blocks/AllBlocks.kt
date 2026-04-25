package de.devin.cbbees.blocks

import com.simibubi.create.AllItems
import com.simibubi.create.AllBlocks as CreateAllBlocks
import com.simibubi.create.api.stress.BlockStressValues
import com.simibubi.create.foundation.data.SharedProperties
import com.tterrag.registrate.providers.RegistrateRecipeProvider
import com.tterrag.registrate.util.entry.BlockEntry
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlock
import de.devin.cbbees.content.deployer.SchematicDeployerBlock
import de.devin.cbbees.content.logistics.ports.LogisticPortBlock
import de.devin.cbbees.content.logistics.transport.TransportPortBlock
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.tags.BlockTags
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
        .blockstate { _, _ -> } // Hand-written multipart blockstate in resources
        .item()
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
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
            ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, c.get())
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
        .blockstate { _, _ -> } // Hand-written multipart blockstate in resources
        .item()
        .recipe { c, p ->
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get())
                .define('W', AllItems.TRANSMITTER.get())
                .define('V', CreateAllBlocks.ITEM_VAULT.get())
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

}
