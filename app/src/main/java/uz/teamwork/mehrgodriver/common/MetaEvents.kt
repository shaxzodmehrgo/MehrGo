package uz.teamwork.mehrgodriver.common

import android.content.Context
import android.os.Bundle
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger

/**
 * Single funnel for Meta (Facebook) app-events used for install attribution /
 * conversion measurement only. Mirrors the iOS `MetaEvents` helper.
 *
 * This object and [App] are the ONLY places that touch the Facebook SDK — feature
 * code must call [MetaEvents] rather than [AppEventsLogger] directly.
 */
object MetaEvents {

    /** Driver finished registration — Meta standard "CompletedRegistration". */
    fun logCompletedRegistration(context: Context) =
        AppEventsLogger.newLogger(context)
            .logEvent(AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION)

    /** A trip was completed; [fareUzs] is the gross fare in UZS. */
    fun logTripCompleted(context: Context, fareUzs: Long) {
        val params = Bundle().apply {
            putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "UZS")
        }
        AppEventsLogger.newLogger(context)
            .logEvent("TripCompleted", fareUzs.toDouble(), params)
    }
}
