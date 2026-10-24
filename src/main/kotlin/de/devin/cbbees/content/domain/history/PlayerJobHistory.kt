package de.devin.cbbees.content.domain.history

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

class PlayerJobHistory(private val onChanged: () -> Unit) {

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    fun pushUndo(entry: HistoryEntry, clearRedo: Boolean) {
        if (clearRedo) redoStack.clear()
        undoStack.pushCapped(entry)
    }

    fun pushRedo(entry: HistoryEntry) = redoStack.pushCapped(entry)

    fun popUndo(): HistoryEntry? = undoStack.removeLastOrNull()?.also { onChanged() }

    fun popRedo(): HistoryEntry? = redoStack.removeLastOrNull()?.also { onChanged() }

    fun entries(): Sequence<HistoryEntry> = undoStack.asSequence() + redoStack.asSequence()

    private fun ArrayDeque<HistoryEntry>.pushCapped(entry: HistoryEntry) {
        addLast(entry)
        while (size > MAX_ENTRIES) removeFirst()
        onChanged()
    }

    fun save(registries: HolderLookup.Provider): CompoundTag = CompoundTag().apply {
        put("Undo", ListTag().apply { undoStack.forEach { add(it.save(registries)) } })
        put("Redo", ListTag().apply { redoStack.forEach { add(it.save(registries)) } })
    }

    fun load(tag: CompoundTag, registries: HolderLookup.Provider) {
        undoStack.loadFrom(tag.getList("Undo", Tag.TAG_COMPOUND.toInt()), registries)
        redoStack.loadFrom(tag.getList("Redo", Tag.TAG_COMPOUND.toInt()), registries)
    }

    private fun ArrayDeque<HistoryEntry>.loadFrom(list: ListTag, registries: HolderLookup.Provider) {
        clear()
        list.mapNotNullTo(this) { HistoryEntry.load(it as CompoundTag, registries) }
    }

    companion object {
        const val MAX_ENTRIES = 20
    }
}
