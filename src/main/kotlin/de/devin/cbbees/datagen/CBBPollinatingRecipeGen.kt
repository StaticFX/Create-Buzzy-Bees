package de.devin.cbbees.datagen

import com.simibubi.create.AllFluids
import com.simibubi.create.api.data.recipe.StandardProcessingRecipeGen
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.processing.PollinatingRecipe
import de.devin.cbbees.registry.AllCBeesRecipeTypes
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.Items
import java.util.concurrent.CompletableFuture

class CBBPollinatingRecipeGen(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>
) : StandardProcessingRecipeGen<PollinatingRecipe>(output, registries, CreateBuzzyBeez.ID) {

    override fun getRecipeType(): IRecipeTypeInfo = AllCBeesRecipeTypes.POLLINATING

    val HONEY = create("honey") {
        it.require(Items.BONE_MEAL)
            .require(ItemTags.FLOWERS)
            .output(AllFluids.HONEY.get().source, 50)
            .duration(150)
    }
}
