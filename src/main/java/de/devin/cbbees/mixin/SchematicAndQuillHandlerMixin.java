package de.devin.cbbees.mixin;

import com.simibubi.create.content.schematics.client.SchematicAndQuillHandler;
import com.tterrag.registrate.util.entry.ItemEntry;
import de.devin.cbbees.content.planner.SchematicAndQuillPlanner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SchematicAndQuillHandler.class, remap = false)
public abstract class SchematicAndQuillHandlerMixin {

    @Redirect(
        method = "isActive",
        at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private boolean ccr$acceptPlanner(ItemEntry<?> entry, ItemStack stack) {
        return SchematicAndQuillPlanner.isHeld(stack);
    }
}
