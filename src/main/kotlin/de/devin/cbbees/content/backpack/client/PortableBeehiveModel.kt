package de.devin.cbbees.content.backpack.client

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.backpack.PortableBeehiveItem
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class PortableBeehiveModel : GeoModel<PortableBeehiveItem>() {

    override fun getModelResource(animatable: PortableBeehiveItem): ResourceLocation {
        return CreateBuzzyBeez.asResource("geo/portable_beehive.geo.json")
    }

    override fun getTextureResource(animatable: PortableBeehiveItem): ResourceLocation {
        return CreateBuzzyBeez.asResource("textures/armor/portabel_beehive_texture.png")
    }

    override fun getAnimationResource(animatable: PortableBeehiveItem): ResourceLocation {
        return CreateBuzzyBeez.asResource("animations/portable_beehive.animation.json")
    }
}
