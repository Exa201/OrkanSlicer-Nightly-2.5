package ru.ytkab0bp.slicebeam.boot

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCacheCleanupTest {
    @Test
    fun preservesFilesCreatedDuringStartup() {
        val root = Files.createTempDirectory("model-cache-fresh").toFile()
        try {
            val recent = File(root, "importing.3mf").apply { writeText("active") }
            val removed = ModelCacheCleanup.removeStaleEntries(root, recent.lastModified() + 1L)
            assertEquals(0, removed)
            assertTrue(recent.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun removesOldFilesAndEmptyConversionDirectoriesRecursively() {
        val root = Files.createTempDirectory("model-cache-stale").toFile()
        try {
            val oldTime = System.currentTimeMillis() - ModelCacheCleanup.MAX_AGE_MILLIS - 60_000L
            val oldFile = File(root, "old.stl").apply {
                writeText("stale")
                setLastModified(oldTime)
            }
            val conversionDir = File(root, "orca_conv").apply { mkdir() }
            val oldNested = File(conversionDir, "mesh.3mf").apply {
                writeText("stale")
                setLastModified(oldTime)
            }
            conversionDir.setLastModified(oldTime)

            val removed = ModelCacheCleanup.removeStaleEntries(root)

            assertEquals(3, removed)
            assertFalse(oldFile.exists())
            assertFalse(oldNested.exists())
            assertFalse(conversionDir.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresMissingOrNonDirectoryCacheRoots() {
        val parent = Files.createTempDirectory("model-cache-root").toFile()
        try {
            assertEquals(0, ModelCacheCleanup.removeStaleEntries(File(parent, "missing")))
            val file = File(parent, "not-a-directory").apply { writeText("keep") }
            assertEquals(0, ModelCacheCleanup.removeStaleEntries(file))
            assertTrue(file.exists())
        } finally {
            parent.deleteRecursively()
        }
    }
}
