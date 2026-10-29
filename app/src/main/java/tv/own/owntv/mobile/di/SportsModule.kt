package tv.own.owntv.mobile.di

import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import tv.own.owntv.mobile.ui.screens.sports.SportsPlayback
import tv.own.owntv.mobile.ui.screens.sports.SportsSettingsViewModel
import tv.own.owntv.mobile.ui.screens.sports.SportsViewModel

/** Sports Hub: the screens' view-models and what the player knows about a match. Lazy; nothing runs at launch. */
val sportsModule = module {
    singleOf(::SportsPlayback)
    viewModel { SportsViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SportsSettingsViewModel(get(), get(), get(), get(), get(), get()) }
}
