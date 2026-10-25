package de.devin.cbbees.ponder

import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import de.devin.cbbees.content.bee.server.BeeType
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.element.ElementLink
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.createmod.ponder.foundation.element.ElementLinkImpl
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object PonderBees {

    private const val HIVE_RPM = 64f

    class Flight(val bee: ElementLink<PonderBeeElement>, val from: Vec3, val to: Vec3)

    fun spinHive(scene: CreateSceneBuilder, util: SceneBuildingUtil, hive: BlockPos, cog: BlockPos, rpm: Float = HIVE_RPM) {
        scene.world().setKineticSpeed(util.select().position(cog), rpm)
        scene.world().setKineticSpeed(util.select().position(hive), -rpm)
    }

    fun showPoweredHive(scene: CreateSceneBuilder, util: SceneBuildingUtil, hive: BlockPos, cog: BlockPos, from: Direction = Direction.DOWN) {
        scene.world().showSection(util.select().position(hive).add(util.select().position(cog)), from)
        spinHive(scene, util, hive, cog)
    }

    fun createBee(scene: CreateSceneBuilder, type: BeeType, position: Vec3, yRot: Float = 0f): ElementLink<PonderBeeElement> {
        val link = ElementLinkImpl(PonderBeeElement::class.java)
        scene.addInstruction { ponderScene ->
            val bee = PonderBeeElement(type, position, yRot)
            ponderScene.addElement(bee)
            ponderScene.linkElement(bee, link)
        }
        return link
    }

    fun carry(scene: CreateSceneBuilder, bee: ElementLink<PonderBeeElement>, stack: ItemStack) {
        scene.addInstruction { it.resolve(bee)?.carried = stack }
    }

    fun fly(scene: CreateSceneBuilder, bee: ElementLink<PonderBeeElement>, from: Vec3, to: Vec3, ticks: Int) =
        flyTogether(scene, listOf(Flight(bee, from, to)), ticks)

    fun flyTogether(scene: CreateSceneBuilder, flights: List<Flight>, ticks: Int) {
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

    fun hide(scene: CreateSceneBuilder, bees: List<ElementLink<PonderBeeElement>>) =
        bees.forEach { bee -> scene.addInstruction { it.resolve(bee)?.isVisible = false } }

    fun rangeBox(scene: CreateSceneBuilder, slot: Any, hive: BlockPos, radius: Int, color: PonderPalette, ticks: Int) {
        val box = AABB(
            (hive.x - radius).toDouble(), hive.y.toDouble(), (hive.z - radius).toDouble(),
            (hive.x + radius + 1).toDouble(), hive.y.toDouble(), (hive.z + radius + 1).toDouble()
        )
        scene.overlay().chaseBoundingBoxOutline(color, slot, box, ticks)
    }

    fun above(util: SceneBuildingUtil, pos: BlockPos, height: Double = 1.0): Vec3 =
        util.vector().centerOf(pos).add(0.0, height, 0.0)
}
