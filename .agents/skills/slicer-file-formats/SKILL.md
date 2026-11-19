---
name: slicer-file-formats
description: Use when changing model/project import or export, Storage Access Framework permissions, STL/3MF/AMF/OBJ/STEP/STP/SVG handling, thumbnails, or G-code packaging and validation.
---

# Slicer file formats

## Grounded project boundary

User file selection is routed through Android SAF in `MainActivity.kt`, `SetupActivity.kt`, and `components/bed_menu/FileMenu.kt`; saving/export also uses document providers and existing `FileProvider` paths. Kotlin preflight and STEP options include `StlImportPreflight.kt`, `StepImportOptions.kt`, `OCCTLoader.kt`, and `ThreeMfProjectSettingsReader.kt`. Native formats live under `app/src/main/jni/libslic3r/Format` (including STL, 3MF, AMF, OBJ, STEP and SVG) and are connected in `app/CMakeLists.txt`/`beam_native.cpp`. G-code validation and printer-specific packaging live in existing utilities.

## Trigger

Use for file selection/grants, parsing, import/export fidelity, format compatibility, project metadata, thumbnails, or printer-specific G-code containers.

## Procedure

1. Identify the supported format and actual Kotlin/native entry point. Inspect the format reader/writer and caller before changing extension filters or parser assumptions.
2. Keep URI grants and stream access within SAF APIs. Do not convert arbitrary document URIs into filesystem paths; copy to app-owned storage only where the current native API requires a path, and clean it up with the owning lifecycle.
3. Treat archives as untrusted: preserve existing entry-count/size/path validation, reject traversal/duplicate entries where policy requires, and avoid unbounded decompression or allocation.
4. Use minimal valid and malformed fixtures. For 3MF, verify plates, object/volume structure, painted filament assignments, and metadata round-trip. For STEP, test import options and independent compound bodies. For G-code, validate terminal layer/height and tool mapping before upload.
5. Check compatibility against existing files and tests before altering serialized structure or metadata. Preserve backward reading where possible.

## Tests and success criteria

- Inspect `StepImportOptionsTest`, `OCCTLoaderTest`, `ThreeMfProjectSettingsReader` coverage, `QidiGCode3mfPackagerTest`, `GCodeIntegrityValidatorTest`, import policy tests, and corresponding device fixtures.
- Run focused JVM tests; run native import/export/slice round-trip instrumentation for changed engine paths.
- Success means the intended file opens/saves with model geometry, plate count, colors/tools, metadata and thumbnails preserved as specified; malformed input fails safely; document-provider access works without broad storage permission.
