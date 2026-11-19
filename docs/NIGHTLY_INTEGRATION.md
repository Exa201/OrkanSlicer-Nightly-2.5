# OrcaSlicer 2.5 Android-core integration

## Nightly APK checkpoint — 2026-11-19 — 0.8.508

- Quellstand: `68ec1b77f7` (`codex/advanced-manufacturing`), Nightly package `com.exa201.orkanslicer.nightly25`, version code **543**.
- Debug JVM suite: **943/943** bestanden; Slice JVM suite: **943/943** bestanden. `:app:assembleSliceTestAndroidTest` und der ARM64 Debug-APK-Build erfolgreich.
- APK: `OrkanSlicerNightly25_68ec1b77f7.apk`, 283,297,835 bytes, SHA-256 `FBECA60C50B18453DB0A50E308E4C39491AFBCB904A663E14232A2D1CD5FED5D`.
- Installation und Start auf Xiaomi 13 (`2211133G`, ADB `192.168.68.56:42641`) erfolgreich; Android meldet Version 0.8.508 / Code 543, App-Prozess aktiv. Im abgefragten Logcat-Ausschnitt keine Fatal-Exception oder ANR gefunden. Kein Honor-Gerätetest in diesem Lauf.
- GitHub pre-release: [v0.8.508-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.508-nightly.1). Diese Nightly ist keine stabile/offizielle Release-Version.

## Nightly app validation — 2026-11-03 — 0.8.428

- Built from code commit `9e4f4da68bc1776ebd483ee5fde1015ea170ecc9`; app 0.8.428 / code 463; isolated package `com.exa201.orkanslicer.nightly25`.
- Debug and Slice JVM suites passed 743/743 each (0 failures, 0 errors, 0 skips). `SettingsMenuActionDeviceTest` passed 10/10 and `NativeSliceDeviceTest#arrangementUsesAvailableBedAreaForNearBedSizeObjects` passed 1/1 on the Android 16/API 36 emulator.
- Debug APK `OrkanSlicerNightly25_9e4f4da68b.apk`, 240,304,840 bytes, SHA-256 `3617a436d3e1b9cc60de39870d90fbd6d047d8ecd4000495cf72008532e1248d`.
- The APK installed and launched on the API 36 emulator; the inspected logcat window had no matching fatal exception, ANR, or OOM. This does not claim physical Xiaomi/Honor or printer acceptance.
- [Nightly prerelease v0.8.428-nightly.2](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.428-nightly.2); APK and SHA-256 file are attached there.
- Orca 2.5 remains a selective Android port, not a full desktop-engine replacement. Feature-batch completion is not claimed by this artifact release.

## Nightly app validation — 2026-10-31 — 0.8.421

- Batch 2 completed 10/10 focused Android adaptations. Orca `efd65b733` filename-template parity is extended by `f273fe8ac`'s `{printing_filament_types}` placeholder, which lists distinct materials used by a plate in tool order. OpenEYE's 13 process profiles use the multi-material placeholder.
- Native ARM64 build and instrumentation compilation succeeded. The real two-material slice/name regression passed 1/1 on API 35 and API 36; identical material types are deduplicated. Debug and Slice JVM suites passed 705/705 each, with 0 failures, 0 errors, and 0 skips.
- Code commit `669c46d161575cac5b59735ea1b5f9f376a3b4bb`; app 0.8.421 / code 456; package `com.exa201.orkanslicer.nightly25`; label `OrkanSlicer Nightly 2.5`.
- Debug APK `OrkanSlicerNightly25_669c46d161.apk`, 239,774,624 bytes, SHA-256 `07713748A33C1A99C49432B5DDF331EBEEE1CAD3543D2D101603DF8F8A1710EB`. APK signature is the Nightly debug signing key (certificate SHA-256 `6254b703f8440e375448b8ec213ef2dc63ebd6c4094ae83eb25f39c2cf426d12`).
- Installed and launched on the API 35 x86_64 emulator with ARM64 native translation; process remained alive and the inspected logcat window had no fatal exception, ANR or OOM markers. This is emulator startup verification, not physical-device or printer acceptance.
- [Nightly prerelease v0.8.421-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.421-nightly.1).
- Selective 2.5 backports only. No physical printer behavior or physical GPU benchmark is claimed; the complete Orca desktop engine baseline is not advanced.

The Android app uses the SliceBeam UI and a platform-adapted OrcaSlicer engine in
`app/src/main/jni`. The Android port is not a desktop source drop-in. The official
upstream is configured as `orcaslicer-upstream`; reviewed revisions are recorded in
`ORCA_NIGHTLY_LOCK.md`.

## Nightly app validation — 2026-10-30 — 0.8.418

- Qidi Q2 package preparation now avoids re-opening and decompressing the entire newly written G-code 3MF solely to repeat its MD5 calculation. The package creation path checks the already-streamed source digest against the MD5 entry and validates required entries and all preview PNGs; the public deep verifier still hashes the full stored toolpath.
- The captured workspace image is still embedded in the G-code thumbnail block and Qidi 3MF image entries. Qidi package tests passed 7/7, including corruption detection; Debug and Slice JVM suites passed 687/687 each, with the ARM64 app and instrumentation APKs built.
- Physical Qidi upload, observed package-preparation timing, and printer-screen preview were not tested: no ADB device was connected. This release is a selective Android port, not a full upstream 2.5 replacement.

## Nightly app validation — 2026-10-30 — 0.8.417

- Code commit `862402e50c954a521a815fd84875b79203410e6e`; app 0.8.417/code 452; native identity 0.8.413/build 448.
- Ported Orca `b7ecf1b29`: multi-nozzle printable-area validation now checks actual polygon intersections, avoiding false warnings for inset regions while preserving out-of-range detection.
- ARM64 app and instrumentation APK build succeeded. Debug and Slice JVM suites passed 686/686 each; focused Qidi package tests passed. The Qidi 3MF path embeds the captured workspace PNG in `Metadata/plate_1.gcode` and package preview entries and checks the G-code MD5. No device was connected, so upload/physical Qidi behavior remains unverified.
- APK SHA-256: `6E27FD90458F84A2A24ED9304463C187EFC79B8568F2431CC0A8EFD84D6663B1`; ARM64-only and debug-signed.
- [Experimental Nightly 2.5 prerelease](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.417-nightly.1).

## Nightly app validation — 2026-10-30 — 0.8.416

- Code commit `098e65375f...`; app 0.8.416/code 451; native identity 0.8.412/build 447.
- Selectively ported Orca `29e189957`: restore layer Z before a travel-capable toolchange over a compacted wipe tower. Both Android tower-generation paths now insert the lift at the toolchange boundary.
- Full ARM64 native/app/instrumentation build succeeded; Debug and Slice JVM suites passed 686/686 each. New compacted-tower multi-color instrumentation regression compiled, but ADB reported no connected device, so it was not executed.
- APK SHA-256: `12D7EF80F583D89356657122A1C499CD891CDBE30A3E6A91DBB54F98E29FA967`. The app remains arm64-only and debug-signed; no physical printer acceptance is claimed.
- [Experimental Nightly 2.5 prerelease](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.416-nightly.1).

## Nightly app validation — 2026-10-30 — 0.8.415

- 0.8.415 app/code commit: `7ec32de2c5677cd63ed10368992165f944eeeb6f`; native identity 0.8.411/build 446.
- Orca `a4a510087` tree-support clearance correction is selectively ported. Full ARM64 app/instrumentation build succeeded; Debug and Slice JVM suites passed 686/686 each.
- Debug APK: `OrkanSlicer_7ec32de2c5.apk`, 239,654,238 bytes, SHA-256 `A4A479E891731E544203822843F2D6670A782D15199B476E88DCEEFAAE064DB7`. Device instrumentation and printer acceptance remain unverified because ADB had no device.
- This release does not advance the full engine lock. Fixed 3MF/STL slicing and multi-material G-code validation remain required.

## Reviewed upstream (2026-10-30)

- Official repository: <https://github.com/OrcaSlicer/OrcaSlicer>
- Current `main`: `a4a51008798934b55ae766aa7cc41d61c40d11e6`
- Latest 2.5 prerelease: `v2.5.0-beta`, published 2026-10-27
- `main` has 68 commits since the beta comparison point and diverged by 5 commits.
- Official release assets are desktop distributions; none is a drop-in Android
  `libslic3r.so`.
- The existing Android source baseline recorded in `ORCA_NIGHTLY_LOCK.md` is not
  sufficient proof of the exact code ancestry. The lock remains unchanged until that
  ancestry and fixed slicing fixtures are verified. The ARM64 build now passes, but
  build success alone does not prove slicing correctness.

## Safe update sequence

The selective ports on `nightly-2.5` include empty-layer guards, 64-bit filament masks,
AMS same-filament preset preservation, Qidi initial-tool filenames, and per-filament
retraction threshold/Z-hop enforcement. The latter is adapted to this Android fork's
filament-ID indexing; upstream's newer variant-column API is not present here. The
0.8.413 ARM64 native build succeeded and Debug/Slice JVM suites pass 686/686 each.
A dedicated multi-filament lift test was added and compiles into the instrumentation APK,
but it has not run because no ADB device is connected. Fixed slicing fixtures and printer
validation remain necessary, so this is not a completed engine migration.

1. Work in the separate `Exa201/OrkanSlicer-Nightly-2.5` repository; keep the stable
   app repository and its release branch independent.
2. Pin upstream source and profiles to reviewed immutable commits. Record each commit,
   license notices, submodule revisions, and generated dependency versions.
3. Compare `src/libslic3r`, profile schemas, CMake source lists, and native APIs against
   the Android port. Port compatible changes in small commits; do not replace JNI,
   Android CMake imports, or bundled ARM64 libraries wholesale.
4. Resolve each new upstream dependency for Android ARM64 and verify ABI, runtime
   licensing, and APK size before enabling its feature.
5. Build `libslic3r.so`, run native/unit tests, slice fixed fixtures, then verify
   single- and multi-material assignments, layer metadata, thumbnails, and
   printer-specific G-code in OrkanSlicer.
6. Advance the engine lock only after the above checks pass on the exact recorded
   upstream commit. Until then the 2.5 branch is experimental, and its native engine
   baseline remains the previously released Android port with selective backports.

## Current source comparison

At the reviewed `main` commit, `src/libslic3r` has 609 paths versus 496 in the Android
port: 130 upstream-only paths and 17 Android-only paths. New source groups include
Belt printing, CAD/sketch solving, and G-code sender support. Android-only code includes
the mobile postprocessor/sender and geometry helpers; these files must be preserved.

The first two upstream core fixes are ported on `nightly-2.5`: return safely when a
thin model produces an empty layer series, and store/serialize filament-to-unprintable-
extruder masks as 64-bit values. The full 2.5 lock remains pending a native build, a
device slice, and G-code/preview checks.
