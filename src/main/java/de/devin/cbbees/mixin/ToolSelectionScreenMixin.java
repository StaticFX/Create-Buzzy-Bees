package de.devin.cbbees.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.client.ToolSelectionScreen;
import com.simibubi.create.content.schematics.client.tools.ToolType;
import com.simibubi.create.foundation.gui.AllIcons;
import de.devin.cbbees.content.drone.client.DroneViewClientState;
import de.devin.cbbees.content.schematics.client.ConstructionToolState;
import de.devin.cbbees.items.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * Mixin for {@link ToolSelectionScreen} to add two extra entries to Create's
 * schematic tool bar when holding the Construction Planner:
 * <ul>
 *   <li><b>Construct</b> — equip, then RMB to start bee construction</li>
 *   <li><b>Unselect</b> — equip, then RMB to clear schematic and return to the picker</li>
 * </ul>
 *
 * <p>Custom tools are "equipped" by setting {@link ConstructionToolState#activeTool}.
 * The actual RMB handling lives in {@link SchematicHandlerHudMixin}.</p>
 */
@Mixin(value = ToolSelectionScreen.class, remap = false)
public abstract class ToolSelectionScreenMixin {

    @Shadow protected List<ToolType> tools;
    @Shadow protected int selection;
    @Shadow protected int w;
    @Shadow protected int h;
    @Shadow public boolean focused;
    @Shadow protected Consumer<ToolType> callback;
    @Shadow private float yOffset;

    /** Number of extra tool slots we add (Construct + Unselect + Program). */
    @Unique private static final int CCR$EXTRA_TOOLS = 3;

    @Unique private static final String[] CCR$NAMES = { "gui.cbbees.tool.construct", "gui.cbbees.tool.unselect", "gui.cbbees.tool.program" };
    @Unique private static final String[] CCR$DESCS = { "gui.cbbees.tool.construct.desc", "gui.cbbees.tool.unselect.desc", "gui.cbbees.tool.program.desc" };
    @Unique private static final AllIcons[] CCR$ICONS = { AllIcons.I_PLAY, AllIcons.I_TRASH, AllIcons.I_CONFIRM };
    @Unique private static final int[] CCR$NAME_COLORS = { 0x88FF88, 0xFFAA88, 0x88CCFF };
    @Unique private static final ConstructionToolState.CustomTool[] CCR$TOOL_ENUM = {
        ConstructionToolState.CustomTool.CONSTRUCT,
        ConstructionToolState.CustomTool.UNSELECT,
        ConstructionToolState.CustomTool.PROGRAM
    };

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                            */
    /* ------------------------------------------------------------------ */

    @Unique
    private boolean ccr$hasExtraTools() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack planner = DroneViewClientState.findActivePlanner(mc.player);
        if (planner.isEmpty()) return false;
        return CreateClient.SCHEMATIC_HANDLER.isDeployed();
    }

    /** Total number of tool slots (regular + custom). */
    @Unique
    private int ccr$totalSize() {
        return tools.size() + (ccr$hasExtraTools() ? CCR$EXTRA_TOOLS : 0);
    }

    /** Index of the first custom slot, or -1 if none. */
    @Unique
    private int ccr$customOffset() {
        return ccr$hasExtraTools() ? tools.size() : -1;
    }

    /* ------------------------------------------------------------------ */
    /*  Cycling                                                            */
    /* ------------------------------------------------------------------ */

    @Inject(method = "cycle", at = @At("HEAD"), cancellable = true)
    private void ccr$extendedCycle(int direction, CallbackInfo ci) {
        // Safety clamp regardless
        if (selection >= ccr$totalSize()) {
            selection = Math.max(0, ccr$totalSize() - 1);
        }
        if (!ccr$hasExtraTools()) return;

        ci.cancel();
        int size = ccr$totalSize();
        selection += (direction < 0) ? 1 : -1;
        selection = (selection + size) % size;
    }

    /* ------------------------------------------------------------------ */
    /*  Closing / equipping                                                */
    /* ------------------------------------------------------------------ */

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void ccr$handleCustomClose(CallbackInfo ci) {
        // Safety clamp
        if (selection >= ccr$totalSize()) {
            selection = Math.max(0, tools.size() - 1);
        }

        int offset = ccr$customOffset();
        if (offset < 0 || selection < offset) {
            // Regular tool selected — clear any custom state
            ConstructionToolState.setActiveTool(ConstructionToolState.CustomTool.NONE);
            return; // let original onClose run
        }

        // Custom tool selected — set state for RMB handling, don't equip a ToolType
        int customIdx = selection - offset;
        ConstructionToolState.setActiveTool(CCR$TOOL_ENUM[customIdx]);
        ci.cancel();
    }

    /* ------------------------------------------------------------------ */
    /*  Drawing — crash prevention for tooltip                             */
    /* ------------------------------------------------------------------ */

    @Redirect(
        method = "draw",
        at = @At(value = "INVOKE",
                 target = "Ljava/util/List;get(I)Ljava/lang/Object;",
                 ordinal = 0)
    )
    private Object ccr$safeToolGet(List<?> list, int index) {
        if (index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
    }

    @Redirect(
        method = "draw",
        at = @At(value = "INVOKE",
                 target = "Lcom/simibubi/create/content/schematics/client/tools/ToolType;getDescription()Ljava/util/List;")
    )
    private List<Component> ccr$getDescription(ToolType toolType) {
        if (toolType == null) {
            // Determine which custom tool is selected
            int offset = ccr$customOffset();
            int customIdx = (offset >= 0 && selection >= offset) ? selection - offset : 0;
            customIdx = Math.min(customIdx, CCR$EXTRA_TOOLS - 1);
            return List.of(
                Component.translatable(CCR$NAMES[customIdx]),
                Component.translatable(CCR$DESCS[customIdx]),
                Component.literal(""),
                Component.literal("")
            );
        }
        return toolType.getDescription();
    }

    /* ------------------------------------------------------------------ */
    /*  Drawing — width + icon rendering                                   */
    /* ------------------------------------------------------------------ */

    @Inject(method = "draw", at = @At("HEAD"))
    private void ccr$adjustWidth(GuiGraphics graphics, float partialTicks, CallbackInfo ci) {
        int count = ccr$totalSize();
        w = Math.max(count * 50 + 30, 220);

        // Clamp if extras disappeared (player dropped item)
        if (selection >= count) {
            selection = Math.max(0, count - 1);
        }
    }

    @Inject(method = "draw", at = @At("TAIL"))
    private void ccr$renderExtraTools(GuiGraphics graphics, float partialTicks, CallbackInfo ci) {
        if (!ccr$hasExtraTools()) return;

        PoseStack poseStack = graphics.pose();
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        int x = (win.getGuiScaledWidth() - w) / 2 + 15;
        int y = win.getGuiScaledHeight() - h - 75;

        poseStack.pushPose();
        poseStack.translate(0, -yOffset, focused ? 100 : 0);

        int offset = tools.size();
        for (int j = 0; j < CCR$EXTRA_TOOLS; j++) {
            int idx = offset + j;
            boolean isSelected = (selection == idx);

            RenderSystem.enableBlend();
            poseStack.pushPose();

            float alpha = focused ? 1 : .2f;
            if (isSelected) {
                poseStack.translate(0, -10, 0);
                String name = Component.translatable(CCR$NAMES[j]).getString();
                graphics.drawCenteredString(mc.font, name, x + idx * 50 + 24, y + 28, CCR$NAME_COLORS[j]);
                alpha = 1;
            }

            // Shadow pass
            RenderSystem.setShaderColor(0, 0, 0, alpha);
            CCR$ICONS[j].render(graphics, x + idx * 50 + 16, y + 12);
            // Normal pass
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            CCR$ICONS[j].render(graphics, x + idx * 50 + 16, y + 11);

            poseStack.popPose();
        }

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }
}
