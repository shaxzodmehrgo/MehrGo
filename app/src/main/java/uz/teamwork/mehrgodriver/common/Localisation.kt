package uz.teamwork.mehrgodriver.common

import android.content.Context
import android.content.res.Configuration
import uz.teamwork.mehrgodriver.common.Localisation.applyToConfig
import uz.teamwork.mehrgodriver.common.Localisation.wrap
import java.util.Locale

/**
 * In-app locale handling WITHOUT `AppCompatDelegate.setApplicationLocales`.
 *
 * On Android 13+ (API 33+) with a live Activity, `setApplicationLocales` delegates to the framework
 * `LocaleManager`, which on some OEMs applies the per-app locale by RELAUNCHING the task from the
 * launcher (our `FirstActivity`) — the "app restarted / splash flash" the user sees. Instead we:
 *  - [wrap] each Activity/Application base context in [attachBaseContext] so a (re)created screen
 *    inflates directly in the saved language, and
 *  - [applyToConfig] the PROCESS/Application resources config so services + overlays that read the
 *    app config (MyTrackingService notifications, AutoOfferService labels) follow the new language,
 * then callers do a plain `Activity.recreate()` to swap the visible UI in place — no task relaunch.
 */
object Localisation {
    private const val LANGUAGE_PREF = "myLanguagePref"
    private const val LANGUAGE_KEY = "my_language_key"

    /** Re-assert [language] on the process/Application config so already-running services follow it,
     *  then the caller recreates the current Activity for the visible UI. */
    fun setLanguage(language: String) {
        applyToConfig(App.appContext, language)
    }

    /** Reads the saved language straight from SharedPreferences (usable in Application/Activity
     *  `attachBaseContext`, before LanguageManager.init) and returns a context whose resources
     *  resolve in it. No-op wrap (returns [base]) when no language has been chosen yet. */
    fun wrap(base: Context): Context = wrap(base, readSavedLanguage(base))

    fun wrap(base: Context, language: String?): Context {
        if (language.isNullOrEmpty()) return base
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        // DELTA, never a snapshot. `Configuration(base.resources.configuration)` would copy every
        // field as a concrete value, and ResourcesManager re-applies the whole override on top of
        // each later system config (Configuration.updateFrom only skips UNDEFINED fields). Since
        // App.attachBaseContext installs this as the APPLICATION context — and AppCompat resolves
        // MODE_NIGHT_FOLLOW_SYSTEM from the application configuration's uiMode — a snapshot froze
        // day/night (plus density, orientation, font scale) for the life of the process. This app
        // keeps a foreground tracking service alive for days, so a driver who turned on system dark
        // mode stayed light until the process was killed.
        //
        // Configuration() leaves every field UNDEFINED except fontScale, which setToDefaults() sets
        // to 1 — clear it to 0 so updateFrom() skips it and system font scaling still applies.
        val config = Configuration()
        config.fontScale = 0f
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Mutate the resources config for [language] in place — for BOTH the Application (so services,
     *  overlays and notifications follow it) AND the passed context's own resources (the LIVE Activity,
     *  so re-read strings + freshly inflated fragments switch language) — WITHOUT recreating the
     *  Activity. This is what lets a language change apply with no splash / white / map GL re-render. */
    @Suppress("DEPRECATION")
    fun applyToConfig(context: Context, language: String) {
        if (language.isEmpty()) return
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        for (res in linkedSetOf(context.applicationContext.resources, context.resources)) {
            val config = Configuration(res.configuration)
            config.setLocale(locale)
            res.updateConfiguration(config, res.displayMetrics)
        }
    }

    private fun readSavedLanguage(base: Context): String? =
        base.getSharedPreferences(LANGUAGE_PREF, Context.MODE_PRIVATE).getString(LANGUAGE_KEY, null)
}
