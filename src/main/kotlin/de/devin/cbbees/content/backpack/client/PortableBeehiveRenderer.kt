package de.devin.cbbees.content.backpack.client

import de.devin.cbbees.content.backpack.PortableBeehiveItem
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import software.bernie.geckolib.renderer.GeoArmorRenderer

class PortableBeehiveRenderer : GeoArmorRenderer<PortableBeehiveItem>(PortableBeehiveModel()) {

    fun updateRenderState(stack: ItemStack, entity: LivingEntity) {
        // No-op — model selection no longer depends on stack data
    }
}
