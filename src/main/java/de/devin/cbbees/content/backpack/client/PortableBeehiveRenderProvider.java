package de.devin.cbbees.content.backpack.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/** Provides the cached GeckoLib armor renderer for the portable beehive. */
public final class PortableBeehiveRenderProvider implements GeoRenderProvider {
    private PortableBeehiveRenderer renderer;

    @Override
    public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
        @Nullable T livingEntity,
        ItemStack itemStack,
        @Nullable EquipmentSlot equipmentSlot,
        @Nullable HumanoidModel<T> original
    ) {
        if (renderer == null) {
            renderer = new PortableBeehiveRenderer();
        }

        return renderer;
    }
}
