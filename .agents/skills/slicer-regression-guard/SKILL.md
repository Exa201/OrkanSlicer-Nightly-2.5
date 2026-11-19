---
name: slicer-regression-guard
description: Use for cross-cutting OrkanSlicer changes, bug fixes, OrcaSlicer upstream ports, or selecting and running regression tests before builds or releases.
---

# Slicer regression guard

## Trigger

Use for cross-cutting changes, bug fixes, upstream behavior ports, and choosing regression suites before builds or releases.

## Procedure

1. Inspect current branch, HEAD, dirty files, app/native version, and the exact target behavior. Preserve every pre-existing user change; do not reset/clean/revert or stage unrelated files.
2. Trace the feature end to end across Compose/View, controller/model, JNI/native engine, serialization, and printer output as applicable. Read existing tests and fixtures before editing.
3. Write the observable invariant and choose the narrowest regression seam: pure Kotlin policy test, Compose callback/semantics test, native device test, or file-format round-trip.
4. For Orca upstream behavior, cite the exact upstream commit/source and compare the local Android call path. A feature existing in the engine does not prove Android UI or config reaches it.
5. Run the focused test first, then required Debug and SliceTest JVM suites. Run `:app:connectedSliceTestAndroidTest` when a verified device/emulator is available; state which serial actually ran it.
6. Inspect reports and crash/logcat; run `git diff --check`. Do not interpret an instrumentation APK build as an executed instrumentation test.
7. Summarize pass/fail/skipped/unavailable counts and any compatibility or device limits. No release, upload, or merge is implied by a passing test.

## Tests and success criteria

- Unit tasks: `:app:testDebugUnitTest`, `:app:testSliceTestUnitTest`.
- Instrumentation task: `:app:connectedSliceTestAndroidTest`; targeted class/method filters should be recorded.
- Native changes require fixture-backed output checks; file-format changes require import/export compatibility; UI changes require observable behavior/accessibility checks.
- Success means the stated invariant is covered, all relevant tests pass or have a concrete documented blocker, and no unrelated dirty work was altered.
