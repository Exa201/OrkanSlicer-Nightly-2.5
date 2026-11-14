package ru.ytkab0bp.slicebeam

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.slic3r.GLModel
import ru.ytkab0bp.slicebeam.slic3r.PaintSession
import ru.ytkab0bp.slicebeam.slic3r.LargeModelPick
import ru.ytkab0bp.slicebeam.slic3r.BooleanOperation
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.ModelMirrorAxis
import ru.ytkab0bp.slicebeam.render.ModelSelectionSnapshot
import ru.ytkab0bp.slicebeam.render.GLRenderer
import ru.ytkab0bp.slicebeam.render.FillBedModelOperation
import ru.ytkab0bp.slicebeam.slic3r.Native
import ru.ytkab0bp.slicebeam.slic3r.ObjectBounds3D
import ru.ytkab0bp.slicebeam.slic3r.ObjectPlacement
import ru.ytkab0bp.slicebeam.components.FastenerFormLogic
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelBooleanDialog
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelObjectPartTransformDialog
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelObjectsDialog
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelObjectRow
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelObjectPartRow
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelModifierVolumesDialog
import ru.ytkab0bp.slicebeam.utils.FastenerStlWriter
import ru.ytkab0bp.slicebeam.utils.FastenerStandards
import ru.ytkab0bp.slicebeam.utils.Vec3d
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ModelEditingNativeDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modifierVolumeDialogCreatesTypedBoxWithMillimeterInputs() {
        val created = AtomicReference<Triple<Int, List<Double>, List<Double>>?>(null)
        compose.setContent {
            BeamComposeTheme {
                ModelModifierVolumesDialog(
                    existing = emptyList(), onDismiss = {},
                    onAdd = { type, position, size -> created.set(Triple(type, position.toList(), size.toList())) },
                    onUpdate = { _, _, _ -> },
                    onDelete = {},
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.ModelModifierNegative)).performClick()
        compose.onNodeWithText(context.getString(R.string.ModelModifierAddBox)).performClick()
        assertEquals(Triple(1, listOf(0.0, 0.0, 0.0), listOf(10.0, 10.0, 10.0)), created.get())
    }

    @Test fun nativeModifierVolumesCanBeCreatedRemovedAndRoundTripThroughThreeMf() {
        val modelFile = copyAsset("models/xyz_cube.stl", "modifier-volume-source.stl")
        val projectFile = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "modifier-volume-roundtrip.3mf")
        val model = Model(modelFile)
        var reloaded: Model? = null
        var undoSnapshot: Model? = null
        var redoSnapshot: Model? = null
        try {
            val negative = model.addModifierBox(0, 1, 2.0, 3.0, 1.0, 4.0, 5.0, 6.0)
            val blocker = model.addModifierBox(0, 3, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0)
            assertTrue("native box volumes should be appended", negative >= 0 && blocker > negative)
            assertEquals(listOf(negative, blocker), model.getModifierVolumeIndices(0).toList())
            assertEquals(1, model.getVolumeType(0, negative))
            assertEquals(3, model.getVolumeType(0, blocker))
            val initialBox = requireNotNull(model.getModifierVolumeBoxState(0, negative))
            assertEquals(listOf(2.0, 3.0, 1.0), initialBox.position.toList())
            assertEquals(listOf(4.0, 5.0, 6.0), initialBox.size.toList())
            assertTrue("modifier box should be independently editable", model.updateModifierBox(0, negative, -3.0, 4.0, 5.0, 8.0, 9.0, 10.0))
            val editedBox = requireNotNull(model.getModifierVolumeBoxState(0, negative))
            assertEquals(listOf(-3.0, 4.0, 5.0), editedBox.position.toList())
            assertEquals(listOf(8.0, 9.0, 10.0), editedBox.size.toList())
            undoSnapshot = Model.merge(model)
            assertTrue("removing a volume should be a reversible model edit", model.deleteModifierVolume(0, blocker))
            assertEquals(1, model.getModifierVolumeIndices(0).size)
            redoSnapshot = Model.merge(model)
            model.replaceContentsFrom(requireNotNull(undoSnapshot))
            assertEquals("the model snapshot must restore deleted modifier volumes", 2, model.getModifierVolumeIndices(0).size)
            model.replaceContentsFrom(requireNotNull(redoSnapshot))
            assertEquals("redo snapshot must restore the deletion", 1, model.getModifierVolumeIndices(0).size)
            model.replaceContentsFrom(requireNotNull(redoSnapshot))
            assertEquals("undo snapshot can restore the modifier again", 2, model.getModifierVolumeIndices(0).size)
            undoSnapshot?.release(); undoSnapshot = null
            redoSnapshot?.release(); redoSnapshot = null
            val originalObjectPosition = Vec3d()
            model.getTranslation(0, originalObjectPosition)
            val expectedWorldPosition = listOf(
                originalObjectPosition.x + editedBox.position[0],
                originalObjectPosition.y + editedBox.position[1],
                originalObjectPosition.z + editedBox.position[2],
            )
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, projectFile.absolutePath)
            reloaded = Model(projectFile)
            val roundTrip = reloaded.getModifierVolumeIndices(0)
            assertEquals("the slicer project must retain both modifier volumes", 2, roundTrip.size)
            assertEquals(1, reloaded.getVolumeType(0, roundTrip[0]))
            assertEquals(3, reloaded.getVolumeType(0, roundTrip[1]))
            val savedBox = requireNotNull(reloaded.getModifierVolumeBoxState(0, roundTrip[0]))
            savedBox.position.zip(listOf(-3.0, 4.0, 5.0)).forEach { (actual, expected) -> assertEquals(expected, actual, 1e-4) }
            savedBox.size.zip(listOf(8.0, 9.0, 10.0)).forEach { (actual, expected) -> assertEquals(expected, actual, 1e-4) }
            val reloadedObjectPosition = Vec3d()
            reloaded.getTranslation(0, reloadedObjectPosition)
            val savedWorldPosition = listOf(
                reloadedObjectPosition.x + savedBox.position[0],
                reloadedObjectPosition.y + savedBox.position[1],
                reloadedObjectPosition.z + savedBox.position[2],
            )
            savedWorldPosition.zip(expectedWorldPosition).forEach { (actual, expected) -> assertEquals(expected, actual, 1e-4) }
            assertEquals(listOf(8.0, 9.0, 10.0), savedBox.size.toList())
            assertTrue("volume deletion must reject a printable model part", !reloaded.deleteModifierVolume(0, 0))
            assertTrue("modifier deletion should succeed", reloaded.deleteModifierVolume(0, roundTrip[0]))
            assertEquals(1, reloaded.getModifierVolumeIndices(0).size)
            assertEquals("invalid dimensions must be rejected", -1,
                reloaded.addModifierBox(0, 2, 0.0, 0.0, 0.0, 0.0, 2.0, 2.0))
        } finally {
            reloaded?.release()
            undoSnapshot?.release()
            redoSnapshot?.release()
            model.release()
            modelFile.delete()
            projectFile.delete()
        }
    }

    @Test fun multipartObjectListExposesASeparatePartFilamentAction() {
        val assigned = AtomicReference<Pair<Int, Int>?>(null)
        compose.setContent {
            BeamComposeTheme {
                ModelObjectsDialog(
                    title = "Objects", cancelLabel = "Cancel", assignLabel = "Assign",
                    rows = listOf(ModelObjectRow(
                        name = "Assembly", tool = 0, material = "PLA", color = 0xFF000000.toInt(),
                        selected = false, assignEnabled = true,
                        parts = listOf(
                            ModelObjectPartRow("Part 1", 0, "PLA", 0xFF000000.toInt()),
                            ModelObjectPartRow("Part 2", 1, "PETG", 0xFFFFFFFF.toInt()),
                        ),
                    )),
                    onDismiss = {}, onSelect = {}, onAssign = {},
                    onAssignPart = { objectIndex, partIndex -> assigned.set(objectIndex to partIndex) },
                    onTransformPart = { _, _ -> }, onEditPart = { _, _ -> }, onApplySelection = {},
                )
            }
        }
        compose.onNodeWithTag("model-object-assign-part-0-0").assertIsDisplayed().performClick()
        assertEquals(0 to 0, assigned.get())
    }

    @Test fun objectListCanSortByHeightAndKeepsOriginalModelIndicesForSelection() {
        val selected = AtomicReference<Int?>(null)
        compose.setContent {
            BeamComposeTheme {
                ModelObjectsDialog(
                    title = "Objects", cancelLabel = "Cancel", assignLabel = "Assign",
                    rows = listOf(
                        ModelObjectRow("Tall", 0, "PLA", 0xFF000000.toInt(), false, true, objectIndex = 7, heightMm = 20.0, sizeMm3 = 8000.0),
                        ModelObjectRow("Short", 0, "PLA", 0xFFFFFFFF.toInt(), false, true, objectIndex = 3, heightMm = 5.0, sizeMm3 = 1000.0),
                        ModelObjectRow("Middle", 1, "PETG", 0xFF00FF00.toInt(), false, true, objectIndex = 11, heightMm = 10.0, sizeMm3 = 3000.0),
                    ),
                    onDismiss = {}, onSelect = { selected.set(it) }, onAssign = {}, onAssignPart = { _, _ -> },
                    onTransformPart = { _, _ -> }, onEditPart = { _, _ -> }, onApplySelection = {},
                )
            }
        }
        compose.onNodeWithTag("model-objects-order-menu").performClick()
        compose.onNodeWithTag("model-objects-order-height").performClick()
        compose.onNodeWithTag("model-object-row-0").assertTextContains("Short").performClick()
        assertEquals("sorting changes display order, not the underlying object index", 3, selected.get())
    }

    @Test fun groupedPartTransformDialogAppliesMillimeterDeltasAndPercentScale() {
        val applied = AtomicReference<List<Double>?>(null)
        compose.setContent {
            BeamComposeTheme {
                ModelObjectPartTransformDialog(
                    cancelLabel = "Cancel",
                    onDismiss = {},
                    onApply = { dx, dy, dz, rx, ry, rz, sx, sy, sz ->
                        applied.set(listOf(dx, dy, dz, rx, ry, rz, sx, sy, sz))
                    },
                )
            }
        }
        compose.onNodeWithTag("model-object-part-transform-field-0").performTextClearance()
        compose.onNodeWithTag("model-object-part-transform-field-0").performTextInput("2.5")
        compose.onNodeWithTag("model-object-part-transform-field-1").performTextClearance()
        compose.onNodeWithTag("model-object-part-transform-field-1").performTextInput("-3")
        compose.onNodeWithTag("model-object-part-transform-field-2").performTextClearance()
        compose.onNodeWithTag("model-object-part-transform-field-2").performTextInput("4")
        compose.onNodeWithTag("model-object-part-transform-field-5").performTextClearance()
        compose.onNodeWithTag("model-object-part-transform-field-5").performTextInput("90")
        compose.onNodeWithTag("model-object-part-transform-field-6").performTextClearance()
        compose.onNodeWithTag("model-object-part-transform-field-6").performTextInput("150")
        compose.onNodeWithTag("model-object-part-transform-apply").performClick()

        assertEquals(listOf(2.5, -3.0, 4.0, 0.0, 0.0, 90.0, 1.5, 1.0, 1.0), applied.get())
    }

    @Test fun groupedPartTransformDialogRejectsZeroOrOutOfRangeScale() {
        val applied = AtomicBoolean(false)
        compose.setContent {
            BeamComposeTheme {
                ModelObjectPartTransformDialog(
                    cancelLabel = "Cancel",
                    onDismiss = {},
                    onApply = { _, _, _, _, _, _, _, _, _ -> applied.set(true) },
                )
            }
        }
        compose.onNodeWithTag("model-object-part-transform-field-6").performTextClearance()
        compose.onNodeWithTag("model-object-part-transform-field-6").performTextInput("0")
        compose.onNodeWithTag("model-object-part-transform-error").assertIsDisplayed()
        compose.onNodeWithTag("model-object-part-transform-apply").performClick()
        assertFalse("invalid scale must not invoke the model mutation", applied.get())
    }

    @Test fun objectPlacementJoiningStandaloneTextAndBooleanActionsHaveGermanLabels() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val germanConfiguration = android.content.res.Configuration(base.resources.configuration).apply {
            setLocale(java.util.Locale.GERMANY)
        }
        val german = base.createConfigurationContext(germanConfiguration)

        assertEquals("Auf ein anderes Objekt setzen", german.getString(R.string.ModelMenuPlaceOnObject))
        assertEquals("Ausgewählte als Teile verbinden", german.getString(R.string.ModelMenuCombineSelected))
        assertEquals("Eigenständigen 3D-Text platzieren", german.getString(R.string.TextModelCreateSeparate))
        assertEquals("Boolesche Funktion", german.getString(R.string.ModelMenuBoolean))
        assertEquals("Vereinigung · ausgewählte Körper verbinden", german.getString(R.string.ModelBooleanUnion))
        assertEquals("Filament für den eigenständigen Text", german.getString(R.string.TextModelFilamentLabel))
    }

    @Test fun releasedModelRejectsTextJoinAndBooleanRequestsBeforeNativeCalls() {
        val model = Model()
        model.release()

        assertTrue("released model must reject multipart join", model.combineObjectsAsParts(intArrayOf(0, 1)) < 0)
        assertTrue("released model must reject Boolean operation", model.booleanObjects(0, 1, BooleanOperation.UNION) < 0)
        assertTrue("released model must reject standalone text creation", model.createTextObject(
            "font.ttf", "Label", 10f, 1f, Vec3d(0.0, 0.0, 0.0), Vec3d(0.0, 0.0, 1.0),
        ) < 0)
    }

    @Test fun releasedGlModelIgnoresLateRaycastWithoutCallingNativeCode() {
        val glModel = GLModel()
        glModel.release()
        val hits = arrayListOf(GLModel.HitResult(Vec3d(1.0, 2.0, 3.0), Vec3d(0.0, 0.0, 1.0)))

        glModel.raycaster.raycast(null, hits, 0f, 0f)

        assertTrue("late raycasts must clear stale hits and ignore the released native handle", hits.isEmpty())
    }

    @Test fun modelSnapshotsPreserveUniformFilamentAssignments() {
        val modelFile = copyAsset("models/xyz_cube.stl", "snapshot-filament-cube.stl")
        val model = Model(modelFile)
        var snapshot: Model? = null
        try {
            model.setUniformFilament(0, 2)
            assertEquals(listOf(2), model.getUsedFilaments(0).toList())

            snapshot = Model.merge(model)

            assertEquals("native model snapshot must retain the object's assigned filament",
                listOf(2), snapshot.getUsedFilaments(0).toList())
        } finally {
            snapshot?.release()
            model.release()
            modelFile.delete()
        }
    }

    @Test fun fillBedCommitsOnlyTheLargestSuccessfulArrangementAndKeepsMaterials() {
        val modelFile = copyAsset("models/xyz_cube.stl", "fill-bed-atomic-cube.stl")
        val model = Model(modelFile)
        try {
            model.translate(0, 13.0, 17.0, 5.0)
            model.setUniformFilament(0, 2)
            val sourceTriangles = model.getTriangleCount(0)
            val sourceVolume = model.getObjectVolume(0)
            val attempts = mutableListOf<Int>()

            val copies = FillBedModelOperation.run(
                activeModel = model,
                objectIndex = 0,
                viewerEnabled = false,
                estimatedCapacity = 5,
            ) { candidate ->
                attempts += candidate.objectsCount
                for (index in 0 until candidate.objectsCount) {
                    val current = Vec3d()
                    candidate.getTranslation(index, current)
                    candidate.translate(index, index * 28.0 - current.x, -current.y, -current.z)
                }
                candidate.objectsCount <= 3
            }

            assertEquals("fill-bed should choose the largest arrangement the bed accepted", 2, copies)
            assertTrue("the packer should retry smaller candidate counts", attempts.size >= 3)
            assertEquals("only the accepted source and two copies should be committed", 3, model.objectsCount)
            for (index in 0 until model.objectsCount) {
                val translation = Vec3d()
                model.getTranslation(index, translation)
                assertEquals("object $index should receive its accepted arrangement", index * 28.0,
                    translation.x, 0.001)
                assertEquals("object $index should retain its material assignment", listOf(2),
                    model.getUsedFilaments(index).toList())
                assertEquals("object $index should retain printable mesh triangles", sourceTriangles,
                    model.getTriangleCount(index))
                assertEquals("object $index should retain its solid volume", sourceVolume,
                    model.getObjectVolume(index), 0.01)
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun fillBedExceptionRollsBackTrialCopiesAndSourcePlacement() {
        val modelFile = copyAsset("models/xyz_cube.stl", "fill-bed-exception-cube.stl")
        val model = Model(modelFile)
        try {
            model.translate(0, 13.0, 17.0, 5.0)
            model.setUniformFilament(0, 3)
            val beforeTranslation = Vec3d().also { model.getTranslation(0, it) }
            val beforeBoundsMin = Vec3d(); val beforeBoundsMax = Vec3d()
            model.getBoundingBoxExact(0, beforeBoundsMin, beforeBoundsMax)
            val beforeTriangles = model.getTriangleCount(0)
            val beforeVolume = model.getObjectVolume(0)

            val copies = FillBedModelOperation.run(
                activeModel = model,
                objectIndex = 0,
                viewerEnabled = false,
                estimatedCapacity = 5,
            ) { candidate ->
                candidate.translate(0, 90.0, -40.0, 12.0)
                throw IllegalStateException("simulated native bed arrangement failure")
            }

            assertEquals("a failed native pack must report no committed copies", 0, copies)
            assertEquals("a failed trial must remove every speculative object", 1, model.objectsCount)
            val afterTranslation = Vec3d().also { model.getTranslation(0, it) }
            assertEquals(beforeTranslation.x, afterTranslation.x, 0.001)
            assertEquals(beforeTranslation.y, afterTranslation.y, 0.001)
            assertEquals(beforeTranslation.z, afterTranslation.z, 0.001)
            val afterBoundsMin = Vec3d(); val afterBoundsMax = Vec3d()
            model.getBoundingBoxExact(0, afterBoundsMin, afterBoundsMax)
            assertEquals(beforeBoundsMin.x, afterBoundsMin.x, 0.001)
            assertEquals(beforeBoundsMin.y, afterBoundsMin.y, 0.001)
            assertEquals(beforeBoundsMin.z, afterBoundsMin.z, 0.001)
            assertEquals(beforeBoundsMax.x, afterBoundsMax.x, 0.001)
            assertEquals(beforeBoundsMax.y, afterBoundsMax.y, 0.001)
            assertEquals(beforeBoundsMax.z, afterBoundsMax.z, 0.001)
            assertEquals(listOf(3), model.getUsedFilaments(0).toList())
            assertEquals(beforeTriangles, model.getTriangleCount(0))
            assertEquals(beforeVolume, model.getObjectVolume(0), 0.01)
        } finally {
            model.release()
            modelFile.delete()
        }
    }


    @Test fun delayedModelEditSelectionSnapshotCannotBeMutatedThroughInputOrGetter() {
        val modelFile = copyAsset("models/xyz_cube.stl", "selection-snapshot-cube.stl")
        val model = Model(modelFile)
        try {
            val callerSelection = intArrayOf(0)
            val snapshot = ModelSelectionSnapshot(
                model = model,
                geometryRevision = model.geometryRevision,
                objectCount = model.objectsCount,
                primaryObject = 0,
                selectedIndices = callerSelection,
            )

            callerSelection[0] = 9
            snapshot.selectedIndices[0] = 9

            assertTrue(snapshot.matches(model, 0, intArrayOf(0)))
            assertFalse(snapshot.matches(model, 0, intArrayOf(9)))
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeJoinBooleanAndTextEntryPointsRejectInvalidRequestsWithoutMutation() {
        val modelFile = copyAsset("models/xyz_cube.stl", "invalid-native-edit-cube.stl")
        val model = Model(modelFile)
        try {
            val triangleCount = model.getTriangleCount(0)
            val volume = model.getObjectVolume(0)

            assertTrue(Native.model_combine_objects_as_parts(model.pointer, intArrayOf(0, 0)) < 0)
            assertTrue(Native.model_boolean_objects(model.pointer, intArrayOf(0, 0), BooleanOperation.UNION.nativeValue) < 0)
            assertTrue(Native.model_create_text_object(
                model.pointer,
                "missing-font.ttf",
                "Text",
                Float.NaN,
                1f,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                -1,
            ) < 0)
            assertTrue(Native.model_create_text_object(
                model.pointer,
                "missing-font.ttf",
                "Text",
                10f,
                1f,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                -1,
            ) < 0)
            assertTrue(Native.model_create_text_object(
                model.pointer,
                "missing-font.ttf",
                "Text",
                10f,
                1f,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                1,
            ) < 0)

            assertEquals(1, model.objectsCount)
            assertEquals(triangleCount, model.getTriangleCount(0))
            assertEquals(volume, model.getObjectVolume(0), 0.001)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun zeroScaleIsRejectedBeforeJoiningAndBothObjectsRemainPrintableParts() {
        val modelFile = copyAsset("models/xyz_cube.stl", "singular-join-transform-cube.stl")
        try {
            for (scaleIndex in 0..1) {
                val model = Model(modelFile)
                try {
                    model.addObject(model, 0)
                    val scaleBefore = Vec3d()
                    model.getScale(scaleIndex, scaleBefore)
                    model.scale(scaleIndex, 0.0, 1.0, 1.0)
                    val scaleAfter = Vec3d()
                    model.getScale(scaleIndex, scaleAfter)
                    val targetTriangles = model.getTriangleCount(0)
                    val sourceTriangles = model.getTriangleCount(1)

                    assertEquals("zero scale must be rejected before it creates a singular join transform",
                        scaleBefore.x, scaleAfter.x, 1e-9)
                    assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
                    assertEquals("both objects should be joined as printable parts", 1, model.objectsCount)
                    assertEquals(targetTriangles + sourceTriangles, model.getTriangleCount(0))
                    assertTrue("joined model keeps a non-zero printable volume", model.getObjectVolume(0) > 0.0)
                } finally {
                    model.release()
                }
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test fun embossRejectsMalformedNativeRequestsWithoutChangingTheModel() {
        val modelFile = copyAsset("models/xyz_cube.stl", "invalid-emboss-cube.stl")
        val fontFile = copyAsset("font/roboto_medium.ttf", "invalid-emboss-font.ttf")
        val model = Model(modelFile)
        try {
            val triangleCount = model.getTriangleCount(0)
            val volume = model.getObjectVolume(0)
            val position = Vec3d(0.0, 0.0, 0.0)
            val normal = Vec3d(0.0, 0.0, 1.0)

            model.embossText(0, fontFile.absolutePath, "Text", Float.NaN, 1f, 0, position, normal)
            model.embossText(0, fontFile.absolutePath, "Text", 10f, Float.POSITIVE_INFINITY, 1, position, normal)
            model.embossText(0, fontFile.absolutePath, "Text", 10f, 1f, 0, position, Vec3d())
            model.embossText(1, fontFile.absolutePath, "Text", 10f, 1f, 0, position, normal)
            model.embossText(0, fontFile.absolutePath, "Text", 10f, 1f, 2, position, normal)

            assertEquals("invalid emboss requests must keep the original mesh", triangleCount, model.getTriangleCount(0))
            assertEquals("invalid emboss requests must keep the original solid volume", volume, model.getObjectVolume(0), 0.001)
            assertEquals("invalid object indices must not add geometry", 1, model.objectsCount)
        } finally {
            model.release()
            modelFile.delete()
            fontFile.delete()
        }
    }

    @Test fun mirrorXyZKeepsObjectCenterMeshAndCanBeToggledBack() {
        val modelFile = copyAsset("models/xyz_cube.stl", "mirror-axes-cube.stl")
        val model = Model(modelFile)
        try {
            val triangles = model.getTriangleCount(0)
            val volume = model.getObjectVolume(0)
            val beforeMin = Vec3d(); val beforeMax = Vec3d()
            model.getBoundingBoxExact(0, beforeMin, beforeMax)
            val centerBefore = doubleArrayOf(
                (beforeMin.x + beforeMax.x) / 2.0,
                (beforeMin.y + beforeMax.y) / 2.0,
                (beforeMin.z + beforeMax.z) / 2.0,
            )

            for ((axis, component) in listOf(
                ModelMirrorAxis.X to 0,
                ModelMirrorAxis.Y to 1,
                ModelMirrorAxis.Z to 2,
            )) {
                model.mirror(0, axis)
                val expected = doubleArrayOf(1.0, 1.0, 1.0).also { it[component] = -1.0 }
                assertLinearTransform(model, expected)
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                val centerAfter = listOf((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0)
                centerBefore.indices.forEach { coordinate ->
                    assertEquals("$axis mirror should preserve the object's center", centerBefore[coordinate], centerAfter[coordinate], 0.001)
                }
                assertEquals("$axis mirror must retain the printable mesh", triangles, model.getTriangleCount(0))
                assertEquals("$axis mirror must retain the solid volume", volume, model.getObjectVolume(0), 0.001)

                model.mirror(0, axis)
                assertLinearTransform(model, doubleArrayOf(1.0, 1.0, 1.0))
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    private fun assertLinearTransform(model: Model, expectedDiagonal: DoubleArray) {
        val rotation = Vec3d(); val scale = Vec3d(); val mirror = Vec3d()
        model.getRotation(0, rotation)
        model.getScale(0, scale)
        model.getMirror(0, mirror)
        val sx = scale.x * mirror.x; val sy = scale.y * mirror.y; val sz = scale.z * mirror.z
        val cx = kotlin.math.cos(rotation.x); val cy = kotlin.math.cos(rotation.y); val cz = kotlin.math.cos(rotation.z)
        val sinX = kotlin.math.sin(rotation.x); val sinY = kotlin.math.sin(rotation.y); val sinZ = kotlin.math.sin(rotation.z)
        val matrix = arrayOf(
            doubleArrayOf(cz * cy * sx, (cz * sinY * sinX - sinZ * cx) * sy, (cz * sinY * cx + sinZ * sinX) * sz),
            doubleArrayOf(sinZ * cy * sx, (sinZ * sinY * sinX + cz * cx) * sy, (sinZ * sinY * cx - cz * sinX) * sz),
            doubleArrayOf(-sinY * sx, cy * sinX * sy, cy * cx * sz),
        )
        for (row in 0..2) for (column in 0..2) {
            val expected = if (row == column) expectedDiagonal[row] else 0.0
            assertEquals("mirrored transform matrix[$row,$column]", expected, matrix[row][column], 1e-5)
        }
    }

    @Test fun combineSelectedMeshesKeepsPlacementAndPerVolumeFilamentAssignments() {
        val modelFile = copyAsset("models/xyz_cube.stl", "combine-cube.stl")
        val model = Model(modelFile)
        try {
            assertEquals(1, model.objectsCount)
            model.addObject(model, 0)
            assertEquals(2, model.objectsCount)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.translate(1, 30.0, 0.0, 0.0)
            val firstTriangles = model.getTriangleCount(0)
            val secondTriangles = model.getTriangleCount(1)
            val expectedFilaments = (model.getUsedFilaments(0) + model.getUsedFilaments(1)).toSet()

            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(1, model.objectsCount)
            assertEquals(firstTriangles + secondTriangles, model.getTriangleCount(0))
            assertTrue("combined part materials should remain represented", model.getUsedFilaments(0).toSet().containsAll(expectedFilaments))
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            assertTrue("both source placements should be retained", max.x - min.x > 30.0)
        } finally {
            model.release()
        }
    }

    @Test fun combineKeepsFirstRequestedTargetWhenDeletingEarlierObjectIndices() {
        val modelFile = copyAsset("models/xyz_cube.stl", "combine-primary-target-cube.stl")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, 32.0, 0.0, 0.0)
            model.translate(2, 64.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.setUniformFilament(2, 3)
            val expectedTriangles = (0..2).sumOf(model::getTriangleCount)

            // Object 2 is intentionally first and must remain the destination even though
            // deleting objects 0 and 1 shifts it down to final index 0.
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(2, 0, 1)))
            assertEquals(1, model.objectsCount)
            assertEquals(expectedTriangles, model.getTriangleCount(0))
            assertEquals("the selected destination material should lead the joined parts", 3,
                model.getVolumeExtruder(0, 0))
            assertEquals(listOf(1, 2, 3), model.getUsedFilaments(0).toList())
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun groupedPartCanMoveAndScaleWithoutSplittingTheParentOrLosingMaterial() {
        val modelFile = createCubeFixture("grouped-part-transform-cube.stl", 20f)
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.translate(1, 30.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(1, model.objectsCount)
            assertEquals(2, model.getObjectPartCount(0))
            val originalTriangles = model.getTriangleCount(0)
            val originalVolume = model.getObjectVolume(0)
            val minBefore = Vec3d(); val maxBefore = Vec3d()
            model.getBoundingBoxExact(0, minBefore, maxBefore)

            assertTrue(model.transformObjectPart(
                0, 1, 5.0, -2.0, 3.0, 2.0, 1.0, 1.0,
                rotationXDegrees = 0.0, rotationYDegrees = 0.0, rotationZDegrees = 90.0,
            ))

            assertEquals("editing a grouped part must keep one parent object", 1, model.objectsCount)
            assertEquals("editing a grouped part must keep both children", 2, model.getObjectPartCount(0))
            assertEquals("a part transform must not change mesh topology", originalTriangles, model.getTriangleCount(0))
            assertEquals("material assignments must remain attached to their original parts", listOf(1, 2),
                (0 until model.getObjectPartCount(0)).map { model.getObjectPartExtruder(0, it) })
            assertTrue("scaling one of two grouped parts should change the total volume",
                model.getObjectVolume(0) > originalVolume)
            val minAfter = Vec3d(); val maxAfter = Vec3d()
            model.getBoundingBoxExact(0, minAfter, maxAfter)
            assertTrue("part movement should update the parent bounds", maxAfter.x > maxBefore.x + 4.9)
            assertTrue("part rotation should update the parent's Y bounds", maxAfter.y > maxBefore.y + 7.9)
            assertTrue("the part's Z movement should be reflected in the model bounds", maxAfter.z > maxBefore.z + 2.9)

            val volumeAfter = model.getObjectVolume(0)
            assertFalse("singular scale must be rejected", model.transformObjectPart(0, 1, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0))
            assertFalse("extreme part movement must be rejected", model.transformObjectPart(0, 1, 10_001.0, 0.0, 0.0))
            assertEquals("rejected transforms must leave the model untouched", volumeAfter, model.getObjectVolume(0), 0.001)
            assertEquals(2, model.getObjectPartCount(0))
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun combiningMirroredObjectPreservesPrintablePartsAndMaterialAssignments() {
        val modelFile = copyAsset("models/xyz_cube.stl", "combine-mirrored-cube.stl")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.mirror(1, ModelMirrorAxis.X)
            model.translate(1, 30.0, 0.0, 0.0)
            val expectedTriangles = model.getTriangleCount(0) + model.getTriangleCount(1)
            val expectedFilaments = (model.getUsedFilaments(0) + model.getUsedFilaments(1)).toSet()

            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(1, model.objectsCount)
            assertEquals(expectedTriangles, model.getTriangleCount(0))
            assertTrue("mirrored printable part keeps its filament assignment",
                model.getUsedFilaments(0).toSet().containsAll(expectedFilaments))
            assertTrue("mirrored world placement survives joining",
                model.getObjectVolume(0) > 0.0)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun combiningObjectsMaterializesEveryLinkedInstanceAtItsWorldPlacement() {
        val modelFile = copyAsset("models/xyz_cube.stl", "combine-linked-instances-cube.stl")
        val model = Model(modelFile)
        try {
            val originalMin = Vec3d(); val originalMax = Vec3d()
            model.getBoundingBoxExact(0, originalMin, originalMax)
            val width = originalMax.x - originalMin.x
            assertTrue(width > 0.0)
            assertEquals(1, model.duplicateInstance(0, 0, Vec3d(width + 8.0, 0.0, 0.0)))
            model.addObject(model, 0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.translate(1, width * 3.0 + 24.0, 0.0, 0.0)

            fun bounds(index: Int): Pair<Vec3d, Vec3d> {
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(index, min, max)
                return min to max
            }
            val before = listOf(bounds(0), bounds(1))
            val expectedMinX = before.minOf { it.first.x }
            val expectedMaxX = before.maxOf { it.second.x }
            val perObjectTriangles = model.getTriangleCount(0)
            val expectedTriangles = perObjectTriangles * 4

            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(1, model.objectsCount)
            assertEquals("both two-instance objects become four printable part volumes",
                expectedTriangles, model.getTriangleCount(0))
            assertTrue("both original part materials must survive", model.getUsedFilaments(0).toSet().containsAll(setOf(1, 2)))
            val joinedMin = Vec3d(); val joinedMax = Vec3d()
            model.getBoundingBoxExact(0, joinedMin, joinedMax)
            assertEquals("joined linked copies keep their outer world-space minimum", expectedMinX, joinedMin.x, 0.02)
            assertEquals("joined linked copies keep their outer world-space maximum", expectedMaxX, joinedMax.x, 0.02)
            assertTrue("the joined repeated instances stay printable", model.getObjectVolume(0) > 0.0)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun linkedInstanceDuplicationValidatesInputsAndPreservesTheSourceMesh() {
        val modelFile = copyAsset("models/xyz_cube.stl", "duplicate-linked-instance-cube.stl")
        val model = Model(modelFile)
        try {
            val minBefore = Vec3d(); val maxBefore = Vec3d()
            model.getBoundingBoxExact(0, minBefore, maxBefore)
            val width = maxBefore.x - minBefore.x
            val volumeBefore = model.getObjectVolume(0)
            val trianglesBefore = model.getTriangleCount(0)

            assertEquals(-1, model.duplicateInstance(-1, 0, Vec3d(30.0, 0.0, 0.0)))
            assertEquals(-1, model.duplicateInstance(0, -1, Vec3d(30.0, 0.0, 0.0)))
            assertEquals(-1, model.duplicateInstance(0, 0, Vec3d(Double.NaN, 0.0, 0.0)))
            val minUnchanged = Vec3d(); val maxUnchanged = Vec3d()
            model.getBoundingBoxExact(0, minUnchanged, maxUnchanged)
            assertEquals(minBefore.x, minUnchanged.x, 0.001)
            assertEquals(maxBefore.x, maxUnchanged.x, 0.001)

            assertEquals(1, model.duplicateInstance(0, 0, Vec3d(width + 9.0, 0.0, 0.0)))
            val minAfter = Vec3d(); val maxAfter = Vec3d()
            model.getBoundingBoxExact(0, minAfter, maxAfter)
            assertEquals(2.0 * width + 9.0, maxAfter.x - minAfter.x, 0.02)
            assertEquals("linked copies share the source mesh instead of duplicating triangle data",
                trianglesBefore, model.getTriangleCount(0))
            assertEquals("each linked instance keeps the original solid volume", volumeBefore,
                model.getObjectVolume(0), 0.001)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun combiningRotatedNonUniformlyScaledObjectPreservesWorldBounds() {
        val modelFile = copyAsset("models/xyz_cube.stl", "combine-transformed-cube.stl")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.scale(1, 1.4, 0.7, 1.2)
            model.rotate(1, 0.35, -0.22, 0.48)
            model.translate(1, 31.0, -17.0, 9.0)

            fun bounds(index: Int): Pair<DoubleArray, DoubleArray> {
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(index, min, max)
                return doubleArrayOf(min.x, min.y, min.z) to doubleArrayOf(max.x, max.y, max.z)
            }
            val sourceBounds = listOf(bounds(0), bounds(1))
            val expectedMin = DoubleArray(3) { axis -> sourceBounds.minOf { it.first[axis] } }
            val expectedMax = DoubleArray(3) { axis -> sourceBounds.maxOf { it.second[axis] } }
            val expectedTriangles = model.getTriangleCount(0) + model.getTriangleCount(1)

            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(1, model.objectsCount)
            assertEquals(expectedTriangles, model.getTriangleCount(0))
            assertTrue("both source filament assignments must remain on the joined part",
                model.getUsedFilaments(0).toSet().containsAll(setOf(1, 2)))
            val joined = bounds(0)
            for (axis in 0..2) {
                assertEquals("joined minimum on axis $axis", expectedMin[axis], joined.first[axis], 0.01)
                assertEquals("joined maximum on axis $axis", expectedMax[axis], joined.second[axis], 0.01)
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun rejectedObjectCombinesLeaveGeometryPlacementAndMaterialsUnchanged() {
        val modelFile = copyAsset("models/xyz_cube.stl", "combine-invalid-input-cube.stl")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.translate(1, 37.0, -12.0, 8.0)

            data class Snapshot(
                val triangles: Int,
                val volume: Double,
                val filaments: List<Int>,
                val min: List<Double>,
                val max: List<Double>,
            )
            fun snapshot(index: Int): Snapshot {
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(index, min, max)
                return Snapshot(
                    model.getTriangleCount(index),
                    model.getObjectVolume(index),
                    model.getUsedFilaments(index).toList(),
                    listOf(min.x, min.y, min.z),
                    listOf(max.x, max.y, max.z),
                )
            }

            val before = listOf(snapshot(0), snapshot(1))
            val invalidRequests = listOf(
                intArrayOf(0),
                intArrayOf(0, 1, 1),
                intArrayOf(0, 2),
                intArrayOf(-1, 1),
            )
            invalidRequests.forEach { indices ->
                assertTrue("invalid combine request must be rejected", model.combineObjectsAsParts(indices) < 0)
                assertEquals("rejected request must preserve both source objects", 2, model.objectsCount)
                assertEquals("target geometry/material/placement must remain unchanged", before[0], snapshot(0))
                assertEquals("source geometry/material/placement must remain unchanged", before[1], snapshot(1))
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun placingOnePrintableObjectOnAnotherAlignsCentersAndTouchesSurface() {
        val modelFile = copyAsset("models/xyz_cube.stl", "place-cube.stl")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.translate(1, 28.0, -11.0, 9.0)
            fun bounds(index: Int): ObjectBounds3D {
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(index, min, max)
                return ObjectBounds3D(min.x, min.y, min.z, max.x, max.y, max.z)
            }
            val before = bounds(1)
            val target = bounds(0)
            val delta = ObjectPlacement.onTop(before, target, clearanceMm = 0.0)
            assertTrue("placement should be calculable for valid printable meshes", delta != null)
            model.translate(1, delta!!.x, delta.y, delta.z)
            val placed = bounds(1)
            assertEquals((target.minX + target.maxX) / 2.0, (placed.minX + placed.maxX) / 2.0, 0.001)
            assertEquals((target.minY + target.maxY) / 2.0, (placed.minY + placed.maxY) / 2.0, 0.001)
            assertEquals(target.maxZ, placed.minZ, 0.001)
        } finally {
            model.release()
        }
    }

    @Test fun failedCutRollsBackOnlyConnectorsAddedForThatCut() {
        val modelFile = copyAsset("models/xyz_cube.stl", "cut-connector-rollback.stl")
        val model = Model(modelFile)
        try {
            val originalTriangles = model.getTriangleCount(0)
            val originalVolume = model.getObjectVolume(0)
            model.addConnector(0, Vec3d(0.0, 0.0, 5.0), 2f, 5f, 0)
            val connectorsBeforeCut = model.getCutConnectorCount(0)
            assertEquals("the fixture should contain one pre-existing connector", 1, connectorsBeforeCut)

            model.addConnector(0, Vec3d(1.0, 1.0, 5.0), 2f, 5f, 1)
            assertEquals(2, model.getCutConnectorCount(0))

            val applied = model.cutPreservingConnectorsOnFailure(
                objectIndex = 0,
                connectorCountBeforeCommit = connectorsBeforeCut,
                zHeight = 0.0,
                rotX = 0.0,
                rotY = 0.0,
                keepUpper = false,
                keepLower = false,
            )

            assertTrue("a cut with neither resulting side selected should fail", !applied)
            assertEquals("rollback must preserve connectors that existed before this cut", connectorsBeforeCut,
                model.getCutConnectorCount(0))
            assertEquals("failed cut must preserve source triangles", originalTriangles, model.getTriangleCount(0))
            assertEquals("failed cut must preserve source volume", originalVolume, model.getObjectVolume(0), 0.001)
            assertEquals(1, model.objectsCount)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun placingSelectedObjectGroupOnAnotherKeepsGroupSpacingAndTouchesSurface() {
        val modelFile = copyAsset("models/xyz_cube.stl", "place-group-cube.stl")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, 30.0, -10.0, 8.0)
            model.translate(2, 65.0, 12.0, 14.0)
            fun bounds(index: Int): ObjectBounds3D {
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(index, min, max)
                return ObjectBounds3D(min.x, min.y, min.z, max.x, max.y, max.z)
            }
            val firstBefore = bounds(1)
            val secondBefore = bounds(2)
            val movingGroup = requireNotNull(ObjectPlacement.enclosing(listOf(firstBefore, secondBefore)))
            val target = bounds(0)
            val delta = requireNotNull(ObjectPlacement.onTop(movingGroup, target))

            model.translate(1, delta.x, delta.y, delta.z)
            model.translate(2, delta.x, delta.y, delta.z)

            val firstAfter = bounds(1)
            val secondAfter = bounds(2)
            val placedGroup = requireNotNull(ObjectPlacement.enclosing(listOf(firstAfter, secondAfter)))
            assertEquals((target.minX + target.maxX) / 2.0, (placedGroup.minX + placedGroup.maxX) / 2.0, 0.001)
            assertEquals((target.minY + target.maxY) / 2.0, (placedGroup.minY + placedGroup.maxY) / 2.0, 0.001)
            assertEquals(target.maxZ, placedGroup.minZ, 0.001)
            assertEquals(
                (secondBefore.minX + secondBefore.maxX) / 2.0 - (firstBefore.minX + firstBefore.maxX) / 2.0,
                (secondAfter.minX + secondAfter.maxX) / 2.0 - (firstAfter.minX + firstAfter.maxX) / 2.0,
                0.001,
            )
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun standaloneTextCreatesIndependentPrintableMeshAtPickedSurface() {
        val modelFile = copyAsset("models/xyz_cube.stl", "text-base-cube.stl")
        val fontFile = copyAsset("font/roboto_medium.ttf", "text-model-font.ttf")
        val model = Model(modelFile)
        try {
            val before = model.objectsCount
            val index = model.createTextObject(
                fontFile.absolutePath,
                "ORCA",
                10f,
                1.2f,
                Vec3d(20.0, 15.0, 12.0),
                Vec3d(0.0, 0.0, 1.0),
            )
            assertEquals(before, index)
            assertEquals(before + 1, model.objectsCount)
            assertTrue("text must be a real mesh, not just an embossed modifier", model.getTriangleCount(index) > 0)
            assertEquals("ORCA", model.getObjectName(index))
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(index, min, max)
            assertTrue(min.z >= 11.99 && min.z <= 12.01)
            assertTrue(max.z - min.z > 1.0 && max.z - min.z < 1.5)
        } finally {
            model.release()
        }
    }

    @Test fun standaloneTextCanBeBooleanUnionedIntoTargetWithoutLosingItsMaterial() {
        val modelFile = createCubeFixture("boolean-text-target.stl", side = 20f)
        val fontFile = copyAsset("font/roboto_medium.ttf", "boolean-text-font.ttf")
        val model = Model(modelFile)
        try {
            model.setUniformFilament(0, 2)
            val targetVolume = model.getObjectVolume(0)
            val targetMin = Vec3d(); val targetMax = Vec3d()
            model.getBoundingBoxExact(0, targetMin, targetMax)

            // The text starts 1 mm inside the target's top face, so the union has a real
            // shared volume and must leave a raised, connected letter above the surface.
            val textIndex = model.createTextObject(
                fontFile.absolutePath,
                "A\nB",
                8f,
                2f,
                Vec3d(10.0, 10.0, targetMax.z - 1.0),
                Vec3d(0.0, 0.0, 1.0),
            )
            assertEquals("standalone text must be added as a second printable object", 1, textIndex)
            assertEquals(2, model.objectsCount)
            model.setUniformFilament(textIndex, 3)
            assertTrue("standalone text needs printable geometry before the Boolean", model.getTriangleCount(textIndex) > 0)

            val result = model.booleanObjects(0, textIndex, BooleanOperation.UNION)

            assertEquals("text and target should become one printable solid", 0, result)
            assertEquals(1, model.objectsCount)
            assertTrue("union must preserve the target body and add raised text volume",
                model.getObjectVolume(result) > targetVolume)
            assertEquals("Boolean union keeps the target object's filament assignment", listOf(2),
                model.getUsedFilaments(result).toList())
            val resultMin = Vec3d(); val resultMax = Vec3d()
            model.getBoundingBoxExact(result, resultMin, resultMax)
            assertEquals("the target keeps its original plate-contact plane", targetMin.z, resultMin.z, 0.02)
            assertTrue("the fused text must protrude above the target surface", resultMax.z > targetMax.z + 0.5)
            assertTrue("the Boolean result remains a printable closed body", model.getObjectVolume(result) > 0.0)
        } finally {
            model.release()
            modelFile.delete()
            fontFile.delete()
        }
    }

    @Test fun standaloneTextCanBeBooleanSubtractedAsAnEngraving() {
        val modelFile = createCubeFixture("boolean-text-engraving-target.stl", side = 20f)
        val fontFile = copyAsset("font/roboto_medium.ttf", "boolean-text-engraving-font.ttf")
        val model = Model(modelFile)
        try {
            model.setUniformFilament(0, 2)
            val targetVolume = model.getObjectVolume(0)
            val targetMin = Vec3d(); val targetMax = Vec3d()
            model.getBoundingBoxExact(0, targetMin, targetMax)

            val textIndex = model.createTextObject(
                fontFile.absolutePath,
                "A",
                8f,
                2f,
                Vec3d(10.0, 10.0, targetMax.z - 1.0),
                Vec3d(0.0, 0.0, 1.0),
            )
            assertEquals("engraving text must be an independent printable cutter", 1, textIndex)
            model.setUniformFilament(textIndex, 3)

            val result = model.booleanObjects(0, textIndex, BooleanOperation.DIFFERENCE)

            assertEquals("text should cut its intersecting shape from the target", 0, result)
            assertEquals(1, model.objectsCount)
            val engravedVolume = model.getObjectVolume(result)
            assertTrue("engraving should remove target material", engravedVolume > 0.0 && engravedVolume < targetVolume)
            assertEquals("engraving keeps the target filament", listOf(2), model.getUsedFilaments(result).toList())
            val resultMin = Vec3d(); val resultMax = Vec3d()
            model.getBoundingBoxExact(result, resultMin, resultMax)
            assertEquals("engraving preserves the plate-contact plane", targetMin.z, resultMin.z, 0.02)
            assertEquals("engraving stays within the original top surface", targetMax.z, resultMax.z, 0.02)
        } finally {
            model.release()
            modelFile.delete()
            fontFile.delete()
        }
    }

    @Test fun standaloneTextCanConformToCurvedCylinderAndRemainsPrintable() {
        val modelFile = createCylinderFixture("text-curved-cylinder.stl", radius = 25f, height = 30f, segments = 96)
        val fontFile = copyAsset("font/roboto_medium.ttf", "text-curved-surface-font.ttf")
        val model = Model(modelFile)
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val position = Vec3d(max.x, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0)
            val flatIndex = model.createTextObject(
                fontFile.absolutePath, "ORCA", 10f, 1.2f, position, Vec3d(1.0, 0.0, 0.0),
            )
            assertEquals(1, flatIndex)
            val flatTriangles = model.getTriangleCount(flatIndex)
            val cylinderTriangles = model.getTriangleCount(0)
            val curvedIndex = model.createTextObject(
                fontFile.absolutePath, "OR\nCA", 10f, 1.2f, position, Vec3d(1.0, 0.0, 0.0),
                targetObjectIndex = 0,
            )
            assertEquals("curved text should be added as a part of the target printable object", 0, curvedIndex)
            assertEquals("surface text must not introduce a floating plate object", 2, model.objectsCount)
            assertTrue("surface following must refine the mesh before bending it",
                model.getTriangleCount(curvedIndex) - cylinderTriangles > flatTriangles)
            assertTrue("the target and curved text must remain a closed printable solid", model.getObjectVolume(curvedIndex) > 0.0)
            val curvedMin = Vec3d(); val curvedMax = Vec3d()
            model.getBoundingBoxExact(curvedIndex, curvedMin, curvedMax)
            assertTrue("text should protrude from the curved cylinder surface", curvedMax.x > max.x + 1.0)
            assertTrue("conforming text remains attached to the surface rather than floating", curvedMin.z <= min.z + 0.01)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun standaloneSurfaceTextUsesTheTappedRepeatedInstance() {
        val model = createRepeatedInstanceModel("standalone-text-repeated-instance.stl")
        val fontFile = copyAsset("font/roboto_medium.ttf", "repeated-instance-text-font.ttf")
        try {
            assertEquals("the repeated build items should remain instances of one object", 1, model.objectsCount)
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            assertTrue("fixture must contain both translated instances", max.x - min.x > 40.0)
            val tapX = max.x

            val index = model.createTextObject(
                fontFile.absolutePath,
                "A",
                3f,
                1f,
                Vec3d(max.x, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0),
                Vec3d(1.0, 0.0, 0.0),
                targetObjectIndex = 0,
            )

            assertEquals("surface text should be added to the shared model object", 0, index)
            model.getBoundingBoxExact(0, min, max)
            assertTrue("text placed on the second copy must not jump to a remote copy", max.x < tapX + 2.0)
            assertTrue("text must protrude from the tapped outer face", max.x > tapX)
        } finally {
            model.release()
            fontFile.delete()
        }
    }

    @Test fun embossOnSecondRepeatedInstanceUsesItsWorldToLocalTransform() {
        val model = createRepeatedInstanceModel("emboss-repeated-instance.stl")
        val fontFile = copyAsset("font/roboto_medium.ttf", "repeated-instance-emboss-font.ttf")
        try {
            assertEquals("the repeated build items should remain instances of one object", 1, model.objectsCount)
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            assertTrue("fixture must contain both translated instances", max.x - min.x > 40.0)
            val tapX = max.x

            model.embossText(
                0,
                fontFile.absolutePath,
                "A",
                3f,
                1f,
                0,
                Vec3d(max.x, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0),
                Vec3d(1.0, 0.0, 0.0),
            )

            model.getBoundingBoxExact(0, min, max)
            assertTrue("emboss must stay attached to the tapped second copy", max.x < tapX + 2.0)
            assertTrue("emboss must extend beyond the tapped face", max.x > tapX)
        } finally {
            model.release()
            fontFile.delete()
        }
    }

    @Test fun standaloneSurfaceTextInheritsTheFilamentOfTheHitMultipartSurface() {
        val modelFile = copyAsset("models/two-color-project.3mf", "surface-text-part-filament.3mf")
        val fontFile = copyAsset("font/roboto_medium.ttf", "surface-text-part-filament.ttf")
        val model = Model(modelFile)
        try {
            assertEquals(2, model.objectsCount)
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals("the second target surface must retain its imported filament", 2,
                model.getVolumeExtruder(0, 1))
            model.setExtruder(0, 4) // Deliberately differs from either part's material.

            val index = model.createTextObject(
                fontFile.absolutePath,
                "A",
                3f,
                1f,
                Vec3d(61.0, 43.0, 3.0),
                Vec3d(1.0, 0.0, 0.0),
                targetObjectIndex = 0,
            )

            assertEquals(0, index)
            assertEquals("surface text must use the hit part's filament, not parent default 4",
                2, model.getVolumeExtruder(0, 2))
        } finally {
            model.release()
            modelFile.delete()
            fontFile.delete()
        }
    }

    @Test fun embossOnMultipartSurfaceInheritsTheFilamentOfTheHitPart() {
        val modelFile = copyAsset("models/two-color-project.3mf", "emboss-part-filament.3mf")
        val fontFile = copyAsset("font/roboto_medium.ttf", "emboss-part-filament.ttf")
        val model = Model(modelFile)
        try {
            assertEquals(2, model.objectsCount)
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals("the second target surface must retain its imported filament", 2,
                model.getVolumeExtruder(0, 1))
            model.setExtruder(0, 4) // The tapped second part remains explicitly assigned to filament 2.

            model.embossText(
                0,
                fontFile.absolutePath,
                "A",
                3f,
                1f,
                0,
                Vec3d(61.0, 43.0, 3.0),
                Vec3d(1.0, 0.0, 0.0),
            )

            assertEquals("emboss must use the hit part's filament, not parent default 4",
                2, model.getVolumeExtruder(0, 2))
        } finally {
            model.release()
            modelFile.delete()
            fontFile.delete()
        }
    }

    @Test fun svgImportCreatesStandalonePrintableGeometry() {
        val svgFile = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "model-editing-printable-shape.svg",
        )
        svgFile.writeText(
            """<svg xmlns="http://www.w3.org/2000/svg" width="24mm" height="18mm" viewBox="0 0 24 18">
                <path id="frame" fill="#202020" d="M1 1 H23 V17 H1 Z M7 6 H17 V12 H7 Z"/>
            </svg>""".trimIndent(),
        )
        val model = Model(svgFile)
        try {
            assertEquals("SVG import should create one printable object", 1, model.objectsCount)
            assertTrue("SVG import should create a non-empty triangle mesh", model.getTriangleCount(0) > 0)
            assertTrue("SVG import should create a closed solid with positive volume", model.getObjectVolume(0) > 0.0)
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            assertTrue("imported SVG should have a printable width", max.x - min.x > 1.0)
            assertTrue("imported SVG should have a printable height", max.y - min.y > 1.0)
            assertTrue("imported SVG should be extruded into a 3D body", max.z - min.z > 1.0)
        } finally {
            model.release()
            svgFile.delete()
        }

        val unsafeSvg = File(svgFile.parentFile, "svg-dtd-must-be-rejected.svg").apply {
            writeText(
                "<!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///data/local/tmp/not-readable\">]>" +
                    "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>&x;</text></svg>",
            )
        }
        try {
            val unsafeResult = runCatching {
                ru.ytkab0bp.slicebeam.components.SvgImportGuard.validate(unsafeSvg)
            }
            assertTrue("DTD/entity declarations must be rejected on Android", unsafeResult.isFailure)
        } finally {
            unsafeSvg.delete()
        }
    }

    @Test fun detailedMulticolorSvgImportsAsPrintableGeometry() {
        val svgFile = copyAsset(
            "models/Beste_Oma_Kind_Herz_3D_Druck.svg",
            "detailed-multicolor-svg.svg",
        )
        val validationProblem = try {
            ru.ytkab0bp.slicebeam.components.SvgImportGuard.validate(svgFile)
        } catch (error: Exception) {
            throw AssertionError("SVG safety validation threw unexpectedly", error)
        }
        assertTrue(
            "the supplied SVG should pass the app's import safety guard; problem=$validationProblem",
            validationProblem == null,
        )
        val model = Model(svgFile)
        try {
            assertEquals("the three SVG color groups should import as one model object", 1, model.objectsCount)
            assertTrue("the imported color groups should produce mesh triangles", model.getTriangleCount(0) > 0)
            assertTrue("the imported SVG should have printable volume", model.getObjectVolume(0) > 0.0)
            assertEquals("white SVG fill should map to the first slicer filament", 1, model.getVolumeExtruder(0, 0))
            assertEquals("red SVG fill should map to a distinct second filament", 2, model.getVolumeExtruder(0, 1))
            assertEquals("black SVG fill should map to a distinct third filament", 3, model.getVolumeExtruder(0, 2))

            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            assertTrue("the source artwork should retain a useful width", max.x - min.x > 100.0)
            assertTrue("the source artwork should retain a useful height", max.y - min.y > 50.0)
            assertTrue("the SVG should be extruded into a 3D body", max.z - min.z >= 9.9)
        } finally {
            model.release()
            svgFile.delete()
        }
    }

    @Test fun nativeBooleanUnionCreatesOneClosedObjectWithCombinedVolume() = assertBooleanResult(
        BooleanOperation.UNION,
        expectedVolumeFactor = 1.5,
    )

    @Test fun nativeBooleanUnionPreservesAllLinkedTargetInstances() {
        val modelFile = copyAsset("models/xyz_cube.stl", "boolean-linked-target-instances.stl")
        val model = Model(modelFile)
        val cutterModel = Model(modelFile)
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val width = max.x - min.x
            val sourceVolume = model.getObjectVolume(0)
            model.setUniformFilament(0, 2)
            assertEquals("the target should gain one linked copy", 1,
                model.duplicateInstance(0, 0, Vec3d(width * 1.5, 0.0, 0.0)))

            // Place the cutter across only the second target instance. The Boolean result
            // must retain the untouched first copy and include the union at the far end.
            model.addObject(cutterModel, 0)
            model.setUniformFilament(1, 1)
            model.translate(1, width * 2.0, 0.0, 0.0)

            val result = model.booleanObjects(0, 1, BooleanOperation.UNION)

            assertEquals("Boolean should replace both operands with one printable result", 0, result)
            assertEquals(1, model.objectsCount)
            assertEquals("both disjoint/overlapping target instances and the cutter must survive",
                2.5, model.getObjectVolume(result) / sourceVolume, 0.03)
            assertEquals("Boolean output should keep the target material", listOf(2),
                model.getUsedFilaments(result).toList())
            val resultMin = Vec3d(); val resultMax = Vec3d()
            model.getBoundingBoxExact(result, resultMin, resultMax)
            assertEquals("the far target instance and cutter must remain in world position",
                width * 3.0, resultMax.x - resultMin.x, 0.03)
            assertEquals(max.y - min.y, resultMax.y - resultMin.y, 0.03)
            assertEquals(max.z - min.z, resultMax.z - resultMin.z, 0.03)
        } finally {
            cutterModel.release()
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeBooleanDifferenceSubtractsTheOverlappingHalf() = assertBooleanResult(
        BooleanOperation.DIFFERENCE,
        expectedVolumeFactor = 0.5,
    )

    @Test fun nativeBooleanIntersectionKeepsOnlyTheOverlappingHalf() = assertBooleanResult(
        BooleanOperation.INTERSECTION,
        expectedVolumeFactor = 0.5,
    )

    @Test fun nativeBooleanUnionKeepsDisjointClosedSolidsAsOneModelObject() {
        val modelFile = copyAsset("models/xyz_cube.stl", "boolean-disjoint-union.stl")
        val model = Model(modelFile)
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val sourceVolume = model.getObjectVolume(0)
            model.addObject(model, 0)
            model.translate(1, (max.x - min.x) * 1.5, 0.0, 0.0)

            val result = model.booleanObjects(0, 1, BooleanOperation.UNION)

            assertEquals(0, result)
            assertEquals(1, model.objectsCount)
            assertEquals(2.0, model.getObjectVolume(result) / sourceVolume, 0.02)
            val resultMin = Vec3d(); val resultMax = Vec3d()
            model.getBoundingBoxExact(result, resultMin, resultMax)
            assertEquals((max.y - min.y), resultMax.y - resultMin.y, 0.02)
            assertEquals((max.z - min.z), resultMax.z - resultMin.z, 0.02)
            assertEquals((max.x - min.x) * 2.5, resultMax.x - resultMin.x, 0.02)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeBooleanUnionMergesSolidsTouchingAcrossOneCoplanarFace() {
        val modelFile = createCubeFixture("boolean-face-touching-union.stl", 20f)
        val model = Model(modelFile)
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val width = max.x - min.x
            val sourceVolume = model.getObjectVolume(0)
            model.addObject(model, 0)
            // The second cube shares the target's +X face exactly: no gap and no overlap.
            model.translate(1, width, 0.0, 0.0)

            val result = model.booleanObjects(0, 1, BooleanOperation.UNION)

            assertEquals("face-touching closed solids should union into one object", 0, result)
            assertEquals(1, model.objectsCount)
            assertEquals("coplanar shared faces must not lose or duplicate volume", 2.0,
                model.getObjectVolume(result) / sourceVolume, 0.002)
            val resultMin = Vec3d(); val resultMax = Vec3d()
            model.getBoundingBoxExact(result, resultMin, resultMax)
            assertEquals(width * 2.0, resultMax.x - resultMin.x, 0.002)
            assertEquals(max.y - min.y, resultMax.y - resultMin.y, 0.002)
            assertEquals(max.z - min.z, resultMax.z - resultMin.z, 0.002)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeBooleanUnionWithVertexTouchIsValidOrLeavesBothInputsUntouched() {
        val modelFile = createCubeFixture("boolean-vertex-touching-union.stl", 20f)
        val model = Model(modelFile)
        try {
            val targetMin = Vec3d(); val targetMax = Vec3d()
            model.getBoundingBoxExact(0, targetMin, targetMax)
            val side = targetMax.x - targetMin.x
            val sourceVolume = model.getObjectVolume(0)
            val sourceTriangles = model.getTriangleCount(0)
            model.addObject(model, 0)
            // The cubes meet at exactly one vertex. Some mesh solvers reject this
            // non-manifold boundary; rejection must not consume or alter either input.
            model.translate(1, side, side, side)
            val result = model.booleanObjects(0, 1, BooleanOperation.UNION)

            if (result >= 0) {
                assertEquals("a successful vertex-touch union should yield one valid object", 1,
                    model.objectsCount)
                assertEquals("a successful union must preserve both volumes", 2.0,
                    model.getObjectVolume(result) / sourceVolume, 0.002)
                assertTrue("a successful union must retain triangles", model.getTriangleCount(result) > 0)
                val resultMin = Vec3d(); val resultMax = Vec3d()
                model.getBoundingBoxExact(result, resultMin, resultMax)
                assertEquals(side * 2.0, resultMax.x - resultMin.x, 0.002)
                assertEquals(side * 2.0, resultMax.y - resultMin.y, 0.002)
                assertEquals(side * 2.0, resultMax.z - resultMin.z, 0.002)
            } else {
                assertEquals("rejected non-manifold union must preserve both source objects", 2,
                    model.objectsCount)
                for (index in 0..1) {
                    assertEquals(sourceVolume, model.getObjectVolume(index), sourceVolume * 0.002)
                    assertEquals(sourceTriangles, model.getTriangleCount(index))
                    val min = Vec3d(); val max = Vec3d()
                    model.getBoundingBoxExact(index, min, max)
                    assertEquals(side, max.x - min.x, 0.002)
                    assertEquals(side, max.y - min.y, 0.002)
                    assertEquals(side, max.z - min.z, 0.002)
                }
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeBooleanDifferenceWithDisjointCutterNeverMutatesTargetGeometry() {
        val modelFile = createCubeFixture("boolean-disjoint-difference.stl", 20f)
        val model = Model(modelFile)
        try {
            val targetMin = Vec3d(); val targetMax = Vec3d()
            model.getBoundingBoxExact(0, targetMin, targetMax)
            val targetVolume = model.getObjectVolume(0)
            val targetTriangles = model.getTriangleCount(0)
            model.addObject(model, 0)
            // Keep a positive gap so the cutter cannot intersect even through tolerance.
            model.translate(1, (targetMax.x - targetMin.x) * 2.0, 0.0, 0.0)
            val cutterVolume = model.getObjectVolume(1)
            val cutterTriangles = model.getTriangleCount(1)

            val result = model.booleanObjects(0, 1, BooleanOperation.DIFFERENCE)

            if (result >= 0) {
                assertEquals("a valid empty subtraction should leave only the unchanged target", 1,
                    model.objectsCount)
                assertEquals(targetVolume, model.getObjectVolume(result), targetVolume * 0.002)
                assertEquals(targetTriangles, model.getTriangleCount(result))
            } else {
                assertEquals("a rejected subtraction must preserve both input objects", 2,
                    model.objectsCount)
                assertEquals(targetVolume, model.getObjectVolume(0), targetVolume * 0.002)
                assertEquals(targetTriangles, model.getTriangleCount(0))
                assertEquals(cutterVolume, model.getObjectVolume(1), cutterVolume * 0.002)
                assertEquals(cutterTriangles, model.getTriangleCount(1))
            }
            val preservedMin = Vec3d(); val preservedMax = Vec3d()
            model.getBoundingBoxExact(if (result >= 0) result else 0, preservedMin, preservedMax)
            assertEquals(targetMin.x, preservedMin.x, 0.002)
            assertEquals(targetMax.x, preservedMax.x, 0.002)
            assertEquals(targetMin.y, preservedMin.y, 0.002)
            assertEquals(targetMax.y, preservedMax.y, 0.002)
            assertEquals(targetMin.z, preservedMin.z, 0.002)
            assertEquals(targetMax.z, preservedMax.z, 0.002)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeMultiCutterDifferenceRejectsLaterNoOpAndPreservesEverySource() {
        val modelFile = copyAsset("models/xyz_cube.stl", "boolean-later-no-op-cutter.stl")
        val model = Model(modelFile)
        try {
            val targetMin = Vec3d(); val targetMax = Vec3d()
            model.getBoundingBoxExact(0, targetMin, targetMax)
            val width = targetMax.x - targetMin.x
            val sourceVolume = model.getObjectVolume(0)
            val sourceTriangles = model.getTriangleCount(0)
            model.addObject(model, 0)
            model.addObject(model, 0)
            // The first cutter removes half of the target. The second is well outside
            // the target; it must not be accepted merely because cutter one already cut.
            model.translate(1, width * 0.5, 0.0, 0.0)
            model.translate(2, width * 2.0, 0.0, 0.0)
            val disjointMin = Vec3d(); val disjointMax = Vec3d()
            model.getBoundingBoxExact(2, disjointMin, disjointMax)
            assertTrue("the no-op cutter must have a positive gap from the target", disjointMin.x > targetMax.x)
            val cutterVolumes = listOf(model.getObjectVolume(1), model.getObjectVolume(2))
            val cutterTriangles = listOf(model.getTriangleCount(1), model.getTriangleCount(2))

            val result = model.booleanObjects(intArrayOf(0, 1, 2), BooleanOperation.DIFFERENCE)

            assertEquals("every selected difference cutter must make a real cut", -7, result)
            assertEquals("a rejected multi-cutter edit must preserve target and cutters", 3, model.objectsCount)
            assertEquals(sourceVolume, model.getObjectVolume(0), sourceVolume * 0.002)
            assertEquals(sourceTriangles, model.getTriangleCount(0))
            for (index in 1..2) {
                assertEquals(cutterVolumes[index - 1], model.getObjectVolume(index), cutterVolumes[index - 1] * 0.002)
                assertEquals(cutterTriangles[index - 1], model.getTriangleCount(index))
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeBooleanUnionSupportsThreeSelectedObjectsAndPreservesWorldBounds() {
        val modelFile = copyAsset("models/xyz_cube.stl", "boolean-three-object-union.stl")
        val model = Model(modelFile)
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val width = max.x - min.x
            val sourceVolume = model.getObjectVolume(0)
            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, width * 0.5, 0.0, 0.0)
            model.translate(2, -width * 0.5, 0.0, 0.0)

            val result = model.booleanObjects(intArrayOf(0, 1, 2), BooleanOperation.UNION)

            assertEquals("the output should replace all three source solids", 0, result)
            assertEquals(1, model.objectsCount)
            assertEquals(2.0, model.getObjectVolume(result) / sourceVolume, 0.03)
            val resultMin = Vec3d(); val resultMax = Vec3d()
            model.getBoundingBoxExact(result, resultMin, resultMax)
            assertEquals(width * 2.0, resultMax.x - resultMin.x, 0.02)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeBooleanDifferenceAppliesTwoSeparateSelectedCutters() {
        val modelFile = copyAsset("models/xyz_cube.stl", "boolean-two-cutter-difference.stl")
        val model = Model(modelFile)
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val width = max.x - min.x
            val centerX = (min.x + max.x) / 2.0
            val centerY = (min.y + max.y) / 2.0
            val centerZ = (min.z + max.z) / 2.0
            val sourceVolume = model.getObjectVolume(0)
            model.addObject(model, 0)
            model.addObject(model, 0)
            for (index in 1..2) {
                model.scale(index, 0.3, 0.3, 0.3)
                val cutterMin = Vec3d(); val cutterMax = Vec3d()
                model.getBoundingBoxExact(index, cutterMin, cutterMax)
                val cutterCenterX = (cutterMin.x + cutterMax.x) / 2.0
                val cutterCenterY = (cutterMin.y + cutterMax.y) / 2.0
                val cutterCenterZ = (cutterMin.z + cutterMax.z) / 2.0
                val offsetX = if (index == 1) -width * 0.2 else width * 0.2
                model.translate(index, centerX + offsetX - cutterCenterX, centerY - cutterCenterY, centerZ - cutterCenterZ)
            }

            val result = model.booleanObjects(intArrayOf(0, 1, 2), BooleanOperation.DIFFERENCE)

            assertEquals("two-cutter difference should replace the three source objects", 0, result)
            assertEquals(1, model.objectsCount)
            assertEquals("two disjoint 30% cutters remove 5.4% of the base volume", 0.946,
                model.getObjectVolume(result) / sourceVolume, 0.015)
            assertTrue(model.getTriangleCount(result) > 0)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeBooleanIntersectionKeepsSharedVolumeAcrossThreeObjects() {
        val modelFile = copyAsset("models/xyz_cube.stl", "boolean-three-object-intersection.stl")
        val model = Model(modelFile)
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val width = max.x - min.x
            val sourceVolume = model.getObjectVolume(0)
            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, width * 0.2, width * 0.2, width * 0.2)
            model.translate(2, -width * 0.2, -width * 0.2, -width * 0.2)

            val result = model.booleanObjects(intArrayOf(0, 1, 2), BooleanOperation.INTERSECTION)

            assertEquals("three-solid intersection should replace all source objects", 0, result)
            assertEquals(1, model.objectsCount)
            assertEquals(0.216, model.getObjectVolume(result) / sourceVolume, 0.02)
            val resultMin = Vec3d(); val resultMax = Vec3d()
            model.getBoundingBoxExact(result, resultMin, resultMax)
            assertEquals(width * 0.6, resultMax.x - resultMin.x, 0.02)
            assertEquals(width * 0.6, resultMax.y - resultMin.y, 0.02)
            assertEquals(width * 0.6, resultMax.z - resultMin.z, 0.02)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeBooleanIntersectionHandlesThreeSolidsWithCoplanarFaces() {
        val modelFile = createCubeFixture("boolean-coplanar-three-object-intersection.stl", 20f)
        val model = Model(modelFile)
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val width = max.x - min.x
            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, width * 0.2, 0.0, 0.0)
            model.translate(2, -width * 0.2, 0.0, 0.0)

            val result = model.booleanObjects(intArrayOf(0, 1, 2), BooleanOperation.INTERSECTION)

            assertEquals("three-body intersection with shared coplanar faces should succeed", 0, result)
            assertEquals(1, model.objectsCount)
            val resultMin = Vec3d(); val resultMax = Vec3d()
            model.getBoundingBoxExact(result, resultMin, resultMax)
            assertEquals(width * 0.6, resultMax.x - resultMin.x, 0.02)
            assertEquals(width, resultMax.y - resultMin.y, 0.02)
            assertEquals(width, resultMax.z - resultMin.z, 0.02)
            assertEquals("coplanar three-solid overlap should have exact 0.6 cube volume", 0.6,
                model.getObjectVolume(result) / (width * width * width), 0.002)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun failedMultiObjectBooleanLeavesEveryInputUntouched() {
        val modelFile = copyAsset("models/xyz_cube.stl", "boolean-three-object-failure.stl")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, 10.0, 0.0, 0.0)
            model.translate(2, 40.0, 0.0, 0.0)
            val volumes = (0 until model.objectsCount).map(model::getObjectVolume)

            assertTrue(model.booleanObjects(intArrayOf(0, 1, 2), BooleanOperation.INTERSECTION) < 0)

            assertEquals(3, model.objectsCount)
            volumes.forEachIndexed { index, volume -> assertEquals(volume, model.getObjectVolume(index), 0.01) }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun nativeBooleanDifferenceKeepsAnEnclosedCavityAndExpectedSolidVolume() {
        val modelFile = copyAsset("models/xyz_cube.stl", "boolean-contained-difference.stl")
        val model = Model(modelFile)
        try {
            val targetMin = Vec3d(); val targetMax = Vec3d()
            model.getBoundingBoxExact(0, targetMin, targetMax)
            val sourceVolume = model.getObjectVolume(0)
            model.addObject(model, 0)
            model.scale(1, 0.5, 0.5, 0.5)
            val toolMin = Vec3d(); val toolMax = Vec3d()
            model.getBoundingBoxExact(1, toolMin, toolMax)
            model.translate(
                1,
                (targetMin.x + targetMax.x - toolMin.x - toolMax.x) / 2.0,
                (targetMin.y + targetMax.y - toolMin.y - toolMax.y) / 2.0,
                (targetMin.z + targetMax.z - toolMin.z - toolMax.z) / 2.0,
            )

            val result = model.booleanObjects(0, 1, BooleanOperation.DIFFERENCE)

            assertEquals(0, result)
            assertEquals(1, model.objectsCount)
            assertEquals(0.875, model.getObjectVolume(result) / sourceVolume, 0.02)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun booleanDialogOffersUnionDifferenceAndIntersectionAsSeparateActions() {
        val selected = AtomicReference<Pair<BooleanOperation, Boolean>?>(null)
        compose.setContent {
            BeamComposeTheme {
                ModelBooleanDialog(
                    targetName = "Base object",
                    toolNames = listOf("Tool A", "Tool B"),
                    onDismiss = {},
                    onChoose = { operation, placeOnSurface -> selected.set(operation to placeOnSurface) },
                )
            }
        }

        compose.onNodeWithTag("model-boolean-target").assertIsDisplayed()
        compose.onNodeWithTag("model-boolean-tools").assertIsDisplayed()
        compose.onNodeWithText("Base object", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tool A", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tool B", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("model-boolean-union").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(BooleanOperation.UNION to false, selected.get()) }
        compose.onNodeWithTag("model-boolean-difference").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(BooleanOperation.DIFFERENCE to false, selected.get()) }
        compose.onNodeWithTag("model-boolean-intersection").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(BooleanOperation.INTERSECTION to false, selected.get()) }

        compose.onNodeWithTag("model-boolean-place-on-surface").performScrollTo().performClick()
        compose.onNodeWithTag("model-boolean-union").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(BooleanOperation.UNION to true, selected.get()) }
    }

    private fun assertBooleanResult(operation: BooleanOperation, expectedVolumeFactor: Double) {
        val modelFile = copyAsset("models/xyz_cube.stl", "boolean-${operation.name.lowercase()}.stl")
        val model = Model(modelFile)
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val sourceVolume = model.getObjectVolume(0)
            assertTrue("fixture cube must have finite positive volume", sourceVolume > 0.0)
            model.addObject(model, 0)
            model.setUniformFilament(0, 3)
            model.setUniformFilament(1, 4)
            model.translate(1, (max.x - min.x) / 2.0, 0.0, 0.0)

            val booleanStarted = System.nanoTime()
            val resultIndex = model.booleanObjects(0, 1, operation)
            val booleanMillis = (System.nanoTime() - booleanStarted) / 1_000_000
            Log.i("BooleanPerfTest", "${operation.name} two-cube boolean: ${booleanMillis}ms")

            assertEquals("boolean replaces both source objects only after success", 0, resultIndex)
            assertEquals(1, model.objectsCount)
            assertEquals("Boolean result must retain the target object's filament assignment",
                listOf(3), model.getUsedFilaments(resultIndex).toList())
            val resultVolume = model.getObjectVolume(resultIndex)
            assertEquals(expectedVolumeFactor, resultVolume / sourceVolume, 0.02)
            assertTrue("boolean output must be a non-empty printable mesh", model.getTriangleCount(resultIndex) > 0)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun invalidBooleanRequestLeavesBothObjectsUnchanged() {
        val modelFile = copyAsset("models/xyz_cube.stl", "boolean-invalid.stl")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.translate(1, 100.0, 0.0, 0.0)
            val firstVolume = model.getObjectVolume(0)
            val secondVolume = model.getObjectVolume(1)
            assertTrue(model.booleanObjects(0, 1, BooleanOperation.DIFFERENCE) < 0)
            assertTrue(model.booleanObjects(0, 1, BooleanOperation.INTERSECTION) < 0)
            assertTrue(model.booleanObjects(intArrayOf(0, 1, 1), BooleanOperation.UNION) < 0)
            assertEquals(2, model.objectsCount)
            assertEquals(firstVolume, model.getObjectVolume(0), 0.01)
            assertEquals(secondVolume, model.getObjectVolume(1), 0.01)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test fun standardM12BoltBuildsBoundedRaycastAndPaintDataWithoutCrash() {
        val values = FastenerFormLogic.defaults(size = FastenerStandards.count() - 1)
        assertEquals(null, FastenerFormLogic.validate(values))
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "m12-raycast-regression.stl")
        val generationStarted = System.nanoTime()
        val written = FastenerStlWriter.writeBolt(
            file,
            FastenerStandards.diameter(values.size),
            FastenerFormLogic.parse(values.second),
            FastenerFormLogic.parse(values.first),
            FastenerFormLogic.parse(values.fourth),
            FastenerFormLogic.parse(values.third),
        )
        val generationMillis = (System.nanoTime() - generationStarted) / 1_000_000
        val importStarted = System.nanoTime()
        val model = Model(file)
        val importMillis = (System.nanoTime() - importStarted) / 1_000_000
        val glModel = GLModel()
        try {
            assertTrue("generated STL should retain its screw triangles", model.getTriangleCount(0) > 0)
            assertEquals(written, model.getTriangleCount(0))
            assertTrue("generated M12 bolt should remain a printable solid with positive volume",
                model.getObjectVolume(0) > 0.0)
            val boltMin = Vec3d(); val boltMax = Vec3d()
            model.getBoundingBoxExact(0, boltMin, boltMax)
            val expectedHeight = FastenerFormLogic.parse(values.first) + FastenerFormLogic.parse(values.third)
            assertEquals("packed ring generation must preserve the bolt's designed total height",
                expectedHeight.toDouble(), boltMax.z - boltMin.z, 0.02)
            assertFalse(
                "the standard M12 mesh must fit the tested non-destructive surface-paint budget",
                LargeModelPick.needsPreciseSurfaceSimplification(model.getTriangleCount(0)),
            )
            assertTrue("the lightweight raycast tree should allow exact surface placement for M12",
                LargeModelPick.canUsePreciseSurfaceTools(model.getTriangleCount(0)))
            assertTrue("the M12 raycast tree must remain below its memory bound",
                !LargeModelPick.needsBoundsOnlyPicking(model.getTriangleCount(0)))
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val hit = LargeModelPick.hit(
                Vec3d((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, max.z + 10.0),
                Vec3d(0.0, 0.0, -1.0),
                min,
                max,
            )
            assertTrue("ray through the bolt bounds should select the generated model", hit != null)
            assertTrue("bounds selection must be marked approximate", hit!!.approximate)

            // Exercise the production picking entry point: build the exact indexed raycaster
            // without constructing AABBMesh's much heavier face-neighbour and vertex-face maps.
            glModel.initFrom(model, 0)
            val raycastIndexStarted = System.nanoTime()
            assertNotNull("dense M12 geometry should expose the lightweight exact raycaster", glModel.raycaster)
            val raycastIndexMillis = (System.nanoTime() - raycastIndexStarted) / 1_000_000
            val paintStarted = System.nanoTime()
            val paint = PaintSession(model, 0, GLRenderer.PAINT_MODE_COLOR)
            val paintInitMillis = (System.nanoTime() - paintStarted) / 1_000_000
            try {
                assertTrue("a standard M12 must enter paint without replacing its original triangles", paint.isValid())
            } finally {
                paint.end()
            }
            assertEquals("released native handles should reject raycasts safely", 0,
                Native.glmodel_raycast_closest_hit(0L, doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(0.0, 0.0, -1.0)).size)
            assertEquals("short origins must be rejected before native vector construction", 0,
                Native.glmodel_raycast_closest_hit(glModel.pointer, doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0, 0.0, -1.0)).size)
            assertEquals("zero-length directions must not produce invalid native hits", 0,
                Native.glmodel_raycast_closest_hit(glModel.pointer, doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(0.0, 0.0, 0.0)).size)
            assertEquals("non-finite origins must be rejected before entering the native tree", 0,
                Native.glmodel_raycast_closest_hit(
                    glModel.pointer,
                    doubleArrayOf(Double.NaN, 0.0, 1.0),
                    doubleArrayOf(0.0, 0.0, -1.0),
                ).size)
            assertEquals("non-finite directions must not produce native hits", 0,
                Native.glmodel_raycast_closest_hit(
                    glModel.pointer,
                    doubleArrayOf(0.0, 0.0, 1.0),
                    doubleArrayOf(0.0, Double.POSITIVE_INFINITY, -1.0),
                ).size)
            // Building the index alone does not prove that the GL touch path can use it.
            // Sample the generated head from above and require at least one precise hit
            // with a finite, normalized face normal.
            val sampleFractions = doubleArrayOf(0.2, 0.5, 0.8)
            var preciseHit: DoubleArray? = null
            for (xFraction in sampleFractions) {
                for (yFraction in sampleFractions) {
                    val point = doubleArrayOf(
                        boltMin.x + (boltMax.x - boltMin.x) * xFraction,
                        boltMin.y + (boltMax.y - boltMin.y) * yFraction,
                        boltMax.z + 1.0,
                    )
                    val hits = Native.glmodel_raycast_closest_hit(
                        glModel.pointer, point, doubleArrayOf(0.0, 0.0, -1.0),
                    )
                    if (hits.size == 6 && hits.all(Double::isFinite)) {
                        preciseHit = hits
                        break
                    }
                }
                if (preciseHit != null) break
            }
            assertNotNull("native M12 raycaster should return a precise surface hit", preciseHit)
            val nativeHit = requireNotNull(preciseHit)
            val normalLength = kotlin.math.sqrt(
                nativeHit[3] * nativeHit[3] + nativeHit[4] * nativeHit[4] + nativeHit[5] * nativeHit[5],
            )
            assertEquals("native raycast should return a unit face normal", 1.0, normalLength, 1e-5)
            assertTrue("a ray from above should hit the generated bolt below its top bound",
                nativeHit[2] <= boltMax.z)
            android.util.Log.i(
                "ModelEditingNativeDeviceTest",
                "M12 profile=${FastenerStandards.diameter(values.size)}mm facets=$written " +
                    "generateMs=$generationMillis importMs=$importMillis raycastIndexMs=$raycastIndexMillis " +
                    "paintInitMs=$paintInitMillis",
            )
        } finally {
            glModel.release()
            model.release()
            file.delete()
        }
    }

    @Test fun nativeRaycastCacheSurvivesConcurrentMeshReinitialization() {
        val modelFile = copyAsset("models/xyz_cube.stl", "raycast-concurrency-cube.stl")
        val model = Model(modelFile)
        val glModel = GLModel()
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val stop = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>()
        var reader: Thread? = null
        var writer: Thread? = null
        try {
            glModel.initFrom(model, 0)
            reader = Thread {
                try {
                    start.await()
                    repeat(60) {
                        if (stop.get()) return@Thread
                        Native.glmodel_init_raycast_data(glModel.pointer)
                        val hits = Native.glmodel_raycast_closest_hit(
                            glModel.pointer,
                            doubleArrayOf(10.0, 10.0, 30.0),
                            doubleArrayOf(0.0, 0.0, -1.0),
                        )
                        assertEquals("raycast result must contain complete position/normal tuples", 0, hits.size % 6)
                    }
                } catch (error: Throwable) {
                    failure.compareAndSet(null, error)
                } finally {
                    done.countDown()
                }
            }
            writer = Thread {
                try {
                    start.await()
                    repeat(30) { index ->
                        if (stop.get()) return@Thread
                        glModel.initFrom(model, 0)
                        if (index % 5 == 0) glModel.reset()
                    }
                } catch (error: Throwable) {
                    failure.compareAndSet(null, error)
                } finally {
                    done.countDown()
                }
            }
            reader.start()
            writer.start()
            start.countDown()
            assertTrue("concurrent raycast/cache rebuild should finish", done.await(30, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("concurrent raycast/cache rebuild failed", it) }
        } finally {
            stop.set(true)
            start.countDown()
            reader?.join(5_000)
            writer?.join(5_000)
            val workersStopped = reader?.isAlive != true && writer?.isAlive != true
            if (workersStopped) {
                glModel.release()
                model.release()
                modelFile.delete()
            } else {
                throw AssertionError("raycast stress workers did not stop; native models kept alive to avoid use-after-free")
            }
        }
    }

    private fun copyAsset(asset: String, name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        return File(context.cacheDir, name).also { file ->
            // Test-only fixtures live in the instrumentation APK; shared application assets live
            // in the target APK. Prefer the former, then fall back to the latter.
            val input = try {
                instrumentation.context.assets.open(asset)
            } catch (_: java.io.IOException) {
                context.assets.open(asset)
            }
            input.use { file.outputStream().use(it::copyTo) }
        }
    }

    /** Creates a true linked copy in the native model, rather than two 3MF build objects. */
    private fun createRepeatedInstanceModel(name: String): Model {
        val source = copyAsset("models/xyz_cube.stl", name)
        val model = try {
            Model(source)
        } finally {
            source.delete()
        }
        val min = Vec3d(); val max = Vec3d()
        model.getBoundingBoxExact(0, min, max)
        val offset = max.x - min.x + 9.0
        val instanceIndex = model.duplicateInstance(0, 0, Vec3d(offset, 0.0, 0.0))
        if (instanceIndex != 1) {
            model.release()
            throw AssertionError("native model should create a second linked instance")
        }
        return model
    }

    private fun createCubeFixture(name: String, side: Float): File {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        val vertices = arrayOf(
            floatArrayOf(0f, 0f, 0f), floatArrayOf(side, 0f, 0f),
            floatArrayOf(side, side, 0f), floatArrayOf(0f, side, 0f),
            floatArrayOf(0f, 0f, side), floatArrayOf(side, 0f, side),
            floatArrayOf(side, side, side), floatArrayOf(0f, side, side),
        )
        val triangles = arrayOf(
            intArrayOf(0, 2, 1), intArrayOf(0, 3, 2),
            intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),
            intArrayOf(0, 1, 5), intArrayOf(0, 5, 4),
            intArrayOf(3, 7, 6), intArrayOf(3, 6, 2),
            intArrayOf(0, 4, 7), intArrayOf(0, 7, 3),
            intArrayOf(1, 2, 6), intArrayOf(1, 6, 5),
        )
        val stl = ByteBuffer.allocate(84 + triangles.size * 50).order(ByteOrder.LITTLE_ENDIAN)
        stl.position(80)
        stl.putInt(triangles.size)
        triangles.forEach { triangle ->
            repeat(3) { stl.putFloat(0f) }
            triangle.forEach { vertex -> vertices[vertex].forEach { stl.putFloat(it) } }
            stl.putShort(0)
        }
        file.writeBytes(stl.array())
        return file
    }

    private fun createCylinderFixture(name: String, radius: Float, height: Float, segments: Int): File {
        val triangles = ArrayList<Array<FloatArray>>(segments * 4)
        fun point(angle: Double, z: Float) = floatArrayOf(
            radius * kotlin.math.cos(angle).toFloat(),
            radius * kotlin.math.sin(angle).toFloat(),
            z,
        )
        val bottomCenter = floatArrayOf(0f, 0f, 0f)
        val topCenter = floatArrayOf(0f, 0f, height)
        for (index in 0 until segments) {
            val a0 = 2.0 * Math.PI * index / segments
            val a1 = 2.0 * Math.PI * (index + 1) / segments
            val bottom0 = point(a0, 0f); val bottom1 = point(a1, 0f)
            val top0 = point(a0, height); val top1 = point(a1, height)
            triangles += arrayOf(bottom0, bottom1, top1)
            triangles += arrayOf(bottom0, top1, top0)
            triangles += arrayOf(bottomCenter, bottom1, bottom0)
            triangles += arrayOf(topCenter, top0, top1)
        }
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        val stl = ByteBuffer.allocate(84 + triangles.size * 50).order(ByteOrder.LITTLE_ENDIAN)
        stl.position(80)
        stl.putInt(triangles.size)
        triangles.forEach { triangle ->
            repeat(3) { stl.putFloat(0f) }
            triangle.forEach { vertex -> vertex.forEach(stl::putFloat) }
            stl.putShort(0)
        }
        file.writeBytes(stl.array())
        return file
    }
}
