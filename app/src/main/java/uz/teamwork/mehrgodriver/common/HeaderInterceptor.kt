package uz.teamwork.mehrgodriver.common

import android.os.Build
import com.google.gson.reflect.TypeToken
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import uz.teamwork.mehrgodriver.BuildConfig
import uz.teamwork.mehrgodriver.common.shared_pref.ErrorRequestManager
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.socket.MySocketListener
import uz.teamwork.mehrgodriver.domain.model.requests.ErrorRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//class HeaderInterceptor: Interceptor {
//    override fun intercept(chain: Interceptor.Chain): Response {
//        var request = chain.request()
//
//        request = request.newBuilder()
//            .addHeader("Authorization", UserManager.getBearerToken())
//            .addHeader("Accept-Language", LanguageManager.getLanguage() ?: "")
//            .build()
//
//        val response = chain.proceed(request)
//        return response
//    }
//}

class HeaderInterceptor : Interceptor {

    companion object {
        // Device/app info contract (MOBILE.md): sent on EVERY authorized request so support
        // can see each driver's app version + device in the admin panel. Diagnostic fields
        // only — never secrets. Values are static per process, so compute them once.
        private val APP_VERSION =
            sanitizeHeader("${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}")
        private val OS_VERSION = sanitizeHeader(Build.VERSION.RELEASE ?: "")
        private val DEVICE_BRAND = sanitizeHeader(Build.MANUFACTURER ?: "")
        private val DEVICE_MODEL = sanitizeHeader(Build.MODEL ?: "")

        /** Backend truncates at 64 chars and expects ASCII — enforce both client-side. */
        private fun sanitizeHeader(raw: String): String =
            raw.filter { it.code in 32..126 }.take(64)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        request = request.newBuilder()
            .addHeader("Authorization", UserManager.getBearerToken())
            .addHeader("Accept-Language", LanguageManager.getLanguage() ?: "")
            .addHeader("X-Platform", "android")
            .addHeader("X-App-Version", APP_VERSION)
            .addHeader("X-OS-Version", OS_VERSION)
            .addHeader("X-Device-Brand", DEVICE_BRAND)
            .addHeader("X-Device-Model", DEVICE_MODEL)
            .build()

        val response = chain.proceed(request)

        if (!response.isSuccessful) {
            // Read the response body
            val responseBody = response.body
            val source: BufferedSource? = responseBody?.source()

            // Buffer the entire body.
            source?.request(Long.MAX_VALUE)
            val buffer: Buffer? = source?.buffer
            val responseBodyString = buffer?.clone()?.readString(Charsets.UTF_8) ?: "Bo'sh satr"

            val duration = response.receivedResponseAtMillis - response.sentRequestAtMillis
            val socketStatus = MySocketListener.isSocketListener.value ?: false
            val errorRequest = ErrorRequest(
                request.url.toString(),
                // Was `request.headers.value(0)` — the Authorization header, i.e. the driver's
                // bearer token. That put a live credential into SharedPreferences in plaintext
                // and then uploaded it to the server with every failed request. It bought
                // nothing: `user/report` goes through THIS interceptor too, so the server
                // already authenticates the sender. Deliberately blank; keep the field so the
                // wire shape does not change.
                "",
                convertMillisToSimpleTime(response.sentRequestAtMillis),
                convertMillisToSimpleTime(response.receivedResponseAtMillis),
                duration,
                socketStatus,
                response.code,
                responseBodyString
            )

            val typeToken = object : TypeToken<List<ErrorRequest>>() {}
            ErrorRequestManager.addItemToList(errorRequest, typeToken)
        }

        return response
    }

    private fun convertMillisToSimpleTime(millis: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return dateFormat.format(Date(millis))
    }
}