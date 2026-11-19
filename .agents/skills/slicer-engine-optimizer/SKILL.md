---
name: slicer-engine-optimizer
description: Use when changing or diagnosing native OrcaSlicer slicing, G-code generation, CPU parallelism, memory use, caching, slice latency, or engine crashes in OrkanSlicer Nightly.
---

# Slicer engine optimizer

## Grounded project boundary

The native engine is built from `app/src/main/jni/libslic3r` and `app/src/main/jni/slicebeam/beam_native.cpp`, linked by `app/CMakeLists.txt`. Kotlin enters native slicing through `slic3r/Native.kt` (`model_slice`) and the existing `Model`/`SliceListener` path. The Android build pins native engine identity separately in `app/build.gradle`. OpenMP/TBB and native worker use must be verified in the actual call path; do not assume every phone core is used.

## Trigger

Use for native slice correctness, G-code changes, crashes in `libslic3r`, CPU/RAM work, cache behavior, or reproducible performance investigations.

## Procedure

1. Record branch/HEAD/dirty tree, active app and native versions, ABI, device, Android version, build type, and the smallest relevant fixture.
2. Trace the real path from Kotlin request through JNI into the engine and back. Read the owning C++ implementation and configuration mapping; do not invent wrappers or Orca APIs.
3. Establish a correctness oracle before performance edits: existing test/fixture output, expected tool/layer/config values, or a documented upstream behavior. Preserve G-code semantics unless the oracle demonstrates a bug.
4. For performance, use a fixed project/profile/plate and warm/cold conditions. Measure at least five comparable runs before and after; report median and spread, build variant, device, and artifact/log paths. Prefer release/profileable builds for timing. Capture CPU with Simpleperf, scheduling/frame stalls with Perfetto, and memory with `dumpsys meminfo`/heap tools as appropriate.
5. Change one cause at a time. For caches, define keys, invalidation, lifetime, memory cap, and stale-result protection. Never retain plate/model/native objects past their owner lifecycle without explicit proof.
6. Run focused JVM/native instrumentation regressions, then relevant Debug and SliceTest unit suites. For native output, execute a fixture-backed device test when a device exists.

## Tests and success criteria

- Search `NativeSliceDeviceTest`, `GCodeIntegrityValidatorTest`, `AnycubicGCodeProcessorScanTest`, and relevant model/filament routing tests before adding coverage.
- Run `:app:testDebugUnitTest`, `:app:testSliceTestUnitTest`; native behavior should also run under `:app:connectedSliceTestAndroidTest` or a documented unavailable-device state.
- Correctness: same reference result except the requested, fixture-proven change; no wrong tool, lost colors, missing layers, crash, ANR, or stale result.
- Performance: a repeatable measured improvement on the same scenario without correctness, memory growth, or lifecycle regressions. If no baseline or compatible device is available, report that no performance claim can be made.
