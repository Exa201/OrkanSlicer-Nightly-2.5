#include <android/log.h>
#include <array>
#include <limits>
#include <typeinfo>
#include <set>
#include <algorithm>
#include <unordered_set>
#include <memory>
#include <mutex>
#include <atomic>
#include <optional>
#include <stdexcept>
#include <cmath>

#include <thread>

#include <jni.h>
#include "libslic3r/libslic3r.h"
#include "libslic3r/Config.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "libslic3r/Format/STEP.hpp"
#include "libslic3r/QuadricEdgeCollapse.hpp"
#include "libslic3r/TriangleMeshDeal.hpp"
#include "libslic3r/Slicing.hpp"
#include "libslic3r/Emboss.hpp"
#include "libslic3r/NSVGUtils.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/ParameterUtils.hpp"
#include "libslic3r/CurledExtrusionUtils.hpp"
#include "libslic3r/ClipperUtils.hpp"
#include "libslic3r/Clipper2Utils.hpp"
#include "libslic3r/PaintedSegmentationPolicy.hpp"
#include "libslic3r/ExtrusionEntity.hpp"
#include "libslic3r/Fill/FillBase.hpp"
#include "libslic3r/Surface.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/PlaceholderParser.hpp"
#include "libslic3r/FilamentGroup.hpp"
#include "libslic3r/ModelArrange.hpp"
#include "libslic3r/MeshBoolean.hpp"
#include "libslic3r/Fill/Lightning/DistanceField.hpp"
#include "libslic3r/CSGMesh/ModelToCSGMesh.hpp"
#include "libslic3r/CSGMesh/PerformCSGMeshBooleans.hpp"
#include <libslic3r/SVG.hpp>
#include <libslic3r/GCode.hpp>
#include <libslic3r/ShortestPath.hpp>
#include <libslic3r/Print.hpp>
#include <libslic3r/Utils.hpp>
#include <libslic3r/AppConfig.hpp>
#include <libslic3r/CutUtils.hpp>
#include <android/log.h>
#define LOG_TAG "NativeCut"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#include "libslic3r/Geometry.hpp"
#include "libslic3r/Arrange.hpp"
#include "libslic3r/AABBMesh.hpp"
#include "libslic3r/AABBTreeIndirect.hpp"
#include "libslic3r/TriangleSelector.hpp"
#include "libslic3r/calib.hpp"
#include "libslic3r/Geometry/ConvexHull.hpp"
#include "libslic3r/Format/3mf.hpp"
#include "bbl/Orient.hpp"
#include "Viewer.hpp"

#include "GLModel.hpp"
#include "GLShader.hpp"
#include "bed_utils.hpp"
#include "ModelFilamentRemap.hpp"
#include "libvgcode_utils.hpp"

#include <igl/unproject.h>
#include <GLES3/gl3.h>

using namespace Slic3r;
using namespace Slic3r::GUI;

#define TAG "SB_Native"

struct PlaneData {
    std::vector<Vec3d> vertices;
    Vec3d normal;
    float area;
};
struct ModelRef {
    Model model;
    std::string base_name;
    std::mutex layer_height_brush_mutex;
    const ModelObject* layer_height_brush_object = nullptr;
    std::string layer_height_brush_config_path;
    std::optional<DynamicPrintConfig> layer_height_brush_config;
    std::optional<SlicingParameters> layer_height_brush_params;
};
struct ModelImportCancelled : std::exception {
    const char *what() const noexcept override { return "Model import canceled"; }
};
struct GLModelRef {
    GLModel model;
    TriangleMesh mesh;
    std::unique_ptr<AABBTreeIndirect::Tree3f> raycast_tree;
    Vec3d flatten_normal;
    // Touch raycasts run on Android's UI thread while the renderer replaces GLModel meshes
    // on the GL thread. Protect the CPU-side picking mesh/cache from concurrent destruction.
    std::mutex picking_mutex;

};
// A paint draft owns one selector and ray tree per model-part volume. A 3MF assembly is a single
// ModelObject, but every constituent part stores independent facet annotations.
struct PaintVolumeRef {
    int volume_idx = -1;
    TriangleMesh mesh;
    Slic3r::Transform3d mesh_to_world = Slic3r::Transform3d::Identity();
    std::unique_ptr<AABBMesh> emesh;
    std::unique_ptr<Slic3r::TriangleSelector> selector;
};
struct PaintSessionRef {
    ModelRef* model = nullptr;
    int objIdx = -1;
    // 0 = color (MMU segmentation), 1 = support, 2 = seam, 3 = fuzzy skin.
    int mode = 0;
    std::vector<std::unique_ptr<PaintVolumeRef>> volumes;
    using Snapshot = std::vector<Slic3r::TriangleSelector::TriangleSplittingData>;
    std::vector<Snapshot> undo_strokes, redo_strokes;
    std::optional<Snapshot> stroke_before;
};
struct ShaderRef {
    GLShaderProgram program;
};
struct BedRef {
    DynamicPrintConfig config;
    ExPolygon contour;
    GLModelRef* meshes = nullptr;
    GLModel* triangles;
    GLModel* gridlines;
    GLModel* contourlines;
    BuildVolume build_volume;
};
struct GCodeViewerRef {
    libvgcode::Viewer viewer;
    libvgcode::GCodeInputData data;
    bool initialized;
};
struct GCodeResultRef {
    GCodeProcessorResult result;
    std::string name;
    std::string spiral_vase_override_warning;
};
struct ConfigRef {
    DynamicPrintConfig config;
};

jclass sliceListenerClass;
jmethodID sliceListenerOnProgress;

jclass shadersManagerClass = nullptr;
jmethodID shadersManagerGetCurrent = nullptr;

static JavaVM* staticVM;

namespace {

class LightningDistanceFieldBoundsProbe final : public Slic3r::FillLightning::DistanceField {
public:
    using Slic3r::FillLightning::DistanceField::DistanceField;

    std::array<jint, 3> inspect(const Slic3r::BoundingBox &original_bounds) const
    {
        const jint sample_count = static_cast<jint>(m_unsupported_points.size());
        const jint outside_original = static_cast<jint>(std::count_if(
            m_unsupported_points.begin(), m_unsupported_points.end(), [&original_bounds](const UnsupportedCell &cell) {
                return !original_bounds.contains(cell.loc);
            }));
        const jint outside_expanded = static_cast<jint>(std::count_if(
            m_unsupported_points.begin(), m_unsupported_points.end(), [this](const UnsupportedCell &cell) {
                return !m_unsupported_points_bbox.contains(cell.loc);
            }));
        return { sample_count, outside_original, outside_expanded };
    }
};

void subdivide_text_mesh_for_surface(indexed_triangle_set &mesh)
{
    constexpr double max_edge_length = 1.25;
    constexpr size_t max_faces = 300000;
    for (int level = 0; level < 5 && mesh.indices.size() < max_faces; ++level) {
        double longest = 0.;
        for (const Vec3i32 &face : mesh.indices) {
            for (int edge = 0; edge < 3; ++edge) {
                const Vec3f &a = mesh.vertices[face[edge]];
                const Vec3f &b = mesh.vertices[face[(edge + 1) % 3]];
                longest = std::max(longest, double((a - b).norm()));
            }
        }
        if (longest <= max_edge_length || mesh.indices.size() * 4 > max_faces) break;

        std::unordered_map<uint64_t, int32_t> midpoint_indices;
        std::vector<Vec3f> vertices = mesh.vertices;
        std::vector<Vec3i32> faces;
        midpoint_indices.reserve(mesh.indices.size());
        faces.reserve(mesh.indices.size() * 4);
        auto midpoint = [&](int32_t a, int32_t b) {
            const uint32_t lo = static_cast<uint32_t>(std::min(a, b));
            const uint32_t hi = static_cast<uint32_t>(std::max(a, b));
            const uint64_t key = (uint64_t(lo) << 32) | uint64_t(hi);
            auto found = midpoint_indices.find(key);
            if (found != midpoint_indices.end()) return found->second;
            const int32_t index = static_cast<int32_t>(vertices.size());
            vertices.emplace_back((mesh.vertices[a] + mesh.vertices[b]) * 0.5f);
            midpoint_indices.emplace(key, index);
            return index;
        };
        for (const Vec3i32 &face : mesh.indices) {
            const int32_t a = face.x(), b = face.y(), c = face.z();
            const int32_t ab = midpoint(a, b), bc = midpoint(b, c), ca = midpoint(c, a);
            faces.emplace_back(a, ab, ca);
            faces.emplace_back(ab, b, bc);
            faces.emplace_back(ca, bc, c);
            faces.emplace_back(ab, bc, ca);
        }
        mesh.vertices = std::move(vertices);
        mesh.indices = std::move(faces);
    }
}

std::vector<Transform3d> model_instance_transforms(const ModelObject &object)
{
    std::vector<Transform3d> instances;
    if (object.instances.empty()) instances.emplace_back(Transform3d::Identity());
    else {
        instances.reserve(object.instances.size());
        for (const ModelInstance *instance : object.instances)
            if (instance != nullptr) instances.emplace_back(instance->get_matrix());
    }
    return instances;
}

size_t find_instance_for_world_surface(
    const std::vector<Transform3d> &instances,
    const AABBMesh &raycaster,
    const Vec3d &world_position,
    const Vec3d &world_normal,
    double ray_distance)
{
    size_t selected = instances.size();
    double closest_hit_distance = std::numeric_limits<double>::infinity();
    const Vec3d normal = world_normal.normalized();
    for (size_t index = 0; index < instances.size(); ++index) {
        const Transform3d inverse = instances[index].inverse();
        const Vec3d inward = -(inverse.linear() * normal).normalized();
        const Vec3d origin = inverse * (world_position + normal * ray_distance);
        const auto hit = raycaster.query_ray_hit(origin, inward);
        if (!hit.is_hit()) continue;
        const Vec3d candidate_world = instances[index] * hit.position();
        const double distance = (candidate_world - world_position).norm();
        if (distance < closest_hit_distance) {
            closest_hit_distance = distance;
            selected = index;
        }
    }
    return closest_hit_distance <= 2.0 ? selected : instances.size();
}

int find_surface_extruder(
    const ModelObject &target,
    const Transform3d &instance,
    const Vec3d &world_position,
    const Vec3d &world_normal,
    double ray_distance)
{
    const Transform3d inverse = instance.inverse();
    const Vec3d normal = world_normal.normalized();
    const Vec3d inward = -(inverse.linear() * normal).normalized();
    const Vec3d origin = inverse * (world_position + normal * ray_distance);
    double nearest_part_hit = std::numeric_limits<double>::infinity();
    int surface_extruder = -1;
    for (const ModelVolume *volume : target.volumes) {
        if (volume == nullptr || !volume->is_model_part()) continue;
        TriangleMesh part_mesh = volume->mesh();
        part_mesh.transform(volume->get_matrix());
        if (part_mesh.its.vertices.empty() || part_mesh.its.indices.empty()) continue;
        AABBMesh part_raycaster(part_mesh);
        const auto hit = part_raycaster.query_ray_hit(origin, inward);
        if (!hit.is_hit()) continue;
        const Vec3d world_hit = instance * hit.position();
        const double distance = (world_hit - world_position).norm();
        if (distance < nearest_part_hit) {
            nearest_part_hit = distance;
            surface_extruder = volume->extruder_id();
        }
    }
    return nearest_part_hit <= 2.0 ? surface_extruder : -1;
}

Transform3d conform_text_mesh_to_model_surface(
    indexed_triangle_set &text_mesh,
    const ModelObject &target,
    const Vec3d &world_position,
    const Vec3d &world_normal,
    int &surface_extruder)
{
    if (target.volumes.empty()) throw std::runtime_error("The target model has no printable surface");
    TriangleMesh target_mesh = target.raw_mesh();
    if (target_mesh.its.vertices.empty() || target_mesh.its.indices.empty())
        throw std::runtime_error("The target model has no printable surface");
    AABBMesh raycaster(target_mesh);

    const Vec3d normal = world_normal.normalized();
    const Transform3d tangent_frame = Emboss::create_transformation_onto_surface(world_position, normal);
    const BoundingBoxf3 bounds = bounding_box(target_mesh.its);
    const double ray_distance = std::max(100., 4. * (bounds.max - bounds.min).norm() + 10.);
    const std::vector<Transform3d> instances = model_instance_transforms(target);
    if (instances.empty()) throw std::runtime_error("The target model has no placed instance");
    const size_t selected_instance = find_instance_for_world_surface(
        instances, raycaster, world_position, normal, ray_distance);
    if (selected_instance == instances.size())
        throw std::runtime_error("Tap the text onto a visible model surface");

    subdivide_text_mesh_for_surface(text_mesh);
    const Transform3d &instance = instances[selected_instance];
    const Transform3d inverse = instance.inverse();
    const Vec3d inward = -(inverse.linear() * normal).normalized();
    const Vec3d outward = -inward;
    // A multipart target can use different filaments per volume. Follow the tapped part's
    // extruder rather than silently inheriting the parent object's default tool.
    surface_extruder = find_surface_extruder(target, instance, world_position, normal, ray_distance);
    const double sink = 0.04; // A small overlap keeps raised text bonded to the model for slicing.
    for (Vec3f &vertex : text_mesh.vertices) {
        const Vec3d planar_world = tangent_frame * Vec3d(vertex.x(), vertex.y(), 0.);
        const Vec3d origin = inverse * (planar_world + normal * ray_distance);
        const auto hit = raycaster.query_ray_hit(origin, inward);
        if (!hit.is_hit())
            throw std::runtime_error("The complete text must fit on the visible model surface");
        Vec3d surface_normal = instance.linear().inverse().transpose() * hit.normal();
        if (surface_normal.squaredNorm() < 1e-12)
            throw std::runtime_error("The target surface has an invalid normal");
        surface_normal.normalize();
        if (surface_normal.dot(normal) < 0.) surface_normal = -surface_normal;
        const Vec3d world_surface = instance * hit.position();
        vertex = (world_surface + surface_normal * (double(vertex.z()) - sink)).cast<float>();
    }
    return instance;
}

} // namespace

GLShaderProgram* get_current_shader() {
    JNIEnv* env;
    if (staticVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return nullptr;
    }

    jlong ptr = env->CallStaticLongMethod(shadersManagerClass, shadersManagerGetCurrent);
    if (ptr == 0) {
        return nullptr;
    }
    ShaderRef* ref = (ShaderRef*) (intptr_t) ptr;
    GLShaderProgram* program = &ref->program;

    return program;
}

extern "C" {
    int JNI_OnLoad(JavaVM *vm, void*) {
        JNIEnv *env;
        if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            return JNI_ERR;
        }

        staticVM = vm;

        sliceListenerClass = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/SliceListener");
        sliceListenerOnProgress = env->GetMethodID(sliceListenerClass, "onProgress", "(ILjava/lang/String;)V");

        shadersManagerClass = static_cast<jclass>(env->NewGlobalRef(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/GLShadersManager")));
        shadersManagerGetCurrent = env->GetStaticMethodID(shadersManagerClass, "getCurrentShaderPointer", "()J");

        return JNI_VERSION_1_6;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_set_1svg_1path_1prefix(JNIEnv *env, jclass, jstring path) {
        const char* chars = env->GetStringUTFChars(path, JNI_FALSE);
        Slic3r::svg_path_prefix = std::string(chars);
        env->ReleaseStringUTFChars(path, chars);
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_reprapfirmware_1g10_1preview_1probe(
            JNIEnv *env, jclass) {
        using namespace Slic3r;
        const auto inspect = [](GCodeFlavor flavor, const std::string &gcode) {
            PrintConfig config;
            config.gcode_flavor.value = flavor;
            GCodeProcessor processor;
            processor.apply_config(config);
            processor.initialize_result_moves();
            processor.process_buffer(gcode);

            jint retract_moves = 0;
            jint extrusion_temperature = -1;
            for (const GCodeProcessorResult::MoveVertex &move : processor.get_result().moves) {
                if (move.type == EMoveType::Retract)
                    ++retract_moves;
                if (move.type == EMoveType::Extrude)
                    extrusion_temperature = static_cast<jint>(std::round(move.temperature));
            }
            return std::array<jint, 2>{ retract_moves, extrusion_temperature };
        };

        const auto reprap = inspect(gcfRepRapFirmware, "G10 P0 S220\nG1 X10 Y10 E1 F1200\n");
        const auto marlin = inspect(gcfMarlinFirmware, "G10 S220\nG1 X10 Y10 E1 F1200\n");
        const std::array<jint, 3> values{ reprap[0], reprap[1], marlin[0] };
        jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
        return result;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_lightning_1distance_1field_1bounds_1probe(
            JNIEnv *env, jclass) {
        using namespace Slic3r;
        Polygon current_outline({
            Point::new_scale(-5.0, -5.0), Point::new_scale(5.0, -5.0),
            Point::new_scale(5.0, 5.0), Point::new_scale(-5.0, 5.0),
        });
        Polygon opened_overhang({
            Point::new_scale(-10.0, -10.0), Point::new_scale(10.0, -10.0),
            Point::new_scale(10.0, 10.0), Point::new_scale(-10.0, 10.0),
        });
        const BoundingBox original_bounds(current_outline.points);
        const LightningDistanceFieldBoundsProbe field(
            scaled(1.2), Polygons{ current_outline }, original_bounds, Polygons{ opened_overhang });
        const std::array<jint, 3> values = field.inspect(original_bounds);
        jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
        return result;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_tpms_1ring_1endpoints_1probe(
            JNIEnv *env, jclass) {
        using namespace Slic3r;
        constexpr double spacing = 0.45;
        const ExPolygon box(Points{
            Point::new_scale(0., 0.), Point::new_scale(60., 0.),
            Point::new_scale(60., 60.), Point::new_scale(0., 60.),
        });
        const AABBTreeLines::LinesDistancer<Line> walls(
            to_lines(offset_ex(ExPolygons{box}, -float(scale_(0.5 * spacing)))));
        const auto on_wall = [&walls](const Point &point) {
            return walls.distance_from_lines<false>(point) < scale_(0.01);
        };
        constexpr std::array<InfillPattern, 3> patterns{ipGyroid, ipTpmsD, ipTpmsFK};
        constexpr std::array<double, 9> z_values{5.1, 12.3, 20.1, 25.3, 28.7, 34.7, 40.1, 47.9, 54.9};
        jint checked = 0;
        jint failures = 0;
        for (const InfillPattern pattern : patterns) {
            for (const int multiline : {1, 2}) {
                for (const double z : z_values) {
                    std::unique_ptr<Fill> filler(Fill::new_from_type(pattern));
                    filler->spacing = spacing;
                    filler->angle = float(M_PI / 4.);
                    filler->z = z;
                    FillParams params;
                    params.density = 0.3f;
                    params.multiline = multiline;
                    params.gyroid_optimized = pattern == ipGyroid;
                    params.layer_height = 0.2;
                    params.dont_adjust = true;
                    Surface surface(stInternal, box);
                    const Polylines paths = filler->fill_surface(&surface, params);
                    ++checked;
                    if (paths.empty()) {
                        ++failures;
                        continue;
                    }
                    for (const Polyline &path : paths) {
                        if (path.first_point() == path.last_point()) {
                            if (std::any_of(path.points.begin(), path.points.end(), on_wall))
                                ++failures;
                        } else if (!on_wall(path.first_point()) || !on_wall(path.last_point())) {
                            ++failures;
                        }
                    }
                }
            }
        }
        const std::array<jint, 2> values{checked, failures};
        jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
        return result;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_multiline_1infill_1ring_1endpoints_1probe(
            JNIEnv *env, jclass) {
        using namespace Slic3r;
        constexpr double spacing = 0.45;
        constexpr std::array<InfillPattern, 15> patterns{
            ipGyroid, ipTpmsD, ipTpmsFK, ipHoneycomb, ipQuarterCubic,
            ipLateralLattice, ipLateralHoneycomb, ipRectilinear, ipAlignedRectilinear,
            ipGrid, ipTriangles, ipStars, ipCubic, ipCrossHatch, ipHilbertCurve,
        };
        constexpr std::array<double, 3> z_values{12.3, 28.7, 47.9};
        std::array<jint, 19> results{};
        jint pattern_index = 1;
        for (const InfillPattern pattern : patterns) {
            jint pattern_failures = 0;
            for (const bool with_hole : {false, true}) {
                ExPolygon region(Points{
                    Point::new_scale(0., 0.), Point::new_scale(60., 0.),
                    Point::new_scale(60., 60.), Point::new_scale(0., 60.),
                });
                if (with_hole) {
                    Polygon hole = make_circle_num_segments(scale_(10.), 60);
                    hole.translate(Point::new_scale(30., 30.));
                    hole.reverse();
                    region.holes.emplace_back(std::move(hole));
                }
                const AABBTreeLines::LinesDistancer<Line> walls(
                    to_lines(offset_ex(ExPolygons{region}, -float(scale_(0.5 * spacing)))));
                const auto on_wall = [&walls](const Point &point) {
                    return walls.distance_from_lines<false>(point) < scale_(0.01);
                };
                const auto runs_between_walls = [&on_wall](const Polyline &path) {
                    for (size_t i = 1; i < path.size(); ++i)
                        if (path.points[i - 1].distance_to(path.points[i]) > scale_(0.1) &&
                            on_wall(path.points[i - 1]) && on_wall(path.points[i]))
                            return true;
                    return false;
                };
                for (const double z : z_values) {
                    std::unique_ptr<Fill> filler(Fill::new_from_type(pattern));
                    if (!filler) {
                        ++pattern_failures;
                        continue;
                    }
                    filler->spacing = spacing;
                    filler->angle = float(M_PI / 4.);
                    filler->z = z;
                    filler->layer_id = size_t(std::lround(z / 0.2));
                    filler->set_bounding_box(get_extents(region.contour));
                    FillParams params;
                    params.pattern = pattern;
                    params.density = 0.3f;
                    params.multiline = 2;
                    params.gyroid_optimized = false;
                    params.layer_height = 0.2;
                    params.dont_adjust = true;
                    Surface surface(stInternal, region);
                    const Polylines paths = filler->fill_surface(&surface, params);
                    ++results[0];
                    if (paths.empty()) {
                        ++pattern_failures;
                        if (pattern == ipGrid)
                            ++results[16];
                        continue;
                    }
                    for (const Polyline &path : paths) {
                        if (path.first_point() == path.last_point()) {
                            if (path.size() <= 3) {
                                ++pattern_failures;
                                if (pattern == ipGrid) ++results[17];
                            } else if (!on_wall(path.first_point()) && runs_between_walls(path)) {
                                ++pattern_failures;
                                if (pattern == ipGrid) ++results[18];
                            }
                        } else if (!on_wall(path.first_point()) || !on_wall(path.last_point())) {
                            ++pattern_failures;
                            if (pattern == ipGrid) ++results[18];
                        }
                    }
                }
            }
            results[pattern_index++] = pattern_failures;
        }
        jintArray result = env->NewIntArray(static_cast<jsize>(results.size()));
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(results.size()), results.data());
        return result;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_mesh_1boolean_1isolated_1vertices_1probe(
            JNIEnv *env, jclass) {
        using namespace Slic3r;
        indexed_triangle_set source = its_make_cube(10., 10., 10.);
        // Isolated vertices before, between, and after the cube's referenced vertices exercise
        // both removal and index compaction in the production CGAL conversion overload.
        source.vertices.insert(source.vertices.begin(), Vec3f(-1.f, -1.f, -1.f));
        source.vertices.insert(source.vertices.begin() + 4, Vec3f(5.f, 5.f, 5.f));
        source.vertices.emplace_back(20.f, 20.f, 20.f);
        for (Vec3i32 &face : source.indices)
            for (int i = 0; i < 3; ++i)
                face[i] += face[i] >= 3 ? 2 : 1;

        auto converted = MeshBoolean::cgal::triangle_mesh_to_cgal(source);
        if (!converted) return nullptr;
        const bool boundsVolume = MeshBoolean::cgal::does_bound_a_volume(*converted);
        const bool selfIntersects = MeshBoolean::cgal::does_self_intersect(*converted);
        TriangleMesh result = MeshBoolean::cgal::cgal_to_triangle_mesh(*converted);
        bool validIndices = true;
        for (const Vec3i32 &face : result.its.indices)
            for (int i = 0; i < 3; ++i)
                validIndices &= face[i] >= 0 && static_cast<size_t>(face[i]) < result.its.vertices.size();
        const std::array<jdouble, 5> values{
            boundsVolume ? 1.0 : 0.0,
            selfIntersects ? 1.0 : 0.0,
            static_cast<jdouble>(result.volume()),
            validIndices ? 1.0 : 0.0,
            static_cast<jdouble>(result.its.indices.size()),
        };
        jdoubleArray output = env->NewDoubleArray(static_cast<jsize>(values.size()));
        if (output != nullptr)
            env->SetDoubleArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
        return output;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_curled_1extrusion_1estimation_1policy_1probe(
            JNIEnv *env, jclass) {
        using namespace Slic3r;
        PrintRegionConfig overhangConfig;
        overhangConfig.enable_overhang_speed.value = true;
        overhangConfig.slowdown_for_curled_perimeters.value = false;
        PrintRegionConfig slowdownConfig;
        slowdownConfig.enable_overhang_speed.value = false;
        slowdownConfig.slowdown_for_curled_perimeters.value = true;
        PrintRegionConfig bothConfig;
        bothConfig.enable_overhang_speed.value = true;
        bothConfig.slowdown_for_curled_perimeters.value = true;

        PrintRegion overhangRegion(overhangConfig);
        PrintRegion slowdownRegion(slowdownConfig);
        PrintRegion bothRegion(bothConfig);
        const std::vector<PrintRegion*> none;
        const std::vector<PrintRegion*> overhangOnly{&overhangRegion};
        const std::vector<PrintRegion*> slowdownOnly{&slowdownRegion};
        const std::vector<PrintRegion*> bothSameRegion{&bothRegion};
        const std::vector<PrintRegion*> bothAcrossRegions{&overhangRegion, &slowdownRegion};
        const std::array<jint, 5> values{
            should_estimate_curled_extrusions(none) ? 1 : 0,
            should_estimate_curled_extrusions(overhangOnly) ? 1 : 0,
            should_estimate_curled_extrusions(slowdownOnly) ? 1 : 0,
            should_estimate_curled_extrusions(bothSameRegion) ? 1 : 0,
            should_estimate_curled_extrusions(bothAcrossRegions) ? 1 : 0,
        };
        jintArray output = env->NewIntArray(static_cast<jsize>(values.size()));
        if (output != nullptr)
            env->SetIntArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
        return output;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_filament_1variant_1resize_1regression_1probe(
            JNIEnv *env, jclass) {
        using namespace Slic3r;
        DynamicPrintConfig config = DynamicPrintConfig::full_print_config();
        config.option<ConfigOptionStrings>("filament_extruder_variant", true)->values = {
            "Direct Drive Standard", "High Flow"};
        config.option<ConfigOptionStrings>("volumetric_speed_coefficients", true)->values = {"10", "20"};

        // One filament slot with two nozzle variants must retain both values.
        config.set_num_filaments(1);
        const auto &variants = config.option<ConfigOptionStrings>("filament_extruder_variant")->values;
        const auto &coefficients = config.option<ConfigOptionStrings>("volumetric_speed_coefficients")->values;
        const bool preserved = variants == std::vector<std::string>{"Direct Drive Standard", "High Flow"} &&
                              coefficients == std::vector<std::string>{"10", "20"};
        const std::array<jint, 4> values{
            preserved ? 1 : 0,
            static_cast<jint>(variants.size()),
            static_cast<jint>(coefficients.size()),
            static_cast<jint>(config.option<ConfigOptionStrings>("filament_type")->size()),
        };
        jintArray output = env->NewIntArray(static_cast<jsize>(values.size()));
        if (output != nullptr)
            env->SetIntArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
        return output;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_painted_1segmentation_1state_1count_1probe(
            JNIEnv *env, jclass, jint filamentCount, jint colorCount) {
        const std::array<jint, 4> values{
            filamentCount,
            colorCount,
            static_cast<jint>(painted_segmentation_state_count(static_cast<size_t>(std::max(0, filamentCount)))),
            static_cast<jint>(painted_segmentation_layer_channel_count(
                static_cast<size_t>(std::max(0, filamentCount)), static_cast<size_t>(std::max(0, colorCount)))),
        };
        jintArray output = env->NewIntArray(static_cast<jsize>(values.size()));
        if (output != nullptr)
            env->SetIntArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
        return output;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_get_1print_1config_1def(JNIEnv *env, jclass, jobject def) {
        jclass printConfigDefClass = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/PrintConfigDef");
        jmethodID printConfigAddOption = env->GetMethodID(printConfigDefClass, "addOption", "(Ljava/lang/String;Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef;)V");
        jmethodID printConfigResolveEnum = env->GetStaticMethodID(printConfigDefClass, "resolveEnum", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");
        jclass configOptionDefClass = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef");
        jmethodID configOptionDefCtr = env->GetMethodID(configOptionDefClass, "<init>", "()V");
        jfieldID keyField = env->GetFieldID(configOptionDefClass, "key", "Ljava/lang/String;");
        jfieldID typeField = env->GetFieldID(configOptionDefClass, "type", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionType;");
        jfieldID guiTypeField = env->GetFieldID(configOptionDefClass, "guiType", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$GUIType;");
        jfieldID labelField = env->GetFieldID(configOptionDefClass, "label", "Ljava/lang/String;");
        jfieldID fullLabelField = env->GetFieldID(configOptionDefClass, "fullLabel", "Ljava/lang/String;");
        jfieldID printerTechField = env->GetFieldID(configOptionDefClass, "printerTechnology", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$PrinterTechnology;");
        jfieldID categoryField = env->GetFieldID(configOptionDefClass, "category", "Ljava/lang/String;");
        jfieldID tooltipField = env->GetFieldID(configOptionDefClass, "tooltip", "Ljava/lang/String;");
        jfieldID sidetextField = env->GetFieldID(configOptionDefClass, "sidetext", "Ljava/lang/String;");
        jfieldID multilineField = env->GetFieldID(configOptionDefClass, "multiline", "Z");
        jfieldID fullWidthField = env->GetFieldID(configOptionDefClass, "fullWidth", "Z");
        jfieldID readonlyField = env->GetFieldID(configOptionDefClass, "readonly", "Z");
        jfieldID nullableField = env->GetFieldID(configOptionDefClass, "nullable", "Z");
        jfieldID heightField = env->GetFieldID(configOptionDefClass, "height", "I");
        jfieldID widthField = env->GetFieldID(configOptionDefClass, "width", "I");
        jfieldID minField = env->GetFieldID(configOptionDefClass, "min", "F");
        jfieldID maxField = env->GetFieldID(configOptionDefClass, "max", "F");
        jfieldID modeField = env->GetFieldID(configOptionDefClass, "mode", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionMode;");
        jfieldID defaultValueField = env->GetFieldID(configOptionDefClass, "defaultValue", "Ljava/lang/String;");
        jfieldID enumLabelsField = env->GetFieldID(configOptionDefClass, "enumLabels", "[Ljava/lang/String;");
        jfieldID enumValuesField = env->GetFieldID(configOptionDefClass, "enumValues", "[Ljava/lang/String;");

        auto resolveEnum = [&env,&printConfigDefClass,&printConfigResolveEnum](char* className, char* enumValue) {
            jobject key = env->NewStringUTF(className);
            jobject val = env->NewStringUTF(enumValue);

            jobject v = env->CallStaticObjectMethod(printConfigDefClass, printConfigResolveEnum, key, val);

            env->DeleteLocalRef(key);
            env->DeleteLocalRef(val);
            return v;
        };

        PrintConfigDef nDef;
        for (std::string key : nDef.keys()) {
            const ConfigOptionDef* nCfgDef = nDef.get(key);
            bool isEnum = false;
            jobject cfgDef = env->NewObject(configOptionDefClass, configOptionDefCtr);
            const char* typeStr;
            switch (nCfgDef->type) {
                default:
                case Slic3r::coNone:
                    typeStr = "NONE";
                    break;
                case Slic3r::coFloat:
                    typeStr = "FLOAT";
                    break;
                case Slic3r::coFloats:
                    typeStr = "FLOATS";
                    break;
                case Slic3r::coInt:
                    typeStr = "INT";
                    break;
                case Slic3r::coInts:
                    typeStr = "INTS";
                    break;
                case Slic3r::coString:
                    typeStr = "STRING";
                    break;
                case Slic3r::coStrings:
                    typeStr = "STRINGS";
                    break;
                case Slic3r::coPercent:
                    typeStr = "PERCENT";
                    break;
                case Slic3r::coPercents:
                    typeStr = "PERCENTS";
                    break;
                case Slic3r::coFloatOrPercent:
                    typeStr = "FLOAT_OR_PERCENT";
                    break;
                case Slic3r::coFloatsOrPercents:
                    typeStr = "FLOATS_OR_PERCENTS";
                    break;
                case Slic3r::coPoint:
                    typeStr = "POINT";
                    break;
                case Slic3r::coPoints:
                    typeStr = "POINTS";
                    break;
                case Slic3r::coPoint3:
                    typeStr = "POINT3";
                    break;
                case Slic3r::coBool:
                    typeStr = "BOOL";
                    break;
                case Slic3r::coBools:
                    typeStr = "BOOLS";
                    break;
                case Slic3r::coEnum:
                    typeStr = "ENUM";
                    isEnum = true;
                    break;
                case Slic3r::coEnums:
                    typeStr = "ENUMS";
                    isEnum = true;
                    break;
            }

            const char* guiTypeStr;
            switch (nCfgDef->gui_type) {
                default:
                case Slic3r::ConfigOptionDef::GUIType::undefined:
                    guiTypeStr = "UNDEFINED";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::i_enum_open:
                    guiTypeStr = "I_ENUM_OPEN";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::f_enum_open:
                    guiTypeStr = "F_ENUM_OPEN";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::select_open:
                    guiTypeStr = "SELECT_OPEN";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::color:
                    guiTypeStr = "COLOR";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::slider:
                    guiTypeStr = "SLIDER";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::legend:
                    guiTypeStr = "LEGEND";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::one_string:
                    guiTypeStr = "ONE_STRING";
                    break;
            }

            // Open enums keep native suggestions in enum_values/enum_labels even when
            // their stored option type is numeric or string rather than coEnum.
            isEnum = isEnum
                    || nCfgDef->gui_type == Slic3r::ConfigOptionDef::GUIType::i_enum_open
                    || nCfgDef->gui_type == Slic3r::ConfigOptionDef::GUIType::f_enum_open
                    || nCfgDef->gui_type == Slic3r::ConfigOptionDef::GUIType::select_open;

            const char* techStr;
            switch (nCfgDef->printer_technology) {
                case Slic3r::ptAny:
                    techStr = "ANY";
                    break;
                case Slic3r::ptFFF:
                    techStr = "FFF";
                    break;
                case Slic3r::ptSLA:
                    techStr = "SLA";
                    break;
                default:
                case Slic3r::ptUnknown:
                    techStr = "UNKNOWN";
                    break;
            }

            const char* modeStr;
            switch (nCfgDef->mode) {
                case Slic3r::comSimple:
                    modeStr = "SIMPLE";
                    break;
                case Slic3r::comAdvanced:
                    modeStr = "ADVANCED";
                    break;
                case Slic3r::comExpert:
                    modeStr = "EXPERT";
                    break;
                default:
                case Slic3r::comDevelop:
                    modeStr = "UNDEFINED";
                    break;
            }

            jobject keyValue = env->NewStringUTF(key.c_str());
            jobject typeValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionType", (char*) typeStr);
            jobject guiTypeValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$GUIType", (char*) guiTypeStr);
            jobject labelValue = env->NewStringUTF(nCfgDef->label.c_str());
            jobject fullLabelValue = env->NewStringUTF(nCfgDef->full_label.c_str());
            jobject printerTechValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$PrinterTechnology", (char*) techStr);
            jobject categoryValue = env->NewStringUTF(nCfgDef->category.c_str());
            jobject tooltipValue = env->NewStringUTF(nCfgDef->tooltip.c_str());
            jobject sidetextValue = env->NewStringUTF(nCfgDef->sidetext.c_str());
            jobject modeValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionMode", (char*) modeStr);

            env->SetObjectField(cfgDef, keyField, keyValue);
            env->SetObjectField(cfgDef, typeField, typeValue);
            env->SetObjectField(cfgDef, guiTypeField, guiTypeValue);
            env->SetObjectField(cfgDef, labelField, labelValue);
            env->SetObjectField(cfgDef, fullLabelField, fullLabelValue);
            env->SetObjectField(cfgDef, printerTechField, printerTechValue);
            env->SetObjectField(cfgDef, categoryField, categoryValue);
            env->SetObjectField(cfgDef, tooltipField, tooltipValue);
            env->SetObjectField(cfgDef, sidetextField, sidetextValue);
            env->SetBooleanField(cfgDef, multilineField, nCfgDef->multiline);
            env->SetBooleanField(cfgDef, fullWidthField, nCfgDef->full_width);
            env->SetBooleanField(cfgDef, readonlyField, nCfgDef->readonly);
            env->SetBooleanField(cfgDef, nullableField, nCfgDef->nullable);
            env->SetIntField(cfgDef, heightField, nCfgDef->height);
            env->SetIntField(cfgDef, widthField, nCfgDef->width);
            env->SetFloatField(cfgDef, minField, nCfgDef->min);
            env->SetFloatField(cfgDef, maxField, nCfgDef->max);
            env->SetObjectField(cfgDef, modeField, modeValue);
            if (isEnum) {
                // OrcaSlicer stores enum labels/values as plain vectors on ConfigOptionDef
                // (PrusaSlicer's enum_def/ConfigOptionEnumDef wrapper does not exist here).
                const std::vector<std::string>& labels = nCfgDef->enum_labels;
                jobjectArray labelsArr = env->NewObjectArray(labels.size(), env->FindClass("java/lang/String"), nullptr);
                for (int i = 0; i < labels.size(); i++) {
                    jobject str = env->NewStringUTF(labels[i].c_str());
                    env->SetObjectArrayElement(labelsArr, i, str);
                    env->DeleteLocalRef(str);
                }

                const std::vector<std::string>& values = nCfgDef->enum_values;
                jobjectArray valuesArr = env->NewObjectArray(values.size(), env->FindClass("java/lang/String"), nullptr);
                for (int i = 0; i < values.size(); i++) {
                    jobject str = env->NewStringUTF(values[i].c_str());
                    env->SetObjectArrayElement(valuesArr, i, str);
                    env->DeleteLocalRef(str);
                }

                env->SetObjectField(cfgDef, enumLabelsField, labelsArr);
                env->SetObjectField(cfgDef, enumValuesField, valuesArr);

                env->DeleteLocalRef(labelsArr);
                env->DeleteLocalRef(valuesArr);
            }

            const ConfigOption* defValue = nCfgDef->get_default_value<ConfigOption>();
            std::unique_ptr<ConfigOption> serializedEnumDefault;
            if (defValue != nullptr && nCfgDef->type == Slic3r::coEnums) {
                // Enum-list defaults may have been created before their definition's
                // key map was attached. Serialize the normalized default so Kotlin
                // receives the Orca string key instead of an empty value.
                serializedEnumDefault.reset(nCfgDef->create_default_option());
                defValue = serializedEnumDefault.get();
            }
            if (defValue != nullptr) {
                jobject defValueObj = env->NewStringUTF(defValue->serialize().c_str());
                env->SetObjectField(cfgDef, defaultValueField, defValueObj);
                env->DeleteLocalRef(defValueObj);
            }

            env->CallVoidMethod(def, printConfigAddOption, keyValue, cfgDef);

            env->DeleteLocalRef(cfgDef);
            env->DeleteLocalRef(keyValue);
            env->DeleteLocalRef(typeValue);
            env->DeleteLocalRef(guiTypeValue);
            env->DeleteLocalRef(labelValue);
            env->DeleteLocalRef(fullLabelValue);
            env->DeleteLocalRef(printerTechValue);
            env->DeleteLocalRef(categoryValue);
            env->DeleteLocalRef(tooltipValue);
            env->DeleteLocalRef(sidetextValue);
            env->DeleteLocalRef(modeValue);
        }
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1read_1from_1file(JNIEnv *env, jclass, jstring path, jstring base_name, jint plateId, jobject progressCallback, jdouble stepLinearDeflection, jdouble stepAngularDeflection, jboolean splitStepCompound) {
        if (path == nullptr || base_name == nullptr) {
            jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
            if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, "Model path and name are required");
            return 0;
        }
        const char* chars = env->GetStringUTFChars(path, JNI_FALSE);
        if (chars == nullptr) return 0;
        const char* baseChars = env->GetStringUTFChars(base_name, JNI_FALSE);
        if (baseChars == nullptr) {
            env->ReleaseStringUTFChars(path, chars);
            return 0;
        }

        ImportstlProgressFn progressFn;
        ImportStepProgressFn stepProgressFn;
        bool stepImportCancelled = false;
        if (progressCallback != nullptr) {
            jclass callbackClass = env->GetObjectClass(progressCallback);
            jmethodID onProgress = env->GetMethodID(callbackClass, "onProgress", "(F)Z");
            env->DeleteLocalRef(callbackClass);
            if (onProgress == nullptr) {
                env->ReleaseStringUTFChars(path, chars);
                env->ReleaseStringUTFChars(base_name, baseChars);
                return 0;
            }
            progressFn = [env, progressCallback, onProgress](float fraction) {
                const jboolean keepGoing = env->CallBooleanMethod(progressCallback, onProgress, fraction);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    throw std::runtime_error("Model import progress callback failed");
                }
                if (!keepGoing)
                    throw ModelImportCancelled();
            };
            stepProgressFn = [env, progressCallback, onProgress, &stepImportCancelled](int stage, int current, int total, bool& cancel) {
                const float stageProgress = total > 0
                    ? std::clamp(static_cast<float>(current) / static_cast<float>(total), 0.f, 1.f)
                    : 0.f;
                const float fraction = std::clamp((static_cast<float>(stage) + stageProgress) / 3.f, 0.f, 1.f);
                const jboolean keepGoing = env->CallBooleanMethod(progressCallback, onProgress, fraction);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    throw std::runtime_error("STEP import progress callback failed");
                }
                if (!keepGoing) {
                    cancel = true;
                    stepImportCancelled = true;
                }
            };
        }

        std::unique_ptr<ModelRef> ref;
        try {
            ref = std::make_unique<ModelRef>();
            LoadStrategy load_strategy = LoadStrategy::AddDefaultInstances;
            if (boost::algorithm::iends_with(std::string(chars), ".3mf")) {
                load_strategy = LoadStrategy::AddDefaultInstances | LoadStrategy::LoadModel |
                    LoadStrategy::KeepOriginalNames;
            }
            const std::string inputPath(chars);
            if (boost::algorithm::iends_with(inputPath, ".step") || boost::algorithm::iends_with(inputPath, ".stp")) {
                Model stepModel = Model::read_from_step(
                    inputPath, LoadStrategy::AddDefaultInstances, stepProgressFn, nullptr, nullptr,
                    stepLinearDeflection, stepAngularDeflection, splitStepCompound == JNI_TRUE);
                if (stepImportCancelled) throw ModelImportCancelled();
                ref->model = std::move(stepModel);
            } else {
                ref->model = Model::read_from_file(inputPath, nullptr, nullptr, load_strategy, nullptr, nullptr, nullptr, nullptr, nullptr, progressFn, nullptr, plateId, nullptr);
            }
            ref->base_name = std::string(baseChars);
        } catch (const ModelImportCancelled&) {
            env->ReleaseStringUTFChars(path, chars);
            env->ReleaseStringUTFChars(base_name, baseChars);
            jclass exceptionClass = env->FindClass("java/util/concurrent/CancellationException");
            if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, "Model import canceled");
            return 0;
        } catch (const Slic3r::RuntimeError& e) {
            env->ReleaseStringUTFChars(path, chars);
            env->ReleaseStringUTFChars(base_name, baseChars);
            jclass exceptionClass = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, e.what());
            return 0;
        } catch (const std::exception& e) {
            env->ReleaseStringUTFChars(path, chars);
            env->ReleaseStringUTFChars(base_name, baseChars);
            jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
            if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, e.what());
            return 0;
        } catch (...) {
            env->ReleaseStringUTFChars(path, chars);
            env->ReleaseStringUTFChars(base_name, baseChars);
            jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
            if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, "Unknown native model loading failure");
            return 0;
        }

        env->ReleaseStringUTFChars(path, chars);
        env->ReleaseStringUTFChars(base_name, baseChars);

        return (jlong) (intptr_t) ref.release();
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1create(JNIEnv *env, jclass) {
        ModelRef* ref = new ModelRef();
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1replace_1contents(
        JNIEnv*, jclass, jlong ptr, jlong from_ptr) {
        ModelRef* target = (ModelRef*) (intptr_t) ptr;
        ModelRef* source = (ModelRef*) (intptr_t) from_ptr;
        if (target == nullptr || source == nullptr || target == source) return;
        Model previous(std::move(target->model));
        target->model = std::move(source->model);
        source->model = std::move(previous);
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_models_1merge(JNIEnv* env, jclass, jlongArray ptrsArr) {
        ModelRef* ref = new ModelRef();

        jlong* ptrs = env->GetLongArrayElements(ptrsArr, JNI_FALSE);
        int len = env->GetArrayLength(ptrsArr);
        for (int i = 0; i < len; i++) {
            ModelRef* sRef = (ModelRef*) (intptr_t) ptrs[i];
            for (ModelObject* obj : sRef->model.objects) {
                ref->model.add_object(*obj);
            }
        }
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1objects_1count(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        return model->model.objects.size();
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1object_1name(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size()) {
            return nullptr;
        }
        const ModelObject* object = model->model.objects[i];
        if (object == nullptr) return nullptr;
        std::string name = object->name;
        if (name.empty()) name = object->input_file;
        return name.empty() ? nullptr : env->NewStringUTF(name.c_str());
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1object_1config_1option(
        JNIEnv* env, jclass, jlong ptr, jint object_index, jstring key_value) {
        ModelRef* model = (ModelRef*)(intptr_t)ptr;
        if (model == nullptr || object_index < 0 || static_cast<size_t>(object_index) >= model->model.objects.size() || key_value == nullptr)
            return nullptr;
        const char* key_chars = env->GetStringUTFChars(key_value, nullptr);
        if (key_chars == nullptr) return nullptr;
        const std::string key(key_chars);
        env->ReleaseStringUTFChars(key_value, key_chars);
        PrintObjectConfig object_config;
        PrintRegionConfig region_config;
        const auto& object_keys = object_config.keys_ref();
        const auto& region_keys = region_config.keys_ref();
        if (std::find(region_keys.begin(), region_keys.end(), key) != region_keys.end()) {
            const ModelObject* object = model->model.objects[object_index];
            if (object == nullptr) return nullptr;
            std::optional<std::string> value;
            bool found_model_part = false;
            bool missing_on_model_part = false;
            for (const ModelVolume* volume : object->volumes) {
                if (volume == nullptr || volume->type() != ModelVolumeType::MODEL_PART || volume->mesh().facets_count() < 3) continue;
                found_model_part = true;
                if (!volume->config.has(key)) {
                    missing_on_model_part = true;
                    continue;
                }
                const std::string serialized = volume->config.opt_serialize(key);
                if (!value.has_value()) value = serialized;
                else if (*value != serialized) return env->NewStringUTF("<mixed>");
            }
            if (value.has_value() && missing_on_model_part) return env->NewStringUTF("<mixed>");
            return found_model_part && value.has_value() ? env->NewStringUTF(value->c_str()) : nullptr;
        }
        if (std::find(object_keys.begin(), object_keys.end(), key) == object_keys.end()) return nullptr;
        const ModelObject* object = model->model.objects[object_index];
        if (object == nullptr || !object->config.has(key)) return nullptr;
        const std::string serialized = object->config.opt_serialize(key);
        return env->NewStringUTF(serialized.c_str());
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1set_1object_1config_1options(
        JNIEnv* env, jclass, jlong ptr, jint object_index, jobjectArray keys_array, jobjectArray values_array) {
        ModelRef* model = (ModelRef*)(intptr_t)ptr;
        if (model == nullptr || object_index < 0 || static_cast<size_t>(object_index) >= model->model.objects.size() ||
            keys_array == nullptr || values_array == nullptr) return JNI_FALSE;
        const jsize count = env->GetArrayLength(keys_array);
        if (count <= 0 || count > 32 || env->GetArrayLength(values_array) != count) return JNI_FALSE;
        ModelObject* object = model->model.objects[object_index];
        if (object == nullptr) return JNI_FALSE;

        struct PendingObjectOption {
            std::string key;
            std::string value;
            std::unique_ptr<ConfigOption> option;
        };
        std::vector<PendingObjectOption> pending;
        pending.reserve(static_cast<size_t>(count));
        std::unordered_set<std::string> seen;
        PrintObjectConfig object_config;
        PrintRegionConfig region_config;
        const auto& object_keys = object_config.keys_ref();
        const auto& region_keys = region_config.keys_ref();
        std::vector<ModelVolume*> model_parts;
        for (ModelVolume* volume : object->volumes) {
            if (volume != nullptr && volume->type() == ModelVolumeType::MODEL_PART && volume->mesh().facets_count() >= 3)
                model_parts.push_back(volume);
        }
        for (jsize i = 0; i < count; ++i) {
            auto key_string = static_cast<jstring>(env->GetObjectArrayElement(keys_array, i));
            auto value_string = static_cast<jstring>(env->GetObjectArrayElement(values_array, i));
            if (key_string == nullptr || value_string == nullptr) {
                if (key_string != nullptr) env->DeleteLocalRef(key_string);
                if (value_string != nullptr) env->DeleteLocalRef(value_string);
                return JNI_FALSE;
            }
            const char* key_chars = env->GetStringUTFChars(key_string, nullptr);
            const char* value_chars = env->GetStringUTFChars(value_string, nullptr);
            if (key_chars == nullptr || value_chars == nullptr) {
                if (key_chars != nullptr) env->ReleaseStringUTFChars(key_string, key_chars);
                if (value_chars != nullptr) env->ReleaseStringUTFChars(value_string, value_chars);
                env->DeleteLocalRef(key_string);
                env->DeleteLocalRef(value_string);
                return JNI_FALSE;
            }
            PendingObjectOption item;
            item.key = key_chars;
            item.value = value_chars;
            env->ReleaseStringUTFChars(key_string, key_chars);
            env->ReleaseStringUTFChars(value_string, value_chars);
            env->DeleteLocalRef(key_string);
            env->DeleteLocalRef(value_string);
            const bool is_object_key = std::find(object_keys.begin(), object_keys.end(), item.key) != object_keys.end();
            const bool is_region_key = std::find(region_keys.begin(), region_keys.end(), item.key) != region_keys.end();
            if (item.key.empty() || item.key.size() > 128 || item.value.size() > 256 || !seen.insert(item.key).second ||
                is_object_key == is_region_key || (is_region_key && model_parts.empty())) return JNI_FALSE;
            if (!item.value.empty()) {
                const ConfigOptionDef* definition = print_config_def.get(item.key);
                if (definition == nullptr) return JNI_FALSE;
                item.option.reset(definition->create_default_option());
                if (item.option == nullptr || !item.option->deserialize(item.value)) return JNI_FALSE;
            }
            pending.push_back(std::move(item));
        }

        // Allocate every per-volume option before touching the model so invalid input or
        // allocation failures cannot leave a multipart object only partly updated.
        std::vector<std::vector<std::unique_ptr<ConfigOption>>> volume_options(pending.size());
        for (size_t i = 0; i < pending.size(); ++i) {
            if (std::find(region_keys.begin(), region_keys.end(), pending[i].key) == region_keys.end() || pending[i].value.empty()) continue;
            volume_options[i].reserve(model_parts.size());
            for (size_t part = 0; part < model_parts.size(); ++part)
                volume_options[i].emplace_back(pending[i].option->clone());
        }

        bool changed = false;
        for (size_t i = 0; i < pending.size(); ++i) {
            PendingObjectOption& item = pending[i];
            const bool is_region_key = std::find(region_keys.begin(), region_keys.end(), item.key) != region_keys.end();
            if (!is_region_key) {
                if (item.value.empty()) changed |= object->config.erase(item.key);
                else if (!object->config.has(item.key) || object->config.opt_serialize(item.key) != item.option->serialize()) {
                    object->config.set_key_value(item.key, item.option.release());
                    changed = true;
                }
                continue;
            }
            for (size_t part = 0; part < model_parts.size(); ++part) {
                ModelConfigObject& config = model_parts[part]->config;
                if (item.value.empty()) changed |= config.erase(item.key);
                else if (!config.has(item.key) || config.opt_serialize(item.key) != item.option->serialize()) {
                    config.set_key_value(item.key, volume_options[i][part].release());
                    changed = true;
                }
            }
        }
        return changed ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1object_1part_1config_1option(
        JNIEnv* env, jclass, jlong ptr, jint object_index, jint part_index, jstring key_value) {
        ModelRef* model = (ModelRef*)(intptr_t)ptr;
        if (model == nullptr || object_index < 0 || static_cast<size_t>(object_index) >= model->model.objects.size() ||
            part_index < 0 || key_value == nullptr) return nullptr;
        const char* key_chars = env->GetStringUTFChars(key_value, nullptr);
        if (key_chars == nullptr) return nullptr;
        const std::string key(key_chars);
        env->ReleaseStringUTFChars(key_value, key_chars);
        PrintRegionConfig region_config;
        const auto& keys = region_config.keys_ref();
        if (std::find(keys.begin(), keys.end(), key) == keys.end()) return nullptr;
        const ModelObject* object = model->model.objects[object_index];
        if (object == nullptr) return nullptr;
        jint current_part = 0;
        for (const ModelVolume* volume : object->volumes) {
            if (volume == nullptr || volume->type() != ModelVolumeType::MODEL_PART || volume->mesh().facets_count() < 3) continue;
            if (current_part++ != part_index) continue;
            if (!volume->config.has(key)) return nullptr;
            const std::string serialized = volume->config.opt_serialize(key);
            return env->NewStringUTF(serialized.c_str());
        }
        return nullptr;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1set_1object_1part_1config_1options(
        JNIEnv* env, jclass, jlong ptr, jint object_index, jint part_index, jobjectArray keys_array, jobjectArray values_array) {
        ModelRef* model = (ModelRef*)(intptr_t)ptr;
        if (model == nullptr || object_index < 0 || static_cast<size_t>(object_index) >= model->model.objects.size() ||
            part_index < 0 || keys_array == nullptr || values_array == nullptr) return JNI_FALSE;
        const jsize count = env->GetArrayLength(keys_array);
        if (count <= 0 || count > 32 || env->GetArrayLength(values_array) != count) return JNI_FALSE;
        ModelObject* object = model->model.objects[object_index];
        if (object == nullptr) return JNI_FALSE;
        ModelVolume* target = nullptr;
        jint current_part = 0;
        for (ModelVolume* volume : object->volumes) {
            if (volume == nullptr || volume->type() != ModelVolumeType::MODEL_PART || volume->mesh().facets_count() < 3) continue;
            if (current_part++ == part_index) { target = volume; break; }
        }
        if (target == nullptr) return JNI_FALSE;

        struct PendingPartOption {
            std::string key;
            std::string value;
            std::unique_ptr<ConfigOption> option;
        };
        std::vector<PendingPartOption> pending;
        pending.reserve(static_cast<size_t>(count));
        std::unordered_set<std::string> seen;
        PrintRegionConfig region_config;
        const auto& region_keys = region_config.keys_ref();
        for (jsize i = 0; i < count; ++i) {
            auto key_string = static_cast<jstring>(env->GetObjectArrayElement(keys_array, i));
            auto value_string = static_cast<jstring>(env->GetObjectArrayElement(values_array, i));
            if (key_string == nullptr || value_string == nullptr) {
                if (key_string != nullptr) env->DeleteLocalRef(key_string);
                if (value_string != nullptr) env->DeleteLocalRef(value_string);
                return JNI_FALSE;
            }
            const char* key_chars = env->GetStringUTFChars(key_string, nullptr);
            const char* value_chars = env->GetStringUTFChars(value_string, nullptr);
            if (key_chars == nullptr || value_chars == nullptr) {
                if (key_chars != nullptr) env->ReleaseStringUTFChars(key_string, key_chars);
                if (value_chars != nullptr) env->ReleaseStringUTFChars(value_string, value_chars);
                env->DeleteLocalRef(key_string);
                env->DeleteLocalRef(value_string);
                return JNI_FALSE;
            }
            PendingPartOption item;
            item.key = key_chars;
            item.value = value_chars;
            env->ReleaseStringUTFChars(key_string, key_chars);
            env->ReleaseStringUTFChars(value_string, value_chars);
            env->DeleteLocalRef(key_string);
            env->DeleteLocalRef(value_string);
            if (item.key.empty() || item.key.size() > 128 || item.value.size() > 256 ||
                !seen.insert(item.key).second ||
                std::find(region_keys.begin(), region_keys.end(), item.key) == region_keys.end()) return JNI_FALSE;
            if (!item.value.empty()) {
                const ConfigOptionDef* definition = print_config_def.get(item.key);
                if (definition == nullptr) return JNI_FALSE;
                item.option.reset(definition->create_default_option());
                if (item.option == nullptr || !item.option->deserialize(item.value)) return JNI_FALSE;
            }
            pending.push_back(std::move(item));
        }

        bool changed = false;
        for (PendingPartOption& item : pending) {
            if (item.value.empty()) changed |= target->config.erase(item.key);
            else if (!target->config.has(item.key) || target->config.opt_serialize(item.key) != item.option->serialize()) {
                target->config.set_key_value(item.key, item.option.release());
                changed = true;
            }
        }
        return changed ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1object_1part_1count(JNIEnv*, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size()) return 0;
        const ModelObject* object = model->model.objects[i];
        if (object == nullptr) return 0;
        jint count = 0;
        for (const ModelVolume* volume : object->volumes) {
            if (volume != nullptr && volume->type() == ModelVolumeType::MODEL_PART && volume->mesh().facets_count() >= 3)
                ++count;
        }
        return count;
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1object_1part_1name(
        JNIEnv* env, jclass, jlong ptr, jint object_index, jint part_index) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || static_cast<size_t>(object_index) >= model->model.objects.size() || part_index < 0)
            return nullptr;
        const ModelObject* object = model->model.objects[object_index];
        if (object == nullptr) return nullptr;
        jint current_part = 0;
        for (const ModelVolume* volume : object->volumes) {
            if (volume == nullptr || volume->type() != ModelVolumeType::MODEL_PART || volume->mesh().facets_count() < 3)
                continue;
            if (current_part++ != part_index) continue;
            if (volume->name.empty()) return nullptr;
            return env->NewStringUTF(volume->name.c_str());
        }
        return nullptr;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1modifier_1box(
        JNIEnv*, jclass, jlong ptr, jint object_index, jint type, jdouble x, jdouble y, jdouble z,
        jdouble width, jdouble depth, jdouble height) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || static_cast<size_t>(object_index) >= model->model.objects.size() ||
            type < static_cast<int>(ModelVolumeType::NEGATIVE_VOLUME) || type > static_cast<int>(ModelVolumeType::SUPPORT_ENFORCER) ||
            !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(z) ||
            !std::isfinite(width) || !std::isfinite(depth) || !std::isfinite(height) ||
            std::fabs(x) > 10000.0 || std::fabs(y) > 10000.0 || std::fabs(z) > 10000.0 ||
            width < 0.2 || width > 500.0 || depth < 0.2 || depth > 500.0 || height < 0.2 || height > 500.0)
            return -1;
        ModelObject* object = model->model.objects[object_index];
        if (object == nullptr) return -1;
        TriangleMesh mesh = make_cube(width, depth, height);
        // Orca stores modifier transforms around the volume's local center. Center the mesh at
        // creation time so 3MF load/save does not shift a corner-origin box by half its size.
        mesh.translate(Vec3f(float(-width * 0.5), float(-depth * 0.5), float(-height * 0.5)));
        ModelVolume* volume = object->add_volume(std::move(mesh), static_cast<ModelVolumeType>(type), false);
        if (volume == nullptr) return -1;
        volume->set_offset(Vec3d(x + width * 0.5, y + depth * 0.5, z + height * 0.5));
        volume->name = type == static_cast<int>(ModelVolumeType::NEGATIVE_VOLUME) ? "Negative volume" :
            type == static_cast<int>(ModelVolumeType::PARAMETER_MODIFIER) ? "Parameter modifier" :
            type == static_cast<int>(ModelVolumeType::SUPPORT_BLOCKER) ? "Support blocker" : "Support enforcer";
        return static_cast<jint>(object->volumes.size() - 1);
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1modifier_1volume_1indices(
        JNIEnv* env, jclass, jlong ptr, jint object_index) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        std::vector<jint> indices;
        if (model != nullptr && object_index >= 0 && static_cast<size_t>(object_index) < model->model.objects.size()) {
            const ModelObject* object = model->model.objects[object_index];
            if (object != nullptr) for (size_t i = 0; i < object->volumes.size(); ++i) {
                const ModelVolume* volume = object->volumes[i];
                if (volume != nullptr && volume->type() != ModelVolumeType::MODEL_PART && volume->mesh().facets_count() >= 3)
                    indices.push_back(static_cast<jint>(i));
            }
        }
        jintArray result = env->NewIntArray(static_cast<jsize>(indices.size()));
        if (result != nullptr && !indices.empty()) env->SetIntArrayRegion(result, 0, static_cast<jsize>(indices.size()), indices.data());
        return result;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1modifier_1volume_1box_1state(
        JNIEnv* env, jclass, jlong ptr, jint object_index, jint volume_index) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || volume_index < 0 ||
            static_cast<size_t>(object_index) >= model->model.objects.size()) return nullptr;
        const ModelObject* object = model->model.objects[object_index];
        if (object == nullptr || static_cast<size_t>(volume_index) >= object->volumes.size() || object->volumes[volume_index] == nullptr ||
            object->volumes[volume_index]->type() == ModelVolumeType::MODEL_PART) return nullptr;
        const ModelVolume* volume = object->volumes[volume_index];
        const BoundingBoxf3 bounds = volume->mesh().bounding_box();
        const Vec3d geometry_size = (bounds.max - bounds.min).cast<double>().cwiseProduct(volume->get_scaling_factor()).cwiseAbs();
        const Vec3d center = volume->get_offset();
        const jdouble values[6] = {
            center.x() - geometry_size.x() * 0.5, center.y() - geometry_size.y() * 0.5, center.z() - geometry_size.z() * 0.5,
            geometry_size.x(), geometry_size.y(), geometry_size.z(),
        };
        jdoubleArray result = env->NewDoubleArray(6);
        if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 6, values);
        return result;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1update_1modifier_1box(
        JNIEnv*, jclass, jlong ptr, jint object_index, jint volume_index,
        jdouble x, jdouble y, jdouble z, jdouble width, jdouble depth, jdouble height) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || volume_index < 0 ||
            static_cast<size_t>(object_index) >= model->model.objects.size() ||
            !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(z) ||
            !std::isfinite(width) || !std::isfinite(depth) || !std::isfinite(height) ||
            std::fabs(x) > 10000.0 || std::fabs(y) > 10000.0 || std::fabs(z) > 10000.0 ||
            width < 0.2 || width > 500.0 || depth < 0.2 || depth > 500.0 || height < 0.2 || height > 500.0)
            return JNI_FALSE;
        ModelObject* object = model->model.objects[object_index];
        if (object == nullptr || static_cast<size_t>(volume_index) >= object->volumes.size() || object->volumes[volume_index] == nullptr)
            return JNI_FALSE;
        ModelVolume* volume = object->volumes[volume_index];
        if (volume->type() == ModelVolumeType::MODEL_PART) return JNI_FALSE;
        const BoundingBoxf3 bounds = volume->mesh().bounding_box();
        const Vec3d base_size = (bounds.max - bounds.min).cast<double>();
        if (!base_size.allFinite() || (base_size.array() <= 1e-9).any()) return JNI_FALSE;
        volume->set_offset(Vec3d(x + width * 0.5, y + depth * 0.5, z + height * 0.5));
        volume->set_scaling_factor(Vec3d(width / base_size.x(), depth / base_size.y(), height / base_size.z()));
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1delete_1volume(
        JNIEnv*, jclass, jlong ptr, jint object_index, jint volume_index) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || static_cast<size_t>(object_index) >= model->model.objects.size() || volume_index < 0)
            return JNI_FALSE;
        ModelObject* object = model->model.objects[object_index];
        if (object == nullptr || static_cast<size_t>(volume_index) >= object->volumes.size() ||
            object->volumes[volume_index] == nullptr || object->volumes[volume_index]->type() == ModelVolumeType::MODEL_PART)
            return JNI_FALSE;
        object->delete_volume(volume_index);
        return JNI_TRUE;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1volume_1type(
        JNIEnv*, jclass, jlong ptr, jint object_index, jint volume_index) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || volume_index < 0 ||
            static_cast<size_t>(object_index) >= model->model.objects.size()) return -1;
        const ModelObject* object = model->model.objects[object_index];
        if (object == nullptr || static_cast<size_t>(volume_index) >= object->volumes.size() || object->volumes[volume_index] == nullptr) return -1;
        return static_cast<jint>(object->volumes[volume_index]->type());
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1object_1part_1extruder(
        JNIEnv*, jclass, jlong ptr, jint object_index, jint part_index) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || static_cast<size_t>(object_index) >= model->model.objects.size() || part_index < 0)
            return -1;
        const ModelObject* object = model->model.objects[object_index];
        if (object == nullptr) return -1;
        jint current_part = 0;
        for (const ModelVolume* volume : object->volumes) {
            if (volume == nullptr || volume->type() != ModelVolumeType::MODEL_PART || volume->mesh().facets_count() < 3) continue;
            if (current_part++ == part_index) return std::max(1, volume->extruder_id());
        }
        return -1;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1set_1object_1part_1extruder(
        JNIEnv*, jclass, jlong ptr, jint object_index, jint part_index, jint extruder) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || static_cast<size_t>(object_index) >= model->model.objects.size() ||
            part_index < 0 || extruder < 1 || extruder > 16)
            return JNI_FALSE;
        ModelObject* object = model->model.objects[object_index];
        if (object == nullptr) return JNI_FALSE;
        jint current_part = 0;
        for (ModelVolume* volume : object->volumes) {
            if (volume == nullptr || volume->type() != ModelVolumeType::MODEL_PART || volume->mesh().facets_count() < 3)
                continue;
            if (current_part++ != part_index) continue;
            volume->config.set("extruder", static_cast<int>(extruder));
            // Explicit part-slot choice replaces imported facet-level material assignments
            // for this volume only. Other volumes and the parent object are untouched.
            volume->mmu_segmentation_facets.reset();
            return JNI_TRUE;
        }
        return JNI_FALSE;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1transform_1object_1part(
        JNIEnv*, jclass, jlong ptr, jint object_index, jint part_index,
        jdouble dx, jdouble dy, jdouble dz, jdouble rotation_x_degrees, jdouble rotation_y_degrees,
        jdouble rotation_z_degrees, jdouble scale_x, jdouble scale_y, jdouble scale_z) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || static_cast<size_t>(object_index) >= model->model.objects.size() ||
            part_index < 0 || !std::isfinite(dx) || !std::isfinite(dy) || !std::isfinite(dz) ||
            std::fabs(dx) > 10000.0 || std::fabs(dy) > 10000.0 || std::fabs(dz) > 10000.0 ||
            !std::isfinite(rotation_x_degrees) || !std::isfinite(rotation_y_degrees) || !std::isfinite(rotation_z_degrees) ||
            std::fabs(rotation_x_degrees) > 36000.0 || std::fabs(rotation_y_degrees) > 36000.0 || std::fabs(rotation_z_degrees) > 36000.0 ||
            !std::isfinite(scale_x) || !std::isfinite(scale_y) || !std::isfinite(scale_z) ||
            scale_x < 0.01 || scale_x > 10.0 || scale_y < 0.01 || scale_y > 10.0 || scale_z < 0.01 || scale_z > 10.0)
            return JNI_FALSE;

        ModelObject* object = model->model.objects[object_index];
        if (object == nullptr) return JNI_FALSE;
        jint current_part = 0;
        ModelVolume* target = nullptr;
        for (ModelVolume* volume : object->volumes) {
            if (volume == nullptr || volume->type() != ModelVolumeType::MODEL_PART || volume->mesh().facets_count() < 3)
                continue;
            if (current_part++ == part_index) {
                target = volume;
                break;
            }
        }
        if (target == nullptr) return JNI_FALSE;

        const BoundingBoxf3 bounds = target->mesh().bounding_box();
        if (target->mesh().empty() || !bounds.min.allFinite() || !bounds.max.allFinite()) return JNI_FALSE;
        const Vec3d center = bounds.center().cast<double>();
        if (!center.allFinite()) return JNI_FALSE;

        // Scale in the part's local axes around its own mesh center. The parent object and its
        // instances remain intact, so the edited component stays grouped with its siblings.
        Transform3d local_scale = Transform3d::Identity();
        local_scale.translate(center);
        local_scale.scale(Vec3d(scale_x, scale_y, scale_z));
        local_scale.translate(-center);
        const double radians = M_PI / 180.0;
        Transform3d local_rotation = Transform3d::Identity();
        local_rotation.translate(center);
        local_rotation.rotate(Eigen::AngleAxisd(rotation_z_degrees * radians, Vec3d::UnitZ()) *
                              Eigen::AngleAxisd(rotation_y_degrees * radians, Vec3d::UnitY()) *
                              Eigen::AngleAxisd(rotation_x_degrees * radians, Vec3d::UnitX()));
        local_rotation.translate(-center);
        Transform3d translation = Transform3d::Identity();
        translation.translate(Vec3d(dx, dy, dz));
        const Transform3d updated = translation * target->get_matrix() * local_rotation * local_scale;
        const double determinant = updated.linear().determinant();
        if (!updated.matrix().allFinite() || !std::isfinite(determinant) || std::fabs(determinant) <= 1e-12)
            return JNI_FALSE;

        target->set_transformation(updated);
        object->invalidate_bounding_box();
        return JNI_TRUE;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1triangle_1count(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size()) return 0;
        size_t count = 0;
        for (const ModelVolume* volume : model->model.objects[i]->volumes) {
            if (volume != nullptr && volume->is_model_part()) count += volume->mesh().facets_count();
        }
        return static_cast<jint>(std::min(count, static_cast<size_t>(INT32_MAX)));
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1simplify(JNIEnv* env, jclass, jlong ptr, jint i, jfloat keepRatio) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size() ||
            !std::isfinite(keepRatio) || keepRatio <= 0.0f || keepRatio > 1.0f) return 0;
        ModelObject* object = model->model.objects[i];
        if (object == nullptr) return 0;
        const float ratio = std::max(0.01f, std::min(0.99f, keepRatio));
        size_t result = 0;
        for (ModelVolume* volume : object->volumes) {
            if (volume == nullptr || !volume->is_model_part()) continue;
            indexed_triangle_set mesh = volume->mesh().its;
            if (mesh.indices.size() > 4) {
                const uint32_t target = std::max<uint32_t>(4, static_cast<uint32_t>(mesh.indices.size() * ratio));
                auto painting = volume->save_painting();
                its_quadric_edge_collapse(mesh, target);
                volume->set_mesh(std::move(mesh));
                volume->calculate_convex_hull();
                volume->restore_painting(painting);
                volume->set_new_unique_id();
            }
            result += volume->mesh().facets_count();
        }
        object->invalidate_bounding_box();
        return static_cast<jint>(std::min(result, static_cast<size_t>(INT32_MAX)));
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1smooth_1mesh(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(ptr));
        if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size()) return 0;
        ModelObject* object = model->model.objects[i];
        if (object == nullptr) return 0;

        struct PreparedVolume {
            ModelVolume* volume;
            TriangleMesh smoothed;
            TriangleMesh unsmoothed;
            std::optional<TriangleSelector::SavedPainting> painting;
        };
        std::vector<PreparedVolume> prepared;
        try {
            constexpr size_t maxInputTrianglesPerObject = 80000;
            constexpr size_t maxModelTrianglesForUndo = (128ULL * 1024ULL * 1024ULL) / (64ULL * 6ULL);
            size_t inputTriangleCount = 0;
            size_t modelTriangleCount = 0;
            for (const ModelObject* modelObject : model->model.objects) {
                if (modelObject == nullptr) continue;
                size_t objectTriangles = 0;
                for (const ModelVolume* volume : modelObject->volumes) {
                    if (volume == nullptr || !volume->is_model_part()) continue;
                    const size_t count = volume->mesh().its.indices.size();
                    if (count == 0 || count > std::numeric_limits<size_t>::max() - objectTriangles) return 0;
                    objectTriangles += count;
                }
                if (modelTriangleCount > std::numeric_limits<size_t>::max() - objectTriangles) return 0;
                modelTriangleCount += objectTriangles;
                if (modelObject == object) inputTriangleCount = objectTriangles;
            }
            if (inputTriangleCount == 0 || inputTriangleCount > maxInputTrianglesPerObject ||
                modelTriangleCount > maxModelTrianglesForUndo ||
                inputTriangleCount > (maxModelTrianglesForUndo - modelTriangleCount) / 3) return 0;
            prepared.reserve(object->volumes.size());
            for (ModelVolume* volume : object->volumes) {
                if (volume == nullptr || !volume->is_model_part()) continue;
                auto painting = volume->save_painting();
                TriangleMesh unsmoothed;
                bool ok = false;
                TriangleMesh smoothed = TriangleMeshDeal::smooth_triangle_mesh(
                    volume->mesh(), ok, painting ? &unsmoothed : nullptr);
                if (!ok || smoothed.empty() || (painting && unsmoothed.empty())) return 0;
                prepared.push_back({volume, std::move(smoothed), std::move(unsmoothed), std::move(painting)});
            }
            if (prepared.empty()) return 0;

            size_t result = 0;
            for (PreparedVolume& item : prepared) {
                if (item.painting) {
                    item.volume->set_mesh(std::move(item.unsmoothed));
                    item.volume->restore_painting(item.painting);
                }
                item.volume->set_mesh(std::move(item.smoothed));
                item.volume->calculate_convex_hull();
                item.volume->set_new_unique_id();
                result += item.volume->mesh().facets_count();
            }
            object->invalidate_bounding_box();
            return static_cast<jint>(std::min(result, static_cast<size_t>(INT32_MAX)));
        } catch (const std::exception& error) {
            __android_log_print(ANDROID_LOG_ERROR, "SB_Native", "Mesh smoothing failed: %s", error.what());
            return 0;
        } catch (...) {
            __android_log_print(ANDROID_LOG_ERROR, "SB_Native", "Mesh smoothing failed with an unknown native error");
            return 0;
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1object_1from_1another(JNIEnv* env, jclass, jlong ptr, jlong fromPtr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelRef* from = (ModelRef *) (intptr_t) fromPtr;
        if (model == nullptr || from == nullptr || i < 0 || static_cast<size_t>(i) >= from->model.objects.size()) return;
        ModelObject* source = from->model.objects[i];
        if (source == nullptr) return;
        model->model.add_object(*source);
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1duplicate_1instance(
        JNIEnv*, jclass, jlong ptr, jint object_index, jint source_instance_index,
        jdouble dx, jdouble dy, jdouble dz) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        if (ref == nullptr || object_index < 0 || object_index >= static_cast<jint>(ref->model.objects.size()) ||
            !std::isfinite(dx) || !std::isfinite(dy) || !std::isfinite(dz)) return -1;
        ModelObject* object = ref->model.objects[object_index];
        if (object == nullptr || source_instance_index < 0 ||
            source_instance_index >= static_cast<jint>(object->instances.size())) return -1;
        const ModelInstance* source = object->instances[source_instance_index];
        if (source == nullptr) return -1;

        ModelInstance* duplicate = object->add_instance(*source);
        duplicate->set_offset(source->get_offset() + Vec3d(dx, dy, dz));
        object->invalidate_bounding_box();
        return static_cast<jint>(object->instances.size() - 1);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1delete_1object(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || i >= static_cast<jint>(model->model.objects.size()) ||
            model->model.objects[i] == nullptr)
            return;
        model->model.delete_object(i);
    }

    enum class ModelVectorProperty { Translation, Scale, Mirror, Rotation };

    static jdoubleArray model_object_vector(JNIEnv* env, jlong ptr, jint object_index, ModelVectorProperty property) {
        Vec3d value = Vec3d::Zero();
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model != nullptr && object_index >= 0 && static_cast<size_t>(object_index) < model->model.objects.size()) {
            ModelObject* object = model->model.objects[object_index];
            if (object != nullptr) {
                if (property == ModelVectorProperty::Rotation) {
                    if (!object->volumes.empty() && object->volumes[0] != nullptr)
                        value = object->volumes[0]->get_rotation();
                } else if (property == ModelVectorProperty::Mirror) {
                    if (!object->volumes.empty() && object->volumes[0] != nullptr)
                        value = object->volumes[0]->get_mirror();
                } else if (property == ModelVectorProperty::Scale) {
                    if (!object->volumes.empty() && object->volumes[0] != nullptr)
                        value = object->volumes[0]->get_scaling_factor();
                } else if (property == ModelVectorProperty::Translation &&
                           std::any_of(object->volumes.begin(), object->volumes.end(),
                                       [](const ModelVolume* volume) { return volume != nullptr; })) {
                    value = object->bounding_box_exact().center();
                }
            }
        }
        jdoubleArray arr = env->NewDoubleArray(3);
        if (arr != nullptr) env->SetDoubleArrayRegion(arr, 0, 3, value.data());
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1rotation(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        return model_object_vector(env, ptr, object_index, ModelVectorProperty::Rotation);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1mirror(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        return model_object_vector(env, ptr, object_index, ModelVectorProperty::Mirror);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1mirror(JNIEnv*, jclass, jlong ptr, jint object_index, jint axis) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || object_index >= static_cast<jint>(model->model.objects.size()) || axis < X || axis > Z)
            return;
        ModelObject* object = model->model.objects[object_index];
        if (object == nullptr || object->instances.empty()) return;

        // Preserve the selected object's world-space center when reflecting its local volumes.
        const Vec3d center_before = object->bounding_box_exact().center();
        for (ModelVolume* volume : object->volumes) {
            if (volume == nullptr) continue;
            Vec3d mirror = volume->get_mirror();
            mirror[axis] *= -1.0;
            volume->set_mirror(mirror);
        }
        object->invalidate_bounding_box();
        const Vec3d center_after = object->bounding_box_exact().center();
        object->translate_instances(center_before - center_after);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1scale(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        return model_object_vector(env, ptr, object_index, ModelVectorProperty::Scale);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1translation(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        return model_object_vector(env, ptr, object_index, ModelVectorProperty::Translation);
    }

    static bool is_unprocessed_cut_connector(const ModelVolume* volume) {
        return volume != nullptr && volume->cut_info.is_connector && !volume->cut_info.is_processed;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1cut_1connector_1count(JNIEnv*, jclass, jlong ptr, jint objIdx) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || objIdx < 0 || static_cast<size_t>(objIdx) >= model->model.objects.size()) return 0;
        ModelObject* object = model->model.objects[objIdx];
        if (object == nullptr) return 0;
        int count = 0;
        for (const ModelVolume* volume : object->volumes) {
            if (is_unprocessed_cut_connector(volume)) ++count;
        }
        return count;
    }

    static CutConnectorType connector_type_from_int(jint type) {
        switch (type) {
            case 1: return CutConnectorType::Dowel;
            case 2: return CutConnectorType::Snap;
            case 0:
            default: return CutConnectorType::Plug;
        }
    }

    static void add_cut_connector_volume(ModelObject* object, const Vec3d& pos, float radius, float height, jint type, double rotX, double rotY) {
        if (object == nullptr || !pos.allFinite() || !std::isfinite(radius) || !std::isfinite(height) ||
            !std::isfinite(rotX) || !std::isfinite(rotY)) return;
        radius = std::max(radius, 0.1f);
        height = std::max(height, 0.1f);

        CutConnectorType connector_type = connector_type_from_int(type);
        TriangleMesh mesh = make_cylinder(1.0, 1.0, PI / 18.0);
        ModelVolume* volume = object->add_volume(std::move(mesh), ModelVolumeType::NEGATIVE_VOLUME, false);
        if (volume == nullptr) return;

        Transform3d rotation_m = Transform3d::Identity();
        rotation_m.rotate(Eigen::AngleAxisd(rotY, Vec3d::UnitY()));
        rotation_m.rotate(Eigen::AngleAxisd(rotX, Vec3d::UnitX()));
        volume->set_transformation(rotation_m);
        volume->set_offset(pos);
        volume->set_scaling_factor(Vec3d(radius, radius, height));
        volume->cut_info = ModelVolume::CutInfo(connector_type, 0.05f, 0.10f, false);
        volume->name = connector_type == CutConnectorType::Dowel ? "Cut Dowel" : (connector_type == CutConnectorType::Snap ? "Cut Snap" : "Cut Plug");
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1connector(JNIEnv* env, jclass, jlong ptr, jint objIdx, jdouble x, jdouble y, jdouble z, jfloat radius, jfloat height, jint type) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || objIdx < 0 || objIdx >= model->model.objects.size()) return;
        add_cut_connector_volume(model->model.objects[objIdx], Vec3d(x, y, z), radius, height, type, 0.0, 0.0);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1connector_1on_1plane(JNIEnv* env, jclass, jlong ptr, jint objIdx, jdouble x, jdouble y, jdouble z, jfloat radius, jfloat height, jint type, jdouble rotX, jdouble rotY) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || objIdx < 0 || objIdx >= model->model.objects.size()) return;
        add_cut_connector_volume(model->model.objects[objIdx], Vec3d(x, y, z), radius, height, type, rotX, rotY);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1remove_1connector(JNIEnv* env, jclass, jlong ptr, jint objIdx, jint connIdx) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || objIdx < 0 || static_cast<size_t>(objIdx) >= model->model.objects.size() || connIdx < 0) return;
        ModelObject* object = model->model.objects[objIdx];
        if (object == nullptr) return;
        int connector_counter = 0;
        for (int volume_idx = 0; volume_idx < object->volumes.size(); ++volume_idx) {
            if (!is_unprocessed_cut_connector(object->volumes[volume_idx])) continue;
            if (connector_counter == connIdx) {
                object->delete_volume(volume_idx);
                return;
            }
            ++connector_counter;
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1clear_1connectors(JNIEnv* env, jclass, jlong ptr, jint objIdx) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || objIdx < 0 || static_cast<size_t>(objIdx) >= model->model.objects.size()) return;
        ModelObject* object = model->model.objects[objIdx];
        if (object == nullptr) return;
        for (int volume_idx = int(object->volumes.size()) - 1; volume_idx >= 0; --volume_idx) {
            if (is_unprocessed_cut_connector(object->volumes[volume_idx])) {
                object->delete_volume(volume_idx);
            }
        }
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1cut(JNIEnv* env, jclass, jlong ptr, jint i, jdouble zHeight, jdouble rotX, jdouble rotY, jboolean keepUpper, jboolean keepLower) {
        ModelRef* modelRef = (ModelRef *) (intptr_t) ptr;
        if (modelRef == nullptr) {
            LOGD("Cut failed: null model");
            return false;
        }
        Model& model = modelRef->model;
        if (i < 0 || static_cast<size_t>(i) >= model.objects.size() || model.objects[i] == nullptr ||
            !std::isfinite(zHeight) || !std::isfinite(rotX) || !std::isfinite(rotY) || (!keepUpper && !keepLower)) {
            LOGD("Cut failed: invalid model or index %d", i);
            return false;
        }
        
        auto object = model.objects[i];
        auto bbox = object->bounding_box_exact();
        LOGD("Cut invoked: index=%d zHeight=%f rotX=%f rotY=%f. Object bounds Z: min=%f, max=%f", i, zHeight, rotX, rotY, bbox.min.z(), bbox.max.z());
        
        Transform3d cut_matrix = Transform3d::Identity();
        cut_matrix.translate(Vec3d(0.0, 0.0, zHeight));
        cut_matrix.rotate(Eigen::AngleAxisd(rotY, Vec3d::UnitY()));
        cut_matrix.rotate(Eigen::AngleAxisd(rotX, Vec3d::UnitX()));
        
        ModelObjectCutAttributes attributes = ModelObjectCutAttribute::InvalidateCutInfo | ModelObjectCutAttribute::CreateDowels;
        if (keepUpper) attributes = attributes | ModelObjectCutAttribute::KeepUpper;
        if (keepLower) attributes = attributes | ModelObjectCutAttribute::KeepLower;
        
        Cut cut(object, 0, cut_matrix, attributes);
        const auto& cut_objects = cut.perform_with_plane();
        
        LOGD("Cut returned %zu objects", cut_objects.size());
        
        if (cut_objects.empty()) {
            return false;
        }
        
        bool isFirst = true;
        for (auto cut_obj : cut_objects) {
            const double drop_to_bed = -cut_obj->bounding_box_exact().min.z();
            Vec3d separation(0.0, 0.0, 0.0);
            if (isFirst && cut_objects.size() > 1) {
                separation.x() = 20.0;
                separation.y() = 20.0;
                isFirst = false;
            }
            // Separate the two pieces in XY, but keep both printable: the upper half
            // otherwise floats above the bed and Orca rejects its empty first layer.
            separation.z() = drop_to_bed;
            cut_obj->translate(separation);
            model.add_object(*cut_obj);
        }
        
        model.delete_object(i);
        
        return true;
    }

    // Split the selected object into separate objects (by connected mesh parts for a single-volume
    // object, or by volume for a multi-volume assembly). split() appends the new objects to the
    // model and preserves painting; we then drop the original. Returns the number of pieces, or 0
    // if there was nothing to split.
    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1split(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* modelRef = (ModelRef*) (intptr_t) ptr;
        if (modelRef == nullptr) return 0;
        Model& model = modelRef->model;
        if (i < 0 || static_cast<size_t>(i) >= model.objects.size() || model.objects[i] == nullptr) return 0;
        size_t before = model.objects.size();
        ModelObjectPtrs new_objects;
        model.objects[i]->split(&new_objects, true);
        if (new_objects.size() <= 1) {
            // Nothing meaningful to split: remove anything split() appended, keep the original.
            while (model.objects.size() > before)
                model.delete_object(model.objects.size() - 1);
            return 0;
        }
        model.delete_object(i);
        return (jint) new_objects.size();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1translate(JNIEnv* env, jclass, jlong ptr, jint i, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || i >= static_cast<jint>(model->model.objects.size()) ||
            model->model.objects[i] == nullptr || !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(z))
            return;
        // UI movement deltas are expressed in build-plate/world coordinates. Moving the
        // object's volumes instead would apply the instance rotation/scale to the delta,
        // so the rendered mesh could land away from the position shown by its bounds.
        model->model.objects[i]->translate_instances(Vec3d(x, y, z));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1ensure_1on_1bed(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || i >= static_cast<jint>(model->model.objects.size()) ||
            model->model.objects[i] == nullptr)
            return;
        model->model.objects[i]->ensure_on_bed(false);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1scale(JNIEnv* env, jclass, jlong ptr, jint i, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size() ||
            model->model.objects[i] == nullptr || !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(z)) return;
        ModelObject* object = model->model.objects[i];
        const ModelVolumePtrs& volumes = object->volumes;
        if (volumes.empty() || volumes.front() == nullptr ||
            std::any_of(volumes.begin(), volumes.end(), [](const ModelVolume* volume) { return volume == nullptr; })) return;

        // The UI's X/Y/Z values describe the visible build-plate axes. Applying a diagonal
        // scale to each volume's local scale vector makes a rotated object change a different
        // visible axis (for example, Y changes the world X bounds after a 90-degree turn).
        // Keep the native scale factors in sync with the UI, but bake the conjugated affine
        // correction into each volume mesh so the assembled object is scaled in plate space.
        const Vec3d target(x, y, z);
        const Vec3d reference = volumes.front()->get_scaling_factor();
        if (!target.allFinite() || !reference.allFinite() ||
            (target.array() <= 0.0).any() || (reference.array() <= 0.0).any()) return;
        const Vec3d ratio = target.cwiseQuotient(reference);
        if (!ratio.allFinite()) return;
        const Vec3d center = object->bounding_box_exact().center().cast<double>();
        if (!center.allFinite()) return;
        Transform3d plate_scale = Transform3d::Identity();
        plate_scale.linear() = ratio.asDiagonal();
        plate_scale.translation() = center - ratio.cwiseProduct(center);
        const Transform3d instance_matrix = object->instances.empty()
            ? Transform3d::Identity()
            : object->instances.front()->get_matrix();

        struct ScaleUpdate {
            ModelVolume* volume;
            Vec3d scale;
            Transform3d correction;
            Vec3d offset;
            bool transform_mesh;
        };
        std::vector<ScaleUpdate> updates;
        updates.reserve(volumes.size());
        for (ModelVolume* volume : volumes) {
            const Vec3d old_scale = volume->get_scaling_factor();
            const Transform3d old_matrix = volume->get_matrix();
            Vec3d new_scale = volume->get_scaling_factor().cwiseProduct(ratio);
            if (!new_scale.allFinite() || (new_scale.array() <= 0.0).any()) return;

            // Read the matrix that Slic3r will use for the target scale without leaving any
            // volume partially modified if a later volume fails validation.
            volume->set_scaling_factor(new_scale);
            const Transform3d new_matrix = volume->get_matrix();
            volume->set_scaling_factor(old_scale);
            if (!new_matrix.matrix().allFinite() || std::abs(new_matrix.linear().determinant()) < 1e-12)
                return;
            const Transform3d old_placement = instance_matrix * old_matrix;
            const Transform3d new_placement = instance_matrix * new_matrix;
            const Transform3d correction = new_placement.inverse() * plate_scale * old_placement;
            if (!correction.matrix().allFinite() || std::abs(correction.linear().determinant()) < 1e-12)
                return;
            const bool transform_mesh = !correction.linear().isApprox(Matrix3d::Identity(), 1e-10);
            const Vec3d new_offset = transform_mesh
                ? volume->get_offset()
                : volume->get_offset() + new_matrix.linear() * correction.translation();
            if (!new_offset.allFinite()) return;
            updates.push_back({volume, new_scale, correction, new_offset, transform_mesh});
        }
        for (const ScaleUpdate& update : updates) {
            update.volume->set_scaling_factor(update.scale);
            if (update.transform_mesh) {
                TriangleMesh corrected_mesh = update.volume->mesh();
                corrected_mesh.transform(update.correction, false);
                update.volume->set_mesh(std::move(corrected_mesh));
                update.volume->calculate_convex_hull();
                update.volume->invalidate_convex_hull_2d();
            } else {
                update.volume->set_offset(update.offset);
            }
        }
        object->invalidate_bounding_box();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1is_1left_1handed(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size()) return false;
        ModelObject* object = model->model.objects[i];
        if (object == nullptr || object->volumes.empty() || object->volumes.front() == nullptr) return false;
        return object->volumes.front()->is_left_handed();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1rotate(JNIEnv* env, jclass, jlong ptr, jint i, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size() ||
            model->model.objects[i] == nullptr || !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(z)) return;
        ModelObject* object = model->model.objects[i];
        if (object->volumes.empty() || std::any_of(object->volumes.begin(), object->volumes.end(),
                [](const ModelVolume* volume) { return volume == nullptr; })) return;
        Vec3d vec(x, y, z);
        ModelVolumePtrs& ptrs = object->volumes;
        for (int i = 0, c = ptrs.size(); i < c; i++) {
            Vec3d current_rotation = ptrs[i]->get_rotation();

            Eigen::Quaterniond q_current =
                    Eigen::AngleAxisd(current_rotation[2], Eigen::Vector3d::UnitZ()) *
                    Eigen::AngleAxisd(current_rotation[1], Eigen::Vector3d::UnitY()) *
                    Eigen::AngleAxisd(current_rotation[0], Eigen::Vector3d::UnitX());

            Eigen::Quaterniond q_delta =
                    Eigen::AngleAxisd(vec[0], Eigen::Vector3d::UnitX()) *
                    Eigen::AngleAxisd(vec[1], Eigen::Vector3d::UnitY()) *
                    Eigen::AngleAxisd(vec[2], Eigen::Vector3d::UnitZ());

            Eigen::Quaterniond q_result = q_delta * q_current;
            Eigen::Vector3d new_rotation = q_result.toRotationMatrix().eulerAngles(2, 1, 0);
            ptrs[i]->set_rotation(Vec3d(new_rotation[2], new_rotation[1], new_rotation[0]));
        }
        object->invalidate_bounding_box();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1reset_1rotation(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        if (model == nullptr || i < 0 || i >= model->model.objects.size()) return;
        ModelObject* object = model->model.objects[i];
        for (ModelVolume* volume : object->volumes)
            volume->set_rotation(Vec3d::Zero());
        object->invalidate_bounding_box();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1flatten_1rotate(JNIEnv* env, jclass, jlong ptr, jint i, jlong surface_ptr) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        GLModelRef* surface = (GLModelRef*) (intptr_t) surface_ptr;
        if (model == nullptr || surface == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size()) return;
        ModelObject* object = model->model.objects[i];
        const Vec3d& surface_normal = surface->flatten_normal;
        const double normal_length = surface_normal.norm();
        if (object == nullptr || object->volumes.empty() || !surface_normal.allFinite() ||
            !std::isfinite(normal_length) || normal_length <= 1e-9 ||
            std::any_of(object->volumes.begin(), object->volumes.end(),
                [](const ModelVolume* volume) { return volume == nullptr; })) return;
        const Vec3d normal = surface_normal / normal_length;
        ModelVolumePtrs& ptrs = object->volumes;
        for (int i = 0, c = ptrs.size(); i < c; i++) {
            auto vol = ptrs[i];
            const Geometry::Transformation& old_transform = vol->get_transformation();
            const Vec3d tnormal = normal;
            const Transform3d rotation_matrix = Transform3d(Eigen::Quaterniond().setFromTwoVectors(tnormal, -Vec3d::UnitZ()));
            vol->set_transformation(old_transform.get_offset_matrix() * rotation_matrix * old_transform.get_matrix_no_offset());
        }
        object->invalidate_bounding_box();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1translate_1global(JNIEnv* env, jclass, jlong ptr, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        model->model.translate(x, y, z);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1approx(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.objects[i]->bounding_box_approx().min.x();
        elements[1] = ref->model.objects[i]->bounding_box_approx().min.y();
        elements[2] = ref->model.objects[i]->bounding_box_approx().min.z();
        elements[3] = ref->model.objects[i]->bounding_box_approx().max.x();
        elements[4] = ref->model.objects[i]->bounding_box_approx().max.y();
        elements[5] = ref->model.objects[i]->bounding_box_approx().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1exact(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.objects[i]->bounding_box_exact().min.x();
        elements[1] = ref->model.objects[i]->bounding_box_exact().min.y();
        elements[2] = ref->model.objects[i]->bounding_box_exact().min.z();
        elements[3] = ref->model.objects[i]->bounding_box_exact().max.x();
        elements[4] = ref->model.objects[i]->bounding_box_exact().max.y();
        elements[5] = ref->model.objects[i]->bounding_box_exact().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jdouble JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1minimum_1projection(
            JNIEnv* env, jclass, jlong ptr, jintArray object_indices, jdoubleArray direction_array) {
        const double invalid = std::numeric_limits<double>::quiet_NaN();
        if (ptr == 0 || object_indices == nullptr || direction_array == nullptr ||
            env->GetArrayLength(direction_array) != 3) return invalid;
        const jsize count = env->GetArrayLength(object_indices);
        if (count <= 0) return invalid;
        std::vector<jint> indices(static_cast<size_t>(count));
        jdouble direction_values[3];
        env->GetIntArrayRegion(object_indices, 0, count, indices.data());
        env->GetDoubleArrayRegion(direction_array, 0, 3, direction_values);
        if (env->ExceptionCheck()) return invalid;
        const Vec3d direction(direction_values[0], direction_values[1], direction_values[2]);
        const double direction_length = direction.norm();
        if (!std::isfinite(direction_length) || direction_length <= 1e-9) return invalid;
        const Vec3d unit_direction = direction / direction_length;

        const ModelRef* ref = reinterpret_cast<const ModelRef*>(static_cast<intptr_t>(ptr));
        double minimum_projection = std::numeric_limits<double>::infinity();
        bool found_vertex = false;
        for (jint index : indices) {
            if (index < 0 || static_cast<size_t>(index) >= ref->model.objects.size()) return invalid;
            const ModelObject* object = ref->model.objects[index];
            for (const ModelVolume* volume : object->volumes) {
                if (!volume->is_model_part()) continue;
                const indexed_triangle_set& mesh = volume->mesh().its;
                for (const ModelInstance* instance : object->instances) {
                    const Transform3d to_world = instance->get_matrix() * volume->get_matrix();
                    for (const Vec3f& vertex : mesh.vertices) {
                        const Vec3d world = to_world * vertex.cast<double>();
                        const double projection = world.dot(unit_direction);
                        if (!std::isfinite(projection)) return invalid;
                        minimum_projection = std::min(minimum_projection, projection);
                        found_vertex = true;
                    }
                }
            }
        }
        return found_vertex ? minimum_projection : invalid;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1projection_1range(
            JNIEnv* env, jclass, jlong ptr, jintArray object_indices, jdoubleArray direction_array) {
        if (ptr == 0 || object_indices == nullptr || direction_array == nullptr ||
            env->GetArrayLength(direction_array) != 3) return nullptr;
        const jsize count = env->GetArrayLength(object_indices);
        if (count <= 0) return nullptr;
        std::vector<jint> indices(static_cast<size_t>(count));
        jdouble direction_values[3];
        env->GetIntArrayRegion(object_indices, 0, count, indices.data());
        env->GetDoubleArrayRegion(direction_array, 0, 3, direction_values);
        if (env->ExceptionCheck()) return nullptr;
        const Vec3d direction(direction_values[0], direction_values[1], direction_values[2]);
        const double direction_length = direction.norm();
        if (!std::isfinite(direction_length) || direction_length <= 1e-9) return nullptr;
        const Vec3d unit_direction = direction / direction_length;

        const ModelRef* ref = reinterpret_cast<const ModelRef*>(static_cast<intptr_t>(ptr));
        double minimum_projection = std::numeric_limits<double>::infinity();
        double maximum_projection = -std::numeric_limits<double>::infinity();
        bool found_vertex = false;
        for (jint index : indices) {
            if (index < 0 || static_cast<size_t>(index) >= ref->model.objects.size()) return nullptr;
            const ModelObject* object = ref->model.objects[index];
            for (const ModelVolume* volume : object->volumes) {
                if (!volume->is_model_part()) continue;
                const indexed_triangle_set& mesh = volume->mesh().its;
                for (const ModelInstance* instance : object->instances) {
                    const Transform3d to_world = instance->get_matrix() * volume->get_matrix();
                    for (const Vec3f& vertex : mesh.vertices) {
                        const double projection = (to_world * vertex.cast<double>()).dot(unit_direction);
                        if (!std::isfinite(projection)) return nullptr;
                        minimum_projection = std::min(minimum_projection, projection);
                        maximum_projection = std::max(maximum_projection, projection);
                        found_vertex = true;
                    }
                }
            }
        }
        if (!found_vertex) return nullptr;
        const jdouble values[2] { minimum_projection, maximum_projection };
        jdoubleArray result = env->NewDoubleArray(2);
        if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 2, values);
        return result;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1approx_1global(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.bounding_box_approx().min.x();
        elements[1] = ref->model.bounding_box_approx().min.y();
        elements[2] = ref->model.bounding_box_approx().min.z();
        elements[3] = ref->model.bounding_box_approx().max.x();
        elements[4] = ref->model.bounding_box_approx().max.y();
        elements[5] = ref->model.bounding_box_approx().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1exact_1global(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.bounding_box_exact().min.x();
        elements[1] = ref->model.bounding_box_exact().min.y();
        elements[2] = ref->model.bounding_box_exact().min.z();
        elements[3] = ref->model.bounding_box_exact().max.x();
        elements[4] = ref->model.bounding_box_exact().max.y();
        elements[5] = ref->model.bounding_box_exact().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jlongArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1create_1flatten_1planes(JNIEnv* env, jclass, jlong ptr, jint i) {
        auto empty_result = [&]() { return env->NewLongArray(0); };
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        if (ref == nullptr || i < 0 || static_cast<size_t>(i) >= ref->model.objects.size()) return empty_result();
        const ModelObject* mo = ref->model.objects[i];
        if (mo == nullptr || mo->instances.empty() || mo->instances.front() == nullptr) return empty_result();
        TriangleMesh ch;
        Transform3d real_transform = Geometry::translation_transform(mo->bounding_box_exact().center());
        for (const ModelVolume* vol : mo->volumes) {
            if (vol == nullptr || vol->type() != ModelVolumeType::MODEL_PART || vol->mesh().empty())
                continue;
            TriangleMesh vol_ch = vol->get_convex_hull();
            if (vol_ch.empty()) continue;
            vol_ch.transform(vol->get_matrix_no_offset());
            vol_ch.transform(real_transform);
            ch.merge(vol_ch);
        }
        if (ch.empty()) return empty_result();
        ch = ch.convex_hull_3d();
        if (ch.empty()) return empty_result();

        std::vector<PlaneData> m_planes;

        const Transform3d inst_matrix = mo->instances.front()->get_matrix_no_offset();

        // Following constants are used for discarding too small polygons.
        const float minimal_area = 5.f; // in square mm (world coordinates)
        const float minimal_side = 1.f; // mm

        const int                num_of_facets  = ch.facets_count();
        const std::vector<Vec3f> face_normals   = its_face_normals(ch.its);
        const std::vector<Vec3i32> face_neighbors = its_face_neighbors(ch.its);
        std::vector<int>         facet_queue(num_of_facets, 0);
        std::vector<bool>        facet_visited(num_of_facets, false);
        int                      facet_queue_cnt = 0;
        const stl_normal*        normal_ptr      = nullptr;
        int facet_idx = 0;
        while (true) {
            // Find next unvisited triangle:
            for (; facet_idx < num_of_facets; ++ facet_idx)
                if (!facet_visited[facet_idx]) {
                    facet_queue[facet_queue_cnt ++] = facet_idx;
                    facet_visited[facet_idx] = true;
                    normal_ptr = &face_normals[facet_idx];
                    m_planes.emplace_back();
                    break;
                }
            if (facet_idx == num_of_facets)
                break; // Everything was visited already

            while (facet_queue_cnt > 0) {
                int facet_idx = facet_queue[-- facet_queue_cnt];
                const stl_normal& this_normal = face_normals[facet_idx];
                if (std::abs(this_normal(0) - (*normal_ptr)(0)) < 0.001 && std::abs(this_normal(1) - (*normal_ptr)(1)) < 0.001 && std::abs(this_normal(2) - (*normal_ptr)(2)) < 0.001) {
                    const Vec3i32 face = ch.its.indices[facet_idx];
                    for (int j=0; j<3; ++j)
                        m_planes.back().vertices.emplace_back(ch.its.vertices[face[j]].cast<double>());

                    facet_visited[facet_idx] = true;
                    for (int j = 0; j < 3; ++ j)
                        if (int neighbor_idx = face_neighbors[facet_idx][j]; neighbor_idx >= 0 && ! facet_visited[neighbor_idx])
                            facet_queue[facet_queue_cnt ++] = neighbor_idx;
                }
            }
            m_planes.back().normal = normal_ptr->cast<double>();

            Pointf3s& verts = m_planes.back().vertices;
            // Now we'll transform all the points into world coordinates, so that the areas, angles and distances
            // make real sense.
            verts = transform(verts, inst_matrix);

            // if this is a just a very small triangle, remove it to speed up further calculations (it would be rejected later anyway):
            if (verts.size() == 3 &&
                ((verts[0] - verts[1]).norm() < minimal_side
                 || (verts[0] - verts[2]).norm() < minimal_side
                 || (verts[1] - verts[2]).norm() < minimal_side))
                m_planes.pop_back();
        }

        // Let's prepare transformation of the normal vector from mesh to instance coordinates.
        const Matrix3d normal_matrix = inst_matrix.matrix().block(0, 0, 3, 3).inverse().transpose();

        // Now we'll go through all the polygons, transform the points into xy plane to process them:
        for (unsigned int polygon_id=0; polygon_id < m_planes.size(); ++polygon_id) {
            Pointf3s& polygon = m_planes[polygon_id].vertices;
            const Vec3d& normal = m_planes[polygon_id].normal;

            // transform the normal according to the instance matrix:
            const Vec3d normal_transformed = normal_matrix * normal;

            // We are going to rotate about z and y to flatten the plane
            Eigen::Quaterniond q;
            Transform3d m = Transform3d::Identity();
            m.matrix().block(0, 0, 3, 3) = q.setFromTwoVectors(normal_transformed, Vec3d::UnitZ()).toRotationMatrix();
            polygon = transform(polygon, m);

            // Now to remove the inner points. We'll misuse Geometry::convex_hull for that, but since
            // it works in fixed point representation, we will rescale the polygon to avoid overflows.
            // And yes, it is a nasty thing to do. Whoever has time is free to refactor.
            Vec3d bb_size = BoundingBoxf3(polygon).size();
            float sf = std::min(1./bb_size(0), 1./bb_size(1));
            Transform3d tr = Geometry::scale_transform({ sf, sf, 1.f });
            polygon = transform(polygon, tr);
            polygon = Slic3r::Geometry::convex_hull(polygon);
            polygon = transform(polygon, tr.inverse());

            // Calculate area of the polygons and discard ones that are too small
            float& area = m_planes[polygon_id].area;
            area = 0.f;
            for (unsigned int i = 0; i < polygon.size(); i++) // Shoelace formula
                area += polygon[i](0)*polygon[i + 1 < polygon.size() ? i + 1 : 0](1) - polygon[i + 1 < polygon.size() ? i + 1 : 0](0)*polygon[i](1);
            area = 0.5f * std::abs(area);

            bool discard = false;
            if (area < minimal_area)
                discard = true;
            else {
                // We also check the inner angles and discard polygons with angles smaller than the following threshold
                const double angle_threshold = ::cos(10.0 * (double)PI / 180.0);

                for (unsigned int i = 0; i < polygon.size(); ++i) {
                    const Vec3d& prec = polygon[(i == 0) ? polygon.size() - 1 : i - 1];
                    const Vec3d& curr = polygon[i];
                    const Vec3d& next = polygon[(i == polygon.size() - 1) ? 0 : i + 1];

                    if ((prec - curr).normalized().dot((next - curr).normalized()) > angle_threshold) {
                        discard = true;
                        break;
                    }
                }
            }

            if (discard) {
                m_planes[polygon_id--] = std::move(m_planes.back());
                m_planes.pop_back();
                continue;
            }

            // We will shrink the polygon a little bit so it does not touch the object edges:
            Vec3d centroid = std::accumulate(polygon.begin(), polygon.end(), Vec3d(0.0, 0.0, 0.0));
            centroid /= (double)polygon.size();
            for (auto& vertex : polygon)
                vertex = 0.9f*vertex + 0.1f*centroid;

            // Polygon is now simple and convex, we'll round the corners to make them look nicer.
            // The algorithm takes a vertex, calculates middles of respective sides and moves the vertex
            // towards their average (controlled by 'aggressivity'). This is repeated k times.
            // In next iterations, the neighbours are not always taken at the middle (to increase the
            // rounding effect at the corners, where we need it most).
            const unsigned int k = 10; // number of iterations
            const float aggressivity = 0.2f;  // agressivity
            const unsigned int N = polygon.size();
            std::vector<std::pair<unsigned int, unsigned int>> neighbours;
            if (k != 0) {
                Pointf3s points_out(2*k*N); // vector long enough to store the future vertices
                for (unsigned int j=0; j<N; ++j) {
                    points_out[j*2*k] = polygon[j];
                    neighbours.push_back(std::make_pair((int)(j*2*k-k) < 0 ? (N-1)*2*k+k : j*2*k-k, j*2*k+k));
                }

                for (unsigned int i=0; i<k; ++i) {
                    // Calculate middle of each edge so that neighbours points to something useful:
                    for (unsigned int j=0; j<N; ++j)
                        if (i==0)
                            points_out[j*2*k+k] = 0.5f * (points_out[j*2*k] + points_out[j==N-1 ? 0 : (j+1)*2*k]);
                        else {
                            float r = 0.2+0.3/(k-1)*i; // the neighbours are not always taken in the middle
                            points_out[neighbours[j].first] = r*points_out[j*2*k] + (1-r) * points_out[neighbours[j].first-1];
                            points_out[neighbours[j].second] = r*points_out[j*2*k] + (1-r) * points_out[neighbours[j].second+1];
                        }
                    // Now we have a triangle and valid neighbours, we can do an iteration:
                    for (unsigned int j=0; j<N; ++j)
                        points_out[2*k*j] = (1-aggressivity) * points_out[2*k*j] +
                                            aggressivity*0.5f*(points_out[neighbours[j].first] + points_out[neighbours[j].second]);

                    for (auto& n : neighbours) {
                        ++n.first;
                        --n.second;
                    }
                }
                polygon = points_out; // replace the coarse polygon with the smooth one that we just created
            }


            // Raise a bit above the object surface to avoid flickering:
            for (auto& b : polygon)
                b(2) += 0.1f;

            // Transform back to 3D (and also back to mesh coordinates)
            polygon = transform(polygon, inst_matrix.inverse() * m.inverse());
        }

        // We'll sort the planes by area and only keep the 254 largest ones (because of the picking pass limitations):
        std::sort(m_planes.rbegin(), m_planes.rend(), [](const PlaneData& a, const PlaneData& b) { return a.area < b.area; });
        m_planes.resize(std::min((int)m_planes.size(), 254));

        jlongArray arr = env->NewLongArray(m_planes.size());

        // And finally create respective VBOs. The polygon is convex with
        // the vertices in order, so triangulation is trivial.
        for (int i = 0, s = m_planes.size(); i < s; i++) {
            auto& plane = m_planes[i];
            indexed_triangle_set its;
            its.vertices.reserve(plane.vertices.size());
            its.indices.reserve(plane.vertices.size() / 3);
            for (size_t i = 0; i < plane.vertices.size(); ++i) {
                its.vertices.emplace_back((Vec3f)plane.vertices[i].cast<float>());
            }
            for (size_t i = 1; i < plane.vertices.size() - 1; ++i) {
                its.indices.emplace_back(0, i, i + 1); // triangle fan
            }

            if (Geometry::Transformation(inst_matrix).is_left_handed()) {
                // we need to swap face normals in case the object is mirrored
                // for the raycaster to work properly
                for (stl_triangle_vertex_indices& face : its.indices) {
                    if (its_face_normal(its, face).cast<double>().dot(plane.normal) < 0.0)
                        std::swap(face[1], face[2]);
                }
            }
            GLModelRef* ref = new GLModelRef();
            ref->mesh = TriangleMesh(its);
            ref->model.init_from(its);
            ref->flatten_normal = plane.normal;
            // Build the lightweight tree only after copying `its` into ref->mesh; the tree
            // stores triangle indices and must never outlive the mesh backing its vertices.
            ref->raycast_tree = std::make_unique<AABBTreeIndirect::Tree3f>(
                AABBTreeIndirect::build_aabb_tree_over_indexed_triangle_set(
                    ref->mesh.its.vertices, ref->mesh.its.indices));

            jlong ptr = reinterpret_cast<jlong>(ref);
            env->SetLongArrayRegion(arr, i, 1, &ptr);

            // vertices are no more needed, clear memory
            plane.vertices = std::vector<Vec3d>();
        }
        m_planes.clear();
        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1auto_1orient(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size()) return;
        ModelObject* obj = model->model.objects[i];
        if (obj == nullptr || obj->volumes.empty() ||
            std::any_of(obj->volumes.begin(), obj->volumes.end(),
                [](const ModelVolume* volume) { return volume == nullptr; })) return;
        orientation::orient(obj);
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1is_1big_1object(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        return obj->volumes.size() == 1 && obj->volumes.front()->mesh().its.indices.size() >= 500000;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1extruder(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        return obj->config.has("extruder") ? obj->config.opt_int("extruder") : -1;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1volume_1extruder(
        JNIEnv* env, jclass, jlong ptr, jint object_index, jint volume_index) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (model == nullptr || object_index < 0 || object_index >= static_cast<jint>(model->model.objects.size())) return -1;
        const ModelObject* object = model->model.objects[object_index];
        if (object == nullptr || volume_index < 0 || volume_index >= static_cast<jint>(object->volumes.size())) return -1;
        const ModelVolume* volume = object->volumes[volume_index];
        return volume == nullptr ? -1 : std::max(1, volume->extruder_id());
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1set_1extruder(JNIEnv* env, jclass, jlong ptr, jint i, jint extruder) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        obj->config.set("extruder", extruder);
    }

    // Material assignments can live on assembly parts, not just on the parent object.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1used_1filaments(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        std::set<jint> used;
        if (model != nullptr && i >= 0 && i < (int) model->model.objects.size()) {
            for (const ModelVolume* volume : model->model.objects[i]->volumes) {
                if (!volume->is_model_part()) continue;
                used.insert(std::max(1, volume->extruder_id()));
                const auto& states = volume->mmu_segmentation_facets.get_data().used_states;
                for (size_t state = 1; state < states.size(); ++state)
                    if (states[state]) used.insert((jint) state);
            }
        }
        std::vector<jint> values(used.begin(), used.end());
        jintArray result = env->NewIntArray((jsize) values.size());
        if (result != nullptr && !values.empty()) env->SetIntArrayRegion(result, 0, (jsize) values.size(), values.data());
        return result;
    }

    // Only an explicit single-material choice removes part colors and facet painting.
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1set_1uniform_1filament(JNIEnv*, jclass, jlong ptr, jint i, jint filament) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (model == nullptr || i < 0 || i >= (int) model->model.objects.size() || filament < 1 || filament > 16) return;
        ModelObject* object = model->model.objects[i];
        object->config.set("extruder", (int) filament);
        for (ModelVolume* volume : object->volumes) {
            if (!volume->is_model_part()) continue;
            volume->config.erase("extruder");
            volume->mmu_segmentation_facets.reset();
        }
    }

    // Remap source 3MF filament IDs to user-selected slots without flattening multipart objects.
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1remap_1filaments(
            JNIEnv* env, jclass, jlong ptr, jintArray sourceFilaments, jintArray targetFilaments) {
        ModelRef* model = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(ptr));
        if (model == nullptr || sourceFilaments == nullptr || targetFilaments == nullptr) return;
        const jsize sourceCount = env->GetArrayLength(sourceFilaments);
        const jsize targetCount = env->GetArrayLength(targetFilaments);
        if (sourceCount != targetCount) return;

        std::array<int, 17> remapping{};
        jint* sources = env->GetIntArrayElements(sourceFilaments, nullptr);
        jint* targets = env->GetIntArrayElements(targetFilaments, nullptr);
        if (sources == nullptr || targets == nullptr) {
            if (sources != nullptr) env->ReleaseIntArrayElements(sourceFilaments, sources, JNI_ABORT);
            if (targets != nullptr) env->ReleaseIntArrayElements(targetFilaments, targets, JNI_ABORT);
            return;
        }
        for (jsize index = 0; index < sourceCount; ++index) {
            if (sources[index] >= 1 && sources[index] <= 16 && targets[index] >= 1 && targets[index] <= 16)
                remapping[static_cast<size_t>(sources[index])] = targets[index];
        }
        env->ReleaseIntArrayElements(sourceFilaments, sources, JNI_ABORT);
        env->ReleaseIntArrayElements(targetFilaments, targets, JNI_ABORT);
        if (std::none_of(remapping.begin(), remapping.end(), [](int target) { return target > 0; })) return;
        remap_model_filaments(model->model, remapping);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1apply_1adaptive_1layer_1height(JNIEnv* env, jclass, jlong ptr, jint i, jstring configPath, jfloat qualityFactor) {
        try {
            ModelRef* model = (ModelRef*) (intptr_t) ptr;
            if (i < 0 || i >= (int) model->model.objects.size()) return;
            ModelObject* obj = model->model.objects[i];

            DynamicPrintConfig config;
            const char *chars = env->GetStringUTFChars(configPath, JNI_FALSE);
            config.load(std::string(chars), ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(configPath, chars);
            config.normalize_fdm();

            SlicingParameters slicing_params = PrintObject::slicing_parameters(config, *obj, 0.0f, Vec3d::Zero());
            std::vector<double> profile = layer_height_profile_adaptive(slicing_params, *obj, qualityFactor);
            obj->layer_height_profile.set(profile);
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
        }
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1apply_1adaptive_1layer_1height_1to_1objects(
        JNIEnv* env, jclass, jlong ptr, jintArray objectIndices, jstring configPath, jfloat qualityFactor) {
        try {
            ModelRef* model = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(ptr));
            if (model == nullptr || objectIndices == nullptr || configPath == nullptr ||
                !std::isfinite(qualityFactor) || qualityFactor < 0.f || qualityFactor > 1.f) return JNI_FALSE;
            const jsize count = env->GetArrayLength(objectIndices);
            if (count < 2) return JNI_FALSE;
            std::vector<jint> indices(static_cast<size_t>(count));
            env->GetIntArrayRegion(objectIndices, 0, count, indices.data());
            if (env->ExceptionCheck()) return JNI_FALSE;
            std::sort(indices.begin(), indices.end());
            if (std::adjacent_find(indices.begin(), indices.end()) != indices.end()) return JNI_FALSE;

            const char* chars = env->GetStringUTFChars(configPath, nullptr);
            if (chars == nullptr) return JNI_FALSE;
            DynamicPrintConfig config;
            try {
                config.load(std::string(chars), ForwardCompatibilitySubstitutionRule::Disable);
                env->ReleaseStringUTFChars(configPath, chars);
            } catch (...) {
                env->ReleaseStringUTFChars(configPath, chars);
                throw;
            }
            config.normalize_fdm();

            std::vector<ModelObject*> objects;
            std::vector<std::vector<coordf_t>> profiles;
            objects.reserve(indices.size());
            profiles.reserve(indices.size());
            for (jint index : indices) {
                if (index < 0 || static_cast<size_t>(index) >= model->model.objects.size()) return JNI_FALSE;
                ModelObject* object = model->model.objects[static_cast<size_t>(index)];
                if (object == nullptr) return JNI_FALSE;
                const SlicingParameters params = PrintObject::slicing_parameters(config, *object, 0.0f, Vec3d::Zero());
                std::vector<coordf_t> profile = layer_height_profile_adaptive(params, *object, qualityFactor);
                if (profile.size() < 4 || profile.size() % 2 != 0) return JNI_FALSE;
                objects.push_back(object);
                profiles.push_back(std::move(profile));
            }

            const std::vector<coordf_t> merged = layer_height_profile_merge_finest(profiles);
            if (merged.size() < 4) return JNI_FALSE;
            std::vector<std::vector<coordf_t>> fittedProfiles;
            fittedProfiles.reserve(objects.size());
            for (ModelObject* object : objects) {
                const double height = object->raw_mesh_bounding_box().size().z();
                std::vector<coordf_t> fitted = layer_height_profile_fit_to_height(merged, height);
                if (fitted.size() < 4) return JNI_FALSE;
                fittedProfiles.push_back(std::move(fitted));
            }
            for (size_t index = 0; index < objects.size(); ++index)
                objects[index]->layer_height_profile.set(std::move(fittedProfiles[index]));
            return JNI_TRUE;
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
            return JNI_FALSE;
        }
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1adjust_1layer_1height_1at_1world_1point(
        JNIEnv* env, jclass, jlong ptr, jint i, jstring configPath, jdouble worldX, jdouble worldY, jdouble worldZ,
        jdouble bandWidth, jdouble delta, jint action, jfloat qualityFactor) {
        try {
            ModelRef* model = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(ptr));
            if (model == nullptr || configPath == nullptr || i < 0 ||
                static_cast<size_t>(i) >= model->model.objects.size() ||
                !std::isfinite(worldX) || !std::isfinite(worldY) || !std::isfinite(worldZ) ||
                !std::isfinite(bandWidth) || bandWidth <= 0.0 || !std::isfinite(delta) || delta <= 0.0 ||
                action < static_cast<jint>(LAYER_HEIGHT_EDIT_ACTION_INCREASE) ||
                action > static_cast<jint>(LAYER_HEIGHT_EDIT_ACTION_SMOOTH) ||
                !std::isfinite(qualityFactor) || qualityFactor < 0.f || qualityFactor > 1.f)
                return JNI_FALSE;

            ModelObject* object = model->model.objects[static_cast<size_t>(i)];
            if (object == nullptr) return JNI_FALSE;
            std::lock_guard<std::mutex> brushLock(model->layer_height_brush_mutex);

            // The GL raycaster hits the combined instance mesh in build-volume coordinates.
            // Layer profiles live in raw object coordinates, so map the hit through the instance
            // transform and accept only the instance whose cached raw bounds contain the surface.
            const BoundingBoxf3& rawBounds = object->raw_mesh_bounding_box();
            if (!rawBounds.defined) return JNI_FALSE;
            const Vec3d worldPoint(worldX, worldY, worldZ);
            double bestOutsideDistance = std::numeric_limits<double>::infinity();
            double localZ = std::numeric_limits<double>::quiet_NaN();
            auto try_instance = [&](const Transform3d& transform) {
                const double determinant = transform.linear().determinant();
                if (!transform.matrix().allFinite() || !std::isfinite(determinant) || std::abs(determinant) < 1e-12)
                    return;
                const Vec3d point = transform.inverse() * worldPoint;
                if (!point.allFinite()) return;
                const Vec3d outside(
                    std::max({rawBounds.min.x() - point.x(), 0.0, point.x() - rawBounds.max.x()}),
                    std::max({rawBounds.min.y() - point.y(), 0.0, point.y() - rawBounds.max.y()}),
                    std::max({rawBounds.min.z() - point.z(), 0.0, point.z() - rawBounds.max.z()}));
                const double distance = outside.squaredNorm();
                if (distance < bestOutsideDistance) {
                    bestOutsideDistance = distance;
                    localZ = point.z();
                }
            };
            if (object->instances.empty()) try_instance(Transform3d::Identity());
            else for (const ModelInstance* instance : object->instances)
                if (instance != nullptr) try_instance(instance->get_matrix());
            if (!std::isfinite(localZ) || bestOutsideDistance > 0.25 * 0.25) return JNI_FALSE;

            const char* configChars = env->GetStringUTFChars(configPath, nullptr);
            if (configChars == nullptr) return JNI_FALSE;
            const std::string configFile(configChars);
            env->ReleaseStringUTFChars(configPath, configChars);
            if (model->layer_height_brush_object != object || model->layer_height_brush_config_path != configFile ||
                !model->layer_height_brush_config || !model->layer_height_brush_params) {
                DynamicPrintConfig config;
                config.load(configFile, ForwardCompatibilitySubstitutionRule::Disable);
                config.normalize_fdm();
                model->layer_height_brush_config.emplace(std::move(config));
                model->layer_height_brush_params.emplace(
                    PrintObject::slicing_parameters(*model->layer_height_brush_config, *object, 0.0f, Vec3d::Zero()));
                model->layer_height_brush_object = object;
                model->layer_height_brush_config_path = configFile;
            }
            const SlicingParameters& slicingParams = *model->layer_height_brush_params;
            std::vector<coordf_t> profile = object->layer_height_profile.get();
            if (profile.empty())
                profile = layer_height_profile_adaptive(slicingParams, *object, qualityFactor);
            if (profile.size() < 2 || (profile.size() & 1) != 0) return JNI_FALSE;
            const std::vector<coordf_t> before = profile;
            adjust_layer_height_profile(
                *object, slicingParams, profile, static_cast<coordf_t>(localZ),
                static_cast<coordf_t>(delta), static_cast<coordf_t>(bandWidth),
                static_cast<LayerHeightEditActionType>(action));
            if (profile == before) return JNI_FALSE;
            object->layer_height_profile.set(std::move(profile));
            return JNI_TRUE;
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
            return JNI_FALSE;
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1clear_1adaptive_1layer_1height(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (i >= 0 && i < (int) model->model.objects.size()) {
            model->model.objects[i]->layer_height_profile.clear();
        }
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1has_1adaptive_1layer_1height(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        if (i >= 0 && i < (int) model->model.objects.size()) {
            return !model->model.objects[i]->layer_height_profile.empty();
        }
        return false;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1layer_1height_1profile(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(ptr));
        if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size() ||
            model->model.objects[static_cast<size_t>(i)] == nullptr)
            return env->NewDoubleArray(0);
        const auto& profile = model->model.objects[static_cast<size_t>(i)]->layer_height_profile.get();
        jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(profile.size()));
        if (result != nullptr && !profile.empty())
            env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(profile.size()), profile.data());
        return result;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1fit_1layer_1height_1profile_1to_1object(
        JNIEnv* env, jclass, jlong ptr, jint sourceIndex, jint targetIndex) {
        ModelRef* model = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(ptr));
        if (model == nullptr || sourceIndex < 0 || targetIndex < 0 || sourceIndex == targetIndex ||
            static_cast<size_t>(sourceIndex) >= model->model.objects.size() ||
            static_cast<size_t>(targetIndex) >= model->model.objects.size()) return JNI_FALSE;

        ModelObject* source = model->model.objects[static_cast<size_t>(sourceIndex)];
        ModelObject* target = model->model.objects[static_cast<size_t>(targetIndex)];
        if (source == nullptr || target == nullptr) return JNI_FALSE;
        const std::vector<coordf_t> profile = source->layer_height_profile.get();
        if (profile.size() < 2) return JNI_FALSE;
        const double targetHeight = target->raw_mesh_bounding_box().size().z();
        std::vector<coordf_t> fitted = layer_height_profile_fit_to_height(profile, targetHeight);
        if (fitted.empty()) return JNI_FALSE;
        target->layer_height_profile.set(std::move(fitted));
        return JNI_TRUE;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1create_1text_1object(
        JNIEnv* env, jclass, jlong ptr, jstring fontPath, jstring textStr, jfloat size, jfloat depth,
        jdouble px, jdouble py, jdouble pz, jdouble nx, jdouble ny, jdouble nz, jint targetObjectIndex) {
        try {
            ModelRef* model = (ModelRef*) (intptr_t) ptr;
            if (model == nullptr || fontPath == nullptr || textStr == nullptr ||
                !std::isfinite(size) || !std::isfinite(depth) || size <= 0.f || depth <= 0.f ||
                !std::isfinite(px) || !std::isfinite(py) || !std::isfinite(pz) ||
                !std::isfinite(nx) || !std::isfinite(ny) || !std::isfinite(nz) ||
                targetObjectIndex < -1) return -1;
            const double normalLengthSquared = nx * nx + ny * ny + nz * nz;
            if (!std::isfinite(normalLengthSquared) || normalLengthSquared <= 1e-18 ||
                (targetObjectIndex >= 0 && targetObjectIndex >= static_cast<jint>(model->model.objects.size())))
                return -1;
            if (targetObjectIndex >= 0 && model->model.objects[targetObjectIndex] == nullptr) return -1;
            const char* font_chars = env->GetStringUTFChars(fontPath, JNI_FALSE);
            if (font_chars == nullptr) return -1;
            std::string font_path(font_chars);
            env->ReleaseStringUTFChars(fontPath, font_chars);
            const char* text_chars = env->GetStringUTFChars(textStr, JNI_FALSE);
            if (text_chars == nullptr) return -1;
            std::string text(text_chars);
            env->ReleaseStringUTFChars(textStr, text_chars);
            if (text.empty()) return -1;

            std::unique_ptr<Emboss::FontFile> font = Emboss::create_font_file(font_path.c_str());
            if (!font) throw std::runtime_error("Failed to load font file");
            Emboss::FontFileWithCache cached(std::move(font));
            FontProp font_prop(size);
            HealedExPolygons shapes = Emboss::text2shapes(cached, text.c_str(), font_prop);
            if (shapes.expolygons.empty()) throw std::runtime_error("Generated text shape is empty");
            const double scale = Emboss::get_text_shape_scale(font_prop, *cached.font_file);
            auto project_z = std::make_unique<Emboss::ProjectZ>((double) depth / scale);
            Emboss::ProjectScale project(std::move(project_z), scale);
            indexed_triangle_set its = Emboss::polygons2model(shapes.expolygons, project);
            if (its.indices.empty()) throw std::runtime_error("Text produced no geometry");

            // Keep Z in [0, depth] and center only XY so the standalone solid rests on the picked surface.
            BoundingBoxf3 bb = bounding_box(its);
            its_translate(its, Vec3f((float) -bb.center().x(), (float) -bb.center().y(), 0.f));
            Vec3d normal(nx, ny, nz);
            if (normal.norm() < 1e-9) normal = Vec3d::UnitZ(); else normal.normalize();
            if (targetObjectIndex >= 0) {
                ModelObject *target = model->model.objects[targetObjectIndex];
                int surfaceExtruder = -1;
                const Transform3d instance = conform_text_mesh_to_model_surface(
                    its, *target, Vec3d(px, py, pz), normal, surfaceExtruder);
                its_transform(its, instance.inverse(), true);
                ModelVolume *volume = target->add_volume(
                    TriangleMesh(std::move(its)), ModelVolumeType::MODEL_PART, false);
                if (volume == nullptr) throw std::runtime_error("Curved text could not be added to the target model");
                if (surfaceExtruder > 0) volume->config.set("extruder", surfaceExtruder);
                volume->name = text;
                target->invalidate_bounding_box();
                return targetObjectIndex;
            } else {
                const Transform3d surface = Emboss::create_transformation_onto_surface(Vec3d(px, py, pz), normal);
                its_transform(its, surface, true);
            }
            TriangleMesh mesh(std::move(its));

            ModelObject* object = model->model.add_object();
            object->name = text;
            ModelVolume* volume = object->add_volume(std::move(mesh), ModelVolumeType::MODEL_PART, false);
            if (volume == nullptr) {
                model->model.delete_object((int) model->model.objects.size() - 1);
                return -1;
            }
            volume->name = text;
            object->add_instance();
            object->invalidate_bounding_box();
            return (jint) model->model.objects.size() - 1;
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
            return -1;
        }
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1create_1primitive(
        JNIEnv* env, jclass, jlong ptr, jint type, jdouble a, jdouble b, jdouble c, jstring nameStr) {
        try {
            ModelRef* ref = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(ptr));
            if (ref == nullptr || nameStr == nullptr || type < 0 || type > 6) return -1;
            auto valid = [](double value) { return std::isfinite(value) && value >= 0.2 && value <= 500.0; };
            const bool dimensionsValid = (type == 0 || type == 6) ? valid(a) && valid(b) && valid(c) :
                (type == 1 || type == 2 || type == 5) ? valid(a) && valid(c) :
                type == 3 ? valid(a) :
                type == 4 ? valid(a) && a >= 0.6 && std::isfinite(b) && b >= 0.2 && b < a / 2.0 && b <= 500.0 : false;
            if (!dimensionsValid) return -1;

            const char* chars = env->GetStringUTFChars(nameStr, JNI_FALSE);
            if (chars == nullptr) return -1;
            const std::string name(chars);
            env->ReleaseStringUTFChars(nameStr, chars);
            if (name.empty() || name.size() > 240) return -1;

            // Match Orca's size-aware primitive tessellation while keeping the mobile mesh
            // bounded. Small primitives should not carry desktop-sized fixed detail, while
            // large primitives need more facets to avoid visibly coarse circular outlines.
            auto primitive_angle_step = [](double radius) {
                constexpr double chord_error_mm = 0.002;
                constexpr double max_segments = 360.0;
                const double desired_step = 2.0 * std::acos(
                    1.0 - std::min(1.0, chord_error_mm / std::max(radius, 1e-9)));
                return std::clamp(desired_step, 2.0 * PI / max_segments, 2.0 * PI / 16.0);
            };

            indexed_triangle_set mesh;
            switch (type) {
                case 0: mesh = its_make_cube(a, b, c); break;                 // Cube / slab
                case 1: mesh = its_make_cylinder(a / 2.0, c, primitive_angle_step(a / 2.0)); break;
                case 2: mesh = its_make_cone(a / 2.0, c, primitive_angle_step(a / 2.0)); break;
                case 3: mesh = its_make_sphere(a / 2.0, primitive_angle_step(a / 2.0)); break;
                case 4: mesh = its_make_torus((a - b) / 2.0, b / 2.0, primitive_angle_step(a / 2.0)); break;
                case 5: mesh = its_make_cylinder(a / 2.0, c, primitive_angle_step(a / 2.0)); break; // Disc
                case 6: mesh = its_make_cube(a, b, c); break;                 // Slab
            }
            if (mesh.vertices.empty() || mesh.indices.empty()) return -1;
            const BoundingBoxf3 bounds = bounding_box(mesh);
            its_translate(mesh, Vec3f(
                static_cast<float>(-bounds.center().x()),
                static_cast<float>(-bounds.center().y()),
                static_cast<float>(-bounds.min.z())));

            ModelObject* object = ref->model.add_object();
            if (object == nullptr) return -1;
            object->name = name;
            ModelVolume* volume = object->add_volume(TriangleMesh(std::move(mesh)), ModelVolumeType::MODEL_PART, false);
            if (volume == nullptr) {
                ref->model.delete_object(static_cast<int>(ref->model.objects.size()) - 1);
                return -1;
            }
            volume->name = name;
            object->add_instance();
            object->invalidate_bounding_box();
            return static_cast<jint>(ref->model.objects.size()) - 1;
        } catch (const std::exception& error) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), error.what());
            return -1;
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1emboss_1text(
        JNIEnv* env, jclass, jlong ptr, jint i, jstring fontPath, jstring textStr, jfloat size, jfloat depth, jint type,
        jdouble px, jdouble py, jdouble pz, jdouble nx, jdouble ny, jdouble nz) {
        try {
            ModelRef* model = (ModelRef*) (intptr_t) ptr;
            if (model == nullptr || i < 0 || static_cast<size_t>(i) >= model->model.objects.size() ||
                fontPath == nullptr || textStr == nullptr || type < 0 || type > 1 ||
                !std::isfinite(size) || size <= 0.f || !std::isfinite(depth) || depth <= 0.f ||
                !std::isfinite(px) || !std::isfinite(py) || !std::isfinite(pz) ||
                !std::isfinite(nx) || !std::isfinite(ny) || !std::isfinite(nz)) return;
            ModelObject* obj = model->model.objects[i];
            const double normal_length_squared = nx * nx + ny * ny + nz * nz;
            if (obj == nullptr || obj->instances.empty() || obj->volumes.empty() ||
                std::any_of(obj->instances.begin(), obj->instances.end(), [](const ModelInstance* instance) {
                    return instance == nullptr;
                }) || !std::isfinite(normal_length_squared) || normal_length_squared <= 1e-18 ||
                std::any_of(obj->volumes.begin(), obj->volumes.end(), [](const ModelVolume* volume) {
                    return volume == nullptr;
                })) return;

            const char *font_path_chars = env->GetStringUTFChars(fontPath, JNI_FALSE);
            if (font_path_chars == nullptr) return;
            std::string font_path(font_path_chars);
            env->ReleaseStringUTFChars(fontPath, font_path_chars);

            const char *text_chars = env->GetStringUTFChars(textStr, JNI_FALSE);
            if (text_chars == nullptr) return;
            std::string text(text_chars);
            env->ReleaseStringUTFChars(textStr, text_chars);
            if (text.empty()) return;

            // 1) Load font
            std::unique_ptr<Emboss::FontFile> font_file = Emboss::create_font_file(font_path.c_str());
            if (!font_file) {
                env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to load font file");
                return;
            }
            Emboss::FontFileWithCache font_with_cache(std::move(font_file));

            // 2) Generate 2D shapes. text2shapes output is in FONT units (scaled by SHAPE_SCALE),
            // NOT millimeters — it must go through ProjectScale with get_text_shape_scale, exactly
            // like desktop's text pipeline. Skipping the scale produces a kilometer-sized mesh that
            // blows up the scene bounding box ("model disappears") and crashes slicing.
            FontProp font_prop(size);
            HealedExPolygons healed_shapes = Emboss::text2shapes(font_with_cache, text.c_str(), font_prop);
            if (healed_shapes.expolygons.empty()) {
                env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Generated text shape is empty");
                return;
            }

            // 3) Project to a small, correctly scaled mesh at the origin: XY in mm, Z in [0, depth].
            double scale = Emboss::get_text_shape_scale(font_prop, *font_with_cache.font_file);
            auto project_z = std::make_unique<Emboss::ProjectZ>((double) depth / scale);
            Emboss::ProjectScale project(std::move(project_z), scale);
            indexed_triangle_set its = Emboss::polygons2model(healed_shapes.expolygons, project);
            if (its.indices.empty()) {
                env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Text produced no geometry");
                return;
            }

            // 4) Center the text on its origin and sink it half a depth, so the volume sits
            // centered on the tap point and overlaps the surface (clean union for emboss, real
            // overlap for engraving with a negative volume).
            {
                BoundingBoxf3 bb = bounding_box(its);
                Vec3d c = bb.center();
                its_translate(its, Vec3f((float) -c.x(), (float) -c.y(), (float) (-depth / 2.f)));
            }

            // 5) Orient local +Z along the surface normal at the tap point. The raycast hit is in
            // object-world space (instance transform applied), but volumes live in object-local
            // space, so pull the instance transform back out.
            Vec3d position(px, py, pz);
            Vec3d normal(nx, ny, nz);
            if (normal.norm() < 1e-9) normal = Vec3d::UnitZ(); else normal.normalize();
            Transform3d world_tr = Emboss::create_transformation_onto_surface(position, normal);
            const std::vector<Transform3d> instances = model_instance_transforms(*obj);
            if (instances.empty()) throw std::runtime_error("The target model has no placed instance");
            const ModelVolumeType vol_type = static_cast<ModelVolumeType>(type);
            std::vector<const ModelVolume*> model_parts;
            if (vol_type == ModelVolumeType::MODEL_PART) {
                for (const ModelVolume *volume : obj->volumes)
                    if (volume != nullptr && volume->is_model_part()) model_parts.push_back(volume);
            }
            size_t selected_instance = 0;
            int surface_extruder = model_parts.size() == 1 ? model_parts.front()->extruder_id() : -1;
            if (instances.size() > 1 || (vol_type == ModelVolumeType::MODEL_PART && model_parts.size() > 1)) {
                // Resolve the tapped copy and material in world space. Using the first instance
                // misplaces text on repeated models; using the parent object's default extruder
                // can recolor text placed on a differently assigned multipart surface.
                TriangleMesh target_mesh = obj->raw_mesh();
                if (target_mesh.its.vertices.empty() || target_mesh.its.indices.empty())
                    throw std::runtime_error("The target model has no printable surface");
                const BoundingBoxf3 bounds = bounding_box(target_mesh.its);
                const double ray_distance = std::max(100., 4. * (bounds.max - bounds.min).norm() + 10.);
                if (instances.size() > 1) {
                    AABBMesh raycaster(target_mesh);
                    selected_instance = find_instance_for_world_surface(
                        instances, raycaster, position, normal, ray_distance);
                    if (selected_instance == instances.size())
                        throw std::runtime_error("Tap the text onto a visible model surface");
                }
                if (vol_type == ModelVolumeType::MODEL_PART && model_parts.size() > 1) {
                    surface_extruder = find_surface_extruder(
                        *obj, instances[selected_instance], position, normal, ray_distance);
                }
            }
            const Transform3d inst_tr = instances[selected_instance];

            // 6) Add as a volume with its own transformation (geometry stays centered at origin).
            ModelVolume* vol = obj->add_volume(TriangleMesh(std::move(its)), vol_type, false);
            if (vol == nullptr) throw std::runtime_error("Text geometry could not be added to the target model");
            vol->set_transformation(Geometry::Transformation(inst_tr.inverse() * world_tr));
            if (vol_type == ModelVolumeType::MODEL_PART && surface_extruder > 0)
                vol->config.set("extruder", surface_extruder);
            vol->name = text;
            obj->invalidate_bounding_box();
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1emboss_1svg(
        JNIEnv* env, jclass, jlong ptr, jint object_index, jstring svg_path, jfloat width, jfloat depth,
        jint operation, jdouble px, jdouble py, jdouble pz, jdouble nx, jdouble ny, jdouble nz) {
        try {
            ModelRef* ref = (ModelRef*) (intptr_t) ptr;
            if (ref == nullptr || svg_path == nullptr || object_index < 0 ||
                static_cast<size_t>(object_index) >= ref->model.objects.size())
                throw std::invalid_argument("SVG-Zielmodell ist nicht mehr verfügbar");
            if (!std::isfinite(width) || width < 0.5f || width > 500.f ||
                !std::isfinite(depth) || depth < 0.1f || depth > 100.f || operation < 0 || operation > 1 ||
                !std::isfinite(px) || !std::isfinite(py) || !std::isfinite(pz) ||
                !std::isfinite(nx) || !std::isfinite(ny) || !std::isfinite(nz))
                throw std::invalid_argument("SVG-Abmessungen oder Platzierung sind ungültig");

            ModelObject* target = ref->model.objects[object_index];
            const double normal_length_squared = nx * nx + ny * ny + nz * nz;
            if (target == nullptr || target->instances.empty() || target->volumes.empty() ||
                !std::isfinite(normal_length_squared) || normal_length_squared <= 1e-18 ||
                std::any_of(target->instances.begin(), target->instances.end(), [](const ModelInstance* instance) {
                    return instance == nullptr;
                }) || std::any_of(target->volumes.begin(), target->volumes.end(), [](const ModelVolume* volume) {
                    return volume == nullptr;
                })) throw std::runtime_error("Das Zielmodell besitzt keine gültige druckbare Oberfläche");

            const char* path_chars = env->GetStringUTFChars(svg_path, JNI_FALSE);
            if (path_chars == nullptr) return;
            EmbossShape::SvgFile source_svg{std::string(path_chars)};
            env->ReleaseStringUTFChars(svg_path, path_chars);
            NSVGimage* image = init_image(source_svg);
            if (image == nullptr || !std::isfinite(image->width) || image->width <= 1e-6f)
                throw std::runtime_error("SVG-Datei kann nicht gelesen oder geparst werden");

            // Convert a 0.05 mm chord error to SVG units, with a defensive upper bound for
            // unusually large viewBoxes. A huge fixed tolerance collapses curved paths to
            // coarse polygons, which is especially visible in lettering and logos.
            const double tessellation_tolerance = std::clamp(
                static_cast<double>(image->width) * 0.05 / static_cast<double>(width), 0.01, 10.0);
            NSVGLineParams params(tessellation_tolerance);
            EmbossShape shape;
            shape.shapes_with_ids = create_shape_with_ids(*image, params);
            if (shape.shapes_with_ids.empty())
                throw std::runtime_error("SVG enthält keine druckbaren Flächen");

            // NanoSVG's parsed width is in millimetres. The engine stores polygon points in
            // SCALING_FACTOR units, so scaling the projection preserves a user-selected print width.
            const double scale = static_cast<double>(SCALING_FACTOR) *
                static_cast<double>(width) / static_cast<double>(image->width);
            if (!std::isfinite(scale) || scale <= 0.0)
                throw std::runtime_error("SVG-Breite ist ungültig");
            ExPolygons outlines = union_with_delta(shape, Emboss::UNION_DELTA, Emboss::UNION_MAX_ITERATIN);
            if (outlines.empty()) throw std::runtime_error("SVG-Konturen konnten nicht verbunden werden");

            auto project_z = std::make_unique<Emboss::ProjectZ>(static_cast<double>(depth) / scale);
            Emboss::ProjectScale projection(std::move(project_z), scale);
            indexed_triangle_set mesh = Emboss::polygons2model(outlines, projection);
            if (mesh.indices.empty()) throw std::runtime_error("SVG erzeugte kein geschlossenes Prägemodell");

            // Center on the picked point and sink half the depth into the target for a clean
            // union/subtraction. The SVG's proportions and holes are preserved by native vector geometry.
            const BoundingBoxf3 mesh_bounds = bounding_box(mesh);
            const Vec3d center = mesh_bounds.center();
            its_translate(mesh, Vec3f(static_cast<float>(-center.x()), static_cast<float>(-center.y()),
                                     static_cast<float>(-depth / 2.0f)));

            Vec3d position(px, py, pz);
            Vec3d normal(nx, ny, nz);
            normal.normalize();
            const Transform3d world_transform = Emboss::create_transformation_onto_surface(position, normal);
            const std::vector<Transform3d> instances = model_instance_transforms(*target);
            if (instances.empty()) throw std::runtime_error("Das Zielmodell hat keine platzierte Instanz");
            size_t instance_index = 0;
            if (instances.size() > 1) {
                TriangleMesh target_mesh = target->raw_mesh();
                if (target_mesh.its.vertices.empty() || target_mesh.its.indices.empty())
                    throw std::runtime_error("Das Zielmodell hat keine druckbare Oberfläche");
                AABBMesh raycaster(target_mesh);
                const BoundingBoxf3 bounds = bounding_box(target_mesh.its);
                const double ray_distance = std::max(100., 4. * (bounds.max - bounds.min).norm() + 10.);
                instance_index = find_instance_for_world_surface(instances, raycaster, position, normal, ray_distance);
                if (instance_index == instances.size())
                    throw std::runtime_error("Bitte die SVG auf eine sichtbare Modellfläche setzen");
            }
            const Transform3d instance_transform = instances[instance_index];
            const ModelVolumeType volume_type = operation == 0 ?
                ModelVolumeType::MODEL_PART : ModelVolumeType::NEGATIVE_VOLUME;
            ModelVolume* volume = target->add_volume(TriangleMesh(std::move(mesh)), volume_type, false);
            if (volume == nullptr) throw std::runtime_error("SVG konnte dem Zielmodell nicht hinzugefügt werden");
            volume->set_transformation(Geometry::Transformation(instance_transform.inverse() * world_transform));
            volume->name = operation == 0 ? "SVG emboss" : "SVG engraving";
            if (operation == 0) {
                for (const ModelVolume* part : target->volumes) {
                    if (part != nullptr && part != volume && part->is_model_part() && part->extruder_id() > 0) {
                        volume->config.set("extruder", part->extruder_id());
                        break;
                    }
                }
            }
            target->invalidate_bounding_box();
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
        }
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1combine_1objects_1as_1parts(
        JNIEnv* env, jclass, jlong ptr, jintArray indices_arr) {
        try {
            ModelRef* ref = (ModelRef*) (intptr_t) ptr;
            if (ref == nullptr || indices_arr == nullptr) return -1;
            const jsize count = env->GetArrayLength(indices_arr);
            if (count < 2) return -1;
            jint* raw = env->GetIntArrayElements(indices_arr, JNI_FALSE);
            if (raw == nullptr) return -1;
            std::vector<int> indices(raw, raw + count);
            env->ReleaseIntArrayElements(indices_arr, raw, JNI_ABORT);
            std::unordered_set<int> unique_indices;
            for (int index : indices)
                if (index < 0 || index >= (int) ref->model.objects.size() || !unique_indices.insert(index).second)
                    return -1;

            const int target_index = indices.front();
            ModelObject* target = ref->model.objects[target_index];
            auto has_invertible_finite_transform = [](const Transform3d& transform) {
                if (!transform.matrix().allFinite()) return false;
                const double determinant = transform.linear().determinant();
                return std::isfinite(determinant) && std::fabs(determinant) > 1e-12;
            };
            auto has_valid_join_state = [](const ModelObject* object) {
                return object != nullptr && !object->instances.empty() && !object->volumes.empty() &&
                    std::none_of(object->instances.begin(), object->instances.end(), [](const ModelInstance* instance) {
                        return instance == nullptr;
                    }) &&
                    std::none_of(object->volumes.begin(), object->volumes.end(), [](const ModelVolume* volume) {
                        return volume == nullptr;
                    });
            };
            if (!has_valid_join_state(target)) return -2;
            const Transform3d target_world = target->instances.front()->get_matrix();
            if (!has_invertible_finite_transform(target_world)) return -2;
            const Transform3d world_to_target = target_world.inverse();
            if (!has_invertible_finite_transform(world_to_target)) return -2;
            // Validate the entire operation before mutating the destination object. Linked
            // instances are materialized as parts below, so every placed copy remains visible
            // after the joined object is reduced to one instance.
            for (size_t n = 1; n < indices.size(); ++n) {
                ModelObject* source = ref->model.objects[indices[n]];
                if (!has_valid_join_state(source)) return -2;
            }
            auto has_valid_volume_transforms = [&](const ModelObject* object) {
                for (const ModelInstance* instance : object->instances) {
                    if (!has_invertible_finite_transform(instance->get_matrix())) return false;
                    for (const ModelVolume* volume : object->volumes) {
                        const Transform3d world_volume = instance->get_matrix() * volume->get_matrix();
                        if (!has_invertible_finite_transform(volume->get_matrix()) ||
                            !has_invertible_finite_transform(world_volume)) return false;
                    }
                }
                return true;
            };
            if (!has_valid_volume_transforms(target)) return -2;
            for (size_t n = 1; n < indices.size(); ++n)
                if (!has_valid_volume_transforms(ref->model.objects[indices[n]])) return -2;
            // Build all copied volumes off-object first. Appending incrementally would expose a
            // partial join if copying/configuration throws; deleting those volumes as rollback is
            // also unsafe because ModelObject::delete_volume collapses the last volume transform
            // into the instance. Reserve and prepare the new name before the no-throw commit.
            std::vector<std::unique_ptr<ModelVolume>> prepared_volumes;
            auto append_instance_volumes = [&](const ModelObject& source, const Transform3d& source_world) {
                for (const ModelVolume* volume : source.volumes) {
                    auto copied = target->prepare_volume_copy(*volume);
                    // The parent object changes during grouping; pin the source's effective tool
                    // on its copied volume so it does not inherit the target object's tool.
                    if (volume->is_model_part())
                        copied->config.set("extruder", std::max(1, volume->extruder_id()));
                    copied->set_transformation(Geometry::Transformation(
                        world_to_target * source_world * volume->get_matrix()));
                    copied->source.object_idx = target_index;
                    copied->source.volume_idx = (int) (target->volumes.size() + prepared_volumes.size());
                    prepared_volumes.push_back(std::move(copied));
                }
            };
            // The target's first instance already exists as the destination object's transform.
            // Convert each additional linked copy into new volumes before dropping those instances.
            for (size_t instance_index = 1; instance_index < target->instances.size(); ++instance_index)
                append_instance_volumes(*target, target->instances[instance_index]->get_matrix());
            for (size_t n = 1; n < indices.size(); ++n) {
                ModelObject* source = ref->model.objects[indices[n]];
                for (const ModelInstance* instance : source->instances)
                    append_instance_volumes(*source, instance->get_matrix());
            }
            size_t absorbed_instances = target->instances.size() - 1;
            for (size_t n = 1; n < indices.size(); ++n)
                absorbed_instances += ref->model.objects[indices[n]]->instances.size();
            std::string combined_name = target->name + " + " + std::to_string(absorbed_instances);
            target->volumes.reserve(target->volumes.size() + prepared_volumes.size());
            // Keep the existing project-backup behavior while taking the snapshot before the
            // mutation, so a failed backup cannot leave a partially committed join.
            Slic3r::save_object_mesh(*target);
            for (auto& volume : prepared_volumes)
                target->volumes.push_back(volume.release());
            while (target->instances.size() > 1)
                target->delete_last_instance();
            target->name.swap(combined_name);
            target->invalidate_bounding_box();
            std::vector<int> deletion_indices;
            deletion_indices.reserve(indices.size() - 1);
            for (int index : indices)
                if (index != target_index) deletion_indices.push_back(index);
            std::sort(deletion_indices.begin(), deletion_indices.end(),
                [](int left, int right) { return left > right; });
            const int result_index = target_index - (int) std::count_if(
                deletion_indices.begin(), deletion_indices.end(),
                [target_index](int index) { return index < target_index; });
            for (int index : deletion_indices) ref->model.delete_object(index);
            // Deleting objects before the chosen target shifts its model index. Keep all
            // volume source references aligned with the final index used by the renderer.
            for (size_t volume_index = 0; volume_index < target->volumes.size(); ++volume_index) {
                target->volumes[volume_index]->source.object_idx = result_index;
                target->volumes[volume_index]->source.volume_idx = (int) volume_index;
            }
            return result_index;
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
            return -3;
        }
    }

    // Boolean operations work on transformed copies and only replace source objects after
    // CGAL reports a valid, closed solid. This avoids mutating project state on failed CSG.
    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1boolean_1objects(
        JNIEnv* env, jclass, jlong ptr, jintArray indices_arr, jint operation) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        const size_t original_object_count = ref == nullptr ? 0 : ref->model.objects.size();
        bool source_objects_replaced = false;
        auto rollback_uncommitted_result = [&]() noexcept {
            if (ref == nullptr || source_objects_replaced) return;
            try {
                // add_object() appends before all per-object configuration is finished. If any
                // later setup throws, remove only objects appended by this Boolean transaction.
                while (ref->model.objects.size() > original_object_count)
                    ref->model.delete_object(ref->model.objects.size() - 1);
            } catch (...) {
                __android_log_print(ANDROID_LOG_ERROR, "ModelBoolean",
                    "Could not roll back an incomplete Boolean result object");
            }
        };
        try {
            if (ref == nullptr || indices_arr == nullptr || operation < 0 || operation > 2) return -1;
            const jsize count = env->GetArrayLength(indices_arr);
            if (count < 2) return -1;
            jint* raw = env->GetIntArrayElements(indices_arr, JNI_FALSE);
            if (raw == nullptr) return -1;
            std::vector<int> indices(raw, raw + count);
            env->ReleaseIntArrayElements(indices_arr, raw, JNI_ABORT);
            std::unordered_set<int> uniqueIndices;
            for (int index : indices) {
                if (index < 0 || index >= (int) ref->model.objects.size() || !uniqueIndices.insert(index).second)
                    return -1;
            }

            ModelObject* target = ref->model.objects[indices.front()];
            auto has_valid_boolean_state = [](const ModelObject* object) {
                if (object == nullptr || object->instances.empty() || object->volumes.empty() ||
                    std::any_of(object->instances.begin(), object->instances.end(), [](const ModelInstance* instance) {
                        return instance == nullptr || !instance->get_matrix().matrix().allFinite();
                    }) ||
                    std::any_of(object->volumes.begin(), object->volumes.end(), [](const ModelVolume* volume) {
                        return volume == nullptr || !volume->get_matrix().matrix().allFinite();
                    })) return false;
                return true;
            };
            if (!has_valid_boolean_state(target)) return -2;

            auto valid_operand = [](const TriangleMesh& mesh) {
                return !mesh.empty() && mesh.stats().open_edges == 0 &&
                    std::isfinite(mesh.stats().volume) && std::fabs(mesh.stats().volume) > 1e-9f;
            };
            auto valid_cgal_operand = [](const MeshBoolean::cgal::CGALMesh& mesh) {
                return !MeshBoolean::cgal::empty(mesh) &&
                    MeshBoolean::cgal::does_bound_a_volume(mesh) &&
                    !MeshBoolean::cgal::does_self_intersect(mesh);
            };
            auto printable_mesh = [](const ModelObject& object) {
                const bool has_negative_volume = std::any_of(
                    object.volumes.begin(), object.volumes.end(), [](const ModelVolume* volume) {
                        return volume != nullptr && volume->is_negative_volume();
                    });
                if (!has_negative_volume) return object.mesh();

                // ModelObject::mesh() intentionally contains only positive model parts. For
                // Boolean inputs with engravings/cutouts, evaluate the same per-instance CSG
                // expression used by slicing so those existing negative volumes are not silently
                // restored or discarded by the resulting Boolean solid.
                MeshBoolean::cgal::CGALMeshPtr combined;
                for (const ModelInstance* instance : object.instances) {
                    if (instance == nullptr) return TriangleMesh{};
                    std::vector<csg::CSGPart> parts;
                    csg::model_to_csgmesh(
                        object, instance->get_matrix(), std::back_inserter(parts),
                        csg::mpartsPositive | csg::mpartsNegative);
                    if (parts.empty()) return TriangleMesh{};
                    auto instance_result = csg::perform_csgmesh_booleans(range(parts));
                    if (!instance_result || MeshBoolean::cgal::empty(*instance_result) ||
                        !MeshBoolean::cgal::does_bound_a_volume(*instance_result) ||
                        MeshBoolean::cgal::does_self_intersect(*instance_result)) return TriangleMesh{};
                    if (!combined) {
                        combined = std::move(instance_result);
                    } else {
                        MeshBoolean::cgal::plus(*combined, *instance_result);
                        if (MeshBoolean::cgal::empty(*combined) ||
                            !MeshBoolean::cgal::does_bound_a_volume(*combined) ||
                            MeshBoolean::cgal::does_self_intersect(*combined)) return TriangleMesh{};
                    }
                }
                return combined ? MeshBoolean::cgal::cgal_to_triangle_mesh(*combined) : TriangleMesh{};
            };
            TriangleMesh resultMesh = printable_mesh(*target);
            if (!valid_operand(resultMesh)) return -3;
            double previousVolume = std::fabs(resultMesh.volume());
            auto applyBoolean = [operation](auto& result, auto& tool) {
                switch (operation) {
                    case 0: MeshBoolean::cgal::plus(result, tool); break;
                    case 1: MeshBoolean::cgal::minus(result, tool); break;
                    case 2: MeshBoolean::cgal::intersect(result, tool); break;
                }
            };
            auto validateStep = [&](TriangleMesh& stepMesh) {
                if (stepMesh.empty() || stepMesh.stats().open_edges != 0 ||
                    !std::isfinite(stepMesh.volume()) || std::fabs(stepMesh.volume()) <= 1e-9f)
                    return false;
                const double stepVolume = std::fabs(stepMesh.volume());
                const double tolerance = std::max(1e-5, previousVolume * 1e-6);
                if (operation == 0 && stepVolume < previousVolume - tolerance)
                    return false; // A union must not lose volume from its accumulated result.
                if (operation == 1 && stepVolume >= previousVolume - tolerance)
                    return false; // Do not silently accept a sequence that did not cut its target.
                if (operation == 2 && stepVolume > previousVolume + tolerance)
                    return false; // An intersection must not add material to its accumulated result.
                previousVolume = stepVolume;
                return true;
            };
            if (indices.size() == 2) {
                // Keep the common single-operation path on EPIC for its lower cost.
                auto resultCgal = MeshBoolean::cgal::triangle_mesh_to_cgal(resultMesh);
                if (!resultCgal || !valid_cgal_operand(*resultCgal)) return -3;
                ModelObject* tool = ref->model.objects[indices[1]];
                if (!has_valid_boolean_state(tool)) return -2;
                TriangleMesh toolMesh = printable_mesh(*tool);
                if (!valid_operand(toolMesh)) return -3;
                auto toolCgal = MeshBoolean::cgal::triangle_mesh_to_cgal(toolMesh);
                if (!toolCgal || !valid_cgal_operand(*toolCgal)) return -3;
                applyBoolean(*resultCgal, *toolCgal);
                if (MeshBoolean::cgal::empty(*resultCgal) ||
                    !MeshBoolean::cgal::does_bound_a_volume(*resultCgal) ||
                    MeshBoolean::cgal::does_self_intersect(*resultCgal)) return -4;
                resultMesh = MeshBoolean::cgal::cgal_to_triangle_mesh(*resultCgal);
                if (!validateStep(resultMesh)) return operation == 1 ? -7 : -4;
            } else {
                // Keep intermediate multi-operand results in CGAL instead of
                // round-tripping through the slicer's float mesh after every step.
                auto resultCgal = MeshBoolean::cgal::triangle_mesh_to_cgal(resultMesh);
                if (!resultCgal || !valid_cgal_operand(*resultCgal)) return -3;
                for (size_t n = 1; n < indices.size(); ++n) {
                    ModelObject* tool = ref->model.objects[indices[n]];
                    if (!has_valid_boolean_state(tool)) return -2;
                    TriangleMesh toolMesh = printable_mesh(*tool);
                    if (!valid_operand(toolMesh)) return -3;
                    auto toolCgal = MeshBoolean::cgal::triangle_mesh_to_cgal(toolMesh);
                    if (!toolCgal || !valid_cgal_operand(*toolCgal)) return -3;
                    applyBoolean(*resultCgal, *toolCgal);
                    if (MeshBoolean::cgal::empty(*resultCgal) ||
                        !MeshBoolean::cgal::does_bound_a_volume(*resultCgal) ||
                        MeshBoolean::cgal::does_self_intersect(*resultCgal)) return -4;
                    TriangleMesh stepMesh = MeshBoolean::cgal::cgal_to_triangle_mesh(*resultCgal);
                    if (!validateStep(stepMesh)) return operation == 1 ? -7 : -4;
                }
                resultMesh = MeshBoolean::cgal::cgal_to_triangle_mesh(*resultCgal);
            }

            // Boolean output is one solid, so it takes the target object's profile and
            // material assignment. Preserve its world-space geometry as the new object.
            const std::string resultName = target->name.empty() ? "Boolean result" : target->name + " (Boolean)";
            const bool printable = target->printable;
            int targetExtruder = 1;
            for (const ModelVolume* volume : target->volumes) {
                if (volume != nullptr && volume->is_model_part()) {
                    targetExtruder = std::max(1, volume->extruder_id());
                    break;
                }
            }
            // A CSG result is newly generated geometry, not a mesh from one source
            // object. Do not serialize the target's source path/object index onto it.
            ModelObject* replacement = ref->model.add_object(resultName.c_str(), "", std::move(resultMesh));
            if (replacement == nullptr || replacement->volumes.empty()) {
                rollback_uncommitted_result();
                return -5;
            }
            // resultMesh is already in world coordinates (ModelObject::mesh applies
            // each source instance). A replacement ModelObject created by add_object
            // has no instances, so add an identity instance to keep it visible and
            // make its bounds/slicing state valid after the two source objects go away.
            replacement->add_instance();
            replacement->config.assign_config(target->config);
            replacement->printable = printable;
            replacement->volumes.front()->config.set("extruder", targetExtruder);
            replacement->volumes.front()->source.input_file.clear();
            replacement->volumes.front()->source.object_idx = -1;
            replacement->volumes.front()->source.volume_idx = -1;
            // Remap one source volume/instance at a time to bound peak memory for large meshes.
            // Boolean output is in world coordinates, so transform the target's saved facet
            // annotations into that frame before mapping them to the new triangulated surface.
            for (const ModelVolume* volume : target->volumes) {
                if (volume == nullptr || !volume->is_model_part()) continue;
                const auto saved = volume->save_painting();
                if (!saved) continue;
                for (const ModelInstance* instance : target->instances) {
                    if (instance == nullptr) {
                        rollback_uncommitted_result();
                        return -2;
                    }
                    TriangleSelector::SavedPainting world_painting = *saved;
                    world_painting.mesh.transform(volume->get_matrix(), true);
                    world_painting.mesh.transform(instance->get_matrix(), true);
                    replacement->volumes.front()->restore_painting(world_painting, true);
                }
            }

            std::sort(indices.begin(), indices.end(), std::greater<int>());
            for (int index : indices) ref->model.delete_object((size_t) index);
            source_objects_replaced = true;
            return (jint) ref->model.objects.size() - 1;
        } catch (const std::exception& e) {
            rollback_uncommitted_result();
            __android_log_print(ANDROID_LOG_ERROR, "ModelBoolean", "Boolean operation failed without replacing inputs: %s", e.what());
            return -6;
        } catch (...) {
            rollback_uncommitted_result();
            __android_log_print(ANDROID_LOG_ERROR, "ModelBoolean", "Boolean operation failed without replacing inputs");
            return -6;
        }
    }

    JNIEXPORT jdouble JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1object_1volume(
        JNIEnv*, jclass, jlong ptr, jint objectIndex) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        if (ref == nullptr || objectIndex < 0 || objectIndex >= (int) ref->model.objects.size()) return 0.0;
        return (jdouble) ref->model.objects[objectIndex]->get_object_stl_stats().volume;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1slice(JNIEnv* env, jclass, jlong ptr, jstring configPath, jstring path, jobject listener, jint numFilaments, jintArray colorsArr, jint calibMode, jdouble calibStart, jdouble calibEnd, jdouble calibStep) {
        try {
            ModelRef* model = (ModelRef*) (intptr_t) ptr;

            Print print;
            DynamicPrintConfig config;
            const char *chars = env->GetStringUTFChars(configPath, JNI_FALSE);
            config.load(std::string(chars), ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(configPath, chars);
            config.normalize_fdm();

            // Legacy bed_temperature keys migrate to hot_plate_temp. When an older profile omits
            // curr_bed_type, keep Orkan's established PEI/high-temperature plate fallback.
            if (config.option("curr_bed_type", false) == nullptr)
                config.set_key_value("curr_bed_type", new ConfigOptionEnum<BedType>(btPEI));

            // An imported project config can itself declare multiple filaments (e.g. a Bambu 3MF's
            // project_settings carries 8-entry filament_colour/filament_diameter vectors) while other
            // per-filament vectors it carries stay single-entry (filament_map, filament_self_index,
            // filament_is_support, ...). Desktop's preset layer keeps these consistent; here we must
            // do it ourselves: adopt the config's filament count so the consistency fixups below run
            // even when the app didn't request multicolor painting. Otherwise the engine's wipe
            // tower / flush-volume ordering indexes the short vectors out of bounds → SIGSEGV.
            {
                int cfgFilaments = 0;
                if (auto* fc = dynamic_cast<const ConfigOptionStrings*>(config.option("filament_colour", false)))
                    cfgFilaments = std::max(cfgFilaments, (int) fc->values.size());
                if (auto* fd = dynamic_cast<const ConfigOptionFloats*>(config.option("filament_diameter", false)))
                    cfgFilaments = std::max(cfgFilaments, (int) fd->values.size());
                if (cfgFilaments > numFilaments) {
                    __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "config declares %d filaments (app requested %d); normalizing", cfgFilaments, (int) numFilaments);
                    numFilaments = cfgFilaments;
                }
            }

            // Multi-color: declare N filaments (resizing all filament vectors) + their colors so the
            // engine runs MMU segmentation on the painted facets and emits tool changes.
            if (numFilaments > 1) {
                // The painting may reference filament indices beyond the palette (e.g. leftover paint
                // from a larger palette). Cover the highest painted filament so export never indexes
                // a per-filament vector out of range.
                int maxPaintedState = 0;
                for (auto* mo : model->model.objects) {
                    __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "object: %s, layer_config_ranges.size=%zu", mo->name.c_str(), mo->layer_config_ranges.size());
                    for (auto* v : mo->volumes) {
                        const auto& us = v->mmu_segmentation_facets.get_data().used_states;
                        int volMax = 0;
                        for (int i = (int) us.size() - 1; i >= 1; --i) { if (us[i]) { if (i > volMax) volMax = i; break; } }
                        __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "volume: %s, maxPainted=%d", v->name.c_str(), volMax);
                        if (volMax > maxPaintedState) maxPaintedState = volMax;
                    }
                }
                if (maxPaintedState > numFilaments) numFilaments = maxPaintedState;

                config.set_num_filaments((unsigned int) numFilaments);

                // Resize ONLY per-filament vectors. We EXCLUDE printer-extruder vectors like 
                // nozzle_diameter to avoid SIGSEGV in DynamicPrintConfig::update_values_to_printer_extruders.
                // In SEMM mode, the engine knows to use nozzle_diameter[0] for all filaments.
                auto cloneToN = [&](ConfigOption* opt) {
                    if (auto* o = dynamic_cast<ConfigOptionFloats*>(opt))                { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                    else if (auto* o = dynamic_cast<ConfigOptionInts*>(opt))             { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                    else if (auto* o = dynamic_cast<ConfigOptionStrings*>(opt))          { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                    else if (auto* o = dynamic_cast<ConfigOptionBools*>(opt))            { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                    else if (auto* o = dynamic_cast<ConfigOptionPercents*>(opt))         { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                    else if (auto* o = dynamic_cast<ConfigOptionFloatsOrPercents*>(opt)) { if (!o->values.empty()) o->values.resize(numFilaments, o->values[0]); }
                };

                // Filament options that don't start with "filament_" prefix.
                static const std::set<std::string> extraFilamentOpts = {
                    "nozzle_temperature", "nozzle_temperature_initial_layer",
                    "nozzle_temperature_range_low", "nozzle_temperature_range_high",
                    "cool_plate_temp", "cool_plate_temp_initial_layer",
                    "eng_plate_temp", "eng_plate_temp_initial_layer",
                    "hot_plate_temp", "hot_plate_temp_initial_layer",
                    "textured_plate_temp", "textured_plate_temp_initial_layer",
                    "chamber_temperature", "chamber_minimal_temperature",
                    "full_fan_speed_layer", "bridge_fan_speed", "max_fan_speed", "min_fan_speed",
                    "slow_down_layer_time", "fan_below_layer_time"
                };

                for (const std::string& key : config.keys()) {
                    bool isFilament = key.rfind("filament_", 0) == 0 || extraFilamentOpts.count(key) > 0;
                    if (isFilament) {
                        // Skip identity/palette fields during cloning (set explicitly below).
                        if (key == "filament_settings_id" || key == "filament_self_index" || key == "filament_colour" || 
                            key == "filament_multi_colour" || key == "filament_map") continue;
                        ConfigOption* opt = config.option(key, false);
                        if (opt && opt->is_vector()) cloneToN(opt);
                    }
                }

                // Override the colors with the user's palette (padded to N by repeating the last color).
                if (colorsArr != nullptr) {
                    jsize n = env->GetArrayLength(colorsArr);
                    jint* c = env->GetIntArrayElements(colorsArr, JNI_FALSE);
                    std::vector<std::string> colorStrs;
                    std::vector<std::string> colorTypes;
                    for (int i = 0; i < numFilaments; ++i) {
                        int src = (i < n) ? i : (n > 0 ? n - 1 : 0);
                        char buf[8];
                        snprintf(buf, sizeof(buf), "#%06X", (n > 0 ? c[src] : 0xFFFFFF) & 0xFFFFFF);
                        colorStrs.emplace_back(buf);
                        colorTypes.emplace_back("1"); // default color type
                    }
                    env->ReleaseIntArrayElements(colorsArr, c, JNI_ABORT);
                    if (!colorStrs.empty()) {
                        config.set_key_value("filament_colour", new ConfigOptionStrings(colorStrs));
                        config.set_key_value("filament_multi_colour", new ConfigOptionStrings(colorStrs));
                        config.set_key_value("filament_colour_type", new ConfigOptionStrings(colorTypes));
                    }
                }

                // filament_self_index must be 1..N. If all filaments have the same index (e.g. 1),
                // the engine merges them and emits no tool changes.
                {
                    std::vector<int> selfIdx(numFilaments);
                    for (int i = 0; i < numFilaments; ++i) selfIdx[i] = i + 1;
                    config.set_key_value("filament_self_index", new ConfigOptionInts(selfIdx));
                }

                // filament_settings_id should also be unique to prevent merging.
                {
                    std::vector<std::string> ids;
                    for (int i = 0; i < numFilaments; ++i) {
                        char buf[32];
                        snprintf(buf, sizeof(buf), "Filament %d", i + 1);
                        ids.emplace_back(buf);
                    }
                    config.set_key_value("filament_settings_id", new ConfigOptionStrings(ids));
                }

                // filament_map must map all virtual filaments back to the physical extruder (1).
                // Without this, update_values_to_printer_extruders crashes if it tries to index 
                // beyond the physical extruder count.
                {
                    std::vector<int> filamentMap(numFilaments, 1);
                    config.set_key_value("filament_map", new ConfigOptionInts(filamentMap));
                }

                // Flush (purge) volumes are project-scoped options that preset INIs never carry,
                // and the engine's default flush_volumes_matrix is sized for exactly 4 filaments.
                // GCode::append_full_config requires matrix.size() == N^2 * flush_multiplier.size()
                // and throws otherwise, so rebuild the flush vectors whenever the sizes disagree.
                {
                    // The engine (ToolOrdering::reorder_extruders_for_minimum_flush_volume,
                    // Print::_make_wipe_tower) treats flush_volumes_matrix as `nozzle_diameter.size()`
                    // blocks, each a `filament_colour.size()` x `filament_colour.size()` matrix. If our
                    // matrix is sized differently the engine slices past the end → SIGSEGV. Size it to
                    // exactly that layout, keyed off filament_colour so the per-nozzle dimension matches
                    // the engine's `number_of_extruders`.
                    size_t n = (size_t) numFilaments;
                    if (auto* fc = dynamic_cast<const ConfigOptionStrings*>(config.option("filament_colour", false)))
                        n = std::max(n, fc->values.size());

                    size_t nozzles = 1;
                    if (auto* nd = dynamic_cast<const ConfigOptionFloats*>(config.option("nozzle_diameter", false)))
                        if (!nd->values.empty()) nozzles = nd->values.size();

                    // Engine default volumes: 280mm^3 between distinct filaments, 0 on the diagonal.
                    std::vector<double> matrix(n * n * nozzles, 280.);
                    for (size_t z = 0; z < nozzles; ++z)
                        for (size_t i = 0; i < n; ++i)
                            matrix[z * n * n + i * n + i] = 0.;
                    config.set_key_value("flush_volumes_matrix", new ConfigOptionFloats(matrix));

                    // flush_multiplier is indexed per nozzle; one entry per nozzle.
                    auto* fm = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_multiplier", false));
                    if (fm == nullptr || fm->values.size() != nozzles)
                        config.set_key_value("flush_multiplier", new ConfigOptionFloats(std::vector<double>(nozzles, 0.3)));

                    auto* fv = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_volumes_vector", false));
                    if (fv == nullptr || fv->values.size() != n * 2)
                        config.set_key_value("flush_volumes_vector", new ConfigOptionFloats(std::vector<double>(n * 2, 140.)));
                }

                const ConfigOption* nd = config.option("nozzle_diameter");
                if (nd && static_cast<const ConfigOptionFloats*>(nd)->values.size() <= 1) {
                    config.set_key_value("single_extruder_multi_material", new ConfigOptionBool(true));
                    if (config.option("single_extruder_multi_material_priming", false) == nullptr) {
                        config.set_key_value("single_extruder_multi_material_priming", new ConfigOptionBool(true));
                    }
                }

                // Enable a prime/wipe tower for reliable filament changes.
                if (config.option("enable_prime_tower", false) == nullptr) {
                    config.set_key_value("enable_prime_tower", new ConfigOptionBool(true));
                }

                int fdSize = 0;
                if (auto* fd = dynamic_cast<const ConfigOptionFloats*>(config.option("filament_diameter", false))) fdSize = (int) fd->values.size();
                int fcSize = 0, mxSize = 0, ndSize = 0, fmSize = 0;
                if (auto* o = dynamic_cast<const ConfigOptionStrings*>(config.option("filament_colour", false))) fcSize = (int) o->values.size();
                if (auto* o = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_volumes_matrix", false))) mxSize = (int) o->values.size();
                if (auto* o = dynamic_cast<const ConfigOptionFloats*>(config.option("nozzle_diameter", false))) ndSize = (int) o->values.size();
                if (auto* o = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_multiplier", false))) fmSize = (int) o->values.size();
                __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "multicolor slice: numFilaments=%d maxPainted=%d filament_diameter.size=%d filament_colour.size=%d flush_matrix.size=%d nozzle_diameter.size=%d flush_multiplier.size=%d", (int) numFilaments, maxPaintedState, fdSize, fcSize, mxSize, ndSize, fmSize);
            }

            // Always normalize the purge/flush matrix to the engine's expected layout:
            // nozzle_diameter.size() blocks, each filament_colour.size() x filament_colour.size().
            // Imported project configs (e.g. a Bambu 3MF) can carry a wipe tower with a mis-sized
            // matrix even when the app's multicolor path above didn't run, which makes
            // _make_wipe_tower / reorder_extruders_for_minimum_flush_volume index out of bounds.
            {
                size_t fc = 0;
                if (auto* o = dynamic_cast<const ConfigOptionStrings*>(config.option("filament_colour", false)))
                    fc = o->values.size();
                if (fc > 1) {
                    size_t nozzles = 1;
                    if (auto* nd = dynamic_cast<const ConfigOptionFloats*>(config.option("nozzle_diameter", false)))
                        if (!nd->values.empty()) nozzles = nd->values.size();

                    auto* mx = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_volumes_matrix", false));
                    if (mx == nullptr || mx->values.size() != fc * fc * nozzles) {
                        __android_log_print(ANDROID_LOG_WARN, "BeamPaint",
                            "normalizing flush matrix: filament_colour=%zu nozzles=%zu old_matrix=%d -> %zu",
                            fc, nozzles, mx ? (int) mx->values.size() : -1, fc * fc * nozzles);
                        std::vector<double> matrix(fc * fc * nozzles, 280.);
                        for (size_t z = 0; z < nozzles; ++z)
                            for (size_t i = 0; i < fc; ++i)
                                matrix[z * fc * fc + i * fc + i] = 0.;
                        config.set_key_value("flush_volumes_matrix", new ConfigOptionFloats(matrix));
                    }
                    auto* fm = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_multiplier", false));
                    if (fm == nullptr || fm->values.size() != nozzles)
                        config.set_key_value("flush_multiplier", new ConfigOptionFloats(std::vector<double>(nozzles, 0.3)));
                    auto* fv = dynamic_cast<const ConfigOptionFloats*>(config.option("flush_volumes_vector", false));
                    if (fv == nullptr || fv->values.size() != fc * 2)
                        config.set_key_value("flush_volumes_vector", new ConfigOptionFloats(std::vector<double>(fc * 2, 140.)));
                }
            }

            for (auto* mo : model->model.objects) {
                print.auto_assign_extruders(mo);
            }

            // OrcaSlicer incorrectly suppresses standard tool changes (T0, T1, etc.) if it thinks
            // the printer is a Bambu Lab machine (is_BBL_printer == true). We force it to false
            // to ensure standard G-code emission for regular Klipper/Marlin printers.
            print.is_BBL_printer() = false;

            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: assigned extruders, validating config");

            // OrcaSlicer's config.validate() returns a map of option-key -> error message
            // instead of a single string; flatten it into one message for the Java side.
            std::map<std::string, std::string> config_errors = config.validate();
            if (!config_errors.empty()) {
                std::string err;
                for (const auto& kv : config_errors) {
                    if (!err.empty()) err += "\n";
                    err += kv.second;
                }
                env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), err.c_str());
                return 0;
            }
            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: config valid, applying");
            print.apply(model->model, config);

            // OrcaSlicer calibration: when a calib mode is requested, set the params on the print so
            // the engine generates the calibration test (e.g. PA line pattern) instead of a normal print.
            if (calibMode != 0) {
                Slic3r::Calib_Params cp;
                cp.mode = static_cast<Slic3r::CalibMode>(calibMode);
                cp.start = calibStart;
                cp.end = calibEnd;
                cp.step = calibStep;
                cp.print_numbers = true;
                print.set_calib_params(cp);
            }

            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: applied, print.validate");

            // Keep Orca's non-fatal spiral-vase override warning with the slice result so the
            // existing printer-preparation dialog can show it before sending the G-code.
            StringObjectException validation_warning;
            std::string err = print.validate(&validation_warning).string;
            if (!err.empty()) {
                env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), err.c_str());
                return 0;
            }

            std::thread::id id = std::this_thread::get_id();

            print.set_status_callback([&id, &listener](const Slic3r::PrintBase::SlicingStatus &s) {
                bool needAttach = id != std::this_thread::get_id();

                JNIEnv* e;
                if (staticVM->GetEnv(reinterpret_cast<void **>(&e), JNI_VERSION_1_6) != JNI_OK) {
                    return;
                }
                if (needAttach) {
                    JavaVMAttachArgs args;
                    args.name = nullptr;
                    args.group = nullptr;
                    args.version = JNI_VERSION_1_6;
                    staticVM->AttachCurrentThread(&e, &args);
                }
                e->CallVoidMethod(listener, sliceListenerOnProgress, s.percent, e->NewStringUTF(s.text.c_str()));
                if (needAttach) {
                    staticVM->DetachCurrentThread();
                }
            });
            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: print.process");
            print.process();
            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: processed, export_gcode");

            chars = env->GetStringUTFChars(path, JNI_FALSE);
            GCodeResultRef* resultRef = new GCodeResultRef();
            print.export_gcode(std::string(chars), &resultRef->result, nullptr);
            if (validation_warning.is_warning &&
                (validation_warning.opt_key == "sparse_infill_density" ||
                 validation_warning.opt_key == "wall_loops" ||
                 validation_warning.opt_key == "top_shell_layers"))
                resultRef->spiral_vase_override_warning = validation_warning.string;
            __android_log_print(ANDROID_LOG_WARN, "BeamPaint", "step: exported gcode");
            env->ReleaseStringUTFChars(path, chars);

            resultRef->name = print.output_filename(model->base_name);

            return (jlong) (intptr_t) resultRef;
        } catch (const Slic3r::SlicingErrors& e) {
            std::string message = e.what();
            for (const Slic3r::SlicingError& error : e.errors_)
                message += "\nObject " + std::to_string(error.objectId()) + ": " + error.what();
            __android_log_print(ANDROID_LOG_ERROR, "BeamPaint", "slice exception type=%s what=%s", typeid(e).name(), message.c_str());
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), message.c_str());
            return 0;
        } catch (const std::exception& e) {
            __android_log_print(ANDROID_LOG_ERROR, "BeamPaint", "slice exception type=%s what=%s", typeid(e).name(), e.what());
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), e.what());
            return 0;
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1export_13mf(JNIEnv* env, jclass, jlong ptr, jstring configPath, jstring path) {
        auto model = reinterpret_cast<ModelRef*>(ptr);

        try {
            DynamicPrintConfig config;
            const char *chars = env->GetStringUTFChars(configPath, JNI_FALSE);
            config.load(std::string(chars), ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(configPath, chars);
            config.normalize_fdm();

            const char *pathChars = env->GetStringUTFChars(path, JNI_FALSE);
            Slic3r::store_3mf(pathChars, &model->model, &config, false, nullptr, false);
            env->ReleaseStringUTFChars(path, pathChars);
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), e.what());
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1release(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        delete model;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1load_1file(JNIEnv* env, jclass, jstring path, jstring name) {
        GCodeResultRef* ref = new GCodeResultRef();

        GCodeProcessor processor;
        try {
            const char* chars = env->GetStringUTFChars(path, JNI_FALSE);
            const char* nameChars = env->GetStringUTFChars(name, JNI_FALSE);
            ref->name = std::string(nameChars);

            // process_file()'s second argument is now a cancel callback (std::function<void()>),
            // not a float progress callback.
            processor.process_file(chars, []() {
                // TODO: Support cancellation
            });
            ref->result = std::move(processor.extract_result());

            env->ReleaseStringUTFChars(path, chars);
            env->ReleaseStringUTFChars(name, nameChars);

            return (jlong) (intptr_t) ref;
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
            return 0;
        }
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1recommended_1name(JNIEnv* env, jclass, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        return env->NewStringUTF(ref->name.c_str());
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1spiral_1vase_1override_1warning(JNIEnv* env, jclass, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        if (ref == nullptr || ref->spiral_vase_override_warning.empty()) return nullptr;
        return env->NewStringUTF(ref->spiral_vase_override_warning.c_str());
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1seam_1marker_1count(JNIEnv*, jclass, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        return static_cast<jint>(std::count_if(ref->result.moves.begin(), ref->result.moves.end(),
            [](const GCodeProcessorResult::MoveVertex& move) { return move.type == EMoveType::Seam; }));
    }

    JNIEXPORT jfloat JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1initial_1layer_1time(JNIEnv*, jclass, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        return ref->result.initial_layer_time;
    }

    JNIEXPORT jfloat JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_00024Companion_gcoderesult_1get_1initial_1layer_1time(JNIEnv*, jobject, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        return ref->result.initial_layer_time;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1gcode_1check_1error_1code(JNIEnv*, jclass, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        return static_cast<jint>(ref->result.gcode_check_result.error_code);
    }

    // Maps a Java-side role index (matching libvgcode::EGCodeExtrusionRole ordering) to the
    // engine's Slic3r::ExtrusionRole, which is the key type of used_filaments_per_role.
    ExtrusionRole mapGCodeRole(int index) {
        ExtrusionRole gRole;
        switch (index) {
            default:
            case 0:
                gRole = erNone;
                break;
            case 1:
                gRole = erPerimeter;
                break;
            case 2:
                gRole = erExternalPerimeter;
                break;
            case 3:
                gRole = erOverhangPerimeter;
                break;
            case 4:
                gRole = erInternalInfill;
                break;
            case 5:
                gRole = erSolidInfill;
                break;
            case 6:
                gRole = erTopSolidInfill;
                break;
            case 7:
                gRole = erIroning;
                break;
            case 8:
                gRole = erBridgeInfill;
                break;
            case 9:
                gRole = erGapFill;
                break;
            case 10:
                gRole = erSkirt;
                break;
            case 11:
                gRole = erSupportMaterial;
                break;
            case 12:
                gRole = erSupportMaterialInterface;
                break;
            case 13:
                gRole = erWipeTower;
                break;
            case 14:
                gRole = erCustom;
                break;
        }
        return gRole;
    }

    JNIEXPORT jdouble JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1used_1filament_1mm(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;

        const auto it = ref->result.print_statistics.used_filaments_per_role.find(mapGCodeRole(role));
        if (it == ref->result.print_statistics.used_filaments_per_role.end())
            return 0.0;
        const std::pair<double, double>& info = it->second;
        return info.first * 1000.0 / 25.4;
    }

    JNIEXPORT jdouble JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1used_1filament_1g(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;

        const auto it = ref->result.print_statistics.used_filaments_per_role.find(mapGCodeRole(role));
        if (it == ref->result.print_statistics.used_filaments_per_role.end())
            return 0.0;
        const std::pair<double, double>& info = it->second;
        return info.second;
    }

    JNIEXPORT jdouble JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1wipe_1tower_1filament_1volume(JNIEnv*, jclass, jlong ptr, jint filament_index) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        if (filament_index < 0)
            return 0.0;
        const auto it = ref->result.print_statistics.wipe_tower_volumes_per_extruder.find(static_cast<size_t>(filament_index));
        return it == ref->result.print_statistics.wipe_tower_volumes_per_extruder.end() ? 0.0 : it->second;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1release(JNIEnv* env, jclass, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        delete ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1init_1from_1texts(JNIEnv* env, jclass, jstring name, jstring fsText, jstring vsText) {
        const char* nameChars = env->GetStringUTFChars(name, JNI_FALSE);
        const char* fsChars = env->GetStringUTFChars(fsText, JNI_FALSE);
        const char* vsChars = env->GetStringUTFChars(vsText, JNI_FALSE);
        GLShaderProgram::ShaderSources sources = {};
        sources[static_cast<size_t>(GLShaderProgram::EShaderType::Vertex)] = std::string(vsChars);
        sources[static_cast<size_t>(GLShaderProgram::EShaderType::Fragment)] = std::string(fsChars);
        ShaderRef* ref = new ShaderRef();
        ref->program.init_from_texts(std::string(nameChars), sources);

        env->ReleaseStringUTFChars(name, nameChars);
        env->ReleaseStringUTFChars(fsText, fsChars);
        env->ReleaseStringUTFChars(vsText, vsChars);

        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1create(JNIEnv* env, jclass) {
        GLModelRef* ref = new GLModelRef();
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1raycast_1data(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        if (ref == nullptr) return JNI_FALSE;
        std::lock_guard<std::mutex> lock(ref->picking_mutex);
        if (ref->raycast_tree != nullptr) return JNI_TRUE;
        try {
            auto tree = std::make_unique<AABBTreeIndirect::Tree3f>(
                AABBTreeIndirect::build_aabb_tree_over_indexed_triangle_set(
                    ref->mesh.its.vertices, ref->mesh.its.indices));
            ref->raycast_tree = std::move(tree);
            return JNI_TRUE;
        } catch (const std::exception& e) {
            __android_log_print(ANDROID_LOG_WARN, TAG, "Raycast index build failed: %s", e.what());
            return JNI_FALSE;
        }
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1raycast_1closest_1hit(JNIEnv* env, jclass, jlong ptr, jdoubleArray pointArr, jdoubleArray directionArr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        if (ref == nullptr || pointArr == nullptr || directionArr == nullptr ||
            env->GetArrayLength(pointArr) < 3 || env->GetArrayLength(directionArr) < 3)
            return env->NewDoubleArray(0);
        jdouble* point = env->GetDoubleArrayElements(pointArr, JNI_FALSE);
        if (point == nullptr) return env->NewDoubleArray(0);
        jdouble* direction = env->GetDoubleArrayElements(directionArr, JNI_FALSE);
        if (direction == nullptr) {
            env->ReleaseDoubleArrayElements(pointArr, point, JNI_ABORT);
            return env->NewDoubleArray(0);
        }

        Vec3d point3d(point);
        Vec3d direction3d(direction);
        if (!point3d.allFinite() || !direction3d.allFinite() || direction3d.squaredNorm() <= 1e-24) {
            env->ReleaseDoubleArrayElements(pointArr, point, JNI_ABORT);
            env->ReleaseDoubleArrayElements(directionArr, direction, JNI_ABORT);
            return env->NewDoubleArray(0);
        }

        Vec3d point_positive = point3d - direction3d;

        std::lock_guard<std::mutex> lock(ref->picking_mutex);
        if (ref->raycast_tree == nullptr) {
            env->ReleaseDoubleArrayElements(pointArr, point, JNI_ABORT);
            env->ReleaseDoubleArrayElements(directionArr, direction, JNI_ABORT);
            return env->NewDoubleArray(0);
        }
        igl::Hit hit{-1, -1, 0.f, 0.f, 0.f};
        hit.t = std::numeric_limits<float>::infinity();
        bool has_hit = false;
        try {
            has_hit = AABBTreeIndirect::intersect_ray_first_hit(
                ref->mesh.its.vertices,
                ref->mesh.its.indices,
                *ref->raycast_tree,
                point_positive,
                direction3d,
                hit);
        } catch (const std::exception& e) {
            __android_log_print(ANDROID_LOG_WARN, TAG, "Raycast query failed: %s", e.what());
            env->ReleaseDoubleArrayElements(pointArr, point, JNI_ABORT);
            env->ReleaseDoubleArrayElements(directionArr, direction, JNI_ABORT);
            return env->NewDoubleArray(0);
        }
        if (!has_hit || hit.id < 0 || static_cast<size_t>(hit.id) >= ref->mesh.its.indices.size()) {
            env->ReleaseDoubleArrayElements(pointArr, point, JNI_ABORT);
            env->ReleaseDoubleArrayElements(directionArr, direction, JNI_ABORT);
            return env->NewDoubleArray(0);
        }
        const Vec3d position = point_positive + direction3d * static_cast<double>(hit.t);
        Vec3d normal = its_unnormalized_normal(ref->mesh.its, hit.id).cast<double>();
        const double normal_length = normal.norm();
        if (!position.allFinite() || !std::isfinite(normal_length) || normal_length <= 1e-12) {
            env->ReleaseDoubleArrayElements(pointArr, point, JNI_ABORT);
            env->ReleaseDoubleArrayElements(directionArr, direction, JNI_ABORT);
            return env->NewDoubleArray(0);
        }
        normal /= normal_length;
        const jdouble hit_data[6] = {
            position.x(), position.y(), position.z(),
            normal.x(), normal.y(), normal.z(),
        };
        jdoubleArray arr = env->NewDoubleArray(6);
        if (arr != nullptr) env->SetDoubleArrayRegion(arr, 0, 6, hit_data);

        env->ReleaseDoubleArrayElements(pointArr, point, JNI_ABORT);
        env->ReleaseDoubleArrayElements(directionArr, direction, JNI_ABORT);

        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1from_1model(JNIEnv* env, jclass, jlong ptr, jlong model) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* mRef = (ModelRef*) (intptr_t) model;
        std::lock_guard<std::mutex> lock(ref->picking_mutex);
        ref->raycast_tree.reset();
        ref->mesh = mRef->model.mesh();
        ref->model.init_from(ref->mesh.its);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1from_1model_1object(JNIEnv* env, jclass, jlong ptr, jlong model, jint i) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* mRef = (ModelRef*) (intptr_t) model;
        std::lock_guard<std::mutex> lock(ref->picking_mutex);
        ref->raycast_tree.reset();
        ref->mesh = mRef->model.objects[i]->mesh();
        ref->model.init_from(ref->mesh.its);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1from_1model_1object_1volume(
        JNIEnv*, jclass, jlong ptr, jlong model, jint object_index, jint volume_index) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* model_ref = (ModelRef*) (intptr_t) model;
        if (ref == nullptr || model_ref == nullptr || object_index < 0 || volume_index < 0 ||
            static_cast<size_t>(object_index) >= model_ref->model.objects.size()) return;
        ModelObject* object = model_ref->model.objects[object_index];
        if (object == nullptr || static_cast<size_t>(volume_index) >= object->volumes.size() || object->volumes[volume_index] == nullptr) return;
        std::lock_guard<std::mutex> lock(ref->picking_mutex);
        ref->raycast_tree.reset();
        ref->mesh = object->volumes[volume_index]->mesh();
        ref->mesh.transform(object->volumes[volume_index]->get_matrix());
        ref->model.init_from(ref->mesh.its);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1from_1model_1object_1preview(JNIEnv* env, jclass, jlong ptr, jlong model, jint i, jint max_triangles) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* mRef = (ModelRef*) (intptr_t) model;
        std::lock_guard<std::mutex> lock(ref->picking_mutex);
        ref->raycast_tree.reset();
        ref->mesh = mRef->model.objects[i]->mesh();
        if (max_triangles >= 4 && ref->mesh.its.indices.size() > static_cast<size_t>(max_triangles)) {
            try {
                its_quadric_edge_collapse(ref->mesh.its, static_cast<uint32_t>(max_triangles));
            } catch (const std::exception& e) {
                __android_log_print(ANDROID_LOG_WARN, TAG, "Preview simplification failed: %s", e.what());
                ref->mesh = mRef->model.objects[i]->mesh();
            }
        }
        ref->model.init_from(ref->mesh.its);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1set_1color(JNIEnv* env, jclass, jlong ptr, jfloat red, jfloat green, jfloat blue, jfloat alpha) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.set_color(ColorRGBA(red, green, blue, alpha));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1stilized_1arrow(JNIEnv* env, jclass, jlong ptr, jfloat tip_radius, jfloat tip_length, jfloat stem_radius, jfloat stem_length) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.init_from(stilized_arrow(16, tip_radius, tip_length, stem_radius, stem_length));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1background_1triangles(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.reset();

        GLModel::Geometry init_data;
        init_data.format = { GLModel::Geometry::EPrimitiveType::Triangles, GLModel::Geometry::EVertexLayout::P2T2 };
        init_data.reserve_vertices(4);
        init_data.reserve_indices(6);

        // vertices
        init_data.add_vertex(Vec2f(-1.0f, -1.0f), Vec2f(0.0f, 0.0f));
        init_data.add_vertex(Vec2f(1.0f, -1.0f),  Vec2f(1.0f, 0.0f));
        init_data.add_vertex(Vec2f(1.0f, 1.0f),   Vec2f(1.0f, 1.0f));
        init_data.add_vertex(Vec2f(-1.0f, 1.0f),  Vec2f(0.0f, 1.0f));

        // indices
        init_data.add_triangle(0, 1, 2);
        init_data.add_triangle(2, 3, 0);

        ref->model.init_from(std::move(init_data));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1box(JNIEnv* env, jclass, jlong ptr, jfloat width, jfloat depth, jfloat height) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        std::lock_guard<std::mutex> lock(ref->picking_mutex);
        ref->model.reset();
        ref->mesh.clear();
        ref->raycast_tree.reset();

        const float w = std::max(1.0f, (float)width);
        const float d = std::max(1.0f, (float)depth);
        const float h = std::max(1.0f, (float)height);

        GLModel::Geometry init_data;
        init_data.format = { GLModel::Geometry::EPrimitiveType::Triangles, GLModel::Geometry::EVertexLayout::P3N3 };
        init_data.reserve_vertices(24);
        init_data.reserve_indices(36);

        auto add_face = [&](const Vec3f& normal, const Vec3f& a, const Vec3f& b, const Vec3f& c, const Vec3f& e) {
            unsigned int base = (unsigned int)init_data.vertices_count();
            init_data.add_vertex(a, normal);
            init_data.add_vertex(b, normal);
            init_data.add_vertex(c, normal);
            init_data.add_vertex(e, normal);
            init_data.add_triangle(base + 0, base + 1, base + 2);
            init_data.add_triangle(base + 2, base + 3, base + 0);
        };

        Vec3f p000(0, 0, 0), p100(w, 0, 0), p110(w, d, 0), p010(0, d, 0);
        Vec3f p001(0, 0, h), p101(w, 0, h), p111(w, d, h), p011(0, d, h);
        add_face(Vec3f(0, 0, -1), p010, p110, p100, p000); // bottom
        add_face(Vec3f(0, 0,  1), p001, p101, p111, p011); // top
        add_face(Vec3f(0, -1, 0), p000, p100, p101, p001); // front
        add_face(Vec3f(1,  0, 0), p100, p110, p111, p101); // right
        add_face(Vec3f(0,  1, 0), p110, p010, p011, p111); // back
        add_face(Vec3f(-1, 0, 0), p010, p000, p001, p011); // left

        ref->model.init_from(std::move(init_data));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1bounding_1box(JNIEnv* env, jclass, jlong ptr, jlong modelPtr, jint i) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* modelRef = (ModelRef*) (intptr_t) modelPtr;

        const BoundingBoxf3& box = modelRef->model.objects[i]->bounding_box_exact();
        const BoundingBoxf3& curr_box = ref->model.get_bounding_box();
        if (!ref->model.is_initialized() || !is_approx(box.min, curr_box.min) || !is_approx(box.max, curr_box.max)) {
            ref->model.reset();

            const Vec3f b_min = box.min.cast<float>();
            const Vec3f b_max = box.max.cast<float>();
            const Vec3f size = 0.2f * box.size().cast<float>();

            GLModel::Geometry init_data;
            init_data.format = { GLModel::Geometry::EPrimitiveType::Lines, GLModel::Geometry::EVertexLayout::P3 };
            init_data.reserve_vertices(48);
            init_data.reserve_indices(48);

            // vertices
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y() + size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y() + size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y() - size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y() - size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y() + size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z() - size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y() + size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z() - size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y() - size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z() - size.z()));

            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y() - size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z() - size.z()));

            // indices
            for (unsigned int i = 0; i < 48; ++i) {
                init_data.add_index(i);
            }

            ref->model.init_from(std::move(init_data));
        }
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1get_1bounding_1box(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        jdouble values[6] = {};
        if (ref != nullptr && ref->model.is_initialized()) {
            const BoundingBoxf3& box = ref->model.get_bounding_box();
            values[0] = box.min.x(); values[1] = box.min.y(); values[2] = box.min.z();
            values[3] = box.max.x(); values[4] = box.max.y(); values[5] = box.max.z();
        }
        jdoubleArray result = env->NewDoubleArray(6);
        if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 6, values);
        return result;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1render(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.render();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1reset(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        std::lock_guard<std::mutex> lock(ref->picking_mutex);
        ref->model.reset();
        ref->raycast_tree.reset();
        ref->mesh.clear();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1is_1initialized(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        return ref->model.is_initialized();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1is_1empty(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        return ref->model.is_empty();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1release(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.reset();
        delete ref;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1get_1id(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        return shader->program.get_id();
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1get_1uniform_1location(JNIEnv* env, jclass, jlong ptr, jstring name) {
        const char* chars = env->GetStringUTFChars(name, JNI_FALSE);
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        if (shader) {
            int location = shader->program.get_uniform_location(chars);
            env->ReleaseStringUTFChars(name, chars);
            return location;
        }
        return 0;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1get_1attrib_1location(JNIEnv* env, jclass, jlong ptr, jstring name) {
        const char *chars = env->GetStringUTFChars(name, JNI_FALSE);
        ShaderRef *shader = (ShaderRef *) (intptr_t) ptr;
        if (shader) {
            int location = shader->program.get_attrib_location(chars);
            env->ReleaseStringUTFChars(name, chars);
            return location;
        }
        return 0;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1start_1using(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        shader->program.start_using();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1stop_1using(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        shader->program.stop_using();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1release(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        delete shader;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1calc_1view_1normal_1matrix(JNIEnv* env, jclass, jdoubleArray view_matrix, jdoubleArray world_matrix, jdoubleArray normal_matrix) {
        jdouble* viewMatrix = env->GetDoubleArrayElements(view_matrix, JNI_FALSE);
        jdouble* worldMatrix = env->GetDoubleArrayElements(world_matrix, JNI_FALSE);

        Matrix4d mViewMatrix(viewMatrix);
        Matrix4d mWorldMatrix(worldMatrix);

        Matrix3d mNormalMatrix = mViewMatrix.block(0, 0, 3, 3) * mWorldMatrix.block(0, 0, 3, 3).inverse().transpose();
        env->SetDoubleArrayRegion(normal_matrix, 0, 12, mNormalMatrix.data());

        env->ReleaseDoubleArrayElements(view_matrix, viewMatrix, JNI_ABORT);
        env->ReleaseDoubleArrayElements(world_matrix, worldMatrix, JNI_ABORT);
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1create(JNIEnv* env, jclass, jstring config) {
        ConfigRef* ref = new ConfigRef();
        const char* config_ini = env->GetStringUTFChars(config, JNI_FALSE);
        // Tolerate version drift in bundled profiles: substitute unknown enum values rather than
        // throwing, so the config/UI layer keeps working instead of failing to construct.
        ref->config.load_from_ini_string(config_ini, ForwardCompatibilitySubstitutionRule::EnableSilent);

        const ConfigOption *opt = ref->config.option("nozzle_diameter");
        if (opt)
            ref->config.set_key_value("num_extruders", new ConfigOptionInt((int)static_cast<const ConfigOptionFloats*>(opt)->values.size()));

        env->ReleaseStringUTFChars(config, config_ini);
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1release(JNIEnv* env, jclass, jlong ptr) {
        ConfigRef* ref = (ConfigRef*) (intptr_t) ptr;
        delete ref;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1check_1compatibility(JNIEnv* env, jclass, jlong ptr, jstring cond) {
        ConfigRef* ref = (ConfigRef*) (intptr_t) ptr;
        const char* condition = env->GetStringUTFChars(cond, JNI_FALSE);

        jboolean value;
        try {
            value = PlaceholderParser::evaluate_boolean_expression(condition, ref->config);
        } catch (const std::runtime_error &err) {
            //FIXME in case of an error, return "compatible with everything".
            __android_log_print(ANDROID_LOG_WARN, TAG, "Parsing error of compatible_printers_condition:\n%s\n", err.what());
            value = true;
        }
        env->ReleaseStringUTFChars(cond, condition);
        return value;
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1eval(JNIEnv* env, jclass, jlong ptr, jstring cond) {
        ConfigRef *ref = (ConfigRef *) (intptr_t) ptr;
        const char *condition = env->GetStringUTFChars(cond, JNI_FALSE);

        try {
            PlaceholderParser parser(&ref->config);
            std::string val = parser.process(std::string(condition));
            env->ReleaseStringUTFChars(cond, condition);
            return env->NewStringUTF(val.c_str());
        } catch (const std::runtime_error &err) {
            env->ReleaseStringUTFChars(cond, condition);

            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), err.what());
            return nullptr;
        }
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1unproject(JNIEnv* env, jclass, jdoubleArray view_matrix, jdoubleArray projection_matrix, jint screen_width, jint screen_height, jdouble screen_x, jdouble screen_y) {
        jdouble* viewMatrix = env->GetDoubleArrayElements(view_matrix, JNI_FALSE);
        jdouble* projectionMatrix = env->GetDoubleArrayElements(projection_matrix, JNI_FALSE);

        Matrix4d modelview(viewMatrix);
        Matrix4d projection(projectionMatrix);
        Vec4i32 viewport(0, 0, screen_width, screen_height);
        Vec3d screenPoint(screen_x, screen_height - screen_y, 0);

        Vec3d point;
        igl::unproject(screenPoint, modelview, projection, viewport, point);

        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, point.data());

        env->ReleaseDoubleArrayElements(view_matrix, viewMatrix, JNI_ABORT);
        env->ReleaseDoubleArrayElements(projection_matrix, projectionMatrix, JNI_ABORT);

        return arr;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1create(JNIEnv* env, jclass, jlongArray data) {
        BedRef* ref = new BedRef();
        GLModelRef* refs = new GLModelRef[3];
        ref->meshes = refs;
        ref->triangles = &refs[0].model;
        ref->gridlines = &refs[1].model;
        ref->contourlines = &refs[2].model;

        for (int i = 0; i < 3; i++) {
            jlong ptrLong = (jlong) (intptr_t) &refs[i];
            env->SetLongArrayRegion(data, i, 1, &ptrLong);
        }
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1configure(JNIEnv* env, jclass, jlong ptr, jstring config_path) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;

        const char* chars = env->GetStringUTFChars(config_path, JNI_FALSE);
        std::string config_path_str(chars);
        env->ReleaseStringUTFChars(config_path, chars);

        // Convert any C++ exception (e.g. a missing/invalid config option) into a Java exception
        // instead of letting it escape the JNI boundary and abort the whole process.
        try {
            // The bed only needs printable_area / printable_height. Use silent substitution so a single
            // unrecognised enum value elsewhere in the profile (engine/profile version drift) falls back
            // to its default instead of throwing and leaving the bed blank.
            ref->config.load(config_path_str, ForwardCompatibilitySubstitutionRule::EnableSilent);

            // OrcaSlicer renamed bed_shape -> printable_area and max_print_height -> printable_height.
            const Pointfs bed_shape = ref->config.option_throw<ConfigOptionPoints>("printable_area")->values;
            float maxHeight = ref->config.option_throw<ConfigOptionFloat>("printable_height")->value;

            ref->contour = ExPolygon(Polygon::new_scale(bed_shape));
            const BoundingBox bbox = ref->contour.contour.bounding_box();
            if (!bbox.defined) {
                env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), "Invalid bed shape");
                return;
            }

            // BuildVolume now also takes per-extruder printable areas/heights; we have none, so pass empty.
            ref->build_volume = BuildVolume { bed_shape, maxHeight, {}, {} };
            ref->triangles->reset();
            ref->gridlines->reset();
            ref->contourlines->reset();

            bed_util_init_gridlines(ref->contour, ref->gridlines);
            bed_util_init_triangles(ref->contour, ref->triangles);
            bed_util_init_contourlines(ref->contour, ref->contourlines);
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), e.what());
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1init_1triangles_1mesh(JNIEnv* env, jclass, jlong ptr, jlong triangles_ptr) {
        auto ref = reinterpret_cast<BedRef*>(ptr);
        auto tRef = reinterpret_cast<GLModelRef*>(triangles_ptr);

        auto contour = ref->contour;
        BoundingBox bb = get_extents(contour);
        Point center = bb.center();
        float scaleFactor = 4;
        contour.scale(scaleFactor);
        contour.translate(-center.x() * scaleFactor * 0.5f, -center.y() * scaleFactor * 0.5f);
        bed_util_init_triangles_its(contour, &tRef->mesh.its);
    }

    static jboolean bed_arrange_with_distance(JNIEnv* env, jlong ptr, jlong model,
            jstring arrange_config, jdouble min_distance_mm, jint selected_object_index = -1) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        ModelRef* mRef = (ModelRef*) (intptr_t) model;
        bool sequential_print = false;

        try {
            // Keep the active build volume from BedRef, but use the current printer/process
            // settings to make sequential (By-Object) placement safe for the selected printer.
            DynamicPrintConfig config = ref->config;
            if (arrange_config != nullptr) {
                const char* config_chars = env->GetStringUTFChars(arrange_config, nullptr);
                if (config_chars == nullptr)
                    return JNI_FALSE;
                try {
                    config.load_from_ini_string(config_chars, ForwardCompatibilitySubstitutionRule::EnableSilent);
                } catch (...) {
                    env->ReleaseStringUTFChars(arrange_config, config_chars);
                    throw;
                }
                env->ReleaseStringUTFChars(arrange_config, config_chars);
            }

            // OrcaSlicer's arrange API takes the bed as Points (scaled bed shape) plus ArrangeParams,
            // rather than the arr2::ArrangeBed/ArrangeSettings types of newer PrusaSlicer.
            Points bed = get_bed_shape(ref->config);
            ArrangeParams arrange_cfg;
            arrange_cfg.min_obj_distance = scaled(std::max(0.0, min_distance_mm));
            // Let the packer rotate footprints in the XY plane. Without this, two
            // perpendicular long parts can be rejected even when one 90-degree turn
            // makes the pair fit on the same plate. This does not change their Z pose.
            arrange_cfg.allow_rotations = true;

            // arrange_objects() / arrange() do NOT translate min_obj_distance into the per-item inflation
            // that the packer uses for spacing — _arrange() zeroes min_obj_distance and expects every item
            // to be pre-inflated (the desktop does this via update_selected_items_inflation()). Without it,
            // parts pack edge-to-edge. Inflate each real object by half the min distance so neighbouring
            // parts end up a full min_obj_distance (6mm) apart, which keeps them separable/printable.
            sequential_print = config.has("print_sequence") &&
                config.opt_enum<PrintSequence>("print_sequence") == PrintSequence::ByObject;

            ModelInstancePtrs instances;
            ArrangePolygons input;
            ArrangePolygons fixed_objects;
            if (selected_object_index >= 0) {
                if (static_cast<size_t>(selected_object_index) >= mRef->model.objects.size()) return JNI_FALSE;
                for (size_t object_index = 0; object_index < mRef->model.objects.size(); ++object_index) {
                    ModelObject* object = mRef->model.objects[object_index];
                    for (ModelInstance* instance : object->instances) {
                        ArrangePolygon item = sequential_print
                            ? get_instance_arrange_poly(instance, config)
                            : get_arrange_poly(instance, config);
                        if (object_index == static_cast<size_t>(selected_object_index)) {
                            input.emplace_back(std::move(item));
                            instances.emplace_back(instance);
                        } else {
                            fixed_objects.emplace_back(std::move(item));
                        }
                    }
                }
                if (input.empty()) return JNI_FALSE;
                // Existing models stay exactly where the user put them. Only the new object
                // is fed to libnest2d as movable input; the old objects are fixed obstacles.
                arrange_cfg.do_final_align = false;
            } else if (sequential_print) {
                // By-Object clearance depends on each instance's real height and print settings.
                // The generic UI helper omits those values, unlike Orca's plater helper.
                for (ModelObject* object : mRef->model.objects) {
                    for (ModelInstance* instance : object->instances) {
                        input.emplace_back(get_instance_arrange_poly(instance, config));
                        instances.emplace_back(instance);
                    }
                }
            } else {
                input = get_arrange_polys(mRef->model, instances);
            }

            // Collision volumes are physical fixed obstacles. Resolve their Z range and
            // nozzle ownership before packing; unknown object-to-nozzle mappings remain
            // conservative in the packer's collision predicate.
            PrintConfig exclusion_config;
            exclusion_config.apply(config, true);
            const auto exclusions_by_extruder = get_bed_excluded_regions_by_extruder(exclusion_config);
            const BedExcludeVolumeMode exclusion_mode = active_bed_exclude_volume_mode(exclusion_config);
            for (size_t extruder = 0; extruder < exclusions_by_extruder.size(); ++extruder) {
                for (const BedExcludeRegion &region : exclusions_by_extruder[extruder]) {
                    if (!region.is_collision_volume()) continue;
                    if (exclusion_mode == BedExcludeVolumeMode::Shared && extruder > 0) continue;
                    if (exclusion_mode == BedExcludeVolumeMode::PerExtruder && region.extruder_id < 0 && extruder > 0) continue;
                    ArrangePolygon obstacle;
                    obstacle.poly = ExPolygon{region.polygon};
                    obstacle.bed_idx = 0;
                    obstacle.is_bed_exclusion = true;
                    obstacle.has_z_range = region.has_z_range;
                    obstacle.z_min = region.z_min;
                    obstacle.z_max = region.z_max;
                    obstacle.bed_exclusion_extruder_id = region.extruder_id;
                    arrange_cfg.excluded_regions.emplace_back(std::move(obstacle));
                }
            }
            if (!arrange_cfg.excluded_regions.empty())
                arrange_cfg.do_final_align = false; // keep fixed physical obstacles in bed coordinates

            const ConfigOptionInts *physical_map = config.option<ConfigOptionInts>("physical_extruder_map");
            auto configure_item = [&](ArrangePolygon &item, const ModelInstance *instance) {
                if (instance != nullptr) {
                    const BoundingBoxf3 bounds = instance->get_object()->instance_convex_hull_bounding_box(instance);
                    item.height = std::max(0.0, bounds.size().z());
                }
                item.z_min = 0.;
                item.z_max = item.height;
                item.has_z_range = true;
                item.bed_exclusion_extruder_ids.clear();
                for (int logical_extruder : item.extrude_ids) {
                    const int logical_index = logical_extruder - 1;
                    if (logical_index < 0) continue;
                    const int physical_extruder = physical_map != nullptr &&
                        static_cast<size_t>(logical_index) < physical_map->size()
                            ? physical_map->get_at(static_cast<size_t>(logical_index)) : logical_index;
                    if (physical_extruder >= 0)
                        item.bed_exclusion_extruder_ids.push_back(physical_extruder);
                }
                std::sort(item.bed_exclusion_extruder_ids.begin(), item.bed_exclusion_extruder_ids.end());
                item.bed_exclusion_extruder_ids.erase(
                    std::unique(item.bed_exclusion_extruder_ids.begin(), item.bed_exclusion_extruder_ids.end()),
                    item.bed_exclusion_extruder_ids.end());
            };
            for (size_t i = 0; i < input.size(); ++i) {
                configure_item(input[i], i < instances.size() ? instances[i] : nullptr);
            }
            if (selected_object_index >= 0) {
                size_t fixed_index = 0;
                for (size_t object_index = 0; object_index < mRef->model.objects.size(); ++object_index) {
                    if (object_index == static_cast<size_t>(selected_object_index)) continue;
                    ModelObject* object = mRef->model.objects[object_index];
                    for (ModelInstance* instance : object->instances) {
                        if (fixed_index < fixed_objects.size())
                            configure_item(fixed_objects[fixed_index++], instance);
                    }
                }
            }

            if (sequential_print) {
                arrange_cfg.is_seq_print = true;
                arrange_cfg.bed_shrink_x = BED_SHRINK_SEQ_PRINT;
                arrange_cfg.bed_shrink_y = BED_SHRINK_SEQ_PRINT;
                arrange_cfg.clearance_radius = config.has("extruder_clearance_radius")
                    ? static_cast<float>(config.opt_float("extruder_clearance_radius")) : 0.f;
                arrange_cfg.clearance_height_to_rod = config.has("extruder_clearance_height_to_rod")
                    ? static_cast<float>(config.opt_float("extruder_clearance_height_to_rod")) : 0.f;
                arrange_cfg.clearance_height_to_lid = config.has("extruder_clearance_height_to_lid")
                    ? static_cast<float>(config.opt_float("extruder_clearance_height_to_lid")) : 0.f;
                arrange_cfg.nozzle_height = config.has("nozzle_height")
                    ? static_cast<float>(config.opt_float("nozzle_height")) : 0.f;
                arrange_cfg.printable_height = config.has("printable_height")
                    ? static_cast<float>(config.opt_float("printable_height")) : 256.f;

                // A per-object skirt must also remain clear of neighboring objects. This is
                // deliberately conservative: use the configured skirt distance plus one nozzle
                // width per loop rather than risk packing an object into its skirt envelope.
                if (config.has("skirt_type") && config.opt_enum<SkirtType>("skirt_type") == stPerObject &&
                    config.has("skirt_loops") && config.opt_int("skirt_loops") > 0) {
                    const float skirt_distance = config.has("skirt_distance")
                        ? static_cast<float>(config.opt_float("skirt_distance")) : 0.f;
                    float nozzle_width = 0.4f;
                    if (config.has("nozzle_diameter")) {
                        const auto& nozzle_diameters = config.option<ConfigOptionFloats>("nozzle_diameter")->values;
                        if (!nozzle_diameters.empty())
                            nozzle_width = *std::max_element(nozzle_diameters.begin(), nozzle_diameters.end());
                    }
                    arrange_cfg.object_skirt_offset = skirt_distance +
                        static_cast<float>(config.opt_int("skirt_loops")) * nozzle_width;
                }

                arrangement::update_arrange_params(arrange_cfg, &config, input);
                arrangement::update_selected_items_inflation(input, &config, arrange_cfg);
                if (selected_object_index >= 0)
                    arrangement::update_unselected_items_inflation(fixed_objects, &config, arrange_cfg);
                bed = arrangement::get_shrink_bedpts(&config, arrange_cfg);
            } else {
                const coord_t inflation = arrange_cfg.min_obj_distance / 2;
                for (ArrangePolygon& ap : input)
                    if (!ap.is_virt_object)
                        ap.inflation = inflation;
                for (ArrangePolygon& ap : fixed_objects)
                    if (!ap.is_virt_object)
                        ap.inflation = inflation;
            }
            // The exclusions argument preloads fixed models and physical geometry into libnest2d.
            // ArrangeParams::excluded_regions alone only informs its alignment scoring.
            ArrangePolygons fixed_geometry = std::move(fixed_objects);
            fixed_geometry.insert(fixed_geometry.end(), arrange_cfg.excluded_regions.begin(),
                arrange_cfg.excluded_regions.end());
            arrangement::arrange(input, fixed_geometry, bed, arrange_cfg);
            if (!sequential_print && selected_object_index < 0) {
                fill_compound_arrange_gaps(input, bed);
                if (!validate_arrange_placements(input, bed)) {
                    __android_log_print(ANDROID_LOG_WARN, "BeamArrange",
                        "Arrangement rejected by final footprint validation: objects=%zu bed=%zu",
                        input.size(), bed.size());
                }
            }
            return apply_arrange_polys(input, instances, throw_if_out_of_bed);
        } catch (const std::exception& e) {
            // If the model cannot fit the bed, it throws "Objects could not fit on the bed".
            // Catch it here to avoid JNI crash.
            __android_log_print(ANDROID_LOG_WARN, "BeamArrange",
                "Arrangement failed: %s (models=%zu selected=%d sequential=%d)", e.what(),
                mRef->model.objects.size(), selected_object_index, sequential_print);
            return false;
        } catch (...) {
            __android_log_print(ANDROID_LOG_ERROR, "BeamArrange",
                "Arrangement failed with unknown native exception (models=%zu selected=%d)",
                mRef->model.objects.size(), selected_object_index);
            return false;
        }
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1arrange(
            JNIEnv* env, jclass, jlong ptr, jlong model, jstring arrange_config) {
        return bed_arrange_with_distance(env, ptr, model, arrange_config, 6.0);
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1arrange_1with_1distance(
            JNIEnv* env, jclass, jlong ptr, jlong model, jstring arrange_config, jdouble min_distance_mm) {
        return bed_arrange_with_distance(env, ptr, model, arrange_config, min_distance_mm);
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1arrange_1object_1with_1distance(
            JNIEnv* env, jclass, jlong ptr, jlong model, jint object_index,
            jstring arrange_config, jdouble min_distance_mm) {
        return bed_arrange_with_distance(env, ptr, model, arrange_config, min_distance_mm, object_index);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1get_1bounding_1volume(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->build_volume.bounding_volume().min.x();
        elements[1] = ref->build_volume.bounding_volume().min.y();
        elements[2] = ref->build_volume.bounding_volume().min.z();
        elements[3] = ref->build_volume.bounding_volume().max.x();
        elements[4] = ref->build_volume.bounding_volume().max.y();
        elements[5] = ref->build_volume.bounding_volume().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1get_1bounding_1volume_1max_1size(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        return ref->build_volume.bounding_volume().max_size();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1is_1rectangular_1build_1volume(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        return ref != nullptr && ref->build_volume.type() == BuildVolume_Type::Rectangle;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1is_1circular_1build_1volume(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        return ref != nullptr && ref->build_volume.type() == BuildVolume_Type::Circle;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1get_1circular_1build_1volume(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        if (ref == nullptr || ref->build_volume.type() != BuildVolume_Type::Circle)
            return nullptr;
        const Geometry::Circled& circle = ref->build_volume.circle();
        const jdouble values[] = {
            unscaled<double>(circle.center.x()),
            unscaled<double>(circle.center.y()),
            unscaled<double>(circle.radius),
        };
        jdoubleArray result = env->NewDoubleArray(3);
        if (result != nullptr)
            env->SetDoubleArrayRegion(result, 0, 3, values);
        return result;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1release(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        delete[] ref->meshes;
        delete ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1create(JNIEnv* env, jclass) {
        GCodeViewerRef* ref = new GCodeViewerRef();
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1colors(JNIEnv* env, jclass, jlong ptr, jintArray colorsArr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;

        jint* colors = env->GetIntArrayElements(colorsArr, JNI_FALSE);
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::Skirt,                    { (unsigned char) colors[0],  (unsigned char) colors[1],  (unsigned char) colors[2] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::ExternalPerimeter,        { (unsigned char) colors[3],  (unsigned char) colors[4],  (unsigned char) colors[5] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::SupportMaterial,          { (unsigned char) colors[6],  (unsigned char) colors[7],  (unsigned char) colors[8] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::SupportMaterialInterface, { (unsigned char) colors[9],  (unsigned char) colors[10], (unsigned char) colors[11] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::InternalInfill,           { (unsigned char) colors[12], (unsigned char) colors[13], (unsigned char) colors[14] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::SolidInfill,              { (unsigned char) colors[15], (unsigned char) colors[16], (unsigned char) colors[17] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::WipeTower,                { (unsigned char) colors[18], (unsigned char) colors[19], (unsigned char) colors[20] });
        env->ReleaseIntArrayElements(colorsArr, colors, JNI_ABORT);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1render(JNIEnv* env, jclass, jlong ptr, jfloatArray viewMatrixArr, jfloatArray projectionMatrixArr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        jfloat* viewMatrix = env->GetFloatArrayElements(viewMatrixArr, JNI_FALSE);
        jfloat* projectionMatrix = env->GetFloatArrayElements(projectionMatrixArr, JNI_FALSE);

        libvgcode::Mat4x4 converted_view_matrix;
        std::memcpy(converted_view_matrix.data(), viewMatrix, 16 * sizeof(float));
        libvgcode::Mat4x4 converted_projection_matrix;
        std::memcpy(converted_projection_matrix.data(), projectionMatrix, 16 * sizeof(float));

        ref->viewer.render(converted_view_matrix, converted_projection_matrix);

        env->ReleaseFloatArrayElements(viewMatrixArr, viewMatrix, JNI_ABORT);
        env->ReleaseFloatArrayElements(projectionMatrixArr, projectionMatrix, JNI_ABORT);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1init(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        if (ref->initialized) return;
        ref->viewer.init(reinterpret_cast<const char*>(glGetString(GL_VERSION)));
        ref->initialized = true;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1is_1initialized(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->initialized;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1load(JNIEnv* env, jclass, jlong ptr, jlong resultPtr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        GCodeResultRef* resultRef = (GCodeResultRef*) (intptr_t) resultPtr;

        ref->data = libvgcode_convert_input_data(resultRef->result, resultRef->result.extruder_colors, resultRef->result.extruder_colors, ref->viewer);
        ref->viewer.load(std::move(ref->data));
        ref->viewer.set_time_mode(libvgcode::ETimeMode::Normal);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1reset(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.reset();
        ref->initialized = false;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1layers_1count(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.get_layers_count();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1layers_1view_1range(JNIEnv* env, jclass, jlong ptr, jlong min, jlong max) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_layers_view_range(static_cast<uint32_t>(min), static_cast<uint32_t>(max));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1infill_1visibility_1depth(JNIEnv* env, jclass, jlong ptr, jint depth) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_infill_visibility_depth(depth);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1fast_1mode(JNIEnv* env, jclass, jlong ptr, jboolean fastMode) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_fast_mode(fastMode);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1selected_1object_1id(JNIEnv* env, jclass, jlong ptr, jint id) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_selected_object_id(id);
    }

    JNIEXPORT jlongArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1layers_1view_1range(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        jlongArray arr = env->NewLongArray(2);
        auto range = ref->viewer.get_layers_view_range();
        jlong min = range[0], max = range[1];
        env->SetLongArrayRegion(arr, 0, 1, &min);
        env->SetLongArrayRegion(arr, 0, 2, &max);
        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1release(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.shutdown();
        delete ref;
    }

    JNIEXPORT jfloat JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1estimated_1time(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.get_estimated_time();
    }

    libvgcode::EGCodeExtrusionRole mapRole(int index) {
        libvgcode::EGCodeExtrusionRole crole;
        switch (index) {
            default:
            case 0:
                crole = libvgcode::EGCodeExtrusionRole::None;
                break;
            case 1:
                crole = libvgcode::EGCodeExtrusionRole::Perimeter;
                break;
            case 2:
                crole = libvgcode::EGCodeExtrusionRole::ExternalPerimeter;
                break;
            case 3:
                crole = libvgcode::EGCodeExtrusionRole::OverhangPerimeter;
                break;
            case 4:
                crole = libvgcode::EGCodeExtrusionRole::InternalInfill;
                break;
            case 5:
                crole = libvgcode::EGCodeExtrusionRole::SolidInfill;
                break;
            case 6:
                crole = libvgcode::EGCodeExtrusionRole::TopSolidInfill;
                break;
            case 7:
                crole = libvgcode::EGCodeExtrusionRole::Ironing;
                break;
            case 8:
                crole = libvgcode::EGCodeExtrusionRole::BridgeInfill;
                break;
            case 9:
                crole = libvgcode::EGCodeExtrusionRole::GapFill;
                break;
            case 10:
                crole = libvgcode::EGCodeExtrusionRole::Skirt;
                break;
            case 11:
                crole = libvgcode::EGCodeExtrusionRole::SupportMaterial;
                break;
            case 12:
                crole = libvgcode::EGCodeExtrusionRole::SupportMaterialInterface;
                break;
            case 13:
                crole = libvgcode::EGCodeExtrusionRole::WipeTower;
                break;
            case 14:
                crole = libvgcode::EGCodeExtrusionRole::Custom;
                break;
        }
        return crole;
    }

    JNIEXPORT jfloat JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1estimated_1time_1role(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.get_extrusion_role_estimated_time(mapRole(role));
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1is_1extrusion_1role_1visible(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.is_extrusion_role_visible(mapRole(role));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1toggle_1extrusion_1role_1visibility(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.toggle_extrusion_role_visibility(mapRole(role));
    }

    // Switch the gcode preview color mode (e.g. FeatureType=0, Tool/Filament=11). See libvgcode EViewType.
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1view_1type(JNIEnv* env, jclass, jlong ptr, jint type) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_view_type(static_cast<libvgcode::EViewType>(type));
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1view_1type(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return (jint) ref->viewer.get_view_type();
    }

    // Set the per-filament/tool colors used by the Tool view. colors are packed 0xRRGGBB ints.
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1tool_1colors(JNIEnv* env, jclass, jlong ptr, jintArray colorsArr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        jsize n = env->GetArrayLength(colorsArr);
        jint* c = env->GetIntArrayElements(colorsArr, JNI_FALSE);
        libvgcode::Palette palette;
        palette.reserve(n);
        for (jsize i = 0; i < n; ++i) {
            int v = c[i];
            palette.push_back({ (unsigned char) ((v >> 16) & 0xFF), (unsigned char) ((v >> 8) & 0xFF), (unsigned char) (v & 0xFF) });
        }
        env->ReleaseIntArrayElements(colorsArr, c, JNI_ABORT);
        if (!palette.empty())
            ref->viewer.set_tool_colors(palette);
    }

    // Preview-only packed 0xRRGGBBAA colors. The native print config remains untouched.
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1tool_1colors_1rgba(JNIEnv* env, jclass, jlong ptr, jintArray colorsArr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        const jsize n = env->GetArrayLength(colorsArr);
        jint* c = env->GetIntArrayElements(colorsArr, JNI_FALSE);
        libvgcode::PaletteWithAlpha palette;
        palette.reserve(n);
        for (jsize i = 0; i < n; ++i) {
            const uint32_t v = static_cast<uint32_t>(c[i]);
            palette.push_back({
                static_cast<unsigned char>((v >> 24) & 0xFF),
                static_cast<unsigned char>((v >> 16) & 0xFF),
                static_cast<unsigned char>((v >> 8) & 0xFF),
                static_cast<unsigned char>(v & 0xFF),
            });
        }
        env->ReleaseIntArrayElements(colorsArr, c, JNI_ABORT);
        if (!palette.empty())
            ref->viewer.set_tool_colors_with_alpha(palette);
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1is_1option_1visible(JNIEnv* env, jclass, jlong ptr, jint optionType) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return (jboolean) ref->viewer.is_option_visible(static_cast<libvgcode::EOptionType>(optionType));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1toggle_1option_1visibility(JNIEnv* env, jclass, jlong ptr, jint optionType) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.toggle_option_visibility(static_cast<libvgcode::EOptionType>(optionType));
    }

    // ---- Multi-color painting (mmu segmentation) ----

    static Slic3r::EnforcerBlockerType paint_state(jint filamentIdx) {
        if (filamentIdx <= 0) return Slic3r::EnforcerBlockerType::NONE;
        int v = filamentIdx;
        if (v > (int) Slic3r::EnforcerBlockerType::ExtruderMax) v = (int) Slic3r::EnforcerBlockerType::ExtruderMax;
        return static_cast<Slic3r::EnforcerBlockerType>(v);
    }

    // The facet channel a paint session reads/writes, by mode: 0 color, 1 support, 2 seam, 3 fuzzy.
    static Slic3r::FacetsAnnotation& facets_for_mode(Slic3r::ModelVolume* v, int mode) {
        switch (mode) {
            case 1:  return v->supported_facets;
            case 2:  return v->seam_facets;
            case 3:  return v->fuzzy_skin_facets;
            default: return v->mmu_segmentation_facets;
        }
    }

    using PaintSnapshot = PaintSessionRef::Snapshot;
    static constexpr size_t PAINT_HISTORY_MAX_BYTES = 8 * 1024 * 1024;
    static constexpr size_t PAINT_HISTORY_MAX_STROKES = 16;

    static size_t paint_snapshot_bytes(const PaintSnapshot& snapshot) {
        size_t bytes = 0;
        for (const auto& volume : snapshot)
            bytes += volume.triangles_to_split.size() * sizeof(Slic3r::TriangleSelector::TriangleBitStreamMapping)
                + (volume.bitstream.size() + 7) / 8 + (volume.used_states.size() + 7) / 8;
        return bytes;
    }
    static bool paint_snapshot_fits(const PaintSnapshot& snapshot) {
        return paint_snapshot_bytes(snapshot) <= PAINT_HISTORY_MAX_BYTES;
    }
    static void push_paint_snapshot(std::vector<PaintSnapshot>& history, PaintSnapshot snapshot) {
        const size_t bytes = paint_snapshot_bytes(snapshot);
        if (bytes > PAINT_HISTORY_MAX_BYTES) { history.clear(); return; }
        size_t total = bytes;
        for (const auto& entry : history) total += paint_snapshot_bytes(entry);
        while (!history.empty() && (history.size() >= PAINT_HISTORY_MAX_STROKES || total > PAINT_HISTORY_MAX_BYTES)) {
            total -= paint_snapshot_bytes(history.front());
            history.erase(history.begin());
        }
        history.push_back(std::move(snapshot));
    }
    static PaintVolumeRef* paint_volume(PaintSessionRef* session, int volume_idx) {
        if (session == nullptr) return nullptr;
        for (const auto& volume : session->volumes)
            if (volume && volume->volume_idx == volume_idx) return volume.get();
        return nullptr;
    }
    static PaintSnapshot capture_paint_snapshot(PaintSessionRef* session) {
        PaintSnapshot snapshot;
        snapshot.reserve(session->volumes.size());
        for (const auto& volume : session->volumes) snapshot.emplace_back(volume->selector->serialize());
        return snapshot;
    }
    static bool restore_paint_snapshot(PaintSessionRef* session, const PaintSnapshot& snapshot) {
        if (session == nullptr || snapshot.size() != session->volumes.size()) return false;
        for (size_t i = 0; i < snapshot.size(); ++i) session->volumes[i]->selector->deserialize(snapshot[i], true);
        return true;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1begin_1stroke(JNIEnv*, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        if (!s || s->volumes.empty() || s->stroke_before.has_value()) return;
        PaintSnapshot before = capture_paint_snapshot(s);
        if (paint_snapshot_fits(before)) s->stroke_before = std::move(before);
        else { s->undo_strokes.clear(); s->redo_strokes.clear(); }
    }
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1end_1stroke(JNIEnv*, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        if (!s || s->volumes.empty() || !s->stroke_before.has_value()) return;
        PaintSnapshot before = std::move(*s->stroke_before); s->stroke_before.reset();
        if (before != capture_paint_snapshot(s)) { push_paint_snapshot(s->undo_strokes, std::move(before)); s->redo_strokes.clear(); }
    }
    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1can_1undo(JNIEnv*, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        return s && !s->stroke_before.has_value() && !s->undo_strokes.empty();
    }
    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1can_1redo(JNIEnv*, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        return s && !s->stroke_before.has_value() && !s->redo_strokes.empty();
    }
    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1undo_1stroke(JNIEnv*, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        if (!s || s->volumes.empty() || s->stroke_before.has_value() || s->undo_strokes.empty()) return false;
        PaintSnapshot current = capture_paint_snapshot(s);
        if (!paint_snapshot_fits(current)) return false;
        push_paint_snapshot(s->redo_strokes, std::move(current));
        PaintSnapshot previous = std::move(s->undo_strokes.back()); s->undo_strokes.pop_back();
        return restore_paint_snapshot(s, previous);
    }
    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1redo_1stroke(JNIEnv*, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        if (!s || s->volumes.empty() || s->stroke_before.has_value() || s->redo_strokes.empty()) return false;
        PaintSnapshot current = capture_paint_snapshot(s);
        if (!paint_snapshot_fits(current)) return false;
        push_paint_snapshot(s->undo_strokes, std::move(current));
        PaintSnapshot next = std::move(s->redo_strokes.back()); s->redo_strokes.pop_back();
        return restore_paint_snapshot(s, next);
    }
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1reset_1history(JNIEnv*, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        if (!s) return;
        s->undo_strokes.clear();
        s->redo_strokes.clear();
        s->stroke_before.reset();
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1begin(JNIEnv*, jclass, jlong modelPtr, jint objIdx, jint mode) {
        ModelRef* mRef = (ModelRef*)(intptr_t)modelPtr;
        if (!mRef || objIdx < 0 || objIdx >= (int)mRef->model.objects.size()) return 0;
        ModelObject* obj = mRef->model.objects[objIdx];
        if (!obj || obj->volumes.empty()) return 0;
        // Selector topology and per-volume ray trees allocate substantially more memory than the
        // imported mesh; keep native initialization guarded and leave the model untouched on error.
        try {
            auto session = std::make_unique<PaintSessionRef>();
            session->model = mRef; session->objIdx = objIdx; session->mode = mode;
            const Transform3d instance = !obj->instances.empty() && obj->instances.front()
                ? obj->instances.front()->get_matrix() : Transform3d::Identity();
            for (size_t i = 0; i < obj->volumes.size(); ++i) {
                ModelVolume* source = obj->volumes[i];
                if (!source || !source->is_model_part()) continue;
                // AABBMesh and TriangleSelector retain references into the mesh. Keep the
                // owning PaintVolume at a stable heap address; vector growth must not move it.
                auto volume = std::make_unique<PaintVolumeRef>();
                volume->volume_idx = (int)i;
                volume->mesh = source->mesh();
                if (volume->mesh.its.indices.empty()) continue;
                volume->mesh_to_world = instance * source->get_matrix();
                volume->selector = std::make_unique<TriangleSelector>(volume->mesh);
                const auto& annotations = facets_for_mode(source, mode).get_data();
                if (!annotations.triangles_to_split.empty()) volume->selector->deserialize(annotations, true);
                volume->emesh = std::make_unique<AABBMesh>(volume->mesh, true);
                session->volumes.emplace_back(std::move(volume));
            }
            if (session->volumes.empty()) return 0;
            return (jlong)(intptr_t)session.release();
        } catch (const std::exception& e) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Paint session initialization failed: %s", e.what());
            return 0;
        } catch (...) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Paint session initialization failed with an unknown native error");
            return 0;
        }
    }

    // Input is a world-space camera ray. Output is {ModelObject volume index, face index, world x,y,z}.
    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1raycast(JNIEnv* env, jclass, jlong sessionPtr, jdoubleArray originArr, jdoubleArray dirArr) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        if (!s || s->volumes.empty() || !originArr || !dirArr || env->GetArrayLength(originArr) < 3 || env->GetArrayLength(dirArr) < 3)
            return env->NewDoubleArray(0);
        jdouble* o = env->GetDoubleArrayElements(originArr, JNI_FALSE);
        jdouble* d = env->GetDoubleArrayElements(dirArr, JNI_FALSE);
        Vec3d origin(o[0], o[1], o[2]), direction(d[0], d[1], d[2]);
        env->ReleaseDoubleArrayElements(originArr, o, JNI_ABORT);
        env->ReleaseDoubleArrayElements(dirArr, d, JNI_ABORT);
        if (direction.squaredNorm() < 1e-16) return env->NewDoubleArray(0);
        direction.normalize();
        double nearest = std::numeric_limits<double>::max();
        int volume_index = -1, face_index = -1;
        Vec3d world_hit = Vec3d::Zero();
        for (const auto& volume_ref : s->volumes) {
            PaintVolumeRef& volume = *volume_ref;
            const Transform3d inverse = volume.mesh_to_world.inverse();
            const Vec3d local_origin = inverse * origin;
            Vec3d local_direction = inverse.linear() * direction;
            if (local_direction.squaredNorm() < 1e-16) continue;
            local_direction.normalize();
            const auto hits = volume.emesh->query_ray_hits(local_origin, local_direction);
            for (const auto& hit : hits) {
                const Vec3d candidate = volume.mesh_to_world * hit.position();
                const double distance = (candidate - origin).dot(direction);
                if (distance >= 0. && distance < nearest) {
                    nearest = distance; volume_index = volume.volume_idx;
                    face_index = (int)hit.face(); world_hit = candidate;
                }
            }
        }
        if (volume_index < 0) return env->NewDoubleArray(0);
        jdoubleArray result = env->NewDoubleArray(5);
        const jdouble values[5] = {(double)volume_index, (double)face_index,
                                   world_hit.x(), world_hit.y(), world_hit.z()};
        env->SetDoubleArrayRegion(result, 0, 5, values);
        return result;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1brush(JNIEnv* env, jclass, jlong sessionPtr, jdoubleArray hitArr, jint facetStart, jdouble radius, jint filamentIdx, jdoubleArray cameraArr, jboolean sphere, jdouble highlightAngle) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        if (!s || !hitArr || env->GetArrayLength(hitArr) < 5 || !cameraArr || env->GetArrayLength(cameraArr) < 3) return;
        jdouble* h = env->GetDoubleArrayElements(hitArr, JNI_FALSE);
        const int volume_index = (int)h[0];
        if (facetStart < 0) facetStart = (int)h[1];
        const Vec3d world_hit(h[2], h[3], h[4]);
        env->ReleaseDoubleArrayElements(hitArr, h, JNI_ABORT);
        PaintVolumeRef* volume = paint_volume(s, volume_index);
        if (!volume || !volume->selector) return;
        jdouble* cam = env->GetDoubleArrayElements(cameraArr, JNI_FALSE);
        const Vec3d camera_world(cam[0], cam[1], cam[2]);
        env->ReleaseDoubleArrayElements(cameraArr, cam, JNI_ABORT);
        const Transform3d& mesh_to_world = volume->mesh_to_world;
        const Transform3d inverse = mesh_to_world.inverse();
        const Vec3f hit = (inverse * world_hit).cast<float>();
        const Vec3d camera_local = inverse * camera_world;
        Transform3d no_translation = mesh_to_world;
        no_translation.translation() = Vec3d::Zero();
        if (facetStart < 0 || facetStart >= (int)volume->mesh.its.indices.size()) {
            const auto& its = volume->mesh.its;
            float best = FLT_MAX; int best_index = -1;
            for (int i = 0; i < (int)its.indices.size(); ++i) {
                const Vec3i32& tri = its.indices[i];
                const Vec3f center = (its.vertices[tri[0]] + its.vertices[tri[1]] + its.vertices[tri[2]]) / 3.f;
                const float distance = (center - hit).squaredNorm();
                if (distance < best) { best = distance; best_index = i; }
            }
            facetStart = best_index;
        }
        if (facetStart < 0) return;
        auto cursor = TriangleSelector::SinglePointCursor::cursor_factory(
            hit, camera_local.cast<float>(), (float)radius,
            sphere ? TriangleSelector::CursorType::SPHERE : TriangleSelector::CursorType::CIRCLE,
            mesh_to_world, TriangleSelector::ClippingPlane());
        volume->selector->select_patch(facetStart, std::move(cursor), paint_state(filamentIdx), no_translation, true, (float)highlightAngle);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1bucket(JNIEnv* env, jclass, jlong sessionPtr, jdoubleArray hitArr, jint facetStart, jint filamentIdx, jboolean propagate, jdouble angle, jdouble highlightAngle) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        if (!s || !hitArr || env->GetArrayLength(hitArr) < 5) return;
        jdouble* h = env->GetDoubleArrayElements(hitArr, JNI_FALSE);
        const int volume_index = (int)h[0];
        if (facetStart < 0) facetStart = (int)h[1];
        const Vec3d world_hit(h[2], h[3], h[4]);
        env->ReleaseDoubleArrayElements(hitArr, h, JNI_ABORT);
        PaintVolumeRef* volume = paint_volume(s, volume_index);
        if (!volume || !volume->selector) return;
        const Vec3f hit = (volume->mesh_to_world.inverse() * world_hit).cast<float>();
        if (facetStart < 0 || facetStart >= (int)volume->mesh.its.indices.size()) return;
        TriangleSelector::ClippingPlane clipping;
        Transform3d no_translation = volume->mesh_to_world;
        no_translation.translation() = Vec3d::Zero();
        volume->selector->bucket_fill_select_triangles(hit, facetStart, clipping, (float)angle, propagate, true,
                                                       (float)highlightAngle, no_translation);
        volume->selector->seed_fill_apply_on_triangles(paint_state(filamentIdx));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1height(JNIEnv*, jclass, jlong sessionPtr, jdouble zMin, jdouble zMax, jint filamentIdx, jdouble highlightAngle) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        if (!s) return;
        const auto state = paint_state(filamentIdx);
        for (const auto& volume_ref : s->volumes) {
            PaintVolumeRef& volume = *volume_ref;
            const auto& its = volume.mesh.its;
            Transform3d no_translation = volume.mesh_to_world;
            no_translation.translation() = Vec3d::Zero();
            for (int i = 0; i < (int)its.indices.size(); ++i) {
                const Vec3i32& tri = its.indices[i];
                const Vec3d a = volume.mesh_to_world * its.vertices[tri[0]].cast<double>();
                const Vec3d b = volume.mesh_to_world * its.vertices[tri[1]].cast<double>();
                const Vec3d c = volume.mesh_to_world * its.vertices[tri[2]].cast<double>();
                const double center_z = (a.z() + b.z() + c.z()) / 3.;
                if ((center_z >= zMin && center_z <= zMax) ||
                    (a.z() >= zMin && a.z() <= zMax) || (b.z() >= zMin && b.z() <= zMax) ||
                    (c.z() >= zMin && c.z() <= zMax)) {
                    if (volume.selector->is_overhang_facet(i, (float)highlightAngle, no_translation))
                        volume.selector->set_facet(i, state);
                }
            }
        }
    }
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1overhang_1boundary_1probe(JNIEnv* env, jclass) {
        const std::vector<Vec3f> vertices{Vec3f(0.f, 0.f, 0.f), Vec3f(1.f, 0.f, 0.f), Vec3f(0.f, -1.f, 1.f)};
        const std::vector<Vec3i32> faces{Vec3i32(0, 1, 2)};
        TriangleMesh mesh(vertices, faces);
        TriangleSelector selector(mesh);
        const Transform3d identity = Transform3d::Identity();
        const jint results[] = {
            selector.is_overhang_facet(0, 45.f, identity) ? 1 : 0,
            selector.is_overhang_facet(0, 43.5f, identity) ? 1 : 0,
        };
        jintArray output = env->NewIntArray(2);
        env->SetIntArrayRegion(output, 0, 2, results);
        return output;
    }
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1clear(JNIEnv*, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        if (s) for (const auto& volume : s->volumes) volume->selector->reset();
    }
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1commit(JNIEnv*, jclass, jlong sessionPtr) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        if (!s || !s->model || s->objIdx < 0 || s->objIdx >= (int)s->model->model.objects.size()) return;
        ModelObject* obj = s->model->model.objects[s->objIdx];
        for (const auto& state_ref : s->volumes) {
            PaintVolumeRef& state = *state_ref;
            if (state.volume_idx < 0 || state.volume_idx >= (int)obj->volumes.size()) continue;
            ModelVolume* volume = obj->volumes[state.volume_idx];
            if (!volume) continue;
            facets_for_mode(volume, s->mode).set(*state.selector);
            if (s->mode == 0 && !volume->mmu_segmentation_facets.get_data().triangles_to_split.empty())
                volume->config.set("extruder", 0);
        }
    }
    JNIEXPORT jfloatArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1get_1mesh(JNIEnv* env, jclass, jlong sessionPtr, jint filamentIdx) {
        PaintSessionRef* s = (PaintSessionRef*)(intptr_t)sessionPtr;
        std::vector<float> buffer;
        if (s && s->model && s->objIdx >= 0 && s->objIdx < (int)s->model->model.objects.size()) {
            ModelObject* obj = s->model->model.objects[s->objIdx];
            const int instances = std::max(1, (int)obj->instances.size());
            for (const auto& volume_ref : s->volumes) {
                const PaintVolumeRef& volume = *volume_ref;
                const indexed_triangle_set part = volume.selector->get_facets(paint_state(filamentIdx));
                for (int instance = 0; instance < instances; ++instance) {
                    const Transform3d inst = obj->instances.empty() || !obj->instances[instance]
                        ? Transform3d::Identity() : obj->instances[instance]->get_matrix();
                    const Transform3d transform = inst * obj->volumes[volume.volume_idx]->get_matrix();
                    for (const Vec3i32& tri : part.indices) for (int k = 0; k < 3; ++k) {
                        const Vec3f vertex = (transform * part.vertices[tri[k]].cast<double>()).cast<float>();
                        buffer.push_back(vertex.x()); buffer.push_back(vertex.y()); buffer.push_back(vertex.z());
                    }
                }
            }
        }
        jfloatArray result = env->NewFloatArray((jsize)buffer.size());
        if (!buffer.empty()) env->SetFloatArrayRegion(result, 0, (jsize)buffer.size(), buffer.data());
        return result;
    }
    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1end(JNIEnv*, jclass, jlong sessionPtr) {
        delete (PaintSessionRef*)(intptr_t)sessionPtr;
    }

    static indexed_triangle_set accumulate_paint_facets(Slic3r::ModelObject* obj, Slic3r::EnforcerBlockerType state) {
        indexed_triangle_set out;
        for (const Slic3r::ModelInstance* inst : obj->instances) {
            const Slic3r::Transform3d inst_m = inst->get_matrix();
            for (const Slic3r::ModelVolume* v : obj->volumes) {
                if (!v->is_model_part()) continue;
                const Slic3r::Transform3d m = inst_m * v->get_matrix();
                auto append = [&](const indexed_triangle_set& part) {
                    const int base = (int) out.vertices.size();
                    out.vertices.reserve(out.vertices.size() + part.vertices.size());
                    for (const stl_vertex& vert : part.vertices)
                        out.vertices.emplace_back((m * vert.cast<double>()).cast<float>());
                    out.indices.reserve(out.indices.size() + part.indices.size());
                    for (const stl_triangle_vertex_indices& tri : part.indices)
                        out.indices.emplace_back(tri + stl_triangle_vertex_indices(base, base, base));
                };
                const auto& data = v->mmu_segmentation_facets.get_data();
                const bool base_matches = std::max(1, v->extruder_id()) == (int) state;
                if (data.triangles_to_split.empty()) {
                    if (base_matches) append(v->mesh().its);
                    continue;
                }
                Slic3r::TriangleSelector sel(v->mesh());
                sel.deserialize(data, true);
                append(sel.get_facets(state));
                if (base_matches) append(sel.get_facets(Slic3r::EnforcerBlockerType::NONE));
            }
        }
        return out;
    }

    // Build the paint-mode preview from the same complete assembly as the committed overlay.
    // The live selector edits volume[0], but other volumes can already carry imported 3MF
    // colors. Hiding committed overlays while painting without restoring those untouched parts
    // made their colors appear to vanish until the user confirmed the session.
    static indexed_triangle_set accumulate_live_paint_facets(
        PaintSessionRef* session,
        Slic3r::EnforcerBlockerType state,
        int base_filament_preview) {
        indexed_triangle_set out;
        if (session == nullptr || session->model == nullptr ||
            session->objIdx < 0 || session->objIdx >= (int)session->model->model.objects.size())
            return out;

        ModelObject* obj = session->model->model.objects[session->objIdx];
        if (obj == nullptr || obj->volumes.empty()) return out;
        auto append_transformed = [&](const indexed_triangle_set& part, const Transform3d& transform) {
            if (part.indices.empty()) return;
            const int base = (int)out.vertices.size();
            out.vertices.reserve(out.vertices.size() + part.vertices.size());
            for (const stl_vertex& vertex : part.vertices)
                out.vertices.emplace_back((transform * vertex.cast<double>()).cast<float>());
            out.indices.reserve(out.indices.size() + part.indices.size());
            for (const stl_triangle_vertex_indices& triangle : part.indices)
                out.indices.emplace_back(triangle + stl_triangle_vertex_indices(base, base, base));
        };

        const std::vector<const ModelInstance*> instances = obj->instances.empty()
            ? std::vector<const ModelInstance*>{ nullptr }
            : std::vector<const ModelInstance*>(obj->instances.begin(), obj->instances.end());
        for (const ModelInstance* instance : instances) {
            const Transform3d instance_matrix = instance != nullptr
                ? instance->get_matrix() : Transform3d::Identity();
            for (ModelVolume* volume : obj->volumes) {
                if (volume == nullptr || !volume->is_model_part()) continue;
                const Transform3d transform = instance_matrix * volume->get_matrix();
                const int volume_idx = (int)std::distance(obj->volumes.begin(), std::find(obj->volumes.begin(), obj->volumes.end(), volume));
                PaintVolumeRef* editable = paint_volume(session, volume_idx);
                const bool base_volume = volume_idx == 0;
                const int base_filament = base_volume && base_filament_preview > 0
                    ? base_filament_preview : std::max(1, volume->extruder_id());
                const bool base_matches = base_filament == (int)state;
                if (editable != nullptr) {
                    append_transformed(editable->selector->get_facets(state), transform);
                    if (session->mode == 0 && base_matches)
                        append_transformed(editable->selector->get_facets(Slic3r::EnforcerBlockerType::NONE), transform);
                    continue;
                }

                const auto& data = facets_for_mode(volume, session->mode).get_data();
                if (data.triangles_to_split.empty()) {
                    if (base_matches) append_transformed(volume->mesh().its, transform);
                    continue;
                }
                Slic3r::TriangleSelector selector(volume->mesh());
                selector.deserialize(data, true);
                append_transformed(selector.get_facets(state), transform);
                if (base_matches)
                    append_transformed(selector.get_facets(Slic3r::EnforcerBlockerType::NONE), transform);
            }
        }
        return out;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1build_1paint_1overlay(JNIEnv* env, jclass, jlong modelPtr, jint objIdx, jlong glPtr, jint filamentIdx) {
        ModelRef* m = (ModelRef*) (intptr_t) modelPtr;
        if (objIdx < 0 || objIdx >= (int) m->model.objects.size()) return 0;
        ModelObject* obj = m->model.objects[objIdx];
        if (obj->volumes.empty()) return 0;
        indexed_triangle_set its = accumulate_paint_facets(obj, paint_state(filamentIdx));
        GLModelRef* g = (GLModelRef*) (intptr_t) glPtr;
        g->model.reset();
        if (!its.indices.empty())
            g->model.init_from(its);
        return (jint) its.indices.size();
    }

    // Native regression seam for the Orca match-mode ranking. It runs the production
    // FilamentGroup solver against small synthetic printer-slot palettes on-device.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_filament_1group_1match_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        using namespace Slic3r::FilamentGroupUtils;
        auto choose_slot = [](const std::string& model_color, const std::string& model_id,
                              const std::string& slot0_color, const std::string& slot0_id,
                              const std::string& slot1_color, const std::string& slot1_id) {
            FilamentGroupContext context{};
            context.model_info.layer_filaments = {{0}};
            Slic3r::FilamentGroupUtils::FilamentInfo model_filament;
            model_filament.color = Color(model_color);
            model_filament.type = "PLA";
            model_filament.is_support = false;
            context.model_info.filament_info = {model_filament};
            context.model_info.filament_ids = {model_id};
            context.model_info.unprintable_filaments.resize(2);
            context.group_info.total_filament_num = 1;
            context.group_info.max_gap_threshold = 0.01;
            context.group_info.mode = MatchMode;
            context.group_info.strategy = BestCost;
            context.group_info.ignore_ext_filament = false;
            context.machine_info.master_extruder_id = 0;
            context.machine_info.max_group_size = {1, 1};
            const std::array<std::pair<std::string, std::string>, 2> slots{{
                {slot0_color, slot0_id}, {slot1_color, slot1_id}
            }};
            std::vector<std::vector<DynamicPrintConfig>> machine_configs(2);
            std::vector<std::map<int, int>> ams_counts(2);
            for (int extruder = 0; extruder < 2; ++extruder) {
                DynamicPrintConfig slot_config;
                slot_config.set_key_value("filament_colour", new ConfigOptionStrings({slots[extruder].first}));
                slot_config.set_key_value("filament_type", new ConfigOptionStrings({"PLA"}));
                slot_config.set_key_value("tray_name", new ConfigOptionStrings({"T0"}));
                slot_config.set_key_value("filament_id", new ConfigOptionStrings({slots[extruder].second}));
                machine_configs[extruder].push_back(std::move(slot_config));
                ams_counts[extruder][1] = 1;
            }
            context.machine_info.machine_filament_info = build_machine_filaments(machine_configs, ams_counts, true);
            FilamentGroup group(context);
            const std::vector<int> mapping = group.calc_filament_group_for_match();
            return mapping.empty() ? -1 : mapping[0];
        };

        const std::array<jint, 4> selected{{
            choose_slot("#FFFFFF", "GFA00", "#FFFFFF", "GFL99", "#FFFFFF", "GFA00"),
            choose_slot("#FFFFFF", "GFA00", "#FFFFFF", "GFL99", "#D0D0D0", "GFA00"),
            choose_slot("#37A850", "", "#40C111", "", "#3F8A30", ""),
            choose_slot("#FFFFFF", "GFA00", "#F0F1F2", "GFA00", "#F0F1F1", "GFL99")
        }};
        jintArray result = env->NewIntArray(static_cast<jsize>(selected.size()));
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(selected.size()), selected.data());
        return result;
    }

    // Orca regression: an unloaded extruder group must be penalized once per model filament,
    // otherwise a 3:1 and 1:3 candidate split tie even when the latter matches loaded slots.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_filament_1group_1empty_1loaded_1slot_1penalty_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        using namespace Slic3r::FilamentGroupUtils;

        const std::vector<unsigned int> used_filaments{0, 1, 2, 3};
        const std::array<const char*, 4> colors{{"#FF0000", "#00FF00", "#0000FF", "#FFFFFF"}};
        std::vector<Slic3r::FilamentGroupUtils::FilamentInfo> used_info;
        used_info.reserve(colors.size());
        for (const char* color : colors)
            used_info.push_back({Color(color), "PLA", false});

        std::vector<std::vector<MachineFilamentInfo>> loaded(2);
        for (int filament = 1; filament < 4; ++filament) {
            MachineFilamentInfo slot{};
            slot.color = Color(colors[filament]);
            slot.type = "PLA";
            slot.is_support = false;
            slot.extruder_id = 1;
            slot.is_extended = false;
            loaded[1].push_back(std::move(slot));
        }

        const std::vector<std::vector<int>> candidates{
            {0, 0, 0, 1}, // Three filaments on the empty group, one match on the loaded group.
            {0, 1, 1, 1}, // One filament on the empty group, three exact loaded-slot matches.
        };
        const std::vector<int> best = select_best_group_for_ams(candidates, used_filaments, used_info, loaded);
        jintArray result = env->NewIntArray(static_cast<jsize>(best.size()));
        if (result != nullptr && !best.empty())
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(best.size()), best.data());
        return result;
    }

    // Regression for Orca's flush-saving candidate pool: an AMS match may justify one typical
    // filament-change worth of extra flush when that keeps both model filaments on their loaded slots.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_filament_1group_1flush_1candidate_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        using namespace Slic3r::FilamentGroupUtils;

        FilamentGroupContext context{};
        context.model_info.layer_filaments = {{0, 1}};
        // reorder_filaments_for_minimum_flush_volume needs one matrix per extruder group.
        context.model_info.flush_matrix = {
            FlushMatrix{{0.f, 100.f}, {100.f, 0.f}},
            FlushMatrix{{0.f, 100.f}, {100.f, 0.f}}
        };
        context.model_info.filament_info = {
            {Color("#FF0000"), "PLA", false},
            {Color("#0000FF"), "PLA", false}
        };
        context.model_info.unprintable_filaments.resize(2);
        context.group_info.total_filament_num = 2;
        context.group_info.max_gap_threshold = 0.01;
        context.group_info.mode = FlushMode;
        context.group_info.strategy = BestCost;
        context.group_info.ignore_ext_filament = false;
        context.machine_info.master_extruder_id = 1;
        context.machine_info.max_group_size = {2, 2};
        context.machine_info.machine_filament_info.resize(2);

        auto add_loaded_slot = [&context](int extruder_id, const char* color) {
            MachineFilamentInfo slot{};
            slot.color = Color(color);
            slot.type = "PLA";
            slot.is_support = false;
            slot.extruder_id = extruder_id;
            slot.is_extended = false;
            context.machine_info.machine_filament_info[extruder_id].push_back(std::move(slot));
        };
        add_loaded_slot(0, "#FF0000");
        add_loaded_slot(0, "#0000FF");
        add_loaded_slot(1, "#FFFFFF");

        FilamentGroup group(context);
        const std::vector<int> mapping = group.calc_filament_group_for_flush();
        const std::array<jint, 2> selected{{mapping.size() > 0 ? mapping[0] : -1,
                                             mapping.size() > 1 ? mapping[1] : -1}};
        jintArray result = env->NewIntArray(static_cast<jsize>(selected.size()));
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(selected.size()), selected.data());
        return result;
    }

    // Exercises the production scarf split around layer-dependent perimeter vertices.
    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_scarf_1ramp_1regression(JNIEnv* env, jclass) {
        auto probe = [](double near_vertex, bool corner) {
            using namespace Slic3r;
            ExtrusionPaths source;
            source.emplace_back(erExternalPerimeter, 1., 0.45f, 0.2f);
            const std::array<Point3, 7> points = corner
                ? std::array<Point3, 7>{Point3::new_scale(0., 0., 0.), Point3::new_scale(5., 0., 0.),
                    Point3::new_scale(10., 0., 0.), Point3::new_scale(15., 0., 0.),
                    Point3::new_scale(near_vertex, 0., 0.), Point3::new_scale(24.63, 2.5, 0.),
                    Point3::new_scale(28.96, 5., 0.)}
                : std::array<Point3, 7>{Point3::new_scale(0., 0., 0.), Point3::new_scale(5., 0., 0.),
                    Point3::new_scale(10., 0., 0.), Point3::new_scale(15., 0., 0.),
                    Point3::new_scale(near_vertex, 0., 0.), Point3::new_scale(25., 0., 0.),
                    Point3::new_scale(30., 0., 0.)};
            for (const Point3& point : points) source.front().polyline.append(point);
            ExtrusionLoopSloped loop(source, 0., 20., scale_(2.), 0.);
            const auto &flat = loop.paths.front().polyline;
            return std::array<jdouble, 4>{
                unscale<double>(loop.starts.front().polyline.last_point().x()),
                unscale<double>(flat.first_point().x()),
                unscale<double>(flat.lines().front().length()),
                unscale<double>(flat.last_point().x()),
            };
        };
        auto probe_bent = []() {
            using namespace Slic3r;
            ExtrusionPaths source;
            source.emplace_back(erExternalPerimeter, 1., 0.45f, 0.2f);
            const std::array<Point3, 7> points{
                Point3::new_scale(0., 0., 0.), Point3::new_scale(5., 0., 0.),
                Point3::new_scale(10., 0., 0.), Point3::new_scale(15., 0., 0.),
                Point3::new_scale(20.3, 0., 0.), Point3::new_scale(25.28, 0.436, 0.),
                Point3::new_scale(30.26, 0.872, 0.)};
            for (const Point3& point : points) source.front().polyline.append(point);
            ExtrusionLoopSloped loop(source, 0., 20., scale_(2.), 0.);
            const auto &flat = loop.paths.front().polyline;
            return std::array<jdouble, 3>{
                unscale<double>(loop.starts.front().polyline.last_point().x()),
                unscale<double>(flat.lines().front().length()),
                double(flat.size())};
        };
        auto probe_z_contour = []() {
            using namespace Slic3r;
            ExtrusionPaths source;
            source.emplace_back(erExternalPerimeter, 1., 0.45f, 0.2f);
            const std::array<Point3, 7> points{
                Point3::new_scale(0., 0., 0.), Point3::new_scale(5., 0., 0.),
                Point3::new_scale(10., 0., 0.), Point3::new_scale(15., 0., 0.),
                Point3::new_scale(20.3, 0., 0.3), Point3::new_scale(25., 0., -1.),
                Point3::new_scale(30., 0., -1.)};
            for (const Point3& point : points) source.front().polyline.append(point);
            ExtrusionLoopSloped loop(source, 0., 20., scale_(2.), 0.);
            const auto &flat = loop.paths.front().polyline;
            return std::array<jdouble, 3>{
                unscale<double>(loop.starts.front().polyline.last_point().x()),
                unscale<double>(flat.first_point().z()),
                unscale<double>(flat.last_point().z())};
        };
        auto probe_multi_path = []() {
            using namespace Slic3r;
            ExtrusionPaths source;
            source.emplace_back(erExternalPerimeter, 1., 0.45f, 0.2f);
            for (double x : {0., 5., 12.}) source.front().polyline.append(Point3::new_scale(x, 0., 0.));
            source.emplace_back(erExternalPerimeter, 1., 0.45f, 0.2f);
            for (double x : {12., 15., 20.2, 25., 30.}) source.back().polyline.append(Point3::new_scale(x, 0., 0.));
            ExtrusionLoopSloped loop(source, 0., 20., scale_(2.), 0.);
            const auto &flat = loop.paths.front().polyline;
            return std::array<jdouble, 3>{
                unscale<double>(loop.starts.back().polyline.last_point().x()),
                unscale<double>(flat.lines().front().length()),
                unscale<double>(flat.last_point().x())};
        };
        const auto first = probe(20.1, false);
        const auto second = probe(20.4, false);
        const auto corner = probe(20.3, true);
        const auto bent = probe_bent();
        const auto z_contour = probe_z_contour();
        const auto multi_path = probe_multi_path();
        const jdouble values[] = {first[0], first[1], first[2], first[3],
                                  second[0], second[1], second[2], second[3],
                                  corner[0], corner[1], corner[2], corner[3],
                                  bent[0], bent[1], bent[2],
                                  z_contour[0], z_contour[1], z_contour[2],
                                  multi_path[0], multi_path[1], multi_path[2]};
        jdoubleArray result = env->NewDoubleArray(21);
        if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 21, values);
        return result;
    }

    // Exercises Orca's height-aware collision-volume parser and nozzle-space resolution.
    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1exclude_1volumes_1regression(JNIEnv* env, jclass)
    {
        try {
            using namespace Slic3r;
            auto make_config = [](BedExcludeVolumeMode mode, const std::string &shared,
                                  std::vector<std::string> per_extruder = {},
                                  Pointfs offsets = {}, Pointfs legacy_bed_area = {}) {
                DynamicPrintConfig dynamic = DynamicPrintConfig::full_print_config();
                dynamic.set_deserialize_strict({
                    {"printable_height", "100"}, {"nozzle_diameter", "0.4,0.4"},
                    {"master_extruder_id", "1"}});
                dynamic.set_key_value("bed_exclude_volume_mode", new ConfigOptionEnum<BedExcludeVolumeMode>(mode));
                dynamic.set_key_value("bed_exclude_volumes", new ConfigOptionString(shared));
                dynamic.set_key_value("extruder_bed_exclude_volumes", new ConfigOptionStrings(std::move(per_extruder)));
                dynamic.set_key_value("extruder_offset", new ConfigOptionPoints(std::move(offsets)));
                dynamic.set_key_value("bed_exclude_area", new ConfigOptionPoints(std::move(legacy_bed_area)));
                PrintConfig config;
                config.apply(dynamic, true);
                return config;
            };
            auto min_x_mm = [](const BedExcludeRegion &region) {
                coord_t min_x = std::numeric_limits<coord_t>::max();
                for (const Point &point : region.polygon.points) min_x = std::min(min_x, point.x());
                return unscale<double>(min_x);
            };

            const std::string shared = "..10;20x20,40x20,40x40,20x40|30..;5x5,8x5,8x8,5x8";
            const PrintConfig shared_config = make_config(BedExcludeVolumeMode::Shared, shared);
            const auto shared_regions = get_bed_excluded_regions(shared_config, 0);
            const auto offset_config = make_config(BedExcludeVolumeMode::ToolheadOffset, "20x20,40x20,40x40,20x40", {},
                                                   {Vec2d(0., 0.), Vec2d(10., 0.)},
                                                   {Vec2d(1., 1.), Vec2d(2., 1.), Vec2d(2., 2.), Vec2d(1., 2.)});
            const auto offset_regions = get_bed_excluded_regions(offset_config, 1);
            const auto per_config = make_config(BedExcludeVolumeMode::PerExtruder, {},
                                                {"1x1,2x1,2x2,1x2", "11x1,12x1,12x2,11x2"});
            const auto first_per = get_bed_excluded_regions(per_config, 0);
            const auto second_per = get_bed_excluded_regions(per_config, 1);
            const jdouble values[] = {
                double(shared_regions.size()), shared_regions.at(0).z_min, shared_regions.at(0).z_max,
                min_x_mm(shared_regions.at(0)), shared_regions.at(1).z_min, shared_regions.at(1).z_max,
                is_valid_bed_exclude_volumes_string(shared, 100.) ? 1. : 0.,
                is_valid_bed_exclude_volumes_string("0x0,10x0,NaNx10", 100.) ? 1. : 0.,
                min_x_mm(offset_regions.at(1)), double(first_per.size()), min_x_mm(first_per.at(0)),
                double(second_per.size()), min_x_mm(second_per.at(0)), min_x_mm(offset_regions.at(0))};
            jdoubleArray result = env->NewDoubleArray(14);
            if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 14, values);
            return result;
        } catch (const std::exception &error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Confirms fixed collision volumes participate in real libnest2d packing and
    // that height/nozzle filtering is applied before generating no-fit polygons.
    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1exclude_1arrangement_1regression(JNIEnv* env, jclass)
    {
        try {
            using namespace Slic3r;
            const Points bed{Point(0, 0), Point(scaled(100.), 0), Point(scaled(100.), scaled(100.)), Point(0, scaled(100.))};
            auto can_place = [&](double item_height, double volume_min_z, double volume_max_z,
                                 int item_nozzle, int volume_nozzle) {
                arrangement::ArrangePolygon item;
                item.poly = ExPolygon(Polygon({Point(0, 0), Point(scaled(20.), 0),
                    Point(scaled(20.), scaled(20.)), Point(0, scaled(20.))}));
                item.height = item_height;
                item.z_min = 0.; item.z_max = item_height; item.has_z_range = true;
                item.bed_exclusion_extruder_ids = {item_nozzle};

                arrangement::ArrangePolygon volume;
                volume.poly = ExPolygon(Polygon({Point(scaled(40.), scaled(40.)), Point(scaled(60.), scaled(40.)),
                    Point(scaled(60.), scaled(60.)), Point(scaled(40.), scaled(60.))}));
                volume.bed_idx = 0;
                volume.is_virt_object = true;
                volume.is_bed_exclusion = true;
                volume.has_z_range = true;
                volume.z_min = volume_min_z; volume.z_max = volume_max_z;
                volume.bed_exclusion_extruder_id = volume_nozzle;

                arrangement::ArrangeParams params;
                params.parallel = false;
                params.do_final_align = false;
                params.progressind = [](unsigned, std::string) {};
                params.excluded_regions.push_back(std::move(volume));
                arrangement::ArrangePolygons items{std::move(item)};
                arrangement::arrange(items, params.excluded_regions, bed, params);
                if (items.front().bed_idx != 0) return -1.;
                const auto overlaps = intersection_ex(ExPolygons{items.front().transformed_poly()},
                    ExPolygons{params.excluded_regions.front().poly});
                const double area_scale = double(scale_(1.)) * scale_(1.);
                double overlap_area = 0.;
                for (const ExPolygon &overlap : overlaps) overlap_area += overlap.area();
                return overlap_area / area_scale;
            };
            const jdouble values[] = {
                can_place(5., 0., 10., 0, -1),       // collision blocks matching Z
                can_place(5., 10., 20., 0, -1),     // no Z overlap: placeable
                can_place(5., 0., 10., 0, 1),       // different physical nozzle: placeable
                can_place(5., 0., 10., 0, 0)        // matching physical nozzle: blocked
            };
            jdoubleArray result = env->NewDoubleArray(4);
            if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 4, values);
            return result;
        } catch (const std::exception &error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Exercises Orca's sequential-arrangement edge cases on a deterministic 200 mm bed.
    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_short_1object_1arrangement_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        DynamicPrintConfig config;
        config.set_key_value("printable_area", new ConfigOptionPoints({
            Vec2d(0., 0.), Vec2d(200., 0.), Vec2d(200., 200.), Vec2d(0., 200.)
        }));
        const Points physical_bed = get_bed_shape(config);
        const BoundingBox physical_bounds(physical_bed);

        auto make_square = [](double side, double height) {
            Polygon polygon;
            polygon.points = {Point(0, 0), Point(scaled(side), 0),
                Point(scaled(side), scaled(side)), Point(0, scaled(side))};
            arrangement::ArrangePolygon item;
            item.poly = ExPolygon(std::move(polygon));
            item.bed_idx = 0;
            item.height = height;
            item.extrude_ids = {1};
            return item;
        };
        auto make_disc = [](double diameter, double height) {
            Polygon polygon;
            for (int i = 0; i < 64; ++i) {
                const double angle = 2. * PI * i / 64.;
                polygon.points.emplace_back(scaled(diameter / 2. * std::cos(angle)),
                    scaled(diameter / 2. * std::sin(angle)));
            }
            arrangement::ArrangePolygon item;
            item.poly = ExPolygon(std::move(polygon));
            item.bed_idx = 0;
            item.height = height;
            item.extrude_ids = {1};
            return item;
        };
        auto arrange_short = [&](arrangement::ArrangePolygons& items) {
            arrangement::ArrangeParams params;
            params.progressind = [](unsigned, std::string) {};
            params.parallel = false;
            params.is_seq_print = true;
            // Match Orca's regression fixture: 2.5 mm nozzle/clearance height and
            // objects at half that height must use the nozzle-width floor.
            params.nozzle_height = 2.5f;
            params.clearance_radius = 30.f;
            params.clearance_height_to_rod = 4.f;
            params.clearance_height_to_lid = 16.f;
            params.bed_shrink_x = BED_SHRINK_SEQ_PRINT;
            params.bed_shrink_y = BED_SHRINK_SEQ_PRINT;
            arrangement::update_arrange_params(params, &config, items);
            arrangement::update_selected_items_inflation(items, &config, params);
            arrangement::ArrangePolygons fixed;
            arrangement::update_unselected_items_inflation(fixed, &config, params);
            const Points arrange_bed = arrangement::get_shrink_bedpts(&config, params);
            arrangement::arrange(items, fixed, arrange_bed, params);
        };
        auto is_inside_bed = [&](const arrangement::ArrangePolygon& item) {
            return item.bed_idx == 0 && physical_bounds.contains(item.transformed_poly().contour.bounding_box());
        };

        arrangement::ArrangePolygons disc{make_disc(196., 1.25)};
        arrange_short(disc);
        const bool large_short_inside = is_inside_bed(disc.front());

        const double turned_side = 196. / (std::cos(PI / 6.) + std::sin(PI / 6.));
        arrangement::ArrangePolygon turned = make_square(turned_side, 1.25);
        turned.rotation = PI / 6.;
        arrangement::ArrangePolygons turned_items{std::move(turned)};
        arrange_short(turned_items);
        const bool turned_short_inside = is_inside_bed(turned_items.front());

        arrangement::ArrangePolygons crowded;
        for (int i = 0; i < 20; ++i)
            crowded.emplace_back(make_square(50., 1.25));
        arrange_short(crowded);
        int placed = 0;
        int outside = 0;
        int overlapping_clearance_pairs = 0;
        for (const auto& item : crowded) {
            if (item.bed_idx == 0) {
                ++placed;
                if (!is_inside_bed(item)) ++outside;
            }
        }
        for (size_t i = 0; i < crowded.size(); ++i) {
            if (crowded[i].bed_idx != 0) continue;
            const ExPolygons left = offset_ex(crowded[i].transformed_poly(), float(crowded[i].inflation), jtSquare);
            for (size_t j = i + 1; j < crowded.size(); ++j) {
                if (crowded[j].bed_idx != 0) continue;
                const ExPolygons right = offset_ex(crowded[j].transformed_poly(), float(crowded[j].inflation), jtSquare);
                if (!intersection_ex(left, right).empty()) ++overlapping_clearance_pairs;
            }
        }
        const auto disc_bounds = disc.front().transformed_poly().contour.bounding_box();
        const auto turned_bounds = turned_items.front().transformed_poly().contour.bounding_box();
        const jdouble values[] = {large_short_inside ? 1. : 0., static_cast<double>(disc.front().bed_idx),
            unscale<double>(disc_bounds.min.x()), unscale<double>(disc_bounds.min.y()),
            unscale<double>(disc_bounds.max.x()), unscale<double>(disc_bounds.max.y()),
            turned_short_inside ? 1. : 0., static_cast<double>(turned_items.front().bed_idx),
            unscale<double>(turned_bounds.min.x()), unscale<double>(turned_bounds.min.y()),
            unscale<double>(turned_bounds.max.x()), unscale<double>(turned_bounds.max.y()),
            static_cast<double>(placed), static_cast<double>(outside), static_cast<double>(overlapping_clearance_pairs)};
        jdoubleArray result = env->NewDoubleArray(15);
        if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 15, values);
        return result;
    }

    // Orca arrange should use the printable area efficiently for an item that nearly fills it,
    // while retaining the configured skirt clearance and brim inflation.
    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_near_1bed_1size_1arrangement_1regression(JNIEnv* env, jclass) {
        try {
            using namespace Slic3r;
            DynamicPrintConfig config;
            config.set_key_value("printable_area", new ConfigOptionPoints({
                Vec2d(0., 0.), Vec2d(200., 0.), Vec2d(200., 200.), Vec2d(0., 200.)
            }));
            config.set_key_value("skirt_distance", new ConfigOptionFloat(0.));
            config.set_key_value("skirt_loops", new ConfigOptionInt(0));

            Polygon footprint({Point(0, 0), Point(scaled(197.), 0),
                Point(scaled(197.), scaled(197.)), Point(0, scaled(197.))});
            arrangement::ArrangePolygon item;
            item.poly = ExPolygon(std::move(footprint));
            item.brim_width = 1.;
            item.height = 10.;
            item.extrude_ids = {1};
            arrangement::ArrangePolygons items{std::move(item)};

            arrangement::ArrangeParams params;
            params.parallel = false;
            params.progressind = [](unsigned, std::string) {};
            arrangement::update_arrange_params(params, &config, items);
            arrangement::update_selected_items_inflation(items, &config, params);
            const Points arrange_bed = arrangement::get_shrink_bedpts(&config, params);
            arrangement::arrange(items, arrange_bed, params);

            const BoundingBox physical_bounds(get_bed_shape(config));
            const BoundingBox packed_bounds = items.front().transformed_poly().contour.bounding_box();
            const bool inside = items.front().bed_idx == 0 && physical_bounds.contains(packed_bounds);
            const jdouble values[] = {inside ? 1. : 0., static_cast<double>(items.front().bed_idx),
                params.bed_shrink_x, params.bed_shrink_y,
                unscale<double>(packed_bounds.size().x()), unscale<double>(packed_bounds.size().y())};
            jdoubleArray result = env->NewDoubleArray(6);
            if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 6, values);
            return result;
        } catch (const std::exception &error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // A status callback may be replaced from a UI callback while a slice worker is
    // reporting progress. Verify callbacks run outside the registration lock and that
    // concurrent replacement/reporting does not race on std::function storage.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_print_1status_1callback_1regression(JNIEnv* env, jclass) {
        try {
            Print print;
            std::atomic<int> first_callback{0};
            std::atomic<int> replacement_callback{0};
            std::atomic<int> stress_callbacks{0};
            std::atomic<int> final_callback{0};

            print.set_status_callback([&](const PrintBase::SlicingStatus&) {
                ++first_callback;
                print.set_status_callback([&](const PrintBase::SlicingStatus&) { ++replacement_callback; });
            });
            print.set_status(1, "first callback replaces itself");
            print.set_status(2, "replacement callback runs");

            auto stress_callback = [&stress_callbacks](const PrintBase::SlicingStatus&) { ++stress_callbacks; };
            print.set_status_callback(stress_callback);
            print.set_status(3, "stress callback is installed before concurrent updates");

            std::thread replace_worker([&] {
                for (int i = 0; i < 2000; ++i)
                    print.set_status_callback((i & 1) ? stress_callback : PrintBase::status_callback_type{});
            });
            std::thread report_worker([&] {
                for (int i = 0; i < 2000; ++i)
                    print.set_status(i % 100, "concurrent callback report");
            });
            replace_worker.join();
            report_worker.join();

            print.set_status_callback([&](const PrintBase::SlicingStatus&) { ++final_callback; });
            print.set_status(100, "final callback remains callable");
            const jint values[] = {first_callback.load(), replacement_callback.load(),
                stress_callbacks.load(), final_callback.load()};
            jintArray result = env->NewIntArray(4);
            if (result != nullptr) env->SetIntArrayRegion(result, 0, 4, values);
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Orca preset transfers may diff a serialized list as post_process#0. An empty
    // source must clear the target list; set_at() cannot read an element that does not exist.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_config_1apply_1empty_1serialized_1list_1regression(JNIEnv* env, jclass) {
        try {
            DynamicPrintConfig source_with_script = DynamicPrintConfig::full_print_config();
            source_with_script.set_key_value("post_process", new ConfigOptionStrings({"script.py"}));
            DynamicPrintConfig empty_target = DynamicPrintConfig::full_print_config();
            empty_target.set_key_value("post_process", new ConfigOptionStrings());
            empty_target.apply_only(source_with_script, {"post_process#0"});
            const auto* imported = empty_target.option<ConfigOptionStrings>("post_process", false);
            const bool created = imported != nullptr && imported->values == std::vector<std::string>{"script.py"};

            DynamicPrintConfig empty_source = DynamicPrintConfig::full_print_config();
            empty_source.set_key_value("post_process", new ConfigOptionStrings());
            DynamicPrintConfig populated_target = DynamicPrintConfig::full_print_config();
            populated_target.set_key_value("post_process", new ConfigOptionStrings({"old-script.py"}));
            populated_target.apply_only(empty_source, {"post_process#0"});
            const auto* cleared = populated_target.option<ConfigOptionStrings>("post_process", false);
            const bool emptied = cleared != nullptr && cleared->values.empty();

            const jint values[] = {created ? 1 : 0, emptied ? 1 : 0};
            jintArray result = env->NewIntArray(2);
            if (result != nullptr) env->SetIntArrayRegion(result, 0, 2, values);
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Exercises production G-code placeholder vector resolution for filament IDs that
    // share a physical extruder, plus the unmapped-filament fallback.
    JNIEXPORT jobjectArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_placeholder_1filament_1map_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        DynamicConfig external;
        external.set_key_value("filament_map", new ConfigOptionInts({1, 1}));
        PlaceholderParser parser(&external);
        DynamicPrintConfig values;
        values.set_key_value("retraction_length", new ConfigOptionFloats({0.4, 3.0}));
        values.set_key_value("nozzle_diameter", new ConfigOptionFloats({0.4, 0.6}));
        values.set_key_value("test_vector", new ConfigOptionFloatsOrPercents({FloatOrPercent{0.2, false}, FloatOrPercent{0.7, false}}));
        parser.apply_config(values);
        std::vector<std::string> resolved;
        resolved.emplace_back(parser.process("{retraction_length}", 0));
        resolved.emplace_back(parser.process("{retraction_length}", 1));
        resolved.emplace_back(parser.process("{nozzle_diameter}", 0));
        resolved.emplace_back(parser.process("{nozzle_diameter}", 1));
        resolved.emplace_back(parser.process("{test_vector}", 1));
        external.set_key_value("filament_map", new ConfigOptionInts({1, 0}));
        resolved.emplace_back(parser.process("{nozzle_diameter}", 1));

        jclass string_class = env->FindClass("java/lang/String");
        jobjectArray result = env->NewObjectArray(static_cast<jsize>(resolved.size()), string_class, nullptr);
        for (jsize i = 0; i < static_cast<jsize>(resolved.size()); ++i) {
            jstring value = env->NewStringUTF(resolved[size_t(i)].c_str());
            env->SetObjectArrayElement(result, i, value);
            env->DeleteLocalRef(value);
        }
        env->DeleteLocalRef(string_class);
        return result;
    }

    // Regression for object_extruders' zero-based IDs being passed to the one-based nozzle limit lookup.
    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_object_1nozzle_1layer_1limits_1regression(JNIEnv* env, jclass)
    {
        try {
            DynamicPrintConfig config = DynamicPrintConfig::full_print_config();
            config.set_deserialize_strict({
                { "nozzle_diameter",  "0.4,0.4,0.4" },
                { "min_layer_height", "0.04,0.08,0.12" },
                { "max_layer_height", "0.48,0.32,0.24" },
                { "layer_height",     0.12 },
                { "enable_support",   0 },
                { "raft_layers",      0 },
            });
            PrintConfig print_config;
            PrintObjectConfig object_config;
            print_config.apply(config, true);
            object_config.apply(config, true);
            const auto first = SlicingParameters::create_from_config(
                print_config, object_config, 20., { 0 }, Vec3d(1., 1., 1.));
            const auto third = SlicingParameters::create_from_config(
                print_config, object_config, 20., { 2 }, Vec3d(1., 1., 1.));
            const std::array<jdouble, 4> values{
                first.min_layer_height, first.max_layer_height,
                third.min_layer_height, third.max_layer_height,
            };
            jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(values.size()));
            if (result != nullptr) env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Reuses the production Print::apply path across a single-filament then a multi-filament
    // model. The tower must be disabled for the first model and re-enabled for the second.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_reused_1print_1prime_1tower_1regression(
        JNIEnv* env, jclass, jlong first_model_ptr, jlong next_model_ptr, jstring config_path)
    {
        try {
            auto* first_model = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(first_model_ptr));
            auto* next_model = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(next_model_ptr));
            if (first_model == nullptr || next_model == nullptr || config_path == nullptr)
                throw std::invalid_argument("Prime-tower regression received a null model or config path");

            const char* path = env->GetStringUTFChars(config_path, nullptr);
            if (path == nullptr) return nullptr;
            DynamicPrintConfig config;
            config.load(path, ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(config_path, path);
            if (!config.has("enable_prime_tower"))
                throw std::runtime_error("Prime-tower regression config has no enable_prime_tower option");
            config.normalize_fdm();

            Print print;
            print.is_BBL_printer() = false;
            print.apply(first_model->model, config);
            const jint first_tower = print.config().enable_prime_tower.value ? 1 : 0;
            const jint first_extruders = static_cast<jint>(print.extruders().size());
            print.process();
            const jint first_generated_tower = print.has_wipe_tower() ? 1 : 0;

            print.apply(next_model->model, config);
            const jint next_tower = print.config().enable_prime_tower.value ? 1 : 0;
            const jint next_extruders = static_cast<jint>(print.extruders().size());
            print.process();
            const jint next_generated_tower = print.has_wipe_tower() ? 1 : 0;

            const jint values[] = {first_tower, first_extruders, first_generated_tower,
                                   next_tower, next_extruders, next_generated_tower};
            jintArray result = env->NewIntArray(6);
            if (result != nullptr) env->SetIntArrayRegion(result, 0, 6, values);
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Regression for Orca's final wipe-tower purge gate: SEMM still unloads at the
    // model top, while a short tower on a toolchanger must skip the final purge.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_short_1wipe_1tower_1final_1unload_1regression(
        JNIEnv* env, jclass)
    {
        const jint values[] = {
            Slic3r::should_generate_final_wipe_tower_purge(false, false) ? 1 : 0,
            Slic3r::should_generate_final_wipe_tower_purge(false, true) ? 1 : 0,
            Slic3r::should_generate_final_wipe_tower_purge(true, false) ? 1 : 0,
            Slic3r::should_generate_final_wipe_tower_purge(true, true) ? 1 : 0,
        };
        jintArray result = env->NewIntArray(4);
        if (result != nullptr) env->SetIntArrayRegion(result, 0, 4, values);
        return result;
    }

    // Exercises Orca's by-layer/as-object-list ordering with copies split into PrintObjects by
    // alternating rotations. The returned indices are the actual production G-code instance order.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_by_1layer_1copy_1order_1regression(
        JNIEnv* env, jclass, jlong model_ptr, jstring config_path)
    {
        try {
            auto* model_ref = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(model_ptr));
            if (model_ref == nullptr || config_path == nullptr || model_ref->model.objects.size() != 1)
                throw std::invalid_argument("By-layer copy-order regression expects one model object");
            ModelObject* object = model_ref->model.objects.front();
            if (object == nullptr || object->instances.size() < 2)
                throw std::invalid_argument("By-layer copy-order regression needs at least two instances");

            const char* path = env->GetStringUTFChars(config_path, nullptr);
            if (path == nullptr) return nullptr;
            DynamicPrintConfig config;
            config.load(path, ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(config_path, path);
            config.normalize_fdm();
            config.option<ConfigOptionEnum<PrintSequence>>("print_sequence")->value = PrintSequence::ByLayer;
            config.option<ConfigOptionEnum<PrintOrder>>("print_order")->value = PrintOrder::AsObjectList;

            for (size_t index = 0; index < object->instances.size(); ++index) {
                ModelInstance* instance = object->instances[index];
                if (instance == nullptr)
                    throw std::runtime_error("By-layer copy-order regression found a null instance");
                instance->set_offset(Vec3d(10. + 12. * double(index % 5), 10. + 12. * double(index / 5), instance->get_offset().z()));
                instance->set_rotation(Vec3d(0., 0., index % 2 == 0 ? 0. : PI / 2.));
            }

            Print print;
            print.is_BBL_printer() = false;
            print.apply(model_ref->model, config);
            const StringObjectException error = print.validate();
            if (!error.string.empty())
                throw std::runtime_error("By-layer copy-order regression fixture is invalid: " + error.string);

            const auto ordered = sort_object_instances_by_model_order(print);
            const ModelObject* print_object = print.model().objects.front();
            if (ordered.size() != print_object->instances.size())
                throw std::runtime_error("By-layer copy-order regression lost a model instance");
            std::vector<jint> values;
            values.reserve(ordered.size() + 1);
            values.emplace_back(static_cast<jint>(ordered.size()));
            for (const PrintInstance* instance : ordered) {
                auto found = std::find(print_object->instances.begin(), print_object->instances.end(), instance->model_instance);
                if (found == print_object->instances.end())
                    throw std::runtime_error("By-layer copy-order regression returned an unknown instance");
                values.emplace_back(static_cast<jint>(std::distance(print_object->instances.begin(), found)));
            }
            jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
            if (result != nullptr) env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Exercises sequential/by-object validation with copies split into PrintObjects by alternating rotations.
    // The returned indices are the actual production instance order used by the sequential G-code path.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_by_1object_1copy_1order_1regression(
        JNIEnv* env, jclass, jlong model_ptr, jstring config_path)
    {
        try {
            auto* model_ref = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(model_ptr));
            if (model_ref == nullptr || config_path == nullptr || model_ref->model.objects.size() != 1)
                throw std::invalid_argument("By-object copy-order regression expects one model object");
            ModelObject* object = model_ref->model.objects.front();
            if (object == nullptr || object->instances.size() < 2)
                throw std::invalid_argument("By-object copy-order regression needs at least two instances");

            const char* path = env->GetStringUTFChars(config_path, nullptr);
            if (path == nullptr) return nullptr;
            DynamicPrintConfig config;
            config.load(path, ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(config_path, path);
            config.normalize_fdm();
            config.option<ConfigOptionEnum<PrintSequence>>("print_sequence")->value = PrintSequence::ByObject;
            config.option<ConfigOptionFloat>("extruder_clearance_radius")->value = 0.;

            for (size_t index = 0; index < object->instances.size(); ++index) {
                ModelInstance* instance = object->instances[index];
                if (instance == nullptr)
                    throw std::runtime_error("By-object copy-order regression found a null instance");
                instance->set_offset(Vec3d(10. + 15. * double(index), 10., instance->get_offset().z()));
                instance->set_rotation(Vec3d(0., 0., index % 2 == 0 ? 0. : PI / 2.));
            }

            Print print;
            print.is_BBL_printer() = false;
            print.apply(model_ref->model, config);
            const StringObjectException error = print.validate();
            if (!error.string.empty())
                throw std::runtime_error("By-object copy-order regression fixture is invalid: " + error.string);

            const auto ordered = sort_object_instances_by_model_order(print);
            const ModelObject* print_object = print.model().objects.front();
            if (ordered.size() != print_object->instances.size())
                throw std::runtime_error("By-object copy-order regression lost a model instance");
            std::vector<jint> values;
            values.reserve(ordered.size() + 1);
            values.emplace_back(static_cast<jint>(ordered.size()));
            for (const PrintInstance* instance : ordered) {
                auto found = std::find(print_object->instances.begin(), print_object->instances.end(), instance->model_instance);
                if (found == print_object->instances.end())
                    throw std::runtime_error("By-object copy-order regression returned an unknown instance");
                values.emplace_back(static_cast<jint>(std::distance(print_object->instances.begin(), found)));
            }
            jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
            if (result != nullptr) env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Exercises Orca's height-range Z-shrinkage behavior and cached-region invalidation on
    // one reused Print. The synthetic range mirrors Orca's 10-15 mm thin/dense fixture.
    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_z_1shrinkage_1height_1range_1regression(
        JNIEnv* env, jclass, jlong model_ptr, jstring config_path, jdouble first_percent, jdouble next_percent)
    {
        try {
            auto* model_ref = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(model_ptr));
            if (model_ref == nullptr || config_path == nullptr || model_ref->model.objects.empty())
                throw std::invalid_argument("Height-range regression received an empty model or config path");

            const char* path = env->GetStringUTFChars(config_path, nullptr);
            if (path == nullptr) return nullptr;
            DynamicPrintConfig base_config;
            base_config.load(path, ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(config_path, path);
            base_config.set_key_value("layer_height", new ConfigOptionFloat(0.2));
            base_config.set_key_value("initial_layer_print_height", new ConfigOptionFloat(0.2));
            base_config.set_key_value("precise_z_height", new ConfigOptionBool(false));
            base_config.set_key_value("raft_layers", new ConfigOptionInt(0));
            base_config.set_key_value("xy_contour_compensation", new ConfigOptionFloat(0.));
            base_config.set_key_value("sparse_infill_density", new ConfigOptionPercent(15.));
            base_config.normalize_fdm();

            ModelObject& object = *model_ref->model.objects.front();
            DynamicPrintConfig range_config;
            range_config.set_key_value("layer_height", new ConfigOptionFloat(0.1));
            range_config.set_key_value("sparse_infill_density", new ConfigOptionPercent(50.));
            object.layer_config_ranges.clear();
            object.layer_config_ranges[{10., 15.}].assign_config(std::move(range_config));

            Print print;
            print.is_BBL_printer() = false;
            const auto inspect = [&](double percent) {
                DynamicPrintConfig config = base_config;
                auto* shrinkage = dynamic_cast<ConfigOptionPercents*>(config.option("filament_shrinkage_compensation_z", false));
                if (shrinkage == nullptr)
                    throw std::runtime_error("Slice config has no Z shrinkage compensation vector");
                if (shrinkage->values.empty()) shrinkage->values.push_back(percent);
                else std::fill(shrinkage->values.begin(), shrinkage->values.end(), percent);
                print.apply(model_ref->model, std::move(config));
                print.process();

                const PrintObject& print_object = *print.objects().front();
                size_t mismatched = 0;
                double first_dense_z = -1.;
                for (const Layer* layer : print_object.layers()) {
                    const bool thin = layer->height < 0.15;
                    const bool dense = std::any_of(layer->regions().begin(), layer->regions().end(), [](const LayerRegion* region) {
                        return !region->slices.empty() && region->region().config().sparse_infill_density.value > 40.;
                    });
                    if (thin != dense) {
                        ++mismatched;
                        __android_log_print(ANDROID_LOG_INFO, "ZShrinkTest",
                            "mismatch z=%g slice_z=%g height=%g thin=%d dense=%d regions=%zu shrink=%g",
                            layer->print_z, layer->slice_z, layer->height, thin ? 1 : 0, dense ? 1 : 0,
                            layer->regions().size(), percent);
                    }
                    if (dense && first_dense_z < 0.) first_dense_z = layer->slice_z;
                }
                return std::array<double, 3>{static_cast<double>(mismatched), first_dense_z,
                    print_object.layers().empty() ? 0. : print_object.layers().back()->print_z};
            };

            const auto first = inspect(first_percent);
            const auto next = inspect(next_percent);
            const std::array<jdouble, 6> values{first[0], first[1], first[2], next[0], next[1], next[2]};
            jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(values.size()));
            if (result != nullptr) env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1layer_1range_1test_1config(
        JNIEnv* env, jclass, jlong model_ptr, jdouble min_z, jdouble max_z, jdouble layer_height, jdouble outer_wall_speed)
    {
        try {
            auto* model_ref = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(model_ptr));
            if (model_ref == nullptr || model_ref->model.objects.empty() || min_z < 0. || max_z <= min_z ||
                layer_height <= 0. || outer_wall_speed <= 0.)
                throw std::invalid_argument("Invalid model or layer-range test fixture");
            DynamicPrintConfig range_config;
            range_config.set_key_value("layer_height", new ConfigOptionFloat(layer_height));
            range_config.set_key_value("outer_wall_speed", new ConfigOptionFloat(outer_wall_speed));
            model_ref->model.objects.front()->layer_config_ranges[{min_z, max_z}].assign_config(std::move(range_config));
            return JNI_TRUE;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return JNI_FALSE;
        }
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_layer_1range_1cache_1isolation_1regression(
        JNIEnv* env, jclass, jlong model_ptr, jstring config_path)
    {
        try {
            auto* model_ref = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(model_ptr));
            if (model_ref == nullptr || model_ref->model.objects.size() != 2 || config_path == nullptr)
                throw std::invalid_argument("Layer-range cache regression requires two copied model objects and a config path");
            const char* path = env->GetStringUTFChars(config_path, nullptr);
            if (path == nullptr) return nullptr;
            DynamicPrintConfig config;
            config.load(path, ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(config_path, path);

            Print print;
            print.apply(model_ref->model, std::move(config));
            print.process();
            int shared_objects = 0;
            for (const PrintObject* object : print.objects())
                if (object->get_shared_object() != nullptr) ++shared_objects;
            const std::array<jint, 2> values{static_cast<jint>(print.objects().size()), shared_objects};
            jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
            if (result != nullptr) env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_center_1offset_1slice_1cache_1regression(
        JNIEnv* env, jclass, jstring config_path)
    {
        try {
            if (config_path == nullptr)
                throw std::invalid_argument("Center-offset cache regression requires a slice config path");
            const char* path = env->GetStringUTFChars(config_path, nullptr);
            if (path == nullptr) return nullptr;
            DynamicPrintConfig config;
            config.load(path, ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(config_path, path);

            const std::vector<Vec3f> vertices{{0, 0, 0}, {30, 0, 0}, {0, 10, 0}, {0, 0, 10}, {30, 0, 10}, {0, 10, 10}};
            const std::vector<Vec3i32> faces{{0, 2, 1}, {3, 4, 5}, {0, 1, 4}, {0, 4, 3}, {1, 2, 5}, {1, 5, 4}, {2, 0, 3}, {2, 3, 5}};
            Model model;
            ModelObject* object_a = model.add_object();
            object_a->add_volume(TriangleMesh(vertices, faces));
            object_a->config.set_key_value("extruder", new ConfigOptionInt(1));
            ModelInstance* first = object_a->add_instance();
            const Vec3d origin = first->get_offset();
            ModelObject* object_b = model.add_object(*object_a);
            ModelInstance* turned = object_b->instances.front();
            turned->set_offset(origin + Vec3d(60., 0., 0.));
            turned->set_rotation(Vec3d(0., 0., PI / 3.));
            ModelInstance* straight = object_b->add_instance();
            straight->set_offset(origin + Vec3d(120., 0., 0.));

            Print print;
            print.apply(model, std::move(config));
            print.process();
            const PrintObject* print_a = nullptr;
            const PrintObject* print_straight = nullptr;
            for (const PrintObject* object : print.objects())
                if (object->model_object()->id() == object_a->id()) print_a = object;
            if (print_a == nullptr)
                throw std::runtime_error("Could not find the reference PrintObject");
            for (const PrintObject* object : print.objects())
                if (object->model_object()->id() == object_b->id() &&
                    object->trafo().matrix() == print_a->trafo().matrix()) print_straight = object;
            if (print_straight == nullptr)
                throw std::runtime_error("Could not find the matching-transform copied PrintObject");

            const bool offsets_differ = print_a->center_offset() != print_straight->center_offset();
            const bool any_shared = print_a->get_shared_object() != nullptr || print_straight->get_shared_object() != nullptr;
            const std::array<jint, 3> values{1, offsets_differ ? 1 : 0, any_shared ? 1 : 0};
            jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
            if (result != nullptr) env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_sequential_1raft_1top_1clearance_1regression(JNIEnv* env, jclass) {
        try {
            auto validate_raft = [](int raft_layers) {
                Model model;
                for (float x : {40.f, 150.f}) {
                    ModelObject* object = model.add_object();
                    TriangleMesh mesh = make_cube(20.f, 20.f, 24.8f);
                    mesh.translate(x, 80.f, 0.f);
                    object->add_volume(std::move(mesh));
                    object->add_instance();
                }
                DynamicPrintConfig config = DynamicPrintConfig::full_print_config();
                config.set_deserialize_strict({
                    {"print_sequence", "by object"},
                    {"extruder_clearance_height_to_rod", 25},
                    {"extruder_clearance_height_to_lid", 200},
                    {"extruder_clearance_radius", 5},
                    {"skirt_loops", 0},
                    {"raft_layers", raft_layers},
                    {"layer_change_gcode", "G92 E0\n"},
                });
                Print print;
                print.apply(model, std::move(config));
                if (raft_layers > 0 && print.objects().front()->slicing_parameters().object_print_z_min + 24.8 <= 25.)
                    throw std::runtime_error("Raft fixture did not raise the model above the configured rod height");
                return print.validate().string.find("too tall") != std::string::npos;
            };
            const bool raft_rejected = validate_raft(3);
            const bool no_raft_rejected = validate_raft(0);
            const std::array<jint, 2> values{raft_rejected ? 1 : 0, no_raft_rejected ? 1 : 0};
            jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
            if (result != nullptr) env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_sequential_1raft_1short_1clearance_1regression(JNIEnv* env, jclass) {
        try {
            auto validate_raft = [](int raft_layers) {
                Model model;
                for (float x : {80.f, 110.f}) {
                    ModelObject* object = model.add_object();
                    TriangleMesh mesh = make_cube(20.f, 20.f, 2.f);
                    mesh.translate(x, 80.f, 0.f);
                    object->add_volume(std::move(mesh));
                    object->add_instance();
                }
                DynamicPrintConfig config = DynamicPrintConfig::full_print_config();
                config.set_deserialize_strict({
                    {"print_sequence", "by object"},
                    {"nozzle_height", 2.5},
                    {"extruder_clearance_radius", 40},
                    {"skirt_loops", 1},
                    {"skirt_type", "perobject"},
                    {"raft_layers", raft_layers},
                    {"layer_change_gcode", "G92 E0\n"},
                });
                Print print;
                print.apply(model, std::move(config));
                const PrintObject* object = print.objects().front();
                if (raft_layers > 0 && object->slicing_parameters().object_print_z_max <= 2.5)
                    throw std::runtime_error("Raft fixture did not raise the model above nozzle height");
                const bool is_short = print.is_all_objects_are_short();
                const std::string error = print.validate().string;
                return std::pair<bool, bool>{is_short, error.find("too close to others") != std::string::npos};
            };
            const auto without_raft = validate_raft(0);
            const auto with_raft = validate_raft(3);
            const std::array<jint, 4> values{
                without_raft.first ? 1 : 0,
                without_raft.second ? 1 : 0,
                with_raft.first ? 1 : 0,
                with_raft.second ? 1 : 0,
            };
            jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
            if (result != nullptr) env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_painted_1bridge_1anchor_1regression(JNIEnv* env, jclass) {
        try {
            Model model;
            ModelObject* object = model.add_object();
            object->name = "painted_bridge_anchor_fixture";

            TriangleMesh pillars = make_cube(10., 20., 6.);
            TriangleMesh far_pillar = make_cube(10., 20., 6.);
            far_pillar.translate(20.f, 0.f, 0.f);
            pillars.merge(far_pillar);
            object->add_volume(std::move(pillars))->config.set("extruder", 1);
            TriangleMesh deck = make_cube(10., 20., 1.);
            deck.translate(10.f, 0.f, 5.f);
            object->add_volume(std::move(deck))->config.set("extruder", 2);
            object->add_instance();
            object->ensure_on_bed();

            DynamicPrintConfig config = DynamicPrintConfig::full_print_config();
            config.set_num_filaments(2);
            config.set_deserialize_strict({
                {"nozzle_diameter", "0.4"},
                {"filament_diameter", "1.75,1.75"},
                {"filament_colour", "#FF0000;#00FF00"},
                {"flush_volumes_matrix", "0,280,280,0"},
                {"layer_height", 0.2},
                {"initial_layer_print_height", 0.2},
                {"wall_generator", "classic"},
                {"wall_loops", 2},
                {"sparse_infill_density", "15%"},
                {"bottom_shell_layers", 3},
                {"outer_wall_line_width", 0.42},
                {"inner_wall_line_width", 0.45},
                {"detect_overhang_wall", true},
                {"enable_support", false},
                {"enable_prime_tower", false},
                {"skirt_loops", 0},
                {"brim_type", "no_brim"},
            });

            Print print;
            print.apply(model, std::move(config));
            print.set_status_silent();
            print.process();

            const Layer* deck_layer = nullptr;
            for (const Layer* layer : print.objects().front()->layers())
                if (std::abs(layer->print_z - 5.2) < EPSILON)
                    deck_layer = layer;
            if (deck_layer == nullptr || deck_layer->lower_layer == nullptr)
                throw std::runtime_error("Painted bridge fixture did not generate its deck layer");

            const LayerRegion* deck_region = nullptr;
            const LayerRegion* pillar_region = nullptr;
            for (const LayerRegion* region : deck_layer->regions())
                if (!region->slices.empty()) {
                    const double width = unscale<double>(get_extents(region->slices.surfaces).size().x());
                    if (width < 15.) deck_region = region;
                    else pillar_region = region;
                }
            if (deck_region == nullptr || pillar_region == nullptr)
                throw std::runtime_error("Painted bridge fixture did not retain separate filament regions");

            const ExPolygons anchors = intersection_ex(deck_region->slices.surfaces, deck_layer->lower_layer->lslices);
            int bridge_paths = 0;
            for (const ExtrusionEntity* entity : deck_region->fills.flatten().entities)
                if (entity->role() == erBridgeInfill) ++bridge_paths;
            const std::array<jint, 5> values{
                static_cast<jint>(anchors.size()),
                bridge_paths,
                deck_region->region().config().outer_wall_filament_id.value != pillar_region->region().config().outer_wall_filament_id.value ? 1 : 0,
                static_cast<jint>(deck_layer->regions().size()),
                static_cast<jint>(deck_layer->lower_layer->lslices.size()),
            };
            jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
            if (result != nullptr) env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcode_1extruder_164_1area_1mask_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        GCodeProcessor processor;
        processor.initialize_result_moves();
        for (const Vec3f& position : {Vec3f(50.f, 50.f, 0.2f), Vec3f(60.f, 50.f, 0.2f), Vec3f(60.f, 60.f, 0.2f)}) {
            GCodeProcessorResult::MoveVertex move;
            move.type = EMoveType::Extrude;
            move.extrusion_role = erPerimeter;
            move.extruder_id = 63; // zero-based T64
            move.position = position;
            move.object_label_id = 7;
            move.print_z = 0.2f;
            processor.result().moves.emplace_back(move);
        }

        const Pointfs bed{{0., 0.}, {200., 0.}, {200., 200.}, {0., 200.}};
        const Pointfs blocked_area{{45., 45.}, {65., 45.}, {65., 65.}, {45., 65.}};
        std::vector<Polygons> unprintable_areas(64);
        unprintable_areas[63].emplace_back(Polygon::new_scale(blocked_area));
        std::vector<double> printable_heights(64, 200.);
        std::vector<int> filament_map(64);
        for (size_t i = 0; i < filament_map.size(); ++i)
            filament_map[i] = static_cast<int>(i + 1);
        std::vector<std::set<int>> unprintable_filament_types(64);

        const bool valid = processor.check_multi_extruder_gcode_valid(
            64, bed, 200., {}, unprintable_areas, printable_heights, filament_map, unprintable_filament_types);
        const GCodeProcessorResult& result = processor.get_result();
        const uint64_t mask = result.limit_filament_maps.size() < 64 ? 0 : result.limit_filament_maps[63];
        const bool error_is_for_extruder_64 = result.gcode_check_result.print_area_error_infos.count(63) == 1;
        const std::array<jint, 4> values{
            valid ? 0 : 1,
            (mask & (uint64_t(1) << 63)) != 0 ? 1 : 0,
            (mask & ~(uint64_t(1) << 63)) == 0 ? 1 : 0,
            error_is_for_extruder_64 ? 1 : 0,
        };
        jintArray output = env->NewIntArray(static_cast<jsize>(values.size()));
        if (output != nullptr)
            env->SetIntArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
        return output;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_00024Companion_gcode_1extruder_164_1area_1mask_1regression(JNIEnv* env, jobject) {
        return Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcode_1extruder_164_1area_1mask_1regression(env, nullptr);
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1spiral_1vase_1override_1test_1config(
        JNIEnv* env, jclass, jlong model_ptr)
    {
        try {
            auto* model_ref = reinterpret_cast<ModelRef*>(static_cast<intptr_t>(model_ptr));
            if (model_ref == nullptr || model_ref->model.objects.empty())
                throw std::invalid_argument("Spiral-vase warning fixture requires a model object");
            DynamicPrintConfig range_config;
            // Height-range configs need their own layer height; omitting it reaches a
            // null option during Print::validate()'s layer-profile calculation.
            range_config.set_key_value("layer_height", new ConfigOptionFloat(0.2));
            range_config.set_key_value("sparse_infill_density", new ConfigOptionPercent(25.));
            model_ref->model.objects.front()->layer_config_ranges[{0.4, 1.2}].assign_config(std::move(range_config));
            return JNI_TRUE;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return JNI_FALSE;
        }
    }

    // Exercises the same inherited-printer extension and child-diff merge used by
    // PresetCollection's file, project-embedded, and cloud/user preset load paths.
    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_preset_1parent_1nozzle_1extension_1regression(JNIEnv* env, jclass) {
        try {
            DynamicPrintConfig parent = DynamicPrintConfig::full_print_config();
            parent.set_num_extruders(2);
            const auto* before = parent.option<ConfigOptionFloats>("nozzle_diameter");
            if (before == nullptr) throw std::runtime_error("Printer defaults have no nozzle_diameter vector");
            const jdouble before_count = static_cast<jdouble>(before->size());

            DynamicPrintConfig child = parent;
            child.set_num_extruders(4);
            child.set_key_value("nozzle_diameter", new ConfigOptionFloats({0.4, 0.5, 0.6, 0.8}));
            Preset::extend_parent_printer_extruders_to_child(parent, child);

            std::string extruder_id_name, extruder_variant_name;
            std::set<std::string>* key_set1 = nullptr;
            std::set<std::string>* key_set2 = nullptr;
            Preset::get_extruder_names_and_keysets(Preset::TYPE_PRINTER, extruder_id_name,
                extruder_variant_name, &key_set1, &key_set2);
            parent.update_diff_values_to_child_config(child, extruder_id_name, extruder_variant_name,
                *key_set1, *key_set2);

            const auto* merged = parent.option<ConfigOptionFloats>("nozzle_diameter");
            if (merged == nullptr || merged->size() != 4)
                throw std::runtime_error("Inherited printer config did not retain all four nozzle slots");
            const jdouble values[] = {before_count, static_cast<jdouble>(merged->size()),
                merged->get_at(0), merged->get_at(1), merged->get_at(2), merged->get_at(3)};
            jdoubleArray result = env->NewDoubleArray(6);
            if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 6, values);
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Exercises the production filament-count update after one filament has been
    // removed from a shared-nozzle multi-material printer. The shared-nozzle mode
    // must retain one slot, while switching to a tool changer must restore one
    // preset slot per nozzle.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shared_1nozzle_1filament_1count_1regression(
        JNIEnv* env, jclass)
    {
        try {
            PresetBundle bundle;
            Preset& printer = bundle.printers.get_edited_preset();
            printer.printer_technology_ref() = ptFFF;
            printer.config.set_num_extruders(2);
            printer.config.set_key_value("single_extruder_multi_material", new ConfigOptionBool(true));
            bundle.filament_presets = { bundle.filaments.get_edited_preset().name };

            bundle.update_filament_count();
            bundle.update_multi_material_filament_presets();
            const jint shared_nozzle_count = static_cast<jint>(bundle.filament_presets.size());

            printer.config.set_key_value("single_extruder_multi_material", new ConfigOptionBool(false));
            bundle.update_filament_count();
            bundle.update_multi_material_filament_presets();
            const jint tool_changer_count = static_cast<jint>(bundle.filament_presets.size());

            const jint values[] = { shared_nozzle_count, tool_changer_count };
            jintArray result = env->NewIntArray(2);
            if (result != nullptr) env->SetIntArrayRegion(result, 0, 2, values);
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Confirms a tray sync updates the physical color while retaining an inherited
    // compatible user preset and its custom extrusion-speed override.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_ams_1sync_1preserves_1inherited_1filament_1regression(
        JNIEnv* env, jclass)
    {
        try {
            PresetBundle bundle;
            const std::string system_name = "Nightly System PLA";
            const std::string custom_name = "Nightly Custom PLA";
            const std::string filament_id = "NIGHTLY_AMS_SYNC_PLA";

            DynamicPrintConfig system_config = DynamicPrintConfig::full_print_config();
            system_config.set_num_extruders(1);
            system_config.set_key_value("filament_type", new ConfigOptionStrings({"PLA"}));
            system_config.set_key_value("filament_settings_id", new ConfigOptionStrings({system_name}));
            system_config.set_key_value("filament_max_volumetric_speed", new ConfigOptionFloats({12.0}));
            Preset& system = bundle.filaments.load_preset("nightly-system-pla", system_name, std::move(system_config), false);
            system.is_system = true;
            system.is_compatible = true;
            system.filament_id = filament_id;

            DynamicPrintConfig custom_config = system.config;
            custom_config.set_key_value("inherits", new ConfigOptionString(system_name));
            custom_config.set_key_value("filament_settings_id", new ConfigOptionStrings({custom_name}));
            custom_config.set_key_value("filament_max_volumetric_speed", new ConfigOptionFloats({7.5}));
            Preset& custom = bundle.filaments.load_preset("nightly-custom-pla", custom_name, std::move(custom_config), false);
            custom.is_system = false;
            custom.is_compatible = true;
            custom.filament_id = filament_id;
            bundle.filament_presets = {custom_name};

            bundle.project_config = DynamicPrintConfig::full_print_config();
            bundle.project_config.set_num_extruders(1);
            bundle.project_config.set_key_value("filament_colour", new ConfigOptionStrings({"#111111"}));
            bundle.project_config.set_key_value("filament_colour_type", new ConfigOptionStrings({"1"}));
            bundle.project_config.set_key_value("filament_multi_colour", new ConfigOptionStrings({"#111111"}));
            bundle.project_config.set_key_value("filament_map", new ConfigOptionInts({1}));
            bundle.project_config.set_key_value("flush_multiplier", new ConfigOptionFloats({1.0}));
            bundle.project_config.set_key_value("flush_volumes_matrix", new ConfigOptionFloats({0.0}));

            DynamicPrintConfig tray;
            tray.set_key_value("filament_id", new ConfigOptionStrings({filament_id}));
            tray.set_key_value("filament_colour", new ConfigOptionStrings({"#123456"}));
            tray.set_key_value("filament_colour_type", new ConfigOptionStrings({"1"}));
            tray.set_key_value("filament_multi_colour", new ConfigOptionStrings({"#123456"}));
            tray.set_key_value("filament_type", new ConfigOptionStrings({"PLA"}));
            tray.set_key_value("ams_id", new ConfigOptionStrings({"0"}));
            tray.set_key_value("slot_id", new ConfigOptionStrings({"0"}));
            bundle.filament_ams_list.clear();
            bundle.filament_ams_list.emplace(0, std::move(tray));
            std::vector<std::pair<DynamicPrintConfig *, std::string>> unknowns;
            std::map<int, AMSMapInfo> maps;
            MergeFilamentInfo merge_info;
            bundle.sync_ams_list(unknowns, false, maps, false, merge_info);

            const std::string synced_name = bundle.filament_presets.empty() ? std::string{} : bundle.filament_presets.front();
            const Preset* synced = synced_name.empty() ? nullptr : bundle.filaments.find_preset(synced_name, false);
            const auto* speed = synced == nullptr ? nullptr : synced->config.option<ConfigOptionFloats>("filament_max_volumetric_speed");
            const auto* inherits = synced == nullptr ? nullptr : synced->config.option<ConfigOptionString>("inherits");
            const auto* color_option = bundle.project_config.option<ConfigOptionStrings>("filament_colour", false);
            const auto& colors = color_option == nullptr ? std::vector<std::string>{} : color_option->values;
            const jint values[] = {
                synced_name == custom_name ? 1 : 0,
                speed != nullptr && !speed->values.empty() ? static_cast<jint>(std::round(speed->values.front() * 10.0)) : -1,
                !colors.empty() && colors.front() == "#123456" ? 1 : 0,
                inherits != nullptr && inherits->value == system_name ? 1 : 0,
            };
            jintArray result = env->NewIntArray(4);
            if (result != nullptr) env->SetIntArrayRegion(result, 0, 4, values);
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Loads a four-tool project that stores only two filament slots through the
    // same PresetBundle project-config path used by imported 3MF projects.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_short_1project_1filament_1arrays_1regression(
        JNIEnv* env, jclass)
    {
        try {
            PresetBundle bundle;
            Preset& printer = bundle.printers.get_edited_preset();
            printer.printer_technology_ref() = ptFFF;
            printer.config.set_num_extruders(4);
            printer.config.set_key_value("single_extruder_multi_material", new ConfigOptionBool(false));
            printer.config.set_key_value("nozzle_diameter", new ConfigOptionFloats({0.4, 0.4, 0.4, 0.4}));

            DynamicPrintConfig project = DynamicPrintConfig::full_print_config();
            project.set_num_extruders(4);
            project.set_key_value("nozzle_diameter", new ConfigOptionFloats({0.4, 0.4, 0.4, 0.4}));
            project.set_key_value("single_extruder_multi_material", new ConfigOptionBool(false));
            project.set_key_value("filament_colour", new ConfigOptionStrings({"#FF0000", "#00FF00"}));
            project.set_key_value("filament_multi_colour", new ConfigOptionStrings({"#FF0000", "#00FF00"}));
            project.set_key_value("filament_colour_type", new ConfigOptionStrings({"1", "1"}));
            project.set_key_value("filament_map", new ConfigOptionInts({1, 1}));
            const std::string selected_filament = bundle.filaments.get_edited_preset().name;
            project.set_key_value("filament_settings_id", new ConfigOptionStrings({selected_filament, selected_filament}));

            bundle.load_config_model("short-filament-array-project.3mf", std::move(project));

            const auto& colors = bundle.project_config.option<ConfigOptionStrings>("filament_colour")->values;
            const auto& color_types = bundle.project_config.option<ConfigOptionStrings>("filament_colour_type")->values;
            const auto& multi_colors = bundle.project_config.option<ConfigOptionStrings>("filament_multi_colour")->values;
            const auto& filament_map = bundle.project_config.option<ConfigOptionInts>("filament_map")->values;
            const auto& flush_multiplier = bundle.project_config.option<ConfigOptionFloats>("flush_multiplier")->values;
            const auto& flush_matrix = bundle.project_config.option<ConfigOptionFloats>("flush_volumes_matrix")->values;
            const bool imported_colors_preserved = colors.size() >= 2 &&
                colors[0] == "#FF0000" && colors[1] == "#00FF00";

            Preset *active_filament = bundle.filaments.find_preset(bundle.filament_presets.front(), true);
            if (active_filament == nullptr)
                throw std::runtime_error("Imported filament preset is missing after short-array project load");
            const std::string test_filament_id = "NIGHTLY_ARRAY_REGRESSION";
            active_filament->filament_id = test_filament_id;
            active_filament->is_compatible = true;

            auto make_tray = [&test_filament_id](const std::string& color, const std::string& slot_id) {
                DynamicPrintConfig tray;
                tray.set_key_value("filament_id", new ConfigOptionStrings({test_filament_id}));
                tray.set_key_value("filament_colour", new ConfigOptionStrings({color}));
                tray.set_key_value("filament_colour_type", new ConfigOptionStrings({"1"}));
                tray.set_key_value("filament_multi_colour", new ConfigOptionStrings({color}));
                tray.set_key_value("filament_type", new ConfigOptionStrings({"PLA"}));
                tray.set_key_value("ams_id", new ConfigOptionStrings({"0"}));
                tray.set_key_value("slot_id", new ConfigOptionStrings({slot_id}));
                return tray;
            };
            bundle.filament_ams_list.clear();
            const std::vector<std::string> synchronized_colors = {"#123456", "#654321", "#00AABB", "#BB00AA"};
            for (size_t slot = 0; slot < synchronized_colors.size(); ++slot)
                bundle.filament_ams_list.emplace(static_cast<int>(slot), make_tray(synchronized_colors[slot], std::to_string(slot)));
            std::vector<std::pair<DynamicPrintConfig *, std::string>> unknowns;
            std::map<int, AMSMapInfo> maps;
            MergeFilamentInfo merge_info;
            const jint synced_count = static_cast<jint>(bundle.sync_ams_list(unknowns, false, maps, false, merge_info));

            const auto& synced_final_colors = bundle.project_config.option<ConfigOptionStrings>("filament_colour")->values;
            const jint values[] = {
                static_cast<jint>(bundle.filament_presets.size()),
                static_cast<jint>(colors.size()),
                static_cast<jint>(color_types.size()),
                static_cast<jint>(multi_colors.size()),
                static_cast<jint>(filament_map.size()),
                static_cast<jint>(flush_multiplier.size()),
                static_cast<jint>(flush_matrix.size()),
                imported_colors_preserved ? 1 : 0,
                synced_count,
                static_cast<jint>(synced_final_colors.size()),
                synced_final_colors.size() >= synchronized_colors.size() && synced_final_colors[0] == synchronized_colors[0] &&
                    synced_final_colors[1] == synchronized_colors[1] && synced_final_colors[2] == synchronized_colors[2] &&
                    synced_final_colors[3] == synchronized_colors[3] ? 1 : 0,
                static_cast<jint>(colors.size() * colors.size() * flush_multiplier.size())
            };
            jintArray result = env->NewIntArray(12);
            if (result != nullptr) env->SetIntArrayRegion(result, 0, 12, values);
            return result;
        } catch (const std::exception& error) {
            jclass runtime_error = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError");
            if (runtime_error != nullptr) env->ThrowNew(runtime_error, error.what());
            return nullptr;
        }
    }

    // Exercises the production short-chain path optimizer against a fixture where
    // nearest-neighbor chaining is measurably longer than the exact solution.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shortest_1path_1exact_1chain_1regression(JNIEnv* env, jclass) {
        const std::vector<int> encoded = Slic3r::shortest_path_exact_chain_regression();
        std::vector<jint> values(encoded.begin(), encoded.end());
        jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
        if (result != nullptr && !values.empty())
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
        return result;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shortest_1path_1long_1chain_1refinement_1regression(JNIEnv* env, jclass) {
        const std::vector<int> encoded = Slic3r::shortest_path_long_chain_refinement_regression();
        std::vector<jint> values(encoded.begin(), encoded.end());
        jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
        if (result != nullptr && !values.empty())
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
        return result;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1exclude_1area_1validation_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        Pointfs valid {{0., 0.}, {20., 0.}, {20., 20.}, {0., 20.}};
        Pointfs self_intersecting {{0., 0.}, {20., 20.}, {0., 20.}, {20., 0.}};
        Pointfs duplicate {{0., 0.}, {20., 0.}, {20., 20.}, {20., 0.}};
        Pointfs non_finite {{0., 0.}, {20., 0.}, {std::numeric_limits<double>::quiet_NaN(), 20.}};
        PrintConfig config;
        config.bed_exclude_area.values = valid;
        const jint valid_area_count = static_cast<jint>(get_bed_excluded_area(config).size());
        config.bed_exclude_area.values = self_intersecting;
        const jint checks[] = {
            is_valid_bed_exclude_area(valid) ? 1 : 0,
            is_valid_bed_exclude_area(self_intersecting) ? 1 : 0,
            is_valid_bed_exclude_area(duplicate) ? 1 : 0,
            is_valid_bed_exclude_area(non_finite) ? 1 : 0,
            valid_area_count,
            static_cast<jint>(get_bed_excluded_area(config).size()),
            static_cast<jint>(get_bed_excluded_regions(config).size())
        };
        jintArray result = env->NewIntArray(7);
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, 7, checks);
        return result;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1shared_1extruder_1area_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        const Pointfs bed {{0., 0.}, {200., 0.}, {200., 200.}, {0., 200.}};
        const Pointfs left {{0., 0.}, {120., 0.}, {120., 200.}, {0., 200.}};
        const Pointfs right_overlap {{80., 0.}, {200., 0.}, {200., 200.}, {80., 200.}};
        const Pointfs right_touch {{120., 0.}, {200., 0.}, {200., 200.}, {120., 200.}};
        const Pointfs right_gap {{130., 0.}, {200., 0.}, {200., 200.}, {130., 200.}};
        const double area_scale = double(scale_(1.)) * double(scale_(1.));
        const auto area_mm2 = [area_scale](const Points &points) {
            if (points.empty())
                return 0.;
            Polygon polygon;
            polygon.points = points;
            return polygon.area() / area_scale;
        };

        const Polygon overlap = get_shared_poly({left, right_overlap}, bed);
        const Polygon touch = get_shared_poly({left, right_touch}, bed);
        const Polygon separated = get_shared_poly({left, right_gap}, bed);
        const Polygon unbounded_nozzle = get_shared_poly({Pointfs{}, left}, bed);
        const Polygon unrestricted = get_shared_poly({}, bed);

        PrintConfig config;
        config.printable_area.values = bed;
        config.extruder_printable_area.values = {left, right_gap};
        const Points placement_fallback = get_bed_shape(config, true);
        DynamicPrintConfig dynamic_config;
        dynamic_config.set_key_value("printable_area", new ConfigOptionPoints(bed));
        dynamic_config.set_key_value("extruder_printable_area", new ConfigOptionPointsGroups({left, right_gap}));
        const Points dynamic_placement_fallback = get_bed_shape(dynamic_config, true);
        dynamic_config.set_key_value("extruder_printable_area", new ConfigOptionPointsGroups({left, right_overlap}));
        const Points dynamic_overlap = get_bed_shape(dynamic_config, true);
        PrintConfig unrestricted_config;
        unrestricted_config.printable_area.values = bed;
        const Points no_extruder_restrictions = get_bed_shape(unrestricted_config, true);

        const jint checks[] = {
            std::abs(overlap.area() / area_scale - 8000.) < 1e-4 ? 1 : 0,
            touch.empty() ? 1 : 0,
            separated.empty() ? 1 : 0,
            std::abs(unbounded_nozzle.area() / area_scale - 24000.) < 1e-4 ? 1 : 0,
            std::abs(unrestricted.area() / area_scale - 40000.) < 1e-4 ? 1 : 0,
            std::abs(area_mm2(placement_fallback) - 40000.) < 1e-4 ? 1 : 0,
            std::abs(area_mm2(dynamic_placement_fallback) - 40000.) < 1e-4 ? 1 : 0,
            std::abs(area_mm2(dynamic_overlap) - 8000.) < 1e-4 ? 1 : 0,
            std::abs(area_mm2(no_extruder_restrictions) - 40000.) < 1e-4 ? 1 : 0,
        };
        jintArray result = env->NewIntArray(9);
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, 9, checks);
        return result;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shortest_1path_1exact_1object_1tour_1regression(JNIEnv* env, jclass) {
        const std::vector<int> encoded = Slic3r::shortest_path_exact_object_tour_regression();
        std::vector<jint> values(encoded.begin(), encoded.end());
        jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
        if (result != nullptr && !values.empty())
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
        return result;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_joined_1infill_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        jint checks[4] = { 0, 0, 0, 0 };
        {
            Polylines paths {
                Polyline(Point::new_scale(0., 0.), Point::new_scale(10., 0.)),
                Polyline(Point::new_scale(0., 1.), Point::new_scale(10., 1.)),
                Polyline(Point::new_scale(0., 20.), Point::new_scale(10., 20.))
            };
            join_close_ends(paths, scale_(1.5), true);
            checks[0] = paths.size() == 2;
            auto joined = std::find_if(paths.begin(), paths.end(), [](const Polyline& path) { return path.size() == 4; });
            if (joined != paths.end())
                checks[0] = checks[0] && std::abs(unscale<double>(joined->length()) - 21.) < 1e-6 &&
                    joined->first_point() != joined->last_point();
        }
        {
            Polylines paths {
                Polyline(Point::new_scale(0., 0.), Point::new_scale(10., 0.)),
                Polyline(Point::new_scale(0., 1.), Point::new_scale(10., 1.)),
                Polyline(Point::new_scale(10., -1.), Point::new_scale(0., -1.))
            };
            join_close_ends(paths, scale_(1.5), true);
            checks[1] = paths.size() == 1 && paths.front().size() == 6 &&
                std::abs(unscale<double>(paths.front().length()) - 32.) < 1e-6;
        }
        {
            Polylines paths {
                Polyline(Point::new_scale(0., 0.), Point::new_scale(10., 0.)),
                Polyline(Point::new_scale(0., 1.), Point::new_scale(10., 1.)),
                Polyline(Point::new_scale(-5., 0.5), Point::new_scale(5., 0.5))
            };
            join_close_ends(paths, scale_(1.5), true);
            auto joined = std::find_if(paths.begin(), paths.end(), [](const Polyline& path) { return path.size() == 4; });
            checks[2] = paths.size() == 2 && joined != paths.end() &&
                joined->first_point().x() == 0 && joined->last_point().x() == 0;
        }
        {
            const Point a = Point::new_scale(0., 0.), b = Point::new_scale(10., 0.);
            const Point c = Point::new_scale(10., 1.), d = Point::new_scale(0., 1.);
            Polylines fixedDirection { Polyline(a, b), Polyline(d, c) };
            join_close_ends(fixedDirection, scale_(1.5), false);
            Polylines orderedJoin { Polyline(c, d), Polyline(a, b) };
            join_close_ends(orderedJoin, scale_(1.5), false);
            if (orderedJoin.size() == 1) {
                const Points& points = orderedJoin.front().points;
                const auto position = [&points](const Point& point) {
                    return std::find(points.begin(), points.end(), point) - points.begin();
                };
                checks[3] = fixedDirection.size() == 2 && position(c) < position(d) && position(a) < position(b);
            }
        }
        jintArray result = env->NewIntArray(4);
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, 4, checks);
        return result;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_other_1layers_1print_1sequence_1regression_1probe(JNIEnv* env, jclass) {
        jint checks[5] = { 0, 0, 0, 0, 0 };
        checks[0] = get_other_layers_print_sequence(0, { 1, 2, 3 }).empty();
        checks[1] = get_other_layers_print_sequence(-1, { 1, 2, 3 }).empty();
        checks[2] = get_other_layers_print_sequence(2, { 1 }).empty();
        checks[3] = get_other_layers_print_sequence(2, { 1, 2, 3, 4, 5 }).empty() &&
            get_other_layers_print_sequence(1, { 10, 20 }).empty();

        const auto valid = get_other_layers_print_sequence(2, { 10, 20, 0, 2, 30, 40, 1, 3 });
        checks[4] = valid.size() == 2 && valid[0].first == std::make_pair(10, 20) &&
            valid[0].second == std::vector<int>({ 0, 2 }) && valid[1].first == std::make_pair(30, 40) &&
            valid[1].second == std::vector<int>({ 1, 3 });

        jintArray result = env->NewIntArray(5);
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, 5, checks);
        return result;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_tree_1support_1grouped_1union_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        ExPolygons polygons;
        const auto append_circle = [&polygons](double x, double y, double radius) {
            Polygon circle;
            for (int k = 0; k < 32; ++k) {
                const double angle = 2. * PI * double(k) / 32.;
                circle.points.emplace_back(Point::new_scale(x + radius * std::cos(angle), y + radius * std::sin(angle)));
            }
            polygons.emplace_back(std::move(circle));
        };
        // Dense overlapping clusters approximate branch circles from a busy tree-support layer.
        for (int y = 0; y < 16; ++y)
            for (int x = 0; x < 32; ++x)
                append_circle(double(x) * 0.8, double(y) * 0.8, 0.55);
        for (int y = 0; y < 8; ++y)
            for (int x = 0; x < 8; ++x)
                append_circle(60. + double(x) * 0.8, double(y) * 0.8, 0.55);

        const ExPolygons single_union = union_ex(polygons);
        const ExPolygons grouped_union = union_overlapping_ex(polygons);
        const double symmetric_difference = area(diff_ex(single_union, grouped_union)) + area(diff_ex(grouped_union, single_union));
        const bool topology_matches = single_union.size() == grouped_union.size();
        const bool rounding_is_sub_micron = symmetric_difference < sqr(scaled<double>(0.001));

        ExPolygons small(polygons.begin(), polygons.begin() + 16);
        const bool small_input_keeps_exact_path = union_overlapping_ex(small) == union_ex(small);
        const bool empty_input_is_safe = union_overlapping_ex({}).empty();
        const jint checks[] = {
            topology_matches ? 1 : 0,
            rounding_is_sub_micron ? 1 : 0,
            small_input_keeps_exact_path ? 1 : 0,
            empty_input_is_safe ? 1 : 0,
        };
        jintArray result = env->NewIntArray(4);
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, 4, checks);
        return result;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_texture_1displacement_1bake_1regression(JNIEnv* env, jclass, jbyteArray png) {
        using namespace Slic3r;
        if (png == nullptr || env->GetArrayLength(png) <= 0)
            return nullptr;
        std::vector<unsigned char> image(size_t(env->GetArrayLength(png)));
        env->GetByteArrayRegion(png, 0, jsize(image.size()), reinterpret_cast<jbyte*>(image.data()));
        if (env->ExceptionCheck()) return nullptr;

        constexpr int cells = 8;
        indexed_triangle_set its;
        its.vertices.reserve((cells + 1) * (cells + 1));
        for (int y = 0; y <= cells; ++y)
            for (int x = 0; x <= cells; ++x)
                its.vertices.emplace_back(float(x), float(y), 0.f);
        const auto vertex = [cells](int x, int y) { return y * (cells + 1) + x; };
        its.indices.reserve(cells * cells * 2);
        for (int y = 0; y < cells; ++y)
            for (int x = 0; x < cells; ++x) {
                its.indices.emplace_back(vertex(x, y), vertex(x + 1, y), vertex(x + 1, y + 1));
                its.indices.emplace_back(vertex(x, y), vertex(x + 1, y + 1), vertex(x, y + 1));
            }

        TriangleMesh mesh(its);
        TriangleSelector selector(mesh);
        for (int i = 0; i < int(mesh.its.indices.size()); ++i)
            selector.set_facet(i, EnforcerBlockerType::ENFORCER);

        TextureDisplacementLayer layer;
        layer.slot = 0;
        layer.name = "native-regression";
        layer.image_data = std::make_shared<std::vector<unsigned char>>(std::move(image));
        layer.depth_mm = 0.8f;
        layer.tiling_scale = float(cells);
        TextureDisplacementOptions options;
        options.pipeline_v2 = false; // Keep this fixture topology fixed and make displacement directly measurable.
        TextureDisplacementFacetsData masks{};
        masks[0] = selector.serialize();
        const std::vector<TextureDisplacementLayer> layers{ layer };
        const indexed_triangle_set baked = build_texture_displacement(mesh.its, layers, masks, options);
        float max_z = -std::numeric_limits<float>::infinity();
        for (const Vec3f &v : baked.vertices) max_z = std::max(max_z, v.z());

        const indexed_triangle_set cancelled = build_texture_displacement(
            mesh.its, layers, masks, options, [](int) { return false; });
        const jdouble values[] = {
            double(mesh.its.vertices.size()),
            double(baked.vertices.size()),
            double(max_z),
            cancelled.vertices.empty() && cancelled.indices.empty() ? 1.0 : 0.0,
        };
        jdoubleArray result = env->NewDoubleArray(4);
        if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 4, values);
        return result;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_texture_1displacement_1roundtrip_1attach(
        JNIEnv* env, jclass, jlong ptr, jbyteArray png) {
        ModelRef* ref = reinterpret_cast<ModelRef*>(intptr_t(ptr));
        if (ref == nullptr || png == nullptr || ref->model.objects.empty() ||
            ref->model.objects.front()->volumes.empty() || env->GetArrayLength(png) <= 0)
            return JNI_FALSE;
        std::vector<unsigned char> image(size_t(env->GetArrayLength(png)));
        env->GetByteArrayRegion(png, 0, jsize(image.size()), reinterpret_cast<jbyte*>(image.data()));
        if (env->ExceptionCheck()) return JNI_FALSE;

        ModelObject* object = ref->model.objects.front();
        ModelVolume* volume = object->volumes.front();
        auto paint_end_facets = [](ModelVolume& target, int slot) {
            TriangleSelector selector(target.mesh());
            if (target.mesh().its.indices.empty()) return false;
            selector.set_facet(0, EnforcerBlockerType::ENFORCER);
            selector.set_facet(int(target.mesh().its.indices.size()) - 1, EnforcerBlockerType::ENFORCER);
            return target.texture_displacement_facet(slot).set(selector);
        };
        if (!paint_end_facets(*volume, 2)) return JNI_FALSE;

        TextureDisplacementLayer layer;
        layer.slot = 2;
        layer.name = "3MF texture persistence regression";
        layer.depth_mm = 0.73f;
        layer.image_data = std::make_shared<std::vector<unsigned char>>(std::move(image));
        TextureDisplacementLayer second_layer = layer;
        second_layer.slot = 6;
        second_layer.name = "Second texture persistence regression";
        second_layer.depth_mm = 0.41f;
        volume->texture_displacement_layers = { std::move(layer), std::move(second_layer) };
        volume->texture_displacement_options.active_slot = 2;
        volume->texture_displacement_options.panel_mode = 1;

        ModelVolume* second_volume = object->add_volume(
            TriangleMesh(volume->mesh()), ModelVolumeType::MODEL_PART, false);
        if (second_volume == nullptr || !paint_end_facets(*second_volume, 2)) return JNI_FALSE;
        TextureDisplacementLayer shared_slot_layer;
        shared_slot_layer.slot = 2;
        shared_slot_layer.name = "Same slot, separate volume";
        shared_slot_layer.depth_mm = 1.07f;
        shared_slot_layer.image_data = volume->texture_displacement_layers.front().image_data;
        second_volume->texture_displacement_layers = { std::move(shared_slot_layer) };
        second_volume->texture_displacement_options.active_slot = 2;
        second_volume->texture_displacement_options.panel_mode = 0;
        if (object->add_volume(*volume) == nullptr) return JNI_FALSE;
        return JNI_TRUE;
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_texture_1displacement_1roundtrip_1inspect(
        JNIEnv* env, jclass, jlong ptr, jint volumeIndex) {
        ModelRef* ref = reinterpret_cast<ModelRef*>(intptr_t(ptr));
        jint values[9] = {0, 0, -1, -1, 0, 0, 0, 0, 0};
        if (ref != nullptr) values[6] = jint(ref->model.objects.size());
        if (ref != nullptr && !ref->model.objects.empty() && volumeIndex >= 0 &&
            volumeIndex < jint(ref->model.objects.front()->volumes.size())) {
            values[7] = jint(ref->model.objects.front()->volumes.size());
            values[8] = jint(ref->model.objects.front()->instances.size());
            const ModelVolume* volume = ref->model.objects.front()->volumes[size_t(volumeIndex)];
            values[0] = jint(volume->texture_displacement_layers.size());
            values[2] = volume->texture_displacement_options.active_slot;
            values[3] = volume->texture_displacement_options.panel_mode;
            values[4] = jint(volume->texture_displacement_facet(2).get_data().triangles_to_split.size());
            if (!volume->texture_displacement_layers.empty()) {
                const TextureDisplacementLayer& layer = volume->texture_displacement_layers.front();
                values[1] = layer.slot;
                values[5] = layer.image_data ? jint(layer.image_data->size()) : 0;
            }
        }
        jintArray result = env->NewIntArray(9);
        if (result != nullptr) env->SetIntArrayRegion(result, 0, 9, values);
        return result;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_texture_1displacement_1roundtrip_1clear(
        JNIEnv*, jclass, jlong ptr, jint volumeIndex) {
        ModelRef* ref = reinterpret_cast<ModelRef*>(intptr_t(ptr));
        if (ref == nullptr || ref->model.objects.empty() || volumeIndex < 0 ||
            volumeIndex >= jint(ref->model.objects.front()->volumes.size()))
            return JNI_FALSE;
        ModelVolume& volume = *ref->model.objects.front()->volumes[size_t(volumeIndex)];
        volume.texture_displacement_layers.clear();
        for (int slot = 0; slot < int(TEXTURE_DISPLACEMENT_MAX_LAYERS); ++slot)
            volume.texture_displacement_facet(slot).reset();
        volume.texture_displacement_options = TextureDisplacementOptions{};
        return JNI_TRUE;
    }

    // Verifies the solid-surface Spiral Inset paths retain the top-surface Arachne geometry.
    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_spiral_1inset_1solid_1regression(JNIEnv* env, jclass) {
        using namespace Slic3r;
        jint checks[3] = { 0, 0, 0 };
        try {
            Polygon contour;
            for (int i = 0; i < 96; ++i) {
                const double angle = 2.0 * M_PI * i / 96.0;
                contour.points.emplace_back(Point::new_scale(12.0 * std::cos(angle), 9.0 * std::sin(angle)));
            }
            contour.make_counter_clockwise();
            Surface top(stTop, ExPolygon(contour));
            Surface solid(stInternalSolid, ExPolygon(contour));
            PrintConfig print_config;
            print_config.nozzle_diameter.values = { 0.4 };
            PrintObjectConfig object_config;
            FillParams params;
            params.density = 1.0f;
            params.dont_adjust = false;
            params.use_arachne = true;
            params.layer_height = 0.2;
            params.extrusion_role = erTopSolidInfill;

            std::unique_ptr<Fill> top_fill(Fill::new_from_type(ipSpiralInset));
            top_fill->spacing = 0.45;
            top_fill->overlap = 0.225;
            top_fill->print_config = &print_config;
            top_fill->print_object_config = &object_config;
            ThickPolylines top_paths = top_fill->fill_surface_arachne(&top, params);
            params.extrusion_role = erSolidInfill;
            std::unique_ptr<Fill> solid_fill(Fill::new_from_type(ipSpiralInset));
            solid_fill->spacing = 0.45;
            solid_fill->overlap = 0.225;
            solid_fill->print_config = &print_config;
            solid_fill->print_object_config = &object_config;
            ThickPolylines solid_paths = solid_fill->fill_surface_arachne(&solid, params);

            checks[0] = !top_paths.empty();
            checks[1] = !solid_paths.empty();
            checks[2] = top_paths.size() == solid_paths.size();
            if (checks[2]) {
                for (size_t i = 0; i < top_paths.size(); ++i) {
                    if (top_paths[i].points.size() != solid_paths[i].points.size()) {
                        checks[2] = 0;
                        break;
                    }
                    for (size_t j = 0; j < top_paths[i].points.size(); ++j) {
                    if (top_paths[i].points[j] != solid_paths[i].points[j]) {
                            checks[2] = 0;
                            break;
                        }
                    }
                if (checks[2]) {
                    for (size_t i = 0; i < top_paths.size(); ++i)
                        if (top_paths[i].width != solid_paths[i].width) {
                            checks[2] = 0;
                            break;
                        }
                }
                    if (!checks[2])
                        break;
                }
            }
        } catch (...) {
            checks[0] = checks[1] = checks[2] = 0;
        }
        jintArray result = env->NewIntArray(3);
        if (result != nullptr)
            env->SetIntArrayRegion(result, 0, 3, checks);
        return result;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1has_1paint(JNIEnv* env, jclass, jlong modelPtr, jint objIdx) {
        ModelRef* m = (ModelRef*) (intptr_t) modelPtr;
        if (objIdx < 0 || objIdx >= (int) m->model.objects.size()) return false;
        ModelObject* obj = m->model.objects[objIdx];
        for (const Slic3r::ModelVolume* v : obj->volumes) {
            if (v->is_model_part() && !v->mmu_segmentation_facets.get_data().triangles_to_split.empty())
                return true;
        }
        return false;
    }

    // Highest filament index (1-based) that has any painted facets across the object's model-part
    // volumes, or 0 if none. Lets the renderer build an overlay for every painted filament even
    // when the user's configured palette has fewer slots than the model was painted with.
    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1paint_1max_1filament(JNIEnv* env, jclass, jlong modelPtr, jint objIdx) {
        ModelRef* m = (ModelRef*) (intptr_t) modelPtr;
        if (objIdx < 0 || objIdx >= (int) m->model.objects.size()) return 0;
        ModelObject* obj = m->model.objects[objIdx];
        int maxF = 0;
        for (const Slic3r::ModelVolume* v : obj->volumes) {
            if (!v->is_model_part()) continue;
            const auto& data = v->mmu_segmentation_facets.get_data();
            if (data.triangles_to_split.empty()) continue;
            TriangleMesh vmesh = v->mesh();
            Slic3r::TriangleSelector sel(vmesh);
            sel.deserialize(data, true);
            for (int f = (int) Slic3r::EnforcerBlockerType::ExtruderMax; f > maxF; --f) {
                if (!sel.get_facets(static_cast<Slic3r::EnforcerBlockerType>(f)).indices.empty()) {
                    maxF = f;
                    break;
                }
            }
        }
        return (jint) maxF;
    }

    // Build a GLModel from the facets painted with the given filament (mesh-local coords), for
    // rendering a colored overlay over the base object. Returns the number of triangles.
    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1from_1paint(
        JNIEnv* env, jclass, jlong glPtr, jlong sessionPtr, jint filamentIdx, jint baseFilamentPreview) {
        GLModelRef* g = (GLModelRef*) (intptr_t) glPtr;
        PaintSessionRef* s = (PaintSessionRef*) (intptr_t) sessionPtr;
        const indexed_triangle_set its = accumulate_live_paint_facets(s, paint_state(filamentIdx), baseFilamentPreview);
        g->model.reset();
        if (!its.indices.empty())
            g->model.init_from(its);
        return (jint) its.indices.size();
    }

    // Build the live overlay palette in one pass. In particular, deserialize each
    // untouched 3MF volume's facet assignments once per preview refresh, rather than
    // once for every visible filament slot. Emit directly into GL geometry so we do not
    // retain a second complete set of indexed meshes while the preview is displayed.
    static void append_live_paint_overlays(
        PaintSessionRef* session,
        int base_filament_preview,
        std::vector<GLModel::Geometry>& geometries) {
        if (session == nullptr || session->model == nullptr || geometries.empty() ||
            session->objIdx < 0 || session->objIdx >= (int)session->model->model.objects.size())
            return;

        ModelObject* obj = session->model->model.objects[session->objIdx];
        if (obj == nullptr || obj->volumes.empty()) return;

        auto append_transformed = [](const indexed_triangle_set& part,
                                     const Transform3d& transform,
                                     GLModel::Geometry& geometry) {
            if (part.indices.empty()) return;
            geometry.reserve_more_vertices(part.indices.size() * 3);
            geometry.reserve_more_indices(part.indices.size() * 3);
            for (const stl_triangle_vertex_indices& triangle : part.indices) {
                stl_vertex vertices[3];
                for (int corner = 0; corner < 3; ++corner)
                    vertices[corner] = (transform * part.vertices[triangle[corner]].cast<double>()).cast<float>();
                const Vec3f normal = face_normal_normalized(vertices);
                const unsigned int base = (unsigned int)geometry.vertices_count();
                for (const stl_vertex& vertex : vertices) geometry.add_vertex(vertex, normal);
                geometry.add_triangle(base, base + 1, base + 2);
            }
        };

        const int filament_count = (int)geometries.size();
        const std::vector<const ModelInstance*> instances = obj->instances.empty()
            ? std::vector<const ModelInstance*>{ nullptr }
            : std::vector<const ModelInstance*>(obj->instances.begin(), obj->instances.end());
        for (const ModelInstance* instance : instances) {
            const Transform3d instance_matrix = instance != nullptr
                ? instance->get_matrix() : Transform3d::Identity();
            for (ModelVolume* volume : obj->volumes) {
                if (volume == nullptr || !volume->is_model_part()) continue;
                const Transform3d transform = instance_matrix * volume->get_matrix();
                const int volume_idx = (int)std::distance(obj->volumes.begin(), std::find(obj->volumes.begin(), obj->volumes.end(), volume));
                PaintVolumeRef* editable = paint_volume(session, volume_idx);
                const bool base_volume = volume_idx == 0;
                const int base_filament = base_volume && base_filament_preview > 0
                    ? base_filament_preview : std::max(1, volume->extruder_id());
                const bool show_base = session->mode == 0;

                if (editable != nullptr) {
                    for (int filament = 1; filament <= filament_count; ++filament) {
                        GLModel::Geometry& geometry = geometries[filament - 1];
                        append_transformed(editable->selector->get_facets(paint_state(filament)), transform, geometry);
                        if (show_base && base_filament == filament)
                            append_transformed(editable->selector->get_facets(Slic3r::EnforcerBlockerType::NONE), transform, geometry);
                    }
                    continue;
                }

                const auto& data = facets_for_mode(volume, session->mode).get_data();
                if (data.triangles_to_split.empty()) {
                    if (base_filament > 0 && base_filament <= filament_count)
                        append_transformed(volume->mesh().its, transform, geometries[base_filament - 1]);
                    continue;
                }

                Slic3r::TriangleSelector selector(volume->mesh());
                selector.deserialize(data, true);
                for (int filament = 1; filament <= filament_count; ++filament) {
                    GLModel::Geometry& geometry = geometries[filament - 1];
                    append_transformed(selector.get_facets(paint_state(filament)), transform, geometry);
                    if (show_base && base_filament == filament)
                        append_transformed(selector.get_facets(Slic3r::EnforcerBlockerType::NONE), transform, geometry);
                }
            }
        }
    }

    JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_paint_1build_1live_1overlays(
        JNIEnv* env, jclass, jlong sessionPtr, jlongArray glModelPtrs, jint baseFilamentPreview) {
        const jsize count = glModelPtrs != nullptr ? env->GetArrayLength(glModelPtrs) : 0;
        jintArray result = env->NewIntArray(count);
        if (result == nullptr || count == 0) return result;

        std::vector<jlong> pointers((size_t)count);
        env->GetLongArrayRegion(glModelPtrs, 0, count, pointers.data());
        if (env->ExceptionCheck()) return result;

        std::vector<GLModel::Geometry> geometries((size_t)count);
        append_live_paint_overlays((PaintSessionRef*)(intptr_t)sessionPtr,
                                   baseFilamentPreview, geometries);
        std::vector<jint> triangleCounts((size_t)count, 0);
        for (jsize i = 0; i < count; ++i) {
            if (pointers[(size_t)i] == 0) continue;
            GLModelRef* target = (GLModelRef*)(intptr_t)pointers[(size_t)i];
            if (!geometries[(size_t)i].is_empty()) {
                target->model.reset();
                target->model.init_from(std::move(geometries[(size_t)i]));
                triangleCounts[(size_t)i] = (jint)(target->model.indices_count() / 3);
            }
        }
        env->SetIntArrayRegion(result, 0, count, triangleCounts.data());
        return result;
    }
}

extern "C" JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_cut_1preserves_1part_1filaments_1regression(JNIEnv* env, jclass) {
    Model model;
    ModelObject* object = model.add_object();
    auto add_part = [](ModelObject* target, int extruder, const std::string& material_id, double x_offset) {
        TriangleMesh mesh = make_cube(12.5, 25., 25.);
        mesh.translate(static_cast<float>(x_offset), -12.5f, -12.5f);
        ModelVolume* volume = target->add_volume(std::move(mesh));
        volume->config.set("extruder", extruder);
        volume->set_material_id(material_id);
    };
    add_part(object, 1, "mat_blue", -6.25);
    add_part(object, 2, "mat_white", 6.25);
    object->add_instance();
    Cut cut(object, 0, Transform3d::Identity(), ModelObjectCutAttribute::KeepUpper | ModelObjectCutAttribute::KeepLower);
    Cut::Groove groove;
    groove.depth = 3.f;
    groove.width = 2.f;
    groove.flaps_angle = 0.5235988f;
    groove.angle = 0.0872665f;
    groove.depth_tolerance = 0.1f;
    groove.width_tolerance = 0.1f;
    const ModelObjectPtrs& cut_objects = cut.perform_with_groove(groove, Transform3d::Identity(), 1, 0.f, 0.f);

    auto inspect = [](const ModelObject* result) {
        std::array<int, 3> state{{0, 0, 0}};
        std::unordered_set<std::string> materials;
        for (const ModelVolume* volume : result->volumes) {
            if (!volume->is_model_part() || volume->is_cut_connector()) continue;
            ++state[0];
            const int extruder = volume->extruder_id();
            if (extruder > 0 && extruder < 31) state[1] |= 1 << (extruder - 1);
            materials.insert(volume->material_id());
        }
        state[2] = static_cast<int>(materials.size());
        return state;
    };

    Model same_model;
    ModelObject* same_object = same_model.add_object();
    same_object->config.set("extruder", 1);
    add_part(same_object, 1, "", 0.0);
    ModelVolume* explicit_default = same_object->add_volume(make_cube(12.5, 25., 25.));
    explicit_default->config.set("extruder", 1);
    same_object->add_instance();
    Cut same_cut(same_object, 0, Transform3d::Identity(), ModelObjectCutAttribute::KeepUpper | ModelObjectCutAttribute::KeepLower);
    const ModelObjectPtrs& same_results = same_cut.perform_by_contour(same_object,
        {{true, false}, {true, false}}, 0);

    Model unit_source;
    ModelObject* unit_object = unit_source.add_object();
    ModelVolume* unit_volume = unit_object->add_volume(make_cube(12.5, 25., 25.));
    unit_volume->set_material_id("unit_material");
    unit_object->add_instance();
    ModelObjectPtrs converted_objects;
    unit_object->convert_units(converted_objects, ConversionType::CONV_TO_INCH, {});
    Model converted_owner;
    converted_owner.objects = std::move(converted_objects);
    const bool unit_conversion_preserved_material = !converted_owner.objects.empty() &&
        !converted_owner.objects.front()->volumes.empty() &&
        converted_owner.objects.front()->volumes.front()->material_id() == "unit_material";

    std::array<jint, 9> state{{static_cast<jint>(cut_objects.size()), 0, 0, 0, 0, 0, 0, 0,
        unit_conversion_preserved_material ? 1 : 0}};
    if (cut_objects.size() == 2) {
        const auto upper = inspect(cut_objects[0]);
        const auto lower = inspect(cut_objects[1]);
        state[1] = upper[0]; state[2] = upper[1]; state[3] = upper[2];
        state[4] = lower[0]; state[5] = lower[1]; state[6] = lower[2];
    }
    if (same_results.size() == 1) state[7] = inspect(same_results.front())[0];
    jintArray result = env->NewIntArray(static_cast<jsize>(state.size()));
    if (result != nullptr) env->SetIntArrayRegion(result, 0, static_cast<jsize>(state.size()), state.data());
    return result;
}

extern "C" JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_clipper2_1scanbeam_1sort_1regression(JNIEnv* env, jclass) {
    using namespace Clipper2Lib;
    Paths64 separated;
    constexpr int rectangle_count = 128;
    separated.reserve(rectangle_count);
    for (int i = 0; i < rectangle_count; ++i) {
        const int64_t x = static_cast<int64_t>(i) * 10;
        separated.push_back(Path64{
            Point64(x, int64_t{0}), Point64(x + 4, int64_t{0}),
            Point64(x + 4, int64_t{4}), Point64(x, int64_t{4}),
        });
    }
    const Paths64 separated_union = Union(separated, FillRule::NonZero);
    double separated_area = 0.0;
    for (const Path64& path : separated_union) separated_area += std::abs(Area(path));

    const Paths64 subject{Path64{
        Point64(int64_t{0}, int64_t{0}), Point64(int64_t{10}, int64_t{0}),
        Point64(int64_t{10}, int64_t{10}), Point64(int64_t{0}, int64_t{10}),
    }};
    const Paths64 clip{Path64{
        Point64(int64_t{5}, int64_t{-3}), Point64(int64_t{13}, int64_t{5}),
        Point64(int64_t{5}, int64_t{13}), Point64(int64_t{-3}, int64_t{5}),
    }};
    const Paths64 intersection = Intersect(subject, clip, FillRule::NonZero);
    double intersection_area = 0.0;
    for (const Path64& path : intersection) intersection_area += std::abs(Area(path));

    const std::array<jdouble, 4> values{
        static_cast<jdouble>(separated_union.size()), separated_area,
        static_cast<jdouble>(intersection.size()), intersection_area,
    };
    jdoubleArray output = env->NewDoubleArray(static_cast<jsize>(values.size()));
    if (output != nullptr)
        env->SetDoubleArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
    return output;
}

extern "C" JNIEXPORT jintArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_00024Companion_cut_1preserves_1part_1filaments_1regression(JNIEnv* env, jobject) {
    return Java_ru_ytkab0bp_slicebeam_slic3r_Native_cut_1preserves_1part_1filaments_1regression(env, nullptr);
}
