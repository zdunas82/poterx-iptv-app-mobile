package tv.own.owntv.mobile.poterx

import tv.own.owntv.core.i18n.SupportedLocale
import tv.own.owntv.core.i18n.SupportedLocales

/**
 * Poterx: a locked build ships Polish and English only (the build packages just these two resource
 * qualifiers, see `localeFilters` in app/build.gradle.kts), so the language picker and the "N
 * languages" counts list the same two instead of the catalogue's full set. Stock builds are untouched.
 */
object PoterxLocales {
    /** Android resource qualifiers packaged into a locked build. Mirrors the build script's list. */
    val qualifiers: Set<String> = setOf("en", "pl")

    /** The catalogue rows this build really carries. */
    val packaged: List<SupportedLocale>
        get() = SupportedLocales.all.filter { it.packaged && (!LockedSource.enabled || it.resourceQualifier in qualifiers) }

    /** [SupportedLocales.pickerRows] narrowed to what this build carries. */
    val pickerRows: List<SupportedLocale>
        get() = SupportedLocales.pickerRows.filter { !LockedSource.enabled || it.resourceQualifier in qualifiers }
}
