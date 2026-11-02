package de.devin.cbbees.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import com.simibubi.create.content.schematics.client.ToolSelectionScreen;
import com.simibubi.create.content.schematics.client.tools.ToolType;
import de.devin.cbbees.content.schematics.client.BuildModeBar;
import de.devin.cbbees.content.schematics.client.ConstructPrompt;
import de.devin.cbbees.content.schematics.client.PlannerToolInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the planner's own tools, shortcuts and the build mode bar into Create's schematic overlay.
 * The behaviour lives in {@link PlannerToolInput}, {@link ConstructPrompt} and {@link BuildModeBar}.
 */
@Mixin(value = SchematicHandler.class, remap = false)
public abstract class SchematicHandlerHudMixin {

    @Shadow private boolean active;
    @Shadow private ItemStack activeSchematicItem;
    @Shadow private ToolType currentTool;
    @Shadow private ToolSelectionScreen selectionScreen;
    @Shadow public abstract boolean isDeployed();

    @Unique
    private boolean ccr$isPlacing() {
        return active && isDeployed();
    }

    @Inject(method = "onMouseInput", at = @At("HEAD"), cancellable = true)
    private void ccr$rightClick(int button, boolean pressed, CallbackInfoReturnable<Boolean> cir) {
        if (!ccr$isPlacing() || !pressed || button != 1) return;
        if (!PlannerToolInput.rightClick()) return;
        selectionScreen.setSelectedElement(currentTool);
        cir.setReturnValue(true);
    }

    @Inject(method = "onKeyInput", at = @At("HEAD"))
    private void ccr$keyPressed(int key, boolean pressed, CallbackInfo ci) {
        if (ccr$isPlacing() && pressed) PlannerToolInput.keyPressed(key);
    }

    @Inject(method = "printInstantly", at = @At("HEAD"), cancellable = true)
    private void ccr$printWithPlanner(CallbackInfo ci) {
        if (PlannerToolInput.printInstantly(activeSchematicItem)) ci.cancel();
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/schematics/client/ToolSelectionScreen;renderPassive(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void ccr$renderBuildModeBar(ToolSelectionScreen screen, GuiGraphics graphics, float partialTicks, Operation<Void> original) {
        if (ccr$isPlacing() && ConstructPrompt.isOpen()) BuildModeBar.render(graphics);
        else original.call(screen, graphics, partialTicks);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ccr$tickBuildModeBar(CallbackInfo ci) {
        BuildModeBar.update();
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void ccr$cycleBuildModeBar(double delta, CallbackInfoReturnable<Boolean> cir) {
        if (!ccr$isPlacing() || !ConstructPrompt.isOpen()) return;
        BuildModeBar.cycle((int) Math.signum(delta));
        cir.setReturnValue(true);
    }
}
