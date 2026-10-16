package de.devin.cbbees.ponder

import com.simibubi.create.AllItems
import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.logistics.ports.LogisticPortBlock
import de.devin.cbbees.content.logistics.ports.PortState
import de.devin.cbbees.content.logistics.ports.PortType
import de.devin.cbbees.content.logistics.transport.TransportMode
import de.devin.cbbees.content.logistics.transport.TransportPortBlock
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.element.ElementLink
import net.createmod.ponder.foundation.element.ElementLinkImpl
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object BeeScenes {

    fun mechanicalBeehive(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)

        scene.title("mechanical_beehive", "The Mechanical Beehive")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val shaftPos = util.grid().at(3, 1, 2)
        val hivePos = util.grid().at(2, 1, 2)

        // Show the cogwheel/shaft
        scene.world().showSection(util.select().position(shaftPos), Direction.DOWN)
        scene.idle(5)

        // Show the mechanical beehive
        scene.world().showSection(util.select().position(hivePos), Direction.DOWN)
        scene.idle(5)

        // Start rotation
        scene.world().setKineticSpeed(util.select().position(shaftPos), 64f)
        scene.world().setKineticSpeed(util.select().position(hivePos), 64f)
        scene.idle(10)

        scene.overlay().showText(60)
            .text("The Mechanical Beehive is the heart of your mechanical bees.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hivePos, Direction.WEST))

        scene.idle(80)

        scene.overlay().showText(120)
            .text("Bees will spawn from it when a new task is available. Bees will also return to their hives when there are no more tasks.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hivePos, Direction.WEST))

        scene.idle(130)

        val funnelPos = util.grid().at(2, 2, 2)
        scene.world().showSection(util.select().position(funnelPos), Direction.DOWN)

        scene.idle(10)

        scene.overlay().showOutlineWithText(util.select().position(funnelPos), 60)
            .text("A funnel can be used to insert bees into your beehives.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(funnelPos, Direction.DOWN))

        scene.idle(80)

        val boundingBox = AABB(0.0, 1.0, 0.0, 5.0, 1.0, 5.0)

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, Object(), boundingBox, 100)

        scene.idle(20)

        scene.overlay().showText(90)
            .text("A Beehive's range is shown when looking at it. It scales with the supplied RPM.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(hivePos, Direction.WEST))

        scene.idle(90)

        scene.markAsFinished()
    }

    fun mechanicalBeehiveNetworks(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("mechanical_beehive_networks", "Mechanical Beehive Networks")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val hive1Pos = util.grid().at(3, 1, 2)
        scene.world().showSection(util.select().position(hive1Pos), Direction.DOWN)
        scene.world().setKineticSpeed(util.select().position(hive1Pos), 64f)

        scene.idle(15)

        scene.overlay().showOutlineWithText(util.select().position(hive1Pos), 90)
            .text("Mechanical Beehives will generate a unique network ID when placed.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive1Pos, Direction.DOWN))

        scene.idle(110)

        val boundingBox = AABB(1.0, 1.0, 0.0, 6.0, 1.0, 5.0)

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, Object(), boundingBox, 170)

        scene.idle(10)

        scene.overlay().showOutlineWithText(util.select().position(hive1Pos), 120)
            .text("The range of a Beehive also determines its network range. Any hive placed in that range will join the same network.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(hive1Pos, Direction.DOWN))

        scene.idle(140)


        val hive2Pos = util.grid().at(3, 1, 0)
        scene.world().showSection(util.select().position(hive2Pos), Direction.DOWN)
        scene.world().setKineticSpeed(util.select().position(hive2Pos), 64f)


        scene.idle(20)

        scene.overlay().showOutlineWithText(util.select().position(hive2Pos), 90)
            .text("Hives extend the range of your network.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive2Pos, Direction.DOWN))

        val boundingBoxCombined = AABB(1.0,1.0,-2.0,6.0,1.0,5.0)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, Object(), boundingBoxCombined, 650)


        scene.idle(220)

        scene.overlay().showOutlineWithText(util.select().position(hive2Pos), 120)
            .text("All hives in a network can contribute to the same task. Closer hives are prioritized.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive2Pos, Direction.DOWN))

        scene.idle(140)

        val portPos = util.grid().at(1, 1, 2)
        val port2Pos = util.grid().at(0, 1, 2)

        scene.world().showSection(util.select().position(portPos), Direction.DOWN)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 120)
            .text("Ports can join the network when placed inside its range.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(portPos, Direction.DOWN))

        scene.idle(150)

        scene.world().showSection(util.select().position(port2Pos), Direction.DOWN)

        scene.overlay().showOutlineWithText(util.select().position(port2Pos), 120)
            .text("Ports placed outside of the range can't be accessed by bees.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(port2Pos, Direction.DOWN))

        scene.idle(150)

        scene.markAsFinished()
    }

    fun introLogisticsPort(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("logistics_port", "The Logistics Port")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val vaultPos = util.grid().at(2, 1, 2)
        scene.world().showSection(util.select().position(vaultPos), Direction.DOWN)

        scene.idle(5)

        val portPos = util.grid().at(2, 2, 2)
        scene.world().showSection(util.select().position(portPos), Direction.DOWN)

        scene.idle(5)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 110)
            .text("The Logistics Port can be placed on any kind of inventory. A green bulb means it can interact with the inventory.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(portPos, Direction.WEST))

        scene.idle(120)

        val wrench = AllItems.WRENCH.asStack()
        scene.overlay().showControls(util.vector().blockSurface(portPos, Direction.UP), Pointing.DOWN, 110)
            .withItem(wrench)
            .rightClick()

        scene.overlay().showOutlineWithText(util.select().position(portPos), 90)
            .text("You can switch the port type to INSERT, by interacting with a wrench, to make it accept items.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(portPos, Direction.WEST))
            .attachKeyFrame()

        scene.idle(40)

        scene.world().modifyBlock(portPos, { state ->
            state.setValue(
                LogisticPortBlock.PORT_TYPE,
                PortType.INSERT
            ) as BlockState
        }, false)

        scene.idle(50)

        scene.markAsFinished()
    }

    private fun createBee(scene: CreateSceneBuilder, type: BeeType, position: Vec3, yRot: Float = 0f): ElementLink<PonderBeeElement> {
        val link = ElementLinkImpl(PonderBeeElement::class.java)
        scene.addInstruction { ponderScene ->
            val bee = PonderBeeElement(type, position, yRot)
            ponderScene.addElement(bee)
            ponderScene.linkElement(bee, link)
        }
        return link
    }

    private fun flyBee(
        scene: CreateSceneBuilder,
        bee: ElementLink<PonderBeeElement>,
        from: Vec3,
        to: Vec3,
        ticks: Int
    ) {
        val yaw = (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0).toFloat()
        scene.addInstruction { it.resolve(bee)?.yRot = yaw }
        for (i in 1..ticks) {
            val position = from.lerp(to, i.toDouble() / ticks)
            scene.addInstruction { it.resolve(bee)?.position = position }
            scene.idle(1)
        }
    }

    fun logisticsPortWithBees(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("logistics_port_with_bees", "The Logistics Port with Bees")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val vaultPos = util.grid().at(1, 1, 3)
        val portPos = util.grid().at(1, 2, 3)
        val hivePos = util.grid().at(3, 1, 1)

        scene.world().showSection(util.select().position(vaultPos), Direction.WEST)
        scene.world().showSection(util.select().position(portPos), Direction.WEST)
        scene.world().showSection(util.select().position(hivePos), Direction.WEST)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 140)
            .text("When a bee is spawned to work on a task, it will automatically find a Logistics Port to gather its items. Ports with a higher priority will be preferred.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hivePos, Direction.WEST))

        scene.idle(150)

        val beeSpawn = util.vector().centerOf(hivePos).add(0.0, 1.0, 0.0)
        val bee = createBee(scene, BeeType.CONSTRUCTION, beeSpawn)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 120)
            .text("When a bee focuses on a port, it will start moving towards it, the port will signal this with a glowing bulb.")
            .pointAt(util.vector().blockSurface(portPos, Direction.WEST))
            .placeNearTarget()
            .attachKeyFrame()


        scene.world().modifyBlock(portPos, { state ->
            state.setValue(
                LogisticPortBlock.PORT_STATE,
                PortState.BUSY
            ) as BlockState
        }, false)

        scene.idle(40)

        // Fly bee from hive to port
        val portTarget = util.vector().centerOf(portPos).add(0.0, 0.5, 0.0)
        flyBee(scene, bee, beeSpawn, portTarget, 60)

        scene.idle(80)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 120)
            .text("The bee will pick up the items from the port and then fly to its destination.")
            .pointAt(util.vector().blockSurface(portPos, Direction.WEST))


        scene.idle(100)

        // Fly bee from port to wood placement
        val woodPos = util.grid().at(3, 1, 3)
        val woodTarget = util.vector().centerOf(woodPos).add(0.0, 1.0, 0.0)
        flyBee(scene, bee, portTarget, woodTarget, 50)

        scene.idle(50)

        scene.world().showSection(util.select().position(woodPos), Direction.DOWN)

        scene.idle(20)

        scene.overlay().showOutlineWithText(util.select().position(hivePos), 120)
            .text("When finished, the bee will look for a new task or fly back to its hive.")
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(portPos, Direction.DOWN))
            .placeNearTarget()

        scene.idle(50)

        // Fly bee back to hive
        flyBee(scene, bee, woodTarget, beeSpawn, 50)

        scene.idle(70)

        // Remove the bee when it arrives home
        scene.addInstruction { it.resolve(bee)?.isVisible = false }

        scene.markAsFinished()
    }

    fun cargoPortIntro(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("cargo_port_intro", "The Cargo Port")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val vaultPos = util.grid().at(2, 1, 2)
        val portPos = util.grid().at(2, 2, 2)

        scene.world().showSection(util.select().position(vaultPos), Direction.DOWN)
        scene.world().showSection(util.select().position(portPos), Direction.DOWN)

        scene.idle(10)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 120)
            .text("The Cargo Port is used to move items around your network using the Mechanical Bumble Bee.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(portPos, Direction.WEST))

        scene.idle(145)

        scene.overlay().showOutlineWithText(util.select().position(portPos), 120)
            .text("The Cargo Port can be placed on any kind of inventory. A green bulb means it can interact with the inventory.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(portPos, Direction.WEST))

        scene.idle(145)

        val wrench = AllItems.WRENCH.asStack()
        scene.overlay().showControls(portPos.center.add(.5, 0.0, 0.0), Pointing.RIGHT, 110)
            .withItem(wrench)
            .rightClick()

        scene.overlay().showOutlineWithText(util.select().position(portPos), 90)
            .text("You can switch the port type to INSERT, by interacting with a wrench, to make it accept items.")
            .placeNearTarget()
            .attachKeyFrame()

        scene.idle(40)

        scene.world().modifyBlock(portPos, { state ->
            state.setValue(
                TransportPortBlock.TRANSPORT_MODE,
                TransportMode.REQUESTER
            ) as BlockState
        }, false)

        scene.idle(90)

        scene.markAsFinished()
    }

    fun cargoPortWithBees(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("cargo_port_with_bees", "The Cargo Port with Bees")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val vault1Pos = util.grid().at(3, 1, 1)
        val vault2Pos = util.grid().at(1, 1, 3)

        val port1Pos = util.grid().at(3, 2, 1)
        val port2Pos = util.grid().at(1, 2, 3)

        scene.world().showSection(util.select().position(vault1Pos), Direction.DOWN)
        scene.world().showSection(util.select().position(vault2Pos), Direction.DOWN)
        scene.world().showSection(util.select().position(port1Pos), Direction.DOWN)
        scene.world().showSection(util.select().position(port2Pos), Direction.DOWN)

        scene.overlay().showOutlineWithText(util.select().position(port1Pos), 90)
            .text("Cargo ports can be configured for a certain frequency.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(port1Pos, Direction.WEST))

        val redstone = Items.REDSTONE

        scene.idle(120)

        scene.overlay().showControls(util.vector().blockSurface(port1Pos, Direction.WEST), Pointing.LEFT, 40)
            .withItem(redstone.defaultInstance)
            .rightClick()

        scene.idle(50)

        scene.overlay().showControls(util.vector().blockSurface(port2Pos, Direction.WEST), Pointing.LEFT, 40)
            .withItem(redstone.defaultInstance)
            .rightClick()
        scene.idle(50)

        scene.overlay().showLine(PonderPalette.GREEN, port1Pos.center, port2Pos.center, 110)

        scene.idle(20)

        scene.overlay().showOutlineWithText(util.select().position(port1Pos), 90)
            .text("When cargo ports have matching frequencies, a line will be displayed between them.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(port1Pos, Direction.DOWN))

        scene.idle(100)

        scene.overlay().showOutlineWithText(util.select().position(port1Pos), 90)
            .text("When a Cargo Port is placed inside a network, it will periodically scan for new items.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(port1Pos, Direction.DOWN))

        scene.idle(100)

        scene.overlay().showOutlineWithText(util.select().position(port1Pos), 90)
            .text("When a new item is detected, a new task will be created for pickup.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(port1Pos, Direction.DOWN))

        scene.idle(110)

        val beeSpawn = util.vector().centerOf(port1Pos).add(0.0, 1.0, 0.0)
        val bee = createBee(scene, BeeType.TRANSPORT, beeSpawn)

        scene.idle(30)

        scene.overlay().showOutlineWithText(util.select().position(port1Pos), 90)
            .text("A Mechanical Bumble Bee will pick it up and bring it to the closest matching requesting Cargo Port.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(port1Pos, Direction.DOWN))

        scene.idle(30)

        flyBee(scene, bee, beeSpawn, util.vector().centerOf(port2Pos).add(0.0, 1.0, 0.0), 50)

        scene.idle(70)

        scene.markAsFinished()
    }

    fun mechanicalBee(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("mechanical_bee", "The Mechanical Bees")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val mechanicalBeePos = util.grid().at(2,2,2)
        val mechanicalBeeSpawnPos = util.vector().centerOf(mechanicalBeePos)
        val mechanicalBumbleBeePos = util.grid().at(1,2,2)
        val mechanicalBumbleBeeSpawnPos = util.vector().centerOf(mechanicalBumbleBeePos)

        createBee(scene, BeeType.CONSTRUCTION, mechanicalBeeSpawnPos, 180f)

        createBee(scene, BeeType.TRANSPORT, mechanicalBumbleBeeSpawnPos, 180f)

        scene.idle(20)

        util.select().position(2, 2, 2)

        scene.overlay().showText( 90)
            .text("Mechanical Bees have an internal spring, which is consumed when flying and finishing tasks.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(mechanicalBeePos, Direction.UP))

        scene.idle(110)

        scene.overlay().showText(120)
            .text("The spring is recharged when empty at the Bees spawn hive.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(mechanicalBeePos, Direction.UP))


        scene.idle(130)

        scene.overlay().showText(120)
            .text("The Mechanical Bee can place/destroy blocks.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(mechanicalBeePos, Direction.UP))


        scene.idle(130)

        util.select().position(1, 2, 2)


        scene.overlay().showText( 120)
            .text("The Mechanical Bumble Bee can pick up items from Cargo ports and transport them to other Cargo Ports.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(mechanicalBumbleBeePos, Direction.UP))


        scene.idle(130)

        scene.markAsFinished()
    }


}
