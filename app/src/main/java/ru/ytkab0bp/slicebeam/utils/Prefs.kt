package ru.ytkab0bp.slicebeam.utils

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import ru.ytkab0bp.slicebeam.BuildConfig
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SetupActivity
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.render.DevicePerformancePolicy

/** Shared preference access for the Android app and its Java-based host integrations. */
open class Prefs {
    companion object {
        @JvmField
        val CAMERA_CONTROL_MODE_ROTATE_MOVE = 0

        @JvmField
        val CAMERA_CONTROL_MODE_MOVE_ROTATE = 1

        @JvmField
        val CAMERA_CONTROL_MODE_MOVE_ONLY = 2

        @JvmField
        val MAX_FILAMENT_COLORS = 16

        private var mPrefs: SharedPreferences? = null
        private var lowRamDevice = false
        @Volatile private var glRendererName: String? = null
        private const val PREF_APP_LANGUAGE = "app_language"

        private fun isSupportedRenderScaleOverride(percent: Int): Boolean = when (percent) {
            0, 50, 65, 80, 100 -> true
            else -> false
        }

        private fun requirePrefs(): SharedPreferences = mPrefs!!

        @JvmStatic
        fun init(ctx: Application) {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            lowRamDevice = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
            // Migrate the former experimental setting. The new namespaced override is
            // intentionally separate so older versions cannot silently force a value.
            mPrefs?.edit()?.remove("render_scale")?.apply()
        }

        @JvmStatic
        fun isLowRamDevice(): Boolean = lowRamDevice

        @JvmStatic
        fun isLowPerformancePreviewDevice(): Boolean = DevicePerformancePolicy.isLowPerformanceDevice(
            lowRamDevice,
            glRendererName,
        )

        /** The renderer is read from GL_RENDERER on the GL thread and is never persisted. */
        @JvmStatic
        fun getGlRendererName(): String? = glRendererName

        /** Returns true only when a new renderer identity should refresh the viewport policy. */
        @JvmStatic
        fun updateGlRendererName(renderer: String?): Boolean {
            val normalized = renderer?.trim()?.takeIf { it.isNotEmpty() }
            if (glRendererName == normalized) return false
            glRendererName = normalized
            return true
        }

        /** May be null before application bootstrap has initialized preferences. */
        @JvmStatic
        fun getPrefs(): SharedPreferences? = mPrefs

        @JvmStatic
        fun getAppLanguage(): String = requirePrefs().getString(PREF_APP_LANGUAGE, "de") ?: "de"

        @JvmStatic
        fun setAppLanguage(languageTag: String) {
            requirePrefs().edit().putString(PREF_APP_LANGUAGE, languageTag).apply()
        }

        @JvmStatic
        fun getLastChangelogBuild(): String? = requirePrefs().getString("last_changelog_build", null)

        @JvmStatic
        fun setLastChangelogBuild() {
            requirePrefs().edit().putString(
                "last_changelog_build",
                "${BuildConfig.VERSION_NAME}:${BuildConfig.COMMIT}",
            ).apply()
        }

        /** Compatibility for existing device tests and integrations that used the old name. */
        @JvmStatic
        fun getLastCommit(): String? = getLastChangelogBuild()

        @JvmStatic
        fun setLastCommit() = setLastChangelogBuild()

        /** Up to sixteen material/color pairs used for multi-color printing and painting. */
        @JvmStatic
        fun getFilamentSlots(): MutableList<FilamentSlot> =
            TemporaryProjectSettings.slots() ?: getStoredFilamentSlots()

        @JvmStatic
        fun getStoredFilamentSlots(): MutableList<FilamentSlot> =
            PrefsValueCodec.parseFilamentSlots(requirePrefs().getString("filament_palette", null))

        @JvmStatic
        fun setFilamentSlots(slots: List<FilamentSlot>) {
            if (TemporaryProjectSettings.active) {
                TemporaryProjectSettings.setSlots(slots)
                return
            }
            setStoredFilamentSlots(slots)
        }

        @JvmStatic
        fun setStoredFilamentSlots(slots: List<FilamentSlot>) {
            requirePrefs().edit()
                .putString("filament_palette", PrefsValueCodec.serializeFilamentSlots(slots))
                .apply()
        }

        @JvmStatic
        fun setMmuSyncedPrinter(printerTitle: String?) {
            if (TemporaryProjectSettings.active) {
                TemporaryProjectSettings.setSyncedPrinter(printerTitle)
                return
            }
            val prefs = requirePrefs()
            val edit = prefs.edit().putString("mmu_synced_printer", printerTitle)
            if (printerTitle == null) {
                prefs.all.keys.filter { it.startsWith("${MmuPrinterPreferenceKeys.PREFIX}synced_") }
                    .forEach { edit.remove(it) }
            } else {
                edit.putBoolean(MmuPrinterPreferenceKeys.forPrinter("synced", printerTitle), true)
            }
            edit.apply()
        }

        @JvmStatic
        fun isMmuSyncedPrinter(printerTitle: String?): Boolean =
            printerTitle != null && if (TemporaryProjectSettings.active) {
                printerTitle == TemporaryProjectSettings.syncedPrinter
            } else {
                val prefs = requirePrefs()
                val key = MmuPrinterPreferenceKeys.forPrinter("synced", printerTitle)
                if (prefs.getBoolean(key, false)) true
                else if (printerTitle == prefs.getString("mmu_synced_printer", null)) {
                    // Preserve the previous single-printer setting when upgrading Nightly.
                    prefs.edit().putBoolean(key, true).apply()
                    true
                } else false
            }

        /** Colors derived from filament slots, for rendering and paint overlays. */
        @JvmStatic
        fun getFilamentPalette(): IntArray {
            val slots = getFilamentSlots()
            val colors = IntArray(slots.size)
            for (index in slots.indices) colors[index] = slots[index].color
            return colors
        }

        @JvmStatic
        fun isScaleInputInMM(): Boolean = requirePrefs().getBoolean("scale_input_mm", false)

        @JvmStatic
        fun setScaleInputInMM(v: Boolean) {
            requirePrefs().edit().putBoolean("scale_input_mm", v).apply()
        }

        @JvmStatic
        fun isScaleLinked(): Boolean = requirePrefs().getBoolean("scale_linked", true)

        @JvmStatic
        fun setScaleLinked(v: Boolean) {
            requirePrefs().edit().putBoolean("scale_linked", v).apply()
        }

        @JvmStatic
        fun getLastCheckedInfo(): Long = requirePrefs().getLong("last_checked_info", 0)

        @JvmStatic
        fun setLastCheckedInfo() {
            requirePrefs().edit().putLong("last_checked_info", System.currentTimeMillis()).apply()
        }

        /** Only used to display optional Boosty information. */
        @JvmStatic
        fun isRussianIP(): Boolean = requirePrefs().getBoolean("russian_ip", false)

        @JvmStatic
        fun setRussianIP(v: Boolean) {
            requirePrefs().edit().putBoolean("russian_ip", v).apply()
        }

        @JvmStatic
        fun setBeamServerData(data: String?) {
            requirePrefs().edit().putString("beam_server_data", data).apply()
        }

        @JvmStatic
        fun getBeamServerData(): String = requirePrefs().getString("beam_server_data", "{}")!!

        @JvmStatic
        fun getCameraControlMode(): Int = requirePrefs().getInt(
            "camera_control_mode",
            if (requirePrefs().getBoolean("rotation_enabled", true)) {
                CAMERA_CONTROL_MODE_ROTATE_MOVE
            } else {
                CAMERA_CONTROL_MODE_MOVE_ONLY
            },
        )

        @JvmStatic
        fun setCameraControlMode(mode: Int) {
            requirePrefs().edit().putInt("camera_control_mode", mode).apply()
        }

        @JvmStatic
        fun isOrthoProjectionEnabled(): Boolean = requirePrefs().getBoolean("ortho_projection", true)

        @JvmStatic
        fun setOrthoProjectionEnabled(e: Boolean) {
            requirePrefs().edit().putBoolean("ortho_projection", e).apply()
        }

        @JvmStatic
        fun getCameraSensitivity(): Float = 5f

        @JvmStatic
        fun getAccentColor(): Int = requirePrefs().getInt("accent", SetupActivity.AccentColors.DEFAULT.color)

        @JvmStatic
        fun setAccentColor(color: Int) {
            requirePrefs().edit().putInt("accent", color).apply()
        }

        @JvmStatic
        fun isVibrationEnabled(): Boolean = requirePrefs().getBoolean("vibration", true)

        @JvmStatic
        fun getAutomaticRenderScale(): Float = DevicePerformancePolicy.defaultRenderScale(
            lowRamDevice,
            glRendererName,
        )

        @JvmStatic
        fun getRenderScale(): Float = DevicePerformancePolicy.renderScale(
            lowRamDevice,
            requirePrefs().getInt("viewport_render_scale_percent", 0),
            glRendererName,
        )

        /** 0 = automatic device policy, otherwise one of 50/65/80/100 percent. */
        @JvmStatic
        fun getRenderScaleOverridePercent(): Int = requirePrefs()
            .getInt("viewport_render_scale_percent", 0)
            .takeIf(::isSupportedRenderScaleOverride) ?: 0

        @JvmStatic
        fun setRenderScaleOverridePercent(percent: Int) {
            require(isSupportedRenderScaleOverride(percent))
            requirePrefs().edit().putInt("viewport_render_scale_percent", percent).apply()
        }

        @JvmStatic
        fun getViewportFpsCapOverride(): Int = requirePrefs()
            .getInt("viewport_fps_cap", 0)
            .takeIf { it in setOf(0, 30, 45, 60) } ?: 0

        /** 0 follows the automatic low-GPU policy; explicit caps are 30/45/60 FPS. */
        @JvmStatic
        fun getViewportFpsCap(): Int = DevicePerformancePolicy.viewportFpsCap(
            lowRamDevice,
            getViewportFpsCapOverride(),
            glRendererName,
        )

        @JvmStatic
        fun getAutomaticViewportFpsCap(): Int = DevicePerformancePolicy.defaultViewportFpsCap(
            lowRamDevice,
            glRendererName,
        )

        @JvmStatic
        fun setViewportFpsCapOverride(fps: Int) {
            require(fps in setOf(0, 30, 45, 60))
            requirePrefs().edit().putInt("viewport_fps_cap", fps).apply()
        }

        /** App-specific text zoom, multiplied on top of the Android accessibility font scale. */
        @JvmStatic
        fun getUiTextScale(): Float = requirePrefs()
            .getFloat("ui_text_scale", 1.0f)
            .coerceIn(0.9f, 1.5f)

        @JvmStatic
        fun setUiTextScale(scale: Float) {
            requirePrefs().edit()
                .putFloat("ui_text_scale", scale.coerceIn(0.9f, 1.5f))
                .apply()
        }

        private var cachedThemeMode: ThemeMode? = null

        @JvmStatic
        fun getThemeMode(): ThemeMode {
            cachedThemeMode?.let { return it }
            val mode = PrefsValueCodec.themeMode(requirePrefs().getInt("theme_mode", 0))
            cachedThemeMode = mode
            return mode
        }

        @JvmStatic
        fun setThemeMode(i: Int) {
            requirePrefs().edit().putInt("theme_mode", i).apply()
            cachedThemeMode = null
        }

        @JvmStatic
        fun getCloudAPIToken(): String? = requirePrefs().getString("cloud_api_token", null)

        @JvmStatic
        fun setCloudAPIToken(token: String?) {
            val editor = requirePrefs().edit()
            if (token == null) editor.remove("cloud_api_token") else editor.putString("cloud_api_token", token)
            editor.apply()
        }

        @JvmStatic
        fun isCloudProfileSyncEnabled(): Boolean = requirePrefs().getBoolean("cloud_profile_sync", true)

        @JvmStatic
        fun setCloudProfileSyncEnabled(en: Boolean) {
            requirePrefs().edit().putBoolean("cloud_profile_sync", en).apply()
        }

        @JvmStatic
        fun getCloudCachedUserInfo(): String? = requirePrefs().getString("cloud_cached_user_info", null)

        @JvmStatic
        fun setCloudCachedUserInfo(info: String?) {
            val editor = requirePrefs().edit()
            if (info == null) editor.remove("cloud_cached_user_info") else editor.putString("cloud_cached_user_info", info)
            editor.apply()
        }

        @JvmStatic
        fun getCloudCachedUsedModels(): Int = requirePrefs().getInt("cloud_cached_models_used", 0)

        @JvmStatic
        fun getCloudCachedMaxModels(): Int = requirePrefs().getInt("cloud_cached_models_max", 50)

        @JvmStatic
        fun setCloudCachedUsedMaxModels(used: Int, max: Int) {
            requirePrefs().edit()
                .putInt("cloud_cached_models_used", used)
                .putInt("cloud_cached_models_max", max)
                .apply()
        }

        @JvmStatic
        fun getCloudCachedUserFeatures(): String? = requirePrefs().getString("cloud_cached_user_features", null)

        @JvmStatic
        fun setCloudCachedUserFeatures(features: String?) {
            val editor = requirePrefs().edit()
            if (features == null) editor.remove("cloud_cached_user_features") else editor.putString("cloud_cached_user_features", features)
            editor.apply()
        }

        @JvmStatic
        fun getCloudLastFeaturesSync(): Long = requirePrefs().getLong("cloud_last_features_sync", 0)

        @JvmStatic
        fun setCloudLastFeaturesSync(ls: Long) {
            requirePrefs().edit().putLong("cloud_last_features_sync", ls).apply()
        }

        @JvmStatic
        fun getCloudLastSync(): Long = requirePrefs().getLong("cloud_last_sync", 0)

        @JvmStatic
        fun setCloudLastSync(ls: Long) {
            requirePrefs().edit().putLong("cloud_last_sync", ls).apply()
        }

        @JvmStatic
        fun getCloudLocalLastSentModified(): Long = requirePrefs().getLong("cloud_local_last_sent_modified", 0)

        @JvmStatic
        fun setCloudLocalLastSentModified(lm: Long) {
            requirePrefs().edit().putLong("cloud_local_last_sent_modified", lm).apply()
        }

        @JvmStatic
        fun getCloudLocalLastModified(): Long = requirePrefs().getLong("cloud_local_last_modified", 0)

        @JvmStatic
        fun setCloudLocalLastModified(lm: Long) {
            requirePrefs().edit().putLong("cloud_local_last_modified", lm).apply()
        }

        @JvmStatic
        fun getCloudRemoteLastModified(): Long = requirePrefs().getLong("cloud_remote_last_modified", 0)

        @JvmStatic
        fun setCloudRemoteLastModified(lm: Long) {
            requirePrefs().edit().putLong("cloud_remote_last_modified", lm).apply()
        }

        @JvmStatic
        fun isPerformanceModeEnabled(): Boolean = requirePrefs().getBoolean("performance_mode", true)

        @JvmStatic
        fun setPerformanceModeEnabled(en: Boolean) {
            requirePrefs().edit().putBoolean("performance_mode", en).apply()
        }

        @JvmStatic
        fun isTranslucentFilamentPreviewEnabled(): Boolean =
            requirePrefs().getBoolean("preview_translucent_filaments", true)

        @JvmStatic
        fun setTranslucentFilamentPreviewEnabled(enabled: Boolean) {
            requirePrefs().edit().putBoolean("preview_translucent_filaments", enabled).apply()
        }
    }

    enum class ThemeMode(@JvmField val title: Int) {
        SYSTEM(R.string.SettingsInterfaceThemeSystem),
        LIGHT(R.string.SettingsInterfaceThemeLight),
        DARK(R.string.SettingsInterfaceThemeDark),
    }
}
