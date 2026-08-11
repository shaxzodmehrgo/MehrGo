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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_RUSSIAN
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_UZBEK
import uz.teamwork.mehrgodriver.common.services.instroction.NavMediaNames.NAV_STRAIGHT
import uz.teamwork.mehrgodriver.common.services.instroction.NavMediaNames.getDistanceAsString
import uz.teamwork.mehrgodriver.common.services.instroction.NavMediaNames.getDistanceName
import uz.teamwork.mehrgodriver.common.services.instroction.NavMediaNames.getNavType
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.databinding.LayoutServiceGoToClientBinding
import uz.teamwork.mehrgodriver.domain.model.DirectionLocations
import javax.inject.Inject

@AndroidEntryPoint
class RouteNavControllerService3 : LifecycleService() {
    private var isRunning = false

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        language = LanguageManager.getLanguage() ?: LANGUAGE_UZBEK
        parentName = when (language) {
            LANGUAGE_RUSSIAN -> "voiceru"
            LANGUAGE_UZBEK -> "voice"
            else -> "voice"
        }

        val reDrawRoute = intent?.getBooleanExtra("reDrawRoute", false) ?: false
        val route = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("route", DirectionLocations.Route::class.java)
        } else {
            intent?.getParcelableExtra("route")
        }

//        if (!isRunning) {
//            isRunning = true
//            steps = route?.legs?.get(0)?.steps ?: emptyList()
//            startPathAudios(steps)
//        }
//
//        if (reDrawRoute) {
//            resetData(true)
//            steps = route?.legs?.get(0)?.steps ?: emptyList()
//            startPathAudios(steps, true)
//        }

        resetData(false)
        steps = route?.legs?.get(0)?.steps ?: emptyList()
        startPathAudios(steps, true)

        return START_NOT_STICKY
    }

    private val locationCallBack = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            result.locations.let { locations ->
                for (location in locations) {
                    Timber.d("ROUTE SERVICE NEW LOCATION: ${location.latitude}, ${location.longitude}")

                    if (location.speed > 5) {
                        updateLocation(location)
                    }
                }
            }
        }
    }

    private fun dipToPixels(context: Context, dipValue: Float): Float {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        fusedLocationProviderClient.removeLocationUpdates(locationCallBack)
        destinationLocation = null
        steps = emptyList()
        resetData(true)
    }

    private fun resetData(onDestroy: Boolean) {
        totalDistance = 0f
        distanceInProgress = 0.0
        routeProgress = 0
        warned = false
        warnedFinish = false
        if (onDestroy) {
            stopService(Intent(this, VoiceService::class.java))
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
                        val list = getDistanceName(200.0, language)
                        list.add("$parentName/navigation/Over.opus")
                        list.add(getNavType(steps[routeProgress + 1].maneuver?.modifier, language))
                        playSoundList(list)
                    }
                }
            }

            if (distanceInProgress > 1000 && !warned) {
                if (remainingDistance > 200 && remainingDistance < 340) {
                    if (routeProgress < steps.size - 1) {
                        warned = true
                        val list = getDistanceName(300.0, language)
                        list.add("$parentName/navigation/Over.opus")
                        list.add(getNavType(steps[routeProgress + 1].maneuver?.modifier, language))
                        playSoundList(list)
                    }
                }
            }

            if (routeProgress <= steps.lastIndex) {
                if (steps[routeProgress].maneuver?.type == NavMediaNames.NAV_DESTINATION && !warnedFinish) {
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

            transDateForUi()

            if (distanceInProgress < 50) {
                sendSoundToPlayer(steps, 10)
            } else if (distanceInProgress >= 50 && distanceInProgress < 150) {
                sendSoundToPlayer(steps, 20)
            } else {
                sendSoundToPlayer(steps, 50)
            }
        }
    }

    private fun sendSoundToPlayer(steps: List<DirectionLocations.Route.Leg.Step>, x: Int) {
        if (totalDistance >= distanceInProgress.toFloat() - x) {
            routeProgress++
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

            if (routeProgress == 0) {
                distance = getDistanceAsString(steps[0].distance.toInt(), language)
            }

            this.modifier = steps[routeProgress].maneuver?.modifier
            this.type = steps[routeProgress].maneuver?.type
            this.streetName = steps[routeProgress].name


            if (routeProgress < steps.size - 1) {
                transDateForUi()
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
                    if (distance <= 1000) {
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

    private fun transDateForUi() {
        binding.apply {

//            imageViewManeur.setImageResource(NavMediaNames.getNavIcon(modifier))
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
//        if (SharedPref.navRouteEnabled && SharedPref.hasVoice && list.isNotEmpty()) shuni quyish kerak
        if (list.isNotEmpty()) {
//            for (i in list.indices) {
//                if (list[i].isEmpty()) {
//                    list[i] = "$parentName/navigation/TurnLeft.opus"
//                }
//            }
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

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}