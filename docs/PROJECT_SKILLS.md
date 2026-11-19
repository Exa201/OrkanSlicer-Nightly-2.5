# OrkanSlicer project skills

## Repository snapshot used to define the skills

- Checkout: `OrkanSlicer-Nightly-2.5-current`, branch `codex/advanced-manufacturing`, HEAD `37e8e4fbeff4953c923e34ff70d258b0cd81b278` at setup time.
- The branch already has a large dirty working tree. This skill setup did not reset or replace those edits.
- Android package: `com.exa201.orkanslicer.nightly25`; current Gradle metadata at inspection: `0.8.507` / code `542`; `sliceTest` is a separate instrumentation build type.
- Rendering: `GLView` (`GLSurfaceView`) + `GLRenderer`, hosted through `RendererAdapter` in the Compose `WorkspaceShell`.
- Slicing: JNI `Native.model_slice` into `beam_native.cpp` and `libslic3r`; Android native sources are configured in `app/CMakeLists.txt`.
- File support in the real source/build path: STL, 3MF, AMF, OBJ, STEP/STP, SVG and G-code packaging/validation. User file access uses Android Storage Access Framework flows.
- Existing validation includes JVM unit tests, `sliceTest` device tests, model/STEP/3MF fixtures, renderer/navigation/profile/accessibility tests and native slicing tests.

## External skill selection and installation

The following repositories/marketplace packages were requested and are installed/enabled in this Codex environment:

| Source | Installed item | Compatibility decision |
|---|---|---|
| Google `android/skills` | `android-skills` 1.0.23 | Trusted Android guidance. Apply only relevant Compose performance, profiling, testing, and Android CLI material. Camera/TV/Wear/Play skills are not part of this app unless that feature is introduced. |
| Chris Banes `chrisbanes/skills` (`main`) | `chrisbanes-skills` 2026.11.18 | Trusted Kotlin/Compose guidance. Use focused performance, state/effects, UI-testing, concurrency, and Gradle-run skills when applicable; do not duplicate their generic checklists here. |
| OpenAI curated marketplace | `test-android-apps` 0.1.2 | Trusted device QA/performance guidance, useful for adb evidence and focused profiling. |

The OpenAI curated marketplace is already registered internally under the reserved name `openai-curated`; trying to add `openai/plugins` separately is rejected as a reserved marketplace. The requested `test-android-apps` plugin was installed directly from the existing curated marketplace. The Google and Chris Banes marketplaces are also registered and enabled. No extra copies were added with `android skills add`: that would duplicate instructions already discoverable from the active official skill plugins. Use the narrow relevant references when a task calls for them (Google: adaptive UI, Compose performance, Android profiling and testing setup; Chris Banes: Compose performance/state/UI testing and the Gradle-run workflow; OpenAI: Android device QA/performance).

Project-specific skills are stored under `.agents/skills/` and define repository paths, existing test targets, invariants, and device policies. They deliberately refer to the installed generic skills rather than copying them.

## Skill routing

See root `AGENTS.md` and each skill's frontmatter for exact triggers. The seven skill folders cover engine optimization, renderer, Compose UI, file formats, Honor QA, regression selection, and offline architecture.

## Baseline and benchmark record

No production source or performance behavior was changed while creating this system, so this task has no before/after performance result to claim. The repeatable benchmark protocol is in `slicer-engine-optimizer`: fixed project/profile/plate, same build/device and warm/cold state, at least five runs before and after, report median and spread, and retain trace/log artifacts.

At setup verification time, ADB listed Xiaomi 13 (`192.168.68.56:42641`, Android 16, arm64-v8a) and API-36 emulator (`emulator-5554`); Honor PTP-N49 was not connected. Therefore Honor Android 16/150% font-scale acceptance was not performed in this setup pass. Do not treat Xiaomi/emulator results as Honor evidence.

## Verification performed for this skill setup

- `:app:testDebugUnitTest`: passed, 942 tests, 0 failures/errors/skips (current Gradle wrapper run; aggregated from `app/build/test-results/testDebugUnitTest`).
- `:app:testSliceTestUnitTest`: passed, 942 tests, 0 failures/errors/skips (same run; aggregated from `app/build/test-results/testSliceTestUnitTest`).
- `:app:connectedSliceTestAndroidTest`: attempted against Xiaomi 13 serial `192.168.68.56:42641`, but the Gradle task failed before a current report was produced: it could not access the task `resultsDir` (“Accessing unreadable inputs or outputs is not supported”). A first attempt also exposed a PowerShell argument-splitting mistake and was not counted. Existing connected-test XMLs are timestamped `2026-11-18T23:27:12` (Xiaomi: 0 tests) and `2026-11-18T23:43:58` (API-36 emulator: 103 tests, 25 failures); they predate this run and are not current results. Instrumentation is therefore not passed.
- Honor hardware QA: not run; Honor PTP-N49 was absent from ADB. Xiaomi presence does not satisfy Honor acceptance.
- Performance benchmark: not run; this work changes skill/configuration documentation only and does not alter production performance behavior. The skills define the controlled before/after benchmark protocol for future optimization work.
- `git diff --check`: passed for the current working tree (only line-ending conversion warnings were reported for pre-existing modified files).

## Known boundaries

- Physical Honor-specific checks require a confirmed ADB serial and model.
- Emulator/native ABI translation can differ from real-device performance and vendor GPU behavior.
- `TEST_INFRA.md` describes a headless historical harness; use current Gradle/JUnit/instrumentation tasks as the actual source of test truth.
- Benchmarks are not meaningful across mismatched build variants, profile/model inputs, device thermal states, or different slice outputs.
- No publication or merge is part of this setup.
