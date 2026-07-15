package de.devin.cbbees.compat;

import de.devin.cbbees.content.backpack.PortableBeehiveFluidHandler;
import de.devin.cbbees.content.deployer.SchematicDeployerItemHandler;
import de.devin.cbbees.items.AllItems;
import de.devin.cbbees.registry.AllBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Java interop bridge for NeoForge's nullable capability context type-use annotations.
 * Kotlin 2.0 reports false-positive JAVA_TYPE_MISMATCH warnings for these APIs.
 */
/** Kotlin/Java nullability bridge for NeoForge capability APIs. */
public final class NeoForgeCapabilityCompat {
    private NeoForgeCapabilityCompat() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            AllBlockEntityTypes.INSTANCE.getMECHANICAL_BEEHIVE().get(),
            (be, side) -> be.getInventory()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            AllBlockEntityTypes.INSTANCE.getLOGISTICS_PORT().get(),
            (be, side) -> be.getItemHandler(be.getWorld())
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            AllBlockEntityTypes.INSTANCE.getSCHEMATIC_DEPLOYER().get(),
            (be, side) -> new SchematicDeployerItemHandler(be)
        );
        event.registerItem(
            Capabilities.FluidHandler.ITEM,
            (stack, context) -> new PortableBeehiveFluidHandler(stack),
            AllItems.INSTANCE.getPORTABLE_BEEHIVE().get()
        );
    }

    @Nullable
    public static IItemHandler getUnsidedItemHandler(Level level, BlockPos pos) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    }
}
