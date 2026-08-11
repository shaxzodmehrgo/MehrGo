package uz.teamwork.mehrgodriver.common.shared_pref

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager.markStartupLanguage

object LanguageManager {
    private const val LANGUAGE_PREF = "myLanguagePref"
    private const val languageKey = "my_language_key"
    private const val lastStartupLanguageKey = "last_startup_language_key"

    lateinit var sharedPreferences: SharedPreferences

    fun init(application: Application) {
        sharedPreferences = application.getSharedPreferences(LANGUAGE_PREF, MODE_PRIVATE)
    }

    fun saveLanguage(language: String) {
        val myEdit = sharedPreferences.edit()
        myEdit.putString(languageKey, language)
        myEdit.apply()
    }

    fun getLanguage(): String? {
        return sharedPreferences.getString(languageKey, null)
    }

    /**
     * The language that was active the last time [markStartupLanguage] ran. Used by
     * `FirstActivity` to detect when an Android-13+ locale-change process restart
     * has just brought the app back up — so it can skip the splash flow and the
     * `version-driver` call that would otherwise fire on every language flip.
     */
    fun getLastStartupLanguage(): String? {
        return sharedPreferences.getString(lastStartupLanguageKey, null)
    }

    fun markStartupLanguage() {
        val current = getLanguage() ?: return
        sharedPreferences.edit().putString(lastStartupLanguageKey, current).apply()
    }
}