package de.devin.cbbees.mixin;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import com.simibubi.create.foundation.utility.RaycastHelper;
import de.devin.cbbees.content.drone.client.DroneViewClientState;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link SchematicToolBase} to redirect raycasting during drone view.
 *
 * <p>Create's tool system uses the player's eye position and look direction for all
 * schematic interaction (deploying, moving, selecting faces). During drone view,
 * the camera is on the drone looking straight down, but the player entity is
 * stationary on the ground with a frozen view direction. This mixin replaces
 * the raycast origin/direction with the drone's position looking downward.</p>
 */
@Mixin(value = SchematicToolBase.class, remap = false)
public abstract class SchematicToolBaseMixin {

    @Shadow protected BlockPos selectedPos;
    @Shadow protected Vec3 chasingSelectedPos;
    @Shadow protected Vec3 lastChasingSelectedPos;
    @Shadow protected boolean selectIgnoreBlocks;
    @Shadow protected int selectionRange;
    @Shadow protected boolean schematicSelected;
    @Shadow protected net.minecraft.core.Direction selectedFace;

    @Inject(method = "updateTargetPos", at = @At("HEAD"), cancellable = true)
    private void ccr$droneViewTargetPos(CallbackInfo ci) {
        if (!DroneViewClientState.INSTANCE.getActive()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        var drone = mc.level != null ? mc.level.getEntity(DroneViewClientState.INSTANCE.getDroneEntityId()) : null;
        if (drone == null) return;

        ci.cancel();

        Vec3 dronePos = drone.position();
        // Look straight down from the drone
        Vec3 droneEnd = dronePos.add(0, -75, 0);

        SchematicHandler handler = CreateClient.SCHEMATIC_HANDLER;

        // Select Blueprint (schematic face selection for move/rotate tools)
        if (handler.isDeployed()) {
            SchematicTransformation transformation = handler.getTransformation();
            AABB localBounds = handler.getBounds();

            Vec3 start = transformation.toLocalSpace(dronePos);
            Vec3 end = transformation.toLocalSpace(droneEnd);
            RaycastHelper.PredicateTraceResult result =
                RaycastHelper.rayTraceUntil(start, end, pos -> localBounds.contains(VecHelper.getCenterOf(pos)));

            schematicSelected = !result.missed();
            selectedFace = schematicSelected ? result.getFacing() : null;
        }

        boolean snap = this.selectedPos == null;

        // Select location at distance (for tools like FlipTool that ignore blocks)
        if (selectIgnoreBlocks) {
            // Place at drone's XZ, on the ground below
            selectedPos = BlockPos.containing(dronePos.add(0, -selectionRange, 0));
            if (snap)
                lastChasingSelectedPos = chasingSelectedPos = Vec3.atLowerCornerOf(selectedPos);
            return;
        }

        // Select targeted Block — raycast straight down from drone
        selectedPos = null;
        ClipContext ctx = new ClipContext(dronePos, droneEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        BlockHitResult trace = player.level().clip(ctx);
        if (trace == null || trace.getType() != HitResult.Type.BLOCK)
            return;

        BlockPos hit = BlockPos.containing(trace.getLocation());
        boolean replaceable = player.level().getBlockState(hit).canBeReplaced();
        if (trace.getDirection().getAxis().isVertical() && !replaceable)
            hit = hit.relative(trace.getDirection());
        selectedPos = hit;
        if (snap)
            lastChasingSelectedPos = chasingSelectedPos = Vec3.atLowerCornerOf(selectedPos);
    }
}
