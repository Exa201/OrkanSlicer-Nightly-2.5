# OrkanSlicer Nightly 2.5

<p align="center"><img src="docs/images/branding/orkanslicer_feature_graphic.png" alt="OrkanSlicer Nightly 2.5 — 3D-Druck und Slicing" width="900"></p>

Experimental Android port for the official [OrcaSlicer 2.5](https://github.com/OrcaSlicer/OrcaSlicer) engine. The GitHub repository slug is `OrkanSlicer-Nightly-2.5`; this is an independent Exa201 project, not an official OrcaSlicer release.

The branch starts from OrkanSlicer 0.8.409 and applies reviewed, selective OrcaSlicer 2.5 fixes, including empty-layer guards, 64-bit filament masks, AMS preset preservation, Qidi multi-material filenames, and per-filament travel-lift settings. It is **not yet a complete 2.5 core update**. Current verification and each APK's limitations are recorded in [`docs/BUILD_STATUS.md`](docs/BUILD_STATUS.md); treat Nightly prereleases as test builds, not verified production slicers.

For the stable app APK use [OrkanSlicer Releases](https://github.com/Exa201/OrkanSlicer/releases). The Nightly APK and its limitations are listed in [this repository's prereleases](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases). Android 6.0 or newer and a 64-bit ARM device are required; always inspect the G-code preview and printer/material assignments before printing.

The app includes STL, OBJ, STEP and 3MF import, Orca profiles, multi-color painting, on-device slicing and G-code preview, printer upload, and offline maker tools. Imported 3MF process and filament values are kept as temporary project settings; saved user profiles remain separate. A model's logical T0/T1 colors can be assigned to physical ACE slots before slicing, and the model preview shows the selected slot colors.

## Source and builds

```bash
git clone --branch nightly-2.5 https://github.com/Exa201/OrkanSlicer-Nightly-2.5.git
cd OrkanSlicer-Nightly-2.5
./gradlew assembleDebug
```

The Android build uses JDK 17, Android SDK 36, NDK 23.1.7779620 and CMake. Native OrcaSlicer/libslic3r and JNI code build from source. Boost, oneTBB, OCCT, GMP and MPFR ARM64 development archives/headers are required under `app/src/main/jniImports/` and `app/src/main/occt/`; these large generated dependency builds are not in Git. Run `python scripts/check-native-prebuilts.py arm64-v8a` to check what is missing. The source build is therefore intended for contributors who have prepared these dependencies. The tagged APK in Releases is built from this source tree and can be installed without building them.

The bundled Kromacut editor is based on [Kromacut commit `d5d8c490`](https://github.com/vycdev/Kromacut/tree/d5d8c490e68b96a015fa29e2508dca53076ca69e), licensed AGPL-3.0-only. Our integration changes and reconstruction instructions are in [`third_party/kromacut-patches`](third_party/kromacut-patches/README.md); its built web assets are under `app/src/main/assets/kromacut-editor/`.

## Credits and license

- [OrcaSlicer Mobile](https://github.com/CodeMasterCody3D/OrcaSlicer-Mobile) by CodeMasterCody3D: the Android OrcaSlicer port and upstream project. [Support the upstream maintainer](https://www.patreon.com/cw/CodeMasterCody3D).
- [Slice Beam](https://github.com/utkabobr/SliceBeam) by utkabobr: Android app foundation and rendering/JNI work.
- [OrcaSlicer](https://github.com/SoftFever/OrcaSlicer) by SoftFever, based on [Bambu Studio](https://github.com/bambulab/BambuStudio) and [PrusaSlicer](https://github.com/prusa3d/PrusaSlicer): slicing engine and profiles.
- [Kromacut](https://github.com/vycdev/Kromacut) by vycdev: integrated color-model editor.

The app and this fork are distributed under [AGPL-3.0](LICENSE). Component notices and upstream licenses remain with their source and bundled assets. Changes in this fork are recorded in [CHANGELOG.md](CHANGELOG.md).
