package de.devin.cbbees.mixin;

import com.simibubi.create.content.schematics.client.SchematicEditScreen;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.gui.widget.IconButton;
import de.devin.cbbees.icons.AllCCRIcons;
import de.devin.cbbees.content.planner.AllPlanners;
import de.devin.cbbees.content.schematics.client.ConstructPrompt;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for {@link SchematicEditScreen} to add a "Start Construction" button
 * that triggers the bee construction system.
 *
 * <p>Only shows the button when the active schematic item is a Construction Planner.
 * This screen is only reachable in state 3 (deployed), so no browsing preview logic needed.</p>
 */
@Mixin(value = SchematicEditScreen.class, remap = false)
public abstract class SchematicEditScreenMixin extends AbstractSimiScreen {

    @Unique
    private IconButton ccr$constructButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void ccr$addConstructButtons(CallbackInfo ci) {
        SchematicHandler handler = CreateClient.SCHEMATIC_HANDLER;
        if (!handler.isDeployed()) return;

        // Only show for schematic-placing planners
        ItemStack activeItem = handler.getActiveSchematicItem();
        if (activeItem == null || AllPlanners.schematicPlannerOf(activeItem) == null) return;

        int buttonX = guiLeft + 10;
        int buttonY = guiTop + 90;

        ccr$constructButton = new IconButton(buttonX, buttonY, AllCCRIcons.START_CONSTRUCTION);
        ccr$constructButton.setToolTip(Component.translatable("gui.cbbees.schematic.start_construction"));
        ccr$constructButton.withCallback(() -> {
            ConstructPrompt.construct(activeItem);
            if (Minecraft.getInstance().screen != null) {
                Minecraft.getInstance().screen.onClose();
            }
        });
        addRenderableWidget(ccr$constructButton);
    }
}
