package de.devin.cbbees.ponder

import com.simibubi.create.AllBlocks as CreateBlocks
import com.simibubi.create.AllItems as CreateItems
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import com.simibubi.create.content.logistics.box.PackageItem
import com.simibubi.create.content.logistics.packager.PackagerBlock
import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo
import de.devin.cbbees.blocks.AllBlocks
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.logistics.ports.PortBlock
import de.devin.cbbees.content.logistics.ports.PortState
import de.devin.cbbees.content.logistics.ports.PortType
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.element.ElementLink
import net.createmod.ponder.api.element.WorldSectionElement
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction

object PortScenes {

    private fun CreateSceneBuilder.portState(pos: BlockPos, state: PortState) =
        world().modifyBlock(pos, { it.setValue(PortBlock.PORT_STATE, state) as BlockState }, false)

    private fun CreateSceneBuilder.tank(pos: BlockPos, change: (FluidTankBlockEntity) -> Unit) =
        world().modifyBlockEntity(pos, FluidTankBlockEntity::class.java) { change(it) }

    fun dockPort(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("dock_port", "The Dock Port")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val sourceTank = util.grid().at(1, 1, 3)
        val sourcePort = util.grid().at(1, 2, 3)
        val targetTank = util.grid().at(3, 1, 3)
        val targetPort = util.grid().at(3, 2, 3)
        val hive = util.grid().at(2, 1, 0)

        scene.tank(sourceTank) { it.tankInventory.fill(FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE) }
        scene.world().showSection(util.select().position(sourceTank), Direction.DOWN)
        scene.idle(5)
        scene.world().showSection(util.select().position(sourcePort), Direction.DOWN)
        scene.idle(10)

        scene.overlay().showOutlineWithText(util.select().position(sourcePort), 110)
            .text("The Dock Port can be placed on any fluid tank. A green bulb means it can interact with the tank.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(sourcePort, Direction.WEST))
        scene.idle(120)

        scene.world().showSection(util.select().position(targetTank), Direction.DOWN)
        scene.idle(5)
        scene.world().showSection(util.select().position(targetPort), Direction.DOWN)
        scene.idle(10)

        scene.overlay().showControls(util.vector().blockSurface(targetPort, Direction.UP), Pointing.DOWN, 60)
            .withItem(CreateItems.WRENCH.asStack())
            .rightClick()
        scene.overlay().showOutlineWithText(util.select().position(targetPort), 90)
            .text("Use a wrench to switch it to INSERT, so bees can fill the tank.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(targetPort, Direction.WEST))
            .attachKeyFrame()
        scene.idle(40)
        scene.world().modifyBlock(targetPort, { it.setValue(PortBlock.PORT_TYPE, PortType.INSERT) as BlockState }, false)
        scene.idle(60)

        BeeScenes.showPoweredHive(scene, util, hive, util.grid().at(3, 1, 0))
        scene.idle(15)

        val home = util.vector().centerOf(hive).add(0.0, 1.0, 0.0)
        val atSource = util.vector().centerOf(sourcePort).add(0.0, 0.5, 0.0)
        val atTarget = util.vector().centerOf(targetPort).add(0.0, 0.5, 0.0)
        val bee = BeeScenes.createBee(scene, BeeType.CONSTRUCTION, home)

        scene.overlay().showOutlineWithText(util.select().position(sourcePort), 110)
            .text("When a task needs a fluid, for example water in a schematic, a bee fetches it from an extracting Dock Port.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(sourcePort, Direction.WEST))
            .attachKeyFrame()
        scene.portState(sourcePort, PortState.BUSY)
        BeeScenes.flyBee(scene, bee, home, atSource, 40)
        scene.tank(sourceTank) { it.tankInventory.drain(1000, FluidAction.EXECUTE) }
        scene.idle(70)
        scene.portState(sourcePort, PortState.VALID)

        scene.overlay().showOutlineWithText(util.select().position(targetPort), 100)
            .text("Leftover fluids are brought to an inserting Dock Port.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(targetPort, Direction.WEST))
        scene.portState(targetPort, PortState.BUSY)
        BeeScenes.flyBee(scene, bee, atSource, atTarget, 30)
        scene.tank(targetTank) { it.tankInventory.fill(FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) }
        scene.idle(70)
        scene.portState(targetPort, PortState.VALID)

        BeeScenes.flyBee(scene, bee, atTarget, home, 40)
        scene.addInstruction { it.resolve(bee)?.isVisible = false }
        scene.idle(20)
        scene.markAsFinished()
    }

    fun wagglePort(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("waggle_port", "Requesting materials with the Waggle Port")
        scene.configureBasePlate(0, 0, 7)
        scene.showBasePlate()

        val vault = util.select().fromTo(4, 1, 3, 5, 2, 5)
        val packagerSection = util.select().fromTo(3, 1, 4, 3, 2, 4)
        val packager = util.grid().at(3, 2, 4)
        val link = util.grid().at(3, 3, 4)
        val belt = util.select().fromTo(2, 1, 5, 2, 1, 3)
        val drive = util.select().fromTo(3, 1, 6, 3, 1, 5).add(util.select().position(3, 0, 7))
        val funnel = util.select().position(2, 2, 4)
        val waggle = util.grid().at(3, 1, 1)
        val barrel = util.grid().at(2, 1, 2)
        val port = util.grid().at(2, 2, 2)
        val hive = util.grid().at(0, 1, 6)
        val site = util.select().fromTo(0, 1, 1, 0, 1, 3)

        scene.idle(10)
        val linkSection: ElementLink<WorldSectionElement> =
            scene.world().showIndependentSection(util.select().position(link), Direction.DOWN)
        scene.world().moveSection(linkSection, util.vector().of(0.0, -2.0, 0.0), 0)
        scene.idle(15)

        scene.overlay().showControls(util.vector().topOf(link.below(2)), Pointing.DOWN, 50)
            .rightClick()
            .withItem(AllBlocks.WAGGLE_PORT.asStack())
        scene.idle(5)
        val linkBox = AABB(link.below(2)).deflate(1 / 16.0).contract(0.0, 8 / 16.0, 0.0)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, link, linkBox, 50)
        scene.idle(20)
        scene.overlay().showText(90)
            .text("Right-click a Stock Link with the Waggle Port before placing it to connect it to that logistics network")
            .attachKeyFrame()
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .pointAt(util.vector().centerOf(link.below(2)))
        scene.idle(40)

        scene.world().showSection(util.select().position(waggle), Direction.DOWN)
        scene.idle(20)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, waggle, AABB(waggle), 40)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, link, linkBox, 40)
        scene.overlay().showLine(
            PonderPalette.GREEN,
            util.vector().centerOf(waggle).subtract(0.0, 0.25, 0.0),
            util.vector().centerOf(link.below(2)).subtract(0.0, 0.25, 0.0),
            40
        )
        scene.idle(60)

        scene.world().cycleBlockProperty(packager, PackagerBlock.LINKED)
        scene.world().moveSection(linkSection, util.vector().of(0.0, 2.25, 0.0), 10)
        scene.idle(8)
        scene.world().showSection(packagerSection, Direction.NORTH)
        scene.idle(3)
        scene.world().moveSection(linkSection, util.vector().of(0.0, -0.25, 0.0), 10)
        scene.idle(10)
        scene.world().cycleBlockProperty(packager, PackagerBlock.LINKED)
        scene.effects().indicateSuccess(packager)
        scene.idle(5)
        scene.world().showSection(vault, Direction.WEST)
        scene.idle(25)

        BeeScenes.showPoweredHive(scene, util, hive, util.grid().at(1, 1, 6))
        scene.idle(10)
        scene.overlay().showOutlineWithText(site, 120)
            .text("Place it inside your bee network. Whenever a bee job is missing materials, the Waggle Port orders them from the logistics network.")
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(util.vector().centerOf(waggle))
        scene.idle(130)

        scene.overlay().showControls(util.vector().topOf(waggle), Pointing.DOWN, 70).rightClick()
        scene.idle(10)
        scene.world().showSection(belt, Direction.EAST)
        scene.world().showSection(drive, Direction.NORTH)
        scene.world().showSection(funnel, Direction.DOWN)
        scene.world().showSection(util.select().position(barrel), Direction.DOWN)
        scene.idle(5)
        scene.world().showSection(util.select().position(port), Direction.DOWN)
        scene.overlay().showOutlineWithText(util.select().position(waggle), 110)
            .text("Right-click it to set the address. Point it at a place where packages are unpacked next to a Logistics Port.")
            .attachKeyFrame()
            .colored(PonderPalette.BLUE)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(waggle, Direction.WEST))
        scene.idle(120)

        val box = PackageItem.containing(listOf(ItemStack(CreateBlocks.ANDESITE_CASING.get(), 3)))
        PonderHilo.requesterEffect(scene, waggle)
        scene.idle(5)
        PonderHilo.packagerCreate(scene, packager, box)
        PonderHilo.linkEffect(scene, link)
        scene.idle(20)
        PonderHilo.packagerClear(scene, packager)
        scene.world().createItemOnBelt(util.grid().at(2, 1, 4), Direction.EAST, box)
        scene.overlay().showText(90)
            .text("Missing items are packaged and sent to that address")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(packager, Direction.WEST))
        scene.idle(50)
        scene.world().removeItemsFromBelt(util.grid().at(2, 1, 3))
        scene.effects().indicateSuccess(barrel)
        scene.idle(30)

        val home = util.vector().centerOf(hive).add(0.0, 1.0, 0.0)
        val atPort = util.vector().centerOf(port).add(0.0, 0.5, 0.0)
        val atSite = util.vector().centerOf(util.grid().at(0, 1, 2)).add(0.0, 1.0, 0.0)
        val bee = BeeScenes.createBee(scene, BeeType.CONSTRUCTION, home)
        scene.overlay().showOutlineWithText(util.select().position(port), 90)
            .text("Bees pick the items up from the Logistics Port and continue building")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(port, Direction.WEST))
        scene.portState(port, PortState.BUSY)
        BeeScenes.flyBee(scene, bee, home, atPort, 40)
        scene.idle(20)
        scene.portState(port, PortState.VALID)
        BeeScenes.flyBee(scene, bee, atPort, atSite, 30)
        scene.world().showSection(site, Direction.DOWN)
        scene.idle(30)

        scene.overlay().showText(110)
            .text("Items your Factory Gauges keep in stock are crafted automatically and requested again until the job is done")
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(packager, Direction.UP))
        BeeScenes.flyBee(scene, bee, atSite, home, 40)
        scene.addInstruction { it.resolve(bee)?.isVisible = false }
        scene.idle(80)
        scene.markAsFinished()
    }
}
