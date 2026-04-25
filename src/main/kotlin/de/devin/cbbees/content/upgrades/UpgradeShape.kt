package de.devin.cbbees.content.upgrades

/**
 * Defines the spatial shape of an upgrade on the grid.
 * Each shape is a list of (col, row) offsets relative to the placement origin.
 */
data class UpgradeShape(val cells: List<Pair<Int, Int>>) {

    /**
     * Returns this shape rotated clockwise [times] times (each 90 degrees).
     * In screen coordinates (Y down), CW rotation is: (x, y) -> (-y, x).
     * Result is normalized so all coords are non-negative.
     */
    fun rotated(times: Int): UpgradeShape {
        val t = ((times % 4) + 4) % 4
        if (t == 0) return this

        var current = cells
        repeat(t) {
            current = current.map { (x, y) -> Pair(-y, x) }
            val minX = current.minOf { it.first }
            val minY = current.minOf { it.second }
            current = current.map { (x, y) -> Pair(x - minX, y - minY) }
        }
        return UpgradeShape(current)
    }

    fun width(): Int = cells.maxOf { it.first } + 1
    fun height(): Int = cells.maxOf { it.second } + 1

    companion object {
        /** L-shape: 3 cells
         * ```
         * ██
         * █
         * ```
         */
        val L_SHAPE = UpgradeShape(listOf(0 to 0, 1 to 0, 0 to 1))

        /** T-shape: 4 cells
         * ```
         *  █
         * ███
         * ```
         */
        val T_SHAPE = UpgradeShape(listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1))

        /** 2x1 horizontal bar: 2 cells
         * ```
         * ██
         * ```
         */
        val BAR_2 = UpgradeShape(listOf(0 to 0, 1 to 0))

        /** Single cell: 1 cell */
        val SINGLE = UpgradeShape(listOf(0 to 0))

        /** 2x2 square: 4 cells
         * ```
         * ██
         * ██
         * ```
         */
        val SQUARE_2X2 = UpgradeShape(listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1))

        /** S-shape: 4 cells
         * ```
         *  ██
         * ██
         * ```
         */
        val S_SHAPE = UpgradeShape(listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1))

        /** 3x3 square: 9 cells
         * ```
         * ███
         * ███
         * ███
         * ```
         */
        val SQUARE_3X3 = UpgradeShape(
            (0..2).flatMap { x -> (0..2).map { y -> x to y } }
        )
    }
}
