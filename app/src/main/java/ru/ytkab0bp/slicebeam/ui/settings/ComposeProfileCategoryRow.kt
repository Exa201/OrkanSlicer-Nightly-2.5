package ru.ytkab0bp.slicebeam.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.components.OrkanIconBadge

/** Compose replacement for the collapsible profile category row. */
@Composable
fun ComposeProfileCategoryRow(
    iconResource: Int,
    title: String,
    expanded: Boolean,
    expandable: Boolean,
    noIconTint: Boolean,
    accentColor: Int?,
    onClick: (() -> Unit)?,
) {
    val language = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language.orEmpty().ifBlank { "en" }
    val localizedTitle = OrcaProfileGroupLocalizer.mobileGroup(title, language)
    val stateLabel = if (expanded) {
        stringResource(R.string.ProfileGroupStateExpanded)
    } else {
        stringResource(R.string.ProfileGroupStateCollapsed)
    }
    val actionLabel = if (expanded) {
        stringResource(R.string.ProfileGroupCollapseAction)
    } else {
        stringResource(R.string.ProfileGroupExpandAction)
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "profile-category-chevron",
    )
    // Keep category titles at the theme's full text contrast; color is reserved for
    // badges and accents so section-specific hues never make labels hard to read.
    val textColor = MaterialTheme.colorScheme.onSurface
    val resolvedIcon = if (noIconTint) {
        PrinterProfileCategoryAssets.stateIconFor(iconResource, active = expanded)
    } else iconResource
    val iconTint = if (noIconTint) Color.Unspecified else {
        accentColor?.let(::Color) ?: MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                if (expandable) stateDescription = stateLabel
            }
            .clickable(
                enabled = onClick != null,
                onClickLabel = actionLabel.takeIf { expandable },
                role = if (onClick != null) Role.Button else null,
            ) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (noIconTint) {
            Icon(
                painter = painterResource(resolvedIcon),
                contentDescription = null,
                modifier = Modifier.size(51.dp),
                tint = iconTint,
            )
        } else {
            OrkanIconBadge(iconResource, size = 42.dp)
        }
        Text(
            text = localizedTitle,
            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
            color = textColor,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
        )
        if (expandable) {
            Icon(
                painter = painterResource(R.drawable.dropdown_24),
                contentDescription = null,
                modifier = Modifier.size(26.dp).rotate(rotation),
                tint = textColor,
            )
        }
    }
}
