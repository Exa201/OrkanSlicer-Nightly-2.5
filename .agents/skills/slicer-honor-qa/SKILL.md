---
name: slicer-honor-qa
description: Use when installing, launching, profiling, or validating OrkanSlicer Nightly on the Honor PTP-N49, especially Android 16, 150% font scale, viewport interaction, or real-device compatibility.
---

# Slicer Honor QA

## Device identity and limits

The primary physical target is Honor PTP-N49 on Android 16. Before every run, inspect `adb devices -l` and confirm the exact model/serial and Android version on that serial. The Nightly package is `com.exa201.orkanslicer.nightly25`; the test package uses the `sliceTest` suffix. Xiaomi, emulator, or a successful APK build is not a Honor result.

## Trigger

Use for Honor installation/launch, Android 16 real-device QA, 150% font-scale/layout acceptance, or physical compatibility/performance checks.

## Procedure

1. Record device serial/model, OS/build, display size/density, font scale, app version/code/commit, build variant, and test names. Confirm the intended Nightly package before install; never replace the stable package accidentally.
2. If the Honor is absent/offline, do not pair, change settings, or install on another target as a substitute. Continue device-independent tests and label physical QA blocked by connectivity.
3. Preserve existing user data and settings. Before changing font scale/display density, record exact prior values; change only for an authorized test and restore them afterward.
4. Validate at 150% font scale and current 480-dp-class layout as configured: top/bottom navigation visibility, scroll/touch, accessibility labels/focus/back, profile lists, dialogs, object list, and print-bed viewport.
5. Exercise one small and one large model, selection/move/rotate/scale, more than one plate, slicing preview, and offline local workflows. Never start a physical print unless separately requested.
6. Collect screenshot/UI tree and crash/logcat evidence; clear/read logs around the focused flow. Record exact artifacts and outcomes.

## Tests and success criteria

- Use targeted `sliceTest` instrumentation for navigation, large text, model editing, import, profile, and offline cases.
- Success requires confirmed Honor identity, reproducible user-visible behavior, no crash/ANR, correct model/plate state, and no clipped text or inaccessible action at the tested font scale. If Honor is unavailable, report device-specific acceptance as not run, not passed.
