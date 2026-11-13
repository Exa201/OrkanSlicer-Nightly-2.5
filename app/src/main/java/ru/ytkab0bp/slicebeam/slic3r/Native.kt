package ru.ytkab0bp.slicebeam.slic3r

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.ytkab0bp.slicebeam.SliceBeam
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/** JNI entry points and Orca profile-bundle helpers. Keep native names stable for beam_native.cpp. */
class Native {
    companion object {
        init {
            System.loadLibrary("c++_shared")
            System.loadLibrary("gmp")
            System.loadLibrary("gmpxx")
            System.loadLibrary("mpfr")
            OCCTLoader.load()
            System.loadLibrary("slic3r")
            set_svg_path_prefix(SliceBeam.INSTANCE!!.cacheDir.absolutePath)
        }

    @JvmStatic
    external fun get_print_config_def(def: PrintConfigDef): Unit
    /** Exercises the native Auto For Match solver against four synthetic two-slot palettes. */
    @JvmStatic
    external fun filament_group_match_regression(): IntArray
    /** Verifies flush grouping may trade one transition to keep both matching loaded filaments together. */
    @JvmStatic
    external fun filament_group_flush_candidate_regression(): IntArray
    @JvmStatic
    external fun scarf_ramp_regression(): DoubleArray
    @JvmStatic
    external fun bed_exclude_volumes_regression(): DoubleArray
    @JvmStatic
    external fun bed_exclude_area_validation_regression(): IntArray
    /** Handles overlapping, touching, separated, and unrestricted extruder printable areas safely. */
    @JvmStatic
    external fun bed_shared_extruder_area_regression(): IntArray
    @JvmStatic
    external fun bed_exclude_arrangement_regression(): DoubleArray
    /** Verifies Orca sequential placement keeps short objects inside the bed and clear. */
    @JvmStatic
    external fun short_object_arrangement_regression(): DoubleArray
    /** Verifies an almost bed-filling object is not rejected by the default arrange margin. */
    @JvmStatic
    external fun near_bed_size_arrangement_regression(): DoubleArray
    /** Exercises status-callback replacement from callbacks and concurrent slice progress. */
    @JvmStatic
    external fun print_status_callback_regression(): IntArray
    /** Verifies preset transfers can create or clear an empty serialized post-processing list safely. */
    @JvmStatic
    external fun config_apply_empty_serialized_list_regression(): IntArray
    /** Checks per-filament and physical-extruder placeholder vectors under a shared filament map. */
    @JvmStatic
    external fun placeholder_filament_map_regression(): Array<String>
    /** Reapplies one native Print from a single-filament model to a two-filament model. */
    @JvmStatic
    external fun reused_print_prime_tower_regression(firstModel: Long, nextModel: Long, configPath: String): IntArray
    @JvmStatic
    external fun gcoderesult_get_spiral_vase_override_warning(ptr: Long): String?
    @JvmStatic
    external fun gcoderesult_get_seam_marker_count(ptr: Long): Int

    @JvmStatic
    external fun gcoderesult_get_initial_layer_time(ptr: Long): Float
    /** Verifies by-layer/as-object-list ordering preserves rotated copies' model-list order. */
    @JvmStatic
    external fun by_layer_copy_order_regression(model: Long, configPath: String): IntArray
    /** Verifies by-object clearance ordering preserves rotated copies' model-list order. */
    @JvmStatic
    external fun by_object_copy_order_regression(model: Long, configPath: String): IntArray
    /** Reads layer-height limits for first and third logical filaments through production slicing parameters. */
    @JvmStatic
    external fun object_nozzle_layer_limits_regression(): DoubleArray
    /** Exercises height-range Z shrinkage and cached-region invalidation through native Print::process. */
    @JvmStatic
    external fun z_shrinkage_height_range_regression(model: Long, configPath: String, firstPercent: Double, nextPercent: Double): DoubleArray
    /** Adds an object-local layer-height/speed range for native slice regression tests. */
    @JvmStatic
    external fun model_add_layer_range_test_config(model: Long, minZ: Double, maxZ: Double, layerHeight: Double, outerWallSpeed: Double): Boolean
    /** Ensures copied objects with different layer-range configs don't share sliced regions. */
    @JvmStatic
    external fun layer_range_cache_isolation_regression(model: Long, configPath: String): IntArray
    /** Verifies matching transforms with different center offsets don't share shifted slice data. */
    @JvmStatic
    external fun center_offset_slice_cache_regression(configPath: String): IntArray
    /** Verifies sequential-print rod checks include raft height above the bed. */
    @JvmStatic
    external fun sequential_raft_top_clearance_regression(): IntArray
    /** Runs the Orca painted-bridge fixture and reports neighbour anchors and bridge paths. */
    @JvmStatic
    external fun painted_bridge_anchor_regression(): IntArray
    /** Verifies printable-area masks retain the 64th extruder in the post-slice check. */
    @JvmStatic
    external fun gcode_extruder_64_area_mask_regression(): IntArray
    @JvmStatic
    external fun model_add_spiral_vase_override_test_config(model: Long): Boolean
    /** Exercises the inherited custom-printer preset merge when the child adds nozzle slots. */
    @JvmStatic
    external fun preset_parent_nozzle_extension_regression(): DoubleArray
    /** Verifies shared-nozzle multi-material presets stay at one after filament deletion. */
    @JvmStatic
    external fun shared_nozzle_filament_count_regression(): IntArray
    /** Verifies AMS sync keeps an inherited user filament override while updating tray color. */
    @JvmStatic
    external fun ams_sync_preserves_inherited_filament_regression(): IntArray
    /** Loads a short-filament multi-tool project, checks paired arrays, then overwrites all tray slots. */
    @JvmStatic
    external fun short_project_filament_arrays_regression(): IntArray
    /** Exercises production short-path chaining against a four-segment optimality fixture. */
    @JvmStatic
    external fun shortest_path_exact_chain_regression(): IntArray
    @JvmStatic
    external fun shortest_path_long_chain_refinement_regression(): IntArray
    /** Exercises exact default object-tour ordering against a six-object fixture. */
    @JvmStatic
    external fun shortest_path_exact_object_tour_regression(): IntArray
    /** Exercises sparse-infill line joining, loop avoidance, and crossing rejection. */
    @JvmStatic
    external fun joined_infill_regression(): IntArray
    /** Compares grouped dense-polygon union with a single Clipper union within sub-micron tolerance. */
    @JvmStatic
    external fun tree_support_grouped_union_regression(): IntArray
    /** Exercises malformed and valid custom layer-sequence parsing through the production helper. */
    @JvmStatic
    external fun other_layers_print_sequence_regression_probe(): IntArray
    /** Checks that solid Spiral Inset keeps the Arachne wall paths used by top surfaces. */
    @JvmStatic
    external fun spiral_inset_solid_regression(): IntArray
    @JvmStatic
    external fun cut_preserves_part_filaments_regression(): IntArray
    external fun layer_range_cache_isolation_regression(): IntArray
    /** Verifies that opened Lightning overhang samples are included in the update-grid bounds. */
    @JvmStatic
    external fun lightning_distance_field_bounds_probe(): IntArray
    @JvmStatic
    external fun tpms_ring_endpoints_probe(): IntArray
    @JvmStatic
    external fun multiline_infill_ring_endpoints_probe(): IntArray
    /** Regression probe for CGAL conversion of meshes with isolated vertex indices. */
    @JvmStatic
    external fun mesh_boolean_isolated_vertices_probe(): DoubleArray
    /** Verifies changing the filament count does not truncate per-nozzle variant values. */
    @JvmStatic
    external fun filament_variant_resize_regression_probe(): IntArray
    /** Checks the production gate for the optional curled-extrusion estimation pass. */
    @JvmStatic
    external fun curled_extrusion_estimation_policy_probe(): IntArray
    /** Confirms painted facet states follow filament count even if the color palette is shorter. */
    @JvmStatic
    external fun painted_segmentation_state_count_probe(filamentCount: Int, colorCount: Int): IntArray
    /** Bakes a generated grayscale PNG onto a synthetic mesh and verifies that cancellation returns no committable mesh. */
    @JvmStatic
    external fun texture_displacement_bake_regression(png: ByteArray): DoubleArray
    @JvmStatic
    external fun texture_displacement_roundtrip_attach(model: Long, png: ByteArray): Boolean
    @JvmStatic
    external fun texture_displacement_roundtrip_inspect(model: Long, volumeIndex: Int): IntArray
    @JvmStatic
    external fun texture_displacement_roundtrip_clear(model: Long, volumeIndex: Int): Boolean
    @JvmStatic
    external fun set_svg_path_prefix(prefix: String): Unit
    @JvmStatic
    external fun shader_init_from_texts(name: String, fragment: String, vertex: String): Long
    @JvmStatic
    external fun shader_get_id(ptr: Long): Int
    @JvmStatic
    external fun shader_get_uniform_location(ptr: Long, name: String): Int
    @JvmStatic
    external fun shader_get_attrib_location(ptr: Long, name: String): Int
    @JvmStatic
    external fun shader_start_using(ptr: Long): Unit
    @JvmStatic
    external fun shader_stop_using(ptr: Long): Unit
    @JvmStatic
    external fun shader_release(ptr: Long): Unit
    @JvmStatic
    external fun glmodel_create(): Long
    @JvmStatic
    external fun glmodel_init_from_model(ptr: Long, model: Long): Unit
    @JvmStatic
    external fun glmodel_init_from_model_object(ptr: Long, model: Long, i: Int): Unit
    @JvmStatic
    external fun glmodel_init_from_model_object_volume(ptr: Long, model: Long, objectIndex: Int, volumeIndex: Int): Unit
    @JvmStatic
    external fun glmodel_init_from_model_object_preview(ptr: Long, model: Long, i: Int, maxTriangles: Int): Unit
    @JvmStatic
    external fun glmodel_init_raycast_data(ptr: Long): Boolean
    @JvmStatic
    external fun glmodel_set_color(ptr: Long, red: Float, green: Float, blue: Float, alpha: Float): Unit
    @JvmStatic
    external fun glmodel_render(ptr: Long): Unit
    @JvmStatic
    external fun glmodel_stilized_arrow(ptr: Long, tipRadius: Float, tipLength: Float, stemRadius: Float, stemLength: Float): Unit
    @JvmStatic
    external fun glmodel_init_background_triangles(ptr: Long): Unit
    @JvmStatic
    external fun glmodel_init_box(ptr: Long, width: Float, depth: Float, height: Float): Unit
    @JvmStatic
    external fun glmodel_init_bounding_box(ptr: Long, modelPtr: Long, i: Int): Unit

    @JvmStatic
    external fun glmodel_get_bounding_box(ptr: Long): DoubleArray
    @JvmStatic
    external fun glmodel_is_initialized(ptr: Long): Boolean
    @JvmStatic
    external fun glmodel_is_empty(ptr: Long): Boolean
    @JvmStatic
    external fun glmodel_raycast_closest_hit(ptr: Long, point: DoubleArray, direction: DoubleArray): DoubleArray
    @JvmStatic
    external fun glmodel_reset(ptr: Long): Unit
    @JvmStatic
    external fun glmodel_release(ptr: Long): Unit
    @JvmStatic
    external fun bed_create(data: LongArray): Long
    @JvmStatic
    external fun bed_get_bounding_volume_max_size(ptr: Long): Int
    @JvmStatic
    external fun bed_get_bounding_volume(ptr: Long): DoubleArray
    @JvmStatic
    external fun bed_is_rectangular_build_volume(ptr: Long): Boolean
    @JvmStatic
    external fun bed_is_circular_build_volume(ptr: Long): Boolean
    @JvmStatic
    external fun bed_get_circular_build_volume(ptr: Long): DoubleArray?
    @JvmStatic
    external fun bed_configure(ptr: Long, configPath: String): Unit
    @JvmStatic
    external fun bed_init_triangles_mesh(ptr: Long, triangles: Long): Unit
    @JvmStatic
    external fun bed_arrange(ptr: Long, modelPtr: Long, arrangeConfig: String): Boolean
    @JvmStatic
    external fun bed_arrange_with_distance(ptr: Long, modelPtr: Long, arrangeConfig: String, minDistanceMm: Double): Boolean
    @JvmStatic
    external fun bed_arrange_object_with_distance(ptr: Long, modelPtr: Long, objectIndex: Int, arrangeConfig: String, minDistanceMm: Double): Boolean
    @JvmStatic
    external fun bed_release(ptr: Long): Unit
    @JvmStatic
    external fun models_merge(vararg ptrs: Long): Long
    @JvmStatic
    external fun model_create(): Long
    @JvmStatic
    external fun model_replace_contents(ptr: Long, fromPtr: Long): Unit
    @JvmStatic
    @Throws(Slic3rRuntimeError::class)
    external fun model_read_from_file(
        path: String,
        baseName: String,
        plateId: Int,
        progress: ImportProgressCallback?,
        stepLinearDeflection: Double,
        stepAngularDeflection: Double,
        splitStepCompound: Boolean,
    ): Long
    @JvmStatic
    external fun model_get_objects_count(ptr: Long): Int
    @JvmStatic
    external fun model_get_object_name(ptr: Long, i: Int): String
    @JvmStatic
    external fun model_get_object_part_count(ptr: Long, i: Int): Int
    @JvmStatic
    external fun model_get_object_part_name(ptr: Long, objectIndex: Int, partIndex: Int): String?
    @JvmStatic
    external fun model_get_object_config_option(ptr: Long, objectIndex: Int, key: String): String?
    @JvmStatic
    external fun model_set_object_config_options(ptr: Long, objectIndex: Int, keys: Array<String>, values: Array<String>): Boolean
    @JvmStatic
    external fun model_get_object_part_config_option(ptr: Long, objectIndex: Int, partIndex: Int, key: String): String?
    @JvmStatic
    external fun model_set_object_part_config_options(ptr: Long, objectIndex: Int, partIndex: Int, keys: Array<String>, values: Array<String>): Boolean
    @JvmStatic
    external fun model_add_modifier_box(ptr: Long, objectIndex: Int, type: Int, x: Double, y: Double, z: Double, width: Double, depth: Double, height: Double): Int
    @JvmStatic
    external fun model_get_modifier_volume_indices(ptr: Long, objectIndex: Int): IntArray
    @JvmStatic
    external fun model_get_modifier_volume_box_state(ptr: Long, objectIndex: Int, volumeIndex: Int): DoubleArray
    @JvmStatic
    external fun model_update_modifier_box(ptr: Long, objectIndex: Int, volumeIndex: Int, x: Double, y: Double, z: Double, width: Double, depth: Double, height: Double): Boolean
    @JvmStatic
    external fun model_get_volume_type(ptr: Long, objectIndex: Int, volumeIndex: Int): Int
    @JvmStatic
    external fun model_delete_volume(ptr: Long, objectIndex: Int, volumeIndex: Int): Boolean
    @JvmStatic
    external fun model_get_object_part_extruder(ptr: Long, objectIndex: Int, partIndex: Int): Int
    @JvmStatic
    external fun model_transform_object_part(
        ptr: Long,
        objectIndex: Int,
        partIndex: Int,
        dx: Double,
        dy: Double,
        dz: Double,
        rotationXDegrees: Double,
        rotationYDegrees: Double,
        rotationZDegrees: Double,
        scaleX: Double,
        scaleY: Double,
        scaleZ: Double,
    ): Boolean
    @JvmStatic
    external fun model_get_triangle_count(ptr: Long, i: Int): Int
    @JvmStatic
    external fun model_simplify(ptr: Long, i: Int, keepRatio: Float): Int
    @JvmStatic
    external fun model_smooth_mesh(ptr: Long, i: Int): Int
    @JvmStatic
    external fun model_add_object_from_another(ptr: Long, from: Long, i: Int): Unit
    @JvmStatic
    external fun model_duplicate_instance(ptr: Long, objectIndex: Int, sourceInstanceIndex: Int, dx: Double, dy: Double, dz: Double): Int
    @JvmStatic
    external fun model_delete_object(ptr: Long, i: Int): Unit
    @JvmStatic
    external fun model_split(ptr: Long, i: Int): Int
    @JvmStatic
    external fun model_get_translation(ptr: Long, objectIndex: Int): DoubleArray
    @JvmStatic
    external fun model_get_scale(ptr: Long, objectIndex: Int): DoubleArray
    @JvmStatic
    external fun model_get_mirror(ptr: Long, objectIndex: Int): DoubleArray
    @JvmStatic
    external fun model_mirror(ptr: Long, objectIndex: Int, axis: Int): Unit
    @JvmStatic
    external fun model_get_rotation(ptr: Long, objectIndex: Int): DoubleArray
    @JvmStatic
    external fun model_get_bounding_box_exact(ptr: Long, i: Int): DoubleArray
    @JvmStatic
    external fun model_get_minimum_projection(ptr: Long, objectIndices: IntArray, direction: DoubleArray): Double
    @JvmStatic
    external fun model_get_projection_range(ptr: Long, objectIndices: IntArray, direction: DoubleArray): DoubleArray
    @JvmStatic
    external fun model_get_bounding_box_approx(ptr: Long, i: Int): DoubleArray
    @JvmStatic
    external fun model_get_bounding_box_exact_global(ptr: Long): DoubleArray
    @JvmStatic
    external fun model_get_bounding_box_approx_global(ptr: Long): DoubleArray
    @JvmStatic
    external fun model_is_left_handed(ptr: Long, i: Int): Boolean
    @JvmStatic
    external fun model_translate(ptr: Long, i: Int, x: Double, y: Double, z: Double): Unit
    @JvmStatic
    external fun model_translate_global(ptr: Long, x: Double, y: Double, z: Double): Unit
    @JvmStatic
    external fun model_ensure_on_bed(ptr: Long, i: Int): Unit
    @JvmStatic
    external fun model_scale(ptr: Long, i: Int, x: Double, y: Double, z: Double): Unit
    @JvmStatic
    external fun model_rotate(ptr: Long, i: Int, x: Double, y: Double, z: Double): Unit
    @JvmStatic
    external fun model_reset_rotation(ptr: Long, i: Int): Unit
    @JvmStatic
    external fun model_flatten_rotate(ptr: Long, i: Int, surfacePtr: Long): Unit
    @JvmStatic
    external fun model_create_flatten_planes(ptr: Long, i: Int): LongArray
    @JvmStatic
    external fun model_cut(ptr: Long, i: Int, zHeight: Double, rotX: Double, rotY: Double, keepUpper: Boolean, keepLower: Boolean): Boolean
    @JvmStatic
    external fun model_add_connector(ptr: Long, objIdx: Int, x: Double, y: Double, z: Double, radius: Float, height: Float, type: Int): Unit
    @JvmStatic
    external fun model_add_connector_on_plane(ptr: Long, objIdx: Int, x: Double, y: Double, z: Double, radius: Float, height: Float, type: Int, rotX: Double, rotY: Double): Unit
    @JvmStatic
    external fun model_remove_connector(ptr: Long, objIdx: Int, connIdx: Int): Unit
    @JvmStatic
    external fun model_clear_connectors(ptr: Long, objIdx: Int): Unit
    @JvmStatic
    external fun model_get_cut_connector_count(ptr: Long, objIdx: Int): Int
    @JvmStatic
    external fun model_auto_orient(ptr: Long, i: Int): Unit
    @JvmStatic
    external fun model_is_big_object(ptr: Long, i: Int): Boolean
    @JvmStatic
    external fun model_get_extruder(ptr: Long, i: Int): Int
    @JvmStatic
    external fun model_get_volume_extruder(ptr: Long, objectIndex: Int, volumeIndex: Int): Int
    @JvmStatic
    external fun model_set_object_part_extruder(ptr: Long, objectIndex: Int, partIndex: Int, extruder: Int): Boolean
    @JvmStatic
    external fun model_set_extruder(ptr: Long, i: Int, extruder: Int): Unit
    @JvmStatic
    external fun model_get_used_filaments(ptr: Long, i: Int): IntArray
    @JvmStatic
    external fun model_set_uniform_filament(ptr: Long, i: Int, filament: Int): Unit
    @JvmStatic
    external fun model_remap_filaments(ptr: Long, sourceFilaments: IntArray, targetFilaments: IntArray): Unit
    @JvmStatic
    external fun model_apply_adaptive_layer_height(ptr: Long, i: Int, configPath: String, qualityFactor: Float): Unit
    @JvmStatic
    external fun model_clear_adaptive_layer_height(ptr: Long, i: Int): Unit
    @JvmStatic
    external fun model_has_adaptive_layer_height(ptr: Long, i: Int): Boolean
    @JvmStatic
    external fun model_emboss_text(ptr: Long, i: Int, fontPath: String, text: String, size: Float, depth: Float, type: Int, px: Double, py: Double, pz: Double, nx: Double, ny: Double, nz: Double): Unit
    @JvmStatic
    external fun model_emboss_svg(ptr: Long, objectIndex: Int, svgPath: String, width: Float, depth: Float, operation: Int, px: Double, py: Double, pz: Double, nx: Double, ny: Double, nz: Double): Unit
    @JvmStatic
    external fun model_create_text_object(ptr: Long, fontPath: String, text: String, size: Float, depth: Float, px: Double, py: Double, pz: Double, nx: Double, ny: Double, nz: Double, targetObjectIndex: Int): Int
    @JvmStatic
    external fun model_create_primitive(ptr: Long, type: Int, a: Double, b: Double, c: Double, name: String): Int
    @JvmStatic
    external fun model_combine_objects_as_parts(ptr: Long, objectIndices: IntArray): Int
    /** op: 0=union, 1=target minus tool, 2=intersection. Negative values mean no mutation was applied. */
    @JvmStatic
    external fun model_boolean_objects(ptr: Long, objectIndices: IntArray, op: Int): Int
    @JvmStatic
    external fun model_get_object_volume(ptr: Long, objectIndex: Int): Double
    @JvmStatic
    external fun paint_begin(modelPtr: Long, objIdx: Int, mode: Int): Long
    @JvmStatic
    external fun paint_raycast(sessionPtr: Long, origin: DoubleArray, dir: DoubleArray): DoubleArray
    @JvmStatic
    external fun paint_brush(sessionPtr: Long, hit: DoubleArray, facetStart: Int, radius: Double, filamentIdx: Int, cameraPos: DoubleArray, sphere: Boolean, overhangAngle: Double): Unit
    @JvmStatic
    external fun paint_bucket(sessionPtr: Long, hit: DoubleArray, facetStart: Int, filamentIdx: Int, propagate: Boolean, angle: Double, overhangAngle: Double): Unit
    @JvmStatic
    external fun paint_height(sessionPtr: Long, zMin: Double, zMax: Double, filamentIdx: Int, overhangAngle: Double): Unit
    @JvmStatic
    external fun paint_overhang_boundary_probe(): IntArray
    @JvmStatic
    external fun paint_begin_stroke(sessionPtr: Long): Unit
    @JvmStatic
    external fun paint_end_stroke(sessionPtr: Long): Unit
    @JvmStatic
    external fun paint_can_undo(sessionPtr: Long): Boolean
    @JvmStatic
    external fun paint_can_redo(sessionPtr: Long): Boolean
    @JvmStatic
    external fun paint_undo_stroke(sessionPtr: Long): Boolean
    @JvmStatic
    external fun paint_redo_stroke(sessionPtr: Long): Boolean
    @JvmStatic
    external fun paint_reset_history(sessionPtr: Long): Unit
    @JvmStatic
    external fun paint_clear(sessionPtr: Long): Unit
    @JvmStatic
    external fun paint_get_mesh(sessionPtr: Long, filamentIdx: Int): FloatArray
    @JvmStatic
    external fun paint_commit(sessionPtr: Long): Unit
    @JvmStatic
    external fun glmodel_init_from_paint(glPtr: Long, sessionPtr: Long, filamentIdx: Int, baseFilamentPreview: Int): Int
    @JvmStatic
    external fun paint_build_live_overlays(sessionPtr: Long, glModelPtrs: LongArray, baseFilamentPreview: Int): IntArray
    @JvmStatic
    external fun paint_end(sessionPtr: Long): Unit
    @JvmStatic
    external fun model_build_paint_overlay(modelPtr: Long, objIdx: Int, glPtr: Long, filamentIdx: Int): Int
    @JvmStatic
    external fun model_has_paint(modelPtr: Long, objIdx: Int): Boolean
    @JvmStatic
    external fun model_paint_max_filament(modelPtr: Long, objIdx: Int): Int
    @JvmStatic
    @Throws(Slic3rRuntimeError::class)
    external fun model_slice(ptr: Long, configPath: String, path: String, listener: SliceListener?, numFilaments: Int, filamentColors: IntArray?, calibMode: Int, calibStart: Double, calibEnd: Double, calibStep: Double): Long
    @JvmStatic
    @Throws(Slic3rRuntimeError::class)
    external fun model_export_3mf(ptr: Long, configPath: String, path: String): Unit
    @JvmStatic
    external fun model_release(ptr: Long): Unit
    @JvmStatic
    external fun gcoderesult_load_file(path: String, name: String): Long
    @JvmStatic
    external fun gcoderesult_get_recommended_name(ptr: Long): String
    @JvmStatic
    external fun gcoderesult_get_gcode_check_error_code(ptr: Long): Int
    @JvmStatic
    external fun gcoderesult_get_used_filament_mm(ptr: Long, role: Int): Double
    @JvmStatic
    external fun gcoderesult_get_used_filament_g(ptr: Long, role: Int): Double
    @JvmStatic
    external fun gcoderesult_release(ptr: Long): Unit
    @JvmStatic
    external fun vgcode_create(): Long
    @JvmStatic
    external fun vgcode_init(ptr: Long): Unit
    @JvmStatic
    external fun vgcode_is_initialized(ptr: Long): Boolean
    @JvmStatic
    external fun vgcode_set_colors(ptr: Long, colors: IntArray): Unit
    @JvmStatic
    external fun vgcode_get_layers_count(ptr: Long): Long
    @JvmStatic
    external fun vgcode_load(ptr: Long, resultPtr: Long): Unit
    @JvmStatic
    external fun vgcode_render(ptr: Long, viewMatrix: FloatArray, projectionMatrix: FloatArray): Unit
    @JvmStatic
    external fun vgcode_set_layers_view_range(ptr: Long, min: Long, max: Long): Unit
    @JvmStatic
    external fun vgcode_set_infill_visibility_depth(ptr: Long, depth: Int): Unit
    @JvmStatic
    external fun vgcode_set_fast_mode(ptr: Long, fastMode: Boolean): Unit
    @JvmStatic
    external fun vgcode_set_selected_object_id(ptr: Long, id: Int): Unit
    @JvmStatic
    external fun vgcode_get_layers_view_range(ptr: Long): LongArray
    @JvmStatic
    external fun vgcode_get_estimated_time(ptr: Long): Float
    @JvmStatic
    external fun vgcode_get_estimated_time_role(ptr: Long, role: Int): Float
    @JvmStatic
    external fun vgcode_is_extrusion_role_visible(ptr: Long, role: Int): Boolean
    @JvmStatic
    external fun vgcode_toggle_extrusion_role_visibility(ptr: Long, role: Int): Unit
    @JvmStatic
    external fun vgcode_set_view_type(ptr: Long, type: Int): Unit
    @JvmStatic
    external fun vgcode_get_view_type(ptr: Long): Int
    @JvmStatic
    external fun vgcode_set_tool_colors(ptr: Long, colors: IntArray): Unit
    /** Preview-only packed 0xRRGGBBAA palette. Native slicing still consumes RGB config. */
    @JvmStatic
    external fun vgcode_set_tool_colors_rgba(ptr: Long, colors: IntArray): Unit
    @JvmStatic
    external fun vgcode_is_option_visible(ptr: Long, optionType: Int): Boolean
    @JvmStatic
    external fun vgcode_toggle_option_visibility(ptr: Long, optionType: Int): Unit
    @JvmStatic
    external fun vgcode_reset(ptr: Long): Unit
    @JvmStatic
    external fun vgcode_release(ptr: Long): Unit
    @JvmStatic
    external fun utils_config_create(config: String?): Long
    @JvmStatic
    external fun utils_config_check_compatibility(ptr: Long, condition: String): Boolean
    @JvmStatic
    @Throws(Slic3rRuntimeError::class)
    external fun utils_config_eval(ptr: Long, condition: String?): String?
    @JvmStatic
    external fun utils_config_release(ptr: Long): Unit
    @JvmStatic
    external fun utils_calc_view_normal_matrix(viewMatrix: DoubleArray, worldMatrix: DoubleArray, normalMatrix: DoubleArray): Unit
    @JvmStatic
    external fun utils_unproject(viewMatrix: DoubleArray, projectionMatrix: DoubleArray, screenWidth: Int, screenHeight: Int, x: Double, y: Double): DoubleArray

    @JvmStatic
    @Throws(IOException::class, JSONException::class)
    fun orca_bundle_read(archivePath: String, extractDir: String): String {
        val extractRoot = File(extractDir)
        if (!extractRoot.exists() && !extractRoot.mkdirs()) {
            throw IOException("Failed to create extraction directory: $extractRoot")
        }
        try {
            unzipArchive(File(archivePath), extractRoot)
            val files = JSONArray()
            val bundleStructureJson = collectJsonFiles(extractRoot, extractRoot, files)
                ?: throw IOException("bundle_structure.json not found in Orca bundle")
            val result = JSONObject()
            result.put("bundle_structure_json", bundleStructureJson)
            result.put("files", files)
            return result.toString()
        } finally {
            deleteRecursively(extractRoot)
        }
    }

    @Throws(IOException::class)
    private fun unzipArchive(archive: File, extractRoot: File) {
        val rootPath = extractRoot.canonicalPath + File.separator
        val rootCanonicalPath = extractRoot.canonicalPath
        val buffer = ByteArray(8192)
        ZipInputStream(FileInputStream(archive)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(extractRoot, entry.name)
                val outPath = outFile.canonicalPath
                if (outPath != rootCanonicalPath && !outPath.startsWith(rootPath)) {
                    throw IOException("Zip entry escapes extraction directory: ${entry.name}")
                }
                if (entry.isDirectory) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw IOException("Failed to create directory: $outFile")
                    }
                    continue
                }
                val parent = outFile.parentFile
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw IOException("Failed to create directory: $parent")
                }
                FileOutputStream(outFile).use { fos ->
                    while (true) {
                        val read = zis.read(buffer)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    @Throws(IOException::class, JSONException::class)
    private fun collectJsonFiles(root: File, current: File, files: JSONArray): String? {
        var bundleStructureJson: String? = null
        val children = current.listFiles() ?: return null
        for (child in children) {
            if (child.isDirectory) {
                val nested = collectJsonFiles(root, child, files)
                if (bundleStructureJson == null && nested != null) bundleStructureJson = nested
                continue
            }
            if (!child.name.endsWith(".json")) continue
            val content = readTextFile(child)
            if (child.name == "bundle_structure.json") bundleStructureJson = content
            val item = JSONObject()
            item.put("path", relativePath(root, child))
            item.put("content", content)
            files.put(item)
        }
        return bundleStructureJson
    }

    @Throws(IOException::class)
    private fun readTextFile(file: File): String {
        val bos = ByteArrayOutputStream()
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                bos.write(buffer, 0, read)
            }
        }
        return String(bos.toByteArray(), StandardCharsets.UTF_8)
    }

    @Throws(IOException::class)
    private fun relativePath(root: File, child: File): String {
        val rootPath = root.canonicalPath
        val childPath = child.canonicalPath
        val prefix = if (rootPath.endsWith(File.separator)) rootPath else rootPath + File.separator
        if (!childPath.startsWith(prefix)) throw IOException("File is outside the profile bundle: $child")
        return childPath.substring(prefix.length).replace('\\', '/')
    }

    private fun deleteRecursively(file: File?) {
        if (file == null || !file.exists()) return
        file.listFiles()?.forEach { child -> deleteRecursively(child) }
        file.delete()
    }
    }
}

