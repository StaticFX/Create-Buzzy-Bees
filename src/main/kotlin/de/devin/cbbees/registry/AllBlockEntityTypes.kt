package de.devin.cbbees.registry

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual
import com.tterrag.registrate.util.entry.BlockEntityEntry
import com.tterrag.registrate.util.nullness.NonNullFunction
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.blocks.AllBlocks
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.beehive.MechanicalBeehiveRenderer
import de.devin.cbbees.content.deployer.SchematicDeployerBlockEntity
import de.devin.cbbees.content.deployer.client.SchematicDeployerRenderer
import de.devin.cbbees.content.logistics.ports.LogisticPortBlockEntity
import de.devin.cbbees.content.logistics.ports.LogisticsPortRenderer
import de.devin.cbbees.content.logistics.transport.TransportPortBlockEntity
import de.devin.cbbees.content.logistics.transport.TransportPortRenderer

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

    fun register() {}
}
