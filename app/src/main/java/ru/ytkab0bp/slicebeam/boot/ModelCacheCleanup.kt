package ru.ytkab0bp.slicebeam.boot

import java.io.File

/** Removes abandoned temporary model imports without racing files created during app startup. */
internal object ModelCacheCleanup {
    const val MAX_AGE_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun removeStaleEntries(root: File, nowMillis: Long = System.currentTimeMillis()): Int {
        if (!root.isDirectory) return 0
        return removeStaleChildren(root, nowMillis)
    }

    private fun removeStaleChildren(directory: File, nowMillis: Long): Int {
        var removed = 0
        val children = directory.listFiles() ?: return 0
        for (child in children) {
            if (child.isDirectory) {
                val staleDirectory = isStale(child, nowMillis)
                removed += removeStaleChildren(child, nowMillis)
                if (child.list()?.isEmpty() == true && staleDirectory && child.delete()) {
                    removed++
                }
            } else if (child.isFile && isStale(child, nowMillis) && child.delete()) {
                removed++
            }
        }
        return removed
    }

    private fun isStale(file: File, nowMillis: Long): Boolean {
        val modified = file.lastModified()
        return modified > 0L && nowMillis >= modified && nowMillis - modified >= MAX_AGE_MILLIS
    }
}
