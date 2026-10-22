package de.devin.cbbees.content.processing;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import de.devin.cbbees.registry.AllCBeesRecipeTypes;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

@ParametersAreNonnullByDefault
public class PollinatingRecipe extends StandardProcessingRecipe<RecipeWrapper> {

	public PollinatingRecipe(ProcessingRecipeParams params) {
		super(AllCBeesRecipeTypes.POLLINATING, params);
	}

	@Override
	public boolean matches(RecipeWrapper inv, Level worldIn) {
		return matchSlots(inv) != null;
	}

	@Nullable
	public int[] matchSlots(RecipeWrapper inv) {
		int[] assignment = new int[ingredients.size()];
		return assign(inv, 0, assignment, new boolean[inv.size()]) ? assignment : null;
	}

	private boolean assign(RecipeWrapper inv, int ingredientIndex, int[] assignment, boolean[] used) {
		if (ingredientIndex == ingredients.size())
			return true;
		Ingredient ingredient = ingredients.get(ingredientIndex);
		for (int slot = 0; slot < inv.size(); slot++) {
			ItemStack stack = inv.getItem(slot);
			if (used[slot] || stack.isEmpty() || !ingredient.test(stack))
				continue;
			used[slot] = true;
			assignment[ingredientIndex] = slot;
			if (assign(inv, ingredientIndex + 1, assignment, used))
				return true;
			used[slot] = false;
		}
		return false;
	}

	@Override
	protected int getMaxInputCount() {
		return 2;
	}

	@Override
	protected int getMaxOutputCount() {
		return 0;
	}

	@Override
	protected int getMaxFluidOutputCount() {
		return 1;
	}

	@Override
	protected boolean canSpecifyDuration() {
		return true;
	}
}
