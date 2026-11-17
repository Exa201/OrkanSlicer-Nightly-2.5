package ru.ytkab0bp.slicebeam.components.bed_menu

import androidx.annotation.DrawableRes
import ru.ytkab0bp.slicebeam.R

/** High contrast, multicolor artwork supplied for the workspace action tiles. */
@DrawableRes
internal fun menuActionArtwork(titleRes: Int): Int? = when (titleRes) {
    R.string.WorkspaceObjects -> R.drawable.ui21_objects
    R.string.ModelMenuDuplicate -> R.drawable.ui21_duplicate
    R.string.ModelMenuMoveToPlate -> R.drawable.ui21_move_plate
    R.string.ModelMenuSplit -> R.drawable.ui21_split_parts
    R.string.ModelMenuModifiers -> R.drawable.ui21_modifier_volume
    R.string.ModelMenuObjectSettings -> R.drawable.ui21_object_settings
    R.string.ModelMenuCenterOnBed -> R.drawable.ui21_center
    R.string.ModelScaleToBuildVolume -> R.drawable.ui21_fit_volume
    R.string.ModelMenuPlaceGroupOnBed -> R.drawable.ui21_place_plate
    R.string.ModelMenuPlaceOnObject -> R.drawable.ui21_place_object
    R.string.ModelMenuPlaceOnSurface -> R.drawable.ui21_place_surface
    R.string.ModelMenuAlignSelected -> R.drawable.ui21_align_all
    R.string.ModelMenuAlignPrimaryAxes -> R.drawable.ui21_align_axes
    R.string.ModelMenuDistributeSelected -> R.drawable.ui21_align_all
    R.string.ModelMenuCombineSelected -> R.drawable.ui21_combine_parts
    R.string.ModelMenuBoolean -> R.drawable.ui21_boolean
    R.string.ModelMenuFillBed -> R.drawable.ui21_fill_bed
    R.string.ModelMenuArrange, R.string.MenuOrientationArrange,
    R.string.MenuArrangeAllObjects -> R.drawable.ui21_arrange_all
    R.string.ModelMenuCreatePrimitive -> R.drawable.ui21_primitives
    R.string.ModelMenuOrientAll, R.string.MenuOrientAllObjects,
    R.string.MenuOrientationAutoOrient -> R.drawable.ui21_auto_orient
    R.string.ModelMenuAutoOrientSelected -> R.drawable.ui21_auto_orient
    R.string.ModelMenuFlatten, R.string.MenuOrientationFlatten -> R.drawable.ui21_flatten
    R.string.ModelMenuDeleteSelected -> R.drawable.ui21_delete
    R.string.ModelMenuSelectAll -> R.drawable.ui21_select_all
    R.string.ModelMenuDeleteAll -> R.drawable.ui21_delete_all

    R.string.ModelContextResetRotation -> R.drawable.ui21_reset_rotation
    R.string.ModelContextResetScale -> R.drawable.ui21_reset_scale
    R.string.ModelUndo -> R.drawable.ui21_undo
    R.string.ModelRedo -> R.drawable.ui21_redo
    R.string.MenuToolbarMove, R.string.MenuOrientationPosition -> R.drawable.ui21_move
    R.string.MenuToolbarRotate, R.string.MenuOrientationRotation -> R.drawable.ui21_rotate
    R.string.MenuTransformScale -> R.drawable.ui21_scale
    R.string.ModelSimplify -> R.drawable.ui21_simplify
    R.string.ModelSmoothMesh -> R.drawable.ui21_smooth_mesh
    R.string.MenuTransformCut -> R.drawable.ui21_cut
    R.string.MenuToolbarVariableLayerHeight -> R.drawable.ui21_variable_layer_height
    R.string.MenuToolbarSupportPaint -> R.drawable.ui21_support_paint
    R.string.MenuToolbarSeamPaint -> R.drawable.ui21_seam_paint
    R.string.MenuToolbarFuzzyPaint -> R.drawable.ui21_fuzzy_paint
    R.string.MenuToolbarColorPaint -> R.drawable.ui21_color_paint
    R.string.MenuToolbarEmboss -> R.drawable.ui21_emboss

    R.string.WorkspaceAddModelAction, R.string.MenuFileOpen -> R.drawable.ui21_open_model
    R.string.RecentModelsTitle, R.string.MenuSliceInfo -> R.drawable.ui21_recent
    R.string.MenuFileExport3mf, R.string.MenuSliceExportToFile -> R.drawable.ui21_export_3mf
    R.string.MenuFileCalibrations, R.string.MenuToolbarMeasure -> R.drawable.ui21_calibration
    R.string.MenuFileImportProfiles -> R.drawable.ui21_profile_import
    R.string.MenuFileExportProfiles -> R.drawable.ui21_profile_export
    R.string.MenuSliceLayers -> R.drawable.ui21_slice
    R.string.MenuSliceShare -> R.drawable.ui21_file
    R.string.MenuSliceSendToPrinter, R.string.MenuSliceSendToPrinterAndPrint -> R.drawable.ui21_slice
    R.string.QuickSliceTitle -> R.drawable.ui21_quick_slice
    R.string.SmartSliceTitle -> R.drawable.ui21_smart_slice
    R.string.MenuSliceCurrentSettings -> R.drawable.ui21_slice_plate
    else -> null
}
