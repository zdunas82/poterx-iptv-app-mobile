package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.network.DnsConfig
import tv.own.owntv.core.network.DohPresets
import tv.own.owntv.core.network.ProxyConfig
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.mobile.ui.stage.StageTextRow

/*
 * Settings › App › Proxy and DNS, the group-page pattern: a switch, the fields as rows, then Save and Test.
 * Both are forms with a Save, not live-writing rows: a proxy written on every keystroke goes through "prox",
 * "proxy.", "proxy.e"… and each half-address takes the whole app offline while it stands. Test uses what is
 * typed, so a setting can be proven before it is kept.
 */

@Composable
fun SettingsProxyPage(modifier: Modifier = Modifier, vm: SettingsViewModel = koinViewModel()) {
    val proxy = vm.settings.proxyConfig.pref(ProxyConfig())
    val test = vm.proxyTest.pref(NetworkTestState.Idle)
    var on by remember { mutableStateOf(proxy.enabled) }
    var host by remember { mutableStateOf(proxy.host) }
    var port by remember { mutableStateOf(proxy.port.toString()) }
    var user by remember { mutableStateOf(proxy.username) }
    var password by remember { mutableStateOf(proxy.password) }
    // Seeded once from what is stored; after that the fields are the user's until Save writes them.
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(proxy) {
        if (!seeded) {
            on = proxy.enabled; host = proxy.host; port = proxy.port.toString(); user = proxy.username; password = proxy.password
            seeded = true
        }
    }
    val portNumber = port.toIntOrNull() ?: 0
    SettingsStagePage(modifier) {
        item(key = "note") { PageNote(stringResource(R.string.settings_proxy_description)) }
        item(key = "on") {
            SettingItemRow(switchItem("proxy_on", MobileIcons.Translate, stringResource(R.string.settings_use_proxy), null, on) { on = it }, {}, null)
        }
        item(key = "host") { StageTextRow(MobileIcons.Translate, stringResource(R.string.settings_host), host, { host = it }, placeholder = stringResource(R.string.settings_proxy_host_hint)) }
        item(key = "port") {
            StageTextRow(
                MobileIcons.Edit, stringResource(R.string.settings_port), port, { port = it.filter { c -> c.isDigit() }.take(5) },
                placeholder = stringResource(R.string.settings_proxy_port_hint), keyboardType = KeyboardType.Number,
            )
        }
        item(key = "user") { StageTextRow(MobileIcons.Person, stringResource(R.string.settings_username_optional), user, { user = it }) }
        item(key = "pass") { StageTextRow(MobileIcons.Lock, stringResource(R.string.settings_password_optional), password, { password = it }, password = true) }
        item(key = "buttons") {
            FormButtons(
                testLabel = stringResource(R.string.settings_test_proxy),
                testing = test == NetworkTestState.Testing,
                onSave = { vm.saveProxy(on, host, portNumber, user, password) },
                onTest = { vm.testProxy(host, portNumber, user, password) },
            )
        }
        item(key = "result") { TestResult(test, R.string.settings_proxy_connected) }
        item(key = "privacy") { PageNote(stringResource(R.string.settings_proxy_privacy)) }
        item(key = "limits") { PageNote(stringResource(R.string.settings_proxy_limitations)) }
    }
}

@Composable
fun SettingsDnsPage(modifier: Modifier = Modifier, vm: SettingsViewModel = koinViewModel()) {
    val dns = vm.settings.dnsConfig.pref(DnsConfig())
    val test = vm.dnsTest.pref(NetworkTestState.Idle)
    var on by remember { mutableStateOf(dns.enabled) }
    var server by remember { mutableStateOf(dns.serverText()) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(dns) {
        if (!seeded) { on = dns.enabled; server = dns.serverText(); seeded = true }
    }
    SettingsStagePage(modifier) {
        item(key = "note") { PageNote(stringResource(R.string.settings_dns_toggle_description)) }
        item(key = "on") {
            SettingItemRow(switchItem("dns_on", MobileIcons.Translate, stringResource(R.string.settings_dns_use_custom), null, on) { on = it }, {}, null)
        }
        item(key = "server") { StageTextRow(MobileIcons.Translate, stringResource(R.string.settings_dns_server), server, { server = it }, placeholder = stringResource(R.string.settings_dns_server_hint)) }
        // The well-known DoH endpoints, so nobody has to type one from memory.
        item(key = "presets") {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DohPresets.all.forEach { (name, url) -> StageTool(onClick = { server = url }, label = name, on = server == url) }
            }
        }
        item(key = "buttons") {
            FormButtons(
                testLabel = stringResource(R.string.settings_dns_test),
                testing = test == NetworkTestState.Testing,
                onSave = { val (h, p, doh) = server.asDnsServer(); vm.saveDns(on && server.isNotBlank(), h, p, doh) },
                onTest = { val (h, p, doh) = server.asDnsServer(); vm.testDns(h, p, doh) },
            )
        }
        item(key = "result") { TestResult(test, R.string.settings_dns_resolved) }
        if (on && server.isBlank()) {
            item(key = "missing") {
                Text(stringResource(R.string.settings_dns_server_missing), style = stageText(11.5f, 600), color = LocalStagePalette.current.danger, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        item(key = "explain") { PageNote(stringResource(R.string.settings_dns_explanation)) }
        item(key = "limits") { PageNote(stringResource(R.string.settings_dns_limitations)) }
    }
}

/** Save (primary) and Test, side by side under a form. */
@Composable
private fun FormButtons(testLabel: String, testing: Boolean, onSave: () -> Unit, onTest: () -> Unit) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StageButton(onClick = onSave, text = stringResource(R.string.common_save), style = StageButtonStyle.PRIMARY, modifier = Modifier.weight(1f))
        StageButton(onClick = onTest, text = testLabel, enabled = !testing, modifier = Modifier.weight(1f))
    }
}

/**
 * One field holds both DNS modes, the way the TV app's does: an `https://` address is DoH, anything else is a
 * plain server with an optional `:port`.
 */
private fun DnsConfig.serverText(): String = when {
    dohUrl.isNotBlank() -> dohUrl
    host.isBlank() -> ""
    port > 0 && port != DnsConfig.DNS_DEFAULT_PORT -> "$host:$port"
    else -> host
}

private fun String.asDnsServer(): Triple<String, Int, String> {
    val s = trim()
    if (s.startsWith("https://", ignoreCase = true)) return Triple("", DnsConfig.DNS_DEFAULT_PORT, s)
    // Only a single colon is a port — an IPv6 literal has several and keeps its default port.
    val colon = s.lastIndexOf(':')
    if (colon > 0 && s.indexOf(':') == colon) {
        return Triple(s.substring(0, colon).trim(), s.substring(colon + 1).toIntOrNull() ?: DnsConfig.DNS_DEFAULT_PORT, "")
    }
    return Triple(s, DnsConfig.DNS_DEFAULT_PORT, "")
}

/** One line under the buttons: testing, how fast it was, or why it failed. */
@Composable
private fun TestResult(state: NetworkTestState, okRes: Int) {
    val palette = LocalStagePalette.current
    val text = when (state) {
        NetworkTestState.Idle -> return
        NetworkTestState.Testing -> stringResource(R.string.settings_testing)
        is NetworkTestState.Ok -> stringResource(okRes, state.millis.toInt())
        is NetworkTestState.Failed -> state.argument?.let { stringResource(state.messageRes, it) } ?: stringResource(state.messageRes)
    }
    Text(
        text,
        style = stageText(12.5f, 600),
        color = if (state is NetworkTestState.Failed) palette.danger else palette.muted,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
