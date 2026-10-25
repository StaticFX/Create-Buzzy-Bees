package de.devin.cbbees.content.zapper.shape

import de.devin.cbbees.config.CBBeesConfig
import net.minecraft.core.Direction
import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.math.sqrt

object ZapperShapes {

    private val maxRadius = { CBBeesConfig.zapperMaxRadius.get() }
    private val maxSize = { CBBeesConfig.zapperMaxSize.get() }

    private fun radius(key: String, min: Int = 0, default: Int = 3) = ShapeParam(key, min, default, maxRadius)
    private fun size(key: String, default: Int = 3) = ShapeParam(key, 1, default, maxSize)

    val CUBOID = object : SolidShape("cuboid", listOf(size("width"), size("height"), size("length"))) {
        override fun Frame.bounds(): BoundingBox {
            val x = centered(p[0]); val y = centered(p[1]); val z = centered(p[2])
            return BoundingBox(x.first, y.first, z.first, x.last, y.last, z.last)
        }

        override fun Frame.inside(x: Int, y: Int, z: Int) = x in centered(p[0]) && y in centered(p[1]) && z in centered(p[2])
    }

    val ELLIPSOID = object : SolidShape("ellipsoid", listOf(radius("radius_x"), radius("radius_y"), radius("radius_z"))) {
        override fun Frame.bounds() = BoundingBox(-p[0], -p[1], -p[2], p[0], p[1], p[2])

        override fun Frame.inside(x: Int, y: Int, z: Int) =
            sq(x / (p[0] + .5)) + sq(y / (p[1] + .5)) + sq(z / (p[2] + .5)) < 1
    }

    val CYLINDER = object : SolidShape("cylinder", listOf(radius("radius_x"), size("height"), radius("radius_z"))) {
        override fun Frame.bounds() = centered(p[1]).let { BoundingBox(-p[0], it.first, -p[2], p[0], it.last, p[2]) }

        override fun Frame.inside(x: Int, y: Int, z: Int) =
            y in centered(p[1]) && sq(x / (p[0] + .5)) + sq(z / (p[2] + .5)) < 1
    }

    val DOME = object : SolidShape("dome", listOf(radius("radius_x"), size("height"), radius("radius_z"))) {
        override fun Frame.bounds() = BoundingBox(-p[0], 0, -p[2], p[0], p[1] - 1, p[2])

        override fun Frame.inside(x: Int, y: Int, z: Int) =
            sq(x / (p[0] + .5)) + sq(y / (p[1] - .5).coerceAtLeast(.5)) + sq(z / (p[2] + .5)) < 1

        override fun Frame.visible(x: Int, y: Int, z: Int) = y >= 0
    }

    val CAPSULE = object : SolidShape("capsule", listOf(radius("radius"), size("length", 8)), oriented = true) {
        override fun Frame.bounds(): BoundingBox {
            val r = p[0]
            val along = centered(p[1]).let { it.first - r..it.last + r }
            return if (axis == Direction.Axis.X) BoundingBox(along.first, -r, -r, along.last, r, r)
            else BoundingBox(-r, -r, along.first, r, r, along.last)
        }

        override fun Frame.inside(x: Int, y: Int, z: Int): Boolean {
            val (along, across) = if (axis == Direction.Axis.X) x to z else z to x
            val segment = centered(p[1])
            val past = (along - along.coerceIn(segment.first, segment.last)).toDouble()
            return sq(past) + sq(y.toDouble()) + sq(across.toDouble()) < sq(p[0] + .5)
        }
    }

    val CONE = object : SolidShape("cone", listOf(radius("radius"), size("height", 6))) {
        override fun Frame.bounds() = BoundingBox(-p[0], 0, -p[0], p[0], p[1] - 1, p[0])

        override fun Frame.inside(x: Int, y: Int, z: Int): Boolean {
            if (y !in 0 until p[1]) return false
            val r = p[0] * (p[1] - y).toDouble() / p[1]
            return sq(x.toDouble()) + sq(z.toDouble()) < sq(r + .5)
        }
    }

    val TORUS = object : SolidShape("torus", listOf(radius("ring_radius", 1, 6), radius("tube_radius", 0, 2))) {
        override fun Frame.bounds() = (p[0] + p[1]).let { BoundingBox(-it, -p[1], -it, it, p[1], it) }

        override fun Frame.inside(x: Int, y: Int, z: Int) =
            sq(sqrt(sq(x.toDouble()) + sq(z.toDouble())) - p[0]) + sq(y.toDouble()) < sq(p[1] + .5)
    }

    val SURFACE = FloodShape("surface", surface = true, radius("range", 1, 3))
    val CLUSTER = FloodShape("cluster", surface = false, radius("range", 1, 3))

    val ALL: List<ZapperShape> = listOf(CUBOID, ELLIPSOID, CYLINDER, DOME, CAPSULE, CONE, TORUS, SURFACE, CLUSTER)

    fun byId(id: String): ZapperShape = ALL.firstOrNull { it.id == id } ?: CUBOID
}
