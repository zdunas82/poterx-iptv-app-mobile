package tv.own.owntv.mobile.poterx

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * Poterx: what the app shows in place of Add a source, Backup & Restore and Local sync on a locked
 * build. Each of those could reveal, edit or copy the built-in account, so they are replaced by this
 * one page.
 */
@Composable
fun LockedInfoPage(modifier: Modifier = Modifier) {
    val palette = LocalStagePalette.current
    Column(modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 24.dp)) {
        Text(stringResource(R.string.poterx_locked_title), style = stageText(18f, 800), color = palette.text)
        Text(
            stringResource(R.string.poterx_locked_body, stringResource(R.string.app_name)),
            style = stageText(12.5f, 500, lineHeight = 1.5f),
            color = palette.muted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
