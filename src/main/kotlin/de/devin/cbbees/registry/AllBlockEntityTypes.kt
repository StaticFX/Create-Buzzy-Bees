package de.devin.cbbees.registry

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual
import com.tterrag.registrate.util.entry.BlockEntityEntry
import com.tterrag.registrate.util.nullness.NonNullFunction
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.blocks.AllBlocks
import de.devin.cbbees.content.apiary.MechanicalApiaryBlockEntity
import de.devin.cbbees.content.apiary.MechanicalApiaryRenderer
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.beehive.MechanicalBeehiveRenderer
import de.devin.cbbees.content.deployer.SchematicDeployerBlockEntity
import de.devin.cbbees.content.deployer.client.SchematicDeployerRenderer
import de.devin.cbbees.content.logistics.dock.DockPortBlockEntity
import de.devin.cbbees.content.logistics.dock.DockPortRenderer
import de.devin.cbbees.content.logistics.ports.LogisticPortBlockEntity
import de.devin.cbbees.content.logistics.ports.LogisticsPortRenderer
import de.devin.cbbees.content.logistics.transport.TransportPortBlockEntity
import de.devin.cbbees.content.logistics.waggle.WagglePortBlockEntity
import de.devin.cbbees.content.logistics.transport.TransportPortRenderer
import net.minecraft.core.Direction

object AllBlockEntityTypes {

    val MECHANICAL_BEEHIVE: BlockEntityEntry<MechanicalBeehiveBlockEntity> = CreateBuzzyBeez.REGISTRATE
        .blockEntity("mechanical_beehive", ::MechanicalBeehiveBlockEntity)
        .visual({ SingleAxisRotatingVisual.of(AllPartialModels.SHAFTLESS_COGWHEEL) }, false)
        .validBlocks(AllBlocks.MECHANICAL_BEEHIVE)
        .renderer { NonNullFunction { MechanicalBeehiveRenderer(it) } }
        .register()

    val LOGISTICS_PORT: BlockEntityEntry<LogisticPortBlockEntity> = CreateBuzzyBeez.REGISTRATE
        .blockEntity("logistics_port", ::LogisticPortBlockEntity)
        .validBlocks(AllBlocks.LOGISTICS_PORT)
        .renderer { NonNullFunction { LogisticsPortRenderer(it) } }
        .register()

    val DOCK_PORT: BlockEntityEntry<DockPortBlockEntity> = CreateBuzzyBeez.REGISTRATE
        .blockEntity("dock_port", ::DockPortBlockEntity)
        .validBlocks(AllBlocks.DOCK_PORT)
        .renderer { NonNullFunction { DockPortRenderer(it) } }
        .register()

    val WAGGLE_PORT: BlockEntityEntry<WagglePortBlockEntity> = CreateBuzzyBeez.REGISTRATE
        .blockEntity("waggle_port", ::WagglePortBlockEntity)
        .validBlocks(AllBlocks.WAGGLE_PORT)
        .register()

    val SCHEMATIC_DEPLOYER: BlockEntityEntry<SchematicDeployerBlockEntity> = CreateBuzzyBeez.REGISTRATE
        .blockEntity("schematic_deployer", ::SchematicDeployerBlockEntity)
        .validBlocks(AllBlocks.SCHEMATIC_DEPLOYER)
        .renderer { NonNullFunction { SchematicDeployerRenderer(it) } }
        .register()

    val TRANSPORT_PORT: BlockEntityEntry<TransportPortBlockEntity> = CreateBuzzyBeez.REGISTRATE
        .blockEntity("transport_port", ::TransportPortBlockEntity)
        .validBlocks(AllBlocks.CARGO_PORT)
        .renderer { NonNullFunction { TransportPortRenderer(it) } }
        .register()

    val MECHANICAL_APIARY: BlockEntityEntry<MechanicalApiaryBlockEntity> = CreateBuzzyBeez.REGISTRATE
        .blockEntity("mechanical_apiary", ::MechanicalApiaryBlockEntity)
        .visual({
            SimpleBlockEntityVisualizer.Factory { context, be, partialTick ->
                OrientedRotatingVisual(context, be, partialTick, Direction.SOUTH, Direction.DOWN, Models.partial(AllPartialModels.SHAFT_HALF))
            }
        }, false)
        .validBlocks(AllBlocks.MECHANICAL_APIARY)
        .renderer { NonNullFunction { MechanicalApiaryRenderer(it) } }
        .register()

    fun register() {}
}
