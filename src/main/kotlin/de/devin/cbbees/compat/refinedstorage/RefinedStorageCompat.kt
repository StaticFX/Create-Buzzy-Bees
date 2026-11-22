package de.devin.cbbees.compat.refinedstorage

import de.devin.cbbees.api.CBBeesApi
import net.neoforged.fml.ModList

object RefinedStorageCompat {

    val isLoaded: Boolean by lazy { ModList.get().isLoaded("refinedstorage") }

    fun register() {
        if (!isLoaded) return
        CBBeesApi.registerPortStorage(RSPortStorage)
        RefinedStorageSchematics.register()
    }
}
