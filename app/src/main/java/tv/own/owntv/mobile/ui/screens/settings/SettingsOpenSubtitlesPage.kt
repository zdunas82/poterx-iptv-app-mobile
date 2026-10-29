package tv.own.owntv.mobile.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.mobile.R
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.ui.screens.settings.customize.ConfirmDialog
import tv.own.owntv.mobile.ui.theme.MobileDimens
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.StageRowValue
import tv.own.owntv.mobile.ui.stage.StageChevron
import androidx.compose.ui.semantics.Role
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageField
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.StageTextRow

/**
 * The account subtitles are downloaded through, what is left of today's allowance, and the files
 * already on the phone.
 *
 * The optional key and server address are saved on a button rather than on each keystroke, for the
 * same reason the TMDB key is: half a key is a key that fails, and that looks like a broken service.
 */
@Composable
fun SettingsOpenSubtitlesPage(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
    accountVm: OpenSubtitlesViewModel = koinViewModel(),
) {
    val storedKey = vm.settings.openSubtitlesApiKey.pref("")
    val storedUrl = vm.settings.openSubtitlesServerUrl.pref("")
    val filterEnabled = vm.settings.subSearchFilterEnabled.pref(false)
    val searchLang = vm.settings.subSearchLanguages.pref("")
    val state by accountVm.state.collectAsStateWithLifecycle()
    val error by accountVm.error.collectAsStateWithLifecycle()

    var showSignIn by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    // Seeded from what is stored, then owned by the fields until Save puts them back.
    var key by remember(storedKey) { mutableStateOf(storedKey) }
    var url by remember(storedUrl) { mutableStateOf(storedUrl) }

    if (showDelete) {
        BackHandler { showDelete = false }
        DeleteSubtitlesPage(onBack = { showDelete = false }, modifier = modifier)
        return
    }

    val connectionLabel = when {
        storedUrl.isNotBlank() -> stringResource(R.string.settings_tier_self_host)
        storedKey.isNotBlank() -> stringResource(R.string.settings_tier_key)
        else -> stringResource(R.string.settings_shared)
    }

    SettingsPage(modifier) {
        settingsNote(R.string.player_subtitles_free_description_full)

        settingsSection(R.string.player_subtitles_account)
        when (val s = state) {
            is OpenSubtitlesViewModel.UiState.SignedIn -> {
                val session = s.session
                settingsGroup(key = "os-user") {
                    StageSettingRow(
                        title = stringResource(R.string.player_subtitles_connected_as),
                        subtitle = listOfNotNull(
                            session.level,
                            stringResource(R.string.player_subtitles_vip).takeIf { session.vip },
                        ).joinToString(stringResource(R.string.player_subtitles_tags_separator))
                            .ifBlank { stringResource(R.string.player_subtitles_free_account) },
                    ) { StageRowValue(session.username, StageChevron.NONE) }
                }
                session.remainingDownloads?.let { remaining ->
                    settingsGroup(key = "os-downloads") {
                        val total = session.allowedDownloads
                        StageSettingRow(
                            title = stringResource(R.string.player_subtitles_downloads),
                            subtitle = stringResource(R.string.settings_open_subtitles_connected),
                        ) { StageRowValue(if (total != null) {
                                pluralStringResource(
                                    R.plurals.player_subtitles_remaining,
                                    remaining,
                                    remaining,
                                    total,
                                )
                            } else {
                                pluralStringResource(
                                    R.plurals.player_subtitles_remaining_short,
                                    remaining,
                                    remaining,
                                )
                            }, StageChevron.NONE) }
                    }
                }
                settingsGroup(key = "os-resets") {
                    StageSettingRow(
                        title = stringResource(R.string.player_subtitles_resets),
                        subtitle = stringResource(R.string.settings_metadata_connection) +
                            stringResource(R.string.content_metadata_separator) + connectionLabel,
                    ) { StageRowValue(openSubtitlesResetLabel(session.resetTime), StageChevron.NONE) }

                    Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH)) {
                        StageButton(
                            text = stringResource(R.string.player_subtitles_refresh),
                            onClick = { accountVm.refresh() },
                            modifier = Modifier.padding(vertical = MobileDimens.GapSmall),
                        )
                        StageButton(
                            text = stringResource(R.string.player_subtitles_sign_out),
                            onClick = { confirmSignOut = true },
                            small = true,
                        )
                    }
                }
            }
            OpenSubtitlesViewModel.UiState.Busy -> settingsNote(R.string.player_subtitles_contacting)
            OpenSubtitlesViewModel.UiState.SignedOut -> item(key = "os-sign-in") {
                StageSettingRow(
                    title = stringResource(R.string.player_subtitles_sign_in),
                    subtitle = stringResource(R.string.player_subtitles_connect_description),
                    onClick = { showSignIn = true },
                )
            }
        }

        settingsSection(R.string.settings_open_subtitles_advanced)
        settingsNote(R.string.settings_open_subtitles_advanced_description)
        settingsGroup(key = "os-fields") {
            StageTextRow(MobileIcons.Lock, stringResource(R.string.settings_open_subtitles_api_key), key, { key = it }, password = true)
            StageTextRow(MobileIcons.Translate, stringResource(R.string.settings_worker_server_url), url, { url = it })
            Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH)) {
                StageButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        vm.edit { setOpenSubtitlesApiKey(key); setOpenSubtitlesServerUrl(url) }
                        accountVm.refresh()
                    },
                    modifier = Modifier.padding(vertical = MobileDimens.GapSmall),
                    style = StageButtonStyle.PRIMARY,
                )
                // Back to the shared service in one press; emptying two fields by hand is easy to
                // get half-right, and half-right here means every search fails.
                if (storedKey.isNotBlank() || storedUrl.isNotBlank()) {
                    StageButton(
                        text = stringResource(R.string.settings_remove_custom_access),
                        onClick = {
                            key = ""; url = ""
                            vm.edit { setOpenSubtitlesApiKey(""); setOpenSubtitlesServerUrl("") }
                        },
                        modifier = Modifier.padding(bottom = MobileDimens.GapSmall),
                    )
                }
            }
        }

        settingsSection(R.string.player_subtitles_search) {
            StageSettingRow(
                title = stringResource(R.string.player_subtitles_filter_title),
                subtitle = stringResource(R.string.player_subtitles_filter_description),
                role = Role.Switch,
                // On / off: a switch; the whole row flips it.
                onClick = {
                    // Turning the filter on with nothing chosen would silently behave like "off",
                    // so seed it from the device's language, falling back to English.
                    if (!filterEnabled && searchLang.isBlank()) {
                        vm.edit { setSubSearchLanguages(defaultSearchLang()) }
                    }
                    vm.edit { setSubSearchFilterEnabled(!filterEnabled) }
                },
            ) { StageSwitch(filterEnabled) }
        }
        if (filterEnabled) {
            settingsGroup(key = "os-language") {
                StageSettingRow(
                    title = stringResource(R.string.player_subtitles_search_language),
                    subtitle = stringResource(R.string.player_subtitles_search_language_description),
                    onClick = { showLangPicker = true },
                ) { StageRowValue(subSearchLanguages().firstOrNull { it.first == searchLang }?.second
                        ?: stringResource(R.string.player_subtitles_language_not_set), StageChevron.DOWN) }
            }
        }

        settingsSection(R.string.player_subtitles_downloads) {
            StageSettingRow(
                title = stringResource(R.string.player_subtitles_delete_action),
                subtitle = stringResource(R.string.player_subtitles_delete_description),
                onClick = { showDelete = true },
            )
        }
        settingsNote(R.string.player_subtitles_api_notice)
    }

    if (showSignIn) {
        SignInDialog(
            onDismiss = { showSignIn = false },
            onSubmit = { user, pass, stay ->
                showSignIn = false
                accountVm.signIn(user.trim(), pass, stay)
            },
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.player_subtitles_sign_out),
            message = stringResource(R.string.player_subtitles_delete_login_message),
            confirmLabel = stringResource(R.string.player_subtitles_sign_out),
            onConfirm = { accountVm.signOut(); confirmSignOut = false },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (showLangPicker) {
        SettingsChoiceSheet(
            title = stringResource(R.string.player_subtitles_search_language),
            choices = subSearchLanguages().map { SettingsChoice(it.first, it.second) },
            selected = searchLang,
            onSelect = { code -> vm.edit { setSubSearchLanguages(code) } },
            onDismiss = { showLangPicker = false },
        )
    }

    error?.let { e ->
        ConfirmDialog(
            title = stringResource(R.string.settings_open_subtitles),
            message = when (e.kind) {
                OpenSubtitlesViewModel.ErrorKind.EMPTY_CREDENTIALS ->
                    stringResource(R.string.player_subtitles_enter_credentials)
                OpenSubtitlesViewModel.ErrorKind.INVALID_CREDENTIALS ->
                    stringResource(R.string.player_subtitles_invalid_credentials)
                OpenSubtitlesViewModel.ErrorKind.SERVER_ERROR ->
                    stringResource(R.string.player_subtitles_sign_in_server_error, e.httpCode)
                OpenSubtitlesViewModel.ErrorKind.NETWORK ->
                    stringResource(R.string.player_subtitles_sign_in_network_error)
                OpenSubtitlesViewModel.ErrorKind.REFRESH_NETWORK ->
                    stringResource(R.string.player_subtitles_refresh_network_error)
            },
            confirmLabel = stringResource(R.string.common_ok),
            onConfirm = { accountVm.dismissError() },
            onDismiss = { accountVm.dismissError() },
        )
    }
}

/** Username, password and whether to stay signed in — the whole of what OpenSubtitles asks for. */
@Composable
private fun SignInDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, Boolean) -> Unit,
) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var stay by remember { mutableStateOf(true) }
    // A Stage sheet: what OpenSubtitles asks for, then Sign in / Cancel.
    tv.own.owntv.mobile.ui.components.MobileBottomSheet(onDismissRequest = onDismiss) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.player_subtitles_sign_in_title))
        SheetText(stringResource(R.string.player_subtitles_sign_in_to_use))
        Column(Modifier.padding(horizontal = 6.dp)) {
            StageField(
                value = user,
                onValueChange = { user = it },
                label = stringResource(R.string.player_subtitles_username),
                imeAction = ImeAction.Next,
            )
            StageField(
                value = pass,
                onValueChange = { pass = it },
                label = stringResource(R.string.player_subtitles_password),
                password = true,
            )
        }
        StageSettingRow(
            title = stringResource(R.string.player_subtitles_stay_signed_in),
            role = Role.Switch,
            onClick = { stay = !stay },
        ) { StageSwitch(stay) }
        tv.own.owntv.mobile.ui.components.StageConfirmItems(
            confirm = stringResource(R.string.player_subtitles_sign_in),
            confirmEnabled = user.isNotBlank() && pass.isNotBlank(),
            onConfirm = { onSubmit(user, pass, stay) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * The languages the OpenSubtitles search can be restricted to (the ISO codes their API expects).
 *
 * Its own list on purpose: the embedded-track list is 3-letter codes and far too short for a
 * subtitle library that carries some sixty languages.
 */
private val SUB_SEARCH_LANGUAGE_CODES = listOf(
    "ar", "bg", "zh-cn", "zh-tw", "hr", "cs", "da", "nl", "en", "et", "fi", "fr",
    "de", "el", "he", "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "ms", "no",
    "fa", "pl", "pt-br", "pt-pt", "ro", "ru", "sr", "sk", "sl", "es", "sv", "th",
    "tr", "uk", "vi",
)

@Composable
private fun subSearchLanguages(): List<Pair<String, String>> {
    val displayLocale = LocalConfiguration.current.locales[0]
    return remember(displayLocale) {
        SUB_SEARCH_LANGUAGE_CODES.map { it to Locale.forLanguageTag(it).getDisplayName(displayLocale) }
    }
}

/** The device's language if OpenSubtitles carries it, else English — the seed when first switched on. */
private fun defaultSearchLang(): String {
    val locale = Locale.getDefault()
    val tag = "${locale.language}-${locale.country}".lowercase()
    return SUB_SEARCH_LANGUAGE_CODES.firstOrNull { it == tag }
        ?: SUB_SEARCH_LANGUAGE_CODES.firstOrNull { it == locale.language.lowercase() }
        ?: "en"
}

/**
 * How long until the daily allowance comes back.
 *
 * OpenSubtitles reports this as an epoch, an instant or a countdown depending on the endpoint, and
 * sometimes omits it entirely while the quota is untouched — in which case the next UTC midnight is
 * still a more useful answer than "not set".
 */
@Composable
private fun openSubtitlesResetLabel(raw: String?): String {
    val now = System.currentTimeMillis()
    val target = raw?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
        value.toLongOrNull()?.let { epoch -> if (epoch < 10_000_000_000L) epoch * 1_000L else epoch }
            ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: Regex("^(\\d{1,2}):(\\d{2}):(\\d{2})$").matchEntire(value)?.let { match ->
                now + (
                    match.groupValues[1].toLong() * 3_600L +
                        match.groupValues[2].toLong() * 60L + match.groupValues[3].toLong()
                    ) * 1_000L
            }
    }
    if (raw != null && target == null) return raw

    val resetAt = target ?: ZonedDateTime.now(ZoneOffset.UTC)
        .toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val totalMinutes = ((resetAt - now).coerceAtLeast(0L) / 60_000L).toInt()
    return stringResource(R.string.settings_open_subtitles_reset_in, totalMinutes / 60, totalMinutes % 60)
}
