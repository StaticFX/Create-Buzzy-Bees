package de.devin.cbbees.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllItems;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.ProcessingViaFanCategory;

import de.devin.cbbees.blocks.AllBlocks;
import de.devin.cbbees.content.processing.GlueingRecipe;
import de.devin.cbbees.content.processing.PollinatingRecipe;
import de.devin.cbbees.registry.AllCBeesRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

@JeiPlugin
public class CBBeesJEI implements IModPlugin {

	private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cbbees", "jei_plugin");

	private final List<CreateRecipeCategory<?>> allCategories = new ArrayList<>();

	@Override
	public ResourceLocation getPluginUid() {
		return ID;
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		allCategories.clear();

		CreateRecipeCategory<GlueingRecipe> glueing = new CreateRecipeCategory.Builder<>(GlueingRecipe.class)
			.addTypedRecipes(AllCBeesRecipeTypes.GLUEING)
			.catalystStack(ProcessingViaFanCategory.getFan("fan_glueing"))
			.doubleItemIcon(AllItems.PROPELLER.get(), Items.HONEY_BOTTLE)
			.emptyBackground(178, 72)
			.build(ResourceLocation.fromNamespaceAndPath("cbbees", "fan_glueing"), FanGlueingCategory::new);

		allCategories.add(glueing);

		CreateRecipeCategory<PollinatingRecipe> pollinating = new CreateRecipeCategory.Builder<>(PollinatingRecipe.class)
			.addTypedRecipes(AllCBeesRecipeTypes.POLLINATING)
			.catalyst(AllBlocks.INSTANCE.getMECHANICAL_APIARY()::get)
			.doubleItemIcon(AllBlocks.INSTANCE.getMECHANICAL_APIARY().get(), Items.HONEY_BOTTLE)
			.emptyBackground(177, 53)
			.build(ResourceLocation.fromNamespaceAndPath("cbbees", "pollinating"), PollinatingCategory::new);

		allCategories.add(pollinating);
		registration.addRecipeCategories(allCategories.toArray(IRecipeCategory[]::new));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		allCategories.forEach(c -> c.registerRecipes(registration));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		allCategories.forEach(c -> c.registerCatalysts(registration));
	}
}
