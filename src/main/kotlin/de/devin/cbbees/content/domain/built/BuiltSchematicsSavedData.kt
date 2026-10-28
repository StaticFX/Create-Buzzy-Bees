package de.devin.cbbees.content.domain.built

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

class BuiltSchematicsSavedData : SavedData() {

    private val builds = linkedMapOf<UUID, BuiltSchematic>()

    val all: Collection<BuiltSchematic> get() = builds.values

    operator fun get(id: UUID): BuiltSchematic? = builds[id]

    fun add(build: BuiltSchematic) {
        builds[build.id] = build
        setDirty()
    }

    fun remove(id: UUID): BuiltSchematic? = builds.remove(id)?.also { setDirty() }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.put("Builds", ListTag().apply { builds.values.forEach { add(it.save()) } })
        return tag
    }

    companion object {
        private const val DATA_NAME = "cbbees_built_schematics"

        private val FACTORY = Factory(::BuiltSchematicsSavedData, { tag, registries ->
            BuiltSchematicsSavedData().apply {
                tag.getList("Builds", Tag.TAG_COMPOUND.toInt()).forEach { element ->
                    BuiltSchematic.load(element as CompoundTag, registries)?.let { builds[it.id] = it }
                }
            }
        })

        fun get(server: MinecraftServer): BuiltSchematicsSavedData =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, DATA_NAME)
    }
}
