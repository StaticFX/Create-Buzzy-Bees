package de.devin.cbbees.compat.immersiveengineering

import de.devin.cbbees.api.CBBeesApi
import net.neoforged.fml.ModList

object ImmersiveEngineeringCompat {

    val isLoaded: Boolean by lazy { ModList.get().isLoaded("immersiveengineering") }

    fun register() {
        if (!isLoaded) return
        MultiblockSchematics.register()
        CBBeesApi.registerBeeAction(FormMultiblockAction.Codec)
    }
}
