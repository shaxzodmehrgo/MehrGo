package uz.teamwork.mehrgodriver.common.shared_pref

import android.app.Application
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import uz.teamwork.mehrgodriver.common.Constants.THEME_DAY
import uz.teamwork.mehrgodriver.common.Constants.THEME_NIGHT
import uz.teamwork.mehrgodriver.common.Constants.THEME_SYSTEM

object ThemeManager {
    private const val THEME_PREF = "myThemePref"
    private const val themeKey = "my_theme_key"

    lateinit var sharedPreferences: SharedPreferences

    fun init(application: Application) {
        sharedPreferences = application.getSharedPreferences(THEME_PREF, MODE_PRIVATE)
    }

    fun saveTheme(theme: String) {
        val myEdit = sharedPreferences.edit()
        myEdit.putString(themeKey, theme)
        myEdit.apply()
    }

    fun getTheme(): String? {
        return sharedPreferences.getString(themeKey, null)
    }

    // Apply theme
    fun applyTheme(theme: String) {
        when (theme) {
            THEME_DAY -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_NIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    /** What the DEVICE is set to — deliberately not [context]'s configuration.
     *
     *  Every AppCompat-hosted context already carries the delegate's own night override, so reading
     *  it here answered "what is the app showing", not "what is the system set to". With THEME_DAY
     *  stored, picking "System" then compared false-vs-false and the screen decided nothing had
     *  changed; the Yandex map made the same mistake and stayed in its day style.
     *  Resources.getSystem() is the global config and is never touched by app-level overrides. */
    @Suppress("UNUSED_PARAMETER")
    fun isSystemNightMode(context: Context): Boolean {
        val nightModeFlags =
            Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}