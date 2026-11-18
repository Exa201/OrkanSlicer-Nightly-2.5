package ru.ytkab0bp.slicebeam

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.ui.setup.SetupAboutScreen
import ru.ytkab0bp.slicebeam.utils.UpdateDownloadState
import ru.ytkab0bp.slicebeam.utils.UpdateDownloadStatus

@RunWith(AndroidJUnit4::class)
class SetupAboutDownloadStateDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reopenedAboutRouteRestoresDownloadProgressPauseAndInstallAction() {
        val state = mutableStateOf(
            UpdateDownloadState(
                status = UpdateDownloadStatus.RUNNING,
                version = "0.8.473",
                downloadedBytes = 37,
                totalBytes = 100,
            ),
        )
        var refreshed = false
        var installRequested = false
        compose.setContent {
            BeamComposeTheme {
                SetupAboutScreen(
                    versionName = "0.8.470",
                    updateStatus = "",
                    downloadState = state.value,
                    onCheckUpdate = {},
                    onInstallUpdate = { installRequested = true },
                    onRefreshDownload = { refreshed = true },
                    onClose = {},
                )
            }
        }

        compose.waitUntil(5_000) { refreshed }
        compose.onNodeWithText("Update wird im Hintergrund geladen · Version 0.8.473")
            .assertIsDisplayed()
        compose.onNodeWithText("37 % · Der Download läuft über Androids Downloadmanager.")
            .assertIsDisplayed()

        compose.runOnIdle {
            state.value = state.value.copy(status = UpdateDownloadStatus.PAUSED)
        }
        compose.onNodeWithText("Download pausiert · Version 0.8.473").assertIsDisplayed()

        compose.runOnIdle {
            state.value = state.value.copy(status = UpdateDownloadStatus.COMPLETE, downloadedBytes = 100)
        }
        compose.onNodeWithText("Heruntergeladenes Update installieren")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue("A completed download can be installed after reopening About", installRequested) }

        compose.runOnIdle {
            state.value = state.value.copy(status = UpdateDownloadStatus.FAILED)
        }
        compose.onNodeWithText("Tippe auf „Auf GitHub nach Updates suchen“, um ihn erneut zu laden.", substring = true)
            .performScrollTo().assertIsDisplayed()
    }
}
