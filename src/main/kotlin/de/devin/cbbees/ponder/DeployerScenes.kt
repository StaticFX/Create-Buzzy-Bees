package de.devin.cbbees.ponder

import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.deployer.SchematicDeployerBlock
import de.devin.cbbees.items.AllItems
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.element.ElementLink
import net.createmod.ponder.api.element.WorldSectionElement
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.RedstoneLampBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

object DeployerScenes {

    private fun CreateSceneBuilder.pulse(deployer: BlockPos, shownAt: BlockPos = deployer) {
        world().modifyBlock(deployer, { it.setValue(SchematicDeployerBlock.POWERED, true) as BlockState }, false)
        effects().indicateRedstone(shownAt)
        idle(10)
        world().modifyBlock(deployer, { it.setValue(SchematicDeployerBlock.POWERED, false) as BlockState }, false)
    }

    private fun buildWith(scene: CreateSceneBuilder, util: SceneBuildingUtil, home: Vec3, blocks: List<BlockPos>) {
        val bees = blocks.map { PonderBees.createBee(scene, BeeType.CONSTRUCTION, home) }
        val targets = blocks.map { PonderBees.above(util, it, 0.8) }
        PonderBees.flyTogether(scene, bees.indices.map { PonderBees.Flight(bees[it], home, targets[it]) }, 35)
        blocks.forEach {
            scene.world().showSection(util.select().position(it), Direction.DOWN)
            PonderEffects.sparkle(scene, util, it)
        }
        scene.idle(15)
        PonderBees.flyTogether(scene, bees.indices.map { PonderBees.Flight(bees[it], targets[it], home) }, 35)
        PonderBees.hide(scene, bees)
    }

    fun intro(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("schematic_deployer", "The Schematic Deployer")
        scene.configureBasePlate(0, 0, 7)
        scene.showBasePlate()
        scene.idle(10)

        val hive = util.grid().at(5, 1, 5)
        val deployer = util.grid().at(2, 1, 4)
        val lever = util.grid().at(1, 1, 4)
        val comparator = util.grid().at(2, 1, 5)
        val lamp = util.grid().at(2, 1, 6)
        val hopper = util.grid().at(2, 2, 4)
        val siteFrom = util.grid().at(1, 1, 0)
        val siteTo = util.grid().at(3, 2, 0)
        val site = (1..3).flatMap { x -> (1..2).map { y -> util.grid().at(x, y, 0) } }

        PonderBees.showPoweredHive(scene, util, hive, util.grid().at(6, 1, 5))
        scene.world().showSection(util.select().fromTo(6, 1, 2, 6, 2, 2), Direction.DOWN)
        scene.idle(15)

        scene.overlay().showControls(util.vector().topOf(deployer.below()), Pointing.DOWN, 80)
            .withItem(AllItems.PROGRAMMED_SCHEMATIC.asStack())
        PonderEffects.highlight(scene, PonderPalette.WHITE, siteFrom, siteTo, 80)
        scene.overlay().showText(80)
            .text("A Programmed Schematic stores a construction or deconstruction job made with a Planner")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(util.grid().at(2, 1, 0)))
        scene.idle(90)

        PonderEffects.highlight(scene, PonderPalette.RED, siteFrom, siteTo, 70)
        scene.overlay().showText(70)
            .text("On its own, the stored job never starts")
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(util.vector().centerOf(util.grid().at(2, 1, 0)))
        scene.idle(80)

        scene.world().showSection(util.select().position(deployer), Direction.DOWN)
        scene.idle(15)
        scene.overlay().showControls(util.vector().topOf(deployer), Pointing.DOWN, 50)
            .withItem(AllItems.PROGRAMMED_SCHEMATIC.asStack())
            .rightClick()
        scene.overlay().showOutlineWithText(util.select().position(deployer), 90)
            .text("The Schematic Deployer holds a Programmed Schematic and starts its job")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(deployer, Direction.WEST))
        scene.idle(100)

        scene.world().showSection(util.select().position(lever), Direction.DOWN)
        scene.idle(15)
        scene.world().toggleRedstonePower(util.select().position(lever))
        scene.pulse(deployer)
        scene.overlay().showText(70)
            .text("A redstone pulse deploys the job")
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(lever, Direction.UP))
        buildWith(scene, util, PonderBees.above(util, hive, 1.5), site)
        PonderEffects.highlight(scene, PonderPalette.GREEN, siteFrom, siteTo, 50)
        scene.world().toggleRedstonePower(util.select().position(lever))
        scene.idle(40)

        scene.world().showSection(util.select().position(comparator).add(util.select().position(lamp)), Direction.DOWN)
        scene.idle(15)
        scene.world().toggleRedstonePower(util.select().position(comparator))
        scene.world().modifyBlock(lamp, { it.setValue(RedstoneLampBlock.LIT, true) as BlockState }, false)
        scene.overlay().showOutlineWithText(util.select().position(comparator), 100)
            .text("A comparator reads its state: empty (0), loaded (1), working (8) or just deployed (15)")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(comparator, Direction.UP))
        scene.idle(110)

        scene.world().showSection(util.select().position(hopper), Direction.DOWN)
        scene.idle(15)
        scene.overlay().showOutlineWithText(util.select().position(hopper), 90)
            .text("Hoppers and pipes can insert and take out Programmed Schematics")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hopper, Direction.WEST))
        scene.idle(100)
        scene.markAsFinished()
    }

    fun deployModes(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("schematic_deployer_automation", "Deploy Modes")
        scene.configureBasePlate(0, 0, 9)
        scene.showBasePlate()
        scene.idle(10)

        val deployer = util.grid().at(1, 1, 6)
        val hive = util.grid().at(4, 1, 7)
        val first = (1..2).flatMap { x -> (1..2).map { y -> util.grid().at(x, y, 2) } }
        val second = first.map { it.east(5) }
        val home = PonderBees.above(util, hive, 1.5)

        PonderBees.showPoweredHive(scene, util, hive, util.grid().at(4, 1, 8))
        val deployerSection: ElementLink<WorldSectionElement> =
            scene.world().showIndependentSection(util.select().position(deployer), Direction.DOWN)
        scene.idle(20)

        scene.pulse(deployer)
        buildWith(scene, util, home, first)
        scene.overlay().showText(90)
            .text("In Absolute mode, the job is built at the coordinates it was programmed with")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(util.grid().at(1, 1, 2)))
        scene.idle(100)

        scene.world().moveSection(deployerSection, util.vector().of(5.0, 0.0, 0.0), 30)
        scene.idle(40)
        scene.pulse(deployer, deployer.east(5))
        PonderEffects.highlight(scene, PonderPalette.RED, first.first(), first.last(), 80)
        scene.overlay().showText(80)
            .text("Moving the Deployer doesn't move the build")
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(util.vector().centerOf(util.grid().at(1, 1, 2)))
        scene.idle(90)

        scene.overlay().showControls(util.vector().topOf(deployer.east(5)), Pointing.DOWN, 60).rightClick()
        scene.overlay().showText(90)
            .text("In Relative mode, the build is placed relative to the Deployer instead")
            .attachKeyFrame()
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .pointAt(util.vector().topOf(deployer.east(5)))
        scene.idle(100)

        scene.pulse(deployer, deployer.east(5))
        buildWith(scene, util, home, second)
        PonderEffects.highlight(scene, PonderPalette.GREEN, second.first(), second.last(), 80)
        scene.overlay().showText(80)
            .text("Wherever the Deployer is placed, the build follows")
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(util.vector().centerOf(second.first()))
        scene.idle(90)

        scene.overlay().showText(80)
            .text("Relative mode can also rotate and mirror the build without reprogramming")
            .placeNearTarget()
            .pointAt(util.vector().topOf(deployer.east(5)))
        scene.idle(90)
        scene.markAsFinished()
    }

    fun selfReplicating(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("schematic_deployer_self_replicating", "Self-Replicating Deployers")
        scene.configureBasePlate(0, 0, 9)
        scene.showBasePlate()
        scene.idle(10)

        val hive = util.grid().at(4, 1, 7)
        val home = PonderBees.above(util, hive, 1.5)
        val deployers = listOf(util.grid().at(0, 1, 4), util.grid().at(3, 1, 4), util.grid().at(6, 1, 4))
        fun segment(k: Int) = listOf(util.grid().at(3 * k - 2, 1, 4), util.grid().at(3 * k - 1, 1, 4)) +
            (if (k < 3) listOf(deployers[k]) else emptyList())

        PonderBees.showPoweredHive(scene, util, hive, util.grid().at(5, 1, 7))
        scene.world().showSection(util.select().position(deployers[0]), Direction.DOWN)
        scene.idle(15)
        PonderEffects.highlight(scene, PonderPalette.WHITE, util.grid().at(1, 1, 4), deployers[1], 90)
        scene.overlay().showText(90)
            .text("A schematic can contain a Schematic Deployer itself, like this bridge segment")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(util.grid().at(2, 1, 4)))
        scene.idle(100)

        scene.pulse(deployers[0])
        buildWith(scene, util, home, segment(1))
        PonderEffects.highlight(scene, PonderPalette.RED, deployers[1], deployers[1], 70)
        scene.overlay().showText(70)
            .text("A freshly built Deployer would be empty")
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(deployers[1], Direction.UP))
        scene.idle(80)

        scene.overlay().showLine(PonderPalette.GREEN, util.vector().centerOf(deployers[0]), util.vector().centerOf(deployers[1]), 90)
        scene.overlay().showControls(util.vector().topOf(deployers[1]), Pointing.DOWN, 90)
            .withItem(AllItems.PROGRAMMED_SCHEMATIC.asStack())
        scene.overlay().showText(90)
            .text("Instead, it receives the program and settings of the Deployer that built it")
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(deployers[1], Direction.UP))
        scene.idle(100)

        scene.overlay().showText(100)
            .text("In Relative mode, each Deployer builds the next segment, carrying the program forward")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(deployers[2], Direction.UP))
        scene.pulse(deployers[1])
        buildWith(scene, util, home, segment(2))
        scene.pulse(deployers[2])
        buildWith(scene, util, home, segment(3))
        PonderEffects.highlight(scene, PonderPalette.GREEN, util.grid().at(1, 1, 4), util.grid().at(8, 1, 4), 60)
        scene.idle(70)
        scene.markAsFinished()
    }
}
