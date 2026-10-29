package de.devin.cbbees.content.schematics.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.CreateBuzzyBeez
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.io.IOException
import java.nio.file.Files

/**
 * Client-side manager for schematic group assignments.
 *
 * Persists a `groups.json` file in the schematics directory that maps
 * schematic filenames to hierarchical group paths (e.g. "Medieval/Towers").
 * Groups exist implicitly when referenced — no separate creation step needed.
 */
@OnlyIn(Dist.CLIENT)
object SchematicGroupManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val groupsFile = CreatePaths.SCHEMATICS_DIR.resolve("groups.json")

    /** filename → group path (e.g. "Medieval/Towers"). Empty string = root. */
    private val groups = mutableMapOf<String, String>()

    /** Folders created by hand, kept even while no schematic is in them. */
    private val folders = sortedSetOf<String>()
    private var loaded = false

    fun load() {
        groups.clear()
        folders.clear()
        if (!Files.exists(groupsFile)) {
            loaded = true
            return
        }
        try {
            val json = JsonParser.parseString(Files.readString(groupsFile)).asJsonObject
            json.getAsJsonArray("folders")?.forEach { folders += it.asString }
            val groupsObj = json.getAsJsonObject("groups") ?: return
            for ((key, value) in groupsObj.entrySet()) {
                groups[key] = value.asString
            }
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.warn("Failed to load groups.json", e)
        }
        loaded = true
    }

    fun save() {
        try {
            val json = JsonObject()
            json.addProperty("version", 1)
            val groupsObj = JsonObject()
            for ((key, value) in groups.toSortedMap()) {
                groupsObj.addProperty(key, value)
            }
            json.add("groups", groupsObj)
            json.add("folders", JsonArray().apply { folders.forEach(::add) })
            Files.createDirectories(groupsFile.parent)
            Files.writeString(groupsFile, gson.toJson(json))
        } catch (e: IOException) {
            CreateBuzzyBeez.LOGGER.error("Failed to save groups.json", e)
        }
    }

    fun ensureLoaded() {
        if (!loaded) load()
    }

    /** Returns the group path for a schematic filename, or empty string for root. */
    fun getGroup(filename: String): String {
        ensureLoaded()
        return groups[filename] ?: ""
    }

    /** Assigns a schematic to a group path. Empty path moves to root (removes entry). */
    fun setGroup(filename: String, groupPath: String) {
        ensureLoaded()
        assign(filename, groupPath.trim().trimEnd('/'))
        save()
    }

    private fun assign(filename: String, groupPath: String) {
        if (groupPath.isEmpty()) groups.remove(filename) else groups[filename] = groupPath
    }

    /** Normalizes a typed folder path like " Medieval / Towers/ " to "Medieval/Towers". */
    fun normalize(path: String): String = path.split('/').map(String::trim).filter(String::isNotEmpty).joinToString("/")

    /** Creates an empty folder at [path]. */
    fun createFolder(path: String) {
        ensureLoaded()
        val folder = normalize(path)
        if (folder.isEmpty()) return
        folders += folder
        save()
    }

    /** Whether [groupPath] can be moved into [target] without ending up inside itself. */
    fun canMoveInto(groupPath: String, target: String): Boolean = target != groupPath && !target.startsWith("$groupPath/")

    /** Moves schematics and whole groups (with everything below them) into [target]. */
    fun moveInto(filenames: Collection<String>, groupPaths: Collection<String>, target: String) {
        ensureLoaded()
        val destination = target.trim().trimEnd('/')
        filenames.forEach { assign(it, destination) }
        groupPaths.filter { canMoveInto(it, destination) }.forEach { group ->
            val moved = listOf(destination, group.substringAfterLast('/')).filter(String::isNotEmpty).joinToString("/")
            val relocate = { path: String ->
                when {
                    path == group -> moved
                    path.startsWith("$group/") -> moved + path.removePrefix(group)
                    else -> path
                }
            }
            groups.replaceAll { _, path -> relocate(path) }
            val relocated = folders.map(relocate)
            folders.clear()
            folders += relocated
        }
        save()
    }

    /** Removes group assignment (moves to root). */
    fun removeGroup(filename: String) {
        ensureLoaded()
        groups.remove(filename)
        save()
    }

    /** Transfers group assignment from one filename to another (used when renaming). */
    fun renameEntry(oldFilename: String, newFilename: String) {
        ensureLoaded()
        val group = groups.remove(oldFilename) ?: return
        groups[newFilename] = group
        save()
    }

    /** Prunes entries for schematic files that no longer exist on disk. */
    fun reconcile(available: List<String>) {
        ensureLoaded()
        val availableSet = available.toSet()
        val removed = groups.keys.filter { it !in availableSet }
        if (removed.isNotEmpty()) {
            removed.forEach { groups.remove(it) }
            save()
        }
    }

    /**
     * Returns items at the given group level.
     * @return Pair of (subgroup display names, schematic filenames)
     */
    fun getItemsAtLevel(groupPath: String): Pair<List<String>, List<String>> {
        ensureLoaded()
        val prefix = if (groupPath.isEmpty()) "" else "$groupPath/"
        val subgroups = mutableSetOf<String>()
        val schematics = mutableListOf<String>()

        return Pair(subgroups.toList().sorted(), schematics)
    }

    /**
     * Returns items at the given group level from a provided list of available schematics.
     * @return Pair of (subgroup display names, schematic filenames)
     */
    fun getItemsAtLevel(groupPath: String, availableSchematics: List<String>): Pair<List<String>, List<String>> {
        ensureLoaded()
        val prefix = if (groupPath.isEmpty()) "" else "$groupPath/"
        val subgroups = mutableSetOf<String>()
        val schematics = mutableListOf<String>()

        for (folder in folders) {
            when {
                groupPath.isEmpty() -> subgroups += folder.substringBefore('/')
                folder.startsWith(prefix) -> subgroups += folder.removePrefix(prefix).substringBefore('/')
            }
        }

        for (filename in availableSchematics) {
            val fileGroup = groups[filename] ?: ""

            if (groupPath.isEmpty()) {
                // At root: schematics with no group or whose group doesn't match any prefix
                if (fileGroup.isEmpty()) {
                    schematics.add(filename)
                } else {
                    // Extract the first segment as a subgroup
                    val firstSegment = fileGroup.split("/")[0]
                    subgroups.add(firstSegment)
                }
            } else {
                if (fileGroup == groupPath) {
                    // Exact match — schematic is directly at this level
                    schematics.add(filename)
                } else if (fileGroup.startsWith(prefix)) {
                    // Deeper — extract next segment as subgroup
                    val remainder = fileGroup.removePrefix(prefix)
                    val nextSegment = remainder.split("/")[0]
                    subgroups.add(nextSegment)
                }
                // else: doesn't belong at this level
            }
        }

        return Pair(subgroups.toList().sorted(), schematics.sorted())
    }

    /** Returns all unique group paths currently in use. */
    fun getAllGroupPaths(): Set<String> {
        ensureLoaded()
        val paths = mutableSetOf<String>()
        for (groupPath in groups.values + folders) {
            // Add the path and all parent paths
            val parts = groupPath.split("/")
            for (i in parts.indices) {
                paths.add(parts.subList(0, i + 1).joinToString("/"))
            }
        }
        return paths
    }

    /** Returns all raw group assignments (for display/debugging). */
    fun getAllAssignments(): Map<String, String> {
        ensureLoaded()
        return groups.toMap()
    }
}
