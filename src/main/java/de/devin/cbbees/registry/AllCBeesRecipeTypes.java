package de.devin.cbbees.registry;

import java.util.Optional;

import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import de.devin.cbbees.CreateBuzzyBeez;
import de.devin.cbbees.content.processing.GlueingRecipe;
import de.devin.cbbees.content.processing.PollinatingRecipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AllCBeesRecipeTypes {

	private static final DeferredRegister<RecipeSerializer<?>> SERIALIZER_REGISTER =
		DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, CreateBuzzyBeez.ID);
	private static final DeferredRegister<RecipeType<?>> TYPE_REGISTER =
		DeferredRegister.create(Registries.RECIPE_TYPE, CreateBuzzyBeez.ID);

	public static final RecipeTypeEntry GLUEING = registerProcessing("glueing", GlueingRecipe::new);
	public static final RecipeTypeEntry POLLINATING = registerProcessing("pollinating", PollinatingRecipe::new);

	private static RecipeTypeEntry registerProcessing(String name, StandardProcessingRecipe.Factory<?> factory) {
		ResourceLocation id = CreateBuzzyBeez.INSTANCE.asResource(name);
		DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializer =
			SERIALIZER_REGISTER.register(name, () -> new StandardProcessingRecipe.Serializer<>(factory));
		DeferredHolder<RecipeType<?>, RecipeType<?>> type =
			TYPE_REGISTER.register(name, () -> RecipeType.simple(id));
		return new RecipeTypeEntry(id, serializer, type);
	}

	public static void register(IEventBus modEventBus) {
		SERIALIZER_REGISTER.register(modEventBus);
		TYPE_REGISTER.register(modEventBus);
	}

	public static class RecipeTypeEntry implements IRecipeTypeInfo {
		private final ResourceLocation id;
		private final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializer;
		private final DeferredHolder<RecipeType<?>, RecipeType<?>> type;

		public RecipeTypeEntry(
			ResourceLocation id,
			DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializer,
			DeferredHolder<RecipeType<?>, RecipeType<?>> type
		) {
			this.id = id;
			this.serializer = serializer;
			this.type = type;
		}

		@Override
		public ResourceLocation getId() {
			return id;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends RecipeSerializer<?>> T getSerializer() {
			return (T) serializer.get();
		}

		@SuppressWarnings("unchecked")
		@Override
		public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
			return (RecipeType<R>) type.get();
		}

		public <I extends RecipeInput, R extends Recipe<I>> Optional<RecipeHolder<R>> find(I inv, Level level) {
			return level.getRecipeManager().getRecipeFor(getType(), inv, level);
		}
	}
}
