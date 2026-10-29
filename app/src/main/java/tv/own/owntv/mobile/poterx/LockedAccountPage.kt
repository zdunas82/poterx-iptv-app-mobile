package tv.own.owntv.mobile.poterx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.repository.SourceTestResult
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.screens.settings.customize.TextPromptDialog
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.settings.PageNote
import tv.own.owntv.mobile.ui.screens.settings.SettingsCrumb
import tv.own.owntv.mobile.ui.screens.settings.SettingsGroup
import tv.own.owntv.mobile.ui.screens.settings.SettingsStagePage
import tv.own.owntv.mobile.ui.screens.settings.SettingsViewModel
import tv.own.owntv.mobile.ui.screens.settings.StageConfirmSheet
import tv.own.owntv.mobile.ui.stage.StageChevron
import tv.own.owntv.mobile.ui.stage.StageRowValue
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSwitch
import java.text.DateFormat

/**
 * Poterx: Settings › Sources & guide › Account on a locked build. The account is the one thing the
 * user has here that is theirs: who is signed in, what the panel says about the line (status, expiry,
 * connections) and a way to sign out so another account can sign in on the same phone. The server is
 * never printed. Signing out asks for the service PIN when the build carries one.
 */
@Composable
fun LockedAccountPage(modifier: Modifier = Modifier, vm: SettingsViewModel = koinViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val test by vm.sourceTest.collectAsStateWithLifecycle()
    val source = sources.firstOrNull()
    val formatDate = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val scope = rememberCoroutineScope()
    val sourceDao = koinInject<tv.own.owntv.core.database.dao.SourceDao>()
    val sourceRepository = koinInject<tv.own.owntv.core.repository.SourceRepository>()
    val profileDao = koinInject<tv.own.owntv.core.database.dao.ProfileDao>()
    val epgStore = koinInject<tv.own.owntv.core.epg.EpgSourceStore>()
    val settings = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val telemetry = koinInject<PoterxTelemetry>()
    val contact = LockedSource.account?.contact.orEmpty()
    val servicePin = LockedSource.account?.servicePin.orEmpty()
    var confirmSignOut by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var pinWrong by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    val updater = koinInject<AppUpdater>() as? PoterxUpdater
    var testChannel by remember { mutableStateOf(updater?.testChannel == true) }
    var askTestPin by remember { mutableStateOf(false) }
    var testPinWrong by remember { mutableStateOf(false) }

    // The panel is asked once when the page opens, and again on a tap of the status row.
    LaunchedEffect(source?.id) { source?.let { vm.testSource(it) } }

    val result = (test as? SettingsViewModel.SourceTestState.Done)?.result
    val ok = result as? SourceTestResult.Ok
    val statusText = when {
        test is SettingsViewModel.SourceTestState.Running -> stringResource(R.string.poterx_account_checking)
        // The panel's own word, translated when it is one of the usual ones; anything else as written.
        ok != null -> when (ok.status?.trim()?.lowercase()) {
            null, "", "active" -> stringResource(R.string.poterx_account_active)
            "expired" -> stringResource(R.string.poterx_account_expired)
            "banned", "disabled" -> stringResource(R.string.poterx_account_blocked)
            else -> ok.status.orEmpty()
        }
        result is SourceTestResult.Expired -> stringResource(R.string.poterx_account_expired)
        result is SourceTestResult.AuthFailed -> stringResource(R.string.poterx_account_rejected)
        result is SourceTestResult.Unreachable -> stringResource(R.string.poterx_account_unreachable)
        else -> stringResource(R.string.poterx_account_unknown)
    }
    val expiryText = ok?.expiryText?.takeIf { it.isNotBlank() }
        ?: ok?.expiryMs?.let { formatDate.format(it) }
        ?: (result as? SourceTestResult.Expired)?.expiryMs?.let { formatDate.format(it) }
        ?: if (ok != null) stringResource(R.string.poterx_account_no_expiry) else null
    val connectionsText = ok?.let {
        when {
            it.maxConnections > 0 && it.activeConnections >= 0 -> stringResource(R.string.poterx_account_connections_of, it.activeConnections, it.maxConnections)
            it.maxConnections > 0 -> it.maxConnections.toString()
            else -> null
        }
    }

    SettingsCrumb(stringResource(R.string.poterx_account_title), SettingsGroup.SOURCES)
    SettingsStagePage(modifier) {
        if (source == null) {
            item(key = "none") { PageNote(stringResource(R.string.poterx_no_account)) }
        } else {
            // The login is the customer's own on a build that asks for it. A build with the account built
            // in carries the provider's: it never shows on a customer's screen.
            if (LockedSource.requiresLogin) item(key = "user") {
                StageSettingRow(
                    title = stringResource(R.string.setup_username),
                    subtitle = stringResource(R.string.poterx_account_user_description),
                    icon = MobileIcons.Person,
                    role = null,
                ) { StageRowValue(source.username.orEmpty(), plain = true) }
            }
            item(key = "status") {
                StageSettingRow(
                    title = stringResource(R.string.poterx_account_status),
                    subtitle = if (ok?.trial == true) stringResource(R.string.poterx_account_trial) else stringResource(R.string.poterx_account_status_description),
                    icon = MobileIcons.Refresh,
                    onClick = { vm.testSource(source) },
                ) { StageRowValue(statusText) }
            }
            if (expiryText != null) item(key = "expiry") {
                StageSettingRow(
                    title = stringResource(R.string.poterx_account_expiry),
                    subtitle = stringResource(R.string.poterx_account_expiry_description),
                    icon = MobileIcons.CalendarMonth,
                    role = null,
                ) { StageRowValue(expiryText, plain = true) }
            }
            if (connectionsText != null) item(key = "connections") {
                StageSettingRow(
                    title = stringResource(R.string.poterx_account_connections),
                    subtitle = stringResource(R.string.poterx_account_connections_description),
                    icon = MobileIcons.Wifi,
                    role = null,
                ) { StageRowValue(connectionsText, plain = true) }
            }
            // The provider's own phones read the test channel of the update server.
            if (updater != null) item(key = "test_channel") {
                StageSettingRow(
                    title = stringResource(R.string.poterx_test_channel),
                    subtitle = stringResource(R.string.poterx_test_channel_description),
                    icon = MobileIcons.Download,
                    onClick = {
                        if (testChannel) { updater.testChannel = false; testChannel = false }
                        else if (servicePin.isBlank()) { updater.testChannel = true; testChannel = true }
                        else askTestPin = true
                    },
                ) { StageSwitch(testChannel) }
            }
            // The installation's random id, for a support call ("which phone is it?").
            item(key = "device") {
                StageSettingRow(
                    title = stringResource(R.string.poterx_device_id),
                    subtitle = stringResource(R.string.poterx_device_id_description),
                    icon = MobileIcons.Info,
                    role = null,
                ) { StageRowValue(telemetry.shortId, plain = true) }
            }
            item(key = "sign_out") {
                StageSettingRow(
                    title = stringResource(R.string.poterx_sign_out),
                    subtitle = stringResource(R.string.poterx_sign_out_description),
                    icon = MobileIcons.Close,
                    onClick = { if (!signingOut) confirmSignOut = true },
                ) { StageRowValue("", StageChevron.FORWARD) }
            }
        }
        if (contact.isNotBlank()) item(key = "contact") { PageNote(stringResource(R.string.poterx_contact_line, contact)) }
    }

    val signOut: () -> Unit = {
        signingOut = true
        scope.launch {
            // Emptying the profile list switches the app to the sign-in page and this page leaves the
            // composition, which cancels this scope: the rest of the sign-out must run regardless.
            withContext(NonCancellable) {
                runCatching { vm.dismissSourceTest() }
                runCatching { PoterxAccount.signOut(sourceDao, sourceRepository, profileDao, epgStore, settings) }
            }
            signingOut = false
        }
    }
    if (confirmSignOut) {
        StageConfirmSheet(
            title = stringResource(R.string.poterx_sign_out),
            text = stringResource(R.string.poterx_sign_out_body),
            confirm = stringResource(R.string.poterx_sign_out),
            danger = true,
            onConfirm = {
                confirmSignOut = false
                if (servicePin.isNotBlank()) { pinWrong = false; askPin = true } else signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
    if (askPin) {
        ServicePinPrompt(
            wrong = pinWrong,
            onSubmit = { pin -> if (pin == servicePin) { askPin = false; signOut() } else pinWrong = true },
            onDismiss = { askPin = false },
        )
    }
    if (askTestPin && updater != null) {
        ServicePinPrompt(
            wrong = testPinWrong,
            onSubmit = { pin -> if (pin == servicePin) { askTestPin = false; testPinWrong = false; updater.testChannel = true; testChannel = true } else testPinWrong = true },
            onDismiss = { askTestPin = false; testPinWrong = false },
        )
    }
}

/** The service PIN, asked in the plain text prompt the Customize pages use; a wrong one re-asks with the "wrong PIN" title. */
@Composable
private fun ServicePinPrompt(wrong: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    TextPromptDialog(
        title = if (wrong) stringResource(R.string.profiles_wrong_pin) else stringResource(R.string.poterx_service_pin),
        initial = "",
        onDismiss = onDismiss,
        onConfirm = onSubmit,
        allowBlank = false,
        confirmLabel = stringResource(R.string.common_ok),
    )
}
