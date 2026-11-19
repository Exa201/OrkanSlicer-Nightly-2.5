---
name: slicer-3d-renderer
description: Use when modifying OrkanSlicer bed rendering, camera controls, picking, selection, transform gizmos, face placement, paint overlays, GL resources, or renderer performance.
---

# Slicer 3D renderer

## Grounded project boundary

`view/GLView.kt` is the existing `GLSurfaceView` and input boundary. `render/GLRenderer.kt` owns camera, visible models, selection, overlays, and bed drawing; geometry/model operations cross into `slic3r/Model.kt` and JNI. `slic3r/GLModel.kt`, shader classes, `render/Camera.kt`, and `slic3r/Bed3D.kt` are supporting renderer code. Compose hosts the existing surface through `ui/workspace/RendererAdapter.kt` and `WorkspaceShell.kt`.

## Trigger

Use for camera/touch regressions, model picking, rotate/scale/move, face-down placement, renderer overlays, multibed visibility, GL lifecycle, or frame/memory issues in the viewport.

## Procedure

1. Identify the gesture/visible result and trace input from `GLView` through `GLRenderer` to the actual model/native mutation. Distinguish preview state from committed model state.
2. Preserve GL thread ownership and surface lifecycle. Do not call GL/native render operations from Compose recomposition or arbitrary worker threads; use the existing queue/dispatch boundary.
3. For geometry edits, capture selection/model/plate identity, apply the transform once, refresh dependent bounds and render data, and verify rollback/undo when supported.
4. For flat-face placement, inspect the source mesh and face-normal/area scoring logic; compare candidate facets against a fixture and the upstream implementation. Do not substitute a heuristic without evidence.
5. Verify camera orbit, pan, and zoom before/during/after the operation; test selected and unselected models, multiple plates, and a high-triangle model where feasible.
6. Measure frame performance with the same device/build/flow before and after. Separate GL frame timing from native slice time.

## Tests and success criteria

- Start with `render/*Test`, `LargeModelPickTest`, `ModelEditRequestValidationTest`, `ModelPlacementTransactionTest`, `BuildPlateProjectionFitTest`, and `ModelEditingNativeDeviceTest`.
- Run focused JVM tests and the relevant `sliceTest` instrumentation on a verified device/emulator.
- Success means the expected object/face is selected or transformed, coordinates and bounds agree with the visible result, camera controls remain responsive, no GL lifecycle error/crash occurs, and the existing renderer remains the sole viewport owner.
