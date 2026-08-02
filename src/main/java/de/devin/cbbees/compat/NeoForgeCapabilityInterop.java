/*
 * Small Java interop boundary for NeoForge capability APIs.
 *
 * NeoForge 1.21.1 uses TYPE_USE nullability in capability context generics,
 * such as BlockCapability<IItemHandler, @Nullable Direction>. Kotlin 2.0.0
 * reports false JAVA_TYPE_MISMATCH diagnostics for those signatures. Keeping
 * only the affected calls in Java lets both compilers type-check the official
 * API without suppressions, unchecked casts, or changed capability behavior.
 */
package de.devin.cbbees.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public final class NeoForgeCapabilityInterop {
    private NeoForgeCapabilityInterop() {}

    @FunctionalInterface
    public interface BlockItemHandlerProvider<BE extends BlockEntity> {
        @Nullable
        IItemHandler get(BE blockEntity);
    }

    @FunctionalInterface
    public interface FluidItemHandlerProvider {
        @Nullable
        IFluidHandlerItem get(ItemStack stack);
    }

    /**
     * Registers the standard sided block item capability with a provider that
     * intentionally exposes the same handler from every side.
     */
    public static <BE extends BlockEntity> void registerUnsidedBlockItemHandler(
        RegisterCapabilitiesEvent event,
        BlockEntityType<BE> blockEntityType,
        BlockItemHandlerProvider<BE> provider
    ) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            blockEntityType,
            (blockEntity, side) -> provider.get(blockEntity)
        );
    }

    /** Registers the standard context-free item fluid capability. */
    public static void registerFluidItemHandler(
        RegisterCapabilitiesEvent event,
        ItemLike item,
        FluidItemHandlerProvider provider
    ) {
        event.registerItem(
            Capabilities.FluidHandler.ITEM,
            (stack, context) -> provider.get(stack),
            item
        );
    }

    /**
     * Queries the standard sided item capability without selecting a side.
     * A null direction is NeoForge's documented unsided-query behavior.
     */
    @Nullable
    public static IItemHandler getUnsidedItemHandler(Level level, BlockPos pos) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    }
}
