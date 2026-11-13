package ru.ytkab0bp.slicebeam.utils

class FillBedPlanner private constructor() {
    companion object {
        /** Returns false only when the object's XY footprint cannot fit in either 90° orientation. */
        @JvmStatic
        fun canFitFootprint(bedWidth: Double, bedDepth: Double, objectWidth: Double, objectDepth: Double): Boolean {
            if (!bedWidth.isFinite() || !bedDepth.isFinite() || !objectWidth.isFinite() || !objectDepth.isFinite() ||
                bedWidth <= 0.0 || bedDepth <= 0.0 || objectWidth <= 0.0 || objectDepth <= 0.0) return false
            return (objectWidth <= bedWidth && objectDepth <= bedDepth) ||
                (objectWidth <= bedDepth && objectDepth <= bedWidth)
        }

        @JvmStatic
        fun copyAttemptsForLimit(currentObjectCount: Int, maxObjectCount: Int): Int {
            if (currentObjectCount <= 0 || maxObjectCount <= currentObjectCount) {
                return 0
            }
            return maxObjectCount - currentObjectCount
        }

        /** Finds the largest packable copy count, probing the likely full-bed fit first. */
        @JvmStatic
        fun maximumPackableCopies(maxCopies: Int, fits: (Int) -> Boolean): Int {
            if (maxCopies <= 0) return 0
            if (fits(maxCopies)) return maxCopies
            var low = 1
            var high = maxCopies - 1
            var best = 0
            while (low <= high) {
                val candidate = low + (high - low + 1) / 2
                if (fits(candidate)) {
                    best = candidate
                    low = candidate + 1
                } else {
                    high = candidate - 1
                }
            }
            return best
        }

        /** Returns a usable grid capacity, or zero when the selected object cannot fit. */
        @JvmStatic
        fun estimatedCapacity(bedWidth: Double, bedDepth: Double, objectWidth: Double, objectDepth: Double, spacing: Double): Int {
            if (!bedWidth.isFinite() || !bedDepth.isFinite() || !objectWidth.isFinite() || !objectDepth.isFinite()) return 0
            if (bedWidth <= 0.0 || bedDepth <= 0.0 || objectWidth <= 0.0 || objectDepth <= 0.0 ||
                !spacing.isFinite() || spacing < 0.0) return 0
            if (objectWidth > bedWidth || objectDepth > bedDepth) return 0
            val columns = ((bedWidth + spacing) / (objectWidth + spacing)).toInt()
            val rows = ((bedDepth + spacing) / (objectDepth + spacing)).toInt()
            if (columns <= 0 || rows <= 0) return 0
            return (columns.toLong() * rows.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
