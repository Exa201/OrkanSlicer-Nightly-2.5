---
name: slicer-offline-architecture
description: Use when changing OrkanSlicer's offline behavior, data ownership, persistence, network boundaries, profile availability, or caching that could affect offline workflows.
---

# Slicer offline architecture

## Grounded project boundary

The app bundles Orca profiles/assets in `app/src/main/assets`, stores local preferences/profile data through existing config and `Prefs` classes, imports/exports user files via SAF, and slices through the local native engine. Network-dependent paths exist separately, including printer transport, profile/cloud synchronization, GitHub updater checks, and remote web content such as Kromacut. Do not claim every feature is offline-capable.

## Trigger

Use when adding a network call, changing profile/cache persistence, altering file ownership/lifecycle, adding background work, or reviewing local workflows in airplane mode.

## Procedure

1. Classify each input and operation as bundled, user-local, device-local, printer-network, cloud, updater, or remote-web data. Identify its current owner, source of truth, permissions, and lifetime.
2. Keep local model import/edit/slice/export and bundled profiles independent of network access. Gate remote-only capabilities explicitly and provide an understandable unavailable state without discarding local work.
3. For caches, document key/version, owner, invalidation, max size, persistence policy, and behavior after process death/profile change. Never let cached remote/profile data silently override an explicit local selection.
4. Keep SAF URI access grant-based; persist grants only when the app needs future access and the chosen action supports it. Use app-owned temporary copies only for APIs that require paths, and delete them on success/cancel/failure.
5. Test offline by disabling network on emulator or a verified device after app setup; cover opening bundled profiles, editing a local model, local slicing, and saving to a user-selected document. Separately verify expected graceful failure for printer/cloud/updater operations.

## Tests and success criteria

- Inspect `PrefsValueCodecTest`, `ConverterStoragePermissionTest`, local profile database tests, `GitHubUpdaterTest`, printer transport tests, and SAF/file-format tests.
- Run focused JVM and relevant device tests. Record which network is disabled and which features are expected to need it.
- Success means local workflows do not make accidental network requests or lose data; remote-dependent operations fail clearly and safely; cache and temporary-file lifetimes are bounded and testable.
