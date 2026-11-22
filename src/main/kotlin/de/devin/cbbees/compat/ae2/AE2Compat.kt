package de.devin.cbbees.compat.ae2

import de.devin.cbbees.api.CBBeesApi
import net.neoforged.fml.ModList

object AE2Compat {

    val isLoaded: Boolean by lazy { ModList.get().isLoaded("ae2") }

    fun register() {
        if (!isLoaded) return
        CBBeesApi.registerPortStorage(MEPortStorage)
        CableBusSchematics.register()
    }
}
