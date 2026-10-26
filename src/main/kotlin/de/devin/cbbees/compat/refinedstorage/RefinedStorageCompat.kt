package de.devin.cbbees.compat.refinedstorage

import de.devin.cbbees.content.logistics.storage.PortStorages
import net.neoforged.fml.ModList

object RefinedStorageCompat {

    val isLoaded: Boolean by lazy { ModList.get().isLoaded("refinedstorage") }

    fun register() {
        if (isLoaded) PortStorages.register(RSPortStorage)
    }
}
