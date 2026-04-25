package de.devin.cbbees.datagen

import com.simibubi.create.api.data.recipe.FillingRecipeGen
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.Tags
import java.util.concurrent.CompletableFuture

class CBBFillingRecipeGen(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>
) : FillingRecipeGen(output, registries, CreateBuzzyBeez.ID) {

    /** Default honey bottle fuel value — must match CBBeesConfig.honeyBottleFuelValue default. */
    private val DEFAULT_HONEY_BOTTLE_FUEL = 100

    val PORTABLE_BEEHIVE_HONEY = create("portable_beehive_honey") {
        val result = ItemStack(AllItems.PORTABLE_BEEHIVE.get())
        result.set(AllDataComponents.HONEY_FUEL.get(), DEFAULT_HONEY_BOTTLE_FUEL)
        it.require(Tags.Fluids.HONEY, 250)
            .require(AllItems.PORTABLE_BEEHIVE.get())
            .output(result)
    }
}
