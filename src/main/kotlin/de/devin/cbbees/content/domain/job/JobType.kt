package de.devin.cbbees.content.domain.job


/**
 * Sealed interface representing the type of a bee job.
 * Each implementation owns its type-specific behavior: display name, etc.
 * Adding a new planner type requires adding a new [JobType] implementation here.
 */
sealed interface JobType {
    val id: String
    val translationKey: String
    val changesBlocks: Boolean get() = true

    companion object {
        val entries: List<JobType> = listOf(Construction, Deconstruction, Pickup, Shaping, Undo, Redo)

        fun fromOrdinal(ordinal: Int): JobType = entries[ordinal]

        fun ordinalOf(type: JobType): Int = entries.indexOf(type)
    }

    data object Construction : JobType {
        override val id = "construction"
        override val translationKey = "cbbees.job_type.construction"
    }

    data object Deconstruction : JobType {
        override val id = "deconstruction"
        override val translationKey = "cbbees.job_type.deconstruction"
    }

    data object Pickup : JobType {
        override val id = "pickup"
        override val translationKey = "cbbees.job_type.pickup"
        override val changesBlocks = false
    }

    data object Shaping : JobType {
        override val id = "shaping"
        override val translationKey = "cbbees.job_type.shaping"
    }

    data object Undo : JobType {
        override val id = "undo"
        override val translationKey = "cbbees.job_type.undo"
    }

    data object Redo : JobType {
        override val id = "redo"
        override val translationKey = "cbbees.job_type.redo"
    }
}
