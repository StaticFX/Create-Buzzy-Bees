package de.devin.cbbees.compat.jei;

import javax.annotation.ParametersAreNonnullByDefault;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import de.devin.cbbees.blocks.AllBlocks;
import de.devin.cbbees.content.processing.PollinatingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.crafting.Ingredient;

@ParametersAreNonnullByDefault
public class PollinatingCategory extends CreateRecipeCategory<PollinatingRecipe> {

	public PollinatingCategory(Info<PollinatingRecipe> info) {
		super(info);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, PollinatingRecipe recipe, IFocusGroup focuses) {
		int i = 0;
		for (Ingredient ingredient : recipe.getIngredients()) {
			builder.addSlot(RecipeIngredientRole.INPUT, 15 + i * 19, 9)
				.setBackground(getRenderedSlot(), -1, -1)
				.addIngredients(ingredient);
			i++;
		}

		recipe.getFluidResults()
			.forEach(fluid -> addFluidSlot(builder, 139, 27, fluid));
	}

	@Override
	public void draw(PollinatingRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
		AllGuiTextures.JEI_ARROW.render(graphics, 85, 32);
		AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 43, 4);
		AllGuiTextures.JEI_SHADOW.render(graphics, 32, 40);

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(46, 45, 0);
		GuiGameElement.of(AllBlocks.INSTANCE.getMECHANICAL_APIARY().getDefaultState())
			.rotateBlock(22.5, 202.5, 0)
			.scale(22)
			.lighting(AnimatedKinetics.DEFAULT_LIGHTING)
			.render(graphics);
		poseStack.popPose();
	}
}
