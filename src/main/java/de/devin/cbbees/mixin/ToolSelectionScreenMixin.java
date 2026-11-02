package de.devin.cbbees.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.client.ToolSelectionScreen;
import com.simibubi.create.content.schematics.client.tools.ToolType;
import de.devin.cbbees.content.drone.client.DroneViewClientState;
import de.devin.cbbees.content.schematics.client.PlacementTool;
import de.devin.cbbees.content.schematics.client.PlacementTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Appends the planner's {@link PlacementTools} to Create's schematic tool bar while a schematic
 * is deployed. Picking one sets {@link PlacementTools#getActive()} instead of equipping a {@link ToolType}.
 */
@Mixin(value = ToolSelectionScreen.class, remap = false)
public abstract class ToolSelectionScreenMixin {

    @Shadow protected List<ToolType> tools;
    @Shadow protected int selection;
    @Shadow protected int w;
    @Shadow protected int h;
    @Shadow public boolean focused;
    @Shadow private float yOffset;

    @Unique
    private List<PlacementTool> ccr$placementTools() {
        Minecraft mc = Minecraft.getInstance();
        boolean available = mc.player != null
            && !DroneViewClientState.findSchematicPlanner(mc.player).isEmpty()
            && CreateClient.SCHEMATIC_HANDLER.isDeployed();
        return available ? PlacementTools.getAll() : List.of();
    }

    @Unique
    private int ccr$size() {
        return tools.size() + ccr$placementTools().size();
    }

    @Unique
    private PlacementTool ccr$selectedPlacementTool() {
        List<PlacementTool> extra = ccr$placementTools();
        int index = selection - tools.size();
        return index >= 0 && index < extra.size() ? extra.get(index) : null;
    }

    @Unique
    private void ccr$clampSelection() {
        if (selection >= ccr$size()) selection = Math.max(0, ccr$size() - 1);
    }

    @Inject(method = "cycle", at = @At("HEAD"), cancellable = true)
    private void ccr$cycle(int direction, CallbackInfo ci) {
        ccr$clampSelection();
        if (ccr$placementTools().isEmpty()) return;
        ci.cancel();
        int size = ccr$size();
        selection = (selection + (direction < 0 ? 1 : -1) + size) % size;
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void ccr$pickPlacementTool(CallbackInfo ci) {
        ccr$clampSelection();
        PlacementTool tool = ccr$selectedPlacementTool();
        PlacementTools.setActive(tool);
        if (tool != null) ci.cancel();
    }

    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0))
    private Object ccr$safeToolGet(List<?> list, int index, Operation<Object> original) {
        return index >= 0 && index < list.size() ? original.call(list, index) : null;
    }

    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/schematics/client/tools/ToolType;getDescription()Ljava/util/List;"))
    private List<Component> ccr$description(ToolType toolType, Operation<List<Component>> original) {
        if (toolType != null) return original.call(toolType);
        PlacementTool tool = ccr$selectedPlacementTool();
        if (tool == null) return List.of();
        return List.of(tool.getName(), tool.getDescription(), Component.empty(), Component.empty());
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void ccr$adjustWidth(GuiGraphics graphics, float partialTicks, CallbackInfo ci) {
        w = Math.max(ccr$size() * 50 + 30, 220);
        ccr$clampSelection();
    }

    @Inject(method = "draw", at = @At("TAIL"))
    private void ccr$drawPlacementTools(GuiGraphics graphics, float partialTicks, CallbackInfo ci) {
        List<PlacementTool> extra = ccr$placementTools();
        if (extra.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int x = (window.getGuiScaledWidth() - w) / 2 + 15;
        int y = window.getGuiScaledHeight() - h - 75;
        PoseStack pose = graphics.pose();

        pose.pushPose();
        pose.translate(0, -yOffset, focused ? 100 : 0);

        for (int j = 0; j < extra.size(); j++) {
            PlacementTool tool = extra.get(j);
            int slotX = x + (tools.size() + j) * 50;
            boolean selected = selection == tools.size() + j;
            float alpha = selected || focused ? 1 : .2f;

            RenderSystem.enableBlend();
            pose.pushPose();
            if (selected) {
                pose.translate(0, -10, 0);
                graphics.drawCenteredString(mc.font, tool.getName(), slotX + 24, y + 28, tool.getColor());
            }
            RenderSystem.setShaderColor(0, 0, 0, alpha);
            tool.getIcon().render(graphics, slotX + 16, y + 12);
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            tool.getIcon().render(graphics, slotX + 16, y + 11);
            pose.popPose();
        }

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        pose.popPose();
    }
}
