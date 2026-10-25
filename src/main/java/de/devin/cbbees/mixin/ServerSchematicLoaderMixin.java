package de.devin.cbbees.mixin;

import com.simibubi.create.content.schematics.ServerSchematicLoader;
import com.tterrag.registrate.util.entry.ItemEntry;
import de.devin.cbbees.content.planner.SchematicAndQuillPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerSchematicLoader.class, remap = false)
public abstract class ServerSchematicLoaderMixin {

    @Redirect(
        method = "handleInstantSchematic",
        at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private boolean ccr$acceptPlanner(ItemEntry<?> entry, ItemStack stack) {
        return SchematicAndQuillPlanner.isHeld(stack);
    }

    @Redirect(
        method = "handleInstantSchematic",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V")
    )
    private void ccr$keepPlanner(ServerPlayer player, InteractionHand hand, ItemStack schematic,
                                 ServerPlayer owner, String name, Level world, BlockPos pos, BlockPos bounds) {
        if (!SchematicAndQuillPlanner.convertSaved(player, schematic, pos)) player.setItemInHand(hand, schematic);
    }
}
