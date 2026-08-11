package uz.teamwork.mehrgodriver.common.shared_pref

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

/**
 * Tracks whether the user has already passed through the AccessPermissionsFragment
 * with every required permission granted. Without this flag, fragments that
 * re-check permissions in `onViewCreated` (Login, Home, Map) keep bouncing the
 * user back to the access screen after login on devices where
 * `Settings.canDrawOverlays` returns `false` until the next process restart
 * (notably some Transsion / Xiaomi OEM builds) — even after the user granted
 * the overlay permission. Once we know the user has completed the flow once,
 * we stop auto-redirecting purely because overlay reports as missing.
 */
object AccessPermissionsManager {
    private const val PREF_NAME = "myAccessPermissionsPref"
    private const val KEY_COMPLETED = "access_permissions_completed"
    private const val KEY_PENDING_RESTART = "pending_restart_after_settings"

    private lateinit var sharedPreferences: SharedPreferences

    fun init(application: Application) {
        sharedPreferences = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun markCompleted() {
        sharedPreferences.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    fun isCompleted(): Boolean = sharedPreferences.getBoolean(KEY_COMPLETED, false)

    /**
     * Set right before the access screen sends the user to system Settings to grant a permission
     * (background location in particular kills/recreates the app). MainActivity consumes it on the
     * next foreground and restarts cleanly through the splash instead of continuing into a restored,
     * half-built (white) screen. Persisted so it survives the process death the grant can trigger.
     */
    fun markPendingRestart() {
        sharedPreferences.edit().putBoolean(KEY_PENDING_RESTART, true).apply()
    }

    fun consumePendingRestart(): Boolean {
        val pending = sharedPreferences.getBoolean(KEY_PENDING_RESTART, false)
        if (pending) sharedPreferences.edit().putBoolean(KEY_PENDING_RESTART, false).apply()
        return pending
    }
}
