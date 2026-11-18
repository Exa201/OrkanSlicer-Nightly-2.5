package ru.ytkab0bp.slicebeam.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.LocalOrkanSection
import ru.ytkab0bp.slicebeam.ui.OrkanSection
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens
import ru.ytkab0bp.slicebeam.ui.settings.PrinterProfileCategoryAssets
import ru.ytkab0bp.slicebeam.ui.settings.AppSettingsIconAssets

/** Colorful, decorative vector badge; the surrounding label provides the accessible name. */
@Composable
fun OrkanIconBadge(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val section = LocalOrkanSection.current
    val start = if (section == OrkanSection.DEFAULT) MaterialTheme.colorScheme.primary else section.accent
    val end = if (section == OrkanSection.DEFAULT) MaterialTheme.colorScheme.secondary else section.secondary
    val isometricAsset = AppSettingsIconAssets.isPackAsset(iconRes) || when (iconRes) {
        R.drawable.menu3d_arrange, R.drawable.menu3d_center, R.drawable.menu3d_combine,
        R.drawable.menu3d_duplicate, R.drawable.menu3d_file_export, R.drawable.menu3d_file_import,
        R.drawable.menu3d_fill_bed, R.drawable.menu3d_layers, R.drawable.menu3d_move,
        R.drawable.menu3d_move_plate, R.drawable.menu3d_objects, R.drawable.menu3d_place,
        R.drawable.menu3d_printer_send, R.drawable.menu3d_quick_slice, R.drawable.menu3d_rotate,
        R.drawable.menu3d_scale, R.drawable.menu3d_smart_slice, R.drawable.menu3d_split,
        R.drawable.menu3d_tools, R.drawable.menu3d_tune, R.drawable.menu3d_prime_tower,
        R.drawable.menu3d_purge_volume, R.drawable.menu3d_spool, R.drawable.menu3d_printer -> true
        R.drawable.ic_druckprofil, R.drawable.ic_schichten_und_wandungen,
        R.drawable.ic_obere_untere_schichten, R.drawable.ic_fuellung,
        R.drawable.ic_haftung_skirt_brim, R.drawable.ic_stuetzstrukturen,
        R.drawable.ic_geschwindigkeit, R.drawable.ic_kuehlung,
        R.drawable.ic_mehrere_extruder, R.drawable.ic_erweitert,
        R.drawable.ic_ausgabeoptionen -> true
        R.drawable.ic_filament, R.drawable.ic_filament_active, R.drawable.ic_filament_disabled,
        R.drawable.ic_filament_kuehlung, R.drawable.ic_filament_kuehlung_active,
        R.drawable.ic_filament_kuehlung_disabled, R.drawable.ic_filament_erweitert,
        R.drawable.ic_filament_erweitert_active, R.drawable.ic_filament_erweitert_disabled,
        R.drawable.ic_filamentkorrekturen, R.drawable.ic_filamentkorrekturen_active,
        R.drawable.ic_filamentkorrekturen_disabled, R.drawable.ic_filamentprofil,
        R.drawable.ic_filamentprofil_active, R.drawable.ic_filamentprofil_disabled,
        R.drawable.ic_filamentslot, R.drawable.ic_filamentslot_active, R.drawable.ic_filamentslot_disabled,
        R.drawable.ic_info, R.drawable.ic_info_active, R.drawable.ic_info_disabled,
        R.drawable.ic_profilsuche, R.drawable.ic_profilsuche_active, R.drawable.ic_profilsuche_disabled,
        R.drawable.ic_speichern, R.drawable.ic_speichern_active, R.drawable.ic_speichern_disabled,
        R.drawable.ic_zuruecksetzen, R.drawable.ic_zuruecksetzen_active, R.drawable.ic_zuruecksetzen_disabled,
        R.drawable.ic_materialwarnung, R.drawable.ic_materialwarnung_active, R.drawable.ic_materialwarnung_disabled -> true
        else -> PrinterProfileCategoryAssets.isPackAsset(iconRes)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .then(if (isometricAsset) Modifier.background(Color(0xFF202B42))
                else Modifier.background(Brush.linearGradient(listOf(start, end))))
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(size * 0.879f),
            // Dark, section-specific line art keeps at least 4.5:1 contrast against
            // the saturated accent badge on both light and dark app themes.
            tint = if (isometricAsset) Color.Unspecified
                else if (section == OrkanSection.DEFAULT) MaterialTheme.colorScheme.onPrimary
                else section.iconAccent,
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (section == OrkanSection.DEFAULT) end else section.accent.copy(alpha = 0.9f)),
        )
    }
}

/** Reusable, readable page heading used by profile and settings destinations. */
@Composable
fun OrkanPageHeader(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    val accent = LocalOrkanSection.current.let {
        if (it == OrkanSection.DEFAULT) MaterialTheme.colorScheme.primary else it.accent
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrkanIconBadge(iconRes, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

/** Elevated section card with a colorful vector heading, suitable for settings groups. */
@Composable
fun OrkanSectionCard(
    @DrawableRes iconRes: Int,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accent = LocalOrkanSection.current.let {
        if (it == OrkanSection.DEFAULT) MaterialTheme.colorScheme.primary else it.accent
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrkanIconBadge(iconRes, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            content()
        }
    }
}

/** A settings row with a consistent hit target and room for long localized labels. */
@Composable
fun OrkanSettingRow(
    title: String,
    value: String? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = OrkanUiTokens.touchTarget)
            .clip(RoundedCornerShape(OrkanUiTokens.compactCornerRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(OrkanUiTokens.compactCornerRadius),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (value != null) {
                Text(
                    value,
                    modifier = Modifier.weight(0.8f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** A reusable accessible accordion surface for settings groups and profile categories. */
@Composable
fun OrkanAccordion(
    @DrawableRes iconRes: Int,
    title: String,
    expanded: Boolean,
    stateDescriptionText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    onToggle: () -> Unit,
) {
    val accent = LocalOrkanSection.current.let {
        if (it == OrkanSection.DEFAULT) MaterialTheme.colorScheme.primary else it.accent
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = OrkanUiTokens.touchTarget)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { stateDescription = stateDescriptionText }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrkanIconBadge(iconRes, size = 40.dp)
                Text(
                    title,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    painter = painterResource(R.drawable.dropdown_24),
                    contentDescription = null,
                    modifier = Modifier.size(31.dp).rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                HorizontalDivider(color = accent.copy(alpha = 0.18f))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { content() }
            }
        }
    }
}

/** High-emphasis action button shared by profile and settings screens. */
@Composable
fun OrkanPrimaryAction(
    title: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val section = LocalOrkanSection.current
    val start = if (section == OrkanSection.DEFAULT) MaterialTheme.colorScheme.primary else section.accent
    Surface(
        modifier = modifier
            .heightIn(min = OrkanUiTokens.touchTarget)
            .clip(RoundedCornerShape(OrkanUiTokens.cornerRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        color = if (enabled) start else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) Color(0xFF07111C) else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(iconRes), contentDescription = null, tint = if (enabled) Color(0xFF07111C) else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}
