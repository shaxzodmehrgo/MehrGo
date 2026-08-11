package uz.teamwork.mehrgodriver.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import uz.teamwork.mehrgodriver.common.shared_pref.FcmTokenManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.domain.use_case.auth.RegisterDeviceTokenUC
import uz.teamwork.mehrgodriver.domain.use_case.main.ChangeLanguageUC

/**
 * Defensive half of the device-token contract from `driver_fcm_mobile_integration.md`:
 * if the server-stored token differs from the device's current FCM token (or the
 * server has none on file), push the local one via `POST /device-token/register`.
 *
 * The reactive half (Firebase `onNewToken`) lives in `MyFirebaseMessagingService`.
 */
object FcmTokenSync {

    // Process-wide scope for fire-and-forget syncs that must outlive their caller's
    // lifecycle — e.g. a language change recreates the activity right after the
    // POST is issued, which would otherwise cancel a viewModelScope-bound call.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncIfNeeded(
        registerDeviceTokenUC: RegisterDeviceTokenUC,
        scope: CoroutineScope,
        serverDeviceToken: String?
    ) {
        if (UserManager.getToken().isNullOrEmpty()) {
            Timber.tag("FCM").d("sync skip: user not logged in")
            return
        }
        val local = FcmTokenManager.getToken()
        if (local.isNullOrBlank()) {
            Timber.tag("FCM").d("sync skip: no local FCM token yet")
            return
        }
        if (serverDeviceToken == local && FcmTokenManager.isSynced()) {
            Timber.tag("FCM").d("sync skip: in sync (server==local, synced flag set)")
            return
        }

        Timber.tag("FCM").d(
            "sync needed: serverToken=%s localToken=%s synced=%s → POST /device-token",
            serverDeviceToken,
            local,
            FcmTokenManager.isSynced()
        )

        scope.launch(Dispatchers.IO) {
            registerDeviceTokenUC.invoke(local).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        FcmTokenManager.markSynced()
                        Timber.tag("FCM").d(
                            "sync OK: token_registered=%s",
                            resource.data?.data?.tokenRegistered
                        )
                    }

                    is Resource.Error -> {
                        Timber.tag("FCM").e("sync FAILED: %s", resource.message)
                    }

                    is Resource.Loading -> Timber.tag("FCM").d("sync loading")
                }
            }
        }
    }

    /**
     * Driver picked a new UI language. Fires `GET /user/change-language?language=<new>`
     * so the backend updates the user's stored language; the backend handler is
     * expected to also re-subscribe this device to the matching `driver_<new>` FCM
     * topic in the same request. We deliberately do NOT POST `/device-token/register`
     * again here — the change-language endpoint owns the full update.
     *
     * Runs on a process-scoped supervisor so the activity recreate triggered by
     * `AppCompatDelegate.setApplicationLocales` can't cancel the in-flight request.
     */
    fun applyLanguageChange(
        changeLanguageUC: ChangeLanguageUC,
        newLanguage: String
    ) {
        if (UserManager.getToken().isNullOrEmpty()) {
            Timber.tag("FCM").d("applyLanguageChange skip: user not logged in")
            return
        }

        Timber.tag("FCM")
            .d("applyLanguageChange: GET /user/change-language?language=%s", newLanguage)

        appScope.launch {
            changeLanguageUC.invoke(newLanguage).collect { resource ->
                when (resource) {
                    is Resource.Success -> Timber.tag("FCM")
                        .d("change-language OK: backend stored=%s", resource.data?.data)

                    is Resource.Error -> Timber.tag("FCM")
                        .e("change-language FAILED: %s", resource.message)

                    is Resource.Loading -> Timber.tag("FCM").d("change-language loading")
                }
            }
        }
    }

    /**
     * Unconditionally re-POST the local FCM token to `/device-token/register`.
     * Use this when the *backend's* view of the driver needs refreshing even though
     * the token itself hasn't changed — e.g. after the driver flips their UI
     * language, so the backend re-subscribes them to the new `driver_<lang>` topic
     * (spec §7).
     *
     * Runs on a process-scoped supervisor so an immediate activity recreate
     * (which `AppCompatDelegate.setApplicationLocales` triggers) doesn't cancel
     * the in-flight request.
     */
    fun forceResync(registerDeviceTokenUC: RegisterDeviceTokenUC) {
        if (UserManager.getToken().isNullOrEmpty()) {
            Timber.tag("FCM").d("forceResync skip: user not logged in")
            return
        }
        val local = FcmTokenManager.getToken()
        if (local.isNullOrBlank()) {
            Timber.tag("FCM").d("forceResync skip: no local FCM token")
            return
        }

        Timber.tag("FCM").d("forceResync: POST /device-token/register")
        FcmTokenManager.markUnsynced()

        appScope.launch {
            registerDeviceTokenUC.invoke(local).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        FcmTokenManager.markSynced()
                        Timber.tag("FCM").d(
                            "forceResync OK: token_registered=%s",
                            resource.data?.data?.tokenRegistered
                        )
                    }

                    is Resource.Error -> {
                        Timber.tag("FCM").e("forceResync FAILED: %s", resource.message)
                    }

                    is Resource.Loading -> Timber.tag("FCM").d("forceResync loading")
                }
            }
        }
    }
}
