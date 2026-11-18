package ru.ytkab0bp.slicebeam.fragment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileSettingsFooter
import java.util.function.Consumer
import java.util.function.Supplier

/** Compose-backed shared profile save/reset footer; the host still owns the profile operations. */
object ProfileSettingsFooter {
    class Controller internal constructor(
        private val resetEnabled: MutableState<Boolean>,
        private val currentTitle: Supplier<String>,
        private val pendingSummary: Supplier<String>,
        private val onApply: Consumer<String>,
        private val onReset: Runnable,
    ) {
        private var profileType = -1
        fun refreshPending(profileType: Int) {
            this.profileType = profileType
            resetEnabled.value = profileType != -1 && ProfileSettingsRepository.pendingCountFor(profileType) > 0
        }

        @Composable
        fun Content(portrait: Boolean) {
            val project by TemporaryProjectSettings.uiState.collectAsState()
            if (project.active && TemporaryProjectSettings.owns(profileType)) {
                Text(stringResource(R.string.ProjectSettingsTemporaryHelp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp, lineHeight = 16.sp)
                return
            }
            ComposeProfileSettingsFooter(
                portrait = portrait,
                resetEnabled = resetEnabled.value,
                useFilamentAssets = profileType == ru.ytkab0bp.slicebeam.config.ConfigObject.PROFILE_LIST_FILAMENT,
                usePrinterAssets = profileType == ru.ytkab0bp.slicebeam.config.ConfigObject.PROFILE_LIST_PRINTER,
                currentTitle = { currentTitle.get().orEmpty() },
                pendingSummary = { pendingSummary.get().orEmpty() },
                onApply = { title -> onApply.accept(title) },
                onReset = { onReset.run() },
            )
        }
    }

    fun create(
        currentTitle: Supplier<String>,
        pendingSummary: Supplier<String>,
        onApply: Consumer<String>,
        onReset: Runnable,
    ): Controller = Controller(
        mutableStateOf(false),
        currentTitle,
        pendingSummary,
        onApply,
        onReset,
    )
}
