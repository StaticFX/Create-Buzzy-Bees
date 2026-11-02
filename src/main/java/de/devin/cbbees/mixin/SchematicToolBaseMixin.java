package de.devin.cbbees.mixin;

import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import de.devin.cbbees.content.drone.client.DroneAim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets Create's schematic tools aim from the drone (straight down) during drone view,
 * instead of from the player standing still on the ground.
 */
@Mixin(value = SchematicToolBase.class, remap = false)
public abstract class SchematicToolBaseMixin {

    @Shadow protected BlockPos selectedPos;
    @Shadow protected Vec3 chasingSelectedPos;
    @Shadow protected Vec3 lastChasingSelectedPos;
    @Shadow protected boolean selectIgnoreBlocks;
    @Shadow protected int selectionRange;
    @Shadow protected boolean schematicSelected;
    @Shadow protected Direction selectedFace;

    @Inject(method = "updateTargetPos", at = @At("HEAD"), cancellable = true)
    private void ccr$aimFromDrone(CallbackInfo ci) {
        DroneAim.Result aim = DroneAim.current(selectIgnoreBlocks, selectionRange);
        if (aim == null) return;
        ci.cancel();

        selectedFace = aim.getSchematicFace();
        schematicSelected = selectedFace != null;

        boolean snap = selectedPos == null;
        selectedPos = aim.getTarget();
        if (snap && selectedPos != null)
            lastChasingSelectedPos = chasingSelectedPos = Vec3.atLowerCornerOf(selectedPos);
    }
}
