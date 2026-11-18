package ru.ytkab0bp.slicebeam.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.BuildConfig
import ru.ytkab0bp.slicebeam.R

internal data class SetupCloudLevel(
    val level: Int,
    val title: String,
    val price: String?,
    val subscribeUrl: String?,
    val manageUrl: String?,
)

internal data class SetupCloudBenefits(
    val earlyAccessLevel: Int,
    val syncRequiredLevel: Int,
    val aiGeneratorRequiredLevel: Int,
    val aiGeneratorModelsPerMonth: Int,
    val alreadySubscribedInfoUrl: String?,
)

internal object SetupCloudSubscriptionPolicy {
    fun isSubscribed(level: Int, currentLevel: Int?): Boolean =
        level > 0 && currentLevel != null && level == currentLevel

    fun canOpenCheckout(level: Int, currentLevel: Int?): Boolean =
        level > 0 && (currentLevel == null || level > currentLevel)

    fun includes(requiredLevel: Int, level: Int): Boolean =
        requiredLevel != -1 && level >= requiredLevel
}

@Composable
internal fun SetupCloudProfileScreen(
    levels: List<SetupCloudLevel>,
    benefits: SetupCloudBenefits,
    currentLevel: Int?,
    loggedIn: Boolean,
    loggingIn: Boolean,
    onAccountAction: () -> Unit,
    onCancelLogin: () -> Unit,
    onOpenUrl: (String?) -> Unit,
) {
    var selectedLevel by remember { mutableStateOf<SetupCloudLevel?>(null) }
    var showCancelLogin by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(top = 42.dp)) {
        Text(
            text = stringResource(R.string.SettingsCloudManageDescription),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(levels, key = { it.level }) { level ->
                val subscribed = SetupCloudSubscriptionPolicy.isSubscribed(level.level, currentLevel)
                val canSubscribe = SetupCloudSubscriptionPolicy.canOpenCheckout(level.level, currentLevel)
                SetupCloudLevelCard(
                    level = level,
                    benefits = benefits,
                    subscribed = subscribed,
                    enabled = subscribed || canSubscribe,
                    onClick = {
                        if (subscribed) onOpenUrl(level.manageUrl) else selectedLevel = level
                    },
                )
            }
        }
        TextButton(
            onClick = { onOpenUrl("https://beam3d.ru/slicebeam_cloud_tos.html") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(text = stringResource(R.string.SettingsCloudManageTermsOfService), maxLines = 2)
        }
        Button(
            onClick = { if (!loggedIn && loggingIn) showCancelLogin = true else onAccountAction() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (!loggedIn && loggingIn) {
                CircularProgressIndicator(modifier = Modifier.size(29.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = stringResource(
                        if (loggedIn) R.string.SettingsCloudManageButtonManage
                        else R.string.SettingsCloudManageButtonLogIn,
                    ),
                    fontSize = 16.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    selectedLevel?.let { level ->
        AlertDialog(
            onDismissRequest = { selectedLevel = null },
            title = { Text(text = level.title) },
            text = { Text(text = stringResource(R.string.SettingsCloudManageLevelRedirectMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    selectedLevel = null
                    onOpenUrl(level.subscribeUrl)
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedLevel = null
                    onOpenUrl(benefits.alreadySubscribedInfoUrl)
                }) { Text(stringResource(R.string.SettingsCloudManageLevelRedirectAlreadySubscribed)) }
            },
        )
    }
    if (showCancelLogin) {
        AlertDialog(
            onDismissRequest = { showCancelLogin = false },
            title = { Text(text = stringResource(R.string.SettingsCloudManageButtonLogInCancelTitle)) },
            text = { Text(text = stringResource(R.string.SettingsCloudManageButtonLogInCancel)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelLogin = false
                    onCancelLogin()
                }) { Text(stringResource(R.string.Yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelLogin = false }) { Text(stringResource(R.string.No)) }
            },
        )
    }
}

@Composable
private fun SetupCloudLevelCard(
    level: SetupCloudLevel,
    benefits: SetupCloudBenefits,
    subscribed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(
                        if (level.level <= 0) R.drawable.zero_ruble_outline_28
                        else if (level.level == 1) R.drawable.stars_outline_28
                        else R.drawable.cloud_plus_outline_28,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = level.title,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val price = when {
                    subscribed -> stringResource(R.string.SettingsCloudManageSubscribed)
                    level.level <= 0 -> stringResource(R.string.SettingsCloudManageFree)
                    !level.price.isNullOrBlank() -> level.price
                    else -> null
                }
                if (!price.isNullOrBlank() && (enabled || level.level <= 0)) {
                    Text(text = price, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                }
            }
            val features = buildList {
                if (!BuildConfig.IS_GOOGLE_PLAY && SetupCloudSubscriptionPolicy.includes(benefits.earlyAccessLevel, level.level)) {
                    add(SetupCloudFeature(R.drawable.clock_circle_dashed_outline_24, R.string.SettingsCloudManageFeatureEarlyAccess, R.string.SettingsCloudManageFeatureEarlyAccessDescription))
                }
                if (SetupCloudSubscriptionPolicy.includes(benefits.syncRequiredLevel, level.level)) {
                    add(SetupCloudFeature(R.drawable.sync_outline_28, R.string.SettingsCloudManageFeatureCloudSync, R.string.SettingsCloudManageFeatureCloudSyncDescription))
                }
                if (SetupCloudSubscriptionPolicy.includes(benefits.aiGeneratorRequiredLevel, level.level)) {
                    add(SetupCloudFeature(R.drawable.brain_outline_28, R.string.SettingsCloudManageFeatureAIGenerator, null))
                }
                if (level.level > 0) {
                    add(SetupCloudFeature(R.drawable.box_heart_outline_28, R.string.SettingsCloudManageFeatureFreeForAll, R.string.SettingsCloudManageFeatureFreeForAllDescription))
                }
            }
            features.forEach { feature ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(feature.icon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(text = stringResource(feature.title), style = MaterialTheme.typography.labelLarge)
                        val description = feature.description
                        if (description != null) {
                            Text(text = stringResource(description), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.SettingsCloudManageFeatureAIGeneratorDescription,
                                    benefits.aiGeneratorModelsPerMonth,
                                    benefits.aiGeneratorModelsPerMonth,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SetupCloudFeature(val icon: Int, val title: Int, val description: Int?)
