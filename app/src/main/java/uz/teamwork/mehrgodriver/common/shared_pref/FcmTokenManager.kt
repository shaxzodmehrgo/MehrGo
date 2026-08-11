package uz.teamwork.mehrgodriver.common.shared_pref

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the FCM device token. Lives in its own SharedPreferences file so it
 * survives a logout (the token is device-bound, not user-bound).
 *
 * The `synced` flag tracks whether the current token has been pushed to the
 * backend yet. When the token rotates or the user logs in, mark it unsynced
 * and re-send. The send-to-backend hook will be wired in later when the API
 * is ready.
 */
object FcmTokenManager {
    private const val PREF_NAME = "fcmTokenPref"
    private const val KEY_TOKEN = "fcm_token"
    private const val KEY_SYNCED = "fcm_token_synced"

    private lateinit var prefs: SharedPreferences

    fun init(application: Application) {
        prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        val current = prefs.getString(KEY_TOKEN, null)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putBoolean(KEY_SYNCED, current == token && prefs.getBoolean(KEY_SYNCED, false))
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun isSynced(): Boolean = prefs.getBoolean(KEY_SYNCED, false)

    fun markSynced() {
        prefs.edit().putBoolean(KEY_SYNCED, true).apply()
    }

    fun markUnsynced() {
        prefs.edit().putBoolean(KEY_SYNCED, false).apply()
    }
}
