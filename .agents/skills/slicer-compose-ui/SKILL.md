---
name: slicer-compose-ui
description: Use when changing OrkanSlicer Jetpack Compose navigation, workspace panels, profile pages, dialogs, accessibility, touch gestures, adaptive layout, or Compose performance.
---

# Slicer Compose UI

## Grounded project boundary

The app is hybrid. `ui/workspace/WorkspaceShell.kt` owns workspace geometry and Compose routes; `RendererAdapter.kt` mounts the existing `GLView`. `ui/navigation/AdaptiveWorkspaceNavigation.kt`, `MobileNavigationDelegate.kt`, `components/bed_menu`, and Compose-backed fragments provide the existing UI surfaces. Some Android/system and legacy View integrations remain intentional. Reuse the existing state/controller and overlay coordinator instead of introducing another navigation or state owner.

## Trigger

Use for navigation, menu/panel layout, Compose dialogs, profile settings, gestures, screen-size/font-scale/accessibility behavior, or recomposition/frame issues.

## Procedure

1. Find the owning destination, state/controller, route callback, and existing UI/device tests before editing. Identify whether input should be handled by Compose, the GL surface, or a system component.
2. Preserve a bounded Compose measure tree: no nested scroll containers with unbounded height, and no duplicate vertical scroll owners. For horizontal swipe, identify pointer-event pass, competing GL gestures, nested scroll, and touch slop before changing arbitration.
3. Keep user-visible labels localized using existing resources. Preserve TalkBack labels/roles, focus order, back behavior, and minimum touch targets. Test the requested viewport and font scale rather than relying on preview alone.
4. Keep transient dialogs/panels under the existing overlay coordinator and ensure open/cancel/back/menu-switch paths release state.
5. For performance claims, profile a named transition and compare the same artifact/trace. Do not optimize based only on source appearance.

## Tests and success criteria

- Inspect `ui/workspace/*Test`, `ui/navigation/*Test`, existing `*ComposeDeviceTest`, accessibility tests, and large-text tests.
- Run targeted JVM tests plus `:app:testDebugUnitTest` and `:app:testSliceTestUnitTest` for shared logic; run targeted `sliceTest` device tests for Compose/lifecycle/navigation behavior.
- Success means no clipping/overlap at tested sizes, every action has the right callback, scroll/touch works on first attempt, back/cancel returns to a clean state, and accessibility semantics remain discoverable.
