package de.devin.cbbees.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.devin.cbbees.content.schematics.BlockTransforms;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {

    @WrapOperation(
        method = "placeInWorld",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;mirror(Lnet/minecraft/world/level/block/Mirror;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState cbbees$mirror(BlockState state, Mirror mirror, Operation<BlockState> original) {
        BlockState transformed = BlockTransforms.mirror(state, mirror);
        return transformed != null ? transformed : original.call(state, mirror);
    }

    @WrapOperation(
        method = "placeInWorld",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;rotate(Lnet/minecraft/world/level/block/Rotation;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState cbbees$rotate(BlockState state, Rotation rotation, Operation<BlockState> original) {
        BlockState transformed = BlockTransforms.rotate(state, rotation);
        return transformed != null ? transformed : original.call(state, rotation);
    }
}
