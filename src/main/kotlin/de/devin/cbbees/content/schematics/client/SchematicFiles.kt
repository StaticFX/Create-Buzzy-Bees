package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.utility.CreatePaths
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.nio.file.Files

@OnlyIn(Dist.CLIENT)
object SchematicFiles {

    private val UNSAFE = Regex("[^\\w .-]")

    fun saveUnique(name: String, bytes: ByteArray): String {
        val slug = name.replace(UNSAFE, "_").trim('.', ' ').ifEmpty { "schematic" }
        val filename = generateSequence(0) { it + 1 }
            .map { if (it == 0) "$slug.nbt" else "${slug}_$it.nbt" }
            .first { !Files.exists(CreatePaths.SCHEMATICS_DIR.resolve(it)) }
        Files.createDirectories(CreatePaths.SCHEMATICS_DIR)
        Files.write(CreatePaths.SCHEMATICS_DIR.resolve(filename), bytes)
        return filename
    }
}
