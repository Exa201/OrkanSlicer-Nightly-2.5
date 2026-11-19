# OrkanSlicer Nightly agent rules

Use the project skills under `.agents/skills/` when their frontmatter trigger matches the requested work. Read only the matching skill plus any installed upstream skill it explicitly references; do not copy the same generic Android/Compose advice into multiple project skills.

## Skill routing

- Native slicing behavior, throughput, CPU use, or memory: `slicer-engine-optimizer`.
- Bed rendering, camera, picking, mesh interaction, GL lifecycle: `slicer-3d-renderer`.
- Compose screens, navigation, accessibility, touch, layout: `slicer-compose-ui`.
- Import/export, SAF, STL/3MF/AMF/OBJ/STEP/STP/SVG/G-code: `slicer-file-formats`.
- Physical Honor PTP-N49 validation: `slicer-honor-qa`.
- Shared behavior, slicing output, regressions, test selection: `slicer-regression-guard`.
- Offline behavior, local state/data, network boundaries: `slicer-offline-architecture`.

If work spans areas, load the smallest relevant set and define the shared contract before editing. Preserve the active Nightly branch and all pre-existing changes. Never reset, clean, revert, or overwrite unrelated dirty files. Do not publish or merge unless separately requested.

## Project constraints

- Confirm branch, HEAD, dirty state, build variants, and device identity before changes or installation.
- Keep the existing `GLView`/`GLRenderer` renderer and Compose workspace host architecture; do not add a second renderer, navigation owner, or parallel state store.
- Treat `app/src/main/jni/libslic3r` and `app/src/main/jni/slicebeam/beam_native.cpp` as slicing-critical. Preserve upstream behavior unless a reference fixture or test proves the intended change.
- Use Android Storage Access Framework grants for user documents; never depend on broad storage permissions or raw external paths.
- The engine, local profiles, model editing, and local file workflows should stay available offline. Printer transport, cloud/profile sync, updater checks, and remote Kromacut/Web content are explicit network-dependent boundaries.
- Verify every device by model/serial and package. A connected Xiaomi or emulator is not evidence of a Honor run.
- Performance claims require comparable repeated measurements and named build/device/scenario; source inspection alone is not a benchmark.

## Verification entry points

- JVM suites: `:app:testDebugUnitTest` and `:app:testSliceTestUnitTest`.
- Device suite: `:app:connectedSliceTestAndroidTest` (requires a reachable device/emulator and the `sliceTest` variant).
- Targeted tests use Gradle's `android.testInstrumentationRunnerArguments.class` filter; record the exact class/method and serial.
- For native or file-format behavior, include a fixture-backed test and verify import/export or sliced output as applicable.
- Run `git diff --check` after edits. Report unavailable device tests explicitly; never mark them passed based on APK compilation.
