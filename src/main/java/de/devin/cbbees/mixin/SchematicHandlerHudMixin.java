package de.devin.cbbees.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import de.devin.cbbees.content.drone.client.DroneViewClientState;
import de.devin.cbbees.content.schematics.ConstructionPlannerItem;
import de.devin.cbbees.content.schematics.client.ConstructionToolState;
import de.devin.cbbees.items.AllItems;
import de.devin.cbbees.content.deployer.SchematicProgram;
import de.devin.cbbees.network.ProgramSchematicPacket;
import de.devin.cbbees.network.StartConstructionPacket;
import de.devin.cbbees.network.StopTasksPacket;
import de.devin.cbbees.network.UnselectSchematicPacket;
import de.devin.cbbees.registry.AllKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for {@link SchematicHandler} providing:
 * <ul>
 *   <li>RMB interception for the custom Construct / Unselect tools (state 3 only)</li>
 *   <li>R-key shortcut for construction (Construction Planner only)</li>
 *   <li>Backspace shortcut for stopping tasks</li>
 * </ul>
 *
 * <p>Note: During state 2 (browsing preview), Create's SchematicHandler is dormant
 * (no data on the item), so none of these injections fire. They only apply to
 * state 3 (deployed) when Create is active.</p>
 */
@Mixin(value = SchematicHandler.class, remap = false)
public abstract class SchematicHandlerHudMixin {

    @Shadow private boolean active;
    @Shadow public abstract boolean isDeployed();

    /* ------------------------------------------------------------------ */
    /*  Construction packet helper                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Reads placement data from the client-side ItemStack and sends a
     * StartConstructionPacket. Only used in state 3 (deployed).
     */
    @Unique
    private void ccr$sendConstructionPacket(ItemStack stack) {
        BlockPos anchor = stack.getOrDefault(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
        Rotation rotation = stack.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
        Mirror mirror = stack.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);
        PacketDistributor.sendToServer(new StartConstructionPacket(anchor, rotation, mirror));
    }

    /* ------------------------------------------------------------------ */
    /*  RMB — custom tool actions (state 3 only)                           */
    /* ------------------------------------------------------------------ */

    /**
     * Intercepts right-click when a custom Construction Planner tool is active.
     */
    @Inject(method = "onMouseInput", at = @At("HEAD"), cancellable = true)
    private void ccr$handleCustomToolRMB(int button, boolean pressed, CallbackInfoReturnable<Boolean> cir) {
        if (!active || !isDeployed() || !pressed || button != 1) return;

        ConstructionToolState.CustomTool tool = ConstructionToolState.getActiveTool();
        if (tool == ConstructionToolState.CustomTool.NONE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Find the planner (main hand, or inventory during drone view)
        ItemStack mainHand = DroneViewClientState.findActivePlanner(mc.player);
        if (mainHand.isEmpty()) {
            ConstructionToolState.setActiveTool(ConstructionToolState.CustomTool.NONE);
            return;
        }

        if (tool == ConstructionToolState.CustomTool.CONSTRUCT) {
            ccr$sendConstructionPacket(mainHand);
            // Clear client state immediately so Create deactivates cleanly
            ConstructionPlannerItem.Companion.clearSchematic(mainHand);
            mc.player.displayClientMessage(
                Component.translatable("gui.cbbees.schematic.construction_started")
                    .withStyle(style -> style.withColor(0x00FF00)),
                true
            );
            ConstructionToolState.setActiveTool(ConstructionToolState.CustomTool.NONE);
            cir.setReturnValue(true);
        } else if (tool == ConstructionToolState.CustomTool.UNSELECT) {
            // Clear both server-side and client-side so Create's SchematicHandler deactivates
            PacketDistributor.sendToServer(UnselectSchematicPacket.Companion.getINSTANCE());
            ConstructionPlannerItem.Companion.clearSchematic(mainHand);
            mc.player.displayClientMessage(
                Component.translatable("gui.cbbees.tool.unselect.done")
                    .withStyle(style -> style.withColor(0xFFAA88)),
                true
            );
            ConstructionToolState.setActiveTool(ConstructionToolState.CustomTool.NONE);
            cir.setReturnValue(true);
        } else if (tool == ConstructionToolState.CustomTool.PROGRAM) {
            // Read placement data and send a ProgramSchematicPacket
            String schematicFile = mainHand.get(AllDataComponents.SCHEMATIC_FILE);
            String owner = mainHand.get(AllDataComponents.SCHEMATIC_OWNER);
            if (schematicFile == null || owner == null) return;

            BlockPos anchor = mainHand.getOrDefault(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
            Rotation rotation = mainHand.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
            Mirror mirror = mainHand.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);

            SchematicProgram program = new SchematicProgram.Construction(
                schematicFile, anchor, rotation, mirror, owner
            );
            PacketDistributor.sendToServer(new ProgramSchematicPacket(program));
            mc.player.displayClientMessage(
                Component.translatable("cbbees.schematic.programmed")
                    .withStyle(style -> style.withColor(0x88CCFF)),
                true
            );
            ConstructionToolState.setActiveTool(ConstructionToolState.CustomTool.NONE);
            cir.setReturnValue(true);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Key shortcuts (state 3 only)                                       */
    /* ------------------------------------------------------------------ */

    @Inject(method = "onKeyInput", at = @At("HEAD"))
    private void ccr$handleKeys(int key, boolean pressed, CallbackInfo ci) {
        if (!active || !isDeployed() || !pressed) return;

        // R key — construction shortcut (Construction Planner only)
        if (AllKeys.INSTANCE.getSTART_ACTION().matches(key, 0)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack mainHand = DroneViewClientState.findActivePlanner(mc.player);
                if (!mainHand.isEmpty()) {
                    ccr$sendConstructionPacket(mainHand);
                    // Clear client state immediately so Create deactivates cleanly
                    ConstructionPlannerItem.Companion.clearSchematic(mainHand);
                    mc.player.displayClientMessage(
                        Component.translatable("gui.cbbees.schematic.construction_started")
                            .withStyle(style -> style.withColor(0x00FF00)),
                        true
                    );
                }
            }
        }

        // Backspace — stop tasks (any item)
        if (AllKeys.INSTANCE.getSTOP_ACTION().matches(key, 0)) {
            PacketDistributor.sendToServer(StopTasksPacket.getINSTANCE());
        }
    }
}
