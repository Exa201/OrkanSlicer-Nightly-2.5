# OrcaSlicer factory profile database

OrkanSlicer Nightly's offline factory profiles are generated from the current
`OrcaSlicer/OrcaSlicer` `main` branch. The bundled database was refreshed from
upstream revision `07f38df2a4fd3439cffbf991ee41ee99ec71fbee` on 2026-11-17.
This is a source revision, not a claim that every vendor's own profile version
uses the same version number.

The refresh contains 72 upstream vendor catalogs, 1,284 printer variants, and
10,254 process and filament presets. The Android loader validated the generated
catalogs without unresolved inheritance. The profiles are bundled locally, so
selection works offline. Generation filters setting keys to those understood
by OrkanSlicer's current Android config wrapper; new Orca settings that the app
does not implement are not silently treated as supported controls.

## Updating

Fetch the latest OrcaSlicer source and run from the OrkanSlicer repository root:

```powershell
python tools/update_orca_profile_database.py `
  <OrcaSlicer-checkout>/resources/profiles `
  app/src/main/assets/orca_profiles `
  --source-revision <OrcaSlicer-git-sha>
```

For a first database migration, add `--archive-legacy`. This stores the former
bundled vendor INIs in `legacy_factory_profiles.zip` for one-time migration.
Do not regenerate that archive after replacing the database.

## Existing installations

At startup, Nightly compares the bundled source revision with the saved config.
For vendor families already present in the user's config, unchanged factory
presets are replaced by current upstream values, new presets are added, and
obsolete unchanged unselected presets are removed. User-edited profiles remain
unchanged. If a selected legacy preset has no safe upstream name match, it is
retained to avoid silently changing the active print setup. The historical
Kobra 3 V2 stock profile name is migrated to Orca's current profile name.

The migration saves transactionally: if saving fails, the previous in-memory
profile lists and selected presets are restored, and the refresh is retried on a
later app start.

## Verification

`OrcaProfileDatabaseTest` parses every bundled vendor INI and checks catalog
size, inheritance, and representative Anycubic and Qidi profiles. It also loads
the archived pre-refresh Qidi database and the historical Kobra 3 V2 Stock
profile, then verifies that Qidi's printer/process/filament catalogs refresh
and the selected Kobra profile migrates to Orca's current printer name.
`FactoryProfileDatabaseUpdaterTest` checks refresh, stale-profile removal,
preservation of user edits, and Kobra 3 V2 profile-name migration.

Verified on 2026-11-17: OrcaSlicer `main` resolves to
`07f38df2a4fd3439cffbf991ee41ee99ec71fbee`. The refreshed factory catalog
retains the latest Anker M5/M5C `file_start_gcode` total-duration header and
the Android printer-profile loader now preserves that upstream key. The
targeted profile database and migration tests passed after this refresh. At
that database-refresh checkpoint, the connected Honor still ran
0.8.493 / code 528; the profile-picker correction and updated APK are recorded
below.

## Profile picker diagnosis — 2026-11-17

The Honor's saved Nightly configuration was checked read-only: it contains no
printer preset, one process preset, and 1,956 filament presets. Orca's parser
keeps printer, process, and filament records in separate typed lists. The
profile picker therefore had no active printer to filter against; returning
only unscoped presets also hid an already-selected vendor-specific process or
filament. The picker now retains that active preset while still hiding other
vendors' presets until a printer is selected. Choosing a printer refreshes the
process and filament options using its compatibility data. The printer page
shows an explicit add-printer action when the stored printer list is empty.

Nightly **0.8.494 / code 529** was installed on the Honor PTP-N49 (Android 16,
476 dp override). Startup reached the existing setup screen without a crash.
Profile compatibility and section-separation JVM tests passed. The profile
selection screen itself still needs an interaction check after the user
completes or exits printer setup; this diagnostic did not add a printer or
change the saved profile selection.

Follow-up diagnosis: the profile-list cache key did not include the active process title, and the filament list also omitted the selected filament title. Changing a preset could therefore leave the old filtered list rendered. The 0.8.494 correction includes the active selections in cache identity and refreshes those lists immediately.
