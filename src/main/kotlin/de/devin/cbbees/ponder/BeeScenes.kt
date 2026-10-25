package de.devin.cbbees.ponder

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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

object BeeScenes {

    private fun CreateSceneBuilder.portState(pos: BlockPos, state: PortState) =
        world().modifyBlock(pos, { it.setValue(PortBlock.PORT_STATE, state) as BlockState }, false)

    private fun gather(scene: CreateSceneBuilder, util: SceneBuildingUtil, bee: ElementLink<PonderBeeElement>, from: Vec3, port: BlockPos, stack: ItemStack): Vec3 {
        val atPort = PonderBees.above(util, port, 0.5)
        scene.portState(port, PortState.BUSY)
        PonderBees.fly(scene, bee, from, atPort, 30)
        PonderEffects.signal(scene, util, port)
        PonderBees.carry(scene, bee, stack)
        scene.idle(10)
        scene.portState(port, PortState.VALID)
        return atPort
    }

    private fun build(scene: CreateSceneBuilder, util: SceneBuildingUtil, bee: ElementLink<PonderBeeElement>, from: Vec3, blocks: List<BlockPos>): Vec3 {
        var position = from
        blocks.forEach { pos ->
            val target = PonderBees.above(util, pos)
            PonderBees.fly(scene, bee, position, target, 15)
            scene.world().showSection(util.select().position(pos), Direction.DOWN)
            PonderEffects.sparkle(scene, util, pos)
            scene.idle(8)
            position = target
        }
        return position
    }

    fun types(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("mechanical_bee", "Mechanical Bees")
        scene.configureBasePlate(0, 0, 7)
        scene.showBasePlate()
        scene.idle(10)

        val builderHive = util.grid().at(2, 1, 5)
        val carrierHive = util.grid().at(4, 1, 5)
        val vault = util.grid().at(0, 1, 2)
        val port = util.grid().at(0, 2, 2)
        val stone = util.grid().at(3, 1, 2)
        val provider = util.grid().at(6, 1, 3)
        val providerPort = util.grid().at(6, 2, 3)
        val requester = util.grid().at(6, 1, 0)
        val requesterPort = util.grid().at(6, 2, 0)

        PonderBees.showPoweredHive(scene, util, builderHive, util.grid().at(1, 1, 5))
        scene.world().showSection(util.select().position(vault).add(util.select().position(port)), Direction.DOWN)
        scene.world().showSection(util.select().position(stone), Direction.DOWN)
        scene.idle(15)

        val home = PonderBees.above(util, builderHive, 1.5)
        val bee = PonderBees.createBee(scene, BeeType.CONSTRUCTION, home)
        scene.overlay().showOutlineWithText(util.select().position(builderHive), 90)
            .text("The Mechanical Bee builds and takes down blocks, and places fluids")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(builderHive, Direction.UP))
        var position = gather(scene, util, bee, home, port, ItemStack(Items.OAK_PLANKS))
        position = build(scene, util, bee, position, (1..3).map { util.grid().at(it, 1, 0) })
        PonderBees.carry(scene, bee, ItemStack.EMPTY)
        val atStone = PonderBees.above(util, stone)
        PonderBees.fly(scene, bee, position, atStone, 15)
        scene.world().hideSection(util.select().position(stone), Direction.UP)
        PonderEffects.sparkle(scene, util, stone)
        PonderBees.carry(scene, bee, ItemStack(Items.STONE))
        scene.idle(15)
        PonderBees.fly(scene, bee, atStone, home, 30)
        PonderBees.hide(scene, listOf(bee))
        scene.idle(20)

        PonderBees.showPoweredHive(scene, util, carrierHive, util.grid().at(5, 1, 5))
        scene.world().showSection(util.select().fromTo(provider, providerPort).add(util.select().fromTo(requester, requesterPort)), Direction.DOWN)
        scene.idle(15)
        val carrierHome = PonderBees.above(util, carrierHive, 1.5)
        val bumble = PonderBees.createBee(scene, BeeType.TRANSPORT, carrierHome)
        scene.overlay().showOutlineWithText(util.select().position(carrierHive), 90)
            .text("The Mechanical Bumble Bee carries items between Cargo Ports, up to three stacks per trip")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(carrierHive, Direction.UP))
        val atProvider = gather(scene, util, bumble, carrierHome, providerPort, ItemStack(Items.WHEAT, 64))
        val atRequester = PonderBees.above(util, requesterPort, 0.5)
        PonderBees.fly(scene, bumble, atProvider, atRequester, 30)
        PonderEffects.signal(scene, util, requesterPort)
        PonderBees.carry(scene, bumble, ItemStack.EMPTY)
        scene.idle(15)
        PonderBees.fly(scene, bumble, atRequester, carrierHome, 30)
        PonderBees.hide(scene, listOf(bumble))
        scene.idle(10)

        PonderEffects.highlight(scene, PonderPalette.BLUE, builderHive, carrierHive, 90)
        scene.overlay().showText(90)
            .text("Each hive keeps one type of bee")
            .attachKeyFrame()
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(util.grid().at(3, 1, 5), Direction.UP))
        scene.idle(100)
        scene.markAsFinished()
    }

    fun spring(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("bee_spring", "The Clockwork Spring")
        scene.configureBasePlate(0, 0, 7)
        scene.showBasePlate()
        scene.idle(10)

        val hive = util.grid().at(3, 1, 5)
        val cog = util.grid().at(4, 1, 5)
        val port = util.grid().at(0, 2, 3)
        val row = (1..5).map { util.grid().at(it, 1, 0) }

        PonderBees.showPoweredHive(scene, util, hive, cog)
        PonderBees.spinHive(scene, util, hive, cog, 32f)
        scene.world().showSection(util.select().fromTo(0, 1, 3, 0, 2, 3), Direction.DOWN)
        scene.idle(15)

        val home = PonderBees.above(util, hive, 1.5)
        val bee = PonderBees.createBee(scene, BeeType.CONSTRUCTION, home)
        scene.overlay().showText(80)
            .text("Every bee runs on a clockwork spring")
            .placeNearTarget()
            .pointAt(home)
        scene.idle(60)

        var position = gather(scene, util, bee, home, port, ItemStack(Items.OAK_PLANKS))
        scene.overlay().showText(90)
            .text("Each block it places or breaks winds the spring down a little")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(row[1]))
        position = build(scene, util, bee, position, row.take(3))
        scene.idle(20)

        PonderEffects.highlight(scene, PonderPalette.RED, row[2].above(), row[2].above(), 60)
        scene.overlay().showText(90)
            .text("When the spring runs out, the bee flies home to rewind it")
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(position)
        scene.idle(20)
        PonderBees.fly(scene, bee, position, home, 35)
        scene.effects().rotationSpeedIndicator(cog)
        PonderEffects.signal(scene, util, hive)
        scene.idle(50)

        scene.overlay().showText(70)
            .text("Then it gets back to work where it stopped")
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(util.vector().centerOf(row[3]))
        position = build(scene, util, bee, home, row.drop(3))
        PonderBees.carry(scene, bee, ItemStack.EMPTY)
        PonderEffects.highlight(scene, PonderPalette.GREEN, row.first(), row.last(), 40)
        PonderBees.fly(scene, bee, position, home, 35)
        PonderBees.hide(scene, listOf(bee))
        scene.idle(10)

        PonderBees.spinHive(scene, util, hive, cog, 96f)
        scene.effects().rotationSpeedIndicator(cog)
        scene.overlay().showOutlineWithText(util.select().position(cog), 90)
            .text("Faster rotation rewinds springs quicker and makes them last longer")
            .attachKeyFrame()
            .colored(PonderPalette.FAST)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(cog, Direction.UP))
        scene.idle(100)

        scene.overlay().showControls(util.vector().blockSurface(hive, Direction.WEST), Pointing.RIGHT, 90)
            .withItem(AllItems.PORTABLE_BEEHIVE.asStack())
        scene.overlay().showText(90)
            .text("Bees from a Portable Beehive pay for every rewind with honey")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(hive, Direction.WEST))
        scene.idle(100)
        scene.markAsFinished()
    }
}
