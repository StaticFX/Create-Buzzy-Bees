package de.devin.cbbees.registry

import com.tterrag.registrate.util.entry.EntityEntry
import com.tterrag.registrate.util.nullness.NonNullFunction
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.drone.DroneEntity
import net.minecraft.client.renderer.entity.NoopRenderer
import net.minecraft.world.entity.MobCategory

object AllEntityTypes {

    val DRONE: EntityEntry<DroneEntity> = CreateBuzzyBeez.REGISTRATE
        .entity("drone", ::DroneEntity, MobCategory.MISC)
        .properties { b -> b.sized(0.5f, 0.5f).fireImmune().noSave().noSummon().clientTrackingRange(8).updateInterval(5) }
        .renderer { NonNullFunction { NoopRenderer(it) } }
        .register()

    fun register() {
        // Just to trigger class loading
    }
}
