package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.core.database.entity.FOLLOW_GLOBAL_LATENCY_SECS
import tv.own.owntv.core.database.entity.FOLLOW_GLOBAL_PREROLL
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES
import tv.own.owntv.core.live.MAX_MULTIVIEW_TILES
import tv.own.owntv.core.live.MIN_MULTIVIEW_TILES
import tv.own.owntv.core.player.EnginePreference
import tv.own.owntv.core.player.FfmpegAudioSetting
import tv.own.owntv.core.player.SurroundMode
import tv.own.owntv.core.player.TrackLanguages
import tv.own.owntv.core.recording.RecordingSchedule
import tv.own.owntv.core.settings.LiveBuffer
import tv.own.owntv.core.settings.LiveLatency
import tv.own.owntv.core.settings.SeekSteps
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.timeshift.TimeshiftRules
import tv.own.owntv.core.ui.findActivity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.recordings.RecordingsViewModel
import tv.own.owntv.mobile.ui.stage.StagePlaylistMark
import tv.own.owntv.player.LiveLadder
import tv.own.owntv.player.ZoomMode

/*
 * The five groups that were the Video player level: Player, Picture, Sound & subtitles, Live TV,
 * Watching & recording. The stored values are core's, under the TV's keys, so a backup means the same thing on
 * both apps and a pin made on one is the same row on the other.
 */

// ----------------------------------------------------------------------------------------------- Player

@Composable
internal fun playerSections(vm: SettingsViewModel): List<SettingSection> {
    val g = SettingsGroup.PLAYER
    val s = vm.settings
    val sources by vm.sources.collectAsStateWithLifecycle()
    val livePins by vm.livePinCount.collectAsStateWithLifecycle()
    val vodPins by vm.vodEnginePinCount.collectAsStateWithLifecycle()
    val liveEngine = s.liveEnginePreference.pref(EnginePreference.EXO_FIRST)
    val vodEngine = s.vodEnginePreference.pref(EnginePreference.MPV_FIRST)
    val buffer = s.vodBufferSecs.pref(0)
    val timeout = s.vodNetworkTimeoutSecs.pref(0)
    val reconnects = s.vodReconnects.pref(1)
    val mobileQuality = s.mobileDataMaxVideoHeight.pref(0)
    val externalOn = s.externalPlayerLive.pref(false) || s.externalPlayerMovies.pref(false) || s.externalPlayerSeries.pref(false)
    val engines = EnginePreference.entries.map { SettingsChoice(it, engineLabel(it)) }
    return listOf(
        SettingSection(
            stringResource(R.string.settings_vp_cat_player),
            listOfNotNull(
                choiceItem(
                    "vp_live_engine", MobileIcons.LiveTv, g, stringResource(R.string.settings_live_tv_player),
                    stringResource(R.string.settings_line_live_engine), stringResource(R.string.settings_help_live_engine),
                    engines, liveEngine, EnginePreference.EXO_FIRST, { p -> vm.edit { setLiveEnginePreference(p) } },
                ),
                if (sources.isNotEmpty()) pageItem(
                    "vp_live_engine_sources", MobileIcons.Layers, stringResource(R.string.settings_live_engine_per_playlist),
                    stringResource(R.string.settings_line_override_playlist),
                    overrideCountLabel(sources.count { it.liveEnginePreference != null }), SettingsLeaf.LIVE_ENGINE_SOURCES.route,
                ) else null,
                savedItem(
                    "vp_reset_live_pins", R.string.settings_title_saved_live_choices, R.string.settings_line_saved_live_choices, livePins,
                    R.string.settings_reset_live_player_choices_confirm, R.string.settings_reset_live_player_choices_confirm_description,
                ) { vm.clearLivePins() },
                choiceItem(
                    "vp_vod_engine", MobileIcons.Movie, g, stringResource(R.string.settings_movies_series_player),
                    stringResource(R.string.settings_line_vod_engine), stringResource(R.string.settings_movies_player_description_mobile),
                    engines, vodEngine, EnginePreference.MPV_FIRST, { p -> vm.edit { setVodEnginePreference(p) } },
                ),
                if (sources.isNotEmpty()) pageItem(
                    "vp_vod_engine_sources", MobileIcons.Layers, stringResource(R.string.settings_vod_engine_per_playlist),
                    stringResource(R.string.settings_line_override_playlist),
                    overrideCountLabel(sources.count { it.vodEnginePreference != null }), SettingsLeaf.VOD_ENGINE_SOURCES.route,
                ) else null,
                savedItem(
                    "vp_reset_pins", R.string.settings_title_saved_choices, R.string.settings_line_saved_choices, vodPins,
                    R.string.settings_reset_player_choices_confirm, R.string.settings_reset_player_choices_confirm_description,
                ) { vm.clearVodEnginePins() },
                pageItem(
                    "vp_external", MobileIcons.OpenInNew, stringResource(R.string.settings_external_player), stringResource(R.string.settings_line_external),
                    stringResource(if (externalOn) R.string.common_on else R.string.common_off), SettingsLeaf.EXTERNAL_PLAYER.route,
                ),
            ),
        ),
        SettingSection(
            stringResource(R.string.settings_vp_cat_streaming),
            listOf(
                choiceItem(
                    "vp_vod_buffer", MobileIcons.Download, g, stringResource(R.string.settings_vod_buffer),
                    stringResource(R.string.settings_line_vod_buffer_mobile), stringResource(R.string.settings_vod_buffer_description),
                    s.vodBufferChoicesSecs.map { SettingsChoice(it, autoOrSeconds(it)) }, buffer, 0, { v -> vm.edit { setVodBufferSecs(v) } },
                ),
                choiceItem(
                    "vp_vod_timeout", MobileIcons.Schedule, g, stringResource(R.string.settings_vod_network_timeout),
                    stringResource(R.string.settings_line_vod_timeout_mobile), stringResource(R.string.settings_vod_network_timeout_description),
                    s.vodNetworkTimeoutChoicesSecs.map { SettingsChoice(it, autoOrSeconds(it)) }, timeout, 0, { v -> vm.edit { setVodNetworkTimeoutSecs(v) } },
                ),
                choiceItem(
                    "vp_vod_reconnects", MobileIcons.Refresh, g, stringResource(R.string.settings_vod_reconnects),
                    stringResource(R.string.settings_line_vod_reconnects_mobile), stringResource(R.string.settings_vod_reconnects_description),
                    s.vodReconnectChoices.map { SettingsChoice(it, stringResource(R.string.settings_vod_reconnects_value, it)) },
                    reconnects, 1, { v -> vm.edit { setVodReconnects(v) } },
                ),
                choiceItem(
                    "mobile_quality", MobileIcons.Translate, g, stringResource(R.string.settings_mobile_data_quality),
                    stringResource(R.string.settings_mobile_data_quality_description), null,
                    s.mobileDataMaxVideoHeightChoices.map { SettingsChoice(it, qualityLabel(it, R.string.common_off)) },
                    mobileQuality, 0, { h -> vm.edit { setMobileDataMaxVideoHeight(h) } },
                ),
                quickSwitch(vm, "data_saver", MobileIcons.Translate, R.string.settings_data_saver, R.string.settings_data_saver_description),
            ),
        ),
    )
}

/**
 * FFmpeg audio for ExoPlayer. The switch shows what this process runs with; a tap asks to restart, and the
 * choice is saved only together with that restart, because it applies at app start.
 */
@Composable
private fun ffmpegAudioItem(): SettingItem {
    val activity = LocalContext.current.findActivity()
    val on = FfmpegAudioSetting.active
    val title = stringResource(R.string.app_icon_restart_title)
    val text = stringResource(R.string.settings_ffmpeg_audio_restart_message)
    val restart = stringResource(R.string.app_icon_restart_now)
    return SettingItem(
        "vp_ffmpeg_audio", MobileIcons.VolumeUp, stringResource(R.string.settings_ffmpeg_audio),
        stringResource(R.string.settings_line_ffmpeg_audio), SettingValue.Switch(on),
        sheet = { dismiss ->
            StageConfirmSheet(title, text, restart, {
                if (activity != null && FfmpegAudioSetting.save(activity, !on)) AppIconSwitcher.restart(activity)
            }, dismiss)
        },
    )
}

/** "19 saved · Reset": what the player remembered per title or channel; a tap asks before forgetting it. */
@Composable
private fun savedItem(key: String, titleRes: Int, lineRes: Int, count: Int, confirmRes: Int, confirmTextRes: Int, onReset: () -> Unit): SettingItem {
    val confirm = stringResource(confirmRes)
    val text = stringResource(confirmTextRes)
    val reset = stringResource(R.string.common_reset)
    return SettingItem(
        key, MobileIcons.Refresh, stringResource(titleRes), stringResource(lineRes), SettingValue.Saved(count),
        // Nothing saved, nothing to forget: the row only says so.
        sheet = if (count > 0) ({ dismiss -> StageConfirmSheet(confirm, text, reset, onReset, dismiss) }) else null,
    )
}

// ---------------------------------------------------------------------------------------------- Picture

@Composable
internal fun pictureSections(vm: SettingsViewModel): List<SettingSection> {
    val g = SettingsGroup.PICTURE
    val s = vm.settings
    val savedZoom by vm.savedZoomCount.collectAsStateWithLifecycle()
    val maxHeight = s.maxVideoHeight.pref(0)
    val zoomName = s.defaultZoom.pref(ZoomMode.FIT.name)
    val zoom = runCatching { ZoomMode.valueOf(zoomName) }.getOrDefault(ZoomMode.FIT)
    val tunneled = s.tunneledPlayback.pref(false)
    val tunnelingFailed = s.tunnelingFailed.pref(false)
    val afr = s.autoFrameRate.pref(false)
    // Below Android 12 a display cannot be asked which refresh rates it reaches without blanking it, so turning
    // auto frame rate on asks first; off is immediate — the TV's rule.
    var afrWarning by remember { mutableStateOf(false) }
    if (afrWarning) {
        StageConfirmSheet(
            title = stringResource(R.string.settings_auto_frame_rate_warning_title_mobile),
            text = stringResource(R.string.settings_auto_frame_rate_warning_description_mobile, android.os.Build.VERSION.RELEASE),
            confirm = stringResource(R.string.settings_auto_frame_rate_turn_on_anyway),
            cancel = stringResource(R.string.settings_auto_frame_rate_keep_off),
            onConfirm = { vm.edit { setAutoFrameRate(true) } },
            onDismiss = { afrWarning = false },
        )
    }
    return listOf(
        SettingSection(
            stringResource(R.string.settings_vp_cat_picture),
            listOfNotNull(
                quickSwitch(vm, "vp_hw", MobileIcons.AutoAwesome, R.string.settings_hardware_decoding, R.string.settings_line_hw_mobile),
                quickSwitch(vm, "vp_sw_above_1080p", MobileIcons.AutoAwesome, R.string.settings_software_decode_above_1080p, R.string.settings_line_sw_decode_mobile),
                quickSwitch(vm, "vp_hdr", MobileIcons.WbSunny, R.string.settings_quick_prefer_sdr, R.string.settings_line_prefer_sdr),
                choiceItem(
                    "vp_max_quality", MobileIcons.OpenInFull, g, stringResource(R.string.settings_max_video_quality),
                    stringResource(R.string.settings_line_max_quality), stringResource(R.string.settings_max_video_quality_description),
                    s.maxVideoHeightChoices.map { SettingsChoice(it, qualityLabel(it, R.string.settings_auto)) }, maxHeight, 0,
                    { h -> vm.edit { setMaxVideoHeight(h) } },
                ),
                // Only where a decoder can tunnel; after a failure the line says why it is off.
                if (tv.own.owntv.player.Tunneling.supported) switchItem(
                    "vp_tunneled", MobileIcons.PlayArrow, stringResource(R.string.settings_title_tunneled),
                    stringResource(R.string.settings_line_tunneled) +
                        if (tunnelingFailed && !tunneled) " " + stringResource(R.string.settings_tunneled_playback_failed) else "",
                    tunneled,
                ) { on ->
                    if (on) tv.own.owntv.player.Tunneling.failedThisSession = false // another try
                    vm.edit { setTunneledPlayback(on) }
                } else null,
                choiceItem(
                    "vp_zoom", MobileIcons.OpenInFull, g, stringResource(R.string.settings_default_zoom),
                    stringResource(R.string.settings_line_zoom), stringResource(R.string.settings_help_zoom_mode),
                    ZoomMode.entries.map { SettingsChoice(it, stringResource(it.labelRes)) }, zoom, ZoomMode.FIT,
                    { z -> vm.edit { setDefaultZoom(z.name) } },
                ),
                savedItem(
                    "vp_reset_zoom", R.string.settings_title_saved_zoom, R.string.settings_line_saved_zoom, savedZoom,
                    R.string.settings_reset_saved_zoom_confirm, R.string.settings_reset_saved_zoom_confirm_description,
                ) { vm.clearSavedZoom() },
            ),
        ),
        SettingSection(
            stringResource(R.string.settings_vp_cat_frame_rate),
            listOf(
                switchItem(
                    "vp_afr", MobileIcons.Schedule, stringResource(R.string.settings_auto_frame_rate),
                    stringResource(R.string.settings_vp_cat_frame_rate_summary), afr,
                ) { on -> if (on && !AFR_SAFE) afrWarning = true else vm.edit { setAutoFrameRate(on) } },
            ),
        ),
    )
}

private fun audioOutputName(output: tv.own.owntv.core.player.AudioOutputKind): Int = when (output) {
    tv.own.owntv.core.player.AudioOutputKind.HDMI -> R.string.settings_audio_output_hdmi
    tv.own.owntv.core.player.AudioOutputKind.ARC -> R.string.settings_audio_output_arc
    tv.own.owntv.core.player.AudioOutputKind.BLUETOOTH -> R.string.settings_audio_output_bluetooth
    tv.own.owntv.core.player.AudioOutputKind.SPEAKER -> R.string.settings_audio_output_speaker
    tv.own.owntv.core.player.AudioOutputKind.OTHER -> R.string.settings_audio_output_other
}

private val AFR_SAFE = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

// ------------------------------------------------------------------------------------------------ Sound

@Composable
internal fun soundSections(vm: SettingsViewModel): List<SettingSection> {
    val g = SettingsGroup.SOUND
    val s = vm.settings
    val savedVolume by vm.savedVolumeCount.collectAsStateWithLifecycle()
    val savedDelay by vm.savedAudioDelayCount.collectAsStateWithLifecycle()
    val volume = s.defaultVolume.pref(100)
    val surround = s.surroundMode.pref(SurroundMode.AUTO)
    val delay = s.audioDelayMs.pref(0)
    val output by tv.own.owntv.core.player.AudioOutputWatch.current(androidx.compose.ui.platform.LocalContext.current).collectAsStateWithLifecycle()
    val outputDelay = s.outputAudioDelays.pref(tv.own.owntv.core.player.OutputAudioDelays.NONE).offsetFor(output)
    val audioLang = s.preferredAudioLang.pref("")
    val subLang = s.preferredSubLang.pref("")
    val subStyle = s.subtitleStyleEnabled.pref(false)
    return listOf(
        SettingSection(
            stringResource(R.string.settings_vp_section_sound),
            listOf(
                SettingItem(
                    "vp_volume", MobileIcons.VolumeUp, stringResource(R.string.settings_default_volume),
                    // Through the formatting overload: "%%" in the string only becomes "%" when formatted.
                    stringResource(R.string.settings_line_volume, *emptyArray<Any>()),
                    SettingValue.Stepper(
                        stringResource(R.string.common_percent, volume),
                        { vm.edit { setDefaultVolume((volume - 5).coerceAtLeast(0)) } },
                        { vm.edit { setDefaultVolume((volume + 5).coerceAtMost(150)) } },
                        volume > 0, volume < 150,
                    ),
                ),
                savedItem(
                    "vp_reset_volume", R.string.settings_title_saved_volume, R.string.settings_line_saved_volume, savedVolume,
                    R.string.settings_reset_saved_volume_confirm, R.string.settings_reset_saved_volume_confirm_description,
                ) { vm.clearSavedVolume() },
                choiceItem(
                    "vp_surround", MobileIcons.Headphones, g, stringResource(R.string.settings_surround_sound),
                    stringResource(R.string.settings_line_surround_mobile), stringResource(R.string.settings_surround_description_mobile),
                    SurroundMode.entries.map { SettingsChoice(it, stringResource(it.labelRes()), stringResource(it.descriptionRes())) },
                    surround, SurroundMode.AUTO, { m -> vm.edit { setSurroundMode(m) } },
                ),
                switchItem(
                    "vp_passthrough", MobileIcons.VolumeUp, stringResource(R.string.settings_audio_passthrough),
                    stringResource(R.string.settings_line_passthrough_mobile), s.audioPassthrough.pref(true),
                ) { vm.edit { setAudioPassthrough(it) } },
                ffmpegAudioItem(),
                switchItem(
                    "vp_night_mode", MobileIcons.Bedtime, stringResource(R.string.settings_night_mode),
                    stringResource(R.string.settings_line_night_mode), s.nightMode.pref(false),
                ) { vm.edit { setNightMode(it) } },
                switchItem(
                    "vp_volume_leveling", MobileIcons.Tune, stringResource(R.string.settings_volume_leveling),
                    stringResource(R.string.settings_line_leveling), s.volumeLevelling.pref(false),
                ) { vm.edit { setVolumeLevelling(it) } },
                // 25 ms steps across ±5 s: a device's picture delay lands in the tens of milliseconds.
                SettingItem(
                    "vp_audio_sync", MobileIcons.Schedule, stringResource(R.string.settings_audio_sync), stringResource(R.string.settings_line_audio_sync),
                    SettingValue.Stepper(
                        stringResource(R.string.settings_audio_delay_value, delay),
                        { vm.edit { setAudioDelayMs((delay - 25).coerceAtLeast(-5000)) } },
                        { vm.edit { setAudioDelayMs((delay + 25).coerceAtMost(5000)) } },
                        delay > -5000, delay < 5000,
                    ),
                ),
                // Same steps and range, stored for whichever output is playing now.
                SettingItem(
                    "vp_output_audio_delay", MobileIcons.Schedule, stringResource(R.string.settings_output_audio_delay),
                    stringResource(R.string.settings_line_output_audio_delay),
                    SettingValue.Stepper(
                        stringResource(
                            R.string.settings_output_audio_delay_value, stringResource(audioOutputName(output)),
                            stringResource(R.string.settings_audio_delay_value, outputDelay),
                        ),
                        { vm.edit { setOutputAudioDelay(output, (outputDelay - 25).coerceAtLeast(-5000)) } },
                        { vm.edit { setOutputAudioDelay(output, (outputDelay + 25).coerceAtMost(5000)) } },
                        outputDelay > -5000, outputDelay < 5000,
                    ),
                ),
                savedItem(
                    "vp_reset_audio_delay", R.string.settings_title_saved_audio_sync, R.string.settings_line_saved_audio_sync, savedDelay,
                    R.string.settings_reset_saved_audio_delay_confirm, R.string.settings_reset_saved_audio_delay_confirm_description,
                ) { vm.clearSavedAudioDelay() },
            ),
        ),
        SettingSection(
            stringResource(R.string.settings_vp_cat_languages),
            listOf(
                choiceItem(
                    "vp_audio_lang", MobileIcons.Translate, g, stringResource(R.string.settings_preferred_audio_language),
                    stringResource(R.string.settings_line_audio_lang), stringResource(R.string.settings_preferred_audio_language_description),
                    trackLanguageChoices(withOriginal = true), audioLang, null, { c -> vm.edit { setPreferredAudioLang(c) } },
                ),
                choiceItem(
                    "vp_sub_lang", MobileIcons.Subtitles, g, stringResource(R.string.settings_preferred_subtitle_language),
                    stringResource(R.string.settings_line_sub_lang_mobile), stringResource(R.string.settings_help_sub_lang),
                    trackLanguageChoices(withOriginal = false), subLang, null, { c -> vm.edit { setPreferredSubLang(c) } },
                ),
                pageItem(
                    "vp_sub_style", MobileIcons.Subtitles, stringResource(R.string.settings_subtitle_appearance),
                    stringResource(R.string.settings_line_sub_style_mobile), stringResource(if (subStyle) R.string.common_on else R.string.common_off),
                    SettingsLeaf.SUBTITLE_APPEARANCE.route,
                ),
            ),
        ),
    )
}

// ---------------------------------------------------------------------------------------------- Live TV

@Composable
internal fun liveSections(vm: SettingsViewModel): List<SettingSection> {
    val g = SettingsGroup.LIVE
    val s = vm.settings
    val sources by vm.sources.collectAsStateWithLifecycle()
    val timeshift = s.timeshiftEnabled.pref(false)
    val window = s.timeshiftWindowMinutes.pref(TimeshiftRules.DEFAULT_WINDOW_MINUTES)
    val resume = s.timeshiftResumeMode.pref(SettingsRepository.ResumeMode.ASK)
    val latency = LiveLatency.fromName(s.liveLatencyMode.pref(LiveLatency.BALANCED.name))
    val custom = s.liveLatencyCustomSecs.pref(LiveBuffer.CUSTOM_DEFAULT)
    val preroll = s.livePrerollSecs.pref(0)
    val giveUp = s.liveTuneTimeoutSecs.pref(LiveLadder.DEFAULT_BUDGET_SECS)

    // Low latency can make a working stream stutter, so it is acknowledged before it applies. [onCancel] runs when
    // the answer is no (a custom value that crossed below Balanced goes back to Balanced).
    var lowWarning by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }
    lowWarning?.let { (onConfirm, onCancel) ->
        var confirmed by remember { mutableStateOf(false) }
        StageConfirmSheet(
            title = stringResource(R.string.settings_low_latency_warning),
            text = stringResource(R.string.settings_low_latency_warning_description),
            confirm = stringResource(R.string.settings_low_latency_understand),
            onConfirm = { confirmed = true; onConfirm() },
            onDismiss = { if (!confirmed) onCancel(); lowWarning = null },
        )
    }
    val setCustom = { secs: Int ->
        vm.edit { setLiveLatencyCustomSecs(secs) }
        if (LiveBuffer.isLowLatency(secs) && !LiveBuffer.isLowLatency(custom)) {
            lowWarning = Pair({}, { vm.edit { setLiveLatencyMode(LiveLatency.BALANCED.name) } })
        }
    }
    val prerollList = LiveBuffer.PREROLL_CHOICES
    val giveUpList = LiveLadder.BUDGET_CHOICES_SECS.sortedBy { if (it == 0) Int.MAX_VALUE else it }
    return listOf(
        SettingSection(
            stringResource(R.string.settings_live_tv),
            listOfNotNull(
                quickSwitch(vm, "vp_channel_numbers", MobileIcons.FormatListBulleted, R.string.settings_channel_numbers, R.string.settings_line_channel_numbers),
                switchItem(
                    "vp_live_preview", MobileIcons.LiveTv, stringResource(R.string.settings_quick_live_preview),
                    stringResource(R.string.settings_line_live_preview_mobile), s.livePreviewEnabled.pref(true),
                ) { vm.edit { setLivePreviewEnabled(it) } },
                switchItem(
                    "vp_preview_audio", MobileIcons.VolumeUp, stringResource(R.string.settings_quick_preview_sound),
                    stringResource(R.string.settings_line_preview_sound), s.livePreviewAudio.pref(false),
                ) { vm.edit { setLivePreviewAudio(it) } },
                switchItem(
                    "vp_timeshift", MobileIcons.Pause, stringResource(R.string.settings_timeshift),
                    stringResource(R.string.settings_line_timeshift), timeshift,
                ) { vm.edit { setTimeshiftEnabled(it) } },
                // The length and the comeback question mean nothing while saving is off, so they come with the switch.
                if (timeshift) choiceItem(
                    "vp_timeshift_window", MobileIcons.Catchup, g, stringResource(R.string.settings_timeshift_window),
                    stringResource(R.string.settings_line_timeshift_window), stringResource(R.string.settings_help_timeshift_window),
                    TimeshiftRules.WINDOW_CHOICES_MINUTES.map { SettingsChoice(it, stringResource(R.string.player_duration_minutes, it)) },
                    window, TimeshiftRules.DEFAULT_WINDOW_MINUTES, { m -> vm.edit { setTimeshiftWindowMinutes(m) } },
                ) else null,
                if (timeshift) choiceItem(
                    "vp_timeshift_resume", MobileIcons.History, g, stringResource(R.string.settings_timeshift_resume),
                    stringResource(R.string.settings_timeshift_resume_description), null,
                    SettingsRepository.ResumeMode.entries.map { SettingsChoice(it, stringResource(it.resumeLabel())) },
                    resume, SettingsRepository.ResumeMode.ASK, { m -> vm.edit { setTimeshiftResumeMode(m) } },
                ) else null,
            ),
        ),
        SettingSection(
            stringResource(R.string.settings_vp_cat_live_tuning),
            listOfNotNull(
                choiceItem(
                    "vp_live_latency", MobileIcons.AutoAwesome, g, stringResource(R.string.settings_live_latency),
                    stringResource(R.string.settings_line_latency), stringResource(R.string.settings_live_latency_description),
                    LiveLatency.entries.map { SettingsChoice(it, stringResource(it.latencyLabel())) }, latency, LiveLatency.DEFAULT,
                    { picked ->
                        if (picked == LiveLatency.LOW) lowWarning = Pair({ vm.edit { setLiveLatencyMode(picked.name) } }, {})
                        else vm.edit { setLiveLatencyMode(picked.name) }
                    },
                    valueText = if (latency == LiveLatency.CUSTOM) stringResource(R.string.settings_live_buffer_seconds, custom) else stringResource(latency.latencyLabel()),
                ),
                if (latency == LiveLatency.CUSTOM) SettingItem(
                    "vp_live_latency_custom", MobileIcons.Tune, stringResource(R.string.settings_live_latency_custom), null,
                    SettingValue.Stepper(
                        stringResource(R.string.settings_live_buffer_seconds, custom),
                        { setCustom((custom - 1).coerceAtLeast(LiveBuffer.CUSTOM_MIN)) },
                        { setCustom((custom + 1).coerceAtMost(LiveBuffer.CUSTOM_MAX)) },
                        custom > LiveBuffer.CUSTOM_MIN, custom < LiveBuffer.CUSTOM_MAX,
                    ),
                    pinnable = false,
                ) else null,
                if (sources.isNotEmpty()) pageItem(
                    "vp_latency_sources", MobileIcons.Layers, stringResource(R.string.settings_live_latency_per_playlist),
                    stringResource(R.string.settings_line_override_playlist),
                    overrideCountLabel(sources.count { it.liveLatencyMode != null }), SettingsLeaf.LATENCY_SOURCES.route,
                ) else null,
                SettingItem(
                    "vp_preroll", MobileIcons.Download, stringResource(R.string.settings_live_preroll), stringResource(R.string.settings_line_preroll),
                    SettingValue.Stepper(
                        prerollLabel(preroll),
                        { vm.edit { setLivePrerollSecs(prerollList.stepFrom(preroll, -1)) } },
                        { vm.edit { setLivePrerollSecs(prerollList.stepFrom(preroll, 1)) } },
                        preroll > prerollList.first(), preroll < prerollList.last(),
                    ),
                ),
                if (sources.isNotEmpty()) pageItem(
                    "vp_preroll_sources", MobileIcons.Layers, stringResource(R.string.settings_live_preroll_per_playlist),
                    stringResource(R.string.settings_line_override_playlist),
                    overrideCountLabel(sources.count { it.livePrerollSecs != FOLLOW_GLOBAL_PREROLL }), SettingsLeaf.PREROLL_SOURCES.route,
                ) else null,
                // Never (0) is the far end of the list: past 60 s comes "never give up".
                SettingItem(
                    "vp_tune_timeout", MobileIcons.Schedule, stringResource(R.string.settings_live_tune_timeout), stringResource(R.string.settings_line_tune_timeout),
                    SettingValue.Stepper(
                        tuneTimeoutLabel(giveUp),
                        { vm.edit { setLiveTuneTimeoutSecs(giveUpList.stepIndex(giveUp, -1)) } },
                        { vm.edit { setLiveTuneTimeoutSecs(giveUpList.stepIndex(giveUp, 1)) } },
                        giveUpList.indexOf(giveUp) != 0, giveUp != 0,
                    ),
                ),
                if (sources.isNotEmpty()) pageItem(
                    "vp_tune_timeout_sources", MobileIcons.Layers, stringResource(R.string.settings_live_tune_timeout_per_playlist),
                    stringResource(R.string.settings_line_override_playlist),
                    overrideCountLabel(sources.count { it.liveTuneTimeoutSecs != null }), SettingsLeaf.TUNE_TIMEOUT_SOURCES.route,
                ) else null,
            ),
        ),
    )
}

/** A step along a list ordered by position rather than by value (Never comes last). */
private fun List<Int>.stepIndex(current: Int, delta: Int): Int {
    val at = indexOf(current).takeIf { it >= 0 } ?: indexOfFirst { it != 0 && it >= current }.takeIf { it >= 0 } ?: lastIndex
    return this[(at + delta).coerceIn(0, lastIndex)]
}

@Composable
private fun prerollLabel(secs: Int): String =
    if (secs <= 0) stringResource(R.string.common_off) else stringResource(R.string.settings_video_seconds, secs)

// ----------------------------------------------------------------------------------- Watching & recording

@Composable
internal fun watchingSections(vm: SettingsViewModel): List<SettingSection> {
    val g = SettingsGroup.WATCHING
    val s = vm.settings
    val seek = s.seekStepSec.pref(SeekSteps.DEFAULT_SEEK_STEP_SEC)
    val rewind = s.liveRewindStepSec.pref(SeekSteps.DEFAULT_LIVE_REWIND_STEP_SEC)
    // ASK: what core stores when nothing has been chosen.
    val resume = s.resumeMode.pref(SettingsRepository.ResumeMode.ASK)
    val gesture = s.gestureSensitivityPct.pref(100)
    val multiview = s.multiviewEnabled.pref(false)
    val tiles = s.multiviewTiles.pref(DEFAULT_MULTIVIEW_TILES)
    // A value stored before "off" was withdrawn reads as docked, so the row never names a choice the sheet lacks.
    val mini = s.miniPlayerStyle.pref(SettingsRepository.MiniPlayerStyle.FLOATING)
        .takeIf { it != SettingsRepository.MiniPlayerStyle.OFF } ?: SettingsRepository.MiniPlayerStyle.DOCKED
    val pipSize = s.pipSize.pref(SettingsRepository.PipSize.MEDIUM)
    val preRoll = s.recordingPreRollMinutes.pref(RecordingSchedule.DEFAULT_PRE_ROLL_MINUTES)
    val postRoll = s.recordingPostRollMinutes.pref(RecordingSchedule.DEFAULT_POST_ROLL_MINUTES)
    val recordWatching = s.recordWhatImWatching.pref(false)
    val recordingsVm: RecordingsViewModel = koinViewModel()

    // The player's record button costs the one spare connection, so turning it on says so first.
    var warnRecordWatching by remember { mutableStateOf(false) }
    if (warnRecordWatching) {
        StageConfirmSheet(
            title = stringResource(R.string.settings_record_watching_warning_title),
            text = stringResource(R.string.settings_record_watching_warning_description),
            confirm = stringResource(R.string.settings_record_watching_turn_on),
            cancel = stringResource(R.string.settings_record_watching_keep_off),
            onConfirm = { vm.edit { setRecordWhatImWatching(true) } },
            onDismiss = { warnRecordWatching = false },
        )
    }
    val minutes = @Composable { m: Int -> pluralStringResource(R.plurals.recording_minutes, m, m) }
    return listOf(
        SettingSection(
            stringResource(R.string.settings_vp_cat_controls_resume),
            listOf(
                choiceItem(
                    "vp_seek_step", MobileIcons.Catchup, g, stringResource(R.string.settings_seek_step),
                    stringResource(R.string.settings_line_seek_step_mobile), stringResource(R.string.settings_seek_step_description),
                    SeekSteps.SEEK_CHOICES.map { SettingsChoice(it, stringResource(R.string.settings_live_buffer_seconds, it)) },
                    seek, SeekSteps.DEFAULT_SEEK_STEP_SEC, { v -> vm.edit { setSeekStepSec(v) } },
                ),
                choiceItem(
                    "vp_rewind_step", MobileIcons.Catchup, g, stringResource(R.string.settings_live_rewind_step),
                    stringResource(R.string.settings_line_rewind_step_mobile), stringResource(R.string.settings_live_rewind_step_description),
                    SeekSteps.LIVE_REWIND_CHOICES.map { SettingsChoice(it, stringResource(R.string.settings_live_buffer_seconds, it)) },
                    rewind, SeekSteps.DEFAULT_LIVE_REWIND_STEP_SEC, { v -> vm.edit { setLiveRewindStepSec(v) } },
                ),
                choiceItem(
                    "vp_resume", MobileIcons.History, g, stringResource(R.string.settings_resume_playback),
                    stringResource(R.string.settings_line_resume), stringResource(R.string.settings_help_resume),
                    SettingsRepository.ResumeMode.entries.map { SettingsChoice(it, stringResource(it.resumeLabel())) },
                    resume, SettingsRepository.ResumeMode.ASK, { m -> vm.edit { setResumeMode(m) } },
                ),
                quickSwitch(vm, "vp_autoplay", MobileIcons.SkipNext, R.string.settings_autoplay_next, R.string.settings_line_autoplay),
                SettingItem(
                    "gesture_sensitivity", MobileIcons.AutoAwesome, stringResource(R.string.settings_gesture_sensitivity),
                    stringResource(R.string.settings_line_gesture_mobile),
                    SettingValue.Stepper(
                        stringResource(R.string.common_percent, gesture),
                        { vm.edit { setGestureSensitivityPct((gesture - 10).coerceAtLeast(50)) } },
                        { vm.edit { setGestureSensitivityPct((gesture + 10).coerceAtMost(200)) } },
                        gesture > 50, gesture < 200,
                    ),
                ),
            ),
        ),
        SettingSection(
            stringResource(R.string.settings_vp_cat_multiview),
            listOfNotNull(
                switchItem(
                    "vp_multiview", MobileIcons.Multiview, stringResource(R.string.settings_multiview),
                    stringResource(R.string.settings_vp_cat_multiview_summary_mobile), multiview,
                ) { vm.edit { setMultiviewEnabled(it) } },
                // "Max 4", not "4": the number is a ceiling — the grid opens with two and grows when asked.
                if (multiview) choiceItem(
                    "vp_multiview_tiles", MobileIcons.GridView, g, stringResource(R.string.settings_multiview_tiles_max),
                    stringResource(R.string.settings_line_tiles), stringResource(R.string.settings_multiview_description),
                    (MIN_MULTIVIEW_TILES..MAX_MULTIVIEW_TILES).map { SettingsChoice(it, stringResource(R.string.settings_multiview_tiles_max_value, it)) },
                    tiles, DEFAULT_MULTIVIEW_TILES, { n -> vm.edit { setMultiviewTiles(n) } },
                ) else null,
                // Docked or floating, and no third answer: "off" would leave a stream nowhere to go.
                choiceItem(
                    "vp_mini", MobileIcons.Phone, g, stringResource(R.string.settings_mini_player),
                    stringResource(R.string.settings_mini_player_style_description), null,
                    SettingsRepository.MiniPlayerStyle.entries.filter { it != SettingsRepository.MiniPlayerStyle.OFF }
                        .map { SettingsChoice(it, stringResource(it.miniLabel())) },
                    mini, SettingsRepository.MiniPlayerStyle.FLOATING, { m -> vm.edit { setMiniPlayerStyle(m) } },
                ),
                quickSwitch(vm, "pip_enabled", MobileIcons.Phone, R.string.settings_pip, R.string.settings_line_pip_mobile),
                choiceItem(
                    "pip_size", MobileIcons.OpenInFull, g, stringResource(R.string.settings_pip_size),
                    stringResource(R.string.settings_line_pip_size_mobile), null,
                    SettingsRepository.PipSize.entries.map { SettingsChoice(it, stringResource(it.pipLabel())) },
                    pipSize, SettingsRepository.PipSize.MEDIUM, { v -> vm.edit { setPipSize(v) } },
                ),
                quickSwitch(vm, "pip_snap", MobileIcons.Move, R.string.settings_pip_snap, R.string.settings_line_pip_snap_mobile),
            ),
        ),
        SettingSection(
            stringResource(R.string.content_downloads_group_on_phone),
            listOf(
                quickSwitch(vm, "background_playback", MobileIcons.Headphones, R.string.settings_background_playback, R.string.settings_background_playback_description),
                quickSwitch(vm, "audio_on_screen_off", MobileIcons.Bedtime, R.string.settings_audio_on_screen_off, R.string.settings_line_audio_screen_off_mobile),
                quickSwitch(vm, "audio_on_mobile_data", MobileIcons.Translate, R.string.settings_audio_on_mobile_data, R.string.settings_audio_on_mobile_data_description),
                quickSwitch(vm, "audio_per_channel", MobileIcons.VolumeUp, R.string.settings_audio_per_channel, R.string.settings_line_audio_per_channel_mobile),
            ),
        ),
        SettingSection(
            stringResource(R.string.recording_settings_group),
            note = stringResource(R.string.settings_recording_timers_inexact).takeUnless { recordingsVm.timersAreExact },
            items = listOf(
                switchItem(
                    "rec_reserve", MobileIcons.Record, stringResource(R.string.settings_recording_reserve),
                    stringResource(R.string.settings_line_rec_reserve),
                    s.recordingReserveConnection.pref(true),
                ) { vm.edit { setRecordingReserveConnection(it) } },
                switchItem(
                    "rec_mobile_data", MobileIcons.Translate, stringResource(R.string.settings_recording_mobile_data),
                    stringResource(R.string.settings_line_rec_mobile_data_mobile), s.recordingOverMobileData.pref(true),
                ) { vm.edit { setRecordingOverMobileData(it) } },
                switchItem(
                    "rec_watching", MobileIcons.Record, stringResource(R.string.settings_record_watching),
                    stringResource(R.string.settings_line_rec_watching_mobile), recordWatching,
                ) { on -> if (on) warnRecordWatching = true else vm.edit { setRecordWhatImWatching(false) } },
                choiceItem(
                    "rec_pre_roll", MobileIcons.Schedule, g, stringResource(R.string.settings_recording_pre_roll),
                    stringResource(R.string.settings_line_rec_pre_roll_mobile), stringResource(R.string.settings_recording_pre_roll_description),
                    ROLL_CHOICES.map { SettingsChoice(it, minutes(it)) }, preRoll, RecordingSchedule.DEFAULT_PRE_ROLL_MINUTES,
                    { m -> vm.edit { setRecordingPreRollMinutes(m) } },
                ),
                choiceItem(
                    "rec_post_roll", MobileIcons.Schedule, g, stringResource(R.string.settings_recording_post_roll),
                    stringResource(R.string.settings_line_rec_post_roll_mobile), stringResource(R.string.settings_recording_post_roll_description),
                    ROLL_CHOICES.map { SettingsChoice(it, minutes(it)) }, postRoll, RecordingSchedule.DEFAULT_POST_ROLL_MINUTES,
                    { m -> vm.edit { setRecordingPostRollMinutes(m) } },
                ),
            ),
        ),
    )
}

/** The paddings worth offering, all inside core's own 0..30 clamp. */
private val ROLL_CHOICES = listOf(0, 1, 2, 3, 5, 10, 15, 30)

// ---------------------------------------------------------------------------- Per-playlist pages

/**
 * One per-playlist override: each playlist with what it is set to; a tap opens the choices for that
 * playlist — Follow the global setting first. Replaces the two stacked sheets of the old page.
 */
@Composable
fun PerPlaylistPage(leaf: SettingsLeaf, modifier: Modifier = Modifier, vm: SettingsViewModel = koinViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val follow = stringResource(R.string.settings_live_preroll_follow)
    val eyebrow = stringResource(leaf.titleRes)
    var editing by remember { mutableStateOf<Long?>(null) }
    var customFor by remember { mutableStateOf<Long?>(null) }
    var lowWarning by remember { mutableStateOf<(() -> Unit)?>(null) }

    SettingsStagePage(modifier) {
        sources.forEachIndexed { index, src ->
            item(key = src.id) {
                StageSettingRowFor(src, index, sourceValue(leaf, src, follow)) { editing = src.id }
            }
        }
    }

    val src = sources.firstOrNull { it.id == editing }
    if (src != null) {
        val engines = EnginePreference.entries.map { SettingsChoice<String?>(it.name, engineLabel(it)) }
        val close = { editing = null }
        when (leaf) {
            SettingsLeaf.LIVE_ENGINE_SOURCES -> StageChoiceSheet(
                eyebrow, src.name, stringResource(R.string.settings_help_live_engine),
                listOf(SettingsChoice<String?>(null, follow)) + engines, src.liveEnginePreference,
                { vm.setSourceLiveEngine(src.id, it) }, close, recommended = null,
            )
            SettingsLeaf.VOD_ENGINE_SOURCES -> StageChoiceSheet(
                eyebrow, src.name, stringResource(R.string.settings_movies_player_description_mobile),
                listOf(SettingsChoice<String?>(null, follow)) + engines, src.vodEnginePreference,
                { vm.setSourceVodEngine(src.id, it) }, close,
            )
            SettingsLeaf.LATENCY_SOURCES -> StageChoiceSheet(
                eyebrow, src.name, stringResource(R.string.settings_live_latency_description),
                listOf(SettingsChoice<String?>(null, follow)) + LiveLatency.entries.map { SettingsChoice<String?>(it.name, stringResource(it.latencyLabel())) },
                src.liveLatencyMode,
                { name ->
                    when (LiveLatency.fromName(name ?: "")) {
                        LiveLatency.LOW -> lowWarning = { vm.setSourceLiveLatency(src.id, name, sourceCustomSecs(src)) }
                        // The seconds come next; Custom is committed with them, never with a value nobody picked.
                        LiveLatency.CUSTOM -> customFor = src.id
                        else -> vm.setSourceLiveLatency(src.id, name, FOLLOW_GLOBAL_LATENCY_SECS)
                    }
                },
                close,
            )
            SettingsLeaf.PREROLL_SOURCES -> StageChoiceSheet(
                eyebrow, src.name, stringResource(R.string.settings_live_preroll_description),
                listOf(SettingsChoice(FOLLOW_GLOBAL_PREROLL, follow)) + LiveBuffer.PREROLL_CHOICES.map { SettingsChoice(it, prerollLabel(it)) },
                src.livePrerollSecs, { vm.setSourcePreroll(src.id, it) }, close,
            )
            SettingsLeaf.TUNE_TIMEOUT_SOURCES -> StageChoiceSheet(
                eyebrow, src.name, stringResource(R.string.settings_live_tune_timeout_description),
                listOf(SettingsChoice<Int?>(null, follow)) + LiveLadder.BUDGET_CHOICES_SECS.map { SettingsChoice<Int?>(it, tuneTimeoutLabel(it)) },
                src.liveTuneTimeoutSecs, { vm.setSourceTuneTimeout(src.id, it) }, close,
            )
            SettingsLeaf.CATCHUP_SOURCES -> {
                val manual = SettingsRepository.CatchupTimezone.MANUAL.name
                StageChoiceSheet(
                    eyebrow, src.name, stringResource(R.string.settings_catchup_timezone_per_playlist_description),
                    listOf(
                        SettingsChoice<Pair<String?, Int?>>(null to null, follow),
                        SettingsChoice<Pair<String?, Int?>>(SettingsRepository.CatchupTimezone.DEVICE.name to null, stringResource(R.string.settings_catchup_timezone_device)),
                    ) + vm.settings.catchupOffsetChoicesMinutes.map { SettingsChoice<Pair<String?, Int?>>(manual to it, utcOffsetLabel(it)) },
                    src.catchupTimezone to src.catchupOffsetMin.takeIf { src.catchupTimezone == manual },
                    { (mode, offset) -> vm.setSourceCatchupTimezone(src.id, mode, offset) }, close,
                )
            }
            else -> Unit
        }
    }
    sources.firstOrNull { it.id == customFor }?.let { s ->
        val secs = sourceCustomSecs(s)
        androidx.compose.runtime.key(s.id) {
            tv.own.owntv.mobile.ui.components.MobileBottomSheet(onDismissRequest = { customFor = null }) {
                tv.own.owntv.mobile.ui.stage.StageSheetHeader(s.name, eyebrow = eyebrow)
                StageSettingRowValue(stringResource(R.string.settings_live_latency_custom)) {
                    tv.own.owntv.mobile.ui.stage.StageStepper(
                        stringResource(R.string.settings_live_buffer_seconds, secs),
                        { vm.setSourceLiveLatency(s.id, LiveLatency.CUSTOM.name, (secs - 1).coerceAtLeast(LiveBuffer.CUSTOM_MIN)) },
                        {
                            val next = (secs + 1).coerceAtMost(LiveBuffer.CUSTOM_MAX)
                            vm.setSourceLiveLatency(s.id, LiveLatency.CUSTOM.name, next)
                        },
                        minusEnabled = secs > LiveBuffer.CUSTOM_MIN, plusEnabled = secs < LiveBuffer.CUSTOM_MAX,
                    )
                }
            }
        }
    }
    lowWarning?.let { apply ->
        StageConfirmSheet(
            title = stringResource(R.string.settings_low_latency_warning),
            text = stringResource(R.string.settings_low_latency_warning_description),
            confirm = stringResource(R.string.settings_low_latency_understand),
            onConfirm = apply,
            onDismiss = { lowWarning = null },
        )
    }
}

/** A playlist row of a per-playlist page: its mark, its name, its value with ▾. */
@Composable
private fun StageSettingRowFor(src: SourceEntity, index: Int, value: String, onClick: () -> Unit) {
    tv.own.owntv.mobile.ui.stage.StageSettingRow(
        title = src.name,
        leading = { StagePlaylistMark(tv.own.owntv.core.ui.stage.PlaylistMark.of(src.name, index)) },
        onClick = onClick,
    ) { tv.own.owntv.mobile.ui.stage.StageRowValue(value, tv.own.owntv.mobile.ui.stage.StageChevron.DOWN) }
}

/** A single row inside a sheet: its name, then [trailing]. */
@Composable
private fun StageSettingRowValue(title: String, trailing: @Composable () -> Unit) {
    tv.own.owntv.mobile.ui.stage.StageSettingRow(title = title, minHeight = 48.dp) { trailing() }
}

@Composable
private fun sourceValue(leaf: SettingsLeaf, src: SourceEntity, follow: String): String = when (leaf) {
    SettingsLeaf.LIVE_ENGINE_SOURCES -> src.liveEnginePreference?.let { n -> EnginePreference.entries.firstOrNull { it.name == n } }?.let { engineLabel(it) } ?: follow
    SettingsLeaf.VOD_ENGINE_SOURCES -> src.vodEnginePreference?.let { n -> EnginePreference.entries.firstOrNull { it.name == n } }?.let { engineLabel(it) } ?: follow
    SettingsLeaf.LATENCY_SOURCES -> {
        val mode = src.liveLatencyMode?.let { LiveLatency.fromName(it) }
        when {
            mode == null -> follow
            mode == LiveLatency.CUSTOM -> stringResource(R.string.settings_live_buffer_seconds, sourceCustomSecs(src))
            else -> stringResource(mode.latencyLabel())
        }
    }
    SettingsLeaf.PREROLL_SOURCES -> if (src.livePrerollSecs == FOLLOW_GLOBAL_PREROLL) follow else prerollLabel(src.livePrerollSecs)
    SettingsLeaf.TUNE_TIMEOUT_SOURCES -> src.liveTuneTimeoutSecs?.let { tuneTimeoutLabel(it) } ?: follow
    SettingsLeaf.CATCHUP_SOURCES -> when (src.catchupTimezone) {
        null -> follow
        SettingsRepository.CatchupTimezone.MANUAL.name -> utcOffsetLabel(src.catchupOffsetMin ?: 0)
        else -> stringResource(R.string.settings_catchup_timezone_device)
    }
    else -> ""
}

/** Settings › Player › External player: which sections open in another app, one switch each. */
@Composable
fun ExternalPlayerPage(modifier: Modifier = Modifier, vm: SettingsViewModel = koinViewModel()) {
    val s = vm.settings
    val rows = listOf(
        Triple(SettingsRepository.ExternalPlayerSection.LIVE_TV, R.string.common_nav_live_tv, s.externalPlayerLive.pref(false)),
        Triple(SettingsRepository.ExternalPlayerSection.MOVIES, R.string.common_nav_movies, s.externalPlayerMovies.pref(false)),
        Triple(SettingsRepository.ExternalPlayerSection.SERIES, R.string.common_nav_series, s.externalPlayerSeries.pref(false)),
    )
    val note = stringResource(R.string.settings_external_player_description)
    SettingsStagePage(modifier) {
        item(key = "note") { PageNote(note) }
        rows.forEach { (section, titleRes, on) ->
            item(key = section.name) {
                SettingItemRow(
                    switchItem(section.name, MobileIcons.OpenInNew, stringResource(titleRes), null, on) { vm.edit { setExternalPlayer(section, it) } },
                    onOpenSheet = {}, onLongClick = null,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- labels

/** "ExoPlayer, then mpv" — the brand names are never translated, the joining phrase is. */
@Composable
internal fun engineLabel(preference: EnginePreference): String {
    val exo = stringResource(R.string.settings_player_exoplayer)
    val mpv = stringResource(R.string.settings_player_mpv)
    return when (preference) {
        EnginePreference.EXO_FIRST -> stringResource(R.string.settings_engine_order, exo, mpv)
        EnginePreference.MPV_FIRST -> stringResource(R.string.settings_engine_order, mpv, exo)
        EnginePreference.EXO_ONLY -> stringResource(R.string.settings_engine_only, exo)
        EnginePreference.MPV_ONLY -> stringResource(R.string.settings_engine_only, mpv)
    }
}

@Composable
private fun trackLanguageName(code: String): String = when (code) {
    "" -> stringResource(R.string.settings_none_auto)
    TrackLanguages.ORIGINAL -> stringResource(R.string.settings_language_original)
    else -> TrackLanguages.displayName(code, LocalConfiguration.current.locales[0])
}

/** No preference first, then (audio only) the title's original language, then every language by name. */
@Composable
private fun trackLanguageChoices(withOriginal: Boolean): List<SettingsChoice<String>> {
    val display = LocalConfiguration.current.locales[0]
    val codes = listOf("") + listOfNotNull(TrackLanguages.ORIGINAL.takeIf { withOriginal }) + remember(display) { TrackLanguages.sortedFor(display) }
    return codes.map { SettingsChoice(it, trackLanguageName(it)) }
}

/** [noneRes] ("Auto" / "Off") for 0, else the picture height ("1080p"). */
@Composable
private fun qualityLabel(height: Int, noneRes: Int): String =
    if (height <= 0) stringResource(noneRes) else stringResource(R.string.settings_video_quality_lines, height)

/** "Auto" for 0, else seconds ("60s") — the film buffer and network timeout choices. */
@Composable
private fun autoOrSeconds(secs: Int): String =
    if (secs <= 0) stringResource(R.string.settings_vod_network_auto) else stringResource(R.string.settings_live_buffer_seconds, secs)

/** "Give up after": 0 is never, else seconds. */
@Composable
private fun tuneTimeoutLabel(secs: Int): String =
    if (secs <= 0) stringResource(R.string.common_never) else stringResource(R.string.settings_live_buffer_seconds, secs)

/** A playlist's own custom latency, or the global default when it has never been given one. */
private fun sourceCustomSecs(source: SourceEntity): Int =
    source.liveLatencyCustomSecs.takeIf { it >= LiveBuffer.CUSTOM_MIN } ?: LiveBuffer.CUSTOM_DEFAULT

private fun SurroundMode.labelRes() = when (this) {
    SurroundMode.AUTO -> R.string.settings_auto
    SurroundMode.STEREO -> R.string.settings_surround_stereo
    SurroundMode.SURROUND -> R.string.settings_surround_sound
}

private fun SurroundMode.descriptionRes() = when (this) {
    SurroundMode.AUTO -> R.string.settings_surround_auto_description_mobile
    SurroundMode.STEREO -> R.string.settings_surround_stereo_description_mobile
    SurroundMode.SURROUND -> R.string.settings_surround_forced_description
}

private fun SettingsRepository.ResumeMode.resumeLabel() = when (this) {
    SettingsRepository.ResumeMode.AUTO -> R.string.settings_resume_always
    SettingsRepository.ResumeMode.ASK -> R.string.settings_resume_ask
    SettingsRepository.ResumeMode.NEVER -> R.string.settings_resume_never
}

private fun LiveLatency.latencyLabel() = when (this) {
    LiveLatency.LOW -> R.string.settings_live_latency_low
    LiveLatency.BALANCED -> R.string.settings_live_latency_balanced
    LiveLatency.STABLE -> R.string.settings_live_latency_stable
    LiveLatency.CUSTOM -> R.string.settings_live_latency_custom
}

private fun SettingsRepository.MiniPlayerStyle.miniLabel(): Int = when (this) {
    SettingsRepository.MiniPlayerStyle.FLOATING -> R.string.settings_mini_player_style_floating
    SettingsRepository.MiniPlayerStyle.DOCKED -> R.string.settings_mini_player_style_docked
    SettingsRepository.MiniPlayerStyle.OFF -> R.string.common_off
}

private fun SettingsRepository.PipSize.pipLabel(): Int = when (this) {
    SettingsRepository.PipSize.SMALL -> R.string.settings_pip_size_small
    SettingsRepository.PipSize.MEDIUM -> R.string.settings_pip_size_medium
    SettingsRepository.PipSize.LARGE -> R.string.settings_pip_size_large
}
