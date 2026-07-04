package de.devin.cbbees.compat.sable

import net.minecraft.core.Position
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * Optional client-side bridge for rendering Buzzy Bees flight-plan bees inside Sable sub-levels.
 *
 * This file intentionally uses reflection only. Buzzy Bees can still run without Sable installed,
 * and the schematic/deployer/planner logic remains unchanged from 1.3.3.
 */
object SableRenderSupport {
    private val sableClass: Class<*>? by lazy {
        runCatching { Class.forName("dev.ryanhcode.sable.Sable") }.getOrNull()
    }

    private val helperInstance: Any? by lazy {
        runCatching { sableClass?.getField("HELPER")?.get(null) }.getOrNull()
    }

    private val helperClass: Class<*>? by lazy { helperInstance?.javaClass }

    private val projectOutOfSubLevelMethod by lazy {
        runCatching {
            helperClass?.getMethod(
                "projectOutOfSubLevel",
                Level::class.java,
                Position::class.java
            )
        }.getOrNull()
    }

    private val distanceSquaredWithSubLevelsMethod by lazy {
        runCatching {
            helperClass?.getMethod(
                "distanceSquaredWithSubLevels",
                Level::class.java,
                Position::class.java,
                Position::class.java
            )
        }.getOrNull()
    }

    fun projectOutOfSubLevel(level: Level, pos: Position): Vec3? {
        val helper = helperInstance ?: return null
        val method = projectOutOfSubLevelMethod ?: return null
        return runCatching { method.invoke(helper, level, pos) as? Vec3 }.getOrNull()
    }

    fun distanceSquaredWithSubLevels(level: Level, posA: Position, posB: Position): Double? {
        val helper = helperInstance ?: return null
        val method = distanceSquaredWithSubLevelsMethod ?: return null
        return runCatching { method.invoke(helper, level, posA, posB) as? Double }.getOrNull()
    }
}
