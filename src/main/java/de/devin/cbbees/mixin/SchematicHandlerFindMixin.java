package de.devin.cbbees.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import de.devin.cbbees.content.drone.client.DroneViewClientState;
import de.devin.cbbees.items.AllItems;
import net.minecraft.world.entity.player.Inventory;
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
     * we re-check for the Construction Planner — first in main hand,
     * then anywhere in inventory when drone view is active.
     */
    @Inject(method = "findBlueprintInHand", at = @At("RETURN"), cancellable = true)
    private void ccr$findConstructionPlanner(Player player, CallbackInfoReturnable<ItemStack> cir) {
        if (cir.getReturnValue() != null) return;

        // Check main hand first (normal behavior)
        ItemStack stack = player.getMainHandItem();
        if (ccr$isDeployedPlanner(stack)) {
            activeSchematicItem = stack;
            activeHotbarSlot = player.getInventory().selected;
            cir.setReturnValue(stack);
            return;
        }

        // During drone view, search entire inventory for a deployed planner
        if (DroneViewClientState.INSTANCE.getActive()) {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack candidate = inv.getItem(i);
                if (ccr$isDeployedPlanner(candidate)) {
                    activeSchematicItem = candidate;
                    activeHotbarSlot = i;
                    cir.setReturnValue(candidate);
                    return;
                }
            }
        }
    }

    private static boolean ccr$isDeployedPlanner(ItemStack stack) {
        return AllItems.INSTANCE.getCONSTRUCTION_PLANNER().isIn(stack)
                && stack.has(AllDataComponents.SCHEMATIC_FILE)
                && stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false);
    }
}
