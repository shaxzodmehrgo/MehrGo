package uz.teamwork.mehrgodriver.common

import okhttp3.logging.HttpLoggingInterceptor
import uz.teamwork.mehrgodriver.BuildConfig

/**
 * The one place that decides how much HTTP traffic reaches logcat.
 *
 * `Level.BODY` prints the full request and response — URL, headers, body. On the authenticated
 * client that includes the login password, the SMS/Telegram OTP code and
 * `Authorization: Bearer <token>` on every single call. Shipping that in a release build puts the
 * driver's credentials in logcat, where anything with adb access, a bug report, or an OEM
 * diagnostic app can read them.
 *
 * There were two independent BODY loggers in this app (the DI-provided one in NetworkModule and a
 * second hand-rolled one inside OrderAdapter), and gating only one of them would have left the
 * hole open. Route every logger through here so a third one cannot silently regress it.
 */
fun httpLogLevel(): HttpLoggingInterceptor.Level =
    if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
    else HttpLoggingInterceptor.Level.NONE
