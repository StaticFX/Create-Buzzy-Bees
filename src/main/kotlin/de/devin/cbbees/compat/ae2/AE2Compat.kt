package de.devin.cbbees.compat.ae2

import de.devin.cbbees.content.logistics.storage.PortStorages
import net.neoforged.fml.ModList

object AE2Compat {

    val isLoaded: Boolean by lazy { ModList.get().isLoaded("ae2") }

    fun register() {
        if (isLoaded) PortStorages.register(MEPortStorage)
    }
}
