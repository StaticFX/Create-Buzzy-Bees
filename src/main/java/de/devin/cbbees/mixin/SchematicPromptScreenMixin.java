package de.devin.cbbees.mixin;

import com.simibubi.create.content.schematics.client.SchematicPromptScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import de.devin.cbbees.content.schematics.client.GroupPickerScreen;
import de.devin.cbbees.content.schematics.client.SchematicGroupManager;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for Create's {@link SchematicPromptScreen} (the Schematic & Quill save dialog).
 * Adds a "Group" button that opens a {@link GroupPickerScreen} for assigning the
 * schematic to a group before saving.
 */
@Mixin(value = SchematicPromptScreen.class, remap = false)
public abstract class SchematicPromptScreenMixin extends AbstractSimiScreen {

    @Shadow
    private EditBox nameField;

    @Unique
    private String ccr$selectedGroup = "";

    @Unique
    private IconButton ccr$groupButton;

    @Inject(method = {"init", "m_7856_"}, at = @At("TAIL"))
    private void ccr$addGroupButton(CallbackInfo ci) {
        int x = guiLeft;
        int y = guiTop + 2;

        // Add group button below the name field area
        ccr$groupButton = new IconButton(x + 80, y + 53, AllIcons.I_TOOLBOX);
        ccr$groupButton.setToolTip(Component.translatable("gui.cbbees.schematic_prompt.group"));
        ccr$groupButton.withCallback(() -> {
            Function1<String, Unit> callback = (selectedPath) -> {
                ccr$selectedGroup = selectedPath;
                return Unit.INSTANCE;
            };
            // Pass this screen as parentScreen so GroupPicker returns here on close/confirm
            Minecraft.getInstance().setScreen(new GroupPickerScreen(
                callback, ccr$selectedGroup, (SchematicPromptScreen) (Object) this
            ));
        });
        addRenderableWidget(ccr$groupButton);
    }

    @Inject(method = "confirm", at = @At("HEAD"))
    private void ccr$saveGroupOnConfirm(boolean convertImmediately, CallbackInfo ci) {
        if (nameField == null) return;
        String filename = nameField.getValue();
        if (filename == null || filename.isEmpty()) return;

        // Ensure .nbt extension for the group mapping
        if (!filename.endsWith(".nbt")) {
            filename = filename + ".nbt";
        }

        if (!ccr$selectedGroup.isEmpty()) {
            SchematicGroupManager.INSTANCE.setGroup(filename, ccr$selectedGroup);
        }
    }
}
