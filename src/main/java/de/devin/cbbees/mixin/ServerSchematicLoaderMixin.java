package de.devin.cbbees.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.schematics.ServerSchematicLoader;
import com.tterrag.registrate.util.entry.ItemEntry;
import de.devin.cbbees.content.planner.SchematicAndQuillPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerSchematicLoader.class, remap = false)
public abstract class ServerSchematicLoaderMixin {

    @WrapOperation(
        method = "handleInstantSchematic",
        at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private boolean ccr$acceptPlanner(ItemEntry<?> entry, ItemStack stack, Operation<Boolean> original) {
        return original.call(entry, stack) || SchematicAndQuillPlanner.isHeld(stack);
    }

    @WrapOperation(
        method = "handleInstantSchematic",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V")
    )
    private void ccr$keepPlanner(ServerPlayer player, InteractionHand hand, ItemStack schematic, Operation<Void> original,
                                 @Local(argsOnly = true, ordinal = 0) BlockPos pos) {
        if (!SchematicAndQuillPlanner.convertSaved(player, schematic, pos)) original.call(player, hand, schematic);
    }
}
