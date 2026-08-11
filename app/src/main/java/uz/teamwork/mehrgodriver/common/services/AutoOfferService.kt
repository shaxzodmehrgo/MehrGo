package uz.teamwork.mehrgodriver.common.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.CountDownTimer
import android.telephony.SmsManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.CheckPermissions
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.ACTION_SHOW_DIALOG_ORDER_ACCEPT
import uz.teamwork.mehrgodriver.common.Constants.DEFAULT_ACCEPT_WAIT_TIME
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.RoutePointsBinder
import uz.teamwork.mehrgodriver.common.shared_pref.ThemeManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.LayoutServiceAutoOfferBinding
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderAcceptUC
import uz.teamwork.mehrgodriver.domain.use_case.main.OrderSkipUC
import uz.teamwork.mehrgodriver.domain.use_case.route.GetDistanceUC
import uz.teamwork.mehrgodriver.presentation.activity.main.MainActivity
import uz.teamwork.mehrgodriver.presentation.maps.yandex_map.MapFragment
import javax.inject.Inject

@AndroidEntryPoint
class AutoOfferService : LifecycleService() {
    companion object {
        /** Service-chip entrance: each chip rises this far and fades in, one after another.
         *  Tuned to the house 250-420 ms / DecelerateInterpolator feel used elsewhere. */
        private const val CHIP_ENTER_RISE_PX = 12f
        private const val CHIP_ENTER_DURATION_MS = 260L
        private const val CHIP_ENTER_STAGGER_MS = 60L
    }

    private var _binding: LayoutServiceAutoOfferBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var orderAcceptUC: OrderAcceptUC

    @Inject
    lateinit var orderSkipUC: OrderSkipUC

    @Inject
    lateinit var getDistanceUC: GetDistanceUC

    @Inject
    lateinit var gson: Gson

    private var windowManager: WindowManager? = null

    // Only removeView what was actually added — addView can fail (revoked overlay permission).
    private var viewAdded = false

    private var orderId: Int? = null
    private var clientPhoneNumber: String? = null

    private var paymentTypeCard: String? = null
    private var paymentTypeCash: String? = null
    private var notShowed: String? = null
    private var km: String? = null
    private var priceCommission: String? = null
    private var sum: String? = null
    private var metre: String? = null
    private var notDefined: String? = null
    private var youHaveTwoOrder: String? = null
    private var accept: String? = null
    private var minLabel: String? = null
    private var toPickup: String? = null

    private var mpPrivateOrder: MediaPlayer? = null

    /** Order whose to-pickup distance is still waiting for a driver GPS fix — resolved by the
     *  first fix (observer in [onCreate]) or the one-shot last-known lookup. Without this the
     *  overlay showed a permanent "Aniqlanmagan" whenever it appeared before the first fix. */
    private var pendingDistanceOrder: Order? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate() {
        super.onCreate()

        // Inflate with a context whose night-mode matches the user's chosen app theme
        // (ThemeManager). The bare applicationContext follows the SYSTEM ui-mode, so
        // without this the over-other-apps offer always rendered light even in dark mode.
        _binding = LayoutServiceAutoOfferBinding.inflate(LayoutInflater.from(themedContext()))

        val width: Int = this.resources.displayMetrics.widthPixels * 6 / 7
        // Height wraps the content so the whole offer (incl. price) shows on one page without
        // scrolling, instead of a fixed 3/4-screen window that cut the price off below the fold.
        val height: Int = WindowManager.LayoutParams.WRAP_CONTENT
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Revoked overlay permission → addView throws BadTokenException inside onCreate
        // (same prod-crash class as WindowToAppService). No canDrawOverlays pre-check —
        // some OEMs misreport it false while overlays still work — catch and bow out.
        try {
            windowManager?.addView(binding.root, windowParams(width, height))
            viewAdded = true
        } catch (e: Exception) {
            stopSelf()
            return
        }

        mpPrivateOrder = MediaPlayer.create(this, R.raw.audio_private_order)
        mpPrivateOrder?.start()

        // Live retry for the to-pickup distance: if the overlay appeared BEFORE the tracking
        // service pushed its first GPS fix, the row said "Aniqlanmagan" and never updated —
        // the driver lost a key piece of the decision. The first fix that arrives while the
        // offer is still up resolves it.
        MyTrackingService.lastLatLngWholeApp.observe(this) { latLng ->
            val order = pendingDistanceOrder ?: return@observe
            if (latLng == null || _binding == null) return@observe
            pendingDistanceOrder = null
            getDistance(
                latLng.latitude, latLng.longitude,
                order.latitude.toDouble(), order.longitude.toDouble(), order.id
            )
        }

        // Overlay-specific dynamic labels are localized inline (the service has no
        // simple way to swap the app locale on its own context). NOTE: tvSummaTitle /
        // tvServicesTitle / tvSkip are NOT overridden here — they keep the layout's
        // @string values so the overlay shares wording with the in-app offer sheet.
        // Key off the SAME locale the layout's @string resources resolve with (the app's current /
        // selected language, via the application config) — NOT LanguageManager, which can drift from
        // it (e.g. a system per-app language change) and mix Russian manual labels (Принять / Оплата
        // / сум / До клиента) into an otherwise-Uzbek overlay.
        when (applicationContext.resources.configuration.locales[0].language) {
            Constants.LANGUAGE_KAZAKH -> {
                paymentTypeCard = "Карта арқылы төлем"
                paymentTypeCash = "Қолма-қол төлем"

                notShowed = "Көрсетілмеген"
                km = " км"
                priceCommission = "Комиссия сомасы: "
                sum = " ₸"
                metre = " метр"
                notDefined = "Көрсетілмеген"
                accept = "Қабылдау"
                minLabel = "мин"
                toPickup = "Клиентке дейін"
            }

            Constants.LANGUAGE_KYRGYZ -> {
                paymentTypeCard = "Карта менен төлөө"
                paymentTypeCash = "Накталай төлөө"

                notShowed = "Көрсөтүлгөн эмес"
                km = " км"
                priceCommission = "Комиссия баасы: "
                sum = " сом"
                metre = " метр"
                notDefined = "Аныкталган эмес"
                accept = "Кабыл алуу"
                minLabel = "мүн"
                toPickup = "Кардарга чейин"
            }

            Constants.LANGUAGE_RUSSIAN -> {
                paymentTypeCard = "Оплата картой"
                paymentTypeCash = "Оплата наличными"

                notShowed = "Не указан"
                km = " км"
                priceCommission = "Сумма комиссии: "
                sum = " сум"
                metre = " метр"
                notDefined = "Не указано"
                accept = "Принять"
                minLabel = "мин"
                toPickup = "До клиента"
            }

            // Uzbek default — and the safe fallback when no language is persisted
            // yet (getLanguage() == null) or an unknown value is returned, so a
            // user-facing label is never left null over another app.
            else -> {
                paymentTypeCard = "To'lov kartadan"
                paymentTypeCash = "To'lov naqd pulda"

                notShowed = "Ko'rsatilmagan"
                km = " km"
                priceCommission = "Kommissiya narxi: "
                sum = " so'm"
                metre = " metr"
                notDefined = "Aniqlanmagan"
                accept = "Qabul qilish"
                minLabel = "daq"
                toPickup = "Mijozgacha"
            }
        }

        binding.apply {
            // The icon + label are a centred row now, so the labels are wrap_content — click the
            // full-width containers instead, or the tap target shrinks to the text itself.
            llSkip.setOnClickListener {
                orderSkip()
            }

            llAccept.setOnClickListener {
                orderAccept()
            }
        }

        // Register once for the service lifetime — NOT in onStartCommand, which can
        // re-fire on repeated startService and stack duplicate observers that
        // double-run setData() and restart the countdown.
        MyTrackingService.listenerNewPrivateOrder.observe(this, Observer { order ->
            if (order != null) {
                setData(order)
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    /** applicationContext overridden to the app's chosen day/night mode (ThemeManager). */
    private fun themedContext(): Context {
        val night = when (ThemeManager.getTheme()) {
            Constants.THEME_DAY -> false
            Constants.THEME_NIGHT -> true
            else -> ThemeManager.isSystemNightMode(applicationContext)
        }
        val config = Configuration(applicationContext.resources.configuration)
        val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        val base = applicationContext.createConfigurationContext(config)
        // A bare service/overlay context resolves to the SYSTEM default theme, not the app's
        // Theme.MaterialComponents descendant — any Material widget in the inflated overlay (or an
        // ?attr/ theme lookup) would then throw "requires Theme.MaterialComponents" (ThemeEnforcement).
        // Wrap the night-mode context in the app theme so the overlay is always safe.
        return ContextThemeWrapper(base, R.style.Theme_TeamworkTaxi)
    }

    private fun windowParams(width: Int, height: Int): WindowManager.LayoutParams {
        val params: WindowManager.LayoutParams =
            if (SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            } else {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            }

//        params.gravity = Gravity.TOP or Gravity.LEFT
        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = 0
        params.height = height
        params.width = width

        return params
    }

    override fun onDestroy() {
        super.onDestroy()
        if (viewAdded) {
            try {
                windowManager?.removeView(binding.root)
            } catch (_: Exception) {
            }
        }
        windowManager = null
        countDownTimer?.cancel()
        countDownTimer = null

        orderId = null
        clientPhoneNumber = null

        notShowed = null
        km = null
        priceCommission = null
        sum = null
        metre = null
        notDefined = null
        youHaveTwoOrder = null
        accept = null
        minLabel = null
        toPickup = null

        releaseMpPrivateOrder()

        _binding = null
    }

    private fun releaseMpPrivateOrder() {
        if (mpPrivateOrder != null) {
            try {
                mpPrivateOrder?.release()
                mpPrivateOrder = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setData(order: Order) {
        binding.apply {
            content.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
        }

        binding.apply {
            if (order.isCardPayment == true) {
                ivPaymentType.setImageResource(R.drawable.ph_credit_card)
                tvPaymentType.text = paymentTypeCard
            } else {
                ivPaymentType.setImageResource(R.drawable.ph_money)
                tvPaymentType.text = paymentTypeCash
            }
        }

        setTimerOrderAccept(order.branch.acceptWaiting ?: DEFAULT_ACCEPT_WAIT_TIME)

        orderId = order.id
        clientPhoneNumber = order.contact?.phone

        // Passenger + tariff + badges (iOS OrderOfferSheet parity).
        binding.apply {
            // Passenger card hidden — the client's name is not shown on a new-order offer.
            cardPassenger.visibility = View.GONE

            val tariffName = order.tariff.name
            if (!tariffName.isNullOrBlank()) {
                tvTariff.visibility = View.VISIBLE
                tvTariff.text = tariffName
            } else {
                tvTariff.visibility = View.GONE
            }

            tvBadgeBonus.visibility = if (order.useBonus) View.VISIBLE else View.GONE
            llBadges.visibility =
                if (tvBadgeBonus.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        }

        binding.apply {
//            tvAddress.text = "${order.address?.name ?: getString(R.string.not_showed)} (${order.addressCategory?.name ?: getString(R.string.not_showed)})"
//            tvAddressFinish.text = "${order.addressFinish?.name ?: getString(R.string.not_showed)} (${order.addressCategoryFinish?.name ?: getString(R.string.not_showed)})"

            // Route — pickup, ALL intermediate stops, dropoff. Address only (no venue/district sub).
            val pickup = order.locations.firstOrNull()?.name
            tvAddress.text = if (!pickup.isNullOrEmpty()) pickup else getString(R.string.not_showed)
            tvAddressSub.visibility = View.GONE

            RoutePointsBinder.bindMiddle(
                llMiddlePoints,
                order.locations.drop(1).dropLast(1)
                    .map { (it.name ?: "").ifEmpty { getString(R.string.not_showed) } }
            )

            val dropoff = if (order.locations.size > 1) order.locations.last().name else null
            tvAddressFinish.text =
                if (!dropoff.isNullOrEmpty()) dropoff else getString(R.string.not_showed)
            tvAddressFinishSub.visibility = View.GONE

            if (!order.info.isNullOrEmpty()) {
                tvInfo.text = order.info
                llInfo.visibility = View.VISIBLE
            } else {
                llInfo.visibility = View.GONE
            }

            // Whole-route trip distance removed from order details per request — keep hidden.
            llOfferDistance.visibility = View.GONE

//            if (order.tariff.commission != null) {
//                tvCommission.text = priceCommission + Helper.formatPrice(order.tariff.commission)
//                tvCommission.visibility = View.VISIBLE
//            } else {
//                tvCommission.visibility = View.GONE
//            }

            // Exact price (no rounding) so both Android offer surfaces and iOS
            // show the same headline figure.
            tvTotalPrice.text = Helper.formatPrice(order.price.toString())
            tvPriceSum.text = sum?.trim()
            // No surge OR zero surge — the server ships 0 for plain orders; "0 so'm ↑" is noise.
            if ((order.addPrice ?: 0) <= 0) {
                tvAddPrice.visibility = View.GONE
                ivAddPrice.visibility = View.GONE
            } else {
                tvAddPrice.visibility = View.VISIBLE
                ivAddPrice.visibility = View.VISIBLE
                tvAddPrice.text = "+ " + Helper.formatPrice(order.addPrice.toString()) + sum
            }

            val services = order.services ?: emptyList()
            // Chips, one per service — replaces the old "name, name, " string concat, which
            // clipped on long names and had to be trimmed with a substring. Same idiom as the
            // in-app offer sheet and the profile screen: inflate adapter_chip (a plain TextView,
            // so the ChipGroup just flows and wraps them).
            //
            // Rebuilt from scratch on every bind: setData() is driven by a LiveData observer
            // registered once, so a SECOND order re-uses these very view instances. Fresh chips
            // are what keeps the entrance animation from leaving stale ones stuck at alpha 0.
            chipGroupServices.removeAllViews()
            if (services.isNotEmpty()) {
                llServices.visibility = View.VISIBLE
                val inflater = LayoutInflater.from(chipGroupServices.context)
                services.forEachIndexed { index, service ->
                    val chip = inflater.inflate(R.layout.adapter_chip, null, false) as TextView
                    chip.text = service.service.name
                    chipGroupServices.addView(chip)

                    // Staggered rise + fade, matching the house entrance animation
                    // (IntroduceAdapter): set the start state, then animate with a
                    // DecelerateInterpolator. Done per-view on purpose — animateLayoutChanges
                    // is banned in this codebase, see the note in dialog_bsh_order_state.xml
                    // where a LayoutTransition kept the tree dirty and snapped a sheet back.
                    chip.alpha = 0f
                    chip.translationY = CHIP_ENTER_RISE_PX
                    chip.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(CHIP_ENTER_STAGGER_MS * index)
                        .setDuration(CHIP_ENTER_DURATION_MS)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            } else {
                llServices.visibility = View.GONE
            }

            // Distance
//            if (MyTrackingService.lastLatLngWholeApp.value != null) {
//                val clientLatLng = LatLng(order.latitude.toDouble(), order.longitude.toDouble())
//                val distanceMetre = Helper.calculateBetweenTwoPoints(
//                    MyTrackingService.lastLatLngWholeApp.value!!,
//                    clientLatLng
//                ).toInt()
//
//                if (distanceMetre < 1000) {
//                    tvDistanceClient.text = distanceMetre.toString() + metre
//                } else {
//                    tvDistanceClient.text = Helper.metreToRoundKm(distanceMetre.toString()) + km
//                }
//            } else {
//                tvDistanceClient.text = notDefined
//            }
            if (MyTrackingService.lastLatLngWholeApp.value != null) {
                getDistance(
                    MyTrackingService.lastLatLngWholeApp.value!!.latitude,
                    MyTrackingService.lastLatLngWholeApp.value!!.longitude,
                    order.latitude.toDouble(),
                    order.longitude.toDouble(),
                    order.id
                )
            } else {
                // No driver fix yet (service just started / process restarted): placeholder now,
                // then resolve via the device's last-known fix immediately and the tracking
                // service's first live fix (observer in onCreate) as backup.
                binding.tvDistanceClient.text = "$toPickup: $notDefined"
                pendingDistanceOrder = order
                primeLastKnownLocation(order)
            }
        }
    }

    private fun orderAccept() {
        lifecycleScope.launch {
            orderAcceptUC.invoke(orderId!!.toInt()).collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        stopSelf()

                        val orderAccept = it.data?.data
//                        sendSmsToClient(orderAccept)

                        // Open the trip detail expanded when the map screen next loads this order,
                        // instead of dropping the driver on the minimised home map.
                        MapFragment.openTripDetailOnNextLoad = true

                        val intent = Intent(this@AutoOfferService, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        intent.action = ACTION_SHOW_DIALOG_ORDER_ACCEPT
                        intent.putExtra("order_id", orderId)
                        startActivity(intent)
                    }

                    is Resource.Error -> {
                        // Accept failed (e.g. "order not found" — another driver took it or it
                        // expired): surface the reason, then CLOSE the offer overlay instead of
                        // leaving it stuck on a greyed-out button.
                        it.message?.let { msg -> showToast(msg) }
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun sendSmsToClient(orderAccept: Order?) {
        val carColor = orderAccept?.car?.carColor?.name ?: ""
        val carNumber = orderAccept?.car?.carNumber ?: ""
        val carModel = orderAccept?.car?.carModel?.name ?: ""
        val driverPhoneNumber = orderAccept?.driverNumber ?: ""

        val message =
            "${getString(R.string.app_name_capital)}: Sizga $carColor $carNumber $carModel mashina belgilandi. Haydovchi: $driverPhoneNumber. Ilovamizni yuklab oling: elgataxi.uz/app"

        if (CheckPermissions.checkSendSmsPermission(this)) {
            val smsManager: SmsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(clientPhoneNumber, null, message, null, null)
        }
    }

    private fun orderSkip() {
        lifecycleScope.launch {
            orderSkipUC.invoke(orderId!!.toInt()).collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        stopSelf()
                    }

                    is Resource.Error -> {
                        showToast(it.message!!)
                        errorVisible()
                    }
                }
            }
        }
    }

    /** One-shot last-known device fix for the to-pickup distance when the tracking service
     *  hasn't published one yet. Also primes [MyTrackingService.lastLatLngWholeApp] so every
     *  other surface (order lists, offer sheet) stops reading "Aniqlanmagan" too. */
    @SuppressLint("MissingPermission")
    private fun primeLastKnownLocation(order: Order) {
        if (!CheckPermissions.checkLocationPermission(this)) return
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { location ->
                if (location == null || _binding == null || orderId != order.id) {
                    return@addOnSuccessListener
                }
                // Clear the pending marker BEFORE publishing the fix — publishing triggers the
                // onCreate observer, which would otherwise fire a duplicate getDistance.
                pendingDistanceOrder = null
                if (MyTrackingService.lastLatLngWholeApp.value == null) {
                    MyTrackingService.lastLatLngWholeApp.value =
                        LatLng(location.latitude, location.longitude)
                }
                getDistance(
                    location.latitude, location.longitude,
                    order.latitude.toDouble(), order.longitude.toDouble(), order.id
                )
            }
    }

    @SuppressLint("SetTextI18n")
    private fun getDistance(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double, forOrderId: Int?
    ) {
        lifecycleScope.launch {
            getDistanceUC.invoke(lon1, lat1, lon2, lat2).collect {
                when (it) {
                    is Resource.Loading -> {}

                    is Resource.Success -> {
                        // Ignore a result that resolved after a newer order replaced the overlay.
                        if (_binding != null && forOrderId == orderId) {
                            val route = it.data?.routes?.firstOrNull()
                            if (route == null) {
                                binding.tvDistanceClient.text = "$toPickup: $notDefined"
                            } else {
                                val distanceMetre = route.distance
                                val distText = if (distanceMetre < 1000) {
                                    distanceMetre.toString() + metre
                                } else {
                                    Helper.metreToRoundKm(distanceMetre.toString()) + km
                                }
                                // ETA to client at a 30 km/h city average (= 500 m/min),
                                // computed from the shown distance so the two always agree
                                // (iOS OrderOfferSheet parity).
                                val minutes = Math.round(distanceMetre / 500.0).toInt()
                                val timeText =
                                    if (minutes < 1) "<1 $minLabel" else "~$minutes $minLabel"
                                binding.tvDistanceClient.text = "$toPickup: $distText · $timeText"
                            }
                        }
                    }

                    is Resource.Error -> {
                        if (_binding != null) {
                            showToast(it.message!!)
                        }
                    }
                }
            }
        }
    }

    fun loadingVisible() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            content.visibility = View.INVISIBLE
        }
    }

    fun screenVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
    }

    fun errorVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
    }

    private var countDownTimer: CountDownTimer? = null
    private fun setTimerOrderAccept(acceptWaitTime: Int) {
        countDownTimer?.cancel()
        var currentTime = acceptWaitTime
        binding.pbTimer.max = currentTime
        // Seed the first frame so the button shows the localized label + full clock
        // immediately (the first onTick is ~1s away).
        binding.pbTimer.progress = currentTime
        binding.tvAccept.text =
            "$accept ${Helper.addNolIsNeeded(currentTime / 60)} : ${
                Helper.addNolIsNeeded(
                    currentTime % 60
                )
            }"
        countDownTimer = object : CountDownTimer((acceptWaitTime * 1000).toLong(), 1000) {
            @SuppressLint("SetTextI18n")
            override fun onTick(p0: Long) {
                currentTime--
                if (currentTime >= 0) {
                    if (_binding != null) {
                        binding.pbTimer.progress = currentTime

                        val minute = currentTime / 60
                        val second = currentTime % 60

                        binding.tvAccept.text = "$accept ${
                            Helper.addNolIsNeeded(
                                minute
                            )
                        } : ${Helper.addNolIsNeeded(second)}"
                    }
                }
            }

            override fun onFinish() {
                stopSelf()
            }

        }.start()
    }
}