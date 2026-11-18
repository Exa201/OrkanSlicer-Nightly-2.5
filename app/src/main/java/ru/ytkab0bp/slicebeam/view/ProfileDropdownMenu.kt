package ru.ytkab0bp.slicebeam.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.R

/** Small immutable snapshot for the selectable profile list shown by the Compose menu. */
internal data class ProfileDropdownMenuModel(
    val titles: List<String>,
    val selectedIndex: Int,
    val canDelete: Boolean,
)

/** Keeps menu boundaries safe when profile lists change between display and selection. */
internal object ProfileDropdownMenuPolicy {
    fun create(titles: List<String?>, selectedIndex: Int): ProfileDropdownMenuModel {
        val safeTitles = titles.map { it.orEmpty() }
        return ProfileDropdownMenuModel(
            titles = safeTitles,
            selectedIndex = selectedIndex.takeIf { it in safeTitles.indices } ?: -1,
            canDelete = safeTitles.size > 1,
        )
    }

    fun selectedIndex(model: ProfileDropdownMenuModel, requestedIndex: Int): Int? =
        requestedIndex.takeIf { it in model.titles.indices }
}

@Composable
internal fun ProfileDropdownContent(
    title: String?,
    modifier: Modifier = Modifier,
    menu: ProfileDropdownMenuModel,
    menuTitle: Int,
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onDelete: () -> Unit,
    onClone: () -> Unit,
    canManage: Boolean = true,
    leadingIconRes: Int? = null,
) {
    val compactPhone = LocalConfiguration.current.screenWidthDp <= 480
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("profile-dropdown")
                .semantics { role = Role.Button }
                .clickable(onClick = onOpen),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIconRes?.let { icon ->
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).width(28.dp).heightIn(min = 28.dp),
                        tint = Color.Unspecified,
                    )
                }
                Text(
                    text = title.orEmpty(),
                    modifier = Modifier.weight(1f).testTag("profile-dropdown-selected-title"),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = if (compactPhone) 5 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.dropdown_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .heightIn(max = 480.dp),
        ) {
            Text(
                text = stringResource(menuTitle),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            menu.titles.forEachIndexed { index, profileTitle ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = profileTitle,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingIcon = {
                        RadioButton(
                            selected = index == menu.selectedIndex,
                            onClick = null,
                        )
                    },
                    onClick = { onSelect(index) },
                )
            }
            if (canManage) HorizontalDivider()
            if (canManage && menu.canDelete) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.SettingsDeleteProfile)) },
                    onClick = onDelete,
                )
            }
            if (canManage) DropdownMenuItem(
                text = { Text(stringResource(R.string.SettingsCloneProfile)) },
                onClick = onClone,
            )
        }
    }
}
