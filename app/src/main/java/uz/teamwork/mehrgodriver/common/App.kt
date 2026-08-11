package uz.teamwork.mehrgodriver.common

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.facebook.appevents.AppEventsLogger
import com.google.firebase.messaging.FirebaseMessaging
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import uz.teamwork.mehrgodriver.common.Constants.MAPKIT_KEY
import uz.teamwork.mehrgodriver.common.Constants.THEME_SYSTEM
import uz.teamwork.mehrgodriver.common.shared_pref.AccessPermissionsManager
import uz.teamwork.mehrgodriver.common.shared_pref.ErrorRequestManager
import uz.teamwork.mehrgodriver.common.shared_pref.FareCpidManager
import uz.teamwork.mehrgodriver.common.shared_pref.FcmTokenManager
import uz.teamwork.mehrgodriver.common.shared_pref.IntroduceManager
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.common.shared_pref.MapTypeManager
import uz.teamwork.mehrgodriver.common.shared_pref.ThemeManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager

@HiltAndroidApp
class App : Application() {
    companion object {
        lateinit var appContext: Context

        /**
         * Whether MainActivity has already been created in the CURRENT process. It's a plain
         * static, so it resets to false whenever the process is recreated. MainActivity reads it
         * to tell a config-change recreate (process alive -> restore the saved nav back stack)
         * from a cold start or a process-death restore (process reloaded -> start the nav host
         * fresh). Without this, restoring the FragmentManager after the OS kills the app — e.g.
         * when the user grants background location in Settings, which kills the process — rebuilds
         * a half-built back stack onto a blank window, i.e. an all-white screen.
         */
        @Volatile
        var mainActivityCreated = false
    }

    /** Create the Application context already in the saved language, so the process config (and every
     *  context derived from it, including services) starts localized — no AppCompat framework
     *  per-app-locale call, hence no task relaunch. Reads the pref directly (Managers aren't init yet). */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Localisation.wrap(base))
    }

    /** Keep resource-resolving contexts localized after a runtime config change (e.g. system font
     *  scale) — re-assert the saved app language onto the (possibly reset) configuration. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LanguageManager.getLanguage()?.let { Localisation.applyToConfig(this, it) }
    }

    override fun onCreate() {
        super.onCreate()

        MapKitFactory.setApiKey(MAPKIT_KEY)
        MapKitFactory.initialize(this)

        ErrorRequestManager.init(this)
        MapTypeManager.init(this)
        LanguageManager.init(this)
        ThemeManager.init(this)
        // Apply the saved day/night mode HERE, not at MainActivity.onCreate — that runs after
        // setContentView, and FirstActivity never applied it at all, so a cold start painted one
        // frame in the wrong mode and then recreated into the right one (a visible flash).
        ThemeManager.applyTheme(ThemeManager.getTheme() ?: THEME_SYSTEM)
        IntroduceManager.init(this)
        AccessPermissionsManager.init(this)
        UserManager.init(this)
        FcmTokenManager.init(this)
        FareCpidManager.init(this)
        Timber.plant(Timber.DebugTree())

        // Meta (Facebook) SDK — ensure the app session + auto app-events fire. The SDK
        // auto-initializes from the manifest meta-data via its ContentProvider, so no
        // FacebookSdk.sdkInitialize() call is required here.
        AppEventsLogger.activateApp(this)

        // Pull the current FCM token at startup so the first install has it
        // even before onNewToken fires. Cheap; the SDK caches it.
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Timber.tag("FCM").d("startup token=%s", token)
                if (token != null && token != FcmTokenManager.getToken()) {
                    Timber.tag("FCM")
                        .d("startup token differs from cached, saving + marking unsynced")
                    FcmTokenManager.saveToken(token)
                    FcmTokenManager.markUnsynced()
                } else {
                    Timber.tag("FCM")
                        .d("startup token matches cached, synced=%s", FcmTokenManager.isSynced())
                }
            } else {
                Timber.tag("FCM").e(task.exception, "failed to fetch FCM token at startup")
            }
        }

        appContext = applicationContext

        val language = LanguageManager.getLanguage()
        if (language != null) {
            // Re-assert on the shared app config too (attachBaseContext localized the base context;
            // this makes updateConfiguration-derived service contexts follow the same locale).
            Localisation.applyToConfig(this, language)
        }
    }
}