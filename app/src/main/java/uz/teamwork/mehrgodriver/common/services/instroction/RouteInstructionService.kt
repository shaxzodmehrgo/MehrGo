package uz.teamwork.mehrgodriver.common.services.instroction

//@AndroidEntryPoint
//class RouteInstructionService : LifecycleService() {
//    private var _binding: LayoutServiceRouteInstructionBinding? = null
//    private val binding get() = _binding!!
//    private lateinit var windowManager: WindowManager
//
//    @Inject
//    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
//
//    private var steps: List<DirectionLocations.Route.Leg.Step> = emptyList()
//    private var routeProgress = 0
//    private var distanceInProgress = 0.0
//    private var oldLocation: Location? = null
//    private var totalDistance = 0.0
//
//    private val language = LanguageManager.getLanguage() ?: Constants.LANGUAGE_UZBEK
//    private var parentName = "voice"
//
//    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
//    override fun onCreate() {
//        super.onCreate()
//        _binding =
//            LayoutServiceRouteInstructionBinding.inflate(LayoutInflater.from(applicationContext))
//
//        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L).apply {
////            setMinUpdateDistanceMeters(50f)
//            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
//            setWaitForAccurateLocation(true)
//        }.build()
//
//        fusedLocationProviderClient.requestLocationUpdates(
//            request,
//            locationCallBack,
//            Looper.getMainLooper()
//        )
//
//        val params: WindowManager.LayoutParams =
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                WindowManager.LayoutParams(
//                    WindowManager.LayoutParams.WRAP_CONTENT,
//                    WindowManager.LayoutParams.WRAP_CONTENT,
//                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
//                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//                    PixelFormat.TRANSLUCENT
//                )
//            } else {
//                WindowManager.LayoutParams(
//                    WindowManager.LayoutParams.WRAP_CONTENT,
//                    WindowManager.LayoutParams.WRAP_CONTENT,
//                    WindowManager.LayoutParams.TYPE_PHONE,
//                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//                    PixelFormat.TRANSLUCENT
//                )
//            }
//
//        params.gravity = Gravity.TOP or Gravity.START
//        params.x = dipToPixels(this, 16f).toInt()
//        params.y = dipToPixels(this, 16f).toInt()
//
//        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
//        windowManager.addView(binding.root, params)
//
//        val relativeLayout: ConstraintLayout = binding.root
//        relativeLayout.setOnTouchListener(@SuppressLint("ClickableViewAccessibility")
//        object : View.OnTouchListener {
//            private var initialX = 0
//            private var initialY = 0
//            private var initTouchX = 0f
//            private var initTouchY = 0f
//            private var lastAction = 0
//
//            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
//                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
//                    initialX = params.x
//                    initialY = params.y
//                    initTouchX = motionEvent.rawX
//                    initTouchY = motionEvent.rawY
//                    lastAction = motionEvent.action
//                    return false
//                }
//                if (motionEvent.action == MotionEvent.ACTION_UP) {
//                    if (lastAction == MotionEvent.ACTION_DOWN) {
//                        lastAction = motionEvent.action
//                        return false
//                    }
//                }
//
//                if (motionEvent.action == MotionEvent.ACTION_MOVE) {
//                    params.x = initialX + (motionEvent.rawX - initTouchX).toInt()
//                    params.y = initialY + (motionEvent.rawY - initTouchY).toInt()
//                    windowManager.updateViewLayout(binding.root, params)
//                    lastAction = motionEvent.action
//
//                    return false
//                }
//                return false
//            }
//        })
//    }
//
//    @SuppressLint("SetTextI18n")
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        binding.root.setOnClickListener {
//            val intent1 = Intent(this@RouteInstructionService, MainActivity::class.java)
//            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
//            startActivity(intent1)
//        }
//
//        intent?.let {
//            when (it.action) {
//                Constants.ACTION_START_ROUTE_INSTRUCTION_SERVICE -> {
//                    val reDrawRoute = it.getBooleanExtra("reDrawRoute", false)
//                    val route: DirectionLocations.Route? = it.extras?.getParcelable("myObject")
//
//                    steps = route?.legs?.get(0)?.steps ?: emptyList()
//                    distanceInProgress = steps[routeProgress].distance
//
//                    setUp(distanceInProgress)
//                }
//
//                Constants.ACTION_STOP_ROUTE_INSTRUCTION_SERVICE -> {
//                    stopSelf()
//                }
//            }
//        }
//
//        return super.onStartCommand(intent, flags, startId)
//    }
//
//    @SuppressLint("SetTextI18n")
//    private fun setUp(remainingDistance: Double) {
//        if (_binding != null) {
//            binding.apply {
//                // Set data to UI
//                tvStreetName.visibility = View.VISIBLE
//                tvStreetName.text = steps.get(routeProgress).name
//                if (remainingDistance < 1000) {
//                    tvDistance.text = "${remainingDistance.toInt()} m"
//                } else {
//                    tvDistance.text = "${Helper.metreToRoundKm(remainingDistance.toString())} km"
//                }
//
////                ivImage.setImageResource(NavMediaNames.getNavIcon(steps?.get(routeProgress + 1)?.maneuver?.modifier ?: ""))
//                if (routeProgress < steps.size - 2) {
//                    ivImage.setImageResource(NavMediaNames.getNavIcon(steps.get(routeProgress + 1).maneuver?.modifier))
//                }
//
//                // Start VoiceService
////                if (remainingDistance > 150 && remainingDistance < 240) {
////                    val list = NavMediaNames.getDistanceName(200.0, language)
////                    list.add("$parentName/navigation/Over.opus")
////                    list.add(NavMediaNames.getNavType(steps?.get(routeProgress + 1)?.maneuver?.modifier ?: "", language))
////                    playSoundList(list)
////                }
////
////                if (remainingDistance > 200 && remainingDistance < 340) {
////                    val list = NavMediaNames.getDistanceName(300.0, language)
////                    list.add("$parentName/navigation/Over.opus")
////                    list.add(NavMediaNames.getNavType(steps?.get(routeProgress + 1)?.maneuver?.modifier ?: "", language))
////                    playSoundList(list)
////                }
//
//                if ((distanceInProgress > 500 && distanceInProgress < 1000) && !initialWarning) {
//                    if (remainingDistance > 150 && remainingDistance < 240) {
//                        if (routeProgress < steps.size - 1) {
//                            initialWarning = true
//                            val list = NavMediaNames.getDistanceName(200.0, language)
//                            list.add("$parentName/navigation/Over.opus")
//                            list.add(NavMediaNames.getNavType(steps[routeProgress + 1].maneuver?.modifier, language))
//                            playSoundList(list)
//                        }
//                    }
//                }
//
//                if (distanceInProgress > 1000 && !warned) {
//                    if (remainingDistance > 200 && remainingDistance < 340) {
//                        if (routeProgress < steps.size - 1) {
//                            warned = true
//                            val list = NavMediaNames.getDistanceName(300.0, language)
//                            list.add("$parentName/navigation/Over.opus")
//                            list.add(NavMediaNames.getNavType(steps[routeProgress + 1].maneuver?.modifier, language))
//                            playSoundList(list)
//                        }
//                    }
//                }
//
//                if (routeProgress <= steps.lastIndex) {
//                    if (steps[routeProgress].maneuver?.type == NAV_DESTINATION && !warnedFinish) {
//                        if (remainingDistance > 99 && remainingDistance < 640) {
//                            warnedFinish = true
//                            val list = ArrayList<String>()
//                            list.add("$parentName/navigation/UntilFinish.mp3")
//                            if (NavMediaNames.getDistanceName(remainingDistance, language).size > 0)
//                                list.addAll(NavMediaNames.getDistanceName(remainingDistance, language))
//
//                            list.add("$parentName/navigation/DistanceLeft.mp3")
//                            playSoundList(list)
//                        }
//                    }
//                }
//
//                if (distanceInProgress < 50) {
//                    sendSoundToPlayer(steps, 10)
//                } else if (distanceInProgress >= 50 && distanceInProgress < 150) {
//                    sendSoundToPlayer(steps, 20)
//                } else {
//                    sendSoundToPlayer(steps, 50)
//                }
//            }
//        }
//    }
//
//    private fun playSoundList(list: ArrayList<String>) {
//        val intent = Intent(this, VoiceService::class.java)
//        intent.putStringArrayListExtra("voice", list)
//        startService(intent)
//    }
//
//    private fun dipToPixels(context: Context, dipValue: Float): Float {
//        val metrics: DisplayMetrics = context.resources.displayMetrics
//        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics)
//    }
//
//    private val locationCallBack = object : LocationCallback() {
//        override fun onLocationResult(result: LocationResult) {
//            super.onLocationResult(result)
//            result.locations.let { locations ->
//                for (location in locations) {
//                    if (location != null && oldLocation != null) {
//                        val distance = location.distanceTo(oldLocation!!)
//                        totalDistance += distance
//                    }
//
//                    if (routeProgress < steps.size) {
//                        val remainingDistance = distanceInProgress - totalDistance
//                        if (remainingDistance <= 0) {
//                            routeProgress++
//                            totalDistance = 0.0
//                            distanceInProgress = steps[routeProgress].distance
//                        } else {
//                            setUp(remainingDistance)
//                        }
//                    }
//
//                    oldLocation = location
//                }
//            }
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        windowManager.removeView(binding.root)
//        fusedLocationProviderClient.removeLocationUpdates(locationCallBack)
//        _binding = null
//    }
//}