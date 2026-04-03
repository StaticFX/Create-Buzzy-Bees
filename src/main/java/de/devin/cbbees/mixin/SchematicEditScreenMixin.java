package de.devin.cbbees.mixin;

import com.simibubi.create.content.schematics.client.SchematicEditScreen;
import de.devin.cbbees.compat.SchematicDataHelper;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import de.devin.cbbees.content.schematics.ConstructionPlannerItem;
import de.devin.cbbees.items.AllItems;
import de.devin.cbbees.network.StartConstructionPacket;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import de.devin.cbbees.network.NetworkHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for {@link SchematicEditScreen} to add a "Start Construction" button
 * that triggers the robot construction system.
 *
 * <p>Only shows the button when the active schematic item is a Construction Planner.
 * This screen is only reachable in state 3 (deployed), so no browsing preview logic needed.</p>
 */
@Mixin(value = SchematicEditScreen.class, remap = false)
public abstract class SchematicEditScreenMixin extends AbstractSimiScreen {

    @Unique
    private IconButton ccr$constructButton;

    @Inject(method = {"init", "m_7856_"}, at = @At("TAIL"))
    private void ccr$addConstructButtons(CallbackInfo ci) {
        SchematicHandler handler = CreateClient.SCHEMATIC_HANDLER;
        if (!handler.isDeployed()) return;

        // Only show for Construction Planner
        ItemStack activeItem = handler.getActiveSchematicItem();
        if (activeItem == null || !AllItems.INSTANCE.getCONSTRUCTION_PLANNER().isIn(activeItem)) return;

        int buttonX = guiLeft + 10;
        int buttonY = guiTop + 90;

        ccr$constructButton = new IconButton(buttonX, buttonY, AllIcons.I_PLAY);
        ccr$constructButton.setToolTip(Component.translatable("gui.cbbees.schematic.start_construction"));
        ccr$constructButton.withCallback(() -> {
            BlockPos anchor = SchematicDataHelper.getAnchor(activeItem);
            Rotation rotation = SchematicDataHelper.getRotation(activeItem);
            Mirror mirror = SchematicDataHelper.getMirror(activeItem);
            NetworkHelper.sendToServer(new StartConstructionPacket(anchor, rotation, mirror));
            // Clear client state immediately so Create deactivates cleanly
            ConstructionPlannerItem.Companion.clearSchematic(activeItem);
            if (Minecraft.getInstance().screen != null) {
                Minecraft.getInstance().screen.onClose();
            }
        });
        addRenderableWidget(ccr$constructButton);
    }
}
