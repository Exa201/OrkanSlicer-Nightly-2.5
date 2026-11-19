# OrcaSlicer 2.5.1 Upgrade Checkpoint

Updated: 2026-11-19

## Current Nightly package checkpoint — 0.8.505

- App/package version: 0.8.505 / code 540, `com.exa201.orkanslicer.nightly25`.
- Version bump commit: `ff7472fd09`. APK: `OrkanSlicerNightly25_ff7472fd09.apk`, 240,448,025 bytes, SHA-256 `3C1A8F59CF6D26BC17E6BF95A9971FF275184357AA70B1D456104D8365C8781F`.
- Source-native `:app:assembleDebug` passed; the prebuilt native bundle was rejected by its integrity check. `aapt` confirmed the package/version and SDK levels. This rebuild only increments package version metadata; it does not add a functional Orca port.
- Published as experimental prerelease [v0.8.505-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.505-nightly.1), with APK and checksum sidecar. No unit suite, device installation, Honor validation, or printer test was run for this metadata-only rebuild; ADB is not available in the current shell.

## Scope and branch

- Target: OrkanSlicer Nightly 2.5 only.
- Branch: `codex/nightly-orca-2.5.1-upgrade` (base `614d44b4`, app version 0.8.453).
- Stable OrkanSlicer is not part of this upgrade.
- OrcaSlicer upstream stable release verified: [v2.5.1](https://github.com/OrcaSlicer/OrcaSlicer/releases/tag/v2.5.1).
- Upstream `main` snapshot checked on 2026-11-19: [`4f71398a`](https://github.com/OrcaSlicer/OrcaSlicer/commit/4f71398a0fc9719aeda72cc6a32997f304ca9da9).

The Android project is based on SliceBeam and has Android-specific JNI, UI, packaging and profile behavior. Upstream changes must therefore be reviewed and ported selectively; replacing the Android core wholesale with desktop OrcaSlicer is not a compatible upgrade strategy.

## Upgrade sequence

1. Inventory the current Android fork and diff the latest upstream Orca commits against Android call sites.
2. Port memory-safety and crash fixes first; keep firmware tool IDs and slicer filament indices semantically separate.
3. Port geometry/arrangement fixes behind targeted native regression fixtures.
4. Port slicing and G-code behavior only with reference output comparisons (including multi-filament slots and preview packaging).
5. Port profile/configuration changes with profile compatibility checks and migration coverage.
6. Run native/build tests, instrumented regression suites, then Honor PTP-N49 Android 16 checks (including 150% font size, navigation, multi-plate, large models, and offline).
7. Capture reproducible before/after performance evidence for any optimization; only then build and publish a Nightly APK/checkpoint.

## Upgrade ledger

This checkpoint records the first candidate fix; it is not counted as completed/verified until its runtime regression test passes.

| # | Upstream change | Android adaptation | Evidence/status |
|---|---|---|---|
| 1 | OrcaSlicer `d3992c228879826a9cb435e745fe2b8931cd95be` (“Bound Out-of-Range filament_map and physical_extruder_map Values”, #17476) | Clamp invalid `filament_map` nozzle indices in the per-print config copy; bounds-check the temporary physical-first-filament table without changing the physical tool number emitted to G-code. | Implemented in `PrintApply.cpp` and `GCode.cpp`; regression instrumentation test added. ARM64 native/test APK build reached device install. Test did **not** execute: Xiaomi returned `INSTALL_FAILED_USER_RESTRICTED` (“Install canceled by user”). Not counted verified. |
| 2 | OrcaSlicer `428a8fcc91` (“allow arranging objects almost as wide as the bed”, #17416) | Port packed-size inflation/cap-to-room preparation while retaining Android sequential-clearance and bed-exclusion behavior; resolve percent skirt distance as a percentage of nozzle diameter. | Implemented in `Arrange.cpp`, `Arrange.hpp`, `PrintConfig.cpp` and Android arranger call sites in `beam_native.cpp`. Expanded `near_bed_size_arrangement_regression()` checks two 190-mm parts with 20-mm spacing, plate containment, and percent skirt conversion. ARM64 test APK compiled successfully; Xiaomi again rejected instrumentation installation with `INSTALL_FAILED_USER_RESTRICTED`, so runtime remains unverified. |
| 3 | OrcaSlicer `8ebb0a9a2b` (“prevent Arachne simplification from accepting far intersection points”, #17599) | Keep squared-distance comparisons in double precision; integer conversion before squaring could overflow for intersections of nearly parallel lines and move a wall vertex far outside the outline. | Code ported in `Arachne/WallToolPaths.cpp`. Existing `arachneSlicesPinchedTubeWithOnlyOneWallOnTop` covers ordinary Arachne slicing, but the exact upstream long notched-strip fixture has not yet been ported. Do not count this fix as regression-verified until that fixture runs. |
| 4 | OrcaSlicer `4aaef535e6` (“skip Arachne toolpaths when the Voronoi diagram cannot be repaired”, #17600) | Validate the Voronoi diagram immediately after construction; on invalid topology, log and return with an empty skeletal graph before later code can dereference missing twin edges. | Guard ported in `Arachne/SkeletalTrapezoidation.cpp`. Upstream includes two unrepairable-sliver fixtures; these are not yet present in Android instrumentation. Compile and dedicated runtime regression are outstanding. |

The new regression case in `app/src/androidTest/java/ru/ytkab0bp/slicebeam/NativeSliceDeviceTest.kt` uses a controlled tool-changer profile and test cube, asserts slicing emits extrusion, validates the firmware tool metadata, and checks the source configuration remains unchanged. It must still run on an authorized test device or a compatible ARM64 Android emulator.

## Validation and environment

- `git diff --check`: passed after the Arachne source changes (rerun after this doc update).
- `python scripts/check-native-prebuilts.py arm64-v8a`: passed, 68/68 dependencies present.
- Focused Gradle task: `:app:assembleSliceTestAndroidTest` passed after the arrange update. The attempted focused runtime task was `:app:connectedSliceTestAndroidTest` with `NativeSliceDeviceTest#arrangementUsesAvailableBedAreaForNearBedSizeObjects`.
- Native ARM64 core and instrumentation APK compile; both assemble attempts passed, most recently with the two Arachne changes.
- The targeted Arachne instrumentation command reached the connected Xiaomi 2211133G but Android declined the test APK installation with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. The test body did not start; no device-level pass is claimed for changes 1–4.
- The configured API 36 ARM64 AVD could not boot because its `system-images/android-36/google_apis/arm64-v8a` package is missing. The installed x86_64 image cannot execute this ARM64-only test APK.
- No before/after performance benchmark has been run. This correctness fix is not a performance optimization, and no speed or memory improvement is claimed.
- No pre/post benchmark has been run and no speed or RAM benefit is claimed.
- The installable Nightly app APK was published as an **experimental prerelease**, not as a verified release: [v0.8.504-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.504-nightly.1). Both the APK and SHA-256 sidecar are attached. Device regression validation is still outstanding.

## Experimental APK checkpoint — 2026-11-19

- Nightly app version was advanced beyond the latest remote release (0.8.503 / code 538) to 0.8.504 / code 539 so Android will treat this as an upgrade.
- Code commit: `e428183d56`. Installable ARM64 debug APK: `app/build/outputs/apk/debug/OrkanSlicerNightly25_e428183d56.apk`, 240,448,042 bytes, SHA-256 `DCE13E44BB4FE55AB920C597A6365E127692F3505C61FC733C311BD90DB88187`.
- `:app:assembleDebug` succeeded; `aapt` confirmed package `com.exa201.orkanslicer.nightly25`, version 0.8.504/code 539, minSdk 23 and targetSdk 36.
- Xiaomi rejected the test APK installation (`INSTALL_FAILED_USER_RESTRICTED`), so this app build is not device-verified. It was published as the experimental GitHub prerelease [v0.8.504-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.504-nightly.1). APK and checksum sidecar upload were confirmed via the GitHub release API.

## Candidate upstream changes to screen next

Review each change against Android call sites and add a targeted regression before porting. Do not bulk-merge upstream `main`.

1. `8ebb0a9a2b` — prevent Arachne outline simplification from moving vertices far from geometry (#17599).
2. `4aaef535e6` — skip Arachne toolpaths when Voronoi repair fails (#17600).
3. `428a8fcc91` — allow arranging objects almost as wide as the bed (#17416); especially relevant to Nightly's arrangement bug reports.
4. `12c9ee813b` — apply brim filament to features (#17655).
5. `8b3de0be86` — sparse infill strategy selector (#17645).
6. `07f38df2a4` — top surface over inner walls with one top wall (#17597).
7. `e76b4d676c` — split smoothed infill corners no finer than 5 microns (#17630).
8. `745207d3e3` — choose filament per calibration (#17621).

## Next concrete action

Port the exact upstream Arachne notched-strip and unrepairable-sliver cases into the Android native regression harness. Then run them after the Xiaomi accepts test installation; the immediate external blocker is Android's `INSTALL_FAILED_USER_RESTRICTED`. Continue screening upstream candidates in plan order while keeping each unverified item marked. Do not claim the upgrade complete or publish a validated release until included fixes have relevant regression evidence.
