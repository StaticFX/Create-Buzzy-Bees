package de.devin.cbbees.registry

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.content.upgrades.UpgradeGrid
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.util.ExtraCodecs
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object AllDataComponents {

    private val REGISTER: DeferredRegister.DataComponents =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateBuzzyBeez.ID)

    val HONEY_FUEL: DeferredHolder<DataComponentType<*>, DataComponentType<Int>> =
        REGISTER.registerComponentType("honey_fuel") { builder ->
            builder.persistent(ExtraCodecs.NON_NEGATIVE_INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
        }

    val UPGRADE_GRID: DeferredHolder<DataComponentType<*>, DataComponentType<UpgradeGrid>> =
        REGISTER.registerComponentType("upgrade_grid") { builder ->
            builder.persistent(UpgradeGrid.CODEC)
                .networkSynchronized(UpgradeGrid.STREAM_CODEC)
        }

    val SCHEMATIC_PROGRAM: DeferredHolder<DataComponentType<*>, DataComponentType<SchematicProgram>> =
        REGISTER.registerComponentType("schematic_program") { builder ->
            builder.persistent(SchematicProgram.CODEC)
                .networkSynchronized(SchematicProgram.STREAM_CODEC)
        }

    fun register(bus: IEventBus) {
        REGISTER.register(bus)
    }
}
