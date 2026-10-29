package tv.own.owntv.mobile.poterx

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageClickable
import tv.own.owntv.mobile.ui.stage.stageText
import java.text.DateFormat
import kotlin.math.ceil

/** Warn this many days before the panel's expiry date, and every day after it. */
const val POTERX_EXPIRY_WARN_DAYS = 7

/** The provider's support group; the About page and the expiry card open it. */
const val POTERX_TELEGRAM_LINK = "t.me/+mzemQ68cEhA2ZmQ0"

private const val EXPIRY_CHECK_DELAY_MS = 8_000L
private const val DAY_MS = 24L * 60 * 60 * 1000
private const val PREFS = "poterx_expiry"
private const val KEY_SHOWN_DAY = "shown_day"

private class ExpiryNotice(val expiryMs: Long, val daysLeft: Int)

/**
 * Poterx: the "subscription ends soon" sheet. Shortly after start the panel is asked for the
 * line's expiry date; within [POTERX_EXPIRY_WARN_DAYS] of it (or past it) a sheet names the date,
 * the days left and how to renew: the provider's contact line and the Telegram group, which a tap
 * opens (a phone has a browser; the television could only show a QR code). Shown at most once a
 * day per playlist, and only when nothing is playing fullscreen ([canShow]) — a notice found while a
 * channel is up waits for the player to close.
 */
@Composable
fun PoterxExpiryNotice(canShow: Boolean) {
    if (!LockedSource.enabled) return
    val context = LocalContext.current
    val sourceDao = koinInject<SourceDao>()
    val settings = koinInject<SettingsRepository>()
    val xtream = koinInject<XtreamClient>()
    var notice by remember { mutableStateOf<ExpiryNotice?>(null) }
    LaunchedEffect(Unit) {
        delay(EXPIRY_CHECK_DELAY_MS)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = System.currentTimeMillis() / DAY_MS
        // The profile in use: a second list has its own subscription, and its own once-a-day notice.
        val source = withContext(Dispatchers.IO) { PoterxAccount.activeXtreamSource(sourceDao, settings) }
            ?: return@LaunchedEffect
        val shownKey = "${KEY_SHOWN_DAY}_${source.id}"
        if (prefs.getLong(shownKey, -1L) == today) return@LaunchedEffect
        val found = withContext(Dispatchers.IO) {
            runCatching { xtream.accountExpiryMs(source) }.getOrNull()
        } ?: return@LaunchedEffect
        val remaining = found - System.currentTimeMillis()
        val daysLeft = if (remaining <= 0L) 0 else ceil(remaining / DAY_MS.toDouble()).toInt()
        if (daysLeft > POTERX_EXPIRY_WARN_DAYS) return@LaunchedEffect
        prefs.edit().putLong(shownKey, today).apply()
        notice = ExpiryNotice(found, daysLeft)
    }
    val current = notice ?: return
    if (!canShow) return
    ExpirySheet(current, onDismiss = { notice = null })
}

@Composable
private fun ExpirySheet(notice: ExpiryNotice, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalStagePalette.current
    val date = remember(notice.expiryMs) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(notice.expiryMs) }
    val expired = notice.daysLeft <= 0
    val title = if (expired) stringResource(R.string.poterx_expiry_expired_title)
    else pluralStringResource(R.plurals.poterx_expiry_days_title, notice.daysLeft, notice.daysLeft)
    val body = if (expired) stringResource(R.string.poterx_expiry_expired_body, date)
    else stringResource(R.string.poterx_expiry_body, date)
    val contact = LockedSource.account?.contact.orEmpty()
    MobileBottomSheet(onDismissRequest = onDismiss) {
        StageSheetHeader(title, eyebrow = stringResource(R.string.poterx_account_title), subtitle = body)
        // The Telegram card the About page draws: the QR from the address, the group, the address in
        // the accent; the whole card opens it.
        val interaction = remember { MutableInteractionSource() }
        Row(
            Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.ink.copy(alpha = 0.045f))
                .stageClickable(interaction, { openPoterxLink(context, "https://$POTERX_TELEGRAM_LINK") }, null)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            remember { CompanionLink.renderQr("https://$POTERX_TELEGRAM_LINK") }?.let { qr ->
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.settings_telegram_qr),
                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp),
                )
            }
            Column {
                Text(stringResource(R.string.poterx_expiry_renew), style = stageText(14f, 800), color = palette.text)
                Text(POTERX_TELEGRAM_LINK, style = stageText(11f, 500), color = stageAccent.accent)
                if (contact.isNotBlank()) {
                    Text(stringResource(R.string.poterx_contact_line, contact), style = stageText(11f, 500), color = palette.muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp), horizontalArrangement = Arrangement.End) {
            StageButton(onClick = onDismiss, text = stringResource(R.string.common_ok), style = StageButtonStyle.PRIMARY)
        }
    }
}

/** Opens [url] in whatever handles it; a phone with no browser gets nothing rather than a crash. */
internal fun openPoterxLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
