# Changelog

## 0.8.457 — Einheitliche, wischbare Workspace-Menüs — 2026-11-14

- Alle unteren Menüs im Hochformat verwenden vier Spalten und bis zu zwei Reihen. Der separate Pfeilbutton entfällt; zusätzliche Aktionen werden direkt per horizontalem Wischen erreicht.
- Modell-, Farb- und Bearbeitungsaktionen nutzen stärker unterscheidbare Symbole.
- Der Changelog wird pro App-Build einmal angezeigt. APK-Builds prüfen, dass CHANGELOG.md und update.json zur versionName passen.

## 0.8.456 — OrcaSlicer 2.5.0 Übersetzungen — 2026-11-14

- Refresh der 23 Orca-Sprachkataloge aus OrcaSlicer 2.5.0; fehlende Übersetzungen ergänzt und bestehende Nightly-Übersetzungen erhalten.
- Sprachkatalog-Instrumentierung auf Android 16/API 36 bestanden; Debug- und SliceTest-Unit jeweils 779/779.

## 0.8.451 — Nightly plate arrangement feedback race — 2026-11-06

- Invalidate any in-flight Fill Bed result as soon as plate-wide Arrange starts. A stale copy-capacity failure can no longer appear over a successful Arrange or restore its earlier layout.
- Keep the visible Arrange action and Fill Bed action separate; current capacity errors remain specific to Fill Bed.
- API-36 Compose instrumentation: four same-size objects moved outside the plate were returned to the active plate by the actual Arrange button; object count stayed four. Debug and Slice unit suites each passed 761/761.

## 0.8.447 — Nightly plate arrangement action verification — 2026-11-06

- Verify the plate-wide Arrange action by its stable action ID in the real Compose menu. The regression test loads four models, moves all of them fully outside the plate, invokes Arrange, and checks that all four return inside the active plate without copies being added.
- Keep Arrange distinct from Fill Bed and selected-group placement, each with its own success or failure feedback.

## 0.8.440 — Nightly VK navigation icon consistency — 2026-11-05

- Replace the six global navigation icons and additional file, edit, and slice action icons with semantically matched VK Icons vectors.
- Keep the 3×2 portrait workspace grid, large-text sizing, and complete wrapped action labels from 0.8.439.

## 0.8.439 — Nightly workspace menu and icon pass — 2026-11-05

- Workspace action grids now use three columns across phone widths, with a two-row portrait layout when more than three actions are present. Panel and tile heights are aligned for readable labels, including larger system text.
- Replace mismatched workspace navigation and common model-action symbols with the corresponding VK Icons vectors. Include VKCOM's MIT license and attribution.
- Focused workspace grid and panel geometry regression tests updated.

## 0.8.438 — Fix updater page crash on HONOR — 2026-11-05

- Remove a nested unbounded vertical scroller from the setup updater/about page. The short version label now participates in the page's existing scroll container, avoiding the `infinity maximum height constraints` Compose crash reported on HONOR Android 16.

## 0.8.437 — Nightly updater recovery and menu readability — 2026-11-05

- Check for Nightly 2.5 updates once per app process at startup while retaining active or completed DownloadManager transfers.
- Keep transfer status and progress visible in the About page after the Android notification is dismissed; provide a persistent install action for a completed APK and retry guidance for a failed transfer.
- Give phone-portrait menu actions wider two-column pages and more room for labels. Landscape action lists now show complete labels in a scrollable column and refresh their enabled state on rotation.
- Preserve the original SliceBeam icon resources, app-specific download location, and existing design tokens. No orientation-only permission changes were introduced.
- Android/Kotlin compile passed; no test suite or physical-device test was run in this verification pass.

## 0.8.436 — Nightly UI polish — 2026-11-05

- Preserve Android dp-based adaptive layout sizing and existing SliceBeam navigation icons, padding, and corner tokens.
- Improve selected-accent readability while preserving the configured hue; label the build-plate counter explicitly and keep its dimensions stable across display densities.
- Update STL batch and model-load progress in a single tagged snackbar instead of replacing it with a succession of animated cards.
- Debug APK is ARM64-only and debug-signed. No physical-device check is claimed for this build.

## 0.8.427 — Nightly 2.5 experimental — 2026-11-02

- Add per-object Orca `seam_position` and `raft_layers` process overrides with schema-backed seam choices, confirmation, and 3MF persistence. Native slicing tests verify object isolation and changed G-code paths/support output.
- Validate Catan archive arrangement on API 36: all 109 STL models are inside bed bounds across 60 plates; 139 same-plate pairs are collision-free. This arrangement test is separate from the feature batch.
- Batch 8 reached 10/10. Debug and Slice JVM suites each passed 738/738; the focused per-object settings Compose instrumentation passed 10/10; native seam and raft slice tests each passed 1/1; the Settlers archive arrangement instrumentation passed 1/1. No physical printer test is claimed.
- This is a selective Orca 2.5 Android backport, not a full upstream engine replacement. APK is debug-signed and ARM64-only.

## 0.8.422 — Nightly 2.5 experimental — 2026-11-01

- Orca match-mode filament assignment now uses filament preset IDs as a tie-breaker after rounded color distance, preferring the same subtype when colors are effectively equal while keeping color similarity primary.
- The Android machine-slot reader retains each slot's `filament_id`; a native regression test covers subtype preference, color priority, and distance tie-breaks.
- Nightly Batch 3 reached 10/10 scoped, focused changes. Debug and Slice JVM suites passed 705/705 each; the targeted native Android test passed 1/1 on API 35 x86_64 with ARM64 translation. No physical printer, physical GPU, or Xiaomi verification is claimed.
- This is a selective Orca Android backport, not a full OrcaSlicer 2.5 core replacement. Nightly remains isolated under `com.exa201.orkanslicer.nightly25`.
- Debug APK `OrkanSlicerNightly25_30dbeeb070.apk`, 239,825,573 bytes, SHA-256 `2262AFF34CF2A1C9C9CD846349CDE963F17B5E261FA780EB7539F54996A71096`; manifest confirms app ID, label, and version 0.8.422/code 457. Full debug build succeeded for code commit `30dbeeb070`.
- API-35 emulator installation and launch succeeded with no Crash/ANR/OOM markers in the captured logcat window. Xiaomi 2211133G was attached but rejected ADB install with `INSTALL_FAILED_USER_RESTRICTED`; physical Xiaomi verification remains outstanding.
- [Nightly 2.5 prerelease v0.8.422](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.422-nightly.1).

## 0.8.421 — Nightly 2.5 experimental — 2026-10-31

- Orca filename templates now list all distinct filament types used by the sliced plate, in tool order. OpenEYE Peacock V2 profiles include the model, optional plate name, used materials, and estimated print time in the recommended G-code filename.
- Added native and Android instrumentation coverage for a two-material multipart slice (`PLA_PETG`) and deduplication when both tools use the same material.
- Batch 2 reached 10/10 scoped, focused changes. Full Debug/Slice JVM suites passed 705/705 each; the new native ARM64 slice test passed on API 35 and API 36 emulators. No physical printer/GPU behavior is claimed.
- Selective OrcaSlicer 2.5 Android backports only; this does not replace or advance the complete desktop engine baseline.
- Debug APK `OrkanSlicerNightly25_669c46d161.apk`, 239,774,624 bytes, SHA-256 `07713748A33C1A99C49432B5DDF331EBEEE1CAD3543D2D101603DF8F8A1710EB`; Nightly package identity/version verified and app installed/launched on API 35. The artifact is debug-signed and ARM64-only.
- [Nightly 2.5 prerelease v0.8.421](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.421-nightly.1).

## 0.8.418 — Nightly 2.5 experimental — 2026-10-30

- Reduce Qidi Q2 send preparation by removing a redundant full decompression/hash pass over the newly generated G-code 3MF. The package writer's streaming MD5 is still checked against the stored MD5; required package entries and every PNG preview are validated. The public deep verifier continues to hash the complete G-code.
- Keep the captured model image both embedded in `Metadata/plate_1.gcode` and in the Qidi 3MF preview entries. Added a regression proving deep verification rejects a toolpath changed after packaging.
- ARM64 app/instrumentation APK build succeeded. Debug JVM and Slice JVM suites each passed 687/687; focused Qidi package tests passed 7/7. No ADB device was connected; Qidi upload timing and physical printer display remain unverified.
- APK `OrkanSlicer_37d9ed1d64.apk` (239,656,516 bytes), SHA-256 `E7C97CD46DC421F2C1BBA1BE3D215C8CBB8078763339EAC7A6A15D0ADCAC54F7`; [Nightly 2.5 v0.8.418](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.418-nightly.1).

## 0.8.417 — Nightly 2.5 experimental — 2026-10-30

- Port OrcaSlicer `b7ecf1b29`: validate multi-nozzle extrusion paths against the actual inset printable polygon instead of its bounding box, avoiding false out-of-range warnings while retaining rejection of paths that cross the true forbidden area. Ignore malformed/empty filament-map entries safely.
- Full ARM64 native app and instrumentation APK build succeeded. Debug JVM and Slice JVM suites each passed 686/686 (0 failures, 0 errors, 0 skipped). Qidi package tests passed, including embedded G-code thumbnail, matching PNG assets, and package checksum checks.
- The instrumentation APK compiled, but no ADB device was connected; physical Qidi upload/print behavior and the new printable-area scenario remain unverified. Selective Orca 2.5 backport only; engine lock not advanced.
- APK: [OrkanSlicer Nightly 2.5 v0.8.417](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.417-nightly.1), SHA-256 `6E27FD90458F84A2A24ED9304463C187EFC79B8568F2431CC0A8EFD84D6663B1`.

## 0.8.416 — Nightly 2.5 experimental — 2026-10-30

- Port OrcaSlicer `29e189957`: when a compacted/no-sparse wipe tower is below the current model layer, restore the model Z before a travel-capable custom macro or physical extruder change. The placeholder is wired into both Android wipe-tower generators.
- Added an instrumentation regression for a tall multi-color object, compacted tower, and custom toolchange macro. The test compiles but could not run because no ADB device is connected.
- Full ARM64 native/app/instrumentation build succeeded. Debug JVM and Slice JVM suites each passed 686/686, with no failures, errors, or skips. Physical printer behavior remains unverified.
- APK: [OrkanSlicer Nightly 2.5 v0.8.416](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.416-nightly.1), SHA-256 `12D7EF80F583D89356657122A1C499CD891CDBE30A3E6A91DBB54F98E29FA967`.

## 0.8.415 — Nightly 2.5 experimental — 2026-10-30

- Port OrcaSlicer `a4a510087`: classify tree-support continuation using the next layer's XY collision-clearance region for build-plate-only supports. This avoids treating the area inside support clearance as a free path to the build plate.
- Full ARM64 native/app/instrumentation APK build succeeded. Debug JVM and Slice JVM suites each passed 686/686, with no failures, errors, or skips.
- The Android instrumentation APK compiled but no device was connected to run it. No support-heavy model or printer behavior is claimed; the Orca 2.5 engine remains a selective port.

## 0.8.414 — Nightly 2.5 experimental — 2026-10-30

- Port OrcaSlicer `7125a546b`: stop derived automatic filament-map values from invalidating G-code again when the unchanged configuration is reapplied after slicing. This targets the extra-slice/repeated-processing path; the upstream Catch2 regression does not exist in the Android build system.
- Full ARM64 native/debug and instrumentation APK build succeeded. Debug JVM and Slice JVM suites each passed 686/686, with no failures, errors, or skips.
- APK is debug-signed and ARM64-only. No ADB device was connected, so Android instrumentation and real printer/G-code behavior were not run. The Orca 2.5 engine baseline remains selectively backported, not fully advanced.

## 0.8.413 — Nightly 2.5 experimental — 2026-10-30

- Backport OrcaSlicer's per-filament retraction travel threshold and Z-hop enforcement. The Android port indexes the active filament by its filament ID, retaining its existing native model while fixing single-nozzle multi-filament G-code settings.
- Expose `filament_retract_lift_enforce` in filament profile import and the Travel lift editor so imported/custom filament overrides are not silently lost.
- ARM64 native build and Debug/Slice JVM suites passed 686/686 each. A dual-filament Z-hop regression test was added and compiles in the instrumentation APK, but no device was connected to execute it. No physical printer behavior is claimed.

## 0.8.412 — Nightly 2.5 experimental — 2026-10-30

- Correct Qidi print filenames to use the initial active tool's material instead of always using filament T0.
- Debug and Slice JVM suites passed 686/686 each, including a regression test for all Qidi profile filename placeholders. ARM64 debug and instrumentation APK builds succeeded.
- Qidi upload and physical printer behavior have not been tested. This remains an experimental selective OrcaSlicer 2.5 backport.

## 0.8.411 — Nightly 2.5 experimental — 2026-10-30

- Preserve compatible per-slot user filament presets during printer/AMS synchronization when the reported tray has the same filament ID, including when empty trays shift list positions.
- Debug and Slice JVM suites passed 685/685 each. ARM64 debug APK and instrumentation APK compiled successfully. The thin-object instrumentation regression and filament sync behavior were not run on a device; slicing fixtures and printer/G-code checks remain pending.
- Selective OrcaSlicer 2.5 port only; the engine lock is not advanced and this debug APK is experimental.

## 0.8.410 — Nightly 2.5 experimental — 2026-10-30

- Port OrcaSlicer 2.5 empty-layer crash guards and widen filament/extruder masks to 64 bits.
- ARM64 debug APK and instrumentation APK build successfully. The thin-object regression test compiles but has not run on a device; no slicing fixture, physical printer, or G-code output was validated for this build.
- This selective backport is not the complete OrcaSlicer 2.5 core. The engine lock stays at the current Android baseline pending fixture and device validation.
- Debug-signed APK published as an experimental prerelease in the Nightly 2.5 repository.

## 0.8.409

- Reduce printer upload preparation overhead for Kobra 3 V2 and Qidi Q2; avoid redundant native mesh conversions while packing Qidi preview files.
- Publish the current app baseline before the isolated OrcaSlicer 2.5 Android-core update.
- The preceding code commit passed 33 focused printer-upload, G-code integrity, and Qidi packaging unit tests; this release rebuild changes the app version metadata only. Xiaomi and printer hardware were not connected.

## 0.8.404

- Keep the color-paint panel and its single filament palette active until the GL thread has finished committing or discarding the paint draft.
- Prevent the bed viewport and global filament shortcut from switching early during paint confirmation or cancellation.
- Verify delayed GL-commit transitions, live paint preview, cancel behavior, plate visibility, and paint undo on Android 16.

## 0.8.403

- Suppress the global filament shortcut whenever a paint panel is active, preventing a duplicate palette during panel transitions.
- Keep live brush color visible before confirmation and preserve the build-plate view when the paint dock resizes the renderer.
- Verify imported multicolor 3MF paint, single-palette layout, plate visibility, cancellation, and brush undo on Android 16. Xiaomi hardware was not connected for this verification.

## 0.8.401

- Add German labels and feedback for object placement, multipart joining, and standalone 3D text filament selection.
- Verify the German resource variants for placement, joining, Boolean union, and standalone text.

## 0.8.400

- Prevent the global filament shortcut from appearing beside a docked paint palette during tool transitions.
- Verify imported 3MF paint is visible while the canvas touch is still held, without committing the model or losing the build-plate view.

## 0.8.397

- Keep the live color-paint palette visible on short phone screens without scrolling; preserve at least 60% of the workspace viewport for the print bed.
- Verify live paint color before confirmation, exactly one visible filament palette, and build-plate retention on Android 16.

## 0.8.395

- Keep live paint feedback responsive on dense models by limiting full-mesh paint-overlay rebuilds during a stroke and flushing the final preview immediately on touch release.
- Retain the single paint palette and reserve most portrait workspace height for the live model and build plate.
- Verify live color before confirmation, final-stroke visibility, 3MF assembly overlays, and cancel/undo behavior on Android 16. Xiaomi hardware was not connected for this verification.

## 0.8.394

- Preserve every imported 3MF part color during live color-paint preview, including parts outside the volume being edited.
- Respect a newly selected base filament in the live overlay so the previous base color cannot cover the preview before confirmation.
- Verify immediate color preview, multi-volume assembly colors, one visible filament palette, and a retained build-plate viewport on Android 16.

## 0.8.393

- Show color-paint base-filament changes and brush strokes on the model before confirming; align live paint overlays with transformed volumes and instances.
- Keep the compact workspace filament button hidden while painting so only the paint palette is shown.
- Dock paint controls below the renderer and reserve a minimum visible build-plate area.
- Recover workspace navigation if a replaced or cancelled menu animation does not settle.
- Verify live color before confirmation, one paint palette, a retained build-plate viewport, and paint cancellation on Android 16.

## 0.8.389

- Make fill-bed packing transactional: failed arrangement trials now roll back speculative copies and restore original object positions before another candidate is tested.
- Verify that the largest accepted packing is committed with every object's filament, survives 3MF export/reload, and slices to G-code using the selected tool.
- Android 16 model-editing instrumentation: 46/46 passed; Debug and Slice JVM suites: 658/658 each.

## 0.8.388

- Verify that joining objects with linked instances preserves every world-space copy and its filament assignments through 3MF export, reload, and slicing.
- Correct the model API documentation to reflect support for materializing linked instances during joins and Boolean operations.

## 0.8.385

- Guard native split, scale, rotate, flatten, auto-orient, handedness, and flatten-plane JNI entry points against invalid handles, stale indices, missing geometry, and non-finite values.
- Apply multi-volume scaling atomically so an invalid derived transform cannot leave a partially modified model.
- Add device regression coverage for invalid native transform requests and verify valid multipart splitting, plane cuts, auto-orientation, and slicing remain functional.

## 0.8.384

- Guard native mesh simplification, object copying, and transform queries against null models, stale indices, and invalid ratios.
- Verify direct JNI calls return safely without changing source meshes, while valid transform reads and object copies still work.

## 0.8.383

- Reject invalid cut and connector requests at the native boundary before they can mutate model geometry.
- Verify invalid inputs preserve model bounds and printable volume, while valid connector edits survive cleanup, 3MF reload, and slicing.

## 0.8.382

- Guard native object deletion, XYZ translation, and bed placement against stale object indices and invalid translation values at the JNI boundary.
- Verify invalid native edit requests preserve object count and exact bounds; valid Z movement and bed placement remain functional on Android 16.

## 0.8.377

- Add Sans, Serif, and Monospace font choices to standalone 3D text; show system-font options only when the device can read them and wrap the controls on narrow panels.

## 0.8.376

- Add a native "Open in browser" fallback to KromaCut so the editor stays accessible when a device WebView cannot display its embedded workspace.
- Verify the bundled KromaCut editor, WebGL 2 canvas, and external HTTPS fallback on the Android 16 emulator.

## 0.8.374

- Keep both pieces of a plane-cut model resting on the print bed while preserving the XY separation between pieces.
- Verify cut-piece volumes, filament assignments, bed placement, and generated extrusion G-code on Android 16.

## 0.8.373

- Reject a multi-cutter Boolean Difference when a later cutter does not further cut the current intermediate solid; failed operations preserve every source object.
- Verify three-object Boolean union, difference, and intersection, grouped surface placement, XYZ group movement, and curved standalone text through slicing on Android 16.

## 0.8.372

- Publish the verified paint-stroke Undo/Redo and cancellable paint-draft workflow from 0.8.371 as the current installable release.
- Preserve the native renderer, project editing, and model-history behavior verified on Android 16.

## 0.8.371

- Add bounded per-stroke Undo/Redo to color, support, seam, and fuzzy painting; each brush gesture can be reversed before confirming the paint session.
- Keep height painting and clearing as cancellable draft changes until Done, so Back no longer leaves those edits committed.
- Verify multi-stroke Undo/Redo, commit, and model-history behavior on the Android 16 emulator.

## 0.8.361

- Synchronize the in-app release notes with the shipped version so the update dialog and changelog test show the current release.
- Keep the dense-model touch crash protection from 0.8.360: meshes above 20,000 triangles use bounds-based object selection instead of synchronous native face-neighbour index construction.

## 0.8.360

- Use safe bounds-based selection for models over 20,000 triangles instead of building the native face-neighbour raycast index during a touch. This prevents slow-device native crashes on generated M12 fasteners and other dense meshes.
- Keep surface painting and placement protected from approximate hits; simplify dense models first for precise surface editing.
- Verify generated M12 touch selection and STEP/STP import, model grouping, XYZ alignment/distribution, and slicing regressions on Honor PTP-N49 / Android 16.

## 0.8.359

- Protect source STEP/STP files during meshing/import so cancelling or failing an import cannot delete the user's original document.
- Bind simplification and model-edit actions to the model and selection captured on the GL thread; reject stale actions after a model or selection change.
- Verify Z positioning, object joining, placement on model surfaces, standalone 3D text, SVG solids, Boolean operations, 3MF roundtrips, and resulting G-code on Honor PTP-N49 / Android 16.

## 0.8.356

- Move all selected model objects to another build plate together, preserving separate objects, their filament assignments, and relative placement.
- Capture the selection on the GL thread and keep the source plate unchanged if the target plate cannot be loaded or copied.
- Verify group transfer and plate reload on Honor PTP-N49 / Android 16; group duplication, bed placement, and multipart slicing to material-aware G-code remain covered.

## 0.8.355

- Fix opening STEP/STP files shared by Android file apps that provide the URI only in `ClipData` instead of `EXTRA_STREAM`.
- Keep the STEP meshing options dialog ahead of geometry import; verify cancel leaves the model untouched and confirm imports a renderable mesh.

## 0.8.354

- Add equal-gap distribution of three or more selected objects along X, Y, and Z, keeping the outer objects in place and rejecting layouts without enough space.
- Warn before Z distribution because it can leave models floating above the bed; no automatic print preparation or bed correction is applied.
- Show an unsupported-file message for unknown file:// extensions instead of opening a document picker that cannot help with that file.
- Verify geometry, unchanged off-axis coordinates, endpoints, no-room rejection, undo/redo, Honor/emulator UI interaction, and native X/Y slicing to extrusion G-code.

## 0.8.351

- Fix numeric X/Y/Z position edits for multi-selected models: the confirmed move now commits the same translation to every selected object, matching the preview while preserving group spacing.
- Verify cancellation, all three axes, undo/redo, and selected-object spacing on Honor PTP-N49 / Android 16.
## 0.8.350

- Keep STEP/STP files selected through Android's document picker while the workspace Compose dialog host is attaching; show tessellation and compound-splitting options before loading and rendering.
- Verify the delayed-host settings-to-mesh path on Honor PTP-N49 / Android 16; the existing version 0.8.349 already contained STEP/STP parsing and the options dialog.

## 0.8.349

- Add precise selected-group placement on a tapped, visible upward-facing model surface. The result remains a printable multipart assembly and preserves per-volume filament assignments.
- Fix native mesh ray-hit JNI data to return both hit position and face normal; cancel the placement mode with Android Back without changing the model.
- Import STEP/STP with a pre-import tessellation options dialog, compound splitting, progress, and cancellation.
- Debug and Slice JVM suites: 610/610 each. Honor PTP-N49 / Android 16 instrumentation: 301 tests, 298 passed, 3 private-fixture tests skipped, 0 failures/errors.

## 0.8.347

- Add individual remapping for imported multipart 3MF colors. Different model colors can share a filament slot, and remapped part and painted-facet assignments persist through 3MF export/reload and slicing.
- Fix Z positioning from the ruler: moving along Z now updates and previews Z while preserving X and Y. Confirm, undo, and redo are covered.
- Verify remapping and sliced G-code with a synthetic multipart project and the user's private KromaCut 3MF on Honor PTP-N49 / Android 16. The private fixture was temporary and is not included in the repository.
- Debug and Slice JVM suites passed 605/605 each. Honor full instrumentation ran 294 tests: 290 passed, 3 private-fixture tests skipped, and the initial run found an outdated changelog version; after updating the release notes, the affected device test passed 1/1.

## 0.8.345

- Make the cut workflow transactional for connector pins: if a cut fails, only pins added for that attempt are removed; existing unprocessed pins and the source mesh remain intact.
- Keep the cut action on the GL thread and verify its successful split path, failed-operation rollback, native model-editing suite, and slicing after surface placement on Honor PTP-N49 / Android 16.
- Debug and Slice JVM suites passed 604/604 each; ARM64 debug build succeeded.

## 0.8.344

- Place selected objects onto a target surface and keep the result as a printable multipart assembly with per-volume filament assignments.
- Reload standalone 3MF exports through the volume-aware importer while preserving BBS multi-plate projects on their project importer.
- Show a lifted, moving menu tile and a highlighted drop target while reordering bottom-menu actions.
- Native Honor PTP-N49 / Android 16 checks passed: model editing 18/18, slicing 10/10, multi-plate projects 4/4, two-color/multicolor projects 8/8, and workspace menu customization 4/4. Debug and Slice JVM suites passed 604/604 each; debug APK build succeeded.

## 0.8.341 (debug prerelease)

- Reorder bottom workspace menu actions directly: long-press an action and drag it to its new position. The menu customization button remains available for hiding, showing, or resetting actions.
- Keep reorder available to accessibility services through move-up and move-down custom actions. Update the existing menu customization device test for drag reordering.
- Kotlin debug compilation succeeded. Device interaction tests have not yet been run; Xiaomi confirmation is pending.

## 0.8.340 (debug)

- Keep the build plate visible while inspecting sliced layers: layer controls are docked outside the renderer viewport in portrait and landscape.
- Update the in-app release notes to match this version and describe the layer-panel change in German, English, and Russian. This also fixes the stale release-note version that failed the Honor changelog test.
- Honor PTP-N49 / Android 16: full instrumentation passed 253/256 with 3 unavailable private KromaCut fixtures skipped; 0 failures. Debug and Slice JVM suites passed 591/591 each. Layer-panel interaction passed 2/2, and actual slice/layer selection passed 1/1.

## 0.8.339 (debug)

- Move the post-slice layer controls out of the build-plate viewport. The renderer now gets a separately measured area above the portrait controls or beside the landscape controls, so the selected layers can be inspected without a dialog obscuring the plate.
- Keep the layer panel scrollable on short or large-text layouts while preserving a minimum visible renderer area. Closing with its button or Android Back restores the normal workspace; layer range changes still update the active G-code viewer.
- Honor PTP-N49 / Android 16: `SliceResultWorkspaceDeviceTest` 2/2 passed, including renderer/panel non-overlap and close/Back. `sliceMenuProducesResultAndExposesSafeResultActions` passed 1/1 with a real native slice and layer block selection. Debug and Slice JVM suites passed 591/591 each. Emulator remains offline.

## 0.8.338

- Use Android dp window dimensions and the existing adaptive workspace policies to size navigation, panels, and the 3D viewport; remove the manual 3D render-resolution selector and migrate its saved override to the device performance policy.
- Separate app text zoom from workspace layout decisions so changing the app font size keeps the viewport and control geometry anchored. Android's system accessibility font scale still drives responsive layout accommodations.
- Reserve the workspace profile header and active temporary 3MF banner for the maximum app text zoom, using only the system accessibility scale. The banner scrolls internally when needed, so changing app text no longer shifts or resizes the build-plate viewport.
- Replace the numeric text-size choices with an accessible slider, live workspace-style preview, Standard reset, Apply, Cancel, and Android Back behavior.
- Honor PTP-N49 / Android 16: the text-scale viewport regression test passed with an active 3MF settings banner. `TemporaryProjectSettingsBarLayoutDeviceTest` passed at 320/360/400 dp portrait and 640×320 / 800×400 dp landscape, 100/130/150% system scale, and 90/100/150% app zoom (45 combinations). `SettingsAccessibilityDeviceTest` also passed for its 12 synthetic layout configurations. Debug and Slice unit suites: 590/590 each.
- Code commit `eca4f3c8daefdc3d359dad58f1784c53a6bfe178`. Debug APK `OrkanSlicer_eca4f3c8da.apk` (239,148,111 bytes), SHA-256 `45D9F6AD3AABD78B01D95CEE57DC0F01E338B7A9820FA3C5804EF8D869087B69`. Installed/launched on Honor PTP-N49 / Android 16 as 0.8.338 / code 373; sampled 1,200 Logcat lines had no fatal exception, package ANR, OOM or fatal-signal match. Emulator is offline. Xiaomi-specific verification and manual TalkBack traversal remain open.

## 0.8.337

- Recover when Android revokes access to a previously selected converter folder: clear the stale folder grant and open the standard Android save picker instead of failing to save generated KromaCut models.
- Do not remember a converter folder unless Android granted write access.

## 0.8.336 (debug build)

- Update the in-app release notes to describe the KromaCut black-screen fix in German, English, and Russian. Xiaomi-specific verification remains pending.

## 0.8.335 (alpha)

- Fix KromaCut's black editor by hiding the existing GL `SurfaceView` only while the full-screen WebView editor is open. Honor diagnostics showed the GL surface overlapped the WebView; hiding it exposed the complete KromaCut controls and image canvas. Restore the same GLView when the editor closes and request a fresh frame; the native model object remains intact.
- Set the bundled page's root heights from the measured WebView pixel viewport because its `100dvh` CSS rule computed to zero on Honor despite a nonzero viewport.
- Extend Honor instrumentation to require a visible sidebar/canvas layout and verify Android Back restores GL visibility and the same native model. A live Honor screenshot showed the KromaCut controls and image canvas after the fix. Xiaomi-specific confirmation remains open.

## 0.8.333 (alpha)

- Force hardware compositing for the embedded KromaCut WebView and detect missing WebGL 2, which the bundled Three.js renderer requires.
- Surface WebGL and shader initialization errors in a localized diagnostic instead of leaving a silent black editor.
- Extend the KromaCut device test to assert WebGL 2 availability. Xiaomi behavior still needs confirmation on the affected device; this debug prerelease is a diagnostic candidate, not a verified Xiaomi fix.

## 0.8.332 (alpha)

- Expand Honor device verification for the selected-object/whole-plate Edit menu and the view cube at 320 dp / 150% text, 400 dp / 130% text, and 800×366 dp landscape / 130% text.
- Use the actual accessible menu paging control in responsive instrumentation instead of assuming a fixed lazy-grid index makes an action visible.
- Full `InitialProjectMenuDeviceTest`: 42/42 passed on Honor PTP-N49 / Android 16. Focused responsive menu and view-cube checks passed in the listed configurations.
- Debug prerelease. Full responsive/accessibility acceptance, manual TalkBack traversal, and multi-plate project persistence remain open.

## 0.8.331 (alpha)

- Bundle the upstream Kromacut 4.2.0 editor with the Android WebView/file bridge and relative offline assets.
- Let multiple model colors intentionally map to the same ACE spool. The slicer now keeps duplicate mappings, remaps tool changes and M104/M109 selectors, and collapses palette/material/mass metadata to the selected physical slots; shared slots are blocked only when material or nozzle temperatures conflict.
- Keep individual model assignment and the mapped slice preview/G-code path aligned; add device coverage for choosing the same ACE slot twice and for loading the bundled Kromacut page.
- Add per-menu action reordering and visibility controls, move camera presets into App settings, and keep edit actions visible but disabled when they cannot currently run.

## 0.8.330 (alpha)

- Refresh the active Project menu after a model import so "3MF sichern" becomes enabled as soon as the renderer commits the model, including when Project was already open.
- Fix landscape instrumentation navigation by swiping the ordinary workspace rail to reveal Slicing; cover both pre-slice cancellation and the actual G-code result/export flow.
- Exercise canvas paint touch through the GL view in tests without creating a competing UiAutomation connection.
- Update the localized in-app release notes for 0.8.330; the first full Honor run caught the stale 0.8.329 title and the focused regression test plus full rerun passed.
- Verify `InitialProjectMenuDeviceTest` 42/42 on Honor PTP-N49 / Android 16 in true 800×366 dp landscape; synthetic filament palette lists 1/4/8/16/24 remain selectable across 320 dp/100%, 360 dp/130%, and 400 dp/150% test layouts.
- With the user-provided KromaCut 3MF staged only in the SliceTest app's external-files sandbox, all 3 KromaCut tests passed: every 3MF import-choice combination, object reassignment, actual slice output, blue/white ACE gate selection, matching G-code palette/tools/layers, and the 3D preview color array matching native slice input. The temporary device copy was removed.
- Debug and Slice unit suites passed 579/579 each; full Honor instrumentation passed 232/235 with three private KromaCut fixtures skipped and zero failures/errors; `:app:assembleDebug` succeeded. The 0.8.330 APK installed and launched on Honor PTP-N49 / Android 16 as code 365; the sampled startup log contained no fatal exception, ANR, OOM, or fatal signal. No printer sync or print was performed. UI 2.0 and manual TalkBack acceptance remain incomplete.

## 0.8.329 (alpha)

- Fix the landscape workspace action menu being compressed to icon slivers; allocate a readable responsive width while reserving room for the renderer.
- Keep the build-plate center directly touchable during landscape color painting by docking the paint controls to the left, and cap mode-panel height when Android briefly measures the workspace with unbounded height.
- Honor PTP-N49 verification: workspace menu actions 42/42 and mode-panel host tests 5/5; Debug and Slice unit suites 579/579 each. The full landscape slicing action did not complete reliably and remains an open verification item; no printer was contacted and no print was started.

## 0.8.328 (alpha)

- Fix view cube face-label collisions on narrow phones at enlarged system text sizes: compact, localized direction letters remain visually bounded while the full localized face names stay available as accessibility actions.
- Verify the view cube camera-face selection and drag rotation on Honor PTP-N49; the face remains a minimum 48 dp accessible target.

## 0.8.327 (alpha)

- Fix slicing with the bundled Anycubic Generic PLA profile: remove the commented fan-control macro block that the embedded native parser incorrectly treated as executable custom G-code. It contained no active machine commands.
- Extend Honor UI coverage to assign two different objects to separate filament slots, slice the same native model, and verify the selected G-code palette and emitted tools.
- Verify the full Honor instrumentation: 234/234 passed with the provided KromaCut 3MF staged privately for testing; 0 skips/failures/errors. Debug and Slice unit suites: 578/578 each. The app was not connected to a printer and no print was started.
- Debug APK and SHA-256 are recorded in the execution status after the final build.

## 0.8.326 (alpha)

- Keep the exact effective filament palette used by each native slice result and use it in the G-code preview, so sliced models no longer revert to stale/original 3MF colors.
- Support per-object physical filament-slot choices in the pre-slice dialog; each object can use a different logical T slot. The bundled multicolor fixture verifies model assignments, the captured slice palette, and emitted G-code tool commands.
- Group single-object operations and whole-plate operations into separate scopes in the Model menu; move/rotate/scale, copy/fill, center, lay-flat, paint and other selected-object actions stay together.
- Add a compact, accessible, draggable six-face view cube next to the plate selector and remove the redundant camera preset tiles from the bottom Camera menu.
- Keep all four compact header actions visible on narrow screens; the printer/profile card now yields space before any action can be clipped.
- Debug and Slice unit suites: 578/578 each. Full Honor instrumentation: 231 passed, 3 private-fixture cases skipped, 0 failed. `InitialProjectMenuDeviceTest`: 42/42; `ThreeMfMulticolorDeviceTest`: 4/4, including per-object tool commands and the sliced preview palette.
- The 48 dp view cube and printer header action bounds also passed on Honor with a temporary 320 dp width override; the physical display configuration was restored afterward.
- Debug APK `OrkanSlicer_7bc07759f8.apk` (157,292,193 bytes), SHA-256 `AA7F0FCB9985FA27C8D0C49F079C21550DF7A476DC3CCB64F1535C371A0945C6`. Installed/launched on Honor PTP-N49 as 0.8.326 / code 361; sampled startup logcat had no fatal exception, ANR, OOM, or fatal signal. Code commit: `7bc07759f8a2b65a6edb077453356e3eac361b5e`.
- Published as a Debug prerelease: [v0.8.326](https://github.com/Exa201/OrkanSlicer/releases/tag/v0.8.326). The uploaded APK digest matches the local build. PR #2 remains open; no merge.

## 0.8.325 (alpha)

- Fix ACE/MMU color assignment so a model tool uses its selected physical gate in the on-bed preview, slice configuration, firmware tool command, upload metadata, and final G-code palette. Selecting an occupied ACE gate swaps the former assignment instead of silently creating duplicate active gate mappings; the final preflight now checks the same mapping used by the upload.
- Fix native slicing overriding configured 3MF/ACE filament colors with the stale global palette. The native slicer now receives the effective `filament_colour` from the generated slice config, with safe fallback colors only for missing entries.
- Verify the supplied private KromaCut 3MF on Honor PTP-N49: all 3 fixture tests passed, including slicing with T0 mapped to white ACE 2 and T1 to blue ACE 4; both tools and the selected palette were present in generated G-code. No printer connection or print start was performed.
- Debug and Slice unit suites: 573/573 each, no failures, errors, or skips. Full Honor `:app:connectedSliceTestAndroidTest`: 229/229 passed, including the supplied KromaCut fixture (0 skipped/failures/errors). `PrintProfileDropdownIntegrationDeviceTest`: 18/18 and the occupied-gate assignment Compose test: 1/1 passed.
- Debug APK `OrkanSlicer_2b652b7e8e.apk` (157,254,372 bytes), SHA-256 `8021A2B11CBAE13972CF7E431442157F282CBA5CA648D4984423BE8EBD6BE30D`; installed/launched on Honor PTP-N49 as 0.8.325 / code 360. Sampled logcat contained no fatal exception, ANR, OOM, or fatal signal.
- Published Debug prerelease: [v0.8.325](https://github.com/Exa201/OrkanSlicer/releases/tag/v0.8.325), asset digest matches the local APK. Code commit: `2b652b7e8e53cae478659e55dff0b373b57e684d`. PR #2 remains open; no merge. No printer sync or physical print was performed; the private fixture was not uploaded.

## 0.8.324 (alpha)

- At 130% system font and above, compact physical ACE labels to keep all four gate and tool numbers readable on narrow displays.
- Add an Honor Compose regression test for four-column gate visibility and readable tool numbers at 150% font scale.
- Filament palette instrumentation: 20/20 passed; full Honor instrumentation: 227 cases, 225 passed, 2 external-fixture skips, 0 failures/errors.
- Debug and Slice unit suites: 561/561 each. Debug APK installed/launched on Honor PTP-N49; the actual 150% screenshot shows `1 · T0` through `4 · T3` fully visible. Startup log sample had no fatal exception, ANR, OOM, or fatal signal.
- Debug prerelease; this is an accessibility/layout correction, not a change to printer slot mappings. Multi-plate persistence and manual TalkBack acceptance remain open.

## 0.8.323 (alpha)

- Refresh the on-demand GL render and discard stale ACE preview colors when the saved filament palette changes, so edited material colors appear on the model.
- Move printer filament synchronization into the Filaments workspace sheet; show ACE/MMU physical gates in four-column rows without horizontal scrolling.
- Keep filament color/type edits as drafts until Save; Cancel now discards them, and adding a palette slot also requires explicit Save.
- Verify the supplied two-color KromaCut 3MF on Honor: independent import choices, recoloring, painting, uniform object reassignment, generated filament colors/tools/layers, and completed G-code. Focused Kroma tests: 2/2; filament UI: 19/19; MMU sync: 15/15; TwoColor project: 3/3.
- Full Honor instrumentation: 226 tests, 0 failures, 5 fixture-dependent skips; Debug and Slice unit suites: 561/561 each. The three private three-color fixtures remain unavailable.
- Debug prerelease; physical TalkBack acceptance and private three-color fixture testing remain open.
- Post-release fixture follow-up: the three private-fixture cases now use a bundled deterministic three-color 3MF; all 3 pass on Honor, including native T0/T1/T2 slicing. Full Honor instrumentation now passes 224/226 with only the two externally supplied KromaCut cases skipped when that private file is not staged.

## 0.8.322 (alpha)

- Dispatch canvas taps to the GL thread before invoking renderer/model click handlers, including cut-connector placement.
- Add a real Honor interaction test: place a cut-connector preview, press Android Back, and confirm the model remains unchanged and the preview is discarded.
- Verify the focused Honor workspace class (38/38), Debug unit tests (562/562), and Slice unit tests (562/562); no failures or skips.
- Debug prerelease; full menu-action audit, manual TalkBack acceptance, and multi-plate project persistence remain open.

## 0.8.321 (alpha)

- Run cut-tool renderer and native model operations on the GL thread; obtain selection and model bounds there as well.
- Stage cut connectors as previews until Apply, so Back/Cancel discards pending connector placements without deleting saved model connectors.
- Verify the cut panel cancellation path and an actual model cut on Honor; the new device test confirms Apply produces two model objects.
- Debug prerelease; full menu action audit and manual accessibility acceptance remain in progress.

## 0.8.320 (alpha)

- Run selected-object auto-orientation and Flatten renderer resource changes on the GL thread; refresh model state after orientation completes.
- Verify the actual Model menu actions with 35 Honor device tests, including auto-orientation, Flatten enter/exit, arrangement, and bed fill using disposable models.
- Debug prerelease; remaining workspace action audit and manual accessibility acceptance are still in progress.

## 0.8.319 (alpha)

- Handle null HTTP headers, bodies, or errors from printer-upload callbacks without crashing the app; preserve the one-time status check after an uncertain print-start response.
- Add a real Honor end-to-end test that slices a disposable STL, uploads G-code only to a loopback Moonraker mock, verifies no print-start request, and confirms a refused connection is shown as an error instead of crashing.
- Verify Debug and Slice unit suites (562/562 each) and the full Honor instrumentation suite (213 passed, 5 fixture-dependent skips, 0 failures).
- Debug prerelease; full UI menu and manual TalkBack acceptance remain in progress.

## 0.8.318 (alpha)

- Map GL paint hits into the native volume/instance coordinates so real canvas brush strokes produce facet previews on transformed meshes.
- Keep base-filament changes in the color-paint panel as a draft until Done; Cancel and Android Back leave the imported 3MF assignment unchanged.
- Verify real canvas painting, preview-before-commit, Undo/Redo, and the supplied colored KromaCut 3MF import-choice and slicing paths on Honor PTP-N49.
- Debug prerelease; full UI menu and manual TalkBack acceptance remain in progress.

## 0.8.317 (alpha)

- Show a subtle, accessible scroll hint when short landscape navigation rails contain destinations outside the viewport.
- Add a large-text short-rail Compose test that verifies the hint and reaches the last workspace destination by scrolling.
- Verify the landscape rail behavior and Smart Slice dialog cancel/validation behavior on Honor PTP-N49.
- Debug APK/release publication is pending the broader phase acceptance; manual TalkBack traversal remains open.

## 0.8.316 (alpha)

- Correct the translated in-app release-note header so it matches the installed app version; this mismatch was found by the complete Honor instrumentation run.
- Verify workspace navigation, slicing result panels, calibration routes, profile edits, and GL tool-panel layouts on Honor.
- Refresh the debug prerelease after the complete instrumentation suite passes; manual TalkBack traversal remains open.

## 0.8.315 (alpha)

- Extend the real Compose Model-menu device audit: duplicate a requested number of copies, select all native objects, and exercise Delete Selected/Delete All through Cancel, Android Back, and confirmation.
- Verify the action result against the native renderer using disposable STL test models; cancellation and Back preserve geometry, while confirmation removes the intended model(s).
- Run the 198-case Honor instrumentation suite (193 passed, 5 private multicolor fixtures skipped, 0 failures) and the responsive/accessibility Compose tests on Honor.
- Refresh the debug prerelease as a verification snapshot. No production runtime behavior changes are included; full menu acceptance and manual TalkBack traversal remain open.

## 0.8.314 (alpha)

- Use one shared password-classification rule in both profile editor render paths so printer API keys stay masked consistently.
- Keep printer hosts, connection types, Web UI endpoints, and API keys out of project-provided 3MF settings; add a regression test with synthetic hostile values.
- Extend Honor coverage for API-key editor Back, Cancel, Apply, and confirmed profile Save behavior.
- Refresh the debug prerelease after 561/561 Debug and Slice unit tests and the 183-pass Honor instrumentation run (five private fixtures skipped).

## 0.8.313 (alpha)

- Treat printer API keys and passwords as sensitive profile values: mask them in the printer profile list and provide explicit show/hide controls in the editor.
- Keep API key edits staged in the printer profile draft until the user applies and saves the profile.
- Add Honor device coverage for masked display, reveal/hide, Cancel, and draft-only Apply behavior.
- Update the in-app release notes to match version 0.8.313 and its printer API-key change.

## 0.8.312 (alpha)

- Publish a refreshed debug verification snapshot after Honor checks for cloud-profile setup/management, app Back behavior, workspace rotation/touch navigation, and responsive large-text layouts. No runtime feature changes are included in this verification snapshot.

## 0.8.311 (alpha)

- Publish a refreshed debug verification snapshot after additional Honor checks for profile dialogs, calibration actions, filament/plate UI, and temporary 3MF project settings. No runtime feature changes are included in this snapshot.

## 0.8.310 (alpha)

- Apply the selected UI text size immediately to open settings and the full workspace.
- Keep all two-row workspace tool actions reachable on narrow displays and at larger system text sizes with visible paging controls.
- Preserve tool-tile touch targets of at least 48 dp and accessible paging semantics.

## 0.8.309 (alpha)

- Reserve space for a visible “More tools” paging control in the two-row workspace action grid so large-text layouts do not hide reachable actions at the screen edge.
- Page the action grid by the available viewport width and keep all actions accessible through grid scrolling.
- Add large-text tile-bound and paging interaction coverage for 320/360/400 dp layouts.

## 0.8.308 (alpha)

- Record confirmed position and rotation edits in the model undo history so Undo/Redo restores the committed native transforms.
- Make Reset Rotation clear each native model volume's rotation, preserve its other transform values, keep the object on the bed, and record the reset as an undoable edit.
- Add Honor device coverage for native position/rotation values, Undo/Redo, and the real Edit-menu reset action.

## 0.8.307 (alpha)

- Reset model undo history after a successful import so the first subsequent edit restores the imported model instead of an empty workspace.
- Make Reset Scale set the committed native object scale to 100% from both Edit and the model context menu, and record it in undo history.
- Cover cancel, apply, reset, undo, redo, and tool Back against a native model on the Honor device.

## 0.8.306 (alpha)

- Keep every visible action tile inside the two-row workspace menu viewport by accounting for its horizontal padding and column spacing.
- Add responsive Compose bounds coverage for 320/360/400 dp layouts at standard and enlarged system text sizes.

## 0.8.305 (alpha)

- Show more of long calibration preset names and explanations at large system text sizes, and expose each complete title and explanation as one accessible action description.
- Add a Compose device regression check for the calibration row at 320 dp and 150% font scale.

## 0.8.304 (alpha)

- Keep tool actions readable on narrow workspace layouts with large system text by switching to wider, taller tiles and compact profile-action captions while preserving full accessibility labels.
- Keep the workspace plate counter visible at 150% system text without expanding adjacent filament and printer controls.

## 0.8.303 (alpha)

- Let the workspace plate counter grow with system font scale so the selected and total plate numbers remain visible at large accessibility text sizes.
- Add a 150% font-scale Compose regression check and verify the responsive workspace on Honor PTP-N49.

## 0.8.302 (alpha)

- Move workspace tool panel sizing from BedFragment's Android FrameLayout pixel measurements into a Compose-owned, dp-based responsive sizing policy while preserving the existing per-tool dimensions.
- Add Compose panel bounds coverage for portrait and landscape plus unit coverage for fixed, fractional, fill, clamped, and invalid sizes.

## 0.8.301 (alpha)

- Settle workspace route-transition state from `WorkspaceShell`, including direct route changes while the portrait tool panel is collapsed; this prevents the navigation guard from remaining stuck when the animated menu host is absent.
- Add Honor coverage for programmatic route changes with the portrait menu collapsed.

## 0.8.300 (alpha)

- Keep workspace navigation transition state in `WorkspaceController` and correctly capture route changes before updating the selected destination, so configuration changes and rapid menu taps respect the active transition.
- Add controller and Honor navigation regression coverage for the shared transition guard.

## 0.8.298 (alpha)

- Use compact workspace destination labels on small and large-text layouts so the Tools destination remains fully readable while its complete accessibility label is preserved.
- Cover compact labels at 320 dp / 150% font with navigation accessibility tests.

## 0.8.297 (alpha)

- Keep the compact workspace printer-profile title to one line at large font sizes so long names do not push out the build plate; the full profile name remains available to accessibility services.
- Add a narrow-display, 150% font Compose device regression test for profile status height and accessibility semantics.

## 0.8.296 (alpha)

- Allow a new Compose workspace tool to replace the currently active tool through the exclusive WorkspaceController panel flow instead of silently ignoring the selection.
- Add Honor coverage for tool replacement, old-route disposal, active tool state, and Android Back cleanup.

## 0.8.295 (alpha)

- Bind the initially selected Project menu to BedFragment before WorkspaceShell creates its first Compose route, so the first tap displays file actions rather than an empty panel.
- Add an Honor device regression test for opening Project on first launch and displaying both model import and 3MF export actions.

## 0.8.294 (alpha)

- Remove the obsolete Android View menu-host lifecycle from the active workspace destinations; `WorkspaceShell` remains their only visible host.
- Convert the dormant converter menu to the shared Compose menu contract and verify the six active workspace routes do not retain duplicate View hosts.

## 0.8.293 (alpha)

- Make an explicit object-filament choice replace imported 3MF part and painted-facet overrides, both from the object action menu and the color-paint base-filament controls.
- Keep 3MF printer, print, and filament setting choices independent; add Honor UI checks for selecting only print settings or only filament settings and a native 3MF assignment regression test.

## 0.8.292 (alpha)

- Stack printer web error actions on narrow panels and at 130% or larger system font scale, keeping retry and external-open controls fully reachable.
- Add Honor bounds and 48 dp target checks for 320 dp / 100%, 360 dp / 130%, and 320 dp / 150% layouts.

## 0.8.291 (alpha)

- Move printer WebView loading, error, retry, external-open, and close controls into the Compose workspace panel; keep only the WebView surface in its technical Android container.
- Replace the remaining nested ComposeViews and native progress indicator in the BedFragment printer panel with Compose state and Material 3 controls.
- Add Honor coverage for printer panel retry, external open, close, and Android view attachment.

## 0.8.290 (alpha)

- Host the printer WebView panel inside the Compose WorkspaceShell overlay instead of attaching it as a separate BedFragment-wide View overlay.
- Keep WebView creation, origin restrictions, and renderer lifecycle in BedFragment while making exclusive panel state and Back dismissal use WorkspaceController.
- Add Honor coverage for attaching and detaching the technical Android view through the Compose panel host.

## 0.8.289 (alpha)

- Replace the remaining third-party native color-picker dialog with one shared Compose custom-color editor using accessible hue, saturation, and brightness sliders.
- Use the shared Compose palette for the app accent color; make Cancel preserve the current color and expose reset as an explicit action.
- Remove the unused Colorpicker dependency and add Honor coverage for custom color cancellation, selection, application, and reset.

## 0.8.288 (alpha)

- Replace the printer-upload `ProgressDialog` with a non-cancelable workspace Compose panel showing preparation, upload percentage, and printer-status checks.
- Keep upload reservation, G-code validation, ACE verification, server callbacks, and the existing single-attempt print-start safety flow unchanged.
- Route AI-generator failures through the shared workspace error dialog and add Honor coverage for error dismissal and upload progress/back behavior.

## 0.8.287 (alpha)

- Move the image-to-STL/lithophane parameter form into the workspace Compose dialog, including target/mode selection, mode-specific dimensions, resolution, wrap/wave controls, source preview, and image-editor handoff.
- Preserve the native generator, validation, document picker, output callbacks, tone settings, and the Android-view fallback when no workspace host is available.
- Add Honor coverage for mode changes, preview, returning from the image editor, and Back cancellation.

## 0.8.286 (alpha)

- Host local generator progress inside the exclusive workspace Compose overlay, including elapsed time, estimated remaining time, and stage progress.
- Preserve background generation, output validation, completion callbacks, and the non-cancelable behavior; retain the Android dialog fallback when the workspace is unavailable.
- Pass the active BedFragment from converter and maker generators and add Honor coverage for progress updates and completion.

## 0.8.285 (alpha)

- Move post-save load/keep decisions and converter errors into the exclusive workspace Compose dialog host.
- Preserve the platform create-document and folder pickers, file copy behavior, and temporary-output cleanup semantics.
- Add Honor coverage for saved-output cleanup and converter error dismissal.

## 0.8.284 (alpha)

- Move lithophane image-tone editing into the exclusive workspace Compose host, including the rendered relief preview, grayscale mode, brightness, contrast, inversion, and explicit apply/cancel behavior.
- Preserve tone values only on Apply, return to the existing converter options after closing the editor, cancel stale preview work, and dispose preview bitmaps safely.
- Add Honor coverage for the workspace editor, preview rendering, Apply, and Back cancellation.

## 0.8.283 (alpha)

- Move generated STL/model decisions, paired lithophane/LED-base actions, and raster result previews into workspace Compose dialogs.
- Preserve output deletion, save-pickers, model loading, and the sequential save-both flow; release preview bitmaps when their dialog leaves composition.
- Add Honor coverage for generated output, pair actions, and preview lifecycle.

## 0.8.282 (alpha)

- Move the image converter source/target format chooser from an Android view dialog into the workspace Compose dialog host; keep the system document picker and conversion pipeline unchanged.
- Add Honor Compose coverage for format selection and Android Back cancellation.

## 0.8.281 (alpha)

- Move onboarding and setup profile-import notices into the existing Compose setup root, including early-access, invalid-file, import-failure, no-profile, and save-failure messages.
- Preserve the same OK and Android Back dismissal behavior; add device coverage for notice display and both dismissal paths.

## 0.8.280 (alpha)

- Move workspace filament, slot editing, color selection, and profile panels into the exclusive Compose overlay host; retain the existing material assignment and profile callbacks.
- Replace the cloud-management, release-notes, and slicing-progress Android bottom-sheet hosts with Compose dialogs. Slicing progress remains non-cancelable and reports completion once.
- Remove the unused legacy ACE assignment AlertDialog wrapper; keep gate mapping and validation shared with the active Compose print-preparation flow.
- Add Honor coverage for the migrated dialog routes, Android Back behavior, long filament lists, physical slot editing, plate selection, and slicing progress.

## 0.8.279 (alpha)

- Replace the historical `UnfoldMenu` lifecycle adapter with `WorkspaceComposeToolRoute` and remove the anchor-based `showUnfoldMenu` entry point.
- Route position, rotation, scale, cut, and Kromacut tools through the exclusive workspace Compose tool panel while preserving their existing Compose controls and renderer lifecycle callbacks.
- Verify direct tool opening, panel ownership, lifecycle cleanup, and Android Back on Honor; this host check does not claim model-transform operation coverage.

## 0.8.278 (alpha)

- Replace the post-slice G-code information and layer-range `UnfoldMenu` routes with workspace-owned Compose dialogs.
- Keep G-code color schemes, seam and extrusion-role visibility, summary information, timeline, layer-range editing, and GL-thread preview updates connected to the existing viewer.
- Closing either result panel preserves the current preview range and uses the exclusive workspace Back/dismiss handling.

## 0.8.277 (alpha)

- Replace both calibration `UnfoldMenu` routes with one exclusive Compose workspace flow for calibration presets, parameter entry, result recording, and bundled model selection.
- Preserve preset validation, pending calibration values, profile staging, model-import ordering, placeholder-model behavior, and safe workspace error feedback.
- Cancel and Android Back return between calibration pages or close the workflow without setting a calibration or importing a model.

## 0.8.276 (alpha)

- Move the cloud model generator camera/gallery choice and live quota display into the exclusive Compose workspace dialog.
- Keep the generation quota and in-progress guards, cloud quota refresh events, temporary camera file provider, and Android picker result routing intact.
- Close the workspace choice before launching the system camera or gallery; Cancel and Back remain workspace-owned.

## 0.8.275 (alpha)

- Host all ten Maker generator forms and the LitoGEN options editor in the exclusive Compose workspace dialog, removing their native AlertDialog/ComposeView wrappers.
- Keep validation errors inside their forms and route STL-generation failures through the workspace message dialog. Preserve dimension checks, image previews, file-picker flow and explicit generation/save actions.
- Cancel and Android Back dismiss without starting generation. Honor verifies cancellation for all eleven forms and LitoGEN validation retention.

## 0.8.274 (alpha)

- Host the pre-slicing model-to-filament assignment flow in the Compose workspace, including individual assignments, imported-color preservation, ACE mappings, and the material-mismatch confirmation.
- Keep assignment persistence and the slicing callback behind explicit Apply/Continue; Cancel and Android Back leave assignments untouched. Preserve access to temperature settings.
- Add Honor coverage for the real workspace route's Cancel and Back behavior.

## 0.8.273 (alpha)

- Move print preparation and its ACE/material warnings into the exclusive Compose workspace dialog host.
- Preserve printer-change re-slicing, validation, upload-only, and the separate firmware-checked print confirmation; add Honor coverage proving Cancel does not dispatch and upload-only cannot request printing.

## 0.8.272 (alpha)

- Show the root-workspace app-exit confirmation in the exclusive Compose dialog host, retaining the native fallback outside an active Bed workspace.
- Keep the existing explicit-exit cleanup and add Honor coverage for back navigation, visible confirmation text, and cancellation without closing the app.

## 0.8.271 (alpha)

- Show the SVG simplification consent prompt and simplification errors in the active Compose workspace.
- Keep simplification behind explicit user consent, retain the size limit, and write only to a separate cache copy so the source remains unchanged.
- Add Honor coverage for the real workspace prompt and Cancel preserving the source file.

## 0.8.270 (alpha)

- Move 3MF embedded printer, print, and filament settings selection into the Compose workspace dialog.
- Keep project settings temporary, retain saved profiles, and clear the previous project's overrides when the new choice is cancelled.
- Add Honor coverage for selected settings, profile preservation, and cancellation cleanup.

## 0.8.269 (alpha)

- Move profile import warnings, print/filament/printer selection, and overwrite/skip conflict handling into one Compose workspace flow.
- Keep all profiles selected by default and preserve validation warnings, cancellation, import transaction snapshot, and save-failure rollback.
- Add Honor coverage for warning acknowledgement, selection toggles, conflict skip/overwrite, and cancellation.

## 0.8.268 (alpha)

- Show file and profile import failures in the Compose workspace dialog while the Bed workspace is active.
- Preserve the platform alert fallback outside the workspace and leave profile import, model loading, and cleanup behavior unchanged.
- Add Honor coverage for the actual unsupported-file `loadFile` route and Compose dismissal.

## 0.8.267 (alpha)

- Host the GL-triggered prime-tower position editor in the exclusive Compose workspace dialog.
- Keep the active plate offset, pending profile edits, render refresh, Cancel behavior, and keyboard-safe scrolling.
- Add Honor coverage for Cancel, Android Back, and explicit Apply through the BedFragment workspace route.

## 0.8.266 (alpha)

- Show slice preparation, printer upload/start, G-code integrity, and Anycubic mapping failures in the shared Compose workspace dialog.
- Preserve main-thread delivery, upload cleanup, integrity validation, and all existing print-start safeguards.
- Add Honor coverage for the real slice preparation failure route and Compose Back dismissal.

## 0.8.265 (alpha)

- Show Kromacut file-picker and export failures in the existing Compose tool panel.
- Keep the active WebView, file chooser callback, temporary-file cleanup, and generated-file handoff unchanged.
- Allow long error details to scroll inside the dialog and add Honor UI coverage.

## 0.8.264 (alpha)

- Move the app-settings reset confirmation and converter-folder-picker error to Compose.
- Preserve the explicit reset confirmation boundary; Cancel and Back cannot reset settings.
- Add Honor UI coverage for reset confirmation and folder-picker error dismissal.

## 0.8.263 (alpha)

- Render MMU filament-sync errors in the print-profile screen with the existing Compose message dialog.
- Keep sync callbacks on the UI thread and ignore results from a profile screen that has already been destroyed.

## 0.8.262 (alpha)

- Move process, printer, and filament profile option-help dialogs and their favorite actions into Compose.
- Replace the app-settings theme, render-scale, and text-scale choice dialogs with one scrollable Compose selection component.
- Keep preference persistence, theme invalidation, and selection/cancel behavior; add Honor interaction coverage for help favorites and long settings lists.

## 0.8.261 (alpha)

- Move the print-profile flush-volume matrix and prime-tower position editors into Compose dialogs hosted by the profile screen.
- Preserve input validation, locale-aware decimals, per-plate coordinates, pending profile updates, and explicit Apply/Cancel behavior.
- Add Honor coverage for both dialog validation and actual profile-screen actions without persisting cancelled edits.

## 0.8.260 (alpha)

- Move Smart Expert Slice setup, cancellable analysis progress, and result actions into the exclusive Compose workspace dialog host.
- Keep GL geometry analysis, background profile analysis, model-revision checks, cancellation, and explicit apply/slice actions intact.
- Use an adaptive action flow for the result dialog and migrate MMU synchronization failure feedback to the workspace message dialog.
- Add Honor UI tests for validation, Cancel, Back, progress cancellation, and result dismissal without running analysis or slicing.

## 0.8.259 (alpha)

- Move Quick Slice into the exclusive Compose workspace dialog host; keep all profile validation and persistence behind the explicit Slice action.
- Use the workspace message dialog for calibration-model load errors.
- Test invalid quick-slice values, corrected values, Cancel, and Android Back without starting a slice.

## 0.8.258 (alpha)

- Move snackbar state and rendering into the Compose workspace instead of attaching a separate Android overlay view.
- Preserve tagged progress replacement, timed dismissal, swipe-to-dismiss policy, and actionable snackbar callbacks.
- Position workspace notifications around the navigation and tool panels without shrinking the build-plate viewport.
- Add controller-state unit coverage and Honor Compose UI tests for tagged progress, button context, and expiry.

## 0.8.257 (alpha)

- Render generic workspace confirmation and message dialogs from the exclusive Compose workspace panel.
- Focus the duplicate-count field on open and handle Android Back by hiding the IME before dismissing the dialog.

## 0.8.256 (alpha)

- Remove the unused AndroidView fallback and legacy View-based overlay from the workspace tool host; tool routes now require Compose content.
- Remove the unreferenced legacy WebViewMenu. Kromacut's active embedded WebView, editor, file chooser, and download flow remain intact.
- Keep real Compose tool lifecycle, Back cleanup, responsive panel, and outside-dismiss device coverage on the production workspace host.

## 0.8.255 (alpha)

- Profile-settings favorites filter now announces its enabled/disabled state to screen readers in English, German, and Russian.
- Added Honor coverage for the favorites toggle, search input, and profile-level picker at 320 dp and 150% text scale.

## 0.8.254 (alpha)

- App-setting sections now announce their expanded or collapsed state to screen readers.
- Added Honor coverage for settings section state, touch size, and an actual theme-setting action across 320/360/400 dp portrait and 640×320 dp landscape at 100/130/150% Compose font scales.

## 0.8.253 (alpha)

- Maker section controls now announce whether they expand or directly open a specific action.
- Maker generator rows meet the 48 dp touch minimum and are tested at 320 dp width with 150% text scale.

## 0.8.252 (alpha)

- The workspace menu host now requires all six primary destinations to provide Compose content directly; it no longer silently falls back to mounting an Android menu view.
- Unknown workspace destinations fail explicitly instead of being routed to the Project menu.

## 0.8.251 (alpha)

- The build-plate picker Cancel action now has a Material 3 touch target that meets the 48 dp accessibility minimum.
- Build-plate selection cards expose distinct TalkBack labels and are verified through 1/3/6/10/20-plate UI fixtures.

## 0.8.250 (alpha)

- Added a labeled, accessible 48 dp control to reveal the remaining ACE physical slots when the printer slot row overflows; it does not alter hardware slot mappings.

## 0.8.249 (alpha)

- Render project settings, profile status, filament access, plate selection, and printer-web access directly in `WorkspaceShell` Compose content instead of separate AndroidView-wrapped ComposeViews.
- Keep the GLView host stable across workspace navigation and verify that it is not recreated while switching all six main-menu routes on Honor.

## 0.8.248 (alpha)

- Render all six workspace main-menu destinations directly in the shared Compose `WorkspaceShell`; retain legacy view rendering only as a compatibility fallback.
- Tie list-menu EventBus listeners to the active Compose destination lifecycle and verify Project, Model, Camera, Edit, Tools and Slice navigation on Honor without allocating legacy menu views.

## 0.8.247 (alpha)

- Render the camera and model-edit menus directly in the Compose `WorkspaceShell` instead of allocating their legacy menu views.
- Preserve transform-menu model-selection updates with a Compose-scoped event-bus subscription; verify both routes and legacy-view absence on Honor.

## 0.8.246 (alpha)

- Complete the plate-switch callback after activating an already-loaded plate, on the GL thread, matching lazy-loaded plate completion.
- Test switching between both loaded plates in the real two-plate 3MF workspace flow.
- Exercise filament rows and two-row tool actions at 320/360/400 dp with 100/130/150% Compose font scales; verify reachability and 48 dp touch bounds on Honor.

## 0.8.245 (alpha)

- Ensure every measurement-panel exit path releases the renderer's measurement state and points.
- Verify loaded-model paint cancellation and measurement-mode cleanup on Honor PTP-N49 / Android 16.

- Render the model context menu through Compose workspace overlay state instead of attaching a separate `ComposeView` host from `BedFragment`; preserve exclusive-panel and Back dismissal behavior.
- Verify Compose overlay content replacement and cleanup in `WorkspaceController` tests and the Honor context-menu dismissal test. Keep the existing dialog hosts until focused-input Back behavior is proven on device.
- Render the Kromacut close toolbar in the Compose workspace route while retaining its embedded WebView, bundled asset loading, file chooser, and download bridge. Remove the unused second `MainActivity` UnfoldMenu/back-state host.
- Verify all ten direct Compose workspace tool routes on Honor, including Kromacut's attached WebView and its restricted file-access settings.
- Load the bundled two-color 3MF on Honor, start a GL paint session, cancel with Android Back, and verify the renderer exits paint mode without changing the model.

## 0.8.244 (alpha)

- Move workspace menu route transitions into `WorkspaceShell` with Compose `AnimatedContent`; keep the GL renderer host stable.
- Render transform, cut, calibration, slice-information, layer-preview, and AI-generator tool routes directly in the Compose workspace panel while preserving callbacks, GL-thread operations, and Android activity-result flows.
- Verify nine Compose tool routes on Honor for attach, active composition, and Back cleanup; keep Kromacut's required WebView/editor integration on its existing route.

## 0.8.243 (alpha)

- Add per-object names, T0-based tool, material and color to the model list, with direct filament assignment.
- Preserve painted facet assignments across OrkanSlicer 3MF export and import. Accept both `paint_color` and `slic3rpe:mmu_segmentation`, and keep paint selectors aligned with the annotated volume mesh.
- Add a device regression test for paint, export, import and restored filament assignments.
- Continue the Compose workspace migration with shared panels and dialogs, clearer model actions, and responsive navigation. This is an incremental migration; legacy tool hosts remain.
- Warn before exporting a project with multiple plates because the current 3MF export includes only the active plate.
- Virtualize the compact filament palette and verify scrolling and selection with 1, 4, 8, 16 and 24 UI entries. The 24-entry case is synthetic; supported ACE capacity is unchanged.
- Keep fixed T0/T1/T2/T3-to-ACE slot mapping, including empty slots, and retain Moonraker upload/print-start safeguards.

## 0.8.242 (alpha)

- Separate first-layer and other-layer temperatures for six Orca bed surfaces.
- Select the active bed surface in printer settings and preview its temperatures before slicing.
- Preserve existing surface values and backfill only missing legacy values.

## 0.8.241 (alpha)

- Preserve painted facet assignments when reopening an OrkanSlicer 3MF; the Orca/Bambu importer now accepts both supported paint attribute names.
- Keep paint selection indices aligned with the annotated mesh for multi-volume objects.
- Verify painting and 3MF export/import on the Honor device.

## 0.8.240 (alpha)

- Commit color, support, seam, and fuzzy painting before refreshing the workspace model history, so undo and redo operate on the completed painted model.
- Make paint tools and filament swatches at least 48 dp tall, expose their active state and accessible action labels, and announce whether painting is paused or active.

## 0.8.239 (alpha)

- Open the complete filament palette from the compact workspace color button. Phones use an expanded bottom sheet; wide windows use a side panel.
- Reuse the existing filament and ACE slot controller for palette editing, then open the existing model assignment flow from the panel.
- Clarify compact model actions: filling the plate makes copies, while arranging repositions the models already on the plate. Keep the model action grid in two rows.

## 0.8.232 (alpha)

- Keep the saved filament temperatures and pressure-advance values aligned with their physical ACE gates when an empty gate makes the model palette compact (for example, ACE 1, 3 and 4). Changing a model tool's gate now selects the corresponding saved palette profile instead of treating the gate number as a palette index.
- Preserve the physical gate order of an explicit full sync independently of later tool assignments and keep that order aligned when a palette entry is deleted.
- When importing a 3MF with the user's own filament settings, reject an obsolete slot profile whose material differs from the slot's current material. Verify a real three-color 3MF import and complete slice on the Honor test app.

## 0.8.231 (alpha)

- Refresh the Kobra 3 V2 ACE state once more immediately before preparing an upload. A failed final check stops the transfer.
- Compare the used gates, their printer-tool mapping, material and color with the state approved in the send dialog. If any used slot changed while the dialog was open, stop and ask the user to review the assignment again.
- Exercise the final-refresh behavior against simulated printer responses on an Android device, including a slot becoming empty and a color changing while the slot remains loaded.

## 0.8.230 (alpha)

- Warn before printing when several used model tools point to the same physical ACE gate: the dialog now shows the reduced count of printable filament colors and offers direct assignment editing, upload only, or explicit confirmation for an intentional shared-slot print.
- Verify the current Kobra 3 V2 ACE state read-only; its second slot remains empty. The printer's Rinkhals version is not exposed by the available status API, so a physical three-color print with that gap still requires firmware verification and supervised testing.

## 0.8.229 (alpha)

- Show the complete physical ACE slot row in the final send dialog, so a gap remains visible immediately before upload or print.
- Recheck every used model color against the freshly synchronized, loaded and firmware-routable ACE slots before sending. If a selected slot became empty or lost its tool mapping, stop and offer to edit the assignment.
- Exercise the complete three-color Anycubic G-code rewrite with an empty second gate: tool selections route to T0/T2/T3, metadata keeps physical positions, and the final layer and end command remain present.

## 0.8.228 (alpha)

- Show every physical ACE gate after filament sync, including an empty gate between loaded ones. The overview distinguishes printer tool numbers from the slicer's compact model-color palette and shows each model color's ACE assignment.
- Keep empty gates unavailable for assignment while retaining their positions. Regression checks cover ACE gates `1,0,1,1` and the corresponding T0→ACE 1, T1→ACE 3, T2→ACE 4 G-code routing.

## 0.8.227 (alpha)

- Stop large-model paint sessions before full-resolution mesh and facet overlays are allocated. Offer an explicit simplification choice; cancel keeps the print geometry unchanged.
- Apply the same early choice to embossed text. The renderer also refuses an oversized paint session when invoked through another route.
- Keep the surface-tool threshold in one place and test the suggested reduction for a dense KromaCut-size model.
- Retry a consented reduction when the native simplifier remains above the safe threshold; report honestly if it still cannot reach the limit. Ignore queued simplification after its model is no longer active.

## 0.8.226 (alpha)

- Keep large models selectable while preventing approximate box hits from placing paint, embossed text, or measurement points on the wrong surface. Show a localized explanation when precise surface tools need a simpler model.
- Clear cached selection data whenever a GL model is rebuilt for another mesh.
- Give plate cards more room and use the scrollable grid for large system fonts; keep narrow navigation labels whole, shorten the German 3MF save label, and allow long printer names another line when text is enlarged.

## 0.8.225 (alpha)

- Avoid the native face-neighbour raycast index when selecting dense KromaCut and other models above 100,000 triangles. Taps use the model's current approximate bounds; slicing geometry stays unchanged.
- Verify the large-model tap path on Xiaomi 13 after centering a real KromaCut 3MF model (about 1.38 million triangles): repeated taps on the model and empty plate no longer crash. Keep the plate-picker improvements from 0.8.224.

## 0.8.224 (alpha)

- Use the correct singular and plural object labels in the build-plate picker.
- Refresh the inexpensive plate footprints whenever the picker opens, so a moved or scaled model does not leave an old miniature behind.

## 0.8.223 (alpha)

- Refresh the plate object count and invalidate its thumbnail cache only after a model import actually commits to the active native model. This fixes a stale "0 objects" label observed after keeping the original geometry of a 502,002-triangle STL.
- Recheck the large-model import consent on Honor with a closed 518,400-triangle STL. Both "keep original" and "simplify" render the model and show one object in the plate picker. A cold restart opens an empty plate while retaining the selected printer.

## 0.8.222 (alpha)

- Keep the compact plate picker usable on narrow and short screens: show the circular layout only when it fits, otherwise use the scrollable grid. Generate its miniature model footprints from inexpensive approximate bounds without changing slice geometry.
- Move duplicate, move-to-plate, delete and further model actions to the front of the Edit menu. Ask before deleting the selected model.
- Share menu and navigation sizing tokens, create one-row phone menus lazily, and choose readable text on the selected accent color in both themes.
- Audit the existing import simplification gate and plate state handling; retain their confirmation and preservation behavior. Add a plate layout regression test and document remaining device and low-memory checks.

## 0.8.221 (alpha)

- Ask for the target printer after opening a 3MF and let the user independently choose file print settings, filament settings, or an exactly matching installed printer profile. Own profiles are the default; raw machine commands and connection details from the file are never imported.
- Keep model colors visible when the user retains own filament values. A selected file filament profile, including temperatures and pressure advance, is applied only to the current model; saved profiles remain unchanged.
- Clear the previous project's temporary settings before showing the new choice, including when the dialog is cancelled. Add selection and reader regression tests.

## 0.8.220 (alpha)

- Require the Moonraker upload acknowledgement to identify the same filename that the app sent; an unrelated stored path can no longer be started.
- Give Moonraker print-start requests a separate 30-second timeout with no automatic retry. On a missing start confirmation, direct the user to check printer status before resending because the first request may already have started a print.
- Bound printer error-response parsing to 256 KiB and inspect only a short prefix when identifying an HTML page from the wrong API endpoint.
- Exercise the actual workspace's lazy switch between two 3MF plates on Android, including each plate's own object and filament.
- Recheck the Kobra host read-only: Moonraker and OctoPrint-compatible information endpoints respond, the last job is complete, and ACE slot 2 remains empty. No firmware or physical print was changed.

## 0.8.219 (alpha)

- Bound QIDI Q2 tool metadata to 16 filaments and reject malformed or out-of-range tool numbers before allocating package metadata. Stop retaining embedded thumbnail text during the first G-code pass.
- Verify every QIDI package preview, not only the primary image, and limit the checksum field before reading it.
- Keep an unfinished OctoPrint upload in the processing state even if a print was requested; do not claim a final print result before the server finishes processing.
- Count real 3MF plate elements while ignoring XML comments and CDATA. Verify separate objects and filament assignments on a generated two-plate project with the native Android reader.
- Recheck main navigation and open screens at 130% system font size on Honor; collect a device memory baseline. Live QIDI/OctoPrint printing and the physical Kobra multicolor run remain open.

## 0.8.218 (alpha)

- After a printer switch in the send dialog, mark the previous slice's filament and G-code values as stale and label the action "Slice again and check" until a new slice is ready.
- Ignore orphan 3MF thumbnail images when counting build plates; use declared plate elements or actual plate data, with a mobile-safe upper bound.
- Validate complete QIDI preview PNG data and CRCs before creating or accepting a print package, with a size limit for embedded thumbnails.
- Report an OctoPrint-confirmed print start even when the app requested upload-only, so unexpected remote behavior is visible rather than silently labelled as an upload.
- Recheck the Kobra ACE state read-only, add regressions for phantom plates and broken thumbnails, and run Android device checks. Physical multicolor printing and firmware changes remain open.

## 0.8.217 (alpha)

- Inspect the real G-code tool order in the print dialog, including three-color jobs whose first-use order differs from the logical tool numbers.
- Warn before starting an Anycubic ACE print when an empty physical slot precedes a selected slot. Older Rinkhals firmware can map sparse slots to the wrong color; the dialog offers upload-only or an explicit firmware-checked start.
- Show the configured physical slot even when it is empty, instead of silently displaying another loaded slot in the mapping summary.
- After switching printers in the send dialog, activate the selected profile, slice again, and reopen preparation with the new file and refreshed slot state before any upload.
- Add regression checks for the observed three-color Kobra assignment, tool order, contiguous slots and one-color jobs. A physical print remains to be verified after the printer firmware is corrected.

## 0.8.216 (alpha)

- Preserve and apply the selected own-filament pressure advance when importing a 3MF project, alongside own temperature settings. Editing a positive PA value enables it for that filament; the enable switch can still be turned off explicitly.
- Inspect executable pressure-advance commands in the finished G-code before upload. The print dialog distinguishes slicer-generated filament values from custom start-code commands and shows the selected printer host.
- Verify on the Kobra 3 V2 Moonraker endpoint that Klipper accepts `SET_PRESSURE_ADVANCE` and reports the changed value; restore the previous live value after the read-only workflow check. A physical print was not started.

## 0.8.215 (alpha)

- Require explicit confirmation before slicing when an ACE slot's material differs from the model filament, while preserving manual slot assignments.
- Recheck material compatibility after assigning slots in the print dialog; a mismatched job can be uploaded without starting a print.
- Rebuild the active model mesh when the simplified-preview setting changes, so it takes effect immediately without altering slicing geometry.
- Add a regression test proving a second 3MF project does not retain colors, temperatures, edits or ACE mappings from the first.
- Allow two-line navigation labels for larger Android font settings; check the Honor layout at 130% system font size.
- Recheck the Kobra status endpoint; it still returns HTML rather than usable printer status JSON, so physical print verification remains open.

## 0.8.214 (alpha)

- Release native raycast trees and bed meshes when previews and build plates are recreated; this prevents retained memory after repeated model and printer changes.
- Simplify the displayed mesh of large, unpainted active models in performance mode while keeping the original geometry for slicing and export. Painting switches back to exact geometry.
- Explain when a printer API address returns an HTML page instead of confirming a file upload or print start; never report such a response as a started print.
- Reserve space for both actions in the pre-slice filament dialog so large-font phone layouts can scroll the content without hiding Cancel.
- Check the user Mewtwo 3MF on Honor and audit existing two-color G-code; the older Bulbasaur sample is incomplete and is explicitly excluded from print verification.
- Fix the Kotlin static JNI binding found by the Honor large-model test; retest a closed 259,200-triangle STL and the three-color Mewtwo project on device.

## 0.8.213 (alpha)

- Show the full selected printer name on the workspace card and allow longer profile names in the print, filament and printer selectors.
- Compact the filament-slot labels so more T-slots are visible on a phone without losing the material name.
- Add a regression case for two logical 3MF filaments with six nozzle-temperature variants, matching a user project layout.
- Validate an existing multicolor G-code sample for layer completeness, final extrusion and end order; inspect the printer and Honor portrait/landscape menus without starting a physical print.
- Save GPU memory in the default performance mode by drawing inactive plates as simple model bounds; rebuild their lightweight previews after an app switch without losing plate positions.
- Use 70% viewport resolution and a 25-layer initial G-code preview on Android low-RAM devices while preserving the complete slicing model and exported G-code. Drop obsolete object meshes after deletions.

## 0.8.212 (alpha)

- Validate OctoPrint file-upload responses instead of treating any `files` or `action` field as a successful print.
- Confirm the returned local file path and reject unrelated folder acknowledgements or unsafe paths.
- Distinguish a received file from one that OctoPrint is still processing (`done`).
- Respect `effectiveSelect` and `effectivePrint`: a requested print is reported as started only when OctoPrint confirms it.
- Show clear German and English messages when a file is selected but not started, or when the print start remains unconfirmed; add protocol regression tests.
- Refresh the in-app release notes, which still showed version 0.8.202 on startup.

## 0.8.211 (alpha)

- Treat printer responses with `success: false` as rejected requests, even when HTTP and the top-level code report success.
- Require a confirmed Moonraker upload before starting a print. Use the file path acknowledged by Moonraker rather than assuming the requested filename was stored unchanged.
- Respect `print_started` and `print_queued` upload flags, avoiding a duplicate start request and showing the actual printer state.
- Require an explicit `ok` response to the Moonraker print-start request before reporting that the print started. Unknown responses ask the user to check the printer state instead of silently claiming success.
- Add regression cases for malformed acknowledgements, unsafe paths, queued jobs and print-start responses.

## 0.8.210 (alpha)

- Expose the native Orca bridge line width and relative bridge angle settings together with the existing bridge controls; move bridge flow and density into the same group so they appear only once.
- Show bridge line width as linked nozzle-relative percent and millimeter fields, capped at 100% to match the native slicer's validation. Add a compact Auto (0) action to the paired line-width editor.
- Add German text for the bridge-width control and explanation, and verify the actual localization catalog through a unit test.
- Run project-settings regression checks against the bundled two-color 3MF and a real Orca Benchy 3MF; confirm filament colors and temperatures remain separate while printer overrides are ignored.

## 0.8.209 (alpha)

- Keep checking a printer's top-level error code when an HTTP-success response also contains a neutral `error: 0` or `error: false` field. A file parser error can no longer be mistaken for a successful upload in this case.
- Validate the configured printer host before sending, reject blank or malformed addresses, and build consistent API URLs for both OctoPrint and Moonraker, including trailing slashes and reverse-proxy paths.
- Add regression tests for mixed printer response fields and printer URL construction; the real printer acceptance tests remain open.

## 0.8.208 (alpha)

- Inspect successful printer API responses for embedded errors before reporting an upload or print as complete. This covers OctoPrint-compatible, Moonraker upload, and Moonraker print-start responses.
- Present compact server error text and codes, including Anycubic's unknown-filament and file-parser responses, instead of dumping an entire JSON response.
- Catch synchronous request setup failures so the upload progress dialog closes with an error rather than remaining stuck.

## 0.8.207 (alpha)

- Read only relevant 3MF settings entries, cap their count and expanded size, and reject duplicate settings entries before parsing to reduce memory pressure from large or malformed archives.
- Limit expanded plate metadata to 8 MiB. When project metadata cannot be read, keep loading the geometry and show a warning rather than silently dropping the project settings.
- Name missing inherited process and filament profiles in the import warning so users can review the active values before slicing.

## 0.8.206 (alpha)

- Reject a send attempt cleanly if the sliced result has become unavailable, and ask the user to slice again instead of crashing on a missing preview result.
- Reconcile the prioritized feature and UI list with the current implementation; retain real print and device checks as open acceptance work.

## 0.8.197 (alpha)

- Hide the settings search and filter controls until the search button beside the profile selector is tapped in Print, Filament and Printer menus.
- Clear an active query and favorites filter when closing search, so hidden filters cannot leave an incomplete settings list.
- Move the long own-filament slot explanation behind an info button, keeping material mismatch warnings visible in a compact form.
- Verify the settings menus in portrait and landscape on Honor Magic7 Pro.

## 0.8.196 (alpha)

- On 3MF import, prefer temperatures from the user's saved filament profile with the same material for each tool; keep the 3MF model colors and do not alter saved profiles.
- Resolve stale slot-to-profile mappings by material, so a PETG slot cannot silently inherit ABS temperatures from an old mapping.
- Test the effective first-layer temperatures in actual G-code on an Honor device.

## 0.8.195 (alpha)

- Apply the filament editor's general bed temperature to every Orca plate surface in the temporary slice config, preventing a hidden Cool Plate value from lowering a print to 35 °C.
- Show the actual first-layer and later nozzle/bed temperatures for each used 3MF tool before slicing.
- Provide a direct Filament-menu action in that dialog and ask for slot confirmation again after temperature or profile changes.
- Compare the material recorded in the sliced G-code with the loaded ACE gate before direct printing. Missing or mismatched material information offers upload-only instead of starting the print.
- Reproduced the 35/215/205 °C values in a previously uploaded G-code file; no live print was started for this check.

## 0.8.194 (alpha)

- Preserve an imported 3MF model's T1/part-color assignment by default instead of silently flattening it to one slot before slicing.
- Use one filament-assignment dialog from the model menu and pre-slice flow. A confirmed assignment updates the model preview and is reused for slicing unless the model, printer or palette changes.
- Show temporary 3MF filament materials in Quick Slice and the workspace header instead of the unrelated saved filament preset.
- Show only tools actually selected in the G-code in the printer preparation dialog; a T1-only file no longer displays an unused T0 mapping.
- Write the selected ACE gate's physical color and material into Anycubic vendor metadata. Keep the sliced temperature commands unchanged.
- If a direct print uses a different material from the selected ACE slot, offer upload-only and require the user to correct the filament settings and re-slice before printing.
- Tested a large two-filament 3MF import, 400,000-triangle reduction, Kobra 3 V2 slicing, complete G-code, and upload-only path on an Honor Magic7 Pro. The uploaded file was retrieved from the printer and passed G-code integrity checks. No physical print was started because the model requests PLA while the connected ACE contains PETG/ABS.

## Earlier alpha development

This fork includes the earlier OrcaMobile/SliceBeam Kotlin UI migration, model and 3MF workflows, maker tools, printer integrations, and Android 16 compatibility work. The upstream project history and retained credits document their origins.

### Verifikation (2026-10-21)

- Debug- und Slice-Unit-Suiten: 586/586 bestanden. Vollständige Honor-Instrumentierung: 237 Tests, keine Fehler oder Überspringungen; KromaCut-Fixture separat 3/3 bestanden.
- Finaler Code-Commit `ba7c1b85ee33a909a7857fca42ad6a18ce735b3a`; Debug-APK `app/build/outputs/apk/debug/OrkanSlicer_ba7c1b85ee.apk` (239,126,049 Bytes, SHA-256 `90964E8769D99BE1FD1B7A41086250AE338CFAB143298E45881B915D20E5042A`). Auf Honor PTP-N49 installiert und gestartet.
