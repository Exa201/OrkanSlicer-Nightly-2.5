package ru.ytkab0bp.slicebeam.fragment

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.recycler.PreferenceItem
import ru.ytkab0bp.slicebeam.ui.settings.OrcaProfileGroupLocalizer

/** Collapsible group title shared by process, filament, and printer profile screens. */
class ProfileSubHeader @JvmOverloads constructor(
    @JvmField val title: String,
    @JvmField val collapsed: Boolean = false,
    @JvmField val onClick: Runnable? = null,
) : ProfileSettingsComposeItem {
    @Composable
    override fun Content(rowPosition: Int, onEditorStateChanged: (Boolean) -> Unit) =
        ProfileSettingGroupHeader(title, collapsed, onClick)
}

@Composable
private fun ProfileSettingGroupHeader(title: String, collapsed: Boolean, onClick: Runnable?) {
    val language = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language.orEmpty().ifBlank { "en" }
    val localizedTitle = OrcaProfileGroupLocalizer.mobileGroup(title, language)
    if (onClick == null) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                localizedTitle, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, lineHeight = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
        }
        return
    }
    val stateLabel = stringResource(
        if (collapsed) R.string.ProfileGroupStateCollapsed else R.string.ProfileGroupStateExpanded,
    )
    val actionLabel = stringResource(
        if (collapsed) R.string.ProfileGroupExpandAction else R.string.ProfileGroupCollapseAction,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { stateDescription = stateLabel }
                .clickable(
                    enabled = onClick != null,
                    onClickLabel = actionLabel,
                    role = Role.Button,
                ) { onClick?.run() }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localizedTitle,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 19.sp,
            )
            Icon(
                painter = painterResource(R.drawable.dropdown_24),
                contentDescription = null,
                modifier = Modifier.size(26.dp).rotate(if (collapsed) -90f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Explanatory profile text shown below a group heading. */
class ProfileSubHint(
    @JvmField val provider: PreferenceItem.ValueProvider,
) : ProfileSettingsComposeItem {
    @Composable
    override fun Content(rowPosition: Int, onEditorStateChanged: (Boolean) -> Unit) =
        ProfileSettingGroupHint(java.lang.String.valueOf(provider.provide()))
}

/** Clickable printer-profile action rendered directly by the shared Compose list. */
class ProfileActionItem(
    @JvmField val title: String,
    @JvmField val subtitle: String?,
    private val action: Runnable,
) : ProfileSettingsComposeItem {
    @Composable
    override fun Content(rowPosition: Int, onEditorStateChanged: (Boolean) -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = title) { action.run() }
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

}

@Composable
private fun ProfileSettingGroupHint(text: String) {
    val language = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language.orEmpty().ifBlank { "en" }
    val localizedText = OrcaProfileGroupLocalizer.mobileGroup(text, language)
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = localizedText,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
    }
}
