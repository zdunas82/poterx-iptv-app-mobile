package tv.own.owntv.mobile.ui.profiles

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import tv.own.owntv.mobile.ui.components.BrandMark
import tv.own.owntv.mobile.ui.components.Wordmark
import tv.own.owntv.mobile.ui.components.rememberAppliedIcon
import tv.own.owntv.mobile.ui.shell.ShellAvatar
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageTag
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageBackground
import tv.own.owntv.mobile.ui.stage.stageClickable
import tv.own.owntv.mobile.ui.stage.stageText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons

private val AVATAR_SIZE = 104.dp

/**
 * "Who's watching?" — the chooser the app opens with when more than one person uses it, or when the
 * only profile is PIN-locked.
 *
 * There is no way past it: it is shown instead of the app, not over it, so there is no back gesture
 * that reveals somebody else's library behind it. Choosing an unlocked profile enters immediately;
 * a locked one asks for its PIN first.
 */
@Composable
fun ProfileGate(
    profiles: List<ProfileEntity>,
    onEntered: (ProfileEntity) -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfilesViewModel,
) {
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var addProfile by remember { mutableStateOf(false) }
    val fallbackName = stringResource(R.string.profiles_default_name)

    fun enter(profile: ProfileEntity) = vm.switchTo(profile) { onEntered(profile) }

    // The brand on top, the question and its line, then two columns of 104 dp avatars — the
    // current profile ringed — and the outlined Add profile, on the Stage background.
    val palette = LocalStagePalette.current
    val activeProfileId by vm.activeProfileId.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .stageBackground()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 34.dp, end = 34.dp, top = 72.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B1216)),
                contentAlignment = Alignment.Center,
            ) { BrandMark(rememberAppliedIcon(), 28.dp) }
            Wordmark(110.dp)
        }
        Text(
            text = stringResource(R.string.profiles_gate_title),
            style = stageText(28f, 800),
            color = palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 44.dp),
        )
        Text(
            text = stringResource(R.string.profiles_gate_subtitle),
            style = stageText(12.5f, 500),
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 82.dp),
        )
        val tiles: List<ProfileEntity?> = profiles + null
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                pair.forEach { profile ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (profile == null) {
                            AddProfileTile(onClick = { addProfile = true })
                        } else {
                            ProfileTile(
                                profile = profile,
                                current = profile.id == activeProfileId,
                                onClick = { if (profile.pinHash == null) enter(profile) else pinFor = profile },
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    pinFor?.let { locked ->
        ProfilePinSheet(
            profileName = locked.name,
            onSubmit = { pin ->
                vm.verifyPin(locked, pin).also { if (it) { pinFor = null; enter(locked) } }
            },
            onDismiss = { pinFor = null },
        )
    }
    if (addProfile) {
        ProfileEditorSheet(
            initial = null,
            takenNames = profiles.map { it.name.lowercase() }.toSet(),
            onConfirm = { name, avatarId, isKids, pin, picture ->
                vm.create(name, avatarId, isKids, pin, fallbackName, picture)
            },
            onDismiss = { addProfile = false },
        )
    }
}

/** One person's tile: the 104 dp avatar (ringed when it is the current profile), the name, KIDS or 🔒 PIN. */
@Composable
private fun ProfileTile(profile: ProfileEntity, current: Boolean, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.stageClickable(interaction, onClick, null), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.then(
                if (current) {
                    Modifier.drawBehind {
                        val r = size.minDimension / 2
                        drawCircle(accent.accent.copy(alpha = 0.3f), r + 7.dp.toPx())
                        drawCircle(accent.focus, r + 3.dp.toPx())
                    }
                } else Modifier,
            ),
        ) {
            ShellAvatar(profile, onClick = null, diameter = AVATAR_SIZE)
        }
        Text(
            text = profile.name,
            style = stageText(15f, 700),
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        when {
            profile.isKids -> StageTag(stringResource(R.string.profiles_kids_badge), Modifier.padding(top = 4.dp))
            profile.pinHash != null -> StageTag(stringResource(R.string.profiles_pin), Modifier.padding(top = 4.dp), icon = MobileIcons.Lock)
        }
    }
}

/** The outlined circle with + and Add profile in the muted colour. */
@Composable
private fun AddProfileTile(onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.stageClickable(interaction, onClick, null), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .drawBehind { drawCircle(palette.ink.copy(alpha = 0.2f), size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx())) },
            contentAlignment = Alignment.Center,
        ) {
            StageIcon(MobileIcons.Add, 30.dp, palette.muted)
        }
        Text(
            text = stringResource(R.string.profiles_add),
            style = stageText(15f, 700),
            color = palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
