## OrkanSlicer Nightly 2.5 — Orca 2.5.0 Sprachkataloge — 2026-11-14 — 0.8.456

- Prerelease: [v0.8.456-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.456-nightly.1), Product-Code-Commit `b7accb79776c562fd9413aa0867586d7c7def0ad`, Branch `codex/nightly-batch11-next`.
- APK `OrkanSlicerNightly25_b7accb7977.apk`, **251,838,734 Bytes**, SHA-256 `facb42073be9d18eb785398aa0854f29d7e299a46e4bd3a9c02ca60e94c0254d`. GitHub meldet denselben Asset-Digest; APK-Signatur mit Android Debug-Zertifikat verifiziert.
- Paket `com.exa201.orkanslicer.nightly25`, Version **0.8.456 / Code 491**. Auf API-36-Emulator installiert und `MainActivity` gestartet; keine Crash-, ANR- oder OOM-Marker in den geprüften Startlogs.
- Orca-Sprachkatalog-Instrumentierung **1/1** bestanden. Debug- und SliceTest-Unit jeweils **779/779**, ohne Fehler oder Skips.
- Batch 17 **10/10** abgeschlossen. Die 23 gebündelten `.po`-Kataloge wurden mit OrcaSlicer 2.5.0 aktualisiert; zusätzliche Nightly-/App-Übersetzungen blieben erhalten.
- Die fünf jüngsten Nightly-Prereleases bleiben erhalten: **0.8.456, 0.8.455, 0.8.454, 0.8.453 und 0.8.452**. `v0.8.451-nightly.1` und sein Tag wurden entfernt. Kein Druckauftrag gestartet.

## OrkanSlicer Nightly 2.5 — Sprachwahl und Batch 16 — 2026-11-13 — 0.8.455

- Prerelease: [v0.8.455-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.455-nightly.1), source commit `e20c0668aeeec92e557727e6636b2e19131aca66`, branch `codex/nightly-batch11-next`.
- Debug APK: `OrkanSlicerNightly25_e20c0668ae.apk`, versionCode 490, package `com.exa201.orkanslicer.nightly25`, size 250,964,847 bytes, SHA-256 `96e3433114e3f0e14fc6217d8dbf6af9e8025515dec6e7b996faa71b50342616`. GitHub reports the same asset digest; signature verified with Android APK Signer.
- Debug and SliceTest JVM suites: 778/778 each. On Android 16/API 36 emulator, ten Batch-16 regression tests passed together and the Orca language catalog asset test passed (10/10 and 1/1). APK installed and launched; no matching fatal crash, app ANR, or OOM in the inspected startup log.
- App settings offer all 23 Orca languages. In addition to the existing German, English, and Russian Android resources, the 20 other languages now ship 172–197 exact-match UI translations each (3,791 total) generated from Orca PO catalogs; custom Android strings absent from those catalogs use the English fallback. Locale-resource regression tests and Android resource compilation passed; this new resource set has not yet been included in a released APK.
- Kept five newest Nightly prereleases: 0.8.455, 0.8.454, 0.8.453, 0.8.452, and 0.8.451. Removed 0.8.450 and its tag. No printer job was started; this is emulator validation, not physical-printer validation.

## Nightly 2.5 Arrange stale-result fix — 0.8.451

- Product-code commit `65358403f31a2969d8a910bb8d468333d17071e6`, branch `codex/nightly-batch11-next`; prerelease [v0.8.451-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.451-nightly.1).
- Debug APK `OrkanSlicerNightly25_0.8.451_debug.apk`, 240,634,902 bytes; SHA-256 `a4b8f1515ab16f7dbcdfffcbe74b78d93d5db4c2f05689481f8461c69e409176`. Package `com.exa201.orkanslicer.nightly25`, version 0.8.451/code 486, ARM64, debug-signed. APK signature verified; GitHub asset SHA-256 matches.
- Debug and Slice unit suites each passed 761/761; API-36 Arrange Compose instrumentation passed 1/1 with four same-size models moved fully outside the build plate and packed back by the visible plate-wide Arrange action. Installed and started on API 36; app process active, no crash/ANR/OOM/fatal-signal hits in inspected logcat. No physical Xiaomi/HONOR was connected.
- Retained the latest five Nightly releases (0.8.447–0.8.451); removed 0.8.446.

## Nightly 2.5 Arrange feedback fix — 2026-11-06 — 0.8.446

- Code commit `c2448cb66a84e47dcbc79120b346eeb956134b89`, branch `codex/nightly-batch11-next`; release [v0.8.446-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.446-nightly.1).
- The Arrange handler was already separate from Fill Bed; it calls `GLView.arrange()` → `GLRenderer.arrangeObjects()` → native plate-wide arrangement. The generic “no room for copies” text was also used by the selected-group placement failure path. That path now has its own message. The visible button now says “Platte neu packen” to distinguish it from “Mit Kopien füllen”.
- Native packing uses a simplified outward-expanded outline while retaining exact mesh footprints for collision validation. Optional cavity filling is capped at 512 candidate positions per object; the primary packing result remains the committed layout.
- `:app:testDebugUnitTest` and `:app:testSliceTestUnitTest`: 756/756 each, no failures/errors/skips. Re-ran `InitialProjectMenuDeviceTest#arrangeCompactsSeveralManuallyMovedModelsInOneAction` on API 36: four models were manually placed fully outside the plate, then the actual Compose Arrange button repacked all four onto the plate (1/1). The emulator executed the ARM64 native test through its translation layer; this is emulator evidence, not a physical-phone result.
- Debug APK `OrkanSlicerNightly25_c2448cb66a.apk`, 240,611,490 bytes; SHA-256 `c3d80d13a15cf4907597b29bc5a7d4c106bbc829735332496898318a58c1e32c`. Package/version verified as `com.exa201.orkanslicer.nightly25`, 0.8.446/code 481. Installed and launched on API 36; inspected startup log had no app crash/ANR/OOM.
- GitHub release APK digest matches the local SHA-256; `.sha256` sidecar uploaded. Five latest Nightly releases retained: 0.8.442–0.8.446; 0.8.441 removed. No physical Xiaomi/HONOR or printer validation was available.
## Nightly 2.5 Arrange feedback fix — 2026-11-06 — 0.8.442

- Published [v0.8.442-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.442-nightly.1) from source commit `865d22406570aa3ae20f64a4e3ff06706e8a2641`; app 0.8.442/code 477, package `com.exa201.orkanslicer.nightly25`.
- Corrected the failure message for “Alle anordnen”: it no longer claims that additional copies cannot fit. The action was already routed to plate-wide arrangement; “Kopien füllen” remains its separate action.
- API 36 instrumentation: six different real mesh assets were moved outside the bed, then arranged through the Compose “Alle anordnen” button; compact placement, single active plate and preview revision passed (1/1).
- `:app:assembleDebug` succeeded. APK `OrkanSlicerNightly25_865d224065.apk`, 240,592,768 bytes; SHA-256 `06d8d092b917689b91ea9817fde87cb86f6084b9dfc309492a5b910481e49e33`; sidecar uploaded. Physical Xiaomi test was unavailable (`adb` not on PATH).
- Retained the newest five Nightly prereleases: 0.8.442 through 0.8.438; removed 0.8.437 and its tag.
# Build Status

## Nightly 2.5 Arrange action verification — 2026-11-06 — 0.8.447

- Release [`v0.8.447-nightly.1`](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.447-nightly.1), source commit `e2ed9672519a815b4d3c7be031516fe0c26b4ad3`; app 0.8.447/code 482, package `com.exa201.orkanslicer.nightly25`.
- ARM64 APK `OrkanSlicerNightly25_e2ed967251.apk`, 230,242,218 bytes; SHA-256 `5f6fea996938c3c52af12a204cb6b30e73c0775a64491460295a933c31039af2`. APK signature verified; GitHub digest matches; `.sha256` sidecar uploaded.
- API-36 Android instrumentation `InitialProjectMenuDeviceTest#arrangeCompactsSeveralManuallyMovedModelsInOneAction` passed 1/1 after switching the test to the stable Arrange action ID. Four models fully outside the plate were packed back inside from the actual Compose action; object count remained four and bounds did not overlap.
- Installed and launched on the API-36 emulator; package version 0.8.447/code 482 verified. Sampled 2,500 Logcat lines had no crash, ANR, fatal signal, or OOM markers. No physical phone was connected.
- The five latest Nightly prereleases retained: v0.8.447, v0.8.446, v0.8.445, v0.8.444, and v0.8.443. v0.8.442 and its tag were removed.

## Nightly 2.5 Arrange-Retry prerelease — 2026-11-06 — 0.8.445

- Release [v0.8.445-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.445-nightly.1), source commit `02b89b9f3834b627639f2545a506d2850255aec3`; app 0.8.445/code 480, package `com.exa201.orkanslicer.nightly25`.
- Debug ARM64 APK `OrkanSlicerNightly25_02b89b9f38.apk`, 240,610,477 bytes; SHA-256 `67e98b3584635c9a86787d176bef060e05fabf8c4fd9f3a4cdd9976cb59ecf04`. APK and `.sha256` sidecar uploaded; GitHub asset digest matches.
- Arrange tries 6 mm, 2 mm and finally 0.5 mm positive separation, restoring original transforms between failed attempts. `PlateArrangeFallbackTest` passed 4/4. Android 16/API 36 tests with four manually moved cubes and six different real meshes moved outside the plate passed 1/1 each.
- APK installed and started on API 36; filtered crash log had no matches. No physical Xiaomi connected. The five retained prereleases are v0.8.445, v0.8.444, v0.8.443, v0.8.442 and v0.8.441; older v0.8.440 and v0.8.439 were removed.

## Nightly 2.5 Arrange-fix prerelease — 2026-11-06 — 0.8.441

- Release [v0.8.441-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.441-nightly.1), source commit `3d0363784ca9b41fa135a699ead35b8baf068ea4`; app version 0.8.441/code 476, package `com.exa201.orkanslicer.nightly25`.
- Debug ARM64 APK `OrkanSlicerNightly25_3d0363784c.apk`, 240,591,983 bytes; SHA-256 `e48f05f766bc247e7bec975e58bd211fe964357f82ba2573e7c55fddadbd337a`. The release includes the `.sha256` sidecar.
- `:app:assembleDebug` succeeded. API-36 instrumentation passed 1/1 with four distinct STL meshes manually moved and arranged through the Compose menu; compacted placement and plate-preview revision were verified. Existing four-cube arrange regression passed 1/1. Instrumented APK installation and startup produced no app-process crash/ANR.
- Installed and launched the release APK in the API-36 emulator; inspected app PID logcat had no `FATAL EXCEPTION`, ANR, fatal signal, or OOM match. No physical Xiaomi or printer test was available.
- Release retention: five newest prereleases retained (`v0.8.441` through `v0.8.437`); `v0.8.436` and its tag were removed.

## Nightly 2.5 verified prerelease — 2026-11-03 — 0.8.429

- Code commit `59313276b9a70c50e1faeb46590b9fafead04d60`; app 0.8.429/code 464; package `com.exa201.orkanslicer.nightly25`.
- Debug and Slice JVM suites: 743/743 each, 0 failures, 0 errors, 0 skipped. API-36 targeted instrumentation: painted-mesh smoothing, 3MF round-trip and slice/G-code tool assignment 1/1 passed.
- Debug APK `OrkanSlicerNightly25_59313276b9.apk`, 240,355,984 bytes, SHA-256 `3792bfb109c9afd2bacbba007d9fa8e81990742c8fc17add6419baab4b79d0f1`. Installed and launched on the API-36 emulator; no crash/ANR/OOM/fatal signal in the inspected startup log.
- Published with checksum sidecar as [Nightly prerelease v0.8.429-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.429-nightly.1). No physical-device or printer test is claimed.

## Nightly 2.5 verified prerelease — 2026-11-03 — 0.8.428

- Code commit `9e4f4da68bc1776ebd483ee5fde1015ea170ecc9`; app 0.8.428/code 463; package `com.exa201.orkanslicer.nightly25`.
- Debug and Slice JVM suites: 743/743 each, 0 failures, 0 errors, 0 skipped. API 36 instrumentation: Settings menu 10/10 and near-bed Arrange regression 1/1.
- Debug APK `OrkanSlicerNightly25_9e4f4da68b.apk`, 240,304,840 bytes, SHA-256 `3617a436d3e1b9cc60de39870d90fbd6d047d8ecd4000495cf72008532e1248d`. Package identity/version verified from the APK; installed and started on the API 36 emulator with no matching crash/ANR/OOM in inspected logcat.
- Published as [Nightly prerelease v0.8.428-nightly.2](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.428-nightly.2), with a `.sha256` asset. Physical-device and printer testing are not claimed.

## Nightly 2.5 experimental APK — 2026-10-30 — 0.8.418

- Code commit `37d9ed1d64b45b57e1c6d4f5f66bdc7ddf52683f`; app 0.8.418/code 453; native engine identity remains 0.8.413/build 448.
- ARM64 app and instrumentation APK builds succeeded. Debug JVM 687/687 and Slice JVM 687/687; 0 failures, 0 errors, 0 skipped. Focused `QidiGCode3mfPackagerTest`: 7/7 passed, including rejection of a post-package G-code modification by the deep verifier.
- Qidi package generation no longer decompresses and hashes its just-written large G-code entry a second time. It compares the stored checksum with the digest calculated while streaming source bytes into the ZIP, and continues checking package structure and all image previews. The separate deep verifier still reads and hashes the archived toolpath.
- The captured workspace image remains embedded in Qidi G-code and all package preview PNG entries. Physical Qidi upload, measured latency, and printer display were not tested because ADB reported no connected device.
- APK `OrkanSlicer_37d9ed1d64.apk`, 239,656,516 bytes, SHA-256 `E7C97CD46DC421F2C1BBA1BE3D215C8CBB8078763339EAC7A6A15D0ADCAC54F7`. `aapt` verified package `com.codemastercody3d.orcaslicermobile`, version 0.8.418/code 453, and ABI `arm64-v8a`.
- Published as [Nightly 2.5 v0.8.418](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.418-nightly.1). Debug-signed experimental build; not a full Orca 2.5 engine migration.

## Nightly 2.5 experimental APK — 2026-10-30 — 0.8.417

- App/code commit `862402e50c954a521a815fd84875b79203410e6e`, app 0.8.417/code 452. Native engine identity 0.8.413/build 448.
- Full ARM64 `:app:assembleDebug :app:assembleSliceTestAndroidTest :app:testDebugUnitTest :app:testSliceTestUnitTest --no-daemon` succeeded in 17m58s. Debug JVM 686/686 and Slice JVM 686/686, with 0 failures, 0 errors, and 0 skipped. Instrumentation APK compiled; it was not run because `adb devices -l` reported no devices.
- APK `OrkanSlicer_862402e50c.apk`, 239,656,527 bytes, SHA-256 `6E27FD90458F84A2A24ED9304463C187EFC79B8568F2431CC0A8EFD84D6663B1`. `aapt` verified package `com.codemastercody3d.orcaslicermobile`, version 0.8.417/code 452, ABI `arm64-v8a`.
- Ported Orca `b7ecf1b29`: multi-nozzle G-code path checks now use the actual inset printable polygon, avoiding bounding-box false positives. The Qidi Q2 package tests pass and verify the rendered PNG in G-code and package preview entries plus MD5. No physical Qidi acceptance or device execution is claimed.
- Published as [OrkanSlicer Nightly 2.5 v0.8.417](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.417-nightly.1). This is a selective experimental port; the full Orca 2.5 engine lock is not advanced.

## Nightly 2.5 experimental APK — 2026-10-30 — 0.8.416

- App/code commit `098e65375f...`, app 0.8.416/code 451, native engine identity 0.8.412/build 447. Debug JVM: 686/686; Slice JVM: 686/686; no failures, errors, or skips.
- Full ARM64 `:app:assembleDebug :app:assembleSliceTestAndroidTest :app:testDebugUnitTest :app:testSliceTestUnitTest --no-daemon` succeeded in 17m48s. Native `GCode.cpp`, `WipeTower.cpp`, and `WipeTower2.cpp` compiled; Android instrumentation APK compiled, but no device was connected to execute it.
- APK `OrkanSlicer_098e653758.apk`, 239,655,755 bytes, SHA-256 `12D7EF80F583D89356657122A1C499CD891CDBE30A3E6A91DBB54F98E29FA967`. `aapt` verified package `com.codemastercody3d.orcaslicermobile`, version 0.8.416/code 451, and ABI `arm64-v8a`.
- Ported Orca `29e189957`: restore the model layer Z before travel-capable toolchanges when a no-sparse wipe tower is below the model. New G-code instrumentation regression compiled but was not run; no printer acceptance is claimed.
- Published as [OrkanSlicer Nightly 2.5 v0.8.416](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.416-nightly.1). This is a selective experimental port; the full Orca 2.5 engine lock is not advanced.

## Nightly 2.5 experimental APK — 2026-10-30 — 0.8.415

- App/code commit `7ec32de2c5677cd63ed10368992165f944eeeb6f`, app 0.8.415/code 450, native engine identity 0.8.411/build 446. Debug JVM: 686/686; Slice JVM: 686/686; no failures, errors, or skips.
- Full ARM64 `:app:assembleDebug :app:assembleSliceTestAndroidTest :app:testDebugUnitTest :app:testSliceTestUnitTest --no-daemon` succeeded. The first attempt hit a transient Windows Ninja dependency-file permission error; the retry completed the full native build and all requested tasks.
- APK `OrkanSlicer_7ec32de2c5.apk`, 239,654,238 bytes, SHA-256 `A4A479E891731E544203822843F2D6670A782D15199B476E88DCEEFAAE064DB7`. `aapt` verified package `com.codemastercody3d.orcaslicermobile`, version 0.8.415/code 450, and ABI `arm64-v8a`.
- Ported Orca `a4a510087`: tree-support continuation now checks the next layer's collision/XY-clearance region. ARM64 compilation included the changed `TreeSupport.cpp`. Instrumentation sources compiled, but no ADB device was connected; no support fixture or printer run is claimed. The full 2.5 engine lock remains unchanged.

## Nightly 2.5 experimental APK — 2026-10-30 — 0.8.414

- App/code commit `5ec59ef50fdbf0768525a3859062a32c5e7a2f81`, app 0.8.414/code 449, native engine identity 0.8.410/build 445. Debug JVM: 686/686; Slice JVM: 686/686; no failures, errors, or skips.
- Full ARM64 `:app:assembleDebug :app:assembleSliceTestAndroidTest :app:testDebugUnitTest :app:testSliceTestUnitTest --no-daemon` succeeded in 18m03s. APK `OrkanSlicer_5ec59ef50f.apk`, 239,654,253 bytes, SHA-256 `55A8490808D4C7D8DD69015D3A6D4569653CF6160FDF17F3DF76455DDCD95ED4`. `aapt` verified package `com.codemastercody3d.orcaslicermobile`, version 0.8.414/code 449, and ABI `arm64-v8a`.
- Ported OrcaSlicer `7125a546b`: engine-derived automatic filament mapping is removed from the config diff when the normalized mapping is reapplied, avoiding unnecessary invalidation of G-code after slicing. The upstream Catch2 regression is not part of the Android test harness. Instrumentation APK compilation succeeded, but `adb devices` had no connected device; no end-to-end slice or printer behavior is claimed.
- APK is debug-signed and experimental. The Orca 2.5 engine lock remains unchanged; this is a selective backport, not a complete engine migration.

## Nightly 2.5 experimental APK — 2026-10-30 — 0.8.413

- Code commit `e722cf6612426bc97ff4764c338fb439131cfbcf`, native core identity 0.8.409 / build 444, app version 0.8.413 / code 448. Debug JVM: 686/686; Slice JVM: 686/686; no failures, errors, or skips.
- ARM64 `:app:assembleDebug :app:assembleSliceTestAndroidTest --no-daemon` succeeded. APK `OrkanSlicer_e722cf6612.apk`, 239,653,454 bytes, SHA-256 `C3546B158C6D6A424D57C4FB4658ADE0FA9BAABCF4150B8DB55DAFFE9B75C320`. Android package/version/ABI verified as `com.codemastercody3d.orcaslicermobile`, 0.8.413/code 448, `arm64-v8a`.
- Ported OrcaSlicer `98b539c3e6b44e87bbde950e26d1b23c66691a0a`: travel threshold and lift-enforcement read the active filament settings; Android uses filament ID because upstream's variant-column API is absent. Added profile import/editor routing for lift enforcement and a two-filament G-code instrumentation regression test. That test compiles but was not run because ADB has no connected device. No physical printer, uploaded job, or actual print was tested.
- The source and app remain a selective experimental port; the Orca engine lock is not advanced to 2.5.

## Nightly 2.5 experimental APK — 2026-10-30 — 0.8.412

- Code commit `92648a381a028eba983f795d8f796b1426f317df`, package 0.8.412 / code 447. Debug JVM: 686/686 passed; Slice JVM: 686/686 passed; 0 failures/errors/skips.
- `:app:assembleDebug :app:assembleSliceTestAndroidTest --no-daemon` succeeded; ARM64 native, app and instrumentation APKs compiled. APK `OrkanSlicer_92648a381a.apk`, 239,653,706 bytes, SHA-256 `A31D2986C10E0AE52CB6AB738E69751608C13B155C1CC17935E6CC348E647EEC`.
- Download: [OrkanSlicer Nightly 2.5 v0.8.412 experimental prerelease](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.412-nightly.1). The repository default branch is `nightly-2.5`.
- `aapt` verified application ID, version 0.8.412/code 447 and ABI `arm64-v8a`; generated BuildConfig records `92648a381a`.
- The new Qidi profile filename regression test passed. No phone was connected; the available AVDs are x86_64 while this APK contains only arm64-v8a, so instrumentation could not run. Qidi transfer/start, model slicing, G-code/preview, and physical printer behavior remain unverified. APK is debug-signed; the engine remains a selective port, not the complete OrcaSlicer 2.5 core.

## Nightly 2.5 experimental APK — 2026-10-30 — 0.8.411

- Code commit `d956a77e9359ad80c9db0a857cff898451ded8c7`, Android package 0.8.411 / code 446. Debug and Slice JVM suites: 685/685 passed each, 0 failures/errors/skips.
- `:app:assembleDebug :app:assembleSliceTestAndroidTest --no-daemon` succeeded; C++ ARM64, app Kotlin, slice-test Kotlin, and instrumentation APK compilation succeeded. APK `OrkanSlicer_d956a77e93.apk`, 239,653,724 bytes, SHA-256 `4A33AED6052F18A564D38F526F15D6D14BB89D845EAFFB694BD0D73A419D320A`.
- `aapt` verified application ID `com.codemastercody3d.orcaslicermobile`, version 0.8.411/code 446, and `arm64-v8a`; generated BuildConfig records commit `d956a77e93`.
- ADB reported no devices. Instrumentation was compiled, not run. No device AMS sync, slicing fixture, multi-color G-code, Qidi/Kobra upload, physical printer, Xiaomi, or Honor test is claimed. The APK is debug-signed and experimental; the complete 2.5 engine lock remains unchanged.

## Nightly 2.5 experimental APK — 2026-10-30

- `:app:assembleDebug :app:assembleSliceTestAndroidTest --no-daemon` succeeded from app-code commit `7d304fa51893bcc3e88c24739c99e4e41e9d7ab2`. Android package 0.8.410 / code 445; native engine identity remains 0.8.408 / ID 443 because the complete upstream engine lock is not advanced.
- APK `OrkanSlicer_7d304fa518.apk`, 239,652,439 bytes; SHA-256 `DAAAAC5F913C1B6FEDCBC2C1154EA45CE225154A3979F22C7FF7AF449826BFFF`. Android `aapt` confirmed package ID, version, ARM64 ABI, and debug signing metadata.
- ARM64 native build succeeded. Production, slice-test, and instrumentation sources compiled and APKs were packaged. The regression was not run because no ADB device is connected. No fixed slicing fixture, Qidi/Kobra upload, G-code, or physical printer result is claimed.
- APK is debug-signed. The 2.5 core port is experimental and selective; use the stable OrkanSlicer repository for production work.

## Current publication and OrcaSlicer 2.5 review — 2026-10-30

- App version 0.8.409 / code 444 built successfully from commit `07602f6dc1cea93849a74639c464d37c678fbac7` on the existing Android engine baseline (native ID 0.8.408 / 443). APK `OrkanSlicer_0.8.409_debug.apk`, 239,646,412 bytes, SHA-256 `2A978E125F1BEEB2F54D99A67F13E391BB9F98009E8C2CBBD8B6560F5E099B48`; Android package metadata verified.
- The official OrcaSlicer 2.5.0 beta and `main` were reviewed. The existing Android native port is not a drop-in desktop build; engine migration remains in the separate Nightly 2.5 repository until ARM64 compilation and slicing fixtures pass.
- Current verified app-code tests: 33 focused tests on commit `b1c0236d8c390e7f1bec88e4d45ef7a4e467659e`; these are not a full regression suite for 0.8.409.

## Aktueller geprüfter Stand — 10.10.2026 — Farbslot-Übernahme und eigene Filamentprofile, Build 0.8.192

- Eine ausdrücklich gewählte physische Farbslot-Zuordnung wird trotz abweichendem Materialnamen übernommen. Vor dem Slicen und nach dem Übernehmen erscheint ein Temperaturhinweis; ein späterer Sync weist dieselbe Auswahl nicht erneut allein wegen des Materialnamens zurück.
- In „Bearbeiten“ sind „Alle Objekte anordnen“ und „Alle Objekte ausrichten“ direkt sichtbar. Die zweite Aktion orientiert jedes Objekt der aktuellen Platte neu.
- Im eigenen Filamentmenü sind T0, T1 usw. einzeln anwählbar. Jeder Slot kann ein gespeichertes Filamentprofil mit eigenen Werten erhalten. Native Filamentvektoren wie Temperatur und Pressure Advance werden pro Slot für den Slice zusammengesetzt; noch nicht gespeicherte Werte des aktuell bearbeiteten Profils werden berücksichtigt. Beim Verlassen einer temporären 3MF-Projektkonfiguration bleiben zusätzliche benötigte eigene Slots verfügbar. Unterschiedliches Slot- und Profilmaterial ist sichtbar markiert.
- ARM64-Quellbuild erfolgreich; anschließender Build mit verifiziertem Quellcode-Cache erfolgreich, 413/413 Unit-Tests. Menüaktionen und Filament-Slotleiste auf dem Honor sichtbar geprüft; finale APK dort installiert und erfolgreich gestartet. Kein Modell gesliced oder Drucker angesprochen.
- APK: `outputs/OrkanSlicer-0.8.192-slot-profile-all-objects-20261010.apk`, 157.634.027 Byte, SHA-256 `B36978DFCD86F083212E62229F468E178120C9217D02B97274FF75954DBACB9C`. Bericht: `outputs/OrkanSlicer-Farbslots-Filamentprofile-2026-10-10.md`.

## Aktueller geprüfter Stand — 09.10.2026 — automatische Modellvereinfachung, Build 0.8.191

- Beim Import werden Objekte mit mehr als 500.000 Dreiecken vor Übergabe an Druckplatte und Renderer automatisch auf höchstens 400.000 Dreiecke vereinfacht. Dies gilt für normale Dateien sowie für sofort und später geladene 3MF-Platten. Schlägt die Vereinfachung fehl, wird das übergroße Objekt nicht angezeigt. Nach erfolgreichem Laden erscheint eine kurze Meldung mit den Dreieckzahlen.
- Die Bulbasaur-3MF des Nutzers wurde auf dem Xiaomi 13 getestet: 1.085.466 → 400.000 Dreiecke, erhaltene sichtbare Modellfarben, kein Absturz. Der separate Modell-Dialog bestätigte 400.000 Dreiecke. Das Original blieb unverändert, und nach einem Neustart war die Platte leer; das Qidi-Q2-Profil blieb aktiv.
- ARM64-Quellbuild und abschließender UI-Build erfolgreich; 412/412 Unit-Tests, APK-Signatur und ZIP-CRC geprüft. Version 0.8.191 / Code 226 auf dem Xiaomi installiert. Der erste Datei-Import benötigt weiterhin Zeit und vorübergehend viel Speicher, da die 3MF zunächst eingelesen werden muss. Slicen und physischer Druck wurden in diesem Schritt nicht getestet.
- APK: `outputs/OrkanSlicer-0.8.191-auto-simplify-20261009.apk`, 157.779.463 Byte, SHA-256 `1F8FF9C320EFFC2A9C047CD0CEB8D65D230086856B02D19F579A17F162E1BE2D`. Bericht: `outputs/OrkanSlicer-Bulbasaur-Auto-Vereinfachung-2026-10-09.md`.

## Aktueller geprüfter Stand — 09.10.2026 — Mewtwo-3MF, Skalierung und trennbare Teile, Build 0.8.190

- Der Skalierungsfehler bei zusammengesetzten 3MF-Modellen wurde mit der Mewtwo-Datei bei 50 % auf dem Xiaomi 13 reproduziert und nach der nativen Korrektur auf demselben Gerät als behoben bestätigt. Die 43 Teilkörper bleiben zusammen.
- „Teile trennen“ ist im direkten Objektmenü erreichbar. Die Mewtwo-Teile werden einzeln benannt und auswählbar; ein ausgewähltes Teil wurde erfolgreich auf eine zweite Druckplatte verschoben. Das Modell wird beim Trennen nicht automatisch auseinander angeordnet.
- ARM64-Quellbuild erfolgreich; letzter UI-Build nutzt die daraus verifizierten nativen Bibliotheken. 412/412 Unit-Tests, APK-Signatur und ZIP-CRC geprüft. Auf Xiaomi installiert; normaler Neustart zeigt eine leere Platte und das zuletzt verwendete Qidi-Q2-Profil. Kein Drucker angesprochen.
- APK: `outputs/OrkanSlicer-0.8.190-mewtwo-assembly-20261009.apk`, 156.185.307 Byte, SHA-256 `FC135A58B109244FB9FC6B2CA18749035EE2F2C99BB579A54E39B8396EA030A0`. Bericht: `outputs/OrkanSlicer-Mewtwo-Teile-Skalierung-2026-10-09.md`.

## Aktueller geprüfter Stand — 09.10.2026 — Farbslots und Schichtteile, Build 0.8.189

- Modellfarben können für Kobra-3-/ACE-Profile vor dem Slicen und unter „Modelle → Farbslots“ physischen Slots zugeordnet werden. „Alles mit einem Slot“ ist verfügbar; belegte Slots zeigen nach Synchronisierung Material/Farbe, bekannte Materialkonflikte werden abgefangen. Fehlender `M106 S0` im Start-G-Code verhindert die erste Werkzeugwahl nicht mehr.
- Der Leistungsmodus ist standardmäßig aktiv, sofern der Nutzer ihn nicht bewusst ausgeschaltet hat. Die Vorschau startet mit 1–50 und bietet Teile mit 25, 50, 100 oder 200 Schichten, direkte Auswahl, Pfeile und alle Schichten. Nur die Darstellung wird begrenzt; die Druckdatei bleibt vollständig. Eine Anzeige nennt den aktuellen Bereich.
- ARM64-Core aus Quellcode gebaut, 412/412 Unit-Tests bestanden, ZIP-CRC und Signatur geprüft. Auf dem Xiaomi 13 installiert; 103/103 Schichten im nativen Test-G-Code, Farbslots und Teilansicht im Hoch- und Querformat direkt geprüft. Originales Qidi-Q2-Profil nach dem Offline-Test wieder aktiv, Neustart mit leerer Platte. Kein Drucker angesprochen. Honor nicht verbunden; automatische Gerätetests auf Xiaomi wegen der zuvor verweigerten Test-App-Installation nicht ausgeführt.
- APK: `outputs/OrkanSlicer-0.8.189-color-slots-layer-parts-20261009.apk`, 157.628.607 Byte, SHA-256 `B50E38BD2DEBFA1828517B3DEEF1396ED439F000236CFA389E0C6C4DE1416AB9`. Bericht: `outputs/OrkanSlicer-Farbslots-Schichtteile-2026-10-09.md`.

## Aktueller geprüfter Stand — 09.10.2026 — Turmposition und Tastatur, Build 0.8.188

- Compose-Dialoge erlauben die Bildschirmtastatur nach der Fensterinitialisierung. Die Turmposition öffnet direkt das erste X-Feld mit Zahlentastatur; Weiter wechselt zum nächsten Feld, Fertig schließt die Tastatur. Beide Zugänge über die Druckplatte und das Druckmenü verwenden die gemeinsame Korrektur.
- Vollständiger ARM64-Quellbuild erfolgreich (12:09 Minuten), 399 Unit-Tests bestanden. Neue Gerätetests erfolgreich gebaut, auf dem Xiaomi wegen `INSTALL_FAILED_USER_RESTRICTED` beim separaten Testpaket nicht ausgeführt. Keine Behauptung eines bestandenen automatisierten Gerätelaufs.
- Produktions-App auf Xiaomi 13 installiert und direkt geprüft: Turm antippen, X=22,5/Y=31,25 eingeben und übernehmen, im Druckmenü wieder öffnen, Änderung abbrechen, Hoch-/Querformat mit sichtbarer Tastatur. Eigene Profildatei unverändert; Testmodell entfernt und ursprüngliche Dreheinstellungen wiederhergestellt. Honor nicht verbunden, Update/Prüfung dort noch ausstehend.
- APK: `outputs/OrkanSlicer-0.8.188-prime-tower-keyboard-20261009.apk`, Version 0.8.188 / Code 223, 155.603.602 Byte, SHA-256 `9C9248E31C29A6D7960C4E1A5D3D402503669CCDADA600F9193E4B46F094F857`. Bericht: `outputs/OrkanSlicer-Prime-Tower-Tastatur-2026-10-09.md`.

## Aktueller geprüfter Stand — 09.10.2026 — mehrfarbige 3MF-Projekte, Build 0.8.187

- Materialzuweisungen einzelner Modellteile werden in der nativen Filamenterkennung und farbigen Vorschau berücksichtigt. Der Dialog vor dem Slicen behält bei mehrfarbigen Projekten standardmäßig Farben und Teilezuordnung bei. Eine ausdrücklich gewählte Einfarb-Zuweisung entfernt die bisherigen Teil-/Malzuweisungen.
- Bambu-H2C-Düsenvarianten werden den wirklichen Filamenten zugeordnet. Bei der Originaldatei `mecha+mewtwo+for+H2C.3mf` sind es jetzt drei Filamente statt sechs, drei benötigte Werkzeuge statt einem und keine der zuvor zehn fehlerhaft gemeldeten Werte. Bekannte Optionswerte werden übersetzt; nullable Automatikwerte bleiben gültig. Die temporäre Projektebene überschreibt weiterhin keine gespeicherten Profile.
- Nativer ARM64-Quellbuild erfolgreich. 399 Unit-Tests und 14 Android-16-Gerätetests bestanden. Alle 957.090 Dreiecke der 43 Modellteile sind den drei richtigen Farben zugeordnet. Vollständiger Slice im Gerätetest und in der normalen App: T0/T1/T2, 1.533 von 1.533 Schichten, letzte Höhe 245,32 mm. Profile unverändert; kein Upload oder physischer Druck gestartet.
- Endgültige APK auf dem Honor installiert und erneut auf Import, Drei-Filament-Dialog und Drehung geprüft. Die doppelte Testinstallation samt Testpaket entfernt; die Produktions-App und die SliceBeam-Referenz-App behalten.
- APK: `outputs/OrkanSlicer-0.8.187-3mf-multicolor-20261009.apk`, 156.111.704 Byte, SHA-256 `13324B1A4FA53396FD63DF57249CB47F58D68FBE39BCA0B638B87D6E38642C04`. Version 0.8.187 / Code 222, Ziel-API 36. Bericht: `outputs/OrkanSlicer-3MF-Mehrfarbig-2026-10-09.md`.

## Aktueller geprüfter Stand — 09.10.2026 — temporäre 3MF-Einstellungen

- 3MF-Import übernimmt Filamente, Farben und Druck-/Filamentwerte ausschließlich in eine temporäre Projektebene. Anzeige und nativer Slicer verwenden dieselben Werte; gespeicherte Profile, Palette und MMU-Zuordnungen bleiben erhalten. „Eigene nutzen“ stellt die eigenen Werte wieder her. Hintergrundwechsel/Drehung behalten das Projekt; bestätigtes Schließen oder Schließen der letzten Platte entfernt es.
- Schnell-Slicen, Profilauswahl und MMU-Sync berücksichtigen die Projektebene. Fehlende Plattentemperaturen werden aus dem allgemeinen Bettwert ergänzt. Veraltete Slice-Ergebnisse werden nach Projektänderungen verworfen.
- 392 Unit-Tests und 12 gezielte Android-16-Gerätetests bestanden. Nativer Zwei-Filament-G-Code enthält passende Temperaturen/Pressure Advance, richtigen Bettheizbefehl und alle 104 angekündigten Schichten. Auf dem Honor importiert, bearbeitet, gedreht, zurückgesetzt, geschlossen und neu gestartet; gespeicherte Profildatei unverändert. Das ursprüngliche Kromacut-Modell wurde wiederhergestellt.
- APK installiert: `outputs/OrkanSlicer-0.8.186-temporary-3mf-20261009.apk`, 159.263.915 Byte, SHA-256 `37999635D69E36608EFBDCC457C0A9B8E75A2DF15EA3345FA429370D9BAC6436`. Bericht: `outputs/OrkanSlicer-Temporaere-3MF-Einstellungen-2026-10-09.md`. Version 0.8.186 / Code 221; geprüfter nativer Quellbuild aus dem bestehenden Cache.

## Aktueller geprüfter Stand — 08.10.2026 — Build 0.8.186

- Die SliceBeam-1.1.0-Arbeitsoberfläche wurde auf dem HONOR PTP-N49 direkt verglichen. Im OrkanSlicer-Arbeitsbereich zeigt die breite Drucker-Karte den aktiven Drucker sowie Düse und Filament gut lesbar; ein klarer Pfeil macht den Profilwechsel erkennbar. Der vergrößerte Abstand trennt diese Karte sichtbar von der Druckplatte, während Plattenauswahl und Weboberfläche direkt erreichbar bleiben.
- Vollständiger arm64-Quellbuild und App-Prüfungen erfolgreich: 354/354 Unit-Tests, 10/10 Android-16-Gerätetests auf dem Honor, Kotlin-/UI-Migrationsaudit (309 Produktions-/92 Unit-Testquellen), Profilrouten (473 Einstellungen/99 deutsche Gruppen), native Prebuilt-Prüfung (63/63), APK-ZIP-CRC und `git diff --check` bestanden.
- Der aktuelle Debug-Build 0.8.186 (`versionCode 221`, `targetSdk 36`) wurde auf dem Honor installiert und visuell geprüft. Die App startete ohne Crash; kein Drucker wurde angesprochen. ADB listet das Honor, kein Xiaomi.
- APK: `outputs/OrkanSlicer_0.8.186_debug.apk` (154.233.635 Byte; SHA-256 `39D45F155D4E790ED96DFDE36F5CE3D410C2D33B111AD8F6931D97B3AB1C4E2D`). Sammelbericht: `outputs/OrkanSlicer_0.8.186_Buildbericht.md`; Honor-Vergleichsbild: `outputs/orkan-workspace-header-final-honor.png`.

## Aktueller geprüfter Stand — 08.10.2026 — Build 0.8.185

- Android-16-Insetbehandlung modernisiert: Der Einrichtungsbildschirm liest Systemleisten- und Aussparungsabstände über `WindowInsetsCompat`. Transparente Systemleistenfarben und Kontrastkorrektur laufen nur auf Android-Versionen vor Android 15, wo diese Plattformaufrufe noch gebraucht werden; Android 15/16 nutzen die erzwungene Edge-to-Edge-Darstellung.
- Toolchain geprüft: `compileSdk`/`targetSdk` 36, Kotlin 2.4.20, AGP 8.9.1, Gradle 8.14.4 und R8 9.1.43. Kotlin 2.4.20 ist stabil aktuell; AGP 8.9.1 ist die Mindestversion für API 36. Ein Wechsel auf AGP 9.4 würde Gradle 9.6 und weitere Plugin-Migrationen erfordern und ist für Android 16 nicht nötig.
- Der vollständige ARM64-Quellbuild dauerte 11:13 Minuten. 354/354 Android-Unit-Tests und 10/10 Android-16-Gerätetests auf dem HONOR PTP-N49 bestanden. Kotlin-/UI-Audit (309 Produktions-/92 Unit-Testquellen), Profilrouten (473 Einstellungen/99 deutsche Gruppen), native Prebuilt-Prüfung (63/63), APK-ZIP-CRC und `git diff --check` bestanden.
- OrkanSlicer 0.8.185 wurde auf dem Honor mit erhaltenen App-Daten installiert und gestartet. Paketversion und aktiver Prozess bestätigt; im geprüften Logcat kein FATAL oder ANR. ADB listet derzeit das Honor, kein Xiaomi.
- Debug-APK: `outputs/OrkanSlicer_0.8.185_debug.apk` (154.233.130 Byte; SHA-256 `8CA6F88757336E95DD4F6304895F2DB30F5C6AB91CC0AB4A83384B9CAA45589F`), `versionCode 220`, `targetSdk 36`. Screenshot: `outputs/OrkanSlicer_0.8.185_Honor.png`. Bericht: `outputs/OrkanSlicer_0.8.185_Android16-Inset-Bericht.md`.

## Aktueller geprüfter Stand — 08.10.2026 — Build 0.8.184

- Die App-Einstellungen folgen jetzt klarer dem SliceBeam-Aufbau: Cloud (sofern verfügbar), Benutzeroberfläche, Druckerprofile, Konverter-Speicher und App-Informationen. Cloud-Aktionen und Druckerprofile haben getrennte, unabhängig aufklappbare Gruppen. Die fünf Hauptbereiche bleiben Vorschau, Druck, Filament, Drucker und App; Maker und alle vorhandenen Optionen bleiben erreichbar. In diesem Offline-Bootstrap-Build ist der Cloud-Dienst deaktiviert, daher wird die Cloud-Gruppe nicht angezeigt.
- Die neue Debug-App wurde mit Erhalt der lokalen Daten auf dem HONOR PTP-N49 (Android 16) installiert. Einstellungen, Oberfläche und Druckerprofile wurden live geöffnet; der App-Prozess blieb aktiv und im gefilterten Logcat erschien kein FATAL- oder ANR-Eintrag. Kein Drucker wurde angesprochen.
- Vollständiger arm64-Quellbuild erfolgreich: 354/354 Android-Unit-Tests und 10/10 Android-16-Gerätetests bestanden. Kotlin-/UI-Migrationsaudit (309 Produktions-/92 Kotlin-Testquellen), Profilrouten (473 Einstellungen/99 deutsche Gruppen), native Prebuilt-Prüfung (63/63), APK-ZIP-CRC und `git diff --check` bestanden.
- Debug-APK: `outputs/OrkanSlicer_0.8.184_debug.apk` (154.233.134 Byte; SHA-256 `11EB3FA47F71AE54C7AB68DF75EA01C0B19FA31F53419FCA277FA77BB070EAF8`), `versionCode 219`, `targetSdk 36`. Honor-Aufnahmen: `outputs/OrkanSlicer_0.8.184_Honor-App.png`, `outputs/OrkanSlicer_0.8.184_Honor-Interface.png`, `outputs/OrkanSlicer_0.8.184_Honor-Druckerprofile.png`; aktuelle SliceBeam-Referenz: `outputs/SliceBeam_live_Honor.png`. Bericht: `outputs/OrkanSlicer_0.8.184_Honor-Menü-Bericht.md`.

## Aktueller geprüfter Stand — 08.10.2026 — Build 0.8.183

- SliceBeam 1.1.0 wurde direkt auf dem HONOR PTP-N49 mit Android 16 geprüft. Die fünf Bereiche Vorschau, Druck, Filament, Drucker und App sowie die geschlossenen Einstellungsgruppen und die feste Speichern-Leiste bleiben erhalten. In OrkanSlicer zählte die obere Navigation den Android-Statusleistenabstand doppelt; dadurch begann das Profilmenü 136 Bildpunkte tiefer als in SliceBeam. Der doppelte Abstand ist entfernt. Die Profilwahl sitzt nun unmittelbar unter den Hauptbereichen, und auf dem Honor sind mehr Einstellungsgruppen sichtbar.
- Die App wurde auf dem Honor installiert und durch alle fünf Bereiche navigiert. Vorschau, Druck, Filament, Drucker und App wurden visuell geprüft; die aktive Profilwahl und Druckerprofile blieben erhalten. Kein Drucker wurde angesprochen.
- Quellbuild erfolgreich: 354/354 Android-Unit-Tests und 10/10 Android-16-Gerätetests auf dem Honor. Kotlin-/UI-Migrationsaudit (309 Produktions-/92 Kotlin-Testquellen), Profilrouten (473 Einstellungen/99 deutsche Gruppen), APK-ZIP-CRC und `git diff --check` bestanden. Die APK enthält 58 ARM64-Bibliotheken einschließlich `libslic3r.so`.
- Debug-APK: `outputs/OrkanSlicer_0.8.183_debug.apk` (154.232.744 Byte; SHA-256 `F9B0E673B946AEB44C8FAEE7B681123351073CE5A33F1D4073D568EFE80AD452`), `versionCode 218`, `targetSdk 36`. Honor-Aufnahmen: `outputs/OrkanSlicer_0.8.183_Honor-menu.png`, `outputs/OrkanSlicer_0.8.183_Honor-filament.png`, `outputs/OrkanSlicer_0.8.183_Honor-printer.png`, `outputs/OrkanSlicer_0.8.183_Honor-app.png`; SliceBeam-Referenzen: `outputs/SliceBeam_1.1.0_Honor-Preview.png` und weitere passende Aufnahmen. Bericht: `outputs/OrkanSlicer_0.8.183_Honor-Menü-Bericht.md`.

## Aktueller geprüfter Stand — 08.10.2026 — Build 0.8.181

- Die Honor-Ansicht wurde nach dem direkten Vergleich mit SliceBeam 1.1.0 angepasst. Die App verwendet 100 % als Standard für die zusätzliche Menüschrift-Skalierung und beachtet weiterhin die Android-Schriftvergrößerung. Die fünf unteren Arbeitsmenüs verwenden 14-sp-Beschriftungen ohne Wortumbruch; „Anpassen“ wurde zu „Ändern“ verkürzt, sodass der Name auf dem Honor vollständig in einer Zeile bleibt.
- Ein positiver Hinweis „Alle Änderungen gespeichert“ ist nicht in den App-Ressourcen enthalten. Der Status für Profiländerungen erscheint nur bei offenen Änderungen; der geprüfte saubere Zustand zeigt keinen Speicherhinweis.
- Die Reinigungsturm-Bedienung ist vorhanden: Ziehen auf der Platte setzt die X/Y-Position für die aktive Platte, Antippen öffnet den genauen X/Y-Editor. Unit-Tests decken den großen Touch-Bereich, das Beibehalten des gegriffenen Punkts und die Begrenzung auf die Druckplatte ab. Ein echter Turm ließ sich auf dem Honor nicht live bewegen, weil kein Mehrfarbenmodell geladen war.
- Der vollständige arm64-Quellbuild und der finale UI-Build waren erfolgreich. 349/349 Android-Unit-Tests und 10/10 Android-16-Gerätetests bestanden. Kotlin-/UI-Audit (307 Produktions-/91 Unit-Testquellen), Profilrouten (473 Einstellungen/99 deutsche Gruppen), APK-CRC und `git diff --check` bestanden. Version 0.8.181 (`versionCode 216`, `targetSdk 36`) wurde auf dem HONOR PTP-N49 installiert; Vorschau, Druckmenü und Einstellungsansicht wurden visuell geprüft. App-Prozess blieb aktiv; keine Crash-, ANR- oder OOM-Meldung im geprüften Logcat. Kein Drucker wurde angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.181_HonorUI.apk` (156.333.255 Byte; SHA-256 `F345359421EDF3200A4C06C6763753D7366A5C7ADB022801067FA79FE1749171`). Honor-Bilder: `outputs/honor-0.8.181-final.png`, `outputs/honor-0.8.181-workspace.png` und `outputs/honor-0.8.181-app-settings.png`. Bericht: `outputs/OrkanSlicer_0.8.181_HonorUI-Bericht.md`.

## Aktueller geprüfter Stand — 08.10.2026 — Build 0.8.180

- SliceBeam 1.1.0 wurde direkt auf dem HONOR PTP-N49 (Android 16) mit OrkanSlicer verglichen. Beide Apps zeigen oben Vorschau, Druck, Filament, Drucker und App; darunter bleiben die fünf kontextbezogenen Arbeitsmenüs in derselben Reihenfolge. Druckgruppen starten geschlossen. Die deutsche Werkzeugregisterkarte „Objekt“ wurde in „Anpassen“ umbenannt, damit Verschieben, Drehen, Skalieren und weitere Modellwerkzeuge verständlicher eingeordnet sind; die Beschriftung ist auf dem Honor vollständig sichtbar.
- Vollständiger arm64-Quellbuild erfolgreich: 349/349 Android-Unit-Tests und 10/10 Android-16-Gerätetests auf dem Honor. Kotlin-/UI-Audit (307 Produktions-/91 Unit-Testquellen), Profilrouten (473 Einstellungen/99 deutsche Gruppen) und `git diff --check` bestanden. Version 0.8.180 (`versionCode 215`, `targetSdk 36`) wurde installiert; die Vorschau, „Anpassen“ samt geöffneten Werkzeugen und das Druckmenü wurden visuell geprüft. Der App-Prozess blieb aktiv, im geprüften Logcat gab es keinen Crash oder ANR. Kein Drucker wurde angesprochen; ein Xiaomi war nicht in ADB sichtbar.
- Debug-APK: `outputs/OrkanSlicer_0.8.180_MenuNavigation.apk` (154.232.745 Byte; SHA-256 `7D0E5B2D47A9A0B5374D4EDF9B95E993F5057C868B7C5CA3E3CD294E0F63D14B`). Screenshots und vollständiger Bericht: `outputs/OrkanSlicer_0.8.180/` und `outputs/OrkanSlicer_0.8.180_MenuNavigation-Bericht.md`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.177

- OrkanSlicer 0.8.177 (`versionCode 212`, `targetSdk 36`) wurde auf dem HONOR PTP-N49 mit Android 16 installiert und gestartet. Der Einrichtungsassistent verarbeitet Zurück über den AndroidX-Dispatcher; der Compose-Gerätetest sichert den Schritt zurück von Seite zwei auf Seite eins.
- Slicebeam 1.1.0 wurde auf demselben Honor direkt verglichen. Beide Apps zeigen oben Vorschau, Druck, Filament, Drucker und App. OrkanSlicer behält darunter die kontextbezogenen Arbeitsbereichsaktionen. Die Menüschriftgröße steht im geprüften Honor-Profil auf 110%. Der Hinweis „Alle Änderungen gespeichert“ fehlt in den App-Ressourcen. Die vorhandene Reinigungsturm-Bedienung ermöglicht Ziehen und X/Y-Positionierung; die Geometrie- und Grenzlogik ist getestet, ein Live-Turm war mangels geladenem Mehrfarbenmodell nicht verfügbar.
- Gradle-Sammellauf erfolgreich: 347/347 Android-Unit-Tests und 10/10 Android-16-Gerätetests. Kotlin-/UI-Audit (305 Produktions-/90 Testquellen), Profilrouten (473 Einstellungen/99 deutsche Gruppen), APK-Archivprüfung und `git diff --check` bestanden. Kein App-Absturz oder ANR im Honor-Protokoll; kein Drucker wurde angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.177_SetupBack.apk` (154.216.374 Byte; SHA-256 `004E285AC46953BA466048285A9FE2804CDC533AFB5ABE81F4C651119CD939EA`). Bericht und Vergleichsbilder: `outputs/OrkanSlicer_0.8.177_SetupBack-Bericht.md`, `outputs/Honor-Slicebeam-1.1.0-main.png`, `outputs/Honor-OrkanSlicer-0.8.177-main.png` und `outputs/Honor-OrkanSlicer-0.8.177-appmenu.png`. ADB fand nur das Honor, kein Xiaomi.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.174

- SliceBeam 1.1.0 wurde direkt auf dem HONOR PTP-N49 geprüft. Die fünf Hauptbereiche oben, die Druckplatte als Arbeitsfläche und die kontextabhängige Werkzeugleiste unten bleiben in derselben Reihenfolge. In OrkanSlicer ist die aktive Registerkarten-Beschriftung jetzt in der Akzentfarbe; der untere Menüpunkt „Objekt“ bleibt auf dem Telefon vollständig lesbar und öffnet die Objektwerkzeuge.
- Das Menü „Druck“ enthält die zwei fehlenden Orca-Glättungsoptionen „Fester Glättwinkel“ und „Glättungsausdehnung“. Die Lüftergeschwindigkeit beim Glätten bleibt zentral beim Filament und wird nicht doppelt angelegt.
- Vollständiger Sammellauf erfolgreich: 342/342 Android-Unit-Tests, 99/99 Headless-/Geometrietests und 9/9 Android-16-Instrumentierungstests. Profilrouten (473 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-Audit (305 Produktions-/89 Testquellen), Connector- und ARM64-Prebuilt-Audits (63/63), Druck-/Upload-/Filament-/G-Code-/Lithophan-/Fastener-Regressionen, Release-Lint (0 Fehler/390 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der aus Quellcode geprüfte ARM64-Cache enthält 54 Bibliotheken.
- Die Endfassung wurde nach der kurzen Beschriftungsanpassung erneut gebaut, auf dem Honor installiert und visuell geprüft; der App-Prozess blieb aktiv. Es wurde kein Drucker angesprochen. Xiaomi war bei diesem Lauf nicht verbunden.
- Debug-APK: `outputs/OrkanSlicer_0.8.174_debug.apk` (156.170.719 Byte; SHA-256 `675AC5DB7F8AB4F1833A48A8AEAE133DA89D15A07B41D12468E632674690C0FC`). Release-APK: `outputs/OrkanSlicer_0.8.174_release_unsigned.apk` (146.670.583 Byte; SHA-256 `87B1B6BF546BC24351C154D02EC24357430B692BA7BAC121985DA7377B194D48`), ohne Release-Keystore unsigniert. Screenshots und Bericht: `outputs/sliceBeam-reference-home.png`, `outputs/OrkanSlicer_0.8.174-final-ui.png`, `outputs/OrkanSlicer_0.8.174-object-menu.png` und `outputs/OrkanSlicer_0.8.174_Buildbericht.md`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.173

- Die SliceBeam-ähnliche Hauptnavigation mit Vorschau, Druck, Filament, Drucker und App bleibt erhalten. Die App-Einstellungen reagieren jetzt auf kurze Querformatfenster: vier geschlossene Bereiche stehen in zwei Spalten; „Maker öffnen“ und „Zurücksetzen“ bleiben direkt sichtbar. Im Hochformat bleibt die große einspaltige Akkordeonansicht bestehen. Ein geöffneter Bereich blieb beim Wechsel der Ausrichtung erhalten.
- Der vollständige Sammellauf bestand 341/341 Android-Unit-Tests, 99/99 Headless-/Geometrietests und 9/9 Android-16-Instrumentierungstests. Kotlin-/UI-Audit (305 Produktionsquellen/89 Testquellen, keine AndroidView-Brücken), Profilrouten (471 Einstellungen/91 Gruppen), Cut-Connector- und ARM64-Prebuilt-Audits (63/63), Java/Kotlin-Interop, Druck-/Upload-/Filament-/G-Code-, Lithophan- und Fastener-Regressionen, Release-Lint (0 Fehler/390 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der Quellfingerabdruck des ARM64-Caches wurde bestätigt; 54 Bibliotheken.
- Die Debug-App 0.8.173 (`versionCode 208`, `compileSdk 36`, `targetSdk 36`, `minSdk 23`) wurde auf dem HONOR PTP-N49 / Magic7 Pro mit Android 16 installiert. Hoch-/Querformat, Gruppen und Drehzustand wurden visuell geprüft; zusätzlich bestanden alle 9 Gerätetests. Der App-Prozess blieb aktiv und kein Drucker wurde angesprochen. Die ADB-Drehautomatik steht wieder auf den vorherigen Werten. Das Xiaomi war in diesem Lauf nicht verbunden.
- Debug-APK: `outputs/OrkanSlicer_0.8.173_debug.apk` (154.774.667 Byte; SHA-256 `30A7ED41009117491720B1E55478253BEE8C8EB385712CF761BB09EE24CC7659`). Release-APK: `outputs/OrkanSlicer_0.8.173_release_unsigned.apk` (146.670.583 Byte; SHA-256 `0A94B5DA8E47A3CAFEBE581236C9C6511D688C5C9DD272497B8CA521E4E944AC`), ohne Release-Keystore unsigniert. Screenshots, Protokoll und Bericht: `outputs/OrkanSlicer_0.8.173/` und `outputs/OrkanSlicer_0.8.173_Buildbericht.md`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.171

- OrkanSlicer 0.8.171 (`versionCode 206`, `compileSdk 36`, `targetSdk 36`, `minSdk 23`) wurde mit der installierten SliceBeam-1.1.0-Oberfläche auf dem HONOR PTP-N49 verglichen. Die fünf Hauptbereiche bleiben oben; die Arbeitsbereichs-Menüs lauten unten Datei, Kamera, Ändern, Optionen und Slicen. „Ändern“ bleibt auf dem schmalen Telefon vollständig sichtbar. Die Profilseiten verwenden große, anfangs geschlossene Gruppen und einen festen Speichern-/Zurücksetzen-Bereich.
- Im Druckprofil bleibt die Filamentpalette T0–T3 sichtbar. Turmposition, Spülvolumen und ACE-Synchronisierung liegen jetzt in einem geschlossenen Abschnitt „Material und Spülung“; so erscheinen auf dem Telefon mehr Druckeinstellungsgruppen ohne Funktionsverlust. Der Abschnitt öffnet und schließt sich, seine Werkzeuge bleiben darin bedienbar.
- Vollständiger Quellbuild erfolgreich: 332/332 Android-Unit-Tests, 99/99 Headless-/Geometrietests und 9/9 Android-16-Instrumentierungstests. Kotlin-/UI-Audit, Profilrouten (471 Einstellungen/91 Gruppen), Connector- und Prebuilt-Audits (63/63), Java/Kotlin-Interop, Druck-/Upload-/Filament-/G-Code-, Lithophan- und Fastener-Regressionen, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. ARM64 wurde aus Quellcode gebaut; der geprüfte Cache enthält 54 Bibliotheken. Release-Lint: 0 Fehler/386 Warnungen. Gradle-Sammellauf: 23:54 Minuten.
- Eine abschließende visuelle Korrektur an den Texten wurde mit dem verifizierten Quellcache neu verpackt (1:34 Minuten). Die Endfassung wurde installiert und auf dem Honor geöffnet. Keine Crash-/ANR-/OOM-Meldung; kein Drucker wurde angesprochen. Screenshots: `outputs/OrkanSlicer_0.8.171/honor-preview-final.png`, `honor-print-collapsed-final.png` und `honor-print-expanded-final.png`.
- Debug-APK: `outputs/OrkanSlicer_0.8.171_debug.apk` (156.301.655 Byte; SHA-256 `36BC6B5E46E587A83A320EC1EE81AA03A2319716C7E56BB5250752C25C32E83E`). Release-APK: `outputs/OrkanSlicer_0.8.171_release_unsigned.apk` (146.670.727 Byte; SHA-256 `8B343FE0A249EE6FA07937994AFB4904B2F16AD2854EEC64AF678976690B915A`); ohne Release-Keystore unsigniert. Vollständiger Bericht: `outputs/OrkanSlicer_0.8.171_Buildbericht.md`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.170

- OrkanSlicer 0.8.170 (`versionCode 205`, `compileSdk 36`, `targetSdk 36`, `minSdk 23`) passt die Profilmenüs an große Displays an: ab 840 dp und bei normaler Schrift zeigt die Druck-, Filament- und Druckerseite links eine Kategorienavigation und rechts nur die ausgewählte Einstellungsgruppe. Bei schmaleren Fenstern oder vergrößerter Schrift bleibt die bisherige Handy-Akkordeonansicht bestehen. Der Auswahlzustand klappt jeweils nur die aktive Tablet-Kategorie auf; untergeordnete Gruppen und deren Werte bleiben derselben Kategorie zugeordnet.
- Sammelbuild erfolgreich: 332/332 Android-Unit-Tests, 99/99 Headless-/Geometrietests und 9/9 Android-16-Instrumentierungstests auf dem HONOR PTP-N49. Kotlin-/UI-Migrationsaudit, Profilrouten (471 Optionen/91 deutsche Gruppen), Connector- und Native-Prebuilt-Audits (63/63), Java/Kotlin-Interop, Maker-, Upload-, G-Code-, Lithophan- und Fastener-Regressionen, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. ARM64 wurde aus Quellcode gebaut; Cache mit 54 Bibliotheken aktualisiert. Release-Lint: 0 Fehler/386 Warnungen. Gradle-Sammellauf: 24:04 Minuten.
- Die Telefonansicht wurde nach Installation auf dem Honor erneut geprüft und bleibt unverändert. Die neue Zweispaltenansicht ist über Breakpoint- und Kategoriezuordnungstests abgesichert; ein physisches Tablet war für eine zusätzliche visuelle Prüfung nicht angeschlossen. Kein Drucker wurde angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.170_debug.apk` (154.199.582 Byte; SHA-256 `DC2A3DF53BA6B24F6CE572F40B0765CD89F6F132FBE475A1188DFE950B46AB22`). Release-APK: `outputs/OrkanSlicer_0.8.170_release_unsigned.apk` (146.669.775 Byte; SHA-256 `CA2BF1E8EC04161CABFBAC1952D639226D78670D21A7C668609480D649F652B0`); ohne Release-Keystore unsigniert. Bericht und Honor-Prüfbild: `outputs/OrkanSlicer_0.8.170_Buildbericht.md` und `outputs/OrkanSlicer_0.8.170/honor-preview.png`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.169

- OrkanSlicer 0.8.169 (`versionCode 204`, `compileSdk 36`, `targetSdk 36`, `minSdk 23`) übernimmt SliceBeams klare Profilnavigation: Druck-, Filament- und Druckerkategorien sind jetzt große, standardmäßig geschlossene Akkordeons. Untergruppen und Einstellungen öffnen sich stufenweise. Suchtreffer behalten ihre Kategorie und Gruppe; der geöffnete Zustand bleibt während der Sitzung je Profil getrennt gespeichert. Suche, Expertenfilter, Profilwechsel und der feste Speichern-/Zurücksetzen-Footer bleiben erhalten.
- Die SliceBeam-1.1.0-Oberfläche wurde direkt auf dem HONOR PTP-N49 verglichen. Die fünf Hauptbereiche Preview, Print, Filament, Printer und App bleiben sichtbar getrennt; die Einstellungsseiten starten ruhiger und lassen sich mit großen Zeilen bedienen. Der angezeigte Android-Prozess blieb aktiv, ohne Crash, ANR oder OOM.
- Vollständiger Sammellauf bestanden: 330/330 Android-Unit-Tests, 99/99 Headless-/Geometrietests und 9/9 Android-16-Instrumentierungstests auf dem HONOR PTP-N49. Kotlin-/UI-Migrationsaudit (302 App-Produktionsquellen/86 Kotlin-Unit-Tests), Profilrouten (471 Einstellungen/91 deutsche Gruppen), Connector- und ARM64-Prebuilt-Audits (63/63), Java/Kotlin-Interop, Druck-/Upload-/Filament-/G-Code-/Lithophan-/Fastener-Regressionen, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der ARM64-Quellcache enthält 54 geprüfte Bibliotheken. Release-Lint: 0 Fehler/385 Warnungen.
- Debug-APK: `outputs/OrkanSlicer_0.8.169_debug.apk` (154.183.196 Byte; SHA-256 `8F9319792060D6DE99972917397699E16DE004F0FFFF63B724709CE3AA20EA39`). Release-APK: `outputs/OrkanSlicer_0.8.169_release_unsigned.apk` (146.653.391 Byte; SHA-256 `E23BE400FC9300DBDB76A96A31303F6A716E6798196CAFB6316A5F3FA353FBFA`); ohne Release-Keystore unsigniert. Bericht und Prüfbilder: `outputs/OrkanSlicer_0.8.169_Buildbericht.md` und `outputs/OrkanSlicer_0.8.169/`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.168

- OrkanSlicer 0.8.168 (`versionCode 203`, `compileSdk 36`, `targetSdk 36`, `minSdk 23`) ergänzt den Orca-Prozesswert `initial_layer_travel_jerk` im Bereich „Ruck (XY)“ der ersten Schicht. Die Einstellung wird über Orcas natives Schema und denselben Profil-/Slicingpfad verarbeitet.
- Vollständiger Sammellauf bestanden: 328/328 Android-Unit-Tests, 99/99 Headless-/Geometrietests und 9/9 Android-16-Instrumentierungstests auf dem HONOR PTP-N49. Kotlin-/UI-Migrationsaudit (302 App-Produktionsquellen und 86 Kotlin-Unit-Tests), Profilrouten (471 Einstellungen/91 deutsche Gruppen), Connector- und ARM64-Prebuilt-Audits (63/63), Java/Kotlin-Interop, Druck-/Upload-/Filament-/G-Code-/Lithophan-/Fastener-Regressionen, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der ARM64-Quellcache wurde auf 54 Bibliotheken aktualisiert. Release-Lint: 0 Fehler/385 Warnungen.
- Die Debug-App wurde mit erhaltenen App-Daten auf dem HONOR PTP-N49 installiert und gestartet. Alle fünf SliceBeam-Arbeitsbereiche wurden zuvor am Honor visuell inspiziert; die UI-Instrumentierung lief erfolgreich. Es wurde kein Drucker angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.168_debug.apk` (154.183.209 Byte; SHA-256 `6BA06F15E822C39508586648FE7D6C6BAF4AE7C9566F869A6B0F0BE2BD170721`). Release-APK: `outputs/OrkanSlicer_0.8.168_release_unsigned.apk` (146.653.391 Byte; SHA-256 `131F99545450E5C02C4C2446C70696384862EC09516349882704E0E81346C595`); ohne Release-Keystore unsigniert. Bericht und Prüfprotokoll: `outputs/OrkanSlicer_0.8.168_Buildbericht.md` und `outputs/OrkanSlicer_0.8.168/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.167

- OrkanSlicer 0.8.167 (`versionCode 202`, `compileSdk 36`, `targetSdk 36`, `minSdk 23`) gleicht die Arbeitsbereichs-Menüs an SliceBeam 1.1.0 an. Unter der Platte sind jetzt fünf direkt sichtbare Ziele: Datei, Ansicht, Ändern, Optionen und Slicen. Die bisher hinter „Mehr“ versteckten Dateiaktionen sind als eigene Kacheln unter Optionen erreichbar. Beschriftungen und Kachelbreiten wurden für das Honor-Telefon angepasst.
- Die vollständige Sammelprüfung bestand: 327/327 Android-Unit-Tests, 99/99 Headless-/Geometrietests und 9/9 Android-16-Instrumentierungstests auf dem HONOR PTP-N49. Kotlin-/UI-Migrationsaudit (302 App-Produktionsquellen und 86 Kotlin-Unit-Tests), Profilrouten (470 Einstellungen/91 deutsche Gruppen), Connector- und ARM64-Prebuilt-Audits (63/63 Importe), Java/Kotlin-Interop, Upload-/Filament-/G-Code-, Lithophan- und Fastener-Regressionen, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der Quellcode-ARM64-Cache ist aktuell und enthält 54 Bibliotheken. Release-Lint: 0 Fehler, 385 Warnungen.
- Die Debug-App wurde auf dem HONOR PTP-N49 installiert und gestartet; App-Prozess aktiv, kein Crash/ANR/OOM im Startprotokoll. Alle fünf Arbeitsbereichsmenüs wurden visuell geprüft. Die Slice-Instrumentierung nutzt eingebettete Testdaten und spricht keinen Drucker an; ein realer Druckauftrag wurde nicht gestartet.
- Debug-APK: `outputs/OrkanSlicer_0.8.167_debug.apk` (156.283.415 Byte; SHA-256 `9044938487D65D2077E8E28D38F6CEADD17F2963114CF00376421374649DF465`). Release-APK: `outputs/OrkanSlicer_0.8.167_release_unsigned.apk` (146.653.391 Byte; SHA-256 `B2CAC3A5F222CD6AB6B0408BB453CBD378F849A498ABF35EC1CB544121888117`); ohne Release-Keystore unsigniert. Bericht und Prüfprotokoll: `outputs/OrkanSlicer_0.8.167_Buildbericht.md` und `outputs/OrkanSlicer_0.8.167/verification-transcript.txt`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.166

- OrkanSlicer 0.8.166 (`versionCode 201`, `compileSdk 36`, `targetSdk 36`, `minSdk 23`) ordnet die Hauptbereiche wie SliceBeam v1.1.0 an: Vorschau, Druck, Filament, Drucker und App. Der zusätzliche Kopfbereich entfällt. Maker bleibt über „Maker öffnen“ direkt im App-Bereich erreichbar; die vorhandenen Maker-Funktionen bleiben verfügbar.
- Beim Gerätetest auf dem HONOR PTP-N49 mit Android 16 zeigte sich ein leerer Maker-Bildschirm und anschließend ein fehlerhaftes Zurückblenden. Die gemeinsame Fragmentnavigation hängt neue und wiederhergestellte Ansichten jetzt sichtbar ein und führt die Zurückanimation in die richtige Richtung.
- 327/327 Android-Unit-Tests und 9/9 Android-16-Instrumentierungstests bestanden. Kotlin-/UI-Migrationsaudit, Connectoraudit, 63/63 ARM64-Prebuilt-Prüfungen, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der aus dem Quellcode gebaute ARM64-Kern enthält 54 Bibliotheken; der Native-Cache wurde für 0.8.166 erneuert. `lintVitalRelease` war erfolgreich. Der vollständige informative `lintRelease`-Scan und die Headless-/Geometriesuite wurden in diesem gezielten UI-Lauf nicht ausgeführt.
- Die Debug-App wurde auf dem Honor installiert und gestartet. Vorschau, Druck, Filament, Drucker, App, Maker und die Rückkehr von Maker zu Einstellungen wurden visuell geprüft. Der Prozess blieb aktiv; das App-Protokoll enthielt keinen Crash, ANR oder Out-of-memory-Fehler. Es wurde kein Drucker angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.166_debug.apk` (154.253.427 Byte; SHA-256 `58301E99177EBBC5BC5050CC2A342FC066702F23936EE7F08B529A1D4B5FC910`). Release-APK: `outputs/OrkanSlicer_0.8.166_release_unsigned.apk` (146.653.015 Byte; SHA-256 `79F6FCDA9DB21136F522BE282E5B7B580B1E38548D5D5A2B2EA792B6BDD8B831`); ohne Release-Keystore unsigniert.
- Buildbericht und Prüfprotokoll: `outputs/OrkanSlicer_0.8.166_Buildbericht.md` und `outputs/OrkanSlicer_0.8.166/verification-transcript.txt`. Honor-Bilder liegen im selben Versionsordner.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.165

- OrkanSlicer 0.8.165 (`versionCode 200`, `compileSdk 36`, `targetSdk 36`, `minSdk 23`) enthält einen Fix für veraltete Profileinstellungswerte: Beim Druckerwechsel wird der Compose-Zustand für den neuen Profilnamen neu aufgebaut.
- Auf dem HONOR PTP-N49 mit Android 16 wurde Qidi Q2 0.6 → „Maschinengrenzen“ → Anycubic Kobra 3 V2 → zurück zu Qidi getestet. Angezeigt wurden unmittelbar Anycubic 255 × 260 mm und Qidi 270 × 256 mm. Qidi blieb nach Kaltstart ausgewählt; die Modellseite startete ohne geladenes Objekt. App-Daten blieben bei der Installation erhalten; kein Drucker wurde angesprochen.
- Der Speichervorgang wurde manuell am Honor mit Qidi-Z-Offset geprüft: 0 → 0,1 mm speichern und in der Profildatei verifizieren, anschließend 0 zurückspeichern und erneut verifizieren. Der Testwert blieb nicht zurück.
- 325/325 Android-Unit-Tests, 99/99 Headless-/Geometrietests und 9/9 Android-16-Instrumentierungstests bestanden. Profilrouten (470 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-/Connector-Audits, Java/Kotlin-Interop, Upload-/G-Code-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, 63/63 native APK-Importe, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der native ARM64-Kern wurde aus dem Quellcode gebaut (54 Bibliotheken). `lintVitalRelease` erfolgreich; der vollständige informative `lintRelease`-Scan wurde wegen des zuvor hängenden Windows-Quelldatei-Scans nicht ausgeführt.
- Debug-APK installiert und gestartet; Prozess blieb aktiv, kein Crash/ANR im geprüften Start. Die Profilseiten wurden visuell geprüft. Das Ziehen eines real gerenderten Reinigungsturms war mangels geladenem Mehrfarbenmodell nicht Teil dieses Gerätetests.
- Debug-APK: `outputs/OrkanSlicer_0.8.165_debug.apk` (154.180.999 Byte; SHA-256 `5C69543D82AF64F8D46DD529698A0B8FF4A605E93736192DDDDD646B65A5402F`). Release-APK: `outputs/OrkanSlicer_0.8.165_release_unsigned.apk` (146.651.179 Byte; SHA-256 `942F0FF7FFE87C84735B1EBC8B51F866FDDA717DDDF680BB40985521F66C1358`); ohne Release-Keystore unsigniert.
- Buildbericht und Protokoll: `outputs/OrkanSlicer_0.8.165_Buildbericht.md` und `outputs/OrkanSlicer_0.8.165/verification-transcript.txt`. Honor-Bilder: `outputs/honor-0.8.165-home.png`, `outputs/honor-0.8.165-qidi.png`, `outputs/honor-0.8.165-anycubic.png` und `outputs/honor-0.8.165-cold-home.png`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.160

- App: OrkanSlicer 0.8.160 (`versionCode 195`, `minSdk 23`, `targetSdk 36`). Die Menüschrift startet jetzt mit 110%; die Einstellung bleibt zwischen 90–130% veränderbar. Die mehrzeilige Profilübersicht auf der Druckplatte ist durch einen kompakten Profilknopf ersetzt, der weiterhin Drucker-, Filament- und Druckprofil-Auswahl öffnet. Die Menübeschriftungen sind größer. Das Ziehziel des Reinigungsturms hat auf dem Telefon eine mindestens 12-mm-Touchzone; Antippen öffnet das X/Y-Fenster.
- 322/322 Android-Unit-Tests, 99/99 Headless-/Geometrietests und 9/9 Android-16-Instrumentierungstests auf dem HONOR PTP-N49 bestanden. Dazu bestanden: Release-Lint (0 Fehler/379 Warnungen), Kotlin-/UI-/Connector- und 63/63 Native-Prebuilt-Audits, Profilrouten (470 Einstellungen/91 deutsche Gruppen), Java/Kotlin-Interop, Upload-/G-Code-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, beide Demo-Builds, APK-Metadaten, ZIP-CRC und `git diff --check`. Der ARM64-Nativkern wurde passend zu 0.8.160 aus Quellcode gebaut; der geprüfte Cache enthält 54 Bibliotheken.
- Die Debug-APK wurde mit erhaltenen App-Daten auf dem Honor Magic7 Pro installiert und gestartet. Modellstart, Druckeinstellungsseite, kompakter Profilknopf, Profilauswahl und X/Y-Dialog wurden visuell geprüft. Prozess aktiv; kein Crash oder ANR im aktuellen Logcat. Es war kein mehrfarbiges Modell mit aktivem Reinigungsturm geladen; Draggen am gerenderten Turm wurde daher über Touch-Geometrie-/Interaktionslogik und Regressionstests geprüft, nicht durch Ziehen an einem realen Modell. Kein Drucker wurde angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.160_debug.apk` (154.163.002 Byte; SHA-256 `FBDF8D4AF2D5603C7E55C1F3F2FFFB24017D89870A82304647ADEBB9349ABA4D`). Release-APK: `outputs/OrkanSlicer_0.8.160_release_unsigned.apk` (146.649.567 Byte; SHA-256 `DA7905A895DFF09EDB9B0B71D5418E1270A2FA41DC86A46E76AA2479209439C3`); ohne Release-Keystore unsigniert.
- Bericht und Protokoll: `outputs/OrkanSlicer_0.8.160_Buildbericht.md` und `outputs/OrkanSlicer_0.8.160/verification-transcript.txt`. Honor-Bilder: `outputs/honor-0.8.160-home-final.png`, `outputs/honor-0.8.160-print.png`, `outputs/honor-0.8.160-profile-sheet.png` und `outputs/honor-0.8.160-tower-dialog.png`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.158

- App: OrkanSlicer 0.8.158 (`versionCode 193`, `minSdk 23`, `targetSdk 36`, Android 16). Die Honor-Telefonoberfläche startet jetzt mit 100% Menüschrift; die Einstellungsseite bietet dauerhaft 90–130%, wobei Androids Barrierefreiheitsskalierung zusätzlich gilt. Lange Druckerprofile umbrechen zweizeilig, die Suchbeschriftung ist verkürzt, und die vier Arbeitsbereichsaktionen erhalten eine an die Bildschirmbreite angepasste Kachelbreite. Der nicht benötigte Hinweis „Alle Änderungen gespeichert“ ist aus den Ressourcen entfernt; es gibt keinen sichtbaren Hinweis ohne offene Änderungen. Der Reinigungsturm bleibt direkt verschiebbar; Antippen öffnet X-/Y-Felder für die aktive Platte und die Position wird auf dem Druckbett gehalten.
- 321/321 Android-Unit-Tests, 99/99 Headless-E2E-Tests und 9/9 Android-16-Instrumentierungstests auf dem HONOR PTP-N49 bestanden. Zusätzlich bestanden: Release-Lint (0 Fehler/380 Warnungen), Kotlin-/UI-/Connector-Audits, 63/63 Native-Prebuilt-Audits, Profilrouten (470 Einstellungen/91 deutsche Gruppen), Java/Kotlin-Interop, Upload-/G-Code-/Filament-/Anycubic-/Lithophan-/Befestiger-Regressionen, beide Demo-Builds, APK-Metadaten, ZIP-CRC und `git diff --check`.
- Die Debug-APK wurde installiert und gestartet. Die App blieb aktiv, das aktuelle Prozessprotokoll enthielt keinen Crash oder ANR. Auf dem Gerät wurden Startbildschirm und Druckerprofilseite visuell geprüft: „Erweitert“, „Anordnen“, Suche und Profilname sind ohne abgeschnittene Beschriftungen erkennbar. Es war kein Modell mit Reinigungsturm geladen; dessen Ziehen wurde daher per vorhandenen Geometrietests und Quell-/Interaktionsverdrahtung geprüft, nicht per realem Touch auf einem Modell.
- Debug-APK: `outputs/OrkanSlicer_0.8.158_debug.apk` (154.724.768 Byte; SHA-256 `3F2FAF8948C1B1A6FC856A82663008F31160719ECA6336ED418AE602CE461980`). Release-APK: `outputs/OrkanSlicer_0.8.158_release_unsigned.apk` (146.649.663 Byte; SHA-256 `7F62E02B9BFCBD344663B679ED6B5F6BFF0C3D8FCA1907B76FCBAE99D04F8305`); ohne Release-Keystore unsigniert.
- Bericht/Protokoll und Honor-Bildschirmfotos: `outputs/OrkanSlicer_0.8.158_Buildbericht.md`, `outputs/OrkanSlicer_0.8.158/verification-transcript.txt`, `outputs/honor-0.8.158-home.png` und `outputs/honor-0.8.158-printer.png`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.157

- App: OrkanSlicer 0.8.157 (`versionCode 192`, `minSdk 23`, `targetSdk 36`, Android 16). Der gemeinsame Theme-Host ist jetzt selbst ein `AbstractComposeView`; die zusätzliche `FrameLayout`- und verschachtelte `ComposeView`-Ebene entfällt auf den betroffenen Compose-Bildschirmen. Theme-Invalidierung und Inhaltszustand bleiben erhalten.
- Vollständiger ARM64-Quellbuild; der daraus extrahierte Cache enthält 54 geprüfte Bibliotheken. 319/319 Android-Unit-Tests, 99/99 Headless-E2E-Tests und 9/9 Android-16-Instrumentierungstests auf dem verbundenen PTP-N49 bestanden. Zusätzlich bestanden: Kotlin-/UI-, Connector- und 63/63 Native-Prebuilt-Audits, Profilrouten (470 Einstellungen/91 deutsche Gruppen), Java/Kotlin-Interop, Druck-/Upload-/G-Code-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, beide Demo-Builds, APK-Metadaten, ZIP-CRC und `git diff --check`. Release-Lint: 0 Fehler/377 Warnungen.
- Der direkte Compose-Host wurde im Android-Setup-Test einschließlich Theme-Invalidierung und Activity-Neuerstellung geprüft. Die Debug-App wurde mit bestehenden App-Daten auf `einem angeschlossenen Honor-Gerät` installiert und gestartet; der Prozess blieb aktiv und es gab keinen Crash oder ANR. ADB identifiziert das Telefon als HONOR PTP-N49 (Android 16), nicht als Xiaomi. Es wurde kein Drucker angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.157_debug.apk` (154.680.492 Byte; SHA-256 `31F93731F6EE728CC99F82D759356F7465048278489B00D7739E09F1F0C244DA`). Release-APK: `outputs/OrkanSlicer_0.8.157_release_unsigned.apk` (146.647.315 Byte; SHA-256 `BCBBD05F1B734CDC6B11E8367FA71E0F633B91856BF0BBC2AB8CB06D2A3B4046`); ohne Release-Keystore unsigniert.
- Bericht und vollständiges Konsolenprotokoll: `outputs/OrkanSlicer_0.8.157_Buildbericht.md` und `outputs/OrkanSlicer_0.8.157/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.156

- Auf dem Honor Magic7 Pro wurde die Smartphone-Oberfläche kompakter gestaltet. Die Meldung „Alle Änderungen gespeichert“ entfällt, solange keine ungespeicherten Profilwerte vorliegen. Der Reinigungsturm lässt sich am tatsächlich gegriffenen Punkt verschieben; Antippen öffnet die X-/Y-Eingabe und die Position wird innerhalb der aktiven Druckplatte begrenzt.
- 319/319 Android-Unit-Tests, 99/99 Headless-E2E-Tests, 9/9 Android-16-Gerätetests, UI-/Native-/Profilrouten-Audits und vollständiger ARM64-Quellbuild bestanden. APK und Bericht: `outputs/OrkanSlicer_0.8.156*`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.155

- App: OrkanSlicer 0.8.155 (`versionCode 190`, `minSdk 23`, `targetSdk 36`, Android 16). Der Wechsel zwischen Druckern speichert jetzt je Drucker die zuletzt verwendeten Druck- und Filamentprofile und stellt sie bei der Rückkehr wieder her, solange sie noch vorhanden sind. Das verhindert, dass ein gespeichertes Qualitätsprofil wie „0,20 mm High Quality“ durch das erste kompatible Profil ersetzt wird.
- Vollständiger ARM64-Quellbuild in 23:07 Minuten; nativer Cache mit 54 Bibliotheken erneuert. 316/316 Android-Unit-Tests, 99/99 Headless-E2E-Tests und 9/9 Android-16-Instrumentierungstests auf dem Honor Magic7 Pro bestanden. Zusätzlich bestanden: Druckerwechsel-/Speichertransaktionen einschließlich Rückwechsel nach Konfigurationsreload, Profilrouten (470 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-Audit (298 Produktionsquellen/82 Kotlin-Tests), Java/Kotlin-Interop, Upload-/G-Code-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, Connector- und 63/63 Native-Prebuilt-Audits, beide Demo-Builds, APK-Metadaten, ZIP-CRC und `git diff --check`. Release-Lint: 0 Fehler/374 Warnungen.
- Auf dem Honor wurde Anycubic → Qidi Q2 0,4 mm → Anycubic tatsächlich in der App durchgetippt. Nach dem Rückwechsel zeigte die Druckseite wieder „0,20 mm High Quality“ und „Alle Änderungen gespeichert“. Nach erzwungenem App-Neustart zeigte der Startbildschirm weiterhin Anycubic und das 0,20-mm-Profil. Die App blieb aktiv; es wurde kein physischer Drucker angesprochen und kein Druckauftrag gesendet.
- Debug-APK: `outputs/OrkanSlicer_0.8.155_debug.apk` (154.159.613 Byte; SHA-256 `7233D69BB77B8D21B4D5CBA3AC353313D0EAA40D58CB48554C72F22D464ECA81`). Release-APK: `outputs/OrkanSlicer_0.8.155_release_unsigned.apk` (146.646.159 Byte; SHA-256 `5C5B7268DBC359E0F5BBB31B381C72DC53B406941134F794E0AB5F824A68F462`); ohne Release-Keystore unsigniert.
- Buildbericht, vollständiges Protokoll und Gerätescreenshots: `outputs/OrkanSlicer_0.8.155_Buildbericht.md`, `outputs/OrkanSlicer_0.8.155/verification-transcript.txt` und `outputs/OrkanSlicer_0.8.155_honor*.png`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.154

- App: OrkanSlicer 0.8.154 (`versionCode 189`, `minSdk 23`, `targetSdk 36`, Android 16). Veraltete Bezeichnungen unter dem App-Namen wurden in Deutsch, Englisch und Russisch durch eine klare Beschreibung des OrcaSlicer-Druckkerns ersetzt.
- Vollständiger ARM64-Quellbuild in 23:02 Minuten; nativer Cache mit 54 Bibliotheken erneuert. 316/316 Android-Unit-Tests, 99/99 Headless-E2E-Tests und 9/9 Android-16-Instrumentierungstests auf dem Honor Magic7 Pro bestanden. Zusätzlich bestanden: Druckerwechsel-/Speichertransaktionen, Profilrouten (470 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-Audit (297 Produktionsquellen/82 Kotlin-Tests), Java/Kotlin-Interop, Upload-/G-Code-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, Connector- und 63/63 Native-Prebuilt-Audits, beide Demo-Builds, APK-Metadaten, ZIP-CRC und `git diff --check`. Release-Lint: 0 Fehler/373 Warnungen.
- Die Debug-APK wurde mit App-Daten-Erhalt auf `einem angeschlossenen Honor-Gerät` (Honor Magic7 Pro, Android 16) installiert und gestartet. Die Modellansicht wurde visuell geprüft; die App blieb aktiv und das Crash-/ANR-Protokoll war sauber. Es wurde kein Drucker angesprochen. Druckerwechsel und Profil-Speicherung sind in Unit-/Headless-Regressionen geprüft, aber nicht durch einen echten Druckauftrag am Gerät.
- Debug-APK: `outputs/OrkanSlicer_0.8.154_debug.apk` (154.159.614 Byte; SHA-256 `7F10E3D5F5FC88B34E51B26C1A6AABF559BEC7FB3B8FAD813A478ACDFF0E07CC`). Release-APK: `outputs/OrkanSlicer_0.8.154_release_unsigned.apk` (146.629.775 Byte; SHA-256 `CA7C1BA583817016AB61E01C8627260C84832DCBE2954BC6613057413A8D4228`); ohne Release-Keystore unsigniert.
- Buildbericht, vollständiges Protokoll und Geräte-Screenshot: `outputs/OrkanSlicer_0.8.154_Buildbericht.md`, `outputs/OrkanSlicer_0.8.154/verification-transcript.txt` und `outputs/OrkanSlicer_0.8.154_honor.png`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.152

- App: OrkanSlicer 0.8.152 (`versionCode 187`, `minSdk 23`, `targetSdk 36`, Android 16). Der Skalierungseditor verwendet jetzt Compose mit drei gut lesbaren X/Y/Z-Linealen, Live-Vorschau, Eingabe in Prozent oder Millimetern, verknüpften Achsen und Bett-Ausrichtung. Die vorhandenen Transformations- und Auswahlereignisse bleiben erhalten; gemeinsam genutzte UI-/Render-Thread-Zwischenwerte wurden entfernt.
- Vollständiger ARM64-Quellbuild in rund 20 Minuten; nativer Cache mit 54 Bibliotheken erneuert. 314/314 Android-Unit-Tests und 99/99 Headless-E2E-Tests bestanden. Zusätzlich bestanden: Skalierungsregressionen, Druckerwechsel-/Speichertransaktionen, G-Code-/Upload-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, Profilrouten (470 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-Audit (296 Produktionsquellen/81 Kotlin-Tests), Java/Kotlin-Interop, Connector- und 63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/373 Warnungen), Demo-Builds, APK-Metadaten, ZIP-CRC und `git diff --check`.
- Android-16-Instrumentierungstests wurden kompiliert, aber nicht auf einem Telefon ausgeführt: Das Honor Magic7 Pro erscheint Windows weiterhin nur als MTP-Gerät; ADB listet kein Gerät. Kein Emulator wurde verwendet. Die offizielle HONOR-Treiberdatei wurde geprüft, aber die Installation ist noch nicht abgeschlossen.
- Debug-APK: `outputs/OrkanSlicer_0.8.152_debug.apk` (154.126.354 Byte; SHA-256 `8BA018ADF2FA92DB88611AEB55FF4DCE77B59F6405C8DA227CD7212EE1BF190E`). Release-APK: `outputs/OrkanSlicer_0.8.152_release_unsigned.apk` (146.629.291 Byte; SHA-256 `7C06B2FC215BD3514DA412CE153F705EFF84A22B15C85416768BE7CA6F20C2C3`); ohne Release-Keystore unsigniert.
- Buildbericht und Protokoll: `outputs/OrkanSlicer_0.8.152_Buildbericht.md` und `outputs/OrkanSlicer_0.8.152/verification-transcript.txt`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.151

- App: OrkanSlicer 0.8.151 (`versionCode 186`, `minSdk 23`, `targetSdk 36`, Android 16). Quick Slice speichert Änderungen jetzt als rücksetzbare Profiltransaktion: Schlägt das Schreiben fehl, werden Quick-Slice-Profil und Druckprofil-Auswahl auf den vorherigen Zustand zurückgesetzt und der Druck startet nicht mit ungesicherten Daten.
- Vollständiger ARM64-Quellbuild in 15:06 Minuten. 310/310 Android-Unit-Tests und 99/99 Headless-End-to-End-Tests bestanden, einschließlich Druckerwechsel, Profilkompatibilität und Hintergrundspeicherung. Dazu bestanden: Profilrouten (470 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-Audit (295 Produktionsquellen/80 Kotlin-Testdateien), Java/Kotlin-Interop, Upload-/G-Code-/Filament-/Anycubic-/Lithophane-/Fastener-Regressionen, Connector- und 63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/372 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check`.
- Android-16-Instrumentierung wurde kompiliert, aber nicht auf einem Gerät ausgeführt: ADB listete kein angeschlossenes und autorisiertes Telefon. Kein Emulator wurde verwendet.
- Debug-APK: `outputs/OrkanSlicer_0.8.151_debug.apk` (154.091.593 Byte; SHA-256 `4C63D9F4A2AB5E57BC07D7351BE5AB5EF91165DEF198AE441BF8BCD5270999EA`). Release-APK: `outputs/OrkanSlicer_0.8.151_release_unsigned.apk` (146.594.547 Byte; SHA-256 `90C05E7F86A9BDDEC5F9B1228097F4A80B45CB871A35838B5AB2E2C937EA6263`); ohne Release-Keystore unsigniert.
- Buildbericht und Protokoll: `outputs/OrkanSlicer_0.8.151_Buildbericht.md` und `outputs/OrkanSlicer_0.8.151/verification-transcript.txt`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.150

- App: OrkanSlicer 0.8.150 (`versionCode 185`, `minSdk 23`, `targetSdk 36`, Android 16). Im Druckdialog wird nach Auswahl eines anderen Druckers nur dann erneut gesliced und gesendet, wenn dessen Profil tatsächlich aktiviert und gespeichert wurde. Schlägt das Speichern fehl, bleibt der Dialog offen und der Auftrag wird nicht mit Druckereinstellungen eines anderen Geräts fortgesetzt.
- Vollständiger ARM64-Quellbuild in 23:13 Minuten; nativer Cache mit 54 Bibliotheken erneuert. 303/303 Android-Unit-Tests und 99/99 Headless-E2E-Tests bestanden. Zusätzlich bestanden: Profilrouten (470 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-Audit (293 Produktionsquellen/78 Kotlin-Tests), Java/Kotlin-Interop, Druckerwechsel-/Hintergrundspeicher-Regressionen, Upload-/G-Code-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, Connector- und 63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/372 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check`.
- Android-16-Instrumentierung wurde kompiliert, aber nicht auf einem Gerät ausgeführt: ADB listete kein angeschlossenes und autorisiertes Telefon. Kein Emulator wurde verwendet.
- Debug-APK: `outputs/OrkanSlicer_0.8.150_debug.apk` (154.075.191 Byte; SHA-256 `ECD8D30D85ECAE7C697B3EEDC490A42391919911A4629D7F679D5DE7F569BE59`). Release-APK: `outputs/OrkanSlicer_0.8.150_release_unsigned.apk` (146.594.547 Byte; SHA-256 `6D4B0DD29EB968C2EB17678C0E6C73EF9EB34D25642C65D01C83C5CFA1474F6D`); ohne Release-Keystore unsigniert.
- Buildbericht und Protokoll: `outputs/OrkanSlicer_0.8.150_Buildbericht.md` und `outputs/OrkanSlicer_0.8.150/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.149

- App: OrkanSlicer 0.8.149 (`versionCode 184`, `minSdk 23`, `targetSdk 36`, Android 16). Die Schnellleiste für Prime-Tower, Spülvolumen und Filament-Synchronisierung rendert direkt im gemeinsamen Compose-Profilbildschirm. Beim Wiederkehren in den Druckeinstellungen liest sie den aktuell aktiven Drucker neu ein; eine veraltete Druckerreferenz kann dadurch nicht länger die Sichtbarkeit oder das Ziel der Slot-Synchronisierung bestimmen.
- Vollständiger ARM64-Quellbuild in 22:31 Minuten; nativer Cache mit 54 Bibliotheken erneuert. 302/302 Unit-Tests und 99/99 Headless-E2E-Tests bestanden. Zusätzlich bestanden: Profilrouten (470 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-Audit (293 Produktionsquellen/78 Kotlin-Tests), Java/Kotlin-Interop, Druckerwechsel-/Hintergrundspeicher-Regressionen, Upload-/G-Code-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, Connector- und 63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/372 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check`.
- Android-16-Instrumentierung wurde kompiliert, aber nicht auf einem Gerät ausgeführt: ADB listete kein angeschlossenes und autorisiertes Telefon. Kein Emulator wurde verwendet.
- Debug-APK: `outputs/OrkanSlicer_0.8.149_debug.apk` (154.075.175 Byte; SHA-256 `6E6AC74BA9AFD5F946791946FF5EF3124C6721C02C9D3CCA1B3C7A22833A24E3`). Release-APK: `outputs/OrkanSlicer_0.8.149_release_unsigned.apk` (146.594.547 Byte; SHA-256 `88FEA14ADD8DEFFCCAD08B8BB45C520F9AE04C1DF0FAF8D6FBFC6D2A4A89D753`); ohne Release-Keystore unsigniert.
- Buildbericht und Protokoll: `outputs/OrkanSlicer_0.8.149_Buildbericht.md` und `outputs/OrkanSlicer_0.8.149/verification-transcript.txt`.

## Aktueller geprüfter Stand — 07.10.2026 — Build 0.8.148

- App: OrkanSlicer 0.8.148 (`versionCode 183`, `minSdk 23`, `targetSdk 36`, Android 16). Die Filament-Slot-Leiste wird jetzt direkt im gemeinsamen Compose-Bildschirm gezeichnet statt über eine verschachtelte Android-Ansicht. Slotbearbeitung, Löschen, Hinzufügen, Synchronisierung, deutsche Zugänglichkeitsnamen und Speichern bleiben erhalten.
- Vollständiger ARM64-Quellbuild in 22:37 Minuten; nativer Cache mit 54 Bibliotheken erneuert. 301/301 Unit-Tests und 99/99 Headless-E2E-Tests bestanden. Die gezielten Tests für Druckerwechsel und Hintergrundspeicherung bestanden ebenfalls. Dazu kamen 470 Profilrouten/91 deutsche Gruppen, Kotlin-/UI-Audit (293 Produktionsquellen/78 Kotlin-Tests), Java/Kotlin-Interop, Druck-/Upload-/G-Code-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, Connector- und 63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/372 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check`.
- Android-16-Instrumentierung wurde kompiliert, aber ein Gerätetest war nicht möglich: ADB listete kein angeschlossenes und autorisiertes Telefon. Es wurde kein Emulator verwendet.
- Debug-APK: `outputs/OrkanSlicer_0.8.148_debug.apk` (154.075.188 Byte; SHA-256 `439AB329A1B5C14C273ADBC51B7AB48E0FB4E315AF801A9727406BBFE2BB0AB3`). Release-APK: `outputs/OrkanSlicer_0.8.148_release_unsigned.apk` (146.594.547 Byte; SHA-256 `F902716258D503AC57A91C2686053204E5605EF15A29A12BE49E5A645EFFECDE`); ohne Release-Keystore unsigniert.
- Buildbericht und Protokoll: `outputs/OrkanSlicer_0.8.148_Buildbericht.md` und `outputs/OrkanSlicer_0.8.148/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.147

- App: OrkanSlicer 0.8.147 (`versionCode 182`, `minSdk 23`, `targetSdk 36`, Android 16). Der Druckerwechsel auf der Arbeitsfläche wird sofort gespeichert; kompatible Druck- und Filamentprofile sowie die Übersicht werden direkt aktualisiert. Offene Profiländerungen werden beim Verlassen der App atomar in `slic3r.ini` gesichert. Wenn der Schreibvorgang fehlschlägt, wird die vorherige Konfiguration wiederhergestellt.
- Vollständiger ARM64-Quellbuild in 22:05 Minuten; native Cache mit 54 Bibliotheken erneuert. 301/301 Unit-Tests, 99/99 Headless-E2E-Tests, 470 Profilrouten/91 deutsche Gruppen, Kotlin-/UI-Audit (293 Produktionsquellen/78 Kotlin-Tests), Java/Kotlin-Interop, Druckerwechsel-/Speichertransaktionen, Druck-/Upload-/G-Code-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, Connector- und 63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/372 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden.
- Android-16-Instrumentierungstests wurden kompiliert, aber kein Gerätetest war möglich: ADB listete kein angeschlossenes und autorisiertes Gerät. Die App wurde nicht auf einem Telefon installiert; ein Emulator wurde nicht verwendet.
- Debug-APK: `outputs/OrkanSlicer_0.8.147_debug.apk` (154.075.210 Byte; SHA-256 `831CB843177060E32B76536CEFBB30D57FA4E669CFD321F30C05334A7C0EDC6C`). Release-APK: `outputs/OrkanSlicer_0.8.147_release_unsigned.apk` (146.594.547 Byte; SHA-256 `72468480F8ED1ECC956727AD47534FF4B52C62E0BF0B4EB3F0F8CA18341758DE`); ohne Release-Keystore unsigniert.
- Buildbericht und Protokoll: `outputs/OrkanSlicer_0.8.147_Buildbericht.md` und `outputs/OrkanSlicer_0.8.147/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.146

- App: OrkanSlicer 0.8.146 (`versionCode 181`, `minSdk 23`, `targetSdk 36`, Android 16). Mehrere nacheinander bearbeitete Extruder-/Farbwerte desselben Vektorfelds bleiben jetzt gemeinsam erhalten. Vorher basierte jede Teiländerung erneut auf dem gespeicherten Originalwert und konnte eine zuvor eingegebene Düse oder Slotfarbe verwerfen. Beim Druckerwechsel werden diese Änderungen weiter im alten Profil gespeichert, bevor die neue Auswahl geschrieben wird.
- 301/301 Unit-Tests und 98/98 Headless-E2E-Tests bestanden. Ebenfalls bestanden: Profilrouten (470 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-Audit (293 App-Produktionsquellen und 78 Kotlin-Testdateien), Java/Kotlin-Interop, Druckerwechsel-/Vektor-Speichertransaktion, Upload-/Filament-/G-Code-/Anycubic-/Lithophan-/Fastener-Regressionen, Connector-Audit, 63/63 ARM64-Prebuilt-Importe, APK-Metadaten, ZIP-CRC und `git diff --check`. Der vollständige ARM64-Quellbuild dauerte 22:34 Minuten und erneuerte den Cache mit 54 Bibliotheken. Release-Lint: 0 Fehler/372 Warnungen.
- Android-16-Instrumentierungstests wurden kompiliert. Ein Gerätetest blieb offen: `adb devices` zeigte kein angeschlossenes und autorisiertes Gerät; deshalb wurde die APK nicht auf einem Telefon installiert oder per Touch getestet. Kein Emulator wurde verwendet.
- Debug-APK: `outputs/OrkanSlicer_0.8.146_debug.apk` (154.075.202 Byte; SHA-256 `9B17C5B61150F115078E53C7C0084615FE27137E3E68741DDFA12A895039B6C3`). Release-APK: `outputs/OrkanSlicer_0.8.146_release_unsigned.apk` (146.594.547 Byte; SHA-256 `DDCA01FAB0511A5847C9BADDDEFC377907B71FC10B70703C10C4B640018BB499`); ohne Release-Keystore unsigniert. Bericht und Protokoll: `outputs/OrkanSlicer_0.8.146_Buildbericht.md` und `outputs/OrkanSlicer_0.8.146/`.

## Vorheriger geprüfter Stand — 07.10.2026 — Build 0.8.144

- App: OrkanSlicer 0.8.144 (`versionCode 179`, `minSdk 23`, `targetSdk 36`, Android 16). Drucker-, Druck- und Filament-Einstellungen werden vollständig über direkt eingebettete Compose-Zeilen gerendert. Die verschachtelten ComposeView-/AndroidView-Fallbacks in der Profil-Liste sind entfernt; der Prüfer erzwingt den direkten Zeilenvertrag. Die bereits abgesicherten Profilwechsel und Speichervorgänge bleiben erhalten.
- 299/299 Unit-Tests und 98/98 Headless-E2E-Tests bestanden. Ebenfalls bestanden: Profilrouten (470 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-Audit (294 Produktionsquellen und 78 Kotlin-Testquellen), Java/Kotlin-Interop, Druckerwechsel-/Speichertransaktionen, Upload-/Filament-/G-Code-/Lithophan-/Befestiger-Regressionen, Connector-Audit, 63/63 erwartete ARM64-Prebuilt-Importe, APK-Metadaten, ZIP-CRC und `git diff --check`. Der ARM64-Quellcache mit 54 Bibliotheken wurde für 0.8.144 erneuert und geprüft. Release-Lint: 0 Fehler/370 Warnungen.
- Android-16-Instrumentierungstests wurden kompiliert. Der reale Gerätetest bleibt offen: Windows erkennt das Honor Magic7 Pro als MTP-Mediengerät, ADB listet kein autorisiertes Telefon. Die App wurde daher nicht auf dem Telefon installiert; ein Emulator wurde für diese Runde nicht verwendet.
- Debug-APK: `outputs/OrkanSlicer_0.8.144_debug.apk` (154.592.357 Byte; SHA-256 `8FA7B9EDF0009EBF85948AB5672CFF87ADA2D62CD2A49FD65F9CE586C606B40C`). Release-APK: `outputs/OrkanSlicer_0.8.144_release_unsigned.apk` (146.594.547 Byte; SHA-256 `38D73D04B55646633499DA1B3C71C61E96BDBB173C6EF3C49C72B844858350DF`); ohne Release-Keystore unsigniert. Bericht und Protokoll: `outputs/OrkanSlicer_0.8.144_Buildbericht.md` und `outputs/OrkanSlicer_0.8.144/`.

## Vorheriger Build-Kandidat — 06.10.2026 — 0.8.140 (Geräteabnahme offen)

- App: OrkanSlicer 0.8.140 (`versionCode 175`, `minSdk 23`, `targetSdk 36`, Android 16). Setup-Seiten werden nun direkt in einem Compose-`HorizontalPager` gezeichnet; verschachtelte `ComposeView`s in `AndroidView` entfallen. Seitenerzeugung und Navigationszustand bleiben erhalten.
- Der vollständige ARM64-Quellbuild erzeugte die Debug-App. 285/285 Unit-Tests, 96/96 Headless-E2E-Tests, Profilrouten (470 Einstellungen/91 deutsche Gruppen), Kotlin-/UI-Audit (290 Kotlin-Appquellen), Connector- und 63/63 ARM64-Prebuilt-Audits sowie Release-Lint (0 Fehler/370 Warnungen) bestanden. Die Android-Instrumentierungstests wurden kompiliert. Der frühere Lauf auf dem Emulator scheiterte beim Setup-Recreation-Test (`Activity` blieb `PAUSED` statt `DESTROYED`) und brach später mit einem Android-16-`HardwareRenderer`-Finalizer-Timeout ab. Das ist noch nicht als bestanden gewertet.
- Ab jetzt erfolgt die Geräteprüfung ausschließlich auf dem Honor Magic7 Pro. Windows erkennt es als HONOR-MTP-Gerät ohne Treiberfehler, ADB listet es aber noch nicht. USB-Debugging und die RSA-Freigabe am Handy sind daher noch nötig; die App ist auf dem Honor noch nicht installiert. Die neue Touch-Instrumentierung prüft zusätzlich zwei Taps auf freier Plattenfläche und wurde nach dem letzten Testupdate erfolgreich kompiliert.
- Debug-APK: `outputs/OrkanSlicer_0.8.140_debug.apk` (154.089.902 Byte; SHA-256 `5DF121773F275F84F648532073385E48511D8CD047CC2C7A983146281B9E805B`). Release-APK: `outputs/OrkanSlicer_0.8.140_release_unsigned.apk` (146.592.859 Byte; SHA-256 `C6BA159158260479C1A46F3CF6224B72FEF82E5C2A79A1AE5452DF3C837043E7`); ohne Release-Keystore unsigniert. Beide APKs bestehen Metadaten- und ZIP-CRC-Prüfung.
- Grobe Schätzung für die eingegrenzte Kotlin-/Compose-/Stabilisierungsliste: weiterhin etwa 89%. Für den Abschluss bleiben ungefähr zwei bis vier geprüfte Etappen bzw. ein bis drei konzentrierte Arbeitstage; die Installation und Touch-/Setup-Abnahme auf dem Honor stehen aus. Vollständige Orca-Nightly-Parität ist ein eigener, größerer Umfang.
- Bericht und Protokoll: `outputs/OrkanSlicer_0.8.140_Buildbericht.md` und `outputs/OrkanSlicer_0.8.140/verification-transcript.txt`.

## Aktueller geprüfter Stand — 06.10.2026 — Build 0.8.139

- App: OrkanSlicer 0.8.139 (`versionCode 174`, `minSdk 23`, `targetSdk 36`, Android 16). Der Setup-Assistent verwendet jetzt Compose `HorizontalPager` statt ViewPager2. Seitennavigation, Wischgesten, dynamisches Laden der Profilseite sowie die Wiederherstellung der aktiven Seite nach einer Activity-Neuerstellung bleiben erhalten.
- Vollständiger ARM64-Quellbuild: 29:07 Minuten. 285/285 Android-Unit-Tests, 96/96 Headless-E2E-Tests und 9/9 Android-16-Instrumentierungstests bestanden. Kotlin-/UI-Audit (290 Kotlin-Appquellen), Profilrouten (470 Einstellungen/91 deutsche Gruppen), Java/Kotlin-Interop, Druck-/Upload-/G-Code-/Generator-/Geometrie-Regressionen, Connector-/63/63 ARM64-Prebuilt-Audits, Release-Lint (0 Fehler/370 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der Quellbuild erneuerte den geprüften Native-Cache mit 54 ARM64-Bibliotheken.
- Debug-App auf dem Android-16-x86_64-Emulator installiert und ohne Crash gestartet. ADB zeigte kein physisches Xiaomi; kein Drucker wurde angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.139_debug.apk` (154.089.964 Byte; SHA-256 `DD477AC199623FA81559FF99963D66CAD630960FAB3404F5729B9DF1C746290C`). Release-APK: `outputs/OrkanSlicer_0.8.139_release_unsigned.apk` (146.592.911 Byte; SHA-256 `4E48F3AA17484174F49EF975F9C9E861A6CB25122075096C17BE442A877BAF28`); ohne Release-Keystore unsigniert.
- Fortschrittsschätzung für die eingegrenzte Kotlin-/Compose- und Stabilisierungsliste: etwa 89%, als grobe Projektschätzung statt Ticketzählung. Für den eingegrenzten Abschluss bleiben voraussichtlich zwei bis vier geprüfte Etappen beziehungsweise ein bis drei konzentrierte Arbeitstage. Vollständige Gleichheit mit allen Orca-Nightly-Änderungen bleibt ein eigenes offenes Ziel.
- Bericht und Prüfprotokoll: `outputs/OrkanSlicer_0.8.139_Buildbericht.md` und `outputs/OrkanSlicer_0.8.139/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 06.10.2026 — Build 0.8.138

- App: OrkanSlicer 0.8.138 (`versionCode 173`, `minSdk 23`, `targetSdk 36`, Android 16). Im Setup-Assistenten verwenden die Boosty-Unterstützerzeile und die umgebende Seite nun Compose. Der vorhandene ViewPager2-Pager bleibt eingebettet und behält Navigation, Gesten, Insets und Titelanimation.
- Der gruppierte Build- und Prüfablauf bestand: 285/285 Android-Unit-Tests, 96/96 Headless-E2E-Tests und 8/8 Android-16-Instrumentierungstests. Release-Lint: 0 Fehler/370 Warnungen. Kotlin-/UI-Audit (290 Kotlin-Appquellen), Profilrouten (470 Einstellungen/91 deutsche Gruppen), Java/Kotlin-Interop, Druck-/Upload-/G-Code-/Generator-/Geometrie-Regressionen, Connector-/63/63 ARM64-Prebuilt-Audits, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Die 54 verifizierten ARM64-Bibliotheken stammen aus dem vorherigen Quellbuild; native Quellen blieben in dieser UI-Runde unverändert.
- Debug-App auf dem Android-16-x86_64-Emulator installiert und ohne Crash gestartet. ADB zeigte kein physisches Xiaomi; kein Drucker wurde angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.138_debug.apk` (154.073.475 Byte; SHA-256 `3A8E1C3CD3194B86636E9AE51D804FB74EA35408C7DFF2A7A86471C005F6C81E`). Release-APK: `outputs/OrkanSlicer_0.8.138_release_unsigned.apk` (146.592.803 Byte; SHA-256 `1E29C8032CDEA07CBDA7B6384B2C659151ED5027C2C62045184877B4868F03FC`); ohne Release-Keystore unsigniert.
- Fortschrittsschätzung für die eingegrenzte Kotlin-/Compose- und Stabilisierungsliste: etwa 88%, grobe Projektschätzung statt Ticketzählung. Für den eingegrenzten Abschluss bleiben voraussichtlich zwei bis vier geprüfte Etappen beziehungsweise ein bis drei konzentrierte Arbeitstage. Vollständige Gleichheit mit allen Orca-Nightly-Änderungen bleibt ein eigener offener Umfang.
- Bericht und Prüfprotokoll: `outputs/OrkanSlicer_0.8.138_Buildbericht.md` und `outputs/OrkanSlicer_0.8.138/verification-transcript.txt`.

## Aktueller geprüfter Stand — 06.10.2026 — Build 0.8.137

- App: OrkanSlicer 0.8.137 (`versionCode 172`, `minSdk 23`, `targetSdk 36`, Android 16). Das Startbild und der animierte Titel des Setup-Assistenten verwenden jetzt Compose; Theme-Farben, Seitenübergänge, Insets und Titelanimation bleiben erhalten. ViewPager2 und seine Gesten bleiben als bestehender Pager erhalten.
- Vollständiger ARM64-Quellbuild: 27:18 Minuten. 285/285 Unit-Tests, 96/96 Headless-E2E-Tests und 8/8 Android-16-Instrumentierungstests bestanden. Release-Lint: 0 Fehler/370 Warnungen. Kotlin-/UI-Audit (288 Kotlin-Appquellen), Profilrouten (470 Einstellungen/91 deutsche Gruppen), Java/Kotlin-Interop, Druck-/Upload-/G-Code-/Generator-/Geometrie-Regressionen, Connector-/63/63 ARM64-Prebuilt-Audits, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der Quellbuild aktualisierte den geprüften Cache mit 54 ARM64-Bibliotheken.
- Debug-App auf dem Android-16-x86_64-Emulator installiert und ohne Crash gestartet; Touch-, Slice-, Rotation-, Setup- und Wiederherstellungstests erfolgreich. ADB erkannte kein physisches Xiaomi; kein Drucker wurde angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.137_debug.apk` (154.073.410 Byte; SHA-256 `6208326104CCBE46ECEB7BEEA34867996F9923C749C059FA052ED59F7AFB9325`). Release-APK: `outputs/OrkanSlicer_0.8.137_release_unsigned.apk` (146.592.723 Byte; SHA-256 `D8532E396529D0F40040F981359AB93A1CBB087122EF88B8D87EAE2A49814B54`; ohne Release-Keystore unsigniert).
- Fortschrittsschätzung für die eingegrenzte Kotlin-/Compose- und Stabilisierungsliste: etwa 87%; grobe Projektschätzung statt Ticketzählung. Für den derzeit eingegrenzten Abschluss bleiben geschätzt zwei bis vier geprüfte Etappen beziehungsweise ein bis drei konzentrierte Arbeitstage. Vollständige Gleichheit mit allen Orca-Nightly-Änderungen ist ein eigener offener Umfang.
- Bericht und Prüfprotokoll: `outputs/OrkanSlicer_0.8.137_Buildbericht.md` und `outputs/OrkanSlicer_0.8.137/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 06.10.2026 — Build 0.8.136

- App: OrkanSlicer 0.8.136 (`versionCode 171`, `minSdk 23`, `targetSdk 36`, Android 16). Die Unterstützerseite des Setup-Assistenten verwendet jetzt Compose, zeigt Namen in einer scrollbar begrenzten Liste und übernimmt aktualisierte Serverdaten. Weiter, Schließen und externer Boosty-Link bleiben erhalten; falls Android keinen Browser öffnen kann, erscheint ein Hinweis.
- Vollständiger ARM64-Quellbuild und erste Sammelprüfung: 25:33 Minuten. Nach der abschließenden Aktualisierung der Serverdatenlogik bestand der endgültige Quellstand noch eine vollständige 14:27-Minuten-Prüfung mit dem versionstreuen, source-built Native-Cache. Insgesamt bestanden: 285/285 Unit-Tests, 96/96 Headless-E2E-Tests und 7/7 Android-16-Instrumentierungstests, darunter der neue Setup-Seitentest. Release-Lint: 0 Fehler/370 Warnungen. Kotlin-/UI-Audit (287 Kotlin-Appquellen), Profilrouten (470 Einstellungen/91 deutsche Gruppen), Java/Kotlin-Interop, Druck-/Upload-/G-Code-/Generator-/Geometrie-Regressionen, Connector-/63 ARM64-Prebuilt-Audits, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der Native-Cache enthält 54 aus dem Quellcode gebaute ARM64-Bibliotheken.
- Debug-App installiert und ohne Crash auf dem Android-16-x86_64-Emulator gestartet. ADB erkannte kein Xiaomi; kein Drucker wurde angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.136_debug.apk` (154.568.731 Byte; SHA-256 `C9D5A22218A54D3DF5692F413938261F357BF20C8207B9620F4265F5B93E67AA`). Release-APK: `outputs/OrkanSlicer_0.8.136_release_unsigned.apk` (146.576.351 Byte; SHA-256 `77812404AF5F70FCAA0A7707601597E3242606C7411B5FBA53B4998BCDB0F959`; ohne Release-Keystore unsigniert).
- Fortschrittsschätzung für die eingegrenzte Kotlin-/Compose- und Stabilisierungsliste: weiterhin etwa 87%; die Zahl ist eine grobe Projektschätzung, keine Ticketzählung. Vollständige Gleichheit mit allen Orca-Nightly-Änderungen ist ein eigener offener Umfang.
- Bericht und Prüfprotokolle: `outputs/OrkanSlicer_0.8.136_Buildbericht.md` und `outputs/OrkanSlicer_0.8.136/verification-transcript.txt`.

## Aktueller geprüfter Stand — 06.10.2026 — Build 0.8.135

- App: OrkanSlicer 0.8.135 (`versionCode 170`, `minSdk 23`, `targetSdk 36`, Android 16). Der Absturzbildschirm ist auf Compose umgestellt, passt sich dem System-Farbmodus an, hält lange Protokolle scrollbar und behält Bericht-Teilen sowie Neustart. Als `ComponentActivity` erhält er den benötigten Lifecycle; die Ansicht lädt weder Druckerprofile noch das App-Theme und kann deshalb unabhängig von diesen Fehlerquellen angezeigt werden.
- Der vollständige ARM64-Quellbuild für 0.8.135 wurde abgeschlossen. Der erfolgreiche Gruppendurchlauf dauerte 14:27 Minuten; er verwendete die im vorangegangenen Quelllauf gebauten nativen Artefakte und aktualisierte den geprüften Cache aus der Debug-APK mit 54 ARM64-Bibliotheken. 285/285 Unit-Tests, 96/96 Headless-E2E-Tests und 6/6 Android-16-Instrumentierungstests bestanden. Release-Lint: 0 Fehler/370 Warnungen. Kotlin-/UI-Audit (286 Kotlin-Appquellen), Profilrouten (470 Einstellungen/91 deutsche Gruppen), Java/Kotlin-Interop, Druck-/Upload-/G-Code-/Generator-/Geometrie-Regressionen, Connector-/63 ARM64-Prebuilt-Audits, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden.
- Die Debug-App wurde auf dem Android-16-x86_64-Emulator installiert und ohne Crash gestartet; der Bildschirm ist unter `outputs/OrkanSlicer_0.8.135/emulator-home.png` festgehalten. Der Wiederherstellungstest öffnet die Compose-Ansicht mit gespeicherter Crashmeldung ohne Lifecycle-Crash. Das Xiaomi war nicht in ADB sichtbar; es wurde kein Drucker angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.135_debug.apk` (154.568.932 Byte; SHA-256 `50ECF6BCCD85AD6527176F0812AEB32C100EA552B03F5D656292AE5EF1079324`). Release-APK: `outputs/OrkanSlicer_0.8.135_release_unsigned.apk` (146.576.443 Byte; SHA-256 `57CC617076FC1EFA3D32FC484CAFA559B1E976E62CAF135469E29D364160998E`; ohne Release-Keystore unsigniert).
- Fortschrittsschätzung für die laufende Kotlin-/Compose- und Stabilisierungsliste: etwa 87%. Die Produktionscode-Portierung nach Kotlin ist abgeschlossen; die Prozentzahl bleibt eine grobe Schätzung, weil die gesamte UI-Modernisierung keine eingefrorene Aufgabenliste hat. Eine vollständige Gleichheit mit allen Orca-Nightly-Änderungen ist ein eigener, offener Umfang.
- Bericht und Prüfprotokolle: `outputs/OrkanSlicer_0.8.135_Buildbericht.md` und `outputs/OrkanSlicer_0.8.135/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 06.10.2026 — Build 0.8.133

- App: OrkanSlicer 0.8.133 (`versionCode 168`, `minSdk 23`, `targetSdk 36`, Android 16). Die Cloud-Kontoseite im Setup wurde auf Compose umgestellt; Abo-Stufen, Vorteilshinweise, Anmelde-/Abbrechen-Zustand und externe Links bleiben erhalten. Cloud-Ereignisse aktualisieren die sichtbaren Daten. Der Kotlin-Audit zählt 284 Kotlin-Appquellen; 470 Profileinstellungen und 91 deutsche Gruppen sind geprüft.
- Vollständiger ARM64-Quellbuild: 24:55 Minuten; danach der Paket-/Regressionstest mit dem aus diesem Quellbuild aktualisierten 54-Bibliotheken-Cache. 282/282 Unit-Tests, 96/96 Headless-E2E-Tests und 4/4 Android-16-Instrumentierungstests bestanden. Release-Lint: 0 Fehler/370 Warnungen. Java/Kotlin-Interop, Druck-/Upload-/G-Code-/Profil-/Geometrie-Regressionen, Kotlin-UI-, Connector- und 63/63 Native-Prebuilt-Audits, APK-Metadaten, ZIP-CRC und `git diff --check` bestanden.
- Die Debug-App wurde auf `emulator-5554` installiert und ohne Crash gestartet. Der neue Instrumentierungstest öffnet die interne Cloud-Kontoseite innerhalb der App und bestätigt ihre Compose-Ansicht. Das Xiaomi war nicht in ADB sichtbar; ein Drucker wurde nicht angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.133_debug.apk` (154.554.683 Byte; SHA-256 `C9FF11A6D9DAEC1B545CA8E3D753BA3B6B0D8AF48325D68473889CC31A543F04`). Release-APK: `outputs/OrkanSlicer_0.8.133_release_unsigned.apk` (146.575.711 Byte; SHA-256 `0B5D5623718983CFBF14E968A4274303D2AE581A93ECD1664D2BE4FC22B1A535`; ohne Release-Keystore unsigniert).

## Vorheriger geprüfter Stand — 06.10.2026 — Build 0.8.132

- App: OrkanSlicer 0.8.132 (`versionCode 167`, `minSdk 23`, `targetSdk 36`, Android 16). Drucker- und Filamentbilder im Setup-Assistenten laufen jetzt durch einen gemeinsamen, lebenszyklusgebundenen Compose-/Glide-Loader. Bildgrößen sind begrenzt; fehlende Bilder behalten das Symbol als Fallback. Der Cloud-Profilavatar verwendet denselben Loader.
- Der vollständige ARM64-Quellbuild dauerte 25:06 Minuten. 279/279 Unit-Tests, 96/96 Headless-E2E-Tests, 3/3 Android-16-Instrumentierungstests, Java/Kotlin-Interop, Kotlin-UI-Audit (283 Kotlin-Dateien), Profilrouten (470 Einstellungen/91 deutsche Gruppen), Connector-/63/63 ARM64-Prebuilt-Audits, Druck-/Upload-/G-Code-/Geometrie-Regressions, Release-Lint (0 Fehler/374 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der Quellbuild aktualisierte den Cache mit 54 ARM64-Bibliotheken.
- Die Debug-App wurde auf `emulator-5554` installiert, gestartet und blieb ohne Crash/ANR aktiv. ADB listete nur den Emulator; das physische Xiaomi und ein Drucker wurden nicht getestet.
- Debug-APK: `outputs/OrkanSlicer_0.8.132_debug.apk` (154.007.262 Byte; SHA-256 `AFB037CB1E890F027DFEC057D7A5381DBBD5D7F8C53F8EDEE5FE6A6505A86FD4`). Release-APK: `outputs/OrkanSlicer_0.8.132_release_unsigned.apk` (146.559.331 Byte; SHA-256 `1A6F6CFABE6B97613B6BB9B6546C1CDFA171E468F31242DE6DB57146DE4AFBA4`; ohne Release-Keystore unsigniert).
- Bericht und Prüfprotokolle: `outputs/OrkanSlicer_0.8.132_Buildbericht.md`, `outputs/OrkanSlicer_0.8.132/gradle-build.log` und `outputs/OrkanSlicer_0.8.132/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 06.10.2026 — Build 0.8.131

- App: OrkanSlicer 0.8.131 (`versionCode 166`, `minSdk 23`, `targetSdk 36`, Android 16). Die Profilstatuskarte zeigt Drucker, Düse, Filament und Prozess vollständig auf bis zu vier Zeilen. Druckplattenleiste und Webbutton werden unterhalb der gemessenen Kartenhöhe positioniert und überlappen auch bei langen Profilnamen nicht.
- Der vollständige ARM64-Quellbuild dauerte 24:22 Minuten. 279/279 Android-Unit-Tests, 96/96 Headless-E2E-Tests und 3/3 Android-16-Instrumentierungstests bestanden. Auch Java/Kotlin-Interop, 470 Profilrouten/91 deutsche Gruppen, Kotlin-UI-/Connector-/54/54 Native-Prebuilt-Audits, Druck-/Upload-/G-Code-/Anycubic-/Lithophan-/Fastener-Regressionen, Release-Lint (0 Fehler/372 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden.
- Die Debug-App wurde installiert und startete auf `emulator-5554` ohne Crash. Deutsch und Englisch wurden im Hochformat visuell geprüft; die Rotation-Instrumentierung bestand, aber eine manuelle Querformataufnahme der Profilstatuskarte ist nicht verifiziert. ADB listete kein Xiaomi; ein Drucker wurde nicht angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.131_debug.apk` (154.007.263 Byte; SHA-256 `ED4E852E2E65D9320DD64E2B7503D08ED7A847D107542B8BC6B641E16C6F7E71`). Release-APK: `outputs/OrkanSlicer_0.8.131_release_unsigned.apk` (146.559.327 Byte; SHA-256 `046CD0BC09BFD18C9F855A90E8ED5F72CC24B05B360752C538371B161A099EEC`; ohne Release-Keystore unsigniert).
- Bericht und Protokoll: `outputs/OrkanSlicer_0.8.131_Buildbericht.md` und `outputs/OrkanSlicer_0.8.131/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 06.10.2026 — Build 0.8.128

- App: OrkanSlicer 0.8.128 (`versionCode 163`, `minSdk 23`, `targetSdk 36`, Android 16). Die vier Arbeitsmenü-Kategorien Datei, Bearbeiten, Ansicht und Slicen laufen jetzt über die adaptive Compose-Navigation. Menüaktionen, Slice-Aktualisierung, Zurück-Navigation und Freigabe des G-Code-Viewers bleiben verbunden; die Auswahl wird beim Neuaufbau zwischen Hoch- und Querformat erhalten.
- Der vollständige ARM64-Quellbuild dauerte 20:16 Minuten. 279/279 Android-Unit-Tests, 96/96 Headless-E2E-Tests, Java/Kotlin-Interop, 470 Profilrouten/91 deutsche Gruppen, Kotlin-UI-/Connector-/63/63 Native-Prebuilt-Audits, Datei-/Upload-/Filament-/G-Code-/Anycubic-/Lithophan-/Fastener-Regressionen, Release-Lint (0 Fehler/372 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Alle 54 ARM64-Bibliotheken wurden gebaut und der Native-Cache passend zu Version 0.8.128 validiert.
- Das Slice-/Rotations-Instrumentierungspaket wurde kompiliert, aber nicht ausgeführt. ADB erkannte weder das Xiaomi 13 noch den Android-16-Emulator; der Kaltstart des Emulators blieb offline. Es gab daher keine Geräteinstallation oder visuelle Laufzeitprüfung für Build 0.8.128.
- Debug-APK: `outputs/OrkanSlicer_0.8.128_debug.apk` (154.007.256 Byte; SHA-256 `B4F4613AF557E8EBC91708D1D7FB157D301097C458696108D4AB0D8B2996F7DD`). Release-APK: `outputs/OrkanSlicer_0.8.128_release_unsigned.apk` (146.559.339 Byte; SHA-256 `FC5C32DC4B8CBD64E75D8ED07DA4F25E02EAB45DFB813ABAE1690A2454CE275B`; ohne Release-Keystore unsigniert).
- Bericht und Protokoll: `outputs/OrkanSlicer_0.8.128_Buildbericht.md` und `outputs/OrkanSlicer_0.8.128/verification-transcript.txt`.

## Vorheriger geprüfter Stand — 05.10.2026 — Build 0.8.127

- App: OrkanSlicer 0.8.127 (`versionCode 162`, `minSdk 23`, `targetSdk 36`, Android 16). Beim Drehen während der adaptiven Navigation konnte die horizontal scrollbare Filamentpalette zeitweise ohne endliche Maximalbreite gemessen werden. Eine Breitenbegrenzung auf 1200 dp verhindert den Compose-Absturz in der Queransicht.
- Der vollständige ARM64-Quellbuild und Gradle-Lauf dauerte 31:51 Minuten. 279/279 Android-Unit-Tests, 96/96 Headless-E2E-Tests und 3/3 Android-16-Instrumentierungstests bestanden. Die Rotationstests erhalten Arbeitsbereich/Modell und prüfen Maker sowie Druck-, Filament- und Druckerprofile in Hoch- und Querformat; die offenen App-Einstellungen bleiben erhalten. Auch Touch-Coalescing für wiederholte Plattentipps bestand. Java/Kotlin-Interop, 470 Profilrouten/91 deutsche Gruppen, Kotlin-UI-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/372 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der Quellbuild aktualisierte den Cache mit 54 ARM64-Bibliotheken.
- Die Debug-App wurde auf `emulator-5554` installiert und ohne Crash gestartet. ADB listete kein Xiaomi; der physische Touchscreen-Test bleibt offen. Es wurde kein Drucker angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.127_debug.apk` (154.007.268 Byte; SHA-256 `5CFEA2BD4E6FE56162ACD3287559DCF8C9DD89E4D15F740E23FAF8CA9925AB63`). Release-APK: `outputs/OrkanSlicer_0.8.127_release_unsigned.apk` (146.559.339 Byte; SHA-256 `43608A40969AC044F9C895223529CC3779F39E82678C4488497213F586B1F992`; ohne Release-Keystore unsigniert).
- Bericht und vollständiges Prüfprotokoll: `outputs/OrkanSlicer_0.8.127_Buildbericht.md` und `outputs/OrkanSlicer_0.8.127/verification-transcript.txt`.

## Aktueller geprüfter Stand — 05.10.2026 — Build 0.8.126

- App: OrkanSlicer 0.8.126 (`versionCode 161`, `minSdk 23`, `targetSdk 36`, Android 16). Der GL-Renderer übernimmt jetzt die tatsächliche Oberflächegröße, bevor er Projektion und 3D-Trefferprüfung berechnet. Dadurch erkennt die Berührungsauswertung geladene Modelle wieder korrekt.
- Der vollständige ARM64-Quellbuild dauerte 28:33 Minuten. 279/279 Android-Unit-Tests, 96/96 Headless-E2E-Tests und 3/3 Android-16-Instrumentierungstests bestanden. Der Touchtest lädt einen echten Würfel, prüft die Renderer-/Oberflächenmaße, tippt zweimal auf das Modell und hält es gedrückt; die Aktionsleiste bleibt eingeklappt und der modale Aktionsdialog öffnet sich nicht. Java/Kotlin-Interop, 470 Profilrouten/91 deutsche Gruppen, Kotlin-UI-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/372 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der Quellbuild aktualisierte den Cache mit 54 ARM64-Bibliotheken.
- Zusatzprüfung der Compose-Oberfläche: Der App-Einstellungsbildschirm wird geöffnet, ein Einstellungsbereich ausgeklappt und derselbe Bildschirm samt geöffnetem Bereich durch Hochformat → Querformat → Hochformat gehalten. Der adaptive Navigationswechsel löst weder eine neue Activity noch einen Verlust der aktiven Ansicht aus. Der Sammelbuild mit verifiziertem Quellcache bestand erneut 279/279 Unit-Tests, 96/96 Headless-Tests und 3/3 Android-16-Instrumentierungstests; Laufzeit 1:57 Minuten.
- Die Debug-App wurde auf `emulator-5554` installiert und ohne Crash gestartet; der leere Modellarbeitsbereich und die sichtbaren Menüs wurden aufgenommen: `outputs/OrkanSlicer_0.8.126/emulator-home.png`. ADB zeigte weiterhin nur den Emulator; ein Touchscreen-Test auf dem Xiaomi bleibt offen. Es wurde kein Drucker angesprochen.
- Aktuelle Debug-APK: `outputs/OrkanSlicer_0.8.126_debug.apk` (154.525.830 Byte; SHA-256 `61DC679076057C3264F9B0206C73B198541A9A7AE2A2395A3179D5E141CE0EDB`). Release-APK: `outputs/OrkanSlicer_0.8.126_release_unsigned.apk` (146.559.339 Byte; SHA-256 `8E7B770130B12D5FF303C2F4D4F0C8F9EBA8CB7F96EBB31D9D05B8773DAD409A`; ohne Release-Keystore unsigniert).
- Bericht und vollständiges Prüfprotokoll: `outputs/OrkanSlicer_0.8.126_Buildbericht.md` und `outputs/OrkanSlicer_0.8.126/verification-transcript.txt`.

## Aktueller geprüfter Stand — 05.10.2026 — Build 0.8.125

- App: OrkanSlicer 0.8.125 (`versionCode 160`, `minSdk 23`, `targetSdk 36`, Android 16). Die zuvor fehlenden 10 Auswahl-Listen und 12 Pluralformen sind auf Deutsch ergänzt. Ein neuer Ressourcen-Audit prüft alle 992 übersetzbaren Strings, Arrays und Plurale auf Abdeckung, identische Optionsanzahl/-reihenfolge und unveränderte Formatplatzhalter.
- Der vollständige ARM64-Quellbuild dauerte 31:36 Minuten. 279/279 Android-Unit-Tests, 95/95 Headless-E2E-Tests, 2/2 Android-16-Instrumentierungstests, Java/Kotlin-Interop, Druck-/Upload-/Filament-/G-Code-/Anycubic-/Lithophan-/Fastener-Regressionen, 470 Profilrouten/91 deutsche Gruppen, Kotlin-UI-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/387 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der ARM64-Cache wurde aus den 54 geprüften Quellbibliotheken erneuert.
- Die Debug-App wurde auf dem Android-16-Emulator installiert, gestartet und visuell geprüft: `outputs/OrkanSlicer_0.8.125/emulator-home.png`. Das Xiaomi wurde von ADB nicht erkannt; der physische Touchscreen-Test steht aus. Es wurde kein Drucker angesprochen.
- Die Folgeprüfung für UI-/Lokalisierungsänderungen nutzte den verifizierten ARM64-Cache und dauerte 15:43 Minuten. Dabei wurden Profilimport-Ergebnisse, G-Code-Schichtmeldungen, ausstehende Profiländerungen und die Messansicht auf lokalisierte Mengen-/Formatressourcen umgestellt; der Audit prüft EN/DE/RU-Pluralformen und Platzhalter. Erneut bestanden 279/279 Unit-Tests, 95/95 Headless-E2E-Tests, 2/2 Android-16-Gerätetests, alle Profil-/Interop-/Upload-/Geometrie-/Paketprüfungen und Release-Lint (0 Fehler/372 Warnungen). Die APK wurde auf `emulator-5554` installiert und gestartet; der aktuelle Emulator-Screenshot ist `outputs/OrkanSlicer_0.8.125/emulator-home.png`. Das Xiaomi war nicht per ADB sichtbar.
- Aktuelle Debug-APK: `outputs/OrkanSlicer_0.8.125_debug.apk` (156.090.591 Byte; SHA-256 `C892516CB781DF679D6C4E0994BF2156CA0CCBF14FB530FA9D55DA9571599491`). Aktuelle Release-APK: `outputs/OrkanSlicer_0.8.125_release_unsigned.apk` (146.559.339 Byte; SHA-256 `634D695630E88937A0F5E2B280BFCC25F55406D6C13C557E850418E1ED2F735A`; ohne Release-Keystore unsigniert).
- Bericht und vollständiges Prüfprotokoll: `outputs/OrkanSlicer_0.8.125_Buildbericht.md` und `outputs/OrkanSlicer_0.8.125/verification-transcript.txt`.

## Aktueller geprüfter Stand — 05.10.2026 — Build 0.8.124

- App: OrkanSlicer 0.8.124 (`versionCode 159`, `minSdk 23`, `targetSdk 36`, Android 16). Ein langes Halten der Druckplatte wird beim Loslassen nicht länger als normaler Tipp behandelt. Ein kurzes Tippen bleibt unverändert erhalten. Wechsel, Kopieren, Löschen und Speichern eines Drucker-, Druck- oder Filamentprofils aktualisieren jetzt über denselben gemeinsamen Pfad die sichtbare Profilübersicht im Arbeitsbereich.
- Der vollständige ARM64-Quellbuild bestand 279/279 Android-Unit-Tests, 95/95 Headless-E2E-Tests und 2/2 Android-16-Instrumentierungstests. Java/Kotlin-Interop, G-Code-/Upload-/Filament-/Anycubic-/Lithophan-/Fastener-Regressionen, 470 Profilrouten/91 deutsche Gruppen, Kotlin-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/384 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der Buildlauf dauerte 35:14 Minuten; der ARM64-Cache wurde aus den geprüften Quellbuild-Artefakten aktualisiert.
- Die Debug-App wurde auf `emulator-5554` installiert und ohne Crash gestartet. Der deutsche Sichttest bestätigt den leeren Arbeitsbereich und die sichtbaren Druckplatten-/Menüaktionen: `outputs/OrkanSlicer_0.8.124/emulator-home.png`. Das Xiaomi war nicht per ADB sichtbar; ein Touchscreen-Test auf dem physischen Telefon steht aus. Es wurde kein Drucker angesprochen.
- Debug-APK: `outputs/OrkanSlicer_0.8.124_debug.apk` (154.002.349 Byte; SHA-256 `1978C2D39953755A35A8255DFBB06E219241AC3470F3044E55167E1407F24415`). Release-APK: `outputs/OrkanSlicer_0.8.124_release_unsigned.apk` (146.554.427 Byte; SHA-256 `15EFA44D8A21638E352C554F78B987FF37CCB85C7177FC2E23E36B2D0407582F`; ohne Release-Keystore unsigniert).
- Bericht und vollständiges Prüfprotokoll: `outputs/OrkanSlicer_0.8.124_Buildbericht.md` und `outputs/OrkanSlicer_0.8.124/verification-transcript.txt`.

## Aktueller geprüfter Stand — 05.10.2026 — Build 0.8.123

- App: OrkanSlicer 0.8.123 (`versionCode 158`, `minSdk 23`, `targetSdk 36`, Android 16). Das abgeschnittene Hochformat-Label „Automatisch anordnen“ heißt jetzt kurz „Anordnen“; „Maker“ bleibt der ausdrücklich gewünschte Bereichsname.
- Der vollständige ARM64-Quellbuild und beide APKs wurden erstellt. 277/277 Android-Unit-Tests, 95/95 Headless-E2E-Tests, 2/2 Android-16-Instrumentierungstests, Java/Kotlin-Interop, Druckdaten-/Upload-/Filament-/G-Code-/Anycubic-/Lithophan-/Fastener-Regressionen, 470 Profilrouten/91 deutsche Gruppen, Kotlin-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/384 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der ARM64-Cache enthält 54 aus dem Quellcode gebaute, geprüfte Bibliotheken. Der vollständige Gradle-Lauf dauerte 27:47 Minuten.
- Die Debug-App wurde auf `emulator-5554` installiert und ohne Absturz gestartet. Mit App-Sprache `de-DE` bestätigt der Sichttest, dass „Anordnen“ in der Hochformat-Schaltfläche lesbar bleibt. Das Xiaomi war diesmal nicht per ADB sichtbar; auf dem Telefon wurde nicht getestet.
- Debug-APK: `outputs/OrkanSlicer_0.8.123_debug.apk` (154.002.385 Byte; SHA-256 `4CB618E1ACC2C199D631D9F27AAEB59850A63D227E8BF345FAF8B9347541B9B4`). Release-APK: `outputs/OrkanSlicer_0.8.123_release_unsigned.apk` (146.554.431 Byte; SHA-256 `D0D4972C643E17BA0DEF0323285EFBA6DFD820155C02DD4B5464BAD2E376307D`; ohne Release-Keystore unsigniert).
- Bericht und vollständiges Prüfprotokoll: `outputs/OrkanSlicer_0.8.123_Buildbericht.md` und `outputs/OrkanSlicer_0.8.123/verification-transcript.txt`; der deutsche Sichttest liegt als `outputs/OrkanSlicer_0.8.123/german-home.png` vor.

## Aktueller geprüfter Stand — 05.10.2026 — Build 0.8.122

- App: OrkanSlicer 0.8.122 (`versionCode 157`, `minSdk 23`, `targetSdk 36`, Android 16). Die sichtbaren Bezeichnungen für Modell, Druck, Drucker, Platten, Ansicht, Datei und weitere Startaktionen wurden im deutschen Ressourcenbestand geklärt; sechs bislang englische oder missverständliche Begriffe sind korrigiert.
- Die normale Debug-App wurde auf dem Android-16-Emulator installiert, gestartet und ohne neuen FATAL-/ANR-Eintrag geprüft. Für den Sichttest wurde die App-Sprache auf `de-DE` gesetzt. „Maker“ bleibt als ausdrücklich gewünschter Bereichsname erhalten. Das lange Aktionslabel „Automatisch anordnen“ wurde in Build 0.8.123 auf das gut lesbare „Anordnen“ verkürzt; die vollständige deutsche Übersetzung bleibt ein laufender Arbeitspunkt. Ein Xiaomi war von ADB nicht sichtbar.
- 277/277 Android-Unit-Tests, 95/95 Headless-E2E-Tests, 2/2 Android-16-Instrumentierungstests, Java/Kotlin-Interop, Upload-/Filament-/G-Code-/Anycubic-/Lithophan-/Fastener-Regressionen, 470 Profilrouten/91 deutsche Gruppen, Kotlin-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/384 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der Rotationstest wartet jetzt auf Androids asynchrone Ausrichtungsänderung und bestätigt danach, dass Arbeitsbereich, Modell und 3D-Ansicht erhalten bleiben. Alle 54 ARM64-Nativbibliotheken wurden aus den Quellen gebaut und im geprüften Cache abgelegt.
- Debug-APK: `outputs/OrkanSlicer_0.8.122_debug.apk` (154.002.376 Byte; SHA-256 `BFB64E80772B6332A53C4423978D24199EC9E9336E831EF329D8855003F5AEC7`). Release-APK: `outputs/OrkanSlicer_0.8.122_release_unsigned.apk` (146.554.443 Byte; SHA-256 `67D67A0E0C9EBD7DD1B50638F5F2D70CCC2F3BA1B1A870AED951277E6C14C413`; ohne Release-Keystore unsigniert).
- Bericht: `outputs/OrkanSlicer_0.8.122_Buildbericht.md`; Prüfprotokoll und Emulatoraufnahmen liegen unter `outputs/OrkanSlicer_0.8.122/`, einschließlich `german-home.png`.

## Aktueller geprüfter Stand — 05.10.2026 — Build 0.8.121

- App: OrkanSlicer 0.8.121 (`versionCode 156`, `minSdk 23`, `targetSdk 36`, Android 16).
- Die Einrichtungsansicht verwendet nun den aktiven Theme-Hintergrund statt einer fest codierten dunklen Fläche. Der Einführungstext auf dem dunklen Titelbild ist hell gesetzt und im Kaltstart lesbar.
- Die installierte App durchlief im Android-16-Emulator den Einrichtungsablauf bis zur Modellansicht. Ein Testwürfel ließ sich laden; Tippen auf die Platte und Tippen/längeres Drücken auf das Modell öffneten kein ungewolltes Aktionsmenü. Die Objektaktionen bleiben über „Mehr“ zugänglich. Das Xiaomi war von ADB nicht erreichbar.
- 277/277 Android-Unit-Tests, 95/95 Headless-E2E-Tests, 2/2 Android-16-Instrumentierungstests, Java/Kotlin-Interop, Upload-/Filament-/G-Code-/Anycubic-/Lithophan-/Fastener-Regressionen, 470 Profilrouten/91 deutsche Gruppen, Kotlin-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/384 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der ARM64-Cache mit 54 Quellbibliotheken wurde vor dem Build validiert.
- Debug-APK: `outputs/OrkanSlicer_0.8.121_debug.apk` (154.520.853 Byte; SHA-256 `90A2F28819D7F5502C0EC8FABF4E1D55AB4E0215536A30C07C04888EE6566B5C`). Release-APK: `outputs/OrkanSlicer_0.8.121_release_unsigned.apk` (146.554.351 Byte; SHA-256 `368DAAE68A6B7AD65D00A8E3ADC634EB07835189C1669F1A2F088FD1F6BCA963`; ohne Release-Keystore unsigniert).
- Bericht und Protokoll: `outputs/OrkanSlicer_0.8.121_Buildbericht.md` und `outputs/OrkanSlicer_0.8.121/verification-transcript.txt`. Emulator-Aufnahmen liegen unter `outputs/OrkanSlicer_0.8.121/`.

## Vorheriger geprüfter Stand — 05.10.2026 — Build 0.8.120

- App: OrkanSlicer 0.8.120 (`versionCode 155`, `minSdk 23`, `targetSdk 36`, Android 16).
- Der Einrichtungsassistent verwendet jetzt Compose für Drucker- und Filamentprofile. Drucker sind nach Hersteller gruppiert, Mehrfachauswahl und benutzerdefinierte Profile bleiben verfügbar; Filamente lassen sich einzeln oder gesammelt auswählen. Drei Policy-Tests decken Einzelauswahl, „Alle auswählen“ und die Weiter-Bedingung ab.
- Der Kaltstarttest mit geleerten App-Daten fand zunächst einen ViewPager2-Absturz: Compose-Seiten wurden ohne `MATCH_PARENT`-Layoutparameter eingesetzt. `composeSetupPage` setzt jetzt explizit ViewPager2-kompatible Layoutparameter. Danach öffnete die APK den vollständigen ersten Einrichtungsablauf auf Android 16: Start, Profilquelle, Druckerauswahl und Filamentauswahl. Anycubic Kobra 3 wurde ausgewählt; „Alle auswählen“ und „Alle abwählen“ aktualisierten die Filamentauswahl sichtbar. Nach dem Kaltstart gab es keinen neuen FATAL-/ANR-Eintrag. Emulator-Aufnahmen liegen in `outputs/OrkanSlicer_0.8.120/`.
- 277/277 Android-Unit-Tests, 95/95 Headless-E2E-Tests, 2/2 Android-16-Instrumentierungstests, Java/Kotlin-Interop, Druckdaten-/Upload-/Filament-/G-Code-/Lithophan-/Fastener-Regressionen, 470 Profilrouten/91 deutsche Gruppen, Kotlin-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/384 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Alle 54 ARM64-Quellbibliotheken wurden gebaut und vor dem Paketlauf validiert.
- Der verifizierte Kaltstart lief auf dem Android-16-Emulator. Ein physisches Xiaomi war von ADB nicht erreichbar; die Prüfung auf dem Telefon steht daher noch aus.
- Debug-APK: `outputs/OrkanSlicer_0.8.120_debug.apk` (154.520.755 Byte; SHA-256 `CE5DC6E36F3ED984A5D5C5047AD84B915C4679CDDA0CF2E076631751016EBAC6`). Release-APK: `outputs/OrkanSlicer_0.8.120_release_unsigned.apk` (146.554.355 Byte; SHA-256 `55CD08189412C6942CF5A6AFAAEBA671F2D146E9E892A917203A50D31BDD2D82`; ohne Release-Keystore unsigniert).
- Bericht und Protokoll: `outputs/OrkanSlicer_0.8.120_Buildbericht.md` und `outputs/OrkanSlicer_0.8.120/verification-transcript.txt`. Emulator-Aufnahmen: `emulator-setup-start.png`, `emulator-setup-printers.png` und `emulator-setup-filaments.png`.

## Vorheriger geprüfter Stand — 04.10.2026 — Build 0.8.119

- App: OrkanSlicer 0.8.119 (`versionCode 154`, `minSdk 23`, `targetSdk 36`, Android 16).
- Der Landschaftsmodus begrenzt das scrollbare Druckbettmenü auf die Fensterhöhe und die Plattenleiste auf die Fensterbreite. Ein Geräte-Instrumentierungstest lädt eine STL und prüft, dass Arbeitsbereich, 3D-Ansicht und Modell bei Hochformat → Querformat → Hochformat erhalten bleiben. Der Slicer-Wrapper übergibt bei optional fehlendem `SliceListener` einen No-op-Callback an JNI und verhindert damit einen nativen Nullzeiger-Absturz.
- 274/274 Android-Unit-Tests, 95/95 Headless-E2E-Tests, 2/2 Android-16-Emulator-Instrumentierungstests, Java/Kotlin-Interop, Druckdaten-/Upload-/Filament-/G-Code-/Lithophan-/Fastener-Regressionen, 470 Profilrouten/91 deutsche Gruppen, Kotlin-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/385 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Alle 54 ARM64-Quellbibliotheken wurden gebaut und in einem geprüften Cache für den finalen Paketlauf verwendet.
- Gerätetest: Die normale Debug-APK wurde auf dem Android-16-Emulator installiert. Nach dem Entfernen des veralteten Wiederherstellungs-Hinweises öffnete die App die Modellansicht und blieb auch bei manueller Drehung ins Querformat aktiv; kein neuer FATAL-/ANR-Eintrag. Die Instrumentierung prüfte zusätzlich eine tatsächlich geladene STL beim Drehen. Ein physisches Xiaomi wurde von ADB nicht erkannt.
- Debug-APK: `outputs/OrkanSlicer_0.8.119_debug.apk` (154.483.430 Byte; SHA-256 `AC9AE20C4B3627937DF41154CFF46677114C72327805BEDD34B7BBC26E939502`). Release-APK: `outputs/OrkanSlicer_0.8.119_release_unsigned.apk` (146.536.699 Byte; SHA-256 `712BFC61E425615F65A21B33817B384995D30136FAF3827F88DFD18E9FA699C6`; ohne Release-Keystore unsigniert).
- Bericht und Protokoll: `outputs/OrkanSlicer_0.8.119_Buildbericht.md` und `outputs/OrkanSlicer_0.8.119/verification-transcript.txt`. Emulator-Aufnahmen: `outputs/OrkanSlicer_0.8.119/emulator-main-portrait.png` und `outputs/OrkanSlicer_0.8.119/emulator-main-landscape.png`.

## Vorheriger geprüfter Stand — Build 0.8.118
- Beim Wechsel der Druckplatte werden die Auswahl und die Aktionsleiste des vorherigen Modells zurückgesetzt. Ein erneuter Tipp auf die schon aktive Platte wechselt nicht erneut und lässt die aktuelle Objektauswahl bestehen.
- 274/274 Android-Unit-Tests, 95/95 Headless-E2E-Tests, Java/Kotlin-Interop, Druckdaten-/Upload-/Filament-/G-Code-/Lithophane-/Fastener-Regressionen, 470 Profilrouten/91 deutsche Gruppen, Kotlin-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/383 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Die ARM64-Bibliotheken wurden aus dem Quellcode neu gebaut; beide Demo-APK-Module und die Slice-/Rotation-Instrumentierungstests wurden gebaut.
- Gerätetest: Ein physisches Xiaomi war in ADB nicht verfügbar. Die Debug-APK wurde auf dem Android-Emulator installiert und gestartet. Modellimport, Objektauswahl, Plattenwechsel, Rückwechsel sowie Tipp auf die aktive Platte und eine leere Plattenfläche funktionierten ohne Absturz; nach dem Plattenwechsel blieb keine Auswahl/Aktionsleiste des vorherigen Modells stehen. Der direkte Xiaomi-Touchscreen-Test und die Slice-/Rotation-Instrumentierung auf Hardware stehen noch aus.
- Debug-APK: `outputs/OrkanSlicer_0.8.118_debug.apk` (153.968.226 Byte; SHA-256 `A37EF08961B8D98647988DAB1FB080E94785E1F0C57154BADC49FD78F4573C7F`). Release-APK: `outputs/OrkanSlicer_0.8.118_release_unsigned.apk` (146.536.703 Byte; SHA-256 `70A6749260121460FDB622E190888229FD65593FF27A737FA03020ACD50A2972`; ohne Release-Keystore unsigniert).
- Bericht: `outputs/OrkanSlicer_0.8.118_Buildbericht.md`; vollständiges Protokoll und Einzelberichte: `outputs/OrkanSlicer_0.8.118/`.

## Vorheriger geprüfter Stand — Build 0.8.117

- 272/272 Android-Unit-Tests, 95/95 Headless-E2E-Tests, Kotlin-/UI-/Connector-/ARM64-Audits, Release-Lint und APK-Prüfungen bestanden. Der Build ergänzte die Compose-Seiten des Einrichtungsassistenten und die Filterung doppelter schneller Taps.
- ADB listete kein angeschlossenes Xiaomi; Installation und Touchscreen-Prüfung blieben offen. APKs, Prüfsummen und Bericht liegen unter `outputs/OrkanSlicer_0.8.117*`.

## Vorheriger geprüfter Stand — Build 0.8.116

- App: OrkanSlicer 0.8.116 (`versionCode 151`, `minSdk 23`, `targetSdk 36`, Android 16).
- Die 3D-Ansicht misst nun die Gesamtbewegung ab dem ersten Berührpunkt; dadurch löst ein langsames Verschieben keine Auswahl- oder Aktionsmenüs aus. Tests decken kumulative Bewegung und normales Fingerzittern ab.
- 270/270 Android-Unit-Tests, 95/95 Headless-E2E-Tests, Druckdaten-/Upload-/Filament-/G-Code-/Lithophane-/Fastener-Regressionen, 470 Profilrouten/91 deutsche Gruppen, Kotlin-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/383 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der source-built ARM64-Cache wurde mit 54 nativen Bibliotheken erneuert.
- Gerätetest: ADB listete kein angeschlossenes Xiaomi. Die APK wurde daher nicht installiert; die langsame Wischbewegung muss noch direkt auf dem Gerät geprüft werden.
- Debug-APK: `outputs/OrkanSlicer_0.8.116_debug.apk` (153.951.720 Byte; SHA-256 `4D37C001613DCC50EF46FE434BFDD36F0660FADA52EA949A81E25735C6BB2DDE`). Release-APK: `outputs/OrkanSlicer_0.8.116_release_unsigned.apk` (146.536.699 Byte; SHA-256 `1FF366362EF65D5E91830DB476782B90950891A36E92D31261A2ECF80530C035`; ohne Release-Keystore unsigniert).
- Bericht: `outputs/OrkanSlicer_0.8.116_Buildbericht.md`; vollständiges Protokoll und Einzelberichte: `outputs/OrkanSlicer_0.8.116/`.

## Vorheriger geprüfter Stand — Build 0.8.115

- App: OrkanSlicer 0.8.115 (`versionCode 150`, `minSdk 23`, `targetSdk 36`, Android 16).
- Die Modellauswahl zeigt zunächst den Modellnamen und die Schaltfläche „Aktionen“. Aktionen erscheinen erst nach einem bewussten Tipp darauf. Jede neue Objektauswahl erzwingt nun eine frische, eingeklappte Compose-Leiste; ein alter, aufgeklappter Zustand bleibt nicht am nächsten Modell hängen. Wiederholtes Tippen auf das bereits ausgewählte Modell hebt die Auswahl nicht auf.
- Zusätzlich laufen die Hauptaktionen im Datei-Menü, die Kalibrierungsmenüs und der KI-Generator mit Kamera/Galerie/Kontingent nun über Compose. 268/268 Android-Unit-Tests, 95/95 Headless-E2E-Tests, Druckdaten-/Upload-/Filament-/G-Code-/Lithophane-/Fastener-Regressionen, 470 Profilrouten/91 deutsche Gruppen, Kotlin-/Connector-/63/63 Native-Prebuilt-Audits, Release-Lint (0 Fehler/383 Warnungen), APK-Metadaten, ZIP-CRC und `git diff --check` bestanden. Der verifizierte Quellbuild-Cache mit 54 ARM64-Bibliotheken wurde für diesen UI-Build verwendet.
- Gerätetest: ADB listete kein angeschlossenes Xiaomi. Die APK wurde daher nicht installiert; die neue Interaktion muss noch direkt auf dem Gerät geprüft werden.
- Debug-APK: `outputs/OrkanSlicer_0.8.115_debug.apk` (154.939.358 Byte; SHA-256 `E1B5C4B99AFC6A2DF23D86ED90C7499F6020C76F5255EAF11FBB69ADC777289D`). Release-APK: `outputs/OrkanSlicer_0.8.115_release_unsigned.apk` (146.536.703 Byte; SHA-256 `5C58D16DFAB53012334C01A6C6FCD3FB01814A01985494354ED61D1E0B17A971`; ohne Release-Keystore unsigniert).
- Bericht: `outputs/OrkanSlicer_0.8.115_Buildbericht.md`; vollständiges Protokoll und Einzelberichte: `outputs/OrkanSlicer_0.8.115/`.

## Vorheriger geprüfter Stand — Build 0.8.114

Die G-Code-Schichtenauswahl und Schichtvorschau wurden von eingebetteten Android-Ansichten auf Compose-Regler und Canvas migriert. Start-/Endschicht bleiben geordnet, und die Live-Vorschau, Performance-Option und Rollenfarben bleiben erhalten. Der Build bestand 268/268 Unit-Tests, 95/95 Headless-E2E-Tests und die vollständigen ARM64-, Lint-, Regressions- und APK-Prüfungen. ADB listete kein angeschlossenes Gerät.

Der nächste Nightly-Abgleich folgt den verbleibenden Punkten in `FUNCTION_GAP_AUDIT.md`: weitere Clipper2-Geometrie, Textur-zu-Farbe, Multi-Nozzle-Helfer, zusätzliche Assimp-Importer und SolveSpace/CAD. Diese Punkte benötigen jeweils native Android-Abhängigkeiten und JNI/UI-Anbindung.

## Historischer Bootstrap-Stand

Die folgenden Angaben dokumentieren den damaligen Projektstart und sind kein aktueller Fehlerstatus.

## Environment used

```bash
JAVA_HOME=/home/cody/.sdkman/candidates/java/17.0.11-tem
ANDROID_HOME=/home/cody/android-sdk
ANDROID_SDK_ROOT=/home/cody/android-sdk
```

Gradle successfully configured the project and automatically installed required Android components:

- Android SDK Platform 35
- Android NDK `23.1.7779620`
- CMake `3.22.1`

## Commands

Configuration check:

```bash
./gradlew projects --no-daemon --stacktrace
```

Result: **successful**.

Source-native debug build wrapper:

```bash
scripts/build-debug.sh
```

Result: **fails at native CMake/Ninja build because upstream prebuilt native libraries are intentionally not stored in git**.

Current first failure:

```text
ninja: error: '../../../../src/main/jniImports/oneTBB/lib/arm64-v8a/libtbb.a', needed by '../../../../build/intermediates/cxx/Release/.../obj/arm64-v8a/libslic3r.so', missing and no known rule to make it
```

Prebuilt-native debug build wrapper:

```bash
scripts/build-debug.sh --prebuilt
```

Result: **successful**. This mode downloads the upstream Slice Beam `0.3.0` APK, extracts non-duplicate runtime `.so` libraries into ignored generated directory `app/src/prebuiltNative/jniLibs/`, skips CMake, and packages an installable bootstrap APK.

Verified APK metadata:

```text
package: com.codemastercody3d.orcaslicermobile
versionCode: 1
versionName: 0.1.0-bootstrap
minSdk: 21
targetSdk: 35
```

This mode is for fast Android app/UI/profile-port iteration. Full source-native builds still require the missing Boost/oneTBB/OCCT prebuilts below.

## Native prebuilt check

Run:

```bash
scripts/check-native-prebuilts.py arm64-v8a
```

Current result:

- present prebuilt imports: 3
- missing prebuilt imports: 61

Present:

- `app/src/main/jniLibs/arm64-v8a/libgmp.so`
- `app/src/main/jniLibs/arm64-v8a/libgmpxx.so`
- `app/src/main/jniLibs/arm64-v8a/libmpfr.so`

Missing groups:

- oneTBB: `libtbb.a`, `libtbbmalloc.a`
- OCCT shared libs: 20 imported `libTK*.so` files
- Boost 1.85 static libs for `arm64-v8a`: 39 imported `libboost_*-clang-mt-a64-1_85.a` files

## Erster Build-Meilenstein (inzwischen abgeschlossen)

Die hier damals fehlenden `arm64-v8a`-Abhängigkeiten wurden später für den vollständigen Quellbuild hergestellt; siehe aktuellen Stand oben. Die ursprünglichen Diagnosebefehle lauteten:

```bash
scripts/check-native-prebuilts.py arm64-v8a
scripts/build-debug.sh
```

