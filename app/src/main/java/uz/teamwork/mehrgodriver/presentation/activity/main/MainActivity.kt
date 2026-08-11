package uz.teamwork.mehrgodriver.presentation.activity.main

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.App
import uz.teamwork.mehrgodriver.common.AppReloadFlag
import uz.teamwork.mehrgodriver.common.CheckPermissions
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.EXTRA_PUSH_KEY
import uz.teamwork.mehrgodriver.common.Constants.FCM_KEY_DOCUMENT_APPROVED
import uz.teamwork.mehrgodriver.common.Constants.FCM_KEY_DOCUMENT_REJECTED
import uz.teamwork.mehrgodriver.common.Constants.FCM_KEY_DRIVER_BONUS_CREDITED
import uz.teamwork.mehrgodriver.common.Constants.FCM_KEY_NEW_DRIVER_NOTIFICATION
import uz.teamwork.mehrgodriver.common.Constants.THEME_SYSTEM
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.InfoPopup
import uz.teamwork.mehrgodriver.common.Localisation
import uz.teamwork.mehrgodriver.common.PendingDeepLink
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.common.services.WindowToAppService
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.shared_pref.AccessPermissionsManager
import uz.teamwork.mehrgodriver.common.shared_pref.ThemeManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.ActivityMainBinding
import uz.teamwork.mehrgodriver.databinding.DialogFragmentInsideAppBinding
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.presentation.activity.splash.FirstActivity
import uz.teamwork.mehrgodriver.presentation.maps.yandex_map.MapFragment
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var gson: Gson

    private var _dialogInsideAppBinding: DialogFragmentInsideAppBinding? = null
    private val dialogInsideAppBinding get() = _dialogInsideAppBinding!!
    private var dialogInsideApp: Dialog? = null

    private var orderId: Int? = null

    /** Frozen-frame overlay of the theme/locale crossfade (see [playRecreateCrossfade]) — kept as
     *  fields so [onDestroy] can clean up if the activity dies mid-fade. */
    private var crossfadeOverlay: ImageView? = null
    private var crossfadeSnapshot: Bitmap? = null

    companion object {
        val isActiveShowDialogInsideAppForMap = MutableLiveData<Boolean?>(null)

        /** Tag for the persistent, Activity-hosted MapFragment mounted in flPersistentMap. */
        const val TAG_PERSISTENT_MAP = "persistent_map"

        /** Max age of a pre-recreate snapshot before it's considered stale (see
         *  [playRecreateCrossfade]) — a real recreate consumes it within a few hundred ms. */
        private const val SNAPSHOT_MAX_AGE_MS = 4_000L

        /** Last-known REAL status-bar inset (px). Cached statically so it survives an Activity
         *  recreate (language/theme change): the recreated window has no insets on its first frame,
         *  and the status_bar_height dimen is smaller than the true inset on cutout/tall-status-bar
         *  devices — which left a pushed toolbar's title briefly under the status bar after recreate. */
        @Volatile
        var cachedStatusInsetTop = 0
    }

    override fun attachBaseContext(newBase: Context) {
        // Inflate this activity in the saved language. On a language change we save + recreate(), and
        // recreate() re-runs attachBaseContext on the fresh instance → the whole UI comes back in the
        // new language IN PLACE (no AppCompat framework locale call, so no FirstActivity relaunch).
        super.attachBaseContext(Localisation.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Returning from the access-permissions Settings round-trip (a background location grant kills
        // the app): don't continue into the restored, half-built screen — restart via the splash.
        val restartAfterPermission = AccessPermissionsManager.consumePendingRestart()
        // On a config-change recreate the process is alive, so restore the saved back stack. On a cold
        // start, a process-death restore, or a permission-return restart, start fresh instead —
        // restoring the FragmentManager would rebuild a half-built nav back stack onto a blank window.
        super.onCreate(
            if (!restartAfterPermission && App.mainActivityCreated) savedInstanceState else null
        )
        App.mainActivityCreated = true
        if (restartAfterPermission) {
            restartFromSplash()
            return
        }
        // Register the pushed-screen toolbar status-bar inset BEFORE setContentView so it also
        // catches fragments restored from the saved back stack during inflation — e.g. the
        // access-permissions screen after an activity recreate on the Settings round-trip. Those
        // restored views are created during setContentView, i.e. before the listener would
        // otherwise be registered, which left their title drawn under the status bar.
        setupEdgeToEdge()

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupNavBarInset()

        navigateFromPushIfNeeded(intent)
        // Telegram "Buyurtmani qabul qilish" App Link. Captured BEFORE anything navigates: a cold
        // start from the link can land on the login flow, and the id has to outlive that.
        PendingDeepLink.capture(intent?.data)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                // navigator-only build: no bottom-nav shell any more. HomeFragment is a pure router
                // that redirects away in onCreate, so it never actually shows bars.
                R.id.navigation_home -> hideBottomBars()
                // Home map: "our style" solid nav-bar strip + divider behind the system nav (like
                // every other screen), NOT a transparent edge-to-edge nav. The map's own bottom
                // controls (llBottomArea) are already inset above the nav bar, so the strip only
                // fills the nav-bar zone behind the system buttons.
                R.id.mapFragment -> showNavBarBacking()
                // Tab-less normal screens (auth/install + pushed details) get the themed nav-bar strip.
                else -> showNavBarBacking()
            }
            applyNavHostBottomInset(destination.id)
            // Persistent map: mount it on first arrival, and hide/show the NavHost + notify the map
            // so it's the interactive front surface only when nothing is pushed on top of it.
            updateMapSurface(destination.id)
            // Logged out (login/register flow reached) → drop the persistent map so a later re-login
            // mounts a fresh one for the new session instead of showing the previous driver's map.
            if (UserManager.getUser() == null) teardownPersistentMap()
            // Second chance for an order link that arrived while the driver was signed out or before
            // the graph was ready — the map is the first destination inside the app proper.
            if (destination.id == R.id.mapFragment) routePendingDeepLink()
        }

        // Pushed screens (no bottom nav) get the gesture-bar height as bottom padding so
        // their content/buttons aren't hidden behind the transparent navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.content) { _, insets ->
            navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            applyNavHostBottomInset(navController.currentDestination?.id)
            // Keep the themed nav-bar backing strip the exact gesture/nav-bar height.
            if (binding.vNavBarBg.visibility == View.VISIBLE) {
                binding.vNavBarBg.updateLayoutParams { height = navBarInset }
            }
            insets
        }

        MyTrackingService.listenerNewPrivateOrder.observe(this) { order ->
            if (MyTrackingService.cdtAcceptingIndividualOrder != null && order != null) {
                showDialogInsideApp(order)
            }
        }

        ThemeManager.applyTheme(ThemeManager.getTheme() ?: THEME_SYSTEM)

        // Fade the frozen pre-recreate frame out over the rebuilt UI (theme / locale switch).
        playRecreateCrossfade()
    }

    /**
     * Recreate the activity with a snapshot crossfade instead of a bare [recreate] — used for the
     * theme / language switches. It freezes the current window into a bitmap (PixelCopy, so the
     * Yandex GL map is captured too), runs [applyChange] (which performs the change and triggers the
     * real recreate), and the rebuilt activity lays that frozen frame on top and fades it out. This
     * hides the window-background flash, the system-bar re-init flicker and the layout jump of a raw
     * recreate. Falls back to running [applyChange] directly if the window can't be captured.
     */
    fun recreateWithCrossfade(applyChange: () -> Unit) {
        val decor = window.decorView
        val w = decor.width
        val h = decor.height
        if (_binding == null || w <= 0 || h <= 0) {
            applyChange()
            return
        }
        val snapshot = runCatching {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }.getOrNull()
        if (snapshot == null) {
            applyChange()
            return
        }
        runCatching {
            PixelCopy.request(window, snapshot, { result ->
                if (result == PixelCopy.SUCCESS) {
                    AppReloadFlag.transitionSnapshot = snapshot
                    AppReloadFlag.transitionSnapshotAtMs = SystemClock.elapsedRealtime()
                } else {
                    snapshot.recycle()
                    AppReloadFlag.transitionSnapshot = null
                }
                applyChange()
            }, Handler(Looper.getMainLooper()))
        }.onFailure {
            snapshot.recycle()
            applyChange()
        }
    }

    /** Lay a pending [recreateWithCrossfade] snapshot over the freshly built UI and fade it out —
     *  the rebuild (incl. the map GL surface) happens behind the frozen frame, so the switch reads
     *  as a smooth crossfade rather than a flashing reload. */
    private fun playRecreateCrossfade() {
        val snapshot = AppReloadFlag.transitionSnapshot ?: return
        AppReloadFlag.transitionSnapshot = null
        // A snapshot older than a moment belongs to a change that never actually recreated
        // (e.g. picking the System theme while the effective day/night stayed the same) — laying
        // it over THIS unrelated recreate (rotation, a later switch) would flash a stale frame.
        val age = SystemClock.elapsedRealtime() - AppReloadFlag.transitionSnapshotAtMs
        if (age > SNAPSHOT_MAX_AGE_MS || snapshot.isRecycled) {
            if (!snapshot.isRecycled) snapshot.recycle()
            return
        }
        val overlay = ImageView(this).apply {
            setImageBitmap(snapshot)
            scaleType = ImageView.ScaleType.FIT_XY
            // Swallow taps while the frozen frame is up so nothing behind reacts mid-transition.
            isClickable = true
            isFocusable = true
        }
        crossfadeOverlay = overlay
        crossfadeSnapshot = snapshot
        addContentView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        overlay.animate()
            .alpha(0f)
            .setStartDelay(160L)
            .setDuration(260L)
            .withEndAction { clearCrossfadeOverlay() }
            .start()
    }

    /** Remove the frozen-frame overlay and recycle its bitmap — runs on fade end, and again from
     *  [onDestroy] as a safety net if the activity dies mid-fade (cancel skips the end action). */
    private fun clearCrossfadeOverlay() {
        crossfadeOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        crossfadeOverlay = null
        crossfadeSnapshot?.takeIf { !it.isRecycled }?.recycle()
        crossfadeSnapshot = null
    }

    /** Relaunch the app from the splash, clearing the task — used after a permission round-trip to
     *  Settings so we never continue on a restored/blank screen. */
    private fun restartFromSplash() {
        startActivity(
            Intent(this, FirstActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        finish()
    }

    override fun onStart() {
        super.onStart()
        // A background-location grant that returned without recreating us still routes back here —
        // restart cleanly so we never continue on a stale screen after the re-permission round-trip.
        if (AccessPermissionsManager.consumePendingRestart()) {
            restartFromSplash()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        if (UserManager.getUser() != null) {
            if (CheckPermissions.isOverlayPermissionAvailable(this)) {
                if (CheckPermissions.checkHasDrawOverlayPermissions(this)) {
                    val intent = Intent(this, WindowToAppService::class.java)
                    intent.action = Constants.ACTION_STOP_WINDOW_SERVICE
                    startService(intent)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()

        if (UserManager.getUser() != null) {
            if (CheckPermissions.isOverlayPermissionAvailable(this)) {
                if (CheckPermissions.checkHasDrawOverlayPermissions(this)) {
                    if (MyTrackingService.isServiceRunning.value == true) {
                        val intent = Intent(this, WindowToAppService::class.java)
                        intent.action = Constants.ACTION_START_WINDOW_SERVICE
                        startService(intent)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearCrossfadeOverlay()
        _binding = null
        // Dismiss BEFORE releasing the reference. Nulling a still-showing dialog drops the only
        // handle to its window, so nothing can ever dismiss it — Android reports `WindowLeaked`.
        // This Activity is destroyed on every theme/locale recreate(), so it is the most exposed
        // owner in the app.
        dialogInsideApp?.dismiss()
        _dialogInsideAppBinding = null
        dialogInsideApp = null

        orderId = null
    }

    // Functions

    private var navBarInset = 0

    /** Tab-less normal screens (auth/install + pushed details): a solid themed strip + divider in the
     *  system-nav-bar area so the bottom reads as "our style" even though the window is edge-to-edge.
     *  Content on these screens is already inset above the nav bar (see [applyNavHostBottomInset]). */
    private fun showNavBarBacking() {
        binding.navView.visibility = View.GONE
        binding.vNavDivider.visibility = View.GONE
        binding.vNavBarBg.updateLayoutParams { height = navBarInsetOrFallback() }
        binding.vNavBarBg.visibility = View.VISIBLE
        binding.vNavBarBgDivider.visibility = View.VISIBLE
    }

    /** Full-screen maps: content fills behind the transparent system nav — nothing at the bottom. */
    private fun hideBottomBars() {
        binding.navView.visibility = View.GONE
        binding.vNavDivider.visibility = View.GONE
        binding.vNavBarBg.visibility = View.GONE
        binding.vNavBarBgDivider.visibility = View.GONE
    }

    private fun navBarInsetOrFallback(): Int =
        if (navBarInset > 0) navBarInset
        else ViewCompat.getRootWindowInsets(window.decorView)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0

    /**
     * The top-level tabs sit above the visible bottom nav, and the navigator map fills
     * behind the gesture bar — neither needs an inset. Every other (pushed) screen gets
     * the gesture-bar height as bottom padding so its content/buttons aren't hidden
     * behind the transparent navigation bar.
     */
    private fun applyNavHostBottomInset(destinationId: Int?) {
        val fillsBottom = destinationId == R.id.navigation_home ||
                destinationId == R.id.mapFragment
        binding.content.updatePadding(
            bottom = if (fillsBottom) 0 else navBarInset
        )
    }

    /** True when the NavHost's current destination is the map placeholder — i.e. nothing is pushed on
     *  top of the persistent map. The Activity-hosted MapFragment reads this (it is always RESUMED, so
     *  it can't use its own onResume/onPause to know whether it is the visible surface). */
    fun isMapFront(): Boolean =
        findNavController(R.id.nav_host_fragment_activity_main).currentDestination?.id == R.id.mapFragment

    /** Mount the real MapFragment ONCE into the persistent (Activity-hosted) container the first time
     *  the driver reaches the map destination. From then on its Yandex GL surface follows the Activity
     *  lifecycle, so it is never torn down/rebuilt on in-app navigation. Lazy so LIST-mode users who
     *  never open the map don't spin up a MapView. */
    private fun mountPersistentMapIfNeeded() {
        if (_binding == null || UserManager.getUser() == null) return
        // Already mounted — including RESTORED after an Activity recreate (language/theme change): the
        // FragmentManager re-attaches the map fragment, but the XML container defaults to GONE, which,
        // with the NavHost hidden on the map, would show a WHITE SCREEN. Ensure the container is shown.
        if (supportFragmentManager.findFragmentByTag(TAG_PERSISTENT_MAP) != null) {
            binding.flPersistentMap.visibility = View.VISIBLE
            return
        }
        binding.flPersistentMap.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.flPersistentMap, MapFragment(), TAG_PERSISTENT_MAP)
            .commitAllowingStateLoss()
    }

    /** Keep the persistent map in sync with the NavHost's current destination: when the map is the
     *  destination (transparent placeholder) HIDE the NavHost so the map behind it is interactive and
     *  never bleeds through; when a screen is pushed, SHOW the NavHost (opaque) over the still-attached
     *  map. Notify the map either way so it re-gates popups / mini-map / Back / status-bar and refreshes
     *  the active order on return. */
    private fun updateMapSurface(destinationId: Int?) {
        if (_binding == null) return
        val mapFront = destinationId == R.id.mapFragment
        if (mapFront) mountPersistentMapIfNeeded()
        // Keep the map's container VISIBLE whenever the map exists — so it renders (and, after a
        // language/theme recreate, REBUILDS) BEHIND a covering screen and is ready with no flash when
        // revealed. Without this the restored map sits in a GONE container and only rebuilds — visibly
        // — the moment you return to it.
        if (supportFragmentManager.findFragmentByTag(TAG_PERSISTENT_MAP) != null) {
            binding.flPersistentMap.visibility = View.VISIBLE
        }
        val navHost = findViewById<View>(R.id.nav_host_fragment_activity_main)
        if (mapFront) {
            // Hide the NavHost entirely so the persistent map behind it is the interactive surface
            // (touches reach it) and no transparent placeholder can intercept or reveal it.
            navHost?.visibility = View.GONE
        } else {
            // Opaque backing so a pushed screen with any transparent region (e.g. an empty
            // notifications list) can't reveal the live map still attached behind the NavHost.
            navHost?.setBackgroundResource(R.color.white_black)
            navHost?.visibility = View.VISIBLE
        }
        val map = supportFragmentManager.findFragmentByTag(TAG_PERSISTENT_MAP) as? MapFragment
        if (mapFront) map?.onMapBecameFront() else map?.onMapBecameCovered()
    }

    /** Remove the persistent map + hide its container (on logout). The next login re-mounts a fresh
     *  instance via [mountPersistentMapIfNeeded] on the first return to the map destination. */
    private fun teardownPersistentMap() {
        val map = supportFragmentManager.findFragmentByTag(TAG_PERSISTENT_MAP) ?: return
        supportFragmentManager.beginTransaction().remove(map).commitAllowingStateLoss()
        binding.flPersistentMap.visibility = View.GONE
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Activity is singleTop, so a link tapped while the app is already open arrives here rather
        // than starting a second MainActivity. Null-guarded: the parameter is nullable here and
        // setIntent(null) would blank what getIntent() returns for everything after this.
        intent?.let { setIntent(it) }
        navigateFromPushIfNeeded(intent)
        if (PendingDeepLink.capture(intent?.data)) routePendingDeepLink()
    }

    /**
     * Send a pending Telegram order link (see [PendingDeepLink]) to the orders pool, which opens the
     * offer sheet for that id once its list has loaded.
     *
     * Deliberately does NOT clear the pending id on failure: a link tapped by a signed-out driver
     * has to survive the whole login flow, and the destination listener retries this every time the
     * map comes up. `OrdersMapFragment` is what consumes the id — including the "not in the pool any
     * more" case — so a one-shot link can't re-open on a later visit.
     */
    private fun routePendingDeepLink() {
        val pending = PendingDeepLink.peek() ?: return
        // Auth flow still in front: keep it pending, the listener will call back.
        if (UserManager.getUser() == null) {
            timber.log.Timber.tag("DEEPLINK").d("order=%d held: not signed in yet", pending)
            return
        }
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val current = navController.currentDestination?.id
        if (current == R.id.ordersMapFragment) return
        timber.log.Timber.tag("DEEPLINK").d("order=%d -> orders pool", pending)
        binding.root.post {
            try {
                navController.navigate(
                    R.id.ordersMapFragment,
                    null,
                    androidx.navigation.navOptions { launchSingleTop = true }
                )
            } catch (e: IllegalArgumentException) {
                // Not reachable from where the graph currently is — same non-fatal case the push
                // routing handles. The id stays pending for the next map visit.
                timber.log.Timber.tag("DEEPLINK").w(e, "orders pool not reachable yet")
            }
        }
    }

    /**
     * Tap-to-open routing for FCM pushes (see `driver_fcm_mobile_integration.md` §6).
     * The PendingIntent built by [uz.teamwork.mehrgodriver.common.services.MyFirebaseMessagingService]
     * carries `EXTRA_PUSH_KEY`; we drop the user on the appropriate destination.
     */
    private fun navigateFromPushIfNeeded(intent: Intent?) {
        val key = intent?.getStringExtra(EXTRA_PUSH_KEY) ?: return
        // Clear the extra so a config-change rotation doesn't re-navigate.
        intent.removeExtra(EXTRA_PUSH_KEY)

        val destinationId = when (key) {
            FCM_KEY_DRIVER_BONUS_CREDITED -> R.id.driverEarningsFragment
            FCM_KEY_NEW_DRIVER_NOTIFICATION -> R.id.notificationsMapFragment
            FCM_KEY_DOCUMENT_APPROVED, FCM_KEY_DOCUMENT_REJECTED -> R.id.verificationFragment
            // Withdrawal approved/rejected → So'rovlar tarixi (the nav arg `tab` defaults
            // to "requests", so no args bundle is needed).
            Constants.FCM_TYPE_PAYLOV_WITHDRAWAL -> R.id.paylovHistoryFragment
            else -> {
                timber.log.Timber.tag("FCM").d("push tap: unknown key=%s, no navigation", key)
                return
            }
        }

        timber.log.Timber.tag("FCM").d("push tap key=%s → destination=%s", key, destinationId)

        binding.root.post {
            try {
                findNavController(R.id.nav_host_fragment_activity_main).navigate(
                    destinationId,
                    null,
                    androidx.navigation.navOptions { launchSingleTop = true }
                )
            } catch (e: IllegalArgumentException) {
                // Destination not reachable from the current graph state (e.g. user not
                // logged in yet). Drop the navigation silently — the push notification
                // already surfaced the content via the system tray.
                timber.log.Timber.tag("FCM").w(e, "push tap nav blocked (graph state?)")
            }
        }
    }

    /**
     * No-op: system bars are transparent and content draws behind them (see
     * [setupEdgeToEdge]). The map's red "not on the line" state shows via its gradient
     * wash, which now extends behind the status bar. Kept so MapFragment compiles
     * unchanged.
     */
    fun applyStatusBarAlert(alert: Boolean) {
        // Bars are visible now. On the map's red offline wash the status-bar icons must flip to
        // light for contrast; otherwise fall back to the day/night appearance.
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            if (alert) false else !isNight
    }

    /**
     * Edge-to-edge (Telegram / iOS style): app content draws behind transparent system
     * bars. Each destination's toolbar grows by the status-bar height so its content
     * sits just below the bar while its background extends behind it, and the bottom
     * navigation rides above the gesture bar.
     */
    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Draw app content into the display-cutout / status-bar strip. Without this,
        // hiding the status bar (immersive) letterboxes that top area solid black on
        // notched / punch-hole devices, while the gesture-bar area hides cleanly.
        // Only needed up to Android 14: on 15+ (edge-to-edge enforcement) the DEFAULT
        // cutout mode is already interpreted as ALWAYS for non-floating windows, and
        // SHORT_EDGES is a deprecated API that Google Play flags.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            @Suppress("DEPRECATION")
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
            // Bars stay VISIBLE app-wide (boss requirement): content still draws edge-to-edge behind
            // transparent bars (so the map fills the screen), but the bars themselves are NEVER
            // hidden — no immersive, no transient-swipe.
        }

        // Each destination's toolbar grows by the status-bar height: background draws
        // behind the bar, content sits below it. Adding to the fixed height (instead of
        // wrap_content) keeps ConstraintLayout toolbars from collapsing.
        //
        // The base height is read PER-TOOLBAR from its own declared layout height (below),
        // never from the theme. This used to resolve android.R.attr.actionBarSize, but the
        // app theme is Theme.MaterialComponents.*.NoActionBar, where the layouts' own
        // ?attr/actionBarSize is the ANDROIDX attr (56dp) while the framework attr resolved
        // to 24dp. Every pushed toolbar was therefore grown to statusInset + 24dp, leaving a
        // 24dp content strip: measured on device as clToolbar [0,0][1080,255] with the 40dp
        // logout chip crushed to 105x72px and its icon clipped at the toolbar's bottom edge.
        // Text titles happened to survive in 24dp, which is why only the chip looked broken.
        // Constant status-bar height, applied to a pushed toolbar the instant its view is created
        // (before the first layout) so it never renders one frame at base height and then jumps
        // down when the real inset lands — that was the "jump" on opening Settings / Orders / etc.
        val statusBarFallback = run {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) resources.getDimensionPixelSize(id) else 0
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?
                ) {
                    val toolbar = v.findViewById<View?>(R.id.clToolbar)
                        ?: v.findViewById<View?>(R.id.llToolbar) ?: return
                    val basePaddingTop = toolbar.paddingTop
                    // The toolbar's OWN declared height — whatever the layout asked for. Only a
                    // fixed height is grown; WRAP_CONTENT/MATCH_PARENT (negative) are left alone
                    // so padding can size them naturally instead of us pinning a wrong number.
                    val baseHeight = toolbar.layoutParams?.height ?: 0

                    // Some screens overlay their content over the toolbar via a fixed
                    // marginTop="?attr/actionBarSize" (FrameLayout pattern). Those siblings
                    // must shift down by the same status inset, or they slip under the
                    // now-taller toolbar.
                    val marginSiblings = mutableListOf<Pair<View, Int>>()
                    (toolbar.parent as? android.widget.FrameLayout)?.let { parent ->
                        for (i in 0 until parent.childCount) {
                            val child = parent.getChildAt(i)
                            if (child !== toolbar) {
                                (child.layoutParams as? ViewGroup.MarginLayoutParams)
                                    ?.takeIf { it.topMargin > 0 }
                                    ?.let { marginSiblings.add(child to it.topMargin) }
                            }
                        }
                    }

                    // Grows the toolbar by the status-bar height and shifts overlay siblings
                    // down to match. Pulled into a function so we can apply it both reactively
                    // (rotation / IME) and immediately on attach.
                    fun applyStatusInset(statusTop: Int) {
                        toolbar.updatePadding(top = basePaddingTop + statusTop)
                        if (baseHeight > 0) {
                            toolbar.updateLayoutParams { height = baseHeight + statusTop }
                        }
                        marginSiblings.forEach { (child, baseTop) ->
                            child.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                                topMargin = baseTop + statusTop
                            }
                        }
                    }

                    // Apply the REAL status inset NOW — read from the already-laid-out ACTIVITY
                    // window (not the not-yet-attached fragment view) so the first frame is grown to
                    // the EXACT final height. A dimen fallback can differ from the true inset on
                    // cutout / gesture devices and cause a second fallback→real jump.
                    fun activityStatusInsetTop(): Int {
                        val real = ViewCompat.getRootWindowInsets(window.decorView)
                            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top
                            ?.takeIf { it > 0 }
                        if (real != null) cachedStatusInsetTop = real
                        // Prefer the real inset; after a recreate it's not ready yet, so fall back to
                        // the last-known real inset (cached across the recreate) before the dimen —
                        // the dimen is too small on tall-status-bar devices and briefly clipped the title.
                        return real ?: cachedStatusInsetTop.takeIf { it > 0 } ?: statusBarFallback
                    }
                    applyStatusInset(activityStatusInsetTop())

                    ViewCompat.setOnApplyWindowInsetsListener(toolbar) { _, insets ->
                        applyStatusInset(insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top)
                        insets
                    }

                    // requestApplyInsets alone only schedules a *deferred* pass, which doesn't
                    // reliably reach freshly-created fragment views — that left some pushed
                    // screens with their title drawn under the status bar. Read the insets that
                    // are already on the window the moment the toolbar attaches so the very
                    // first frame is correct, and still request a pass for later changes.
                    toolbar.doOnAttach { attached ->
                        applyStatusInset(activityStatusInsetTop())
                        ViewCompat.requestApplyInsets(attached)
                    }
                }
            },
            true
        )
    }

    /** Bottom navigation rides above the gesture / navigation bar. Split out of [setupEdgeToEdge]
     *  because it needs the inflated content (binding.navView), so it runs AFTER setContentView —
     *  while setupEdgeToEdge (window flags + the pushed-toolbar inset callback) now runs BEFORE it. */
    private fun setupNavBarInset() {
        val navBasePadding = binding.navView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { v, insets ->
            // Ignoring-visibility = constant nav-bar height; the live inset can momentarily flip to 0
            // during a navigation/inset re-dispatch and make the bar (and content above it) jump.
            val nav = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBasePadding + nav.bottom)
            insets
        }
    }

    // Part 1
    @SuppressLint("SetTextI18n")
    /**
     * Countdown observer for the private-order offer dialog.
     *
     * This used to be registered INSIDE [showDialogInsideApp], i.e. once per incoming offer, with
     * an Activity lifecycle owner and no removal — so every private order permanently attached
     * another observer, each capturing that offer's binding and its views. After N offers, N
     * observers fired on every tick and N dead bindings were retained for the life of the Activity.
     * MapFragment guards the same anti-pattern with `trackingObserversRegistered`.
     *
     * Registered at most once now, and it reads `_dialogInsideAppBinding` rather than closing over
     * one binding, so it always drives the dialog that is currently up.
     */
    private var individualOrderTimerObserved = false

    private fun observeIndividualOrderTimer() {
        if (individualOrderTimerObserved) return
        individualOrderTimerObserved = true
        MyTrackingService.timeAcceptingIndividualOrder.observe(this) { time ->
            val b = _dialogInsideAppBinding ?: return@observe
            if (time != null) {
                b.pbTimer.progress = time
                b.tvAccept.text =
                    "${getString(R.string.accept)} ${Helper.addNolIsNeeded(time / 60)} : ${
                        Helper.addNolIsNeeded(time % 60)
                    }"
            } else {
                dialogInsideApp?.dismiss()
            }
        }
    }

    private fun showDialogInsideApp(order: Order) {
        if (dialogInsideApp?.isShowing == true) return
        dialogInsideApp = Dialog(this)
        _dialogInsideAppBinding = DialogFragmentInsideAppBinding.inflate(layoutInflater)
        dialogInsideApp!!.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogInsideAppBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        dialogInsideAppBinding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE

            if (order.isCardPayment == true) {
                ivPaymentType.setImageResource(R.drawable.icon_card)
                tvPaymentType.text = getString(R.string.payment_type_card)
            } else {
                ivPaymentType.setImageResource(R.drawable.icon_cash)
                tvPaymentType.text = getString(R.string.payment_type_cash)
            }

            observeIndividualOrderTimer()

//            tvAddress.text = "${order.address?.name ?: getString(R.string.not_showed)} (${order.addressCategory?.name ?: getString(R.string.not_showed)})"
//            tvAddressFinish.text = "${order.addressFinish?.name ?: getString(R.string.not_showed)} (${order.addressCategoryFinish?.name ?: getString(R.string.not_showed)})"

            if (order.locations[0].name.isNotEmpty()) {
                tvAddress.text = order.locations[0].name
            } else {
                tvAddress.text = getString(R.string.not_showed)
            }

            if (order.locations.size > 1 && order.locations.last().name.isNotEmpty()) {
                tvAddressFinish.text = order.locations.last().name
            } else {
                tvAddressFinish.text = getString(R.string.not_showed)
            }

            if (!order.info.isNullOrEmpty()) {
                tvInfo.text = order.info
                llInfo.visibility = View.VISIBLE
            } else {
                llInfo.visibility = View.GONE
            }

            if (order.locations.size > 1) {
//                val distanceMetre = Helper.calculateBetweenAllPoints(order.locations).toInt()
//                if (distanceMetre < 1000) {
//                    tvDistanceTrack.text = distanceMetre.toString() + getString(R.string.metre)
//                } else {
//                    tvDistanceTrack.text = Helper.metreToRoundKm(distanceMetre.toString()) + getString(R.string.km)
//                }

                tvDistanceTrack.text = order.distance.toString() + getString(R.string.km)

            } else {
                tvDistanceTrack.text = ""
            }

//            if (order.tariff.commission != null) {
//                tvCommission.text = getString(R.string.price_commission) + Helper.formatPrice(order.tariff.commission)
//                tvCommission.visibility = View.VISIBLE
//            } else {
//                tvCommission.visibility = View.GONE
//            }

            // Surge ("addPrice") only adds noise when it's null or 0 — collapse the row
            // so the total price is the unambiguous hero number.
            val surge = order.addPrice
            val roundTotalPrice = Helper.roundPrice(order.price.toLong())
            tvTotalPrice.text = Helper.formatPrice(roundTotalPrice) + getString(R.string.sum)
            if (surge == null || surge == 0) {
                tvAddPrice.visibility = View.GONE
                ivAddPrice.visibility = View.GONE
            } else {
                tvAddPrice.visibility = View.VISIBLE
                ivAddPrice.visibility = View.VISIBLE
                tvAddPrice.text =
                    "+" + Helper.formatPrice(surge.toString()) + getString(R.string.sum)
            }

            if (MyTrackingService.lastLatLngWholeApp.value != null) {
                val clientLatLng = LatLng(order.latitude.toDouble(), order.longitude.toDouble())
                val distanceMetre = Helper.calculateBetweenTwoPoints(
                    MyTrackingService.lastLatLngWholeApp.value!!,
                    clientLatLng
                ).toInt()

                if (distanceMetre < 1000) {
                    tvDistanceClient.text = distanceMetre.toString() + getString(R.string.metre)
                } else {
                    tvDistanceClient.text =
                        Helper.metreToRoundKm(distanceMetre.toString()) + getString(R.string.km)
                }
            } else {
                tvDistanceClient.text = getString(R.string.not_defined)
            }

            val services = order.services ?: emptyList()
            if (services.isNotEmpty()) {
                llServices.visibility = View.VISIBLE

                var textServices = ""
                for (service in services) {
                    textServices += "${service.service.name}, "
                }

                tvServices.text = textServices.substring(0, textServices.length - 2)
            }

            tvAccept.setDebouncedClickListener {
                orderAccept(order.id)
            }

            tvSkip.setDebouncedClickListener {
                orderSkip(order.id)
            }
        }

        dialogInsideApp!!.show()
    }

    private fun orderAccept(id: Int) {
        lifecycleScope.launch {
            mainViewModel.orderAccept(id).collect {
                when (it) {
                    is Resource.Loading -> {
                        dialogInsideAppBinding.apply {
                            progressBar.visibility = View.VISIBLE
                            content.visibility = View.INVISIBLE
                        }
                    }

                    is Resource.Success -> {
                        dialogInsideApp?.dismiss()
                        // Open the trip detail expanded (not the minimised resume card) for the
                        // order just accepted from the in-app offer dialog. MapFragment consumes
                        // this on its refresh.
                        MapFragment.openTripDetailOnNextLoad = true
                        isActiveShowDialogInsideAppForMap.value = true
                    }

                    is Resource.Error -> {
                        // Can't accept from the in-app offer dialog (order taken/expired, or the
                        // driver already has an active order not yet started). Dismiss the offer
                        // dialog first — leaving it open behind the popup lets the driver re-tap
                        // Accept on a doomed order — then show the server reason in a clear popup.
                        dialogInsideApp?.dismiss()
                        InfoPopup.show(
                            context = this@MainActivity,
                            title = getString(R.string.order_not_accepted),
                            message = it.message,
                            lifecycle = lifecycle,
                        )
                    }
                }
            }
        }
    }

    private fun orderSkip(id: Int) {
        lifecycleScope.launch {
            mainViewModel.orderSkip(id).collect {
                when (it) {
                    is Resource.Loading -> {
                        dialogInsideAppBinding.apply {
                            progressBar.visibility = View.VISIBLE
                            content.visibility = View.INVISIBLE
                        }
                    }

                    is Resource.Success -> {
                        dialogInsideApp?.dismiss()
                    }

                    is Resource.Error -> {
                        showToast(it.message!!)

                        dialogInsideAppBinding.apply {
                            progressBar.visibility = View.GONE
                            content.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    // Part 2

    // Open Maps
    private fun openOverallMap(location: Order.Location) {
        val uri = "geo:${location.latitude},${location.longitude}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        startActivity(intent)
    }

    private fun openGoogleMap(location: Order.Location) {
        try {
            val mapUri =
                Uri.parse("https://maps.google.com/maps?saddr=${MyTrackingService.lastLatLngWholeApp.value?.latitude},${MyTrackingService.lastLatLngWholeApp.value?.longitude}&daddr=${location.latitude},${location.longitude}")
            val intent = Intent(Intent.ACTION_VIEW, mapUri)
            startActivity(intent)
        } catch (e: Exception) {
            showToast(e.message.toString())
        }
    }

    private fun openYandexMap(location: Order.Location) {
        try {
            val uri =
                Uri.parse("yandexmaps://maps.yandex.ru/?rtext=${MyTrackingService.lastLatLngWholeApp.value?.latitude},${MyTrackingService.lastLatLngWholeApp.value?.longitude}~${location.latitude},${location.longitude}&rtt=auto")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: Exception) {
            showToast(e.message.toString())
        }
    }

    private fun openYandexNavi(location: Order.Location) {
        try {
            val uri =
                Uri.parse("yandexnavi://build_route_on_map?lat_to=${location.latitude}&lon_to=${location.longitude}&lat_from=${MyTrackingService.lastLatLngWholeApp.value?.latitude}&lon_from=${MyTrackingService.lastLatLngWholeApp.value?.longitude}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            showToast(e.message.toString())
        }
    }

    private fun open2GISMap(location: Order.Location) {
        try {
            val uri =
                Uri.parse("dgis://2gis.ru/routeSearch/rsType/car/from/${MyTrackingService.lastLatLngWholeApp.value?.longitude},${MyTrackingService.lastLatLngWholeApp.value?.latitude}/to/${location.longitude},${location.latitude}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: Exception) {
            showToast(e.message.toString())
        }
    }

    private fun openWazeMap(location: Order.Location) {
        try {
            val uri =
                Uri.parse("https://waze.com/ul?ll=${location.latitude},${location.longitude}&navigate=yes")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: Exception) {
            showToast(e.message.toString())
        }
    }
}