package tv.own.owntv.mobile.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.i18n.SupportedLocales
import tv.own.owntv.core.profile.PROFILE_AVATAR_COUNT
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.BrandMark
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.profiles.ProfileAvatar
import tv.own.owntv.mobile.ui.screens.settings.AppIconSheet
import tv.own.owntv.mobile.ui.screens.settings.RemoteSourceSheet
import tv.own.owntv.mobile.ui.screens.settings.SettingsChoice
import tv.own.owntv.mobile.ui.screens.settings.StageChoiceSheet
import tv.own.owntv.mobile.ui.screens.settings.StageConfirmSheet
import tv.own.owntv.mobile.ui.stage.StageTextRow
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageChevron
import tv.own.owntv.mobile.ui.stage.StageRowValue
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageStepper
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * The first-run wizard's own steps, the TV's sequence and words, each in a [WizardFrame]:
 * Welcome → Display → Before you start → Set up OwnTV → Who's watching? → Add a playlist. The TV's chooser
 * for a backup has no step here: a phone has its own file picker, so Restore goes straight to it.
 */

/** The language as a row (a tap opens the list), then Get started. */
@Composable
fun WelcomeStep(onNext: () -> Unit) {
    WizardFrame(
        step = WizardStep.LANGUAGE,
        title = stringResource(R.string.setup_welcome_title),
        sub = stringResource(R.string.setup_welcome_sub),
        next = stringResource(R.string.setup_get_started),
        onNext = onNext,
        onBack = null,
    ) {
        // Here and not only in Settings: every screen after this one is text, and a user who cannot read the
        // disclaimer cannot agree to it.
        FirstRunLanguageRow()
    }
}

/**
 * How big everything is, before the first screen that is mostly words: UI zoom and Font size as
 * steppers, written straight through — `MobileTheme` scales the whole step under the finger — the app icon, and
 * the sample row to judge them by. Below [UiZoom.LOW_RAM_WARN] the same accept-the-risk question Settings asks
 * comes first, once per visit.
 */
@Composable
fun DisplaySizeStep(onNext: () -> Unit, onBack: () -> Unit) {
    val settings: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val zoom by settings.uiZoomPercent.collectAsStateWithLifecycle(UiZoom.DEFAULT)
    val fonts by settings.fontCustomization.collectAsStateWithLifecycle(FontCustomization())
    val appIcon by settings.appIcon.collectAsStateWithLifecycle(AppIcon.DEFAULT)
    var pendingLowZoom by remember { mutableStateOf<Int?>(null) }
    var lowZoomAccepted by remember { mutableStateOf(zoom < UiZoom.LOW_RAM_WARN) }
    var pickIcon by remember { mutableStateOf(false) }
    val setZoom: (Int) -> Unit = { raw ->
        val pct = UiZoom.clamp(raw)
        if (pct < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) pendingLowZoom = pct else scope.launch { settings.setUiZoomPercent(pct) }
    }
    val resetSize: () -> Unit = {
        scope.launch {
            settings.setUiZoomPercent(UiZoom.DEFAULT)
            settings.setFontCustomization(fonts.copy(sizePercent = UiFontScale.DEFAULT))
        }
    }
    val setFont: (Int) -> Unit = { raw -> scope.launch { settings.setFontCustomization(fonts.copy(sizePercent = UiFontScale.clamp(raw))) } }
    WizardFrame(
        step = WizardStep.DISPLAY,
        title = stringResource(R.string.setup_display_size_title),
        sub = stringResource(R.string.setup_display_sub_mobile),
        next = stringResource(R.string.setup_continue),
        onNext = onNext,
        onBack = onBack,
        extraLeft = listOf(stringResource(R.string.settings_reset) to resetSize),
    ) {
        StageSettingRow(title = stringResource(R.string.settings_ui_zoom), icon = MobileIcons.OpenInFull, subtitle = stringResource(R.string.setup_line_zoom), role = null) {
            StageStepper(
                stringResource(R.string.settings_surface_transparency, zoom),
                onMinus = { setZoom(zoom - UiZoom.STEP) },
                onPlus = { setZoom(zoom + UiZoom.STEP) },
                minusEnabled = zoom > UiZoom.MIN,
                plusEnabled = zoom < UiZoom.MAX,
            )
        }
        StageSettingRow(title = stringResource(R.string.settings_font_size), icon = MobileIcons.Edit, subtitle = stringResource(R.string.setup_line_font), role = null) {
            StageStepper(
                stringResource(R.string.settings_surface_transparency, fonts.sizePercent),
                onMinus = { setFont(fonts.sizePercent - UiFontScale.STEP) },
                onPlus = { setFont(fonts.sizePercent + UiFontScale.STEP) },
                minusEnabled = fonts.sizePercent > UiFontScale.MIN,
                plusEnabled = fonts.sizePercent < UiFontScale.MAX,
            )
        }
        StageSettingRow(
            title = stringResource(R.string.settings_app_icon),
            icon = MobileIcons.Palette,
            subtitle = stringResource(R.string.setup_line_app_icon_mobile),
            onClick = { pickIcon = true },
        ) { AppIconSwatches(appIcon) }
        // The sample: a row's own type at the size it will really be.
        val palette = LocalStagePalette.current
        Row(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.ink.copy(alpha = 0.045f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_panel_width_preview).uppercase(), style = stageText(10.5f, 800, letterSpacing = androidx.compose.ui.unit.TextUnit(0.12f, androidx.compose.ui.unit.TextUnitType.Em)), color = palette.dim)
            Text(stringResource(R.string.home_row_now_trending), style = stageText(13f, 700), color = palette.text, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(stringResource(R.string.common_on), style = stageText(12f, 700), color = stageAccent.accent)
        }
    }

    // No restart question: nothing is on the home screen yet, and the pick applies once the app is in the background.
    if (pickIcon) {
        AppIconSheet(chosen = appIcon, onPick = { scope.launch { settings.setAppIcon(it) } }, onDismiss = { pickIcon = false }, askRestart = false)
    }
    pendingLowZoom?.let { target ->
        StageConfirmSheet(
            title = stringResource(R.string.settings_low_zoom_warning_title),
            text = stringResource(R.string.settings_low_zoom_warning_mobile, UiZoom.LOW_RAM_WARN, UiZoom.LOW_RAM_WARN),
            confirm = stringResource(R.string.settings_low_zoom_accept),
            onConfirm = {
                lowZoomAccepted = true
                scope.launch { settings.setUiZoomPercent(target) }
            },
            onDismiss = { pendingLowZoom = null },
        )
    }
}

/** The App icon row's value: five icons from the chosen one's set, the chosen one ringed. */
@Composable
private fun AppIconSwatches(chosen: AppIcon) {
    val accent = stageAccent.accent
    val shown = (AppIcon.entries.take(5) + chosen).distinct().takeLast(5)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { icon ->
            Box(
                Modifier
                    .size(18.dp)
                    .then(if (icon == chosen) Modifier.border(1.5.dp, accent, RoundedCornerShape(6.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) { BrandMark(icon, 16.dp, Modifier.clip(RoundedCornerShape(5.dp)), followAccent = false) }
        }
    }
}

/** What the app is not — it ships no channels, the user brings their own sources. */
@Composable
fun DisclaimerStep(onAgree: () -> Unit, onBack: () -> Unit) {
    WizardFrame(
        step = WizardStep.DISPLAY,
        title = stringResource(R.string.setup_before_you_start),
        sub = stringResource(R.string.setup_disclaimer),
        next = stringResource(R.string.setup_i_understand),
        onNext = onAgree,
        onBack = onBack,
    )
}

/**
 * Start fresh, or bring everything back. Restoring first is why the profile step comes after this one — a
 * restore brings its own profiles.
 */
@Composable
fun SetupChoiceStep(onCreateProfile: () -> Unit, onRestore: () -> Unit, onSyncDevice: () -> Unit, onBack: () -> Unit) {
    WizardFrame(
        step = WizardStep.PROFILE,
        title = stringResource(R.string.setup_set_up_owntv),
        sub = stringResource(R.string.setup_setup_choice_description),
        next = null,
        onNext = {},
        onBack = onBack,
    ) {
        WizardCard(MobileIcons.Person, stringResource(R.string.setup_new_profile), stringResource(R.string.setup_create_profile_add_sources), onCreateProfile)
        // The phone restores from a file it picks itself; Remote restore lives on the Backup page.
        WizardCard(MobileIcons.Download, stringResource(R.string.setup_restore_a_backup), stringResource(R.string.setup_import_profiles_playlists), onRestore)
        WizardCard(MobileIcons.Phone, stringResource(R.string.setup_sync_device), stringResource(R.string.setup_sync_device_description), onSyncDevice)
    }
}

/**
 * Who's watching?: Name, Avatar (the letter first, a tap opens the pictures), Kids profile and an optional
 * PIN, then Create profile. A blank name becomes the default one, as it always has on the phone. A
 * picture of the user's own is held until Create: before then there is no profile to store it under.
 */
@Composable
fun ProfileStep(onCreate: (name: String, avatarId: Int, isKids: Boolean, pin: String?, picture: android.net.Uri?) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var avatarId by remember { mutableIntStateOf(-1) }
    var isKids by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pickAvatar by remember { mutableStateOf(false) }
    var picture by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) picture = uri }
    // A password manager must not offer to save a profile PIN.
    val autofill = LocalAutofillManager.current
    WizardFrame(
        step = WizardStep.PROFILE,
        title = stringResource(R.string.profiles_gate_title),
        sub = stringResource(R.string.setup_profile_sub),
        next = stringResource(R.string.setup_create_profile),
        onNext = { autofill?.cancel(); onCreate(name, avatarId, isKids, pin.takeIf { it.isNotBlank() }, picture) },
        nextEnabled = pin.isEmpty() || pin.length >= PIN_MIN,
        onBack = onBack,
    ) {
        StageTextRow(MobileIcons.Edit, stringResource(R.string.profiles_name), name, { name = it }, placeholder = stringResource(R.string.profiles_name_hint))
        StageSettingRow(
            title = stringResource(R.string.setup_avatar),
            icon = MobileIcons.Person,
            subtitle = stringResource(R.string.setup_avatar_line),
            onClick = { pickAvatar = true },
        ) { AvatarStrip(avatarId) }
        StageSettingRow(
            title = stringResource(R.string.profiles_avatar_own_picture),
            icon = MobileIcons.Person,
            onClick = {
                picker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
        ) {
            picture?.let { ProfileAvatar(avatarId = avatarId, imagePath = it.toString(), modifier = Modifier.size(28.dp).clip(CircleShape)) }
        }
        StageSettingRow(
            title = stringResource(R.string.profiles_kids),
            icon = MobileIcons.FavoriteBorder,
            subtitle = stringResource(R.string.profiles_kids_description),
            onClick = { isKids = !isKids },
            role = Role.Switch,
        ) { StageSwitch(isKids) }
        StageTextRow(
            MobileIcons.Edit,
            stringResource(R.string.profiles_optional_pin),
            pin,
            { typed -> pin = typed.filter(Char::isDigit).take(PIN_MAX) },
            placeholder = stringResource(R.string.profiles_pin_digits),
            password = true,
            keyboardType = KeyboardType.NumberPassword,
        )
    }
    if (pickAvatar) AvatarSheet(avatarId, onPick = { avatarId = it; pickAvatar = false }, onDismiss = { pickAvatar = false })
}

/** The Avatar row's value: five pictures from the chosen one on, the chosen one ringed. */
@Composable
private fun AvatarStrip(chosen: Int) {
    val accent = stageAccent.accent
    val ids = (-1 until PROFILE_AVATAR_COUNT).toList()
    val start = ids.indexOf(chosen).coerceAtLeast(0)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { k ->
            val id = ids[(start + k) % ids.size]
            Box(
                Modifier.size(18.dp).then(if (id == chosen) Modifier.border(1.5.dp, accent, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) { ProfileAvatar(avatarId = id, modifier = Modifier.size(16.dp).clip(CircleShape)) }
        }
    }
}

/** Every picture, the letter first; a tap picks and closes. */
@Composable
private fun AvatarSheet(chosen: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val accent = stageAccent.accent
    MobileBottomSheet(onDismissRequest = onDismiss) {
        StageSheetHeader(stringResource(R.string.setup_avatar))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(64.dp),
            modifier = Modifier.heightIn(max = sheetListHeight()).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(PROFILE_AVATAR_COUNT + 1) { index ->
                val id = index - 1
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .then(if (id == chosen) Modifier.border(2.dp, accent, CircleShape) else Modifier)
                        .clickable(role = Role.RadioButton) { onPick(id) }
                        .padding(4.dp),
                ) { ProfileAvatar(avatarId = id, modifier = Modifier.size(56.dp).clip(CircleShape)) }
            }
        }
    }
}

/**
 * Add a playlist: Remote first (a phone keyboard is slow for addresses too), Type it here, Existing when
 * another profile has playlists, Import, then Skip for now.
 */
@Composable
fun AddContentStep(
    vm: SetupViewModel,
    hasExisting: Boolean,
    onRemoteReceived: () -> Unit,
    onNew: () -> Unit,
    onExisting: () -> Unit,
    onImport: () -> Unit,
    onSkip: () -> Unit,
    onBack: (() -> Unit)?,
) {
    var remote by remember { mutableStateOf(false) }
    WizardFrame(
        step = WizardStep.PLAYLIST,
        title = stringResource(R.string.setup_add_playlist),
        sub = stringResource(R.string.setup_add_playlist_description),
        next = null,
        onNext = {},
        onBack = onBack,
    ) {
        WizardCard(MobileIcons.Translate, stringResource(R.string.setup_from_phone), stringResource(R.string.setup_phone_card_line)) {
            vm.consumeRemotePayload()
            vm.startRemote()
            remote = true
        }
        WizardCard(MobileIcons.Edit, stringResource(R.string.settings_add_type_here), stringResource(R.string.settings_line_add_type_here_mobile)) {
            vm.consumeRemotePayload()
            onNew()
        }
        if (hasExisting) {
            WizardCard(MobileIcons.Layers, stringResource(R.string.setup_existing), stringResource(R.string.setup_use_other_profile_playlists), onExisting)
        }
        WizardCard(MobileIcons.Folder, stringResource(R.string.setup_import), stringResource(R.string.setup_restore_backup_file), onImport)
        WizardLink(stringResource(R.string.setup_skip_for_now), onSkip)
    }
    if (remote) RemoteSourceSheet(vm, onReceived = { remote = false; onRemoteReceived() }, onDismiss = { remote = false })
}

/** Playlists another profile already has, shared rather than downloaded a second time: a switch each. */
@Composable
fun ExistingSourcesStep(sources: List<SourceEntity>, onAdd: (Set<Long>) -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    WizardFrame(
        step = WizardStep.PLAYLIST,
        title = stringResource(R.string.setup_use_existing_playlists),
        sub = stringResource(R.string.setup_pick_playlists),
        next = pluralStringResource(R.plurals.setup_add_selected_playlists, selected.size, selected.size),
        onNext = { onAdd(selected) },
        nextEnabled = selected.isNotEmpty(),
        onBack = onBack,
    ) {
        sources.forEach { source ->
            val checked = source.id in selected
            // The name only: an address can carry a user name and password.
            StageSettingRow(
                title = source.name,
                icon = MobileIcons.PlaylistPlay,
                onClick = { selected = if (checked) selected - source.id else selected + source.id },
                role = Role.Switch,
            ) { StageSwitch(checked) }
        }
    }
}

/** The language row: "All 26 languages" under it, the chosen one on the right; a tap opens the list. */
@Composable
private fun FirstRunLanguageRow(localeStore: LocaleStore = koinInject()) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val current by localeStore.currentTag.collectAsStateWithLifecycle()
    val systemLabel = stringResource(R.string.settings_language_system_default)
    val rows = remember { SupportedLocales.pickerRows.sortedBy { it.englishName } }
    val label = rows.firstOrNull { it.languageTag == current }?.endonym ?: systemLabel
    StageSettingRow(
        title = stringResource(R.string.settings_language),
        icon = MobileIcons.Translate,
        subtitle = pluralStringResource(R.plurals.setup_languages_all, rows.size, rows.size),
        onClick = { open = true },
    ) { StageRowValue(label, StageChevron.DOWN) }
    if (open) {
        val choices = listOf(SettingsChoice("", systemLabel)) + rows.map { SettingsChoice(it.languageTag, it.endonym, it.englishName) }
        StageChoiceSheet(
            eyebrow = null,
            title = stringResource(R.string.settings_language),
            text = null,
            choices = choices,
            selected = current.takeIf { tag -> rows.any { it.languageTag == tag } }.orEmpty(),
            onSelect = { tag -> scope.launch { runCatching { localeStore.set(tag) } } },
            onDismiss = { open = false },
        )
    }
}

private const val PIN_MIN = 4
private const val PIN_MAX = 6
