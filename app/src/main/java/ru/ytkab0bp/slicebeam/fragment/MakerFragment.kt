package ru.ytkab0bp.slicebeam.fragment

import android.content.Context
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.MainActivity
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.components.CableClipGenerator
import ru.ytkab0bp.slicebeam.components.CableGrommetGenerator
import ru.ytkab0bp.slicebeam.components.FastenerGenerator
import ru.ytkab0bp.slicebeam.components.ImageToStlConverter
import ru.ytkab0bp.slicebeam.components.KromacutEditorMenu
import ru.ytkab0bp.slicebeam.components.PlanterPotGenerator
import ru.ytkab0bp.slicebeam.components.PlantLabelSetGenerator
import ru.ytkab0bp.slicebeam.components.PhoneStandGenerator
import ru.ytkab0bp.slicebeam.components.PowerStripTrayGenerator
import ru.ytkab0bp.slicebeam.components.TextTagGenerator
import ru.ytkab0bp.slicebeam.components.UtilityBoxGenerator
import ru.ytkab0bp.slicebeam.components.WallToolHolderGenerator
import ru.ytkab0bp.slicebeam.navigation.Fragment
import ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme

/** Compose home for converters, Kromacut and useful generators. */
class MakerFragment : Fragment() {
    private var themeRevision by mutableIntStateOf(0)
    private var expandedSections by mutableStateOf(emptySet<Int>())

    override fun onCreateView(ctx: Context): View {
        val sections = buildSections()
        return ComposeView(ctx).apply {
            setContent {
                val revision = themeRevision
                if (revision >= 0) {
                    BeamComposeTheme {
                        MakerContent(
                            sections = sections,
                            expandedSections = expandedSections,
                            onToggleSection = { index ->
                                expandedSections = if (index in expandedSections) {
                                    expandedSections - index
                                } else {
                                    expandedSections + index
                                }
                            },
                            onAction = ::openOnWorkspace,
                            onBack = ::returnToPreviousScreen,
                        )
                    }
                }
            }
        }
    }

    private fun buildSections(): List<MakerSection> = listOf(
        MakerSection(
            R.string.MakerConverters,
            R.string.MakerConvertersDescription,
            R.drawable.ic_maker_konverter_normal,
            listOf(
                action(R.string.ImageToStlMenu) { bed -> activity()?.let { ImageToStlConverter.pickImage(it, bed) } },
                action(R.string.LithophaneGeneratorMenu) { activity()?.let { ImageToStlConverter.pickLithophane(it) } },
            ),
        ),
        MakerSection(
            R.string.FastenerTitle,
            R.string.FastenerDescription,
            R.drawable.ic_maker_schrauben_muttern_scheiben_normal,
            listOf(action(R.string.FastenerStart) { bed -> activity()?.let { FastenerGenerator.show(it, bed) } }),
        ),
        MakerSection(
            R.string.MakerKromacut,
            R.string.MakerKromacutDescription,
            R.drawable.ic_maker_kromacut_normal,
            listOf(action(R.string.MakerOpenKromacut) { bed ->
                bed.showWorkspaceComposeTool(KromacutEditorMenu())
            }),
        ),
        MakerSection(
            R.string.MakerGenerators,
            R.string.MakerGeneratorsDescription,
            R.drawable.ic_maker_generatoren_normal,
            listOf(
                action(R.string.TextTagMenu) { bed -> activity()?.let { TextTagGenerator.show(it, bed) } },
                action(R.string.UtilityBoxMenu) { bed -> activity()?.let { UtilityBoxGenerator.show(it, bed) } },
                action(R.string.CableClipMenu) { bed -> activity()?.let { CableClipGenerator.show(it, bed) } },
                action(R.string.CableGrommetMenu) { bed -> activity()?.let { CableGrommetGenerator.show(it, bed) } },
                action(R.string.WallToolHolderMenu) { bed -> activity()?.let { WallToolHolderGenerator.show(it, bed) } },
                action(R.string.PlanterMenu) { bed -> activity()?.let { PlanterPotGenerator.show(it, bed) } },
                action(R.string.PlantTagsMenu) { bed -> activity()?.let { PlantLabelSetGenerator.show(it, bed) } },
                action(R.string.PhoneStandMenu) { bed -> activity()?.let { PhoneStandGenerator.show(it, bed) } },
                action(R.string.PowerStripMenu) { bed -> activity()?.let { PowerStripTrayGenerator.show(it, bed) } },
            ),
        ),
    )

    private fun action(titleRes: Int, onClick: (BedFragment) -> Unit) = MakerAction(titleRes, onClick)

    private fun activity(): MainActivity? = getContext() as? MainActivity

    private fun returnToPreviousScreen() {
        val navigation = activity()?.navigationDelegate as? MobileNavigationDelegate ?: return
        if (navigation.getCurrentFragment() === this) navigation.onBackPressed()
    }

    private fun openOnWorkspace(action: MakerAction) {
        val host = activity() ?: return
        val navigation = host.navigationDelegate as? MobileNavigationDelegate ?: return
        val current = navigation.getCurrentFragment()
        if (current is BedFragment) {
            action.onClick(current)
            return
        }

        navigation.switchToSlot(0)
        val workspace = navigation.getCurrentFragment() as? BedFragment ?: return
        val workspaceView = workspace.getView() ?: return
        workspaceView.postDelayed({ action.onClick(workspace) }, WORKSPACE_SWITCH_DELAY_MS)
    }

    override fun onApplyTheme() {
        super.onApplyTheme()
        themeRevision++
    }

    private companion object {
        const val WORKSPACE_SWITCH_DELAY_MS = 360L
    }
}

internal data class MakerAction(val titleRes: Int, val onClick: (BedFragment) -> Unit)

internal data class MakerSection(
    val titleRes: Int,
    val descriptionRes: Int,
    val iconRes: Int,
    val actions: List<MakerAction>,
)

@Composable
internal fun MakerContent(
    sections: List<MakerSection>,
    expandedSections: Set<Int>,
    onToggleSection: (Int) -> Unit,
    onAction: (MakerAction) -> Unit,
    onBack: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Column(
        modifier = Modifier
            // Navigation changes from a weighted bottom bar to a weighted rail on rotation.
            // Cap the scroll viewport to the current window so Compose never receives an
            // unbounded height during that layout transition.
            .heightIn(max = screenHeight)
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Image(painterResource(R.drawable.ic_maker_zurueck_normal), contentDescription = null, modifier = Modifier.size(31.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.MakerBack), fontSize = 14.sp, lineHeight = 18.sp)
            }
            Text(
                text = stringResource(R.string.MakerTitle),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.MakerSubtitle),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
            sections.forEachIndexed { index, section ->
                MakerSectionCard(
                    section = section,
                    expanded = index in expandedSections,
                    onToggle = { onToggleSection(index) },
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun MakerSectionCard(
    section: MakerSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: (MakerAction) -> Unit,
) {
    val headingDescription = if (section.actions.size == 1) {
        stringResource(R.string.MakerSectionOpenDescription, stringResource(section.actions.single().titleRes))
    } else {
        stringResource(
            R.string.MakerSectionToggleDescription,
            stringResource(section.titleRes),
            stringResource(if (expanded) R.string.MakerCollapse else R.string.MakerExpand),
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(role = Role.Button) {
                        if (section.actions.size == 1) onAction(section.actions.single()) else onToggle()
                    }
                    .semantics(mergeDescendants = true) { contentDescription = headingDescription }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(makerSectionIcon(section.iconRes, expanded)),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Column(Modifier.weight(1f).padding(start = 8.dp, end = 4.dp)) {
                    Text(stringResource(section.titleRes), color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (expanded) Text(stringResource(section.descriptionRes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(
                    text = if (section.actions.size == 1) "›" else if (expanded) "⌃" else "⌄",
                    color = MaterialTheme.colorScheme.primary, fontSize = 18.sp,
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    section.actions.forEach { action ->
                        MakerActionRow(action = action, onClick = { onAction(action) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MakerActionRow(action: MakerAction, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(makerActionIcon(action.titleRes)),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = stringResource(action.titleRes),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.MakerOpenAction),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
            )
        }
    }
}

private fun makerActionIcon(titleRes: Int): Int = when (titleRes) {
    R.string.ImageToStlMenu, R.string.LithophaneGeneratorMenu -> R.drawable.ic_maker_konverter_normal
    R.string.FastenerStart -> R.drawable.ic_maker_schrauben_muttern_scheiben_normal
    R.string.MakerOpenKromacut -> R.drawable.ic_maker_kromacut_normal
    else -> R.drawable.ic_maker_generatoren_normal
}

private fun makerSectionIcon(normalRes: Int, expanded: Boolean): Int = if (!expanded) normalRes else when (normalRes) {
    R.drawable.ic_maker_konverter_normal -> R.drawable.ic_maker_konverter_active
    R.drawable.ic_maker_schrauben_muttern_scheiben_normal -> R.drawable.ic_maker_schrauben_muttern_scheiben_active
    R.drawable.ic_maker_kromacut_normal -> R.drawable.ic_maker_kromacut_active
    else -> R.drawable.ic_maker_generatoren_active
}
