package de.devin.cbbees.registry

import com.tterrag.registrate.builders.MenuBuilder.ForgeMenuFactory
import com.tterrag.registrate.builders.MenuBuilder.ScreenFactory
import com.tterrag.registrate.util.entry.MenuEntry
import com.tterrag.registrate.util.nullness.NonNullSupplier
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.backpack.BeehiveContainer
import de.devin.cbbees.content.backpack.BeehiveScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.world.inventory.AbstractContainerMenu

object AllMenuTypes {
    
    val CONSTRUCTOR_BACKPACK: MenuEntry<BeehiveContainer> = register(
        "constructor_backpack",
        { type, containerId, inv, buf -> BeehiveContainer(type, containerId, inv, buf!!) },
        { ScreenFactory { menu, inv, title -> BeehiveScreen(menu, inv, title) } }
    )
    
    private fun <C : AbstractContainerMenu, S> register(
        name: String,
        factory: ForgeMenuFactory<C>,
        screenFactory: NonNullSupplier<ScreenFactory<C, S>>
    ): MenuEntry<C> where S : Screen, S : MenuAccess<C> {
        return CreateBuzzyBeez.REGISTRATE
            .menu(name, factory, screenFactory)
            .register()
    }
    
    fun register() {
        // Called to classload and register all menu types
    }
}
