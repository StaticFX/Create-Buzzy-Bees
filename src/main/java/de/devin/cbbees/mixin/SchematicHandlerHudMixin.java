package de.devin.cbbees.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.SchematicInstances;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import de.devin.cbbees.content.drone.client.DroneViewClientState;
import de.devin.cbbees.content.schematics.ConstructionPlannerItem;
import de.devin.cbbees.content.schematics.client.ConstructionToolState;
import de.devin.cbbees.items.AllItems;
import de.devin.cbbees.content.deployer.SchematicProgram;
import de.devin.cbbees.network.PlannerTransformSyncPacket;
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
    @Shadow private int activeHotbarSlot;
    @Shadow private int syncCooldown;
    @Shadow private ItemStack activeSchematicItem;
    @Shadow public abstract boolean isDeployed();

    /* ------------------------------------------------------------------ */
    /*  Construction packet helper                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Copies Create's live transformation into the planner ItemStack and mirrors
     * it to the server. Create's own SchematicSyncPacket ignores this custom
     * item, so without this packet a slot change reloads the original anchor.
     */
    @Unique
    private boolean ccr$persistPlannerTransformation() {
        ItemStack stack = activeSchematicItem;
        if (stack == null || stack.isEmpty()) return false;
        if (!AllItems.INSTANCE.getCONSTRUCTION_PLANNER().isIn(stack)) return false;
        if (!stack.has(AllDataComponents.SCHEMATIC_FILE)) return false;

        var transform = CreateClient.SCHEMATIC_HANDLER.getTransformation();
        if (transform == null) return false;

        var settings = transform.toSettings();
        BlockPos anchor = transform.getAnchor();
        Rotation rotation = settings.getRotation();
        Mirror mirror = settings.getMirror();
        boolean deployed = stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false);

        stack.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor);
        stack.set(AllDataComponents.SCHEMATIC_ROTATION, rotation);
        stack.set(AllDataComponents.SCHEMATIC_MIRROR, mirror);
        SchematicInstances.clearHash(stack);

        PacketDistributor.sendToServer(new PlannerTransformSyncPacket(
            activeHotbarSlot, anchor, rotation, mirror, deployed
        ));
        return true;
    }

    /**
     * Reads placement data from the live transformation and starts construction.
     */
    @Unique
    private void ccr$sendConstructionPacket(ItemStack stack) {
        ccr$persistPlannerTransformation();
        var transform = CreateClient.SCHEMATIC_HANDLER.getTransformation();
        var settings = transform.toSettings();
        BlockPos anchor = transform.getAnchor();
        Rotation rotation = settings.getRotation();
        Mirror mirror = settings.getMirror();
        PacketDistributor.sendToServer(new StartConstructionPacket(anchor, rotation, mirror));
    }

    /**
     * Every Move XZ / Move Y / Rotate / Mirror operation ends in markDirty().
     * Persist immediately instead of waiting for Create's delayed sync packet,
     * because that packet only accepts Create's own Schematic item server-side.
     */
    @Inject(method = "markDirty", at = @At("TAIL"))
    private void ccr$persistPlannerTransformOnDirty(CallbackInfo ci) {
        if (ccr$persistPlannerTransformation()) {
            // Create's delayed SchematicSyncPacket rejects custom planner items.
            // Cancel that redundant delayed send after our own sync succeeds.
            syncCooldown = 0;
        }
    }

    /**
     * Creative Print copies the ItemStack itself into SchematicPlacePacket. Make
     * sure that copy contains the fine-tuned live position first.
     */
    @Inject(method = "printInstantly", at = @At("HEAD"))
    private void ccr$persistPlannerTransformBeforePrint(CallbackInfo ci) {
        ccr$persistPlannerTransformation();
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
            // Read live placement data from Create's SchematicTransformation.
            // The ItemStack components only store the first deploy anchor and are not
            // updated immediately by Move XZ / Move Y / Rotate / Mirror tools. Those
            // tools update the transformation and only sync later via Create's normal
            // SchematicSyncPacket. Programming from the ItemStack therefore rolled the
            // schematic back to its first clicked position. This must match Construct.
            String schematicFile = mainHand.get(AllDataComponents.SCHEMATIC_FILE);
            String owner = mainHand.get(AllDataComponents.SCHEMATIC_OWNER);
            if (schematicFile == null || owner == null) return;

            ccr$persistPlannerTransformation();
            var transform = CreateClient.SCHEMATIC_HANDLER.getTransformation();
            var settings = transform.toSettings();
            BlockPos anchor = transform.getAnchor();
            Rotation rotation = settings.getRotation();
            Mirror mirror = settings.getMirror();

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
