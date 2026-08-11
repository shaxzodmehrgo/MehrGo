package uz.teamwork.mehrgodriver.common.shared_pref

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the highest `cpid` ever issued per order. Lets [GpsBatchUploader]
 * keep `cpid` monotonic across process death and across DB row purges (rows
 * are deleted right after the server acks them, so the local DB can't be
 * trusted as a "max cpid I ever sent" oracle once a batch has been acked).
 *
 * Keyed by `cpid_<orderId>`; cleared per-order when an order is cancelled or
 * finished. Per-order cleanup happens in [GpsBatchUploader.stop] indirectly
 * via DAO purge, but cpid prefs follow the order lifecycle explicitly.
 */
object FareCpidManager {
    private const val PREF_NAME = "fare_cpid_pref"
    private const val KEY_PREFIX = "cpid_"

    private lateinit var prefs: SharedPreferences

    fun init(application: Application) {
        prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun last(orderId: Int): Long = prefs.getLong(KEY_PREFIX + orderId, 0L)

    /** Bumps stored cpid to [value] when it's higher. Safe to call concurrently. */
    fun bumpAtLeast(orderId: Int, value: Long) {
        val key = KEY_PREFIX + orderId
        val current = prefs.getLong(key, 0L)
        if (value > current) {
            prefs.edit().putLong(key, value).apply()
        }
    }

    fun clear(orderId: Int) {
        prefs.edit().remove(KEY_PREFIX + orderId).apply()
    }
}
