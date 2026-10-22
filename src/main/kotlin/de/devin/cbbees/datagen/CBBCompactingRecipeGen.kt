package de.devin.cbbees.datagen

import com.simibubi.create.api.data.recipe.CompactingRecipeGen
import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.world.item.Items
import net.neoforged.neoforge.common.Tags
import java.util.concurrent.CompletableFuture

class CBBCompactingRecipeGen(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>
) : CompactingRecipeGen(output, registries, CreateBuzzyBeez.ID) {

    val HONEYCOMB = create("honeycomb") {
        it.require(Tags.Fluids.HONEY, 250)
            .require(Items.SUGAR)
            .output(Items.HONEYCOMB)
    }
}
