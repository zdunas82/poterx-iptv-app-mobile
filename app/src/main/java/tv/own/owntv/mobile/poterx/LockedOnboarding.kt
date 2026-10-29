package tv.own.owntv.mobile.poterx

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.setup.displayText
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.core.sync.detailText
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.core.sync.primaryText
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.BrandMark
import tv.own.owntv.mobile.ui.components.Wordmark
import tv.own.owntv.mobile.ui.components.rememberAppliedIcon
import tv.own.owntv.mobile.ui.setup.SetupViewModel
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageField
import tv.own.owntv.mobile.ui.stage.stageBackground
import tv.own.owntv.mobile.ui.stage.stageGlass
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * Poterx: the whole first run on one screen — the television's onboarding in the phone's wizard
 * frame. Stock OwnTV Mobile walks the user through the setup wizard (welcome, display size, the
 * disclaimer, a profile, "add a playlist" with the server typed in). A locked build never shows the
 * server. Two shapes, decided at build time (see [LockedSource]):
 *
 *  - account built in: the profile is created, the playlist imported, the app opens. The only
 *    thing a user can see is the progress line, and "Try again" if the panel was unreachable.
 *  - server built in: the same, after a sign-in page asking for the username and password only.
 *
 * [addingProfile]: a second profile from the "Who's watching?" gate or Settings › Profile. The
 * playlist may already exist then (the same login shares it); a different login — a second list —
 * is imported as a playlist of its own and belongs to the new profile.
 */
@Composable
fun LockedOnboarding(
    addingProfile: Boolean,
    onDone: (Long?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SetupViewModel = koinViewModel()
    val account = LockedSource.account
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val appName = stringResource(R.string.app_name)
    val appContext = LocalContext.current.applicationContext
    val resources = LocalContext.current.resources
    val profileName = stringResource(if (addingProfile) R.string.poterx_profile_added else R.string.poterx_profile_default)
    var profileId by rememberSaveable { mutableLongStateOf(-1L) }
    // The sign-in page's fields, kept across a failed attempt so a typo is fixed, not retyped.
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // Each submission bumps this; the import effect below keys on it.
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    // Whether the sign-in page is showing (server-only build, nothing submitted or the last attempt failed).
    var signingIn by rememberSaveable { mutableStateOf(LockedSource.requiresLogin) }

    // Poterx: before the import, the panel is asked whether these credentials are right. The answer
    // tells a wrong password, an expired line, no internet and an ISP block apart. A phone has no
    // built-in VPN: a block is explained (mobile data or a VPN app gets around it), not worked around.
    val reach: PoterxReachability = koinInject()
    val scope = rememberCoroutineScope()
    var verdict by remember { mutableStateOf<PoterxReachability.Verdict?>(null) }
    /** Wrong-password answers in a row: from the second one the block hint is shown beside the
     *  message, since an ISP's block page served with a 4xx reads exactly like a refused password. */
    var authFailures by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    /** The import failed after the panel had said yes: ask the network why, once per failure. */
    var diagnosed by remember { mutableStateOf<SourceImporter.ImportState.Failed?>(null) }
    val startImport = { vm.reset(); verdict = null; attempt++; signingIn = false }
    val checkSignIn: () -> Unit = {
        if (account != null && !checking) {
            checking = true
            scope.launch {
                val found = reach.signIn(account.server, username.trim(), password, PoterxAccount.userAgent)
                checking = false
                verdict = found
                authFailures = if (found is PoterxReachability.Verdict.AuthFailed) authFailures + 1 else 0
                if (found is PoterxReachability.Verdict.Ok) startImport()
            }
        }
    }
    LaunchedEffect(state) {
        val failed = state as? SourceImporter.ImportState.Failed ?: run { diagnosed = null; verdict = null; return@LaunchedEffect }
        if (diagnosed == failed || account == null) return@LaunchedEffect
        diagnosed = failed
        val sync = (failed.failure as? SourceImporter.SetupFailure.Sync)?.failure
        if (sync == null || sync is tv.own.owntv.core.util.FriendlySyncFailure.AuthenticationRejected) return@LaunchedEffect
        verdict = reach.signIn(account.server, account.username.ifBlank { username.trim() }, account.password.ifBlank { password }, PoterxAccount.userAgent)
    }

    // 1. The profile. Poterx: an empty profile left by an earlier showing of this screen (a cold start
    //    on the sign-in page, a failed attempt) is taken over instead of a new "Dom" beside it; the
    //    others are removed, so the "Who's watching?" gate never lists holes.
    val profileDao: tv.own.owntv.core.database.dao.ProfileDao = koinInject()
    val sourceDao: tv.own.owntv.core.database.dao.SourceDao = koinInject()
    val settings: tv.own.owntv.core.settings.SettingsRepository = koinInject()
    // The importer is app-wide and keeps its last result: this screen starts from a clean slate, or a
    // finished first sign-in would let the next "Add a profile" complete by itself.
    var ready by remember { mutableStateOf(false) }
    // Whether this screen has begun its own import: only then does a Success belong to it.
    var started by rememberSaveable { mutableStateOf(false) }
    val sourceRepository: tv.own.owntv.core.repository.SourceRepository = koinInject()
    val profileManager: tv.own.owntv.core.profile.ProfileManager = koinInject()
    val epgStore: tv.own.owntv.core.epg.EpgSourceStore = koinInject()
    val epgRepository: tv.own.owntv.core.repository.EpgRepository = koinInject()
    LaunchedEffect(Unit) {
        // A screen recreated while its own import runs (a rotation, a font or locale change) must not reset that import.
        if (!(started && vm.state.value is SourceImporter.ImportState.Running)) vm.reset()
        if (profileId >= 0L) {
            // Restored after the process was killed: the importer forgot which profile it was filling.
            if (runCatching { profileDao.getById(profileId) }.getOrNull() != null) {
                vm.useProfile(profileId)
                ready = true
                return@LaunchedEffect
            }
            profileId = -1L
        }
        val leftover = if (addingProfile) null else runCatching { PoterxAccount.pruneEmptyProfiles(profileDao, sourceDao, settings) }.getOrNull()
        if (leftover != null) { vm.useProfile(leftover); profileId = leftover; ready = true }
        else {
            // Profile names stay unique (a backup restore matches profiles by name): "New profile", "New profile 2", …
            val taken = runCatching { profileDao.getAllOnce() }.getOrDefault(emptyList()).map { it.name.trim().lowercase() }.toSet()
            val name = generateSequence(1) { it + 1 }
                .map { if (it == 1) profileName else "$profileName $it" }
                .first { it.lowercase() !in taken }
            vm.createProfile(name, 0, isKids = false, pin = null, picture = null) { profileId = it; ready = true }
        }
    }
    // Cancel while adding a profile: the profile made for it goes too (its links with it; a playlist it
    // was sharing stays with the profile it belongs to), so no "New profile" is left at the gate.
    val cancelAdding: () -> Unit = {
        scope.launch {
            vm.cancelImport()
            // The profile's insert may still be on its way: wait for its id, so that it can be removed too.
            val id = withTimeoutOrNull(3_000) { snapshotFlow { profileId }.first { it >= 0L } } ?: -1L
            withContext(NonCancellable) {
                // Through the profile manager, which also clears what the profile left behind.
                if (id >= 0L) runCatching { profileDao.getById(id)?.let { profileManager.delete(it) } }
                // A playlist the cancelled import began and could not remove goes with it.
                PoterxAccount.pruneOrphanSourcesSoon()
            }
            onCancel()
        }
    }
    if (addingProfile) BackHandler { cancelAdding() }
    // 2. The playlist. With a sign-in page the customer's own login decides: the same login as a
    //    playlist that exists (any profile, PIN or not) shares it; a different login — a second list —
    //    is imported as a playlist of its own. With the account built in there is one account, shared.
    //    A playlist the profile already holds (an earlier try linked it before its refresh failed) is
    //    refreshed again, never added a second time.
    LaunchedEffect(profileId, attempt, signingIn, ready) {
        if (!ready || profileId < 0L || account == null || signingIn) return@LaunchedEffect
        // An import this screen began before it was recreated is still running: it is not started again.
        if (vm.state.value is SourceImporter.ImportState.Running) return@LaunchedEffect
        val typed = LockedSource.requiresLogin
        val login = account.username.ifBlank { username.trim() }
        val typedPassword = account.password.ifBlank { password }
        val mine = runCatching { sourceDao.sourceIdsForProfile(profileId) }.getOrDefault(emptyList()).toSet()
        val found = runCatching { sourceDao.getAllOnce() }.getOrDefault(emptyList())
            .filter { it.type == tv.own.owntv.core.model.SourceType.XTREAM }
            .filter { !typed || it.username.orEmpty().trim().equals(login, ignoreCase = true) }
        val share = found.firstOrNull { it.id in mine } ?: found.firstOrNull()
        started = true
        if (share != null) {
            // The password just checked with the panel replaces an old one (the provider reset it),
            // for every profile that shares this playlist.
            if (typed && share.password != typedPassword) {
                val fresh = runCatching { sourceDao.getAllOnce().firstOrNull { it.id == share.id } }.getOrNull() ?: share
                val updated = fresh.copy(password = typedPassword)
                val oldUrls = epgRepository.guideUrls(fresh)
                runCatching { sourceRepository.updateSource(updated) }
                // The guide feed's address carries the password: the feed is repointed, so the guide keeps
                // refreshing from the same feed instead of a dead one and a second made at the next start.
                val newUrls = epgRepository.guideUrls(updated)
                if (oldUrls.size == newUrls.size) {
                    runCatching {
                        epgStore.getAll().forEach { feed ->
                            val at = oldUrls.indexOf(feed.url)
                            if (at >= 0 && newUrls[at] != feed.url) epgStore.update(feed.copy(url = newUrls[at]))
                        }
                    }
                }
            }
            // A playlist whose first refresh never finished (the phone died mid-import) holds partial
            // rows, and core's first refresh assumes empty tables: they are cleared before it runs again.
            if (share.lastSyncAt == null) runCatching { sourceRepository.clearSourceContent(share.id) }
            vm.linkExisting(setOf(share.id))
        } else {
            vm.startXtream(
                // A second list is named after its login, so the two read apart in Settings › Playlists.
                name = if (typed && addingProfile) "$appName · $login" else appName,
                server = account.server,
                username = login,
                password = typedPassword,
                // Every request to the panel carries this app's name, so the provider can tell it apart.
                userAgent = PoterxAccount.userAgent,
                httpReferer = "",
                // The channel list refreshes itself twice a day; Settings › Playlists changes that.
                autoRefresh = PlaylistRefresh(PlaylistAutoRefresh.HOURS_12),
                live = SyncScopeChoice.Now,
                movies = SyncScopeChoice.Now,
                series = SyncScopeChoice.Now,
                preferHls = account.preferHls,
            )
        }
    }
    // 3. Straight into the app; the wizard's "All set" page has nothing a locked build needs to say.
    //    The wizard would offer "Add a TV guide"; a locked build just starts the panel's guide, in the
    //    background, while the app opens. Linking an existing playlist (a second profile) carries no
    //    source here, and its guide is already registered from the first import.
    LaunchedEffect(state) {
        val done = state as? SourceImporter.ImportState.Success ?: return@LaunchedEffect
        if (!started) return@LaunchedEffect
        // The new profile gets the module defaults now, not at the next start.
        runCatching { PoterxDefaults.applyProfileDefaults(appContext, settings, profileDao) }
        // A cancel that landed meanwhile has removed the profile: nothing below may activate it.
        ensureActive()
        done.source?.let { vm.syncGuideNow(it) }
        vm.finish(onDone)
    }

    val palette = LocalStagePalette.current
    Box(modifier.fillMaxSize().stageBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(rememberAppliedIcon(), 28.dp)
                Wordmark(80.dp)
            }
            Column(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 44.dp)
                    .fillMaxWidth()
                    .stageGlass(RoundedCornerShape(26.dp))
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            ) {
                val s = state
                when {
                    account == null -> Text(
                        stringResource(R.string.poterx_no_account),
                        style = stageText(24f, 800, letterSpacing = (-0.5f / 24f).em), color = palette.text,
                    )
                    signingIn -> {
                        val canSubmit = username.isNotBlank() && password.isNotBlank() && !checking
                        val submit = { if (canSubmit) checkSignIn() }
                        val blocked = verdict as? PoterxReachability.Verdict.Blocked
                        val maybeBlocked = blocked == null && authFailures >= 2
                        val another = addingProfile && LockedSource.requiresLogin
                        Text(
                            stringResource(
                                when {
                                    blocked != null -> R.string.poterx_blocked_title
                                    another -> R.string.poterx_login_title_another
                                    else -> R.string.poterx_login_title
                                },
                            ),
                            style = stageText(24f, 800, letterSpacing = (-0.5f / 24f).em), color = palette.text,
                        )
                        // After a failed attempt the reason replaces the invitation: the panel's answer to
                        // these credentials, or what the network diagnosis found.
                        val reason = when {
                            checking -> stringResource(R.string.poterx_login_checking)
                            else -> verdictText(verdict) ?: (s as? SourceImporter.ImportState.Failed)?.failure?.displayText(resources)
                        }
                        val warning = reason != null && !checking
                        Text(
                            reason ?: stringResource(if (another) R.string.poterx_login_body_another else R.string.poterx_login_body),
                            style = stageText(12.5f, 500, lineHeight = 1.45f),
                            color = if (warning) palette.danger else palette.muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (warning && verdict !is PoterxReachability.Verdict.AuthFailed) LockedContactLine()
                        if (maybeBlocked && warning) Text(
                            stringResource(R.string.poterx_login_wrong_twice),
                            style = stageText(12.5f, 500, lineHeight = 1.45f), color = palette.muted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Column(Modifier.padding(top = 14.dp)) {
                            StageField(
                                value = username,
                                onValueChange = { username = it },
                                label = stringResource(R.string.setup_username),
                                imeAction = ImeAction.Next,
                            )
                            StageField(
                                value = password,
                                onValueChange = { password = it },
                                label = stringResource(R.string.setup_password),
                                password = true,
                                imeAction = ImeAction.Done,
                                onImeDone = submit,
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (addingProfile) StageButton(onClick = cancelAdding, text = stringResource(R.string.common_cancel), style = StageButtonStyle.PLAIN)
                            Spacer(Modifier.weight(1f))
                            StageButton(onClick = submit, text = stringResource(R.string.setup_continue), style = StageButtonStyle.PRIMARY, enabled = canSubmit)
                        }
                    }
                    s is SourceImporter.ImportState.Failed -> {
                        val blocked = verdict as? PoterxReachability.Verdict.Blocked
                        Text(
                            stringResource(if (blocked != null) R.string.poterx_blocked_title else R.string.setup_import_failed),
                            style = stageText(24f, 800, letterSpacing = (-0.5f / 24f).em), color = palette.text,
                        )
                        Text(
                            verdictText(verdict) ?: s.failure.displayText(resources),
                            style = stageText(12.5f, 500, lineHeight = 1.45f), color = palette.muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        LockedContactLine()
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (addingProfile) StageButton(onClick = cancelAdding, text = stringResource(R.string.common_cancel), style = StageButtonStyle.PLAIN)
                            Spacer(Modifier.weight(1f))
                            StageButton(
                                // A server-only build goes back to the sign-in page (the password may be the reason).
                                onClick = { vm.reset(); verdict = null; if (LockedSource.requiresLogin) signingIn = true else attempt++ },
                                text = stringResource(R.string.setup_try_again),
                                style = StageButtonStyle.PRIMARY,
                            )
                        }
                    }
                    else -> {
                        val display = progress?.importProgressDisplay()
                        Text(
                            stringResource(R.string.poterx_connecting),
                            style = stageText(24f, 800, letterSpacing = (-0.5f / 24f).em), color = palette.text,
                        )
                        val title = display?.primaryText(resources) ?: stringResource(R.string.setup_preparing_catalog)
                        Text(title, style = stageText(12.5f, 500, lineHeight = 1.45f), color = palette.muted, modifier = Modifier.padding(top = 4.dp))
                        display?.detailText(resources)?.takeIf { it.isNotBlank() && it != title }?.let {
                            Text(it, style = stageText(11.5f, 500), color = palette.dim, modifier = Modifier.padding(top = 4.dp))
                        }
                        // Adding a profile can be given up while it connects; a first sign-in has nowhere to go back to.
                        if (addingProfile) {
                            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                StageButton(onClick = cancelAdding, text = stringResource(R.string.common_cancel), style = StageButtonStyle.PLAIN)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The sentence for a diagnosis, or null when there is nothing (yet) to say. */
@Composable
private fun verdictText(verdict: PoterxReachability.Verdict?): String? = when (verdict) {
    null, PoterxReachability.Verdict.Ok -> null
    PoterxReachability.Verdict.AuthFailed -> stringResource(R.string.poterx_login_wrong)
    is PoterxReachability.Verdict.Expired -> stringResource(R.string.poterx_login_expired)
    PoterxReachability.Verdict.Offline -> stringResource(R.string.poterx_login_offline)
    PoterxReachability.Verdict.PanelDown -> stringResource(R.string.poterx_login_panel_down)
    is PoterxReachability.Verdict.Blocked -> blockText(verdict.how)
}

@Composable
internal fun blockText(how: PoterxReachability.Block): String = stringResource(
    when (how) {
        PoterxReachability.Block.DNS -> R.string.poterx_blocked_dns
        PoterxReachability.Block.CONNECTION -> R.string.poterx_blocked_connection
        PoterxReachability.Block.PAGE -> R.string.poterx_blocked_page
    },
)

/** The provider's contact line under a failure, when the build carries one: who to call about it. */
@Composable
private fun LockedContactLine() {
    val contact = LockedSource.account?.contact.orEmpty()
    if (contact.isBlank()) return
    Text(
        stringResource(R.string.poterx_contact_line, contact),
        style = stageText(12.5f, 700, lineHeight = 1.45f), color = LocalStagePalette.current.text,
        modifier = Modifier.padding(top = 6.dp),
    )
}
