package de.devin.cbbees.ponder

import com.simibubi.create.AllItems as CreateItems
import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.logistics.ports.PortBlock
import de.devin.cbbees.content.logistics.ports.PortState
import de.devin.cbbees.items.AllItems
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.element.ElementLink
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object BeehiveScenes {

    private class Flight(val bee: ElementLink<PonderBeeElement>, val from: Vec3, val to: Vec3)

    private fun flyTogether(scene: CreateSceneBuilder, flights: List<Flight>, ticks: Int) {
        flights.forEach { flight ->
            val yaw = Math.toDegrees(Math.atan2(flight.to.z - flight.from.z, flight.to.x - flight.from.x)).toFloat() - 90f
            scene.addInstruction { it.resolve(flight.bee)?.yRot = yaw }
        }
        for (i in 1..ticks) {
            val t = i.toDouble() / ticks
            flights.forEach { flight ->
                val position = flight.from.lerp(flight.to, t)
                scene.addInstruction { it.resolve(flight.bee)?.position = position }
            }
            scene.idle(1)
        }
    }

    private fun hideBees(scene: CreateSceneBuilder, bees: List<ElementLink<PonderBeeElement>>) =
        bees.forEach { bee -> scene.addInstruction { it.resolve(bee)?.isVisible = false } }

    private fun rangeBox(scene: CreateSceneBuilder, slot: Any, hive: BlockPos, radius: Int, color: PonderPalette, ticks: Int) {
        val box = AABB(
            (hive.x - radius).toDouble(), hive.y.toDouble(), (hive.z - radius).toDouble(),
            (hive.x + radius + 1).toDouble(), hive.y.toDouble(), (hive.z + radius + 1).toDouble()
        )
        scene.overlay().chaseBoundingBoxOutline(color, slot, box, ticks)
    }

    private fun above(util: SceneBuildingUtil, pos: BlockPos, height: Double = 1.0): Vec3 =
        util.vector().centerOf(pos).add(0.0, height, 0.0)

    fun intro(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("mechanical_beehive", "The Mechanical Beehive")
        scene.configureBasePlate(0, 0, 7)
        scene.showBasePlate()
        scene.idle(10)

        val hive = util.grid().at(3, 1, 4)
        val cog = util.grid().at(4, 1, 4)
        val funnel = util.grid().at(3, 2, 4)
        val siteFrom = util.grid().at(1, 1, 0)
        val siteTo = util.grid().at(5, 2, 0)
        val site = util.select().fromTo(siteFrom, siteTo)
        val siteCenter = util.vector().centerOf(util.grid().at(3, 1, 0))

        scene.overlay().showControls(util.vector().topOf(util.grid().at(3, 0, 2)), Pointing.DOWN, 60)
            .withItem(AllItems.CONSTRUCTION_PLANNER.asStack())
        PonderEffects.highlight(scene, PonderPalette.WHITE, siteFrom, siteTo, 80)
        scene.overlay().showText(80)
            .text("A build has been planned with the Construction Planner")
            .placeNearTarget()
            .pointAt(siteCenter)
        scene.idle(90)

        PonderEffects.highlight(scene, PonderPalette.RED, siteFrom, siteTo, 70)
        scene.overlay().showText(70)
            .text("But blocks don't place themselves")
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(siteCenter)
        scene.idle(80)

        scene.world().showSection(util.select().position(hive), Direction.DOWN)
        scene.idle(15)
        scene.overlay().showOutlineWithText(util.select().position(hive), 80)
            .text("The Mechanical Beehive is the home of your Mechanical Bees")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive, Direction.WEST))
        scene.idle(90)

        PonderEffects.highlight(scene, PonderPalette.RED, hive, hive, 70)
        scene.overlay().showText(70)
            .text("Without rotation, no bee leaves the hive")
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive, Direction.WEST))
        scene.idle(80)

        scene.world().showSection(util.select().position(cog), Direction.DOWN)
        scene.idle(10)
        BeeScenes.spinHive(scene, util, hive, cog)
        scene.effects().rotationSpeedIndicator(cog)
        PonderEffects.highlight(scene, PonderPalette.GREEN, hive, cog, 40)
        scene.idle(45)

        scene.world().showSection(util.select().position(funnel), Direction.DOWN)
        scene.idle(10)
        val dropped = scene.world().createItemEntity(above(util, funnel, 1.0), Vec3.ZERO, AllItems.MECHANICAL_BEE.asStack(8))
        scene.overlay().showOutlineWithText(util.select().position(funnel), 90)
            .text("Bees are inserted with funnels or hoppers")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(funnel, Direction.WEST))
        scene.idle(25)
        scene.world().modifyEntity(dropped, Entity::discard)
        scene.effects().indicateSuccess(hive)
        scene.idle(70)

        val spawn = above(util, hive, 1.5)
        val bees = (0..2).map { BeeScenes.createBee(scene, BeeType.CONSTRUCTION, spawn) }
        val targets = listOf(1, 3, 5).map { above(util, util.grid().at(it, 2, 0), 1.0) }
        scene.overlay().showText(90)
            .text("Once powered and filled, bees fly out to work on any job in range")
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(spawn)
        flyTogether(scene, bees.indices.map { Flight(bees[it], spawn, targets[it]) }, 40)
        scene.world().showSection(site, Direction.DOWN)
        for (x in 1..5) PonderEffects.sparkle(scene, util, util.grid().at(x, 2, 0))
        scene.idle(30)
        PonderEffects.highlight(scene, PonderPalette.GREEN, siteFrom, siteTo, 50)
        scene.idle(30)

        scene.overlay().showText(80)
            .text("When the work is done, they return to the hive to rewind their springs")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive, Direction.UP))
        flyTogether(scene, bees.indices.map { Flight(bees[it], targets[it], spawn) }, 40)
        hideBees(scene, bees)
        scene.idle(50)
        scene.markAsFinished()
    }

    fun speed(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("mechanical_beehive_speed", "Rotation Speed and Range")
        scene.configureBasePlate(0, 0, 9)
        scene.showBasePlate()
        scene.idle(10)

        val hive = util.grid().at(5, 1, 6)
        val cog = util.grid().at(6, 1, 6)
        val siteFrom = util.grid().at(0, 1, 0)
        val siteTo = util.grid().at(2, 1, 0)
        val siteCenter = util.vector().centerOf(util.grid().at(1, 1, 0))
        val range = Any()

        BeeScenes.showPoweredHive(scene, util, hive, cog)
        scene.world().setKineticSpeed(util.select().position(cog), 16f)
        scene.world().setKineticSpeed(util.select().position(hive), -16f)
        scene.idle(20)

        rangeBox(scene, range, hive, 2, PonderPalette.BLUE, 120)
        scene.overlay().showText(80)
            .text("A hive only takes jobs within its range")
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive, Direction.WEST))
        scene.idle(90)

        PonderEffects.highlight(scene, PonderPalette.RED, siteFrom, siteTo, 80)
        scene.overlay().showText(80)
            .text("This build is out of range, so no bee will come")
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(siteCenter)
        scene.idle(90)

        scene.world().setKineticSpeed(util.select().position(cog), 64f)
        scene.world().setKineticSpeed(util.select().position(hive), -64f)
        scene.effects().rotationSpeedIndicator(cog)
        rangeBox(scene, range, hive, 6, PonderPalette.BLUE, 160)
        scene.overlay().showOutlineWithText(util.select().position(cog), 100)
            .text("Faster rotation increases its range, how many bees fly at once and how fast they fly")
            .attachKeyFrame()
            .colored(PonderPalette.FAST)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(cog, Direction.UP))
        scene.idle(110)

        val spawn = above(util, hive, 1.5)
        val bees = (0..2).map { BeeScenes.createBee(scene, BeeType.CONSTRUCTION, spawn) }
        val targets = (0..2).map { above(util, util.grid().at(it, 1, 0), 1.0) }
        scene.overlay().showText(70)
            .text("Now the build is in reach")
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(siteCenter)
        flyTogether(scene, bees.indices.map { Flight(bees[it], spawn, targets[it]) }, 45)
        scene.world().showSection(util.select().fromTo(siteFrom, siteTo), Direction.DOWN)
        for (x in 0..2) PonderEffects.sparkle(scene, util, util.grid().at(x, 1, 0))
        scene.idle(25)
        flyTogether(scene, bees.indices.map { Flight(bees[it], targets[it], spawn) }, 45)
        hideBees(scene, bees)
        scene.idle(10)

        scene.overlay().showControls(util.vector().blockSurface(hive, Direction.WEST), Pointing.RIGHT, 90)
            .withItem(CreateItems.GOGGLES.asStack())
        scene.overlay().showText(90)
            .text("Engineer's Goggles show its network, bees and spring efficiency")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive, Direction.WEST))
        scene.idle(100)
        scene.markAsFinished()
    }

    fun networking(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("mechanical_beehive_networks", "Beehive Networks")
        scene.configureBasePlate(0, 0, 9)
        scene.showBasePlate()
        scene.idle(10)

        val hive1 = util.grid().at(6, 1, 6)
        val hive2 = util.grid().at(2, 1, 3)
        val vault = util.grid().at(8, 1, 4)
        val port = util.grid().at(8, 2, 4)
        val siteFrom = util.grid().at(0, 1, 1)
        val siteTo = util.grid().at(1, 1, 2)
        val siteCenter = util.vector().centerOf(util.grid().at(0, 1, 1))
        val range1 = Any()
        val range2 = Any()

        BeeScenes.showPoweredHive(scene, util, hive1, util.grid().at(7, 1, 6))
        scene.idle(20)
        rangeBox(scene, range1, hive1, 2, PonderPalette.BLUE, 160)
        scene.overlay().showText(70)
            .text("Each hive covers its own range")
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive1, Direction.WEST))
        scene.idle(80)

        PonderEffects.highlight(scene, PonderPalette.RED, siteFrom, siteTo, 80)
        scene.overlay().showText(80)
            .text("A build further away can't be reached")
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(siteCenter)
        scene.idle(90)

        BeeScenes.showPoweredHive(scene, util, hive2, util.grid().at(2, 1, 4))
        scene.idle(15)
        rangeBox(scene, range1, hive1, 2, PonderPalette.GREEN, 140)
        rangeBox(scene, range2, hive2, 2, PonderPalette.GREEN, 140)
        scene.overlay().showLine(PonderPalette.GREEN, above(util, hive1, 0.6), above(util, hive2, 0.6), 140)
        scene.overlay().showText(110)
            .text("Hives placed in each other's range join one network. Together they reach every build in any of their ranges")
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive2, Direction.UP))
        scene.idle(120)

        scene.world().showSection(util.select().position(vault), Direction.DOWN)
        scene.idle(5)
        scene.world().showSection(util.select().position(port), Direction.DOWN)
        scene.idle(10)
        scene.overlay().showOutlineWithText(util.select().fromTo(vault, port), 90)
            .text("Ports in range join the network too, so every bee in it can use them")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(port, Direction.WEST))
        scene.idle(100)

        val spawn1 = above(util, hive1, 1.5)
        val spawn2 = above(util, hive2, 1.5)
        val atPort = above(util, port, 0.5)
        val target1 = above(util, util.grid().at(1, 1, 2))
        val target2 = above(util, util.grid().at(0, 1, 1))
        val bee1 = BeeScenes.createBee(scene, BeeType.CONSTRUCTION, spawn1)
        val bee2 = BeeScenes.createBee(scene, BeeType.CONSTRUCTION, spawn2)
        scene.overlay().showText(100)
            .text("Bees of every hive work together. The closest hive with free bees is asked first")
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(siteCenter)
        scene.world().modifyBlock(port, { it.setValue(PortBlock.PORT_STATE, PortState.BUSY) as BlockState }, false)
        flyTogether(scene, listOf(Flight(bee1, spawn1, atPort), Flight(bee2, spawn2, target2)), 30)
        PonderEffects.signal(scene, util, port)
        scene.idle(10)
        scene.world().modifyBlock(port, { it.setValue(PortBlock.PORT_STATE, PortState.VALID) as BlockState }, false)
        flyTogether(scene, listOf(Flight(bee1, atPort, target1)), 50)
        scene.world().showSection(util.select().fromTo(siteFrom, siteTo), Direction.DOWN)
        for (pos in listOf(siteFrom, siteTo, util.grid().at(1, 1, 1), util.grid().at(0, 1, 2))) PonderEffects.sparkle(scene, util, pos)
        PonderEffects.highlight(scene, PonderPalette.GREEN, siteFrom, siteTo, 50)
        scene.idle(30)
        flyTogether(scene, listOf(Flight(bee1, target1, spawn1), Flight(bee2, target2, spawn2)), 45)
        hideBees(scene, listOf(bee1, bee2))
        scene.idle(30)
        scene.markAsFinished()
    }
}
