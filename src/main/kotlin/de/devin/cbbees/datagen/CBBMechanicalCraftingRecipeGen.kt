package de.devin.cbbees.datagen

import com.simibubi.create.AllItems as CreateItems
import com.simibubi.create.api.data.recipe.MechanicalCraftingRecipeGen
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.blocks.AllBlocks
import de.devin.cbbees.items.AllItems
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.world.item.Items
import net.neoforged.neoforge.common.Tags
import java.util.concurrent.CompletableFuture

class CBBMechanicalCraftingRecipeGen(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>
) : MechanicalCraftingRecipeGen(output, registries, CreateBuzzyBeez.ID) {

    val SCHEMATIC_ZAPPER = create { AllItems.SCHEMATIC_ZAPPER.get() }
        .recipe {
            it.key('E', CreateItems.ELECTRON_TUBE.get())
                .key('H', Items.HONEYCOMB)
                .key('B', CreateItems.BRASS_SHEET.get())
                .key('P', CreateItems.PRECISION_MECHANISM.get())
                .key('R', CreateItems.BRASS_INGOT.get())
                .key('Q', CreateItems.SCHEMATIC_AND_QUILL.get())
                .key('L', com.simibubi.create.AllBlocks.ANALOG_LEVER.get())
                .key('W', Tags.Items.STRIPPED_WOODS)
                .patternLine("   EE ")
                .patternLine("RHBPQB")
                .patternLine("   BLW")
                .patternLine("     W")
                .disallowMirrored()
        }
}

