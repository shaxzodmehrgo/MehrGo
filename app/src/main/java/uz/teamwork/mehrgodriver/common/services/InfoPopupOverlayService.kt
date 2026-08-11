package uz.teamwork.mehrgodriver.common.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.common.shared_pref.ThemeManager
import uz.teamwork.mehrgodriver.databinding.DialogInfoPopupBinding
import uz.teamwork.mehrgodriver.presentation.activity.main.MainActivity
import java.util.Locale

/**
 * Shows the reusable info popup ([R.layout.dialog_info_popup]) as an OVER-OTHER-APPS overlay
 * (TYPE_APPLICATION_OVERLAY, reusing the SYSTEM_ALERT_WINDOW permission already used by
 * [AutoOfferService]) so trip notices reach the driver even when the app is backgrounded.
 *
 * The in-app [showInfoPopup] still handles the foreground case; a backgrounded service event
 * (e.g. wait auto-stopped, ride auto-started) starts THIS service instead. Started only from a
 * foreground service (MyTrackingService), so the Android 8+ background-start limit doesn't apply.
 */
class InfoPopupOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val titleRes = intent?.getIntExtra(EXTRA_TITLE, 0) ?: 0
        if (titleRes == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val iconRes = intent?.getIntExtra(EXTRA_ICON, R.drawable.ic_error_circle)
            ?: R.drawable.ic_error_circle
        val messageRes = intent?.getIntExtra(EXTRA_MESSAGE, 0) ?: 0
        val autoDismissMs = intent?.getLongExtra(EXTRA_AUTO_DISMISS, 0L) ?: 0L
        val openApp = intent?.getBooleanExtra(EXTRA_OPEN_APP, false) ?: false
        showOverlay(iconRes, titleRes, messageRes, autoDismissMs, openApp, startId)
        return START_NOT_STICKY
    }

    private fun showOverlay(
        iconRes: Int, titleRes: Int, messageRes: Int, autoDismissMs: Long, openApp: Boolean,
        startId: Int
    ) {
        removeOverlay()
        // Inflate with a context carrying the app's chosen theme + locale (a bare service
        // context follows the SYSTEM ui-mode/locale — see AutoOfferService.themedContext).
        val binding = DialogInfoPopupBinding.inflate(LayoutInflater.from(themedLocalizedContext()))
        binding.ivIcon.setImageResource(iconRes)
        binding.tvTitle.setText(titleRes)
        if (messageRes != 0) {
            binding.tvMessage.visibility = View.VISIBLE
            binding.tvMessage.setText(messageRes)
        } else {
            binding.tvMessage.visibility = View.GONE
        }
        if (openApp) {
            // "Reached destination" notice → OK opens the app so the in-app finish dialog shows.
            binding.btnOk.setOnClickListener {
                startActivity(
                    Intent(this, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                )
                stopSelf()
            }
        } else {
            binding.btnOk.setOnClickListener { stopSelf() }
        }

        rootView = binding.root
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            windowManager?.addView(binding.root, windowParams())
        } catch (_: Exception) {
            stopSelf(startId)
            return
        }
        if (autoDismissMs > 0L) {
            // stopSelf(startId): if a newer notice (a later start) has since replaced this overlay,
            // this stale auto-dismiss is a no-op instead of tearing down the newer overlay.
            dismissRunnable = Runnable { stopSelf(startId) }
            handler.postDelayed(dismissRunnable!!, autoDismissMs)
        }
    }

    private fun windowParams(): WindowManager.LayoutParams {
        // ~88% of the screen width so titles stay whole + the message/OK button never clip
        // (same width the in-app popup uses).
        val width = (resources.displayMetrics.widthPixels * 0.88f).toInt()
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            // Not focusable → touches outside the card pass through to the app/launcher below;
            // the OK button still receives its tap.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        return params
    }

    private fun themedLocalizedContext(): Context {
        val night = when (ThemeManager.getTheme()) {
            Constants.THEME_DAY -> false
            Constants.THEME_NIGHT -> true
            else -> ThemeManager.isSystemNightMode(applicationContext)
        }
        val lang = LanguageManager.getLanguage() ?: "uz"
        val config = Configuration(applicationContext.resources.configuration)
        val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        config.setLocale(Locale(lang))
        val base = applicationContext.createConfigurationContext(config)
        // A bare service/overlay context resolves to the SYSTEM default theme, not the app's
        // Theme.MaterialComponents descendant — so any Material widget in the inflated layout (or an
        // ?attr/ theme lookup) throws "requires Theme.MaterialComponents" via ThemeEnforcement
        // (Crashlytics InflateException). Wrap the night/locale context in the app theme so the
        // overlay is safe even if a Material component is ever added to dialog_info_popup.xml.
        return ContextThemeWrapper(base, R.style.Theme_TeamworkTaxi)
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        removeOverlay()
        windowManager = null
    }

    private fun removeOverlay() {
        // Cancel any pending auto-dismiss from a PRIOR event first — otherwise a stale 5s runnable
        // (e.g. from a wait-stopped notice) fires stopSelf() and tears down THIS newer overlay
        // (e.g. the no-auto-dismiss "reached destination" prompt) before the driver can act.
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        rootView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        rootView = null
    }

    companion object {
        private const val EXTRA_ICON = "extra_icon"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_MESSAGE = "extra_message"
        private const val EXTRA_AUTO_DISMISS = "extra_auto_dismiss"
        private const val EXTRA_OPEN_APP = "extra_open_app"

        /** Mirror of MapFragment.showInfoPopup, but as an over-other-apps overlay. */
        fun show(
            context: Context,
            iconRes: Int,
            titleRes: Int,
            messageRes: Int = 0,
            autoDismissMs: Long = 0L,
            openApp: Boolean = false
        ) {
            val intent = Intent(context, InfoPopupOverlayService::class.java).apply {
                putExtra(EXTRA_ICON, iconRes)
                putExtra(EXTRA_TITLE, titleRes)
                putExtra(EXTRA_MESSAGE, messageRes)
                putExtra(EXTRA_AUTO_DISMISS, autoDismissMs)
                putExtra(EXTRA_OPEN_APP, openApp)
            }
            context.startService(intent)
        }
    }
}
