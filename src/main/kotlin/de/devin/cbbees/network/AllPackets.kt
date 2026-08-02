package de.devin.cbbees.network

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object AllPackets {

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("ccr")

        // Construction packet - starts building from a schematic
        registrar.playToServer(
            StartConstructionPacket.TYPE,
            StartConstructionPacket.STREAM_CODEC,
            StartConstructionPacket.Companion::handle
        )

        // Deconstruction packet - removes blocks within schematic bounds
        registrar.playToServer(
            StartDeconstructionPacket.TYPE,
            StartDeconstructionPacket.STREAM_CODEC,
            StartDeconstructionPacket.Companion::handle
        )

        // Stop packet - cancels all active bee tasks
        registrar.playToServer(
            StopTasksPacket.TYPE,
            StopTasksPacket.STREAM_CODEC,
            StopTasksPacket.Companion::handle
        )

        registrar.playToServer(
            RequestHiveJobsPacket.TYPE,
            RequestHiveJobsPacket.STREAM_CODEC,
            RequestHiveJobsPacket.Companion::handle
        )
        registrar.playToClient(
            HiveJobsSyncPacket.TYPE,
            HiveJobsSyncPacket.STREAM_CODEC,
            HiveJobsSyncPacket.Companion::handle
        )
        registrar.playToServer(
            CancelJobPacket.TYPE,
            CancelJobPacket.STREAM_CODEC,
            CancelJobPacket.Companion::handle
        )
        registrar.playToClient(
            BeeDebugSyncPacket.TYPE,
            BeeDebugSyncPacket.STREAM_CODEC,
            BeeDebugSyncPacket.Companion::handle
        )
        registrar.playToClient(
            NetworkSyncPacket.TYPE,
            NetworkSyncPacket.STREAM_CODEC,
            NetworkSyncPacket.Companion::handle
        )
        registrar.playToServer(
            SelectSchematicPacket.TYPE,
            SelectSchematicPacket.STREAM_CODEC,
            SelectSchematicPacket.Companion::handle
        )
        registrar.playToServer(
            PlannerTransformSyncPacket.TYPE,
            PlannerTransformSyncPacket.STREAM_CODEC,
            PlannerTransformSyncPacket.Companion::handle
        )
        registrar.playToServer(
            UnselectSchematicPacket.TYPE,
            UnselectSchematicPacket.STREAM_CODEC,
            UnselectSchematicPacket.Companion::handle
        )
        registrar.playToServer(
            InstantConstructionPacket.TYPE,
            InstantConstructionPacket.STREAM_CODEC,
            InstantConstructionPacket.Companion::handle
        )
        registrar.playToServer(
            ProgramSchematicPacket.TYPE,
            ProgramSchematicPacket.STREAM_CODEC,
            ProgramSchematicPacket.Companion::handle
        )
        registrar.playToServer(
            PlannerUploadPacket.TYPE,
            PlannerUploadPacket.STREAM_CODEC,
            PlannerUploadPacket.Companion::handle
        )
        registrar.playToServer(
            RequestPlayerJobsPacket.TYPE,
            RequestPlayerJobsPacket.STREAM_CODEC,
            RequestPlayerJobsPacket.Companion::handle
        )

        // Pickup packet - scans area for items and dispatches bumble bees
        registrar.playToServer(
            StartPickupPacket.TYPE,
            StartPickupPacket.STREAM_CODEC,
            StartPickupPacket.Companion::handle
        )

        // Grid upgrade packets
        registrar.playToServer(
            GridPlaceUpgradePacket.TYPE,
            GridPlaceUpgradePacket.STREAM_CODEC,
            GridPlaceUpgradePacket.Companion::handle
        )
        registrar.playToServer(
            GridRemoveUpgradePacket.TYPE,
            GridRemoveUpgradePacket.STREAM_CODEC,
            GridRemoveUpgradePacket.Companion::handle
        )

        // Deployer settings packet
        registrar.playToServer(
            DeployerSettingsPacket.TYPE,
            DeployerSettingsPacket.STREAM_CODEC,
            DeployerSettingsPacket.Companion::handle
        )

        // Job calculation progress (tick-counted, cached for late joiners)
        registrar.playToClient(
            JobProgressPacket.TYPE,
            JobProgressPacket.STREAM_CODEC,
            JobProgressPacket.Companion::handle
        )

        // Checkpoint-based bee flight plans
        registrar.playToClient(
            BeeFlightPlanPacket.TYPE,
            BeeFlightPlanPacket.STREAM_CODEC,
            BeeFlightPlanPacket.Companion::handle
        )
        registrar.playToClient(
            BeeRemovePacket.TYPE,
            BeeRemovePacket.STREAM_CODEC,
            BeeRemovePacket.Companion::handle
        )
        registrar.playToClient(
            BeeCheckpointConfirmPacket.TYPE,
            BeeCheckpointConfirmPacket.STREAM_CODEC,
            BeeCheckpointConfirmPacket.Companion::handle
        )

        // Drone view packets
        registrar.playToServer(
            ToggleDroneViewPacket.TYPE,
            ToggleDroneViewPacket.STREAM_CODEC,
            ToggleDroneViewPacket.Companion::handle
        )
        registrar.playToClient(
            DroneViewSyncPacket.TYPE,
            DroneViewSyncPacket.STREAM_CODEC,
            DroneViewSyncPacket.Companion::handle
        )
        registrar.playToServer(
            MoveDronePacket.TYPE,
            MoveDronePacket.STREAM_CODEC,
            MoveDronePacket.Companion::handle
        )
    }
}
