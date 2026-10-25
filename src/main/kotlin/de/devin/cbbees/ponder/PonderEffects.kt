package de.devin.cbbees.ponder

import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object PonderEffects {

    fun highlight(scene: CreateSceneBuilder, color: PonderPalette, from: BlockPos, to: BlockPos = from, ticks: Int = 60) {
        val box = AABB(Vec3.atLowerCornerOf(from), Vec3.atLowerCornerOf(to).add(1.0, 1.0, 1.0))
        scene.overlay().chaseBoundingBoxOutline(color, from, box.deflate(0.45), 1)
        scene.idle(1)
        scene.overlay().chaseBoundingBoxOutline(color, from, box.inflate(1 / 32.0), ticks)
    }

    fun sparkle(scene: CreateSceneBuilder, util: SceneBuildingUtil, pos: BlockPos) =
        burst(scene, util, pos, ParticleTypes.HAPPY_VILLAGER, 0.6)

    fun splash(scene: CreateSceneBuilder, util: SceneBuildingUtil, pos: BlockPos) =
        burst(scene, util, pos, ParticleTypes.SPLASH, 0.9)

    fun signal(scene: CreateSceneBuilder, util: SceneBuildingUtil, pos: BlockPos) =
        burst(scene, util, pos, ParticleTypes.WAX_ON, 0.7)

    private fun burst(scene: CreateSceneBuilder, util: SceneBuildingUtil, pos: BlockPos, particle: ParticleOptions, height: Double) {
        val emitter = scene.effects().simpleParticleEmitter(particle, Vec3(0.0, 0.05, 0.0))
        scene.effects().emitParticles(util.vector().centerOf(pos).add(0.0, height, 0.0), emitter, 4f, 3)
    }
}
