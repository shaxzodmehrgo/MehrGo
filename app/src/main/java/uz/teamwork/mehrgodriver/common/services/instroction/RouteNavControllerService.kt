package uz.teamwork.mehrgodriver.common.services.instroction

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_RUSSIAN
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_UZBEK
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.location_utils.DetermineLocationInsideRoute
import uz.teamwork.mehrgodriver.common.location_utils.LocationUtils
import uz.teamwork.mehrgodriver.common.services.instroction.NavMediaNames.NAV_STRAIGHT
import uz.teamwork.mehrgodriver.common.services.instroction.NavMediaNames.getDistanceAsString
import uz.teamwork.mehrgodriver.common.services.instroction.NavMediaNames.getDistanceName
import uz.teamwork.mehrgodriver.common.services.instroction.NavMediaNames.getNavType
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.databinding.LayoutServiceGoToClientBinding
import uz.teamwork.mehrgodriver.domain.model.DirectionLocations
import uz.teamwork.mehrgodriver.domain.use_case.route.RouteUC
import javax.inject.Inject

@AndroidEntryPoint
class RouteNavControllerService : LifecycleService() {
    private var isRunning = false

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    @Inject
    lateinit var routeUC: RouteUC

    private lateinit var binding: LayoutServiceGoToClientBinding
    private lateinit var windowManager: WindowManager
    private lateinit var windowLayoutAccepted: ConstraintLayout

    private var oldLocation: Location? = null
    private var totalDistance = 0f

    private var distanceInProgress = 0.0
    private var routeProgress = 0
    private var steps: List<DirectionLocations.Route.Leg.Step> = emptyList()
    private var warned = false
    private var initialWarning = false
    private var warnedFinish = false
    private var distance: String = ""
    private var streetName: String = ""
    private var modifier: String? = ""
    private var type: String? = ""
    private var language = ""
    private var parentName = "voice"

    private var destinationLocation: LatLng? = null

    private val locationCallBack = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            result.locations.let { locations ->
                for (location in locations) {
                    Timber.d("ROUTE SERVICE NEW LOCATION: ${location.latitude}, ${location.longitude}")

                    if (location.speed > 5) {
                        updateLocation(location)

                        if (location != null && destinationLocation != null && routePath.isNotEmpty()) {
                            val isLocationInsideRoute =
                                DetermineLocationInsideRoute.isLocationInsideRoute(
                                    LatLng(
                                        location.latitude,
                                        location.longitude
                                    ), routePath
                                )

                            if (isLocationInsideRoute) {
                                val index = LocationUtils.findNearestLocationIndex(
                                    LatLng(
                                        location.latitude,
                                        location.longitude
                                    ), routePath
                                )?.first

                                if (index != null) {
                                    val remainingLocations = ArrayList<LatLng>()
                                    for (i in routePath.indices) {
                                        if (index <= i) {
                                            remainingLocations.add(
                                                LatLng(
                                                    routePath[i].latitude,
                                                    routePath[i].longitude
                                                )
                                            )
                                        }
                                    }
                                    routePath = remainingLocations
                                }
                            } else {
                                resetData(false)
                                getRoute(
                                    location.latitude,
                                    location.longitude,
                                    destinationLocation!!.latitude,
                                    destinationLocation!!.longitude,
                                    true
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        binding = LayoutServiceGoToClientBinding.inflate(LayoutInflater.from(applicationContext))

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L).apply {
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()

        fusedLocationProviderClient.requestLocationUpdates(
            request,
            locationCallBack,
            Looper.getMainLooper()
        )

        val params: WindowManager.LayoutParams =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        params.gravity = Gravity.TOP or Gravity.START
        params.x = dipToPixels(this, 16f).toInt()
        params.y = dipToPixels(this, 16f).toInt()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(binding.root, params)

        windowLayoutAccepted = binding.root

        val relativeLayout: ConstraintLayout = binding.root
        relativeLayout.setOnTouchListener(
            @SuppressLint("ClickableViewAccessibility")
            object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initTouchX = 0f
                private var initTouchY = 0f
                private var lastAction = 0

                override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {

                    if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                        initialX = params.x
                        initialY = params.y
                        initTouchX = motionEvent.rawX
                        initTouchY = motionEvent.rawY
                        lastAction = motionEvent.action
                        return false
                    }
                    if (motionEvent.action == MotionEvent.ACTION_UP) {
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            lastAction = motionEvent.action
                            return false

                        }
                    }

                    if (motionEvent.action == MotionEvent.ACTION_MOVE) {
                        params.x = initialX + (motionEvent.rawX - initTouchX).toInt()
                        params.y = initialY + (motionEvent.rawY - initTouchY).toInt()
                        windowManager.updateViewLayout(binding.root, params)
                        lastAction = motionEvent.action

                        return false
                    }
                    return false
                }
            })
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        language = LanguageManager.getLanguage() ?: LANGUAGE_UZBEK
        parentName = when (language) {
            LANGUAGE_RUSSIAN -> "voiceru"
            LANGUAGE_UZBEK -> "voice"
            else -> "voice"
        }

        val lat1 = intent?.getDoubleExtra("lat1", 0.0) ?: 0.0
        val lon1 = intent?.getDoubleExtra("lon1", 0.0) ?: 0.0
        val lat2 = intent?.getDoubleExtra("lat2", 0.0) ?: 0.0
        val lon2 = intent?.getDoubleExtra("lon2", 0.0) ?: 0.0

        if (!isRunning) {
            destinationLocation = LatLng(lat2, lon2)
            getRoute(lat1, lon1, lat2, lon2, false)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        fusedLocationProviderClient.removeLocationUpdates(locationCallBack)
        destinationLocation = null
        steps = emptyList()
        resetData(true)
    }

    // Other functions

    private fun dipToPixels(context: Context, dipValue: Float): Float {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics)
    }

    private fun resetData(onDestroy: Boolean) {
        totalDistance = 0f
        distanceInProgress = 0.0
        routeProgress = 0
        warned = false
        warnedFinish = false
        stopService(Intent(this, VoiceService::class.java))
        if (onDestroy) {
            windowManager.removeView(binding.root)
        }
    }

    private fun updateLocation(location: Location) {
        oldLocation?.let { oldLoc ->
            val distance = location.distanceTo(oldLoc)
            totalDistance += distance
        }

        oldLocation = location

        val remainingDistance = distanceInProgress - totalDistance

        if (remainingDistance > 0) {
            distance = getDistanceAsString(remainingDistance.toInt(), language)
        }

        steps.let { steps ->
            if ((distanceInProgress > 500 && distanceInProgress < 1000) && !initialWarning) {
                if (remainingDistance > 150 && remainingDistance < 240) {
                    if (routeProgress < steps.size - 1) {
                        initialWarning = true

                        if (steps[routeProgress + 1].maneuver?.modifier != null) {
                            val list = getDistanceName(200.0, language)
                            list.add("$parentName/navigation/Over.opus")
                            list.add(
                                getNavType(
                                    steps[routeProgress + 1].maneuver?.modifier,
                                    language
                                )
                            )
                            playSoundList(list)
                        }
                    }
                }
            }

            if (distanceInProgress > 1000 && !warned) {
                if (remainingDistance > 200 && remainingDistance < 340) {
                    if (routeProgress < steps.size - 1) {
                        warned = true

                        if (steps[routeProgress + 1].maneuver?.modifier != null) {
                            val list = getDistanceName(300.0, language)
                            list.add("$parentName/navigation/Over.opus")
                            list.add(
                                getNavType(
                                    steps[routeProgress + 1].maneuver?.modifier,
                                    language
                                )
                            )
                            playSoundList(list)
                        }
                    }
                }
            }

            if (routeProgress == steps.lastIndex - 1) {
                if (steps[routeProgress + 1].maneuver?.type == NavMediaNames.NAV_DESTINATION && !warnedFinish) {
                    if (remainingDistance > 99 && remainingDistance < 640) {
                        warnedFinish = true
                        val list = ArrayList<String>()
                        list.add("$parentName/navigation/UntilFinish.mp3")
                        if (getDistanceName(remainingDistance, language).size > 0)
                            list.addAll(getDistanceName(remainingDistance, language))

                        list.add("$parentName/navigation/DistanceLeft.mp3")
                        playSoundList(list)
                    }
                }
            }

            setUpUI()

            if (distanceInProgress < 50) {
                sendSoundToPlayer(steps, 10)
            } else if (distanceInProgress >= 50 && distanceInProgress < 150) {
                sendSoundToPlayer(steps, 15)
            } else {
                sendSoundToPlayer(steps, 30)
            }
        }
    }

    private fun sendSoundToPlayer(steps: List<DirectionLocations.Route.Leg.Step>, x: Int) {
        if (totalDistance >= distanceInProgress.toFloat() - x && totalDistance != 0f) {
            routeProgress++
//            Toast.makeText(this@RouteNavControllerService, "${steps.size}, $routeProgress", Toast.LENGTH_SHORT).show()
            startPathAudios(steps)
            totalDistance = 0f
            warned = false
            initialWarning = false
            warnedFinish = false
        }
    }

    private fun startPathAudios(
        steps: List<DirectionLocations.Route.Leg.Step>,
        reDrawRoute: Boolean = false,
        orderViaApplication: Boolean = false
    ) {
        if (routeProgress < steps.size) {
            val list = getDistanceName(steps[routeProgress].distance, language)

            distanceInProgress = steps[routeProgress].distance

            this.modifier = steps[routeProgress].maneuver?.modifier
            this.type = steps[routeProgress].maneuver?.type
            this.streetName = steps[routeProgress].name

            if (routeProgress == 0) {
                distance = getDistanceAsString(steps[0].distance.toInt(), language)
                setUpUI()
            }

            if (type == NavMediaNames.NAV_DESTINATION) {
                list.add("$parentName/navigation/RouteFinished.opus")
            } else if (modifier == NAV_STRAIGHT) {
                list.add("$parentName/navigation/Forward.opus")
                list.add("$parentName/navigation/Then.opus")
                if (routeProgress < steps.size - 1) {
                    list.add(getNavType(steps[routeProgress + 1].maneuver?.modifier, language))
                }
            } else {
                list.clear()
                list.add(getNavType(modifier, language))
                val distance = steps[routeProgress].distance

                if (routeProgress < steps.size - 1) {
                    if (distance <= 1000 && steps[routeProgress + 1].maneuver?.modifier != null) {
                        list.add("$parentName/navigation/Then.opus")

                        if (getDistanceName(distance, language).size != 0)
                            list.addAll(getDistanceName(distance, language))

                        list.add("$parentName/navigation/Over.opus")
                        list.add(getNavType(steps[routeProgress + 1].maneuver?.modifier, language))
                    }

                    if (distance > 1000) {
                        list.add("$parentName/navigation/Then.opus")
                        if (getDistanceName(distance, language).size != 0)
                            list.addAll(getDistanceName(distance, language))
                        list.add("$parentName/navigation/Forward.opus")
                    }
                }
            }

            playSoundList(
                list,
                reDrawRoute = reDrawRoute,
                orderViaApplication = orderViaApplication
            )
        }
    }

    private fun setUpUI() {
        binding.apply {
            if (routeProgress < steps.size - 2) {
                imageViewManeur.setImageResource(NavMediaNames.getNavIcon(steps[routeProgress + 1].maneuver?.modifier))
            }

            textViewStreetName.visibility = if (streetName.isEmpty()) {
                View.GONE
            } else {
                textViewStreetName.text = streetName
                View.VISIBLE
            }
            textViewDistanceInstruction.text = distance
        }
    }

    private fun playSoundList(
        list: ArrayList<String>,
        reDrawRoute: Boolean = false,
        orderViaApplication: Boolean = false
    ) {
        if (list.isNotEmpty()) {
            if (reDrawRoute) {
                list.add(0, "$parentName/navigation/RouteRecalculated.opus")
                list.add(0, "$parentName/navigation/RouteLost.opus")
            }
            if (orderViaApplication) {
                list.add(0, "$parentName/additional/be_careful_this_order.opus")
            }
            val intent = Intent(this, VoiceService::class.java)
            intent.putStringArrayListExtra("voice", list)
            startService(intent)
        }
    }

    // Get route
    private var routePath = ArrayList<LatLng>()
    private fun getRoute(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        reDrawRoute: Boolean
    ) {
        lifecycleScope.launch {
            routeUC.invoke(lon1, lat1, lon2, lat2).collect {
                when (it) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {

                        // The route server returns NoRoute (empty routes) for unroutable / off-grid
                        // points — guard before indexing routes[0]/legs[0] to avoid IndexOutOfBounds.
                        val firstLeg = it.data?.routes?.firstOrNull()?.legs?.firstOrNull()
                            ?: return@collect

                        val points = ArrayList<LatLng>()
                        firstLeg.steps.forEach { item ->
                            points.addAll(Helper.decode(item.geometry, 5))
                        }

                        val newPoints = ArrayList<LatLng>()
                        newPoints.add(LatLng(lat1, lon1))
                        newPoints.addAll(points)
                        newPoints.add(LatLng(lat2, lon2))

                        routePath.clear()
                        newPoints.forEach { point ->
                            routePath.add(LatLng(point.latitude, point.longitude))
                        }

                        // Start or Resume RouteInstructionService
                        isRunning = true
                        steps = firstLeg.steps
                        startPathAudios(steps, reDrawRoute)
                    }

                    is Resource.Error -> {}
                }
            }
        }
    }
}