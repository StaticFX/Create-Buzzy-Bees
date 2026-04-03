package de.devin.cbbees.mixin;

import com.simibubi.create.content.schematics.client.SchematicHandler;
import de.devin.cbbees.compat.SchematicDataHelper;
import de.devin.cbbees.items.AllItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link SchematicHandler#findBlueprintInHand(Player)} to also
 * recognize the Construction Planner item as a valid schematic holder.
 *
 * <p>Create's original method only accepts {@code AllItems.SCHEMATIC}, so
 * without this mixin the placement overlay won't activate for our item.</p>
 */
@Mixin(value = SchematicHandler.class, remap = false)
public abstract class SchematicHandlerFindMixin {

    @Shadow
    private ItemStack activeSchematicItem;

    @Shadow
    private int activeHotbarSlot;

    /**
     * If Create's check returns null (item not a Create SchematicItem),
     * we re-check for the Construction Planner.
     */
    @Inject(method = "findBlueprintInHand", at = @At("RETURN"), cancellable = true)
    private void ccr$findConstructionPlanner(Player player, CallbackInfoReturnable<ItemStack> cir) {
        if (cir.getReturnValue() != null) return;

        ItemStack stack = player.getMainHandItem();
        if (!AllItems.INSTANCE.getCONSTRUCTION_PLANNER().isIn(stack)) return;

        if (SchematicDataHelper.hasFile(stack) && SchematicDataHelper.isDeployed(stack)) {
            activeSchematicItem = stack;
            activeHotbarSlot = player.getInventory().selected;
            cir.setReturnValue(stack);
        }
    }
}
