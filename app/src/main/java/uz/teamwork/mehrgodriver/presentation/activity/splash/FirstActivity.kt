package uz.teamwork.mehrgodriver.presentation.activity.splash

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.ACTION_STOP_SERVICE
import uz.teamwork.mehrgodriver.common.Constants.APP_VERSION
import uz.teamwork.mehrgodriver.common.Localisation
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.shared_pref.FcmTokenManager
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.databinding.ActivityFirstBinding
import uz.teamwork.mehrgodriver.databinding.DialogRestrictingOtherAppsBinding
import uz.teamwork.mehrgodriver.presentation.activity.main.MainActivity

@AndroidEntryPoint
class FirstActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFirstBinding

    // For ViewModel
    private val firstVM: FirstViewModel by viewModels()

    private var _dialogRestrictingOtherAppsBinding: DialogRestrictingOtherAppsBinding? = null
    private val dialogRestrictingOtherAppsBinding get() = _dialogRestrictingOtherAppsBinding!!
    private var dialogRestrictingOtherApps: Dialog? = null

    private var timer: CountDownTimer? = null
    private var timerIntent: CountDownTimer? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Localisation.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ kills the process when AppCompatDelegate.setApplicationLocales
        // is called, then the OS auto-relaunches us. We don't want to fire the
        // version-driver check (or show the splash) in that case — the version data
        // is still fresh from seconds earlier. We detect it by comparing the
        // language we recorded on the previous startup with the current one: if
        // they differ, this is a locale-change relaunch.
        val current = LanguageManager.getLanguage()
        val lastSeen = LanguageManager.getLastStartupLanguage()
        if (current != null && lastSeen != null && current != lastSeen) {
            Timber.tag("FCM")
                .d("FirstActivity: locale-change restart, skipping splash + version-driver")
            LanguageManager.markStartupLanguage()
            startActivity(Intent(this@FirstActivity, MainActivity::class.java))
            finish()
            return
        }
        LanguageManager.markStartupLanguage()

        binding = ActivityFirstBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeBands()

        binding.cvSkip.setDebouncedClickListener {
            val intent = Intent(this@FirstActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.cvUpdate.setDebouncedClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${Constants.APPLICATION_ID}")
                )
            )
        }

        getData()
    }

    override fun onRestart() {
        super.onRestart()

        getData()
    }

    override fun onStop() {
        super.onStop()

        dialogRestrictingOtherApps?.dismiss()
        dialogRestrictingOtherApps = null
        _dialogRestrictingOtherAppsBinding = null

        timer?.cancel()
        timer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        timerIntent?.cancel()
    }

    /**
     * Android 15+ enforces edge-to-edge: the splash theme's white status-bar / brand-teal
     * nav-bar colors are ignored and the layout draws behind the transparent bars. Paint
     * the same bands with inset-sized strips and lift the update-screen buttons above the
     * nav strip. Below 35 the theme still colors the real bars, so the strips stay gone —
     * sizing them there would overlay real content, since the window is not edge-to-edge.
     */
    private fun applyEdgeToEdgeBands() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val baseBottomPadding = binding.llBottom.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusTop = insets
                .getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
            val navBottom = insets
                .getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.vSplashStatusBarBg.updateLayoutParams { height = statusTop }
            binding.vSplashStatusBarBg.visibility = View.VISIBLE
            binding.vSplashNavBarBg.updateLayoutParams { height = navBottom }
            binding.vSplashNavBarBg.visibility = View.VISIBLE
            binding.llBottom.updatePadding(bottom = baseBottomPadding + navBottom)
            insets
        }
    }

    private fun getData() {
        if (Constants.IS_MAKTABGO) {
            // MaktabGo: свой бэкенд (maktabgo.uz); таксишный mobile/version-driver не вызываем.
            startActivity(Intent(this@FirstActivity, MainActivity::class.java))
            finish()
            return
        }
        val localFcm = FcmTokenManager.getToken()
        Timber.tag("FCM").d("mobile/version-driver: attaching device_token=%s", localFcm)
        lifecycleScope.launch {
            firstVM.updateApp(UserManager.getToken(), localFcm).collect {
                when (it) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {
                        val data = it.data!!
                        Timber.tag("FCM").d(
                            "mobile/version-driver OK, server device_token=%s",
                            data.deviceToken
                        )
                        // The version-driver call carried our local FCM token in the query
                        // string. If the server echoes back a different one we resync via
                        // /device-token (defensive half of the contract).
                        firstVM.syncFcmTokenIfNeeded(data.deviceToken)
                        if (data.versionCode <= APP_VERSION) {
                            val blockedAppsText = data.blockedApps

                            if (blockedAppsText.isNullOrEmpty()) {
                                Timber.d("Hello1")

                                val intent = Intent(this@FirstActivity, MainActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                val blockedAppsList: List<String> =
                                    blockedAppsText.split(",").map { it1 -> it1.trim() }

                                val result = canInstallCheckingFromManifest(blockedAppsList)
                                if (result.first) {
                                    Timber.d("Hello2")

                                    setCountDownTimerIntent()
                                } else {
                                    showDialog(result.second.toString())
                                }
                            }
                        } else {
                            binding.ivSplashScreen.visibility = View.GONE
                            binding.clUpdate.visibility = View.VISIBLE

                            if (data.required) {
                                binding.cvSkip.visibility = View.GONE
                            }
                        }

                    }

                    is Resource.Error -> {
                        Toast.makeText(this@FirstActivity, it.message, Toast.LENGTH_SHORT).show()

                        timer?.cancel()
                        timer = null
                        setCountDownTimer()
                    }
                }
            }
        }
    }

    private fun setCountDownTimerIntent() {
        timerIntent = object : CountDownTimer((3_000L).toLong(), 1_000L) {
            override fun onTick(p0: Long) {}

            override fun onFinish() {
                val intent = Intent(this@FirstActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
        (timerIntent as CountDownTimer).start()
    }

    private fun setCountDownTimer() {
        timer = object : CountDownTimer((3_000L).toLong(), 1_000L) {
            override fun onTick(p0: Long) {}

            override fun onFinish() {
                getData()
            }
        }
        (timer as CountDownTimer).start()
    }

    private fun canInstallCheckingFromManifest(blockedAppList: List<String>): Pair<Boolean, String?> {
        for (pkg in blockedAppList) {
            if (Constants.blockAppsInManifest.contains(pkg)) {
                return try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getApplicationInfo(
                            pkg,
                            PackageManager.ApplicationInfoFlags.of(0)
                        )
                    } else {
                        packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                    }
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    return Pair(false, appName)
                } catch (e: PackageManager.NameNotFoundException) {
                    Timber.d("Error for $pkg: ${e.message}")
                    continue
                }
            }
        }
        return Pair(true, null)
    }

//    private fun canInstallCheckingFromManifest(blockedAppList: List<String>): Pair<Boolean, String?> {
//        val blockedAppInManifest = blockedAppList.firstOrNull {
//            Constants.blockAppsInManifest.contains(it)
//        } ?: return Pair(true, null)
//
//        return try {
//            val appName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                val appInfo = packageManager.getApplicationInfo(blockedAppInManifest, PackageManager.ApplicationInfoFlags.of(0))
//                packageManager.getApplicationLabel(appInfo).toString()
//            } else {
//                val appInfo = packageManager.getApplicationInfo(blockedAppInManifest, PackageManager.GET_META_DATA)
//                packageManager.getApplicationLabel(appInfo).toString()
//            }
//            Pair(false, appName)
//        } catch (e: PackageManager.NameNotFoundException) {
//            Timber.d("Error")
//            Pair(true, null)
//        }
//    }

    @SuppressLint("SetTextI18n")
    private fun showDialog(appName: String) {
        if (dialogRestrictingOtherApps?.isShowing == true) return
        dialogRestrictingOtherApps = Dialog(this)
        _dialogRestrictingOtherAppsBinding =
            DialogRestrictingOtherAppsBinding.inflate(layoutInflater)
        dialogRestrictingOtherApps?.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogRestrictingOtherAppsBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // A custom-view dialog defaults to wrap_content width and clips its text;
            // pin it to 88% of the screen so the message reads cleanly.
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialogRestrictingOtherAppsBinding.apply {
            tvInfo.text =
                "${getString(R.string.part_one_restricting_apps_info)} \"$appName\" ${getString(R.string.part_second_restricting_apps_info)}"

            mcvCloseing.setDebouncedClickListener {
                if (MyTrackingService.isServiceRunning.value == true) {
                    sendCommandToService()
                } else {
                    dialogRestrictingOtherApps?.dismiss()
                    finish()
                }
            }
        }

        dialogRestrictingOtherApps?.show()
    }

    private fun sendCommandToService() =
        Intent(this, MyTrackingService::class.java).also {
            it.action = ACTION_STOP_SERVICE
            startService(it)
            dialogRestrictingOtherApps?.dismiss()
            finish()
        }

}