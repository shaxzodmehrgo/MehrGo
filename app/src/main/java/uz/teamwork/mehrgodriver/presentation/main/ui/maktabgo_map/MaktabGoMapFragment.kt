package uz.teamwork.mehrgodriver.presentation.main.ui.maktabgo_map

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ncorti.slidetoact.SlideToActView
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.PolylineMapObject
import com.yandex.runtime.image.ImageProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import timber.log.Timber
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.MaktabGoSession
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.databinding.FragmentMaktabgoMapBinding
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRouteFull
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaStop

/**
 * Экран рейса MaktabGo на Yandex MapKit (отдельный от таксишного MapFragment — тот 7k строк и
 * завязан на заказы; этот лёгкий и изолированный). Рисует точки посадки по pickup_seq + школу-финиш
 * и линию маршрута (polyline с бэкенда, фолбэк — соединение точек). Кнопки: Начать/Забрал/Завершить,
 * «Открыть в Яндекс.Навигаторе». GPS ведём тут (FusedLocation) и шлём в driver/routes/{id}/track —
 * MyTrackingService НЕ редактируем (он запущен для presence/сокета через «Выйти на линию»).
 */
@AndroidEntryPoint
class MaktabGoMapFragment : Fragment() {

    private var _binding: FragmentMaktabgoMapBinding? = null
    private val binding get() = _binding!!
    private val vm: MaktabGoMapVM by viewModels()

    private var routeId: Int = 0
    private var route: BirgaRouteFull? = null
    private var running = false
    private var startedLocally = false
    private var idleMode = false
    private var centeredOnMe = false

    private lateinit var fused: FusedLocationProviderClient
    private var curLat: Double? = null
    private var curLon: Double? = null
    private var meMark: PlacemarkMapObject? = null
    private var routeLine: PolylineMapObject? = null
    private var smoothedAzimuth = 0f
    private var homeFollowDriver = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMaktabgoMapBinding.inflate(inflater, container, false)
        routeId = arguments?.getInt(ARG_ROUTE_ID) ?: 0
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fused = LocationServices.getFusedLocationProviderClient(requireContext())

        binding.btnStart.setOnClickListener { startRoute() }
        binding.btnPickup.setOnClickListener { pickupNext() }
        binding.btnComplete.setOnClickListener { completeRoute() }
        binding.btnNavigator.setOnClickListener { openNavigator() }
        binding.btnMenu.setOnClickListener { openRoutesList() }
        binding.btnZoomIn.setOnClickListener { zoomBy(1f) }
        binding.btnZoomOut.setOnClickListener { zoomBy(-1f) }
        binding.btnMyLoc.setOnClickListener { homeFollowDriver = true; val la = curLat; val lo = curLon; if (la != null && lo != null) followDriver(la, lo) }
        binding.btnOnline.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(v: SlideToActView) {
                if (!MaktabGoSession.online && !ensureLocationReady()) { v.resetSlider(); return }
                toggleOnline()
                v.resetSlider()
            }
        }
        binding.btnNotif.setOnClickListener { showToast(getString(R.string.maktabgo_soon)) }
        binding.ivAvatar.setOnClickListener { openSettings() }
        maybeShowPermissionGate()
        binding.mapView.map.addCameraListener(object : CameraListener {
            override fun onCameraPositionChanged(map: com.yandex.mapkit.map.Map, cameraPosition: CameraPosition, cameraUpdateReason: CameraUpdateReason, finished: Boolean) {
                if (cameraUpdateReason == CameraUpdateReason.GESTURES) homeFollowDriver = false
            }
        })

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (MaktabGoSession.online || findNavController().previousBackStackEntry == null) {
                    requireActivity().moveTaskToBack(true)
                } else {
                    findNavController().popBackStack()
                }
            }
        })

        loadHomeOrRoute()
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        binding.mapView.onStart()
        startLocation()
    }

    override fun onStop() {
        stopLocation()
        binding.mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    // ---- data ----

    private fun openRoutesList() {
        findNavController().navigate(R.id.action_maktabGoMapFragment_to_maktabGoRoutesFragment)
    }

    private fun loadHomeOrRoute() {
        if (routeId > 0) { loadRoute(); return }
        if (!MaktabGoSession.online) { showOffline(); return }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.routes().collect { res ->
                val b = _binding ?: return@collect
                when (res) {
                    is Resource.Loading -> b.pbMap.visibility = View.VISIBLE
                    is Resource.Success -> {
                        b.pbMap.visibility = View.GONE
                        val active = res.data?.mine?.firstOrNull { it.status == "running" || it.status == "active" }
                        if (active != null) { routeId = active.id; loadRoute() } else showOnlineIdle()
                    }
                    is Resource.Error -> { b.pbMap.visibility = View.GONE; showOnlineIdle() }
                }
            }
        }
    }

    private fun toggleOnline() {
        val target = !MaktabGoSession.online
        viewLifecycleOwner.lifecycleScope.launch {
            vm.setOnline(target).collect { res ->
                val b = _binding ?: return@collect
                when (res) {
                    is Resource.Loading -> b.pbMap.visibility = View.VISIBLE
                    is Resource.Success -> {
                        b.pbMap.visibility = View.GONE
                        MaktabGoSession.online = target
                        if (target) sendTrackingAction(Constants.ACTION_START_MAKTABGO_TRACKING)
                        else sendTrackingAction(Constants.ACTION_STOP_MAKTABGO_TRACKING)
                        showToast(getString(if (target) R.string.maktabgo_online_on else R.string.maktabgo_online_off))
                        routeId = 0
                        loadHomeOrRoute()
                    }
                    is Resource.Error -> { b.pbMap.visibility = View.GONE; showToast(res.message ?: getString(R.string.error)) }
                }
            }
        }
    }

    private fun showOffline() {
        val b = _binding ?: return
        idleMode = true
        b.bottomPanel.visibility = View.GONE
        b.btnOnline.visibility = View.VISIBLE
        b.btnOnline.text = getString(R.string.maktabgo_go_online)
        b.tvStatus.text = getString(R.string.maktabgo_status_offline)
        centerOnMe()
    }

    private fun showOnlineIdle() {
        val b = _binding ?: return
        idleMode = true
        b.bottomPanel.visibility = View.GONE
        b.btnOnline.visibility = View.VISIBLE
        b.btnOnline.text = getString(R.string.maktabgo_go_offline)
        b.tvStatus.text = getString(R.string.maktabgo_status_online)
        centerOnMe()
    }

    private fun centerOnMe() {
        val b = _binding ?: return
        val la = curLat; val lo = curLon
        if (la != null && lo != null) b.mapView.map.move(CameraPosition(Point(la, lo), 15.0f, 0.0f, 0.0f))
    }

    private fun loadRoute() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.route(routeId).collect { res ->
                val b = _binding ?: return@collect
                when (res) {
                    is Resource.Loading -> b.pbMap.visibility = View.VISIBLE
                    is Resource.Success -> {
                        b.pbMap.visibility = View.GONE
                        res.data?.route?.let { render(it) }
                    }
                    is Resource.Error -> {
                        b.pbMap.visibility = View.GONE
                        showToast(res.message ?: getString(R.string.error))
                    }
                }
            }
        }
    }

    private fun render(r: BirgaRouteFull) {
        route = r
        running = r.status == "running"
        if (running) sendTrackingAction(Constants.ACTION_START_MAKTABGO_TRACKING)
        val b = _binding ?: return
        idleMode = false
        b.btnOnline.visibility = View.GONE
        b.bottomPanel.visibility = View.VISIBLE
        b.tvStatus.text = getString(
            R.string.maktabgo_map_title,
            r.school?.name ?: getString(R.string.maktabgo_route_no_school),
            r.children, r.capacity
        )

        drawMap(r)
        renderStops(r)

        // Возобновление: если точки уже отмечены — рейс в процессе.
        if (r.stops.any { isStopDone(it) }) startedLocally = true
        // Последовательность: «Забрал»/«Завершить» доступны только ПОСЛЕ «Начать рейс».
        val canStart = !startedLocally && (r.status == "active" || r.status == "running")
        b.btnStart.isEnabled = canStart
        val allDone = r.stops.isNotEmpty() && r.stops.all { isStopDone(it) }
        b.btnPickup.isEnabled = startedLocally && running && !allDone
        b.btnComplete.isEnabled = startedLocally && running
        b.btnStart.alpha = if (canStart) 1f else 0.4f
        b.btnPickup.alpha = if (b.btnPickup.isEnabled) 1f else 0.4f
        b.btnComplete.alpha = if (b.btnComplete.isEnabled) 1f else 0.4f
    }

    private fun drawMap(r: BirgaRouteFull) {
        val map = binding.mapView.map
        map.mapObjects.clear()
        meMark = null

        val pts = ArrayList<Point>()

        for (stop in r.stops) {
            val p = Point(stop.lat, stop.lon)
            pts.add(p)
            map.mapObjects.addPlacemark(p, ImageProvider.fromBitmap(dot(stop.seq.toString(), stopColor(stop))))
        }
        r.school?.let { s ->
            val p = Point(s.lat, s.lon)
            pts.add(p)
            map.mapObjects.addPlacemark(p, ImageProvider.fromBitmap(dot(getString(R.string.maktabgo_map_school_short), Color.parseColor("#C62828"))))
        }

        // Маршрут строим ОТ ВОДИТЕЛЯ (если рейс идёт и позиция известна) через оставшиеся точки к школе.
        val la = curLat; val lo = curLon
        val routePts = ArrayList<Point>()
        if (running && la != null && lo != null) {
            routePts.add(Point(la, lo))
            for (stop in r.stops) if (!isStopDone(stop)) routePts.add(Point(stop.lat, stop.lon))
            r.school?.let { routePts.add(Point(it.lat, it.lon)) }
        } else {
            routePts.addAll(pts)
        }

        // Мгновенная линия (до ответа OSRM): при активном рейсе — от водителя; иначе backend/соединение.
        val line: List<Point> = if (running && la != null && lo != null) routePts else decodeOrConnect(r, pts)
        if (line.size >= 2) {
            routeLine = map.mapObjects.addPolyline(Polyline(line)).apply {
                setStrokeColor(Color.parseColor("#1565C0")); strokeWidth = 5f
            }
        }
        fetchAndDrawRoad(routePts)

        if (pts.isNotEmpty()) {
            val cLat = pts.sumOf { it.latitude } / pts.size
            val cLon = pts.sumOf { it.longitude } / pts.size
            map.move(CameraPosition(Point(cLat, cLon), 12.0f, 0.0f, 0.0f))
        }
    }

    private fun fetchAndDrawRoad(pts: List<Point>) {
        if (pts.size < 2) return
        viewLifecycleOwner.lifecycleScope.launch {
            val enc = withContext(Dispatchers.IO) { osrmPolyline(pts) } ?: return@launch
            val road = try { decodePolyline(enc) } catch (e: Exception) { return@launch }
            val b = _binding ?: return@launch
            if (road.size < 2) return@launch
            try {
                routeLine?.let { b.mapView.map.mapObjects.remove(it) }
                routeLine = b.mapView.map.mapObjects.addPolyline(Polyline(road)).apply {
                    setStrokeColor(Color.parseColor("#1565C0")); strokeWidth = 6f
                }
            } catch (e: Exception) { Timber.tag("MaktabGo").d("draw road: %s", e.message) }
        }
    }

    private fun osrmPolyline(pts: List<Point>): String? {
        return try {
            val coords = pts.joinToString(";") { "${it.longitude},${it.latitude}" }
            val url = URL("https://route.teamwork.uz/route/v1/driving/$coords?overview=full&geometries=polyline")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000; conn.readTimeout = 6000; conn.requestMethod = "GET"
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val txt = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val routes = JSONObject(txt).optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null
            val g = routes.getJSONObject(0).optString("geometry", "")
            if (g.isBlank()) null else g
        } catch (e: Exception) { Timber.tag("MaktabGo").d("osrm: %s", e.message); null }
    }

    private fun zoomBy(d: Float) {
        val b = _binding ?: return
        homeFollowDriver = false
        val cp = b.mapView.map.cameraPosition
        b.mapView.map.move(CameraPosition(cp.target, cp.zoom + d, cp.azimuth, cp.tilt), Animation(Animation.Type.SMOOTH, 0.2f), null)
    }

    private fun decodeOrConnect(r: BirgaRouteFull, orderedPts: List<Point>): List<Point> {
        val enc = r.polyline
        if (!enc.isNullOrBlank()) {
            try {
                val decoded = decodePolyline(enc)
                if (decoded.size >= 2) return decoded
            } catch (e: Exception) {
                Timber.tag("MaktabGo").d("polyline decode failed: %s", e.message)
            }
        }
        return orderedPts
    }

    private fun renderStops(r: BirgaRouteFull) {
        val ll = binding.llStops
        ll.removeAllViews()
        addRouteDetails(ll, r)
        for (stop in r.stops) {
            val tv = TextView(requireContext())
            val done = isStopDone(stop)
            val mark = if (done) "✔" else stop.seq.toString()
            tv.text = getString(R.string.maktabgo_map_stop_row, mark, stop.childrenTotal)
            tv.textSize = 14f
            tv.setPadding(0, 8, 0, 8)
            tv.setTextColor(if (done) Color.parseColor("#2E7D32") else Color.parseColor("#212121"))
            ll.addView(tv)
        }
    }

    // ---- actions ----

    private fun startRoute() {
        if (!ensureLocationReady()) return
        viewLifecycleOwner.lifecycleScope.launch {
            vm.start(routeId).collect { res ->
                val b = _binding ?: return@collect
                when (res) {
                    is Resource.Loading -> b.pbMap.visibility = View.VISIBLE
                    is Resource.Success -> {
                        b.pbMap.visibility = View.GONE
                        startedLocally = true
                        showToast(getString(R.string.maktabgo_route_started))
                        sendTrackingAction(Constants.ACTION_START_MAKTABGO_TRACKING)
                        res.data?.route?.let { render(it) }
                    }
                    is Resource.Error -> {
                        b.pbMap.visibility = View.GONE
                        showToast(res.message ?: getString(R.string.error))
                    }
                }
            }
        }
    }

    private fun pickupNext() {
        if (!ensureLocationReady()) return
        val target = nextStop() ?: run { showToast(getString(R.string.maktabgo_all_picked)); return }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.pickup(routeId, target.seq).collect { res ->
                val b = _binding ?: return@collect
                when (res) {
                    is Resource.Loading -> b.pbMap.visibility = View.VISIBLE
                    is Resource.Success -> {
                        b.pbMap.visibility = View.GONE
                        showToast(getString(R.string.maktabgo_pickup_done))
                        loadRoute()
                    }
                    is Resource.Error -> {
                        b.pbMap.visibility = View.GONE
                        showToast(res.message ?: getString(R.string.error))
                    }
                }
            }
        }
    }

    private fun completeRoute() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.complete(routeId).collect { res ->
                val b = _binding ?: return@collect
                when (res) {
                    is Resource.Loading -> b.pbMap.visibility = View.VISIBLE
                    is Resource.Success -> {
                        b.pbMap.visibility = View.GONE
                        showToast(getString(R.string.maktabgo_route_completed))
                        sendTrackingAction(Constants.ACTION_STOP_MAKTABGO_TRACKING)
                        findNavController().popBackStack()
                    }
                    is Resource.Error -> {
                        b.pbMap.visibility = View.GONE
                        showToast(res.message ?: getString(R.string.error))
                    }
                }
            }
        }
    }

    private fun openNavigator() {
        val r = route ?: return
        // Полный маршрут: from = текущая точка, via = оставшиеся точки посадки по порядку, to = школа.
        val remaining = r.stops.filter { !isStopDone(it) }
        val viaStops = if (remaining.isNotEmpty()) remaining else r.stops
        val destLat: Double
        val destLon: Double
        val vias: List<BirgaStop>
        val school = r.school
        if (school != null) {
            destLat = school.lat; destLon = school.lon; vias = viaStops
        } else {
            val last = viaStops.lastOrNull() ?: return
            destLat = last.lat; destLon = last.lon; vias = viaStops.dropLast(1)
        }
        val viaPart = vias.mapIndexed { i, v -> "&lat_via_$i=${v.lat}&lon_via_$i=${v.lon}" }.joinToString("")
        try {
            val uri = Uri.parse(
                "yandexnavi://build_route_on_map?lat_to=$destLat&lon_to=$destLon$viaPart" +
                    "&lat_from=${curLat ?: ""}&lon_from=${curLon ?: ""}"
            )
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            showToast(e.message ?: getString(R.string.error))
        }
    }

    // ---- location + track upload ----

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            curLat = loc.latitude
            curLon = loc.longitude
            updateMe(loc.latitude, loc.longitude)
            val kmh = if (loc.hasSpeed()) (loc.speed * 3.6f).toInt() else 0
            _binding?.tvSpeed?.text = "$kmh\nkm/h"
            followDriver(loc.latitude, loc.longitude, if (loc.hasBearing() && loc.hasSpeed() && loc.speed > 1.5f) loc.bearing else null)
        }
    }

    private fun followDriver(lat: Double, lon: Double, bearing: Float? = null) {
        val b = _binding ?: return
        if (!homeFollowDriver) return
        val az = if (bearing != null) smoothAzimuth(bearing) else smoothedAzimuth
        try {
            b.mapView.map.move(
                CameraPosition(Point(lat, lon), 16.5f, az, 0.0f),
                Animation(Animation.Type.SMOOTH, 0.7f), null
            )
        } catch (e: Exception) { Timber.tag("MaktabGo").d("follow: %s", e.message) }
    }

    /** Дедбенд + сглаживание GPS-азимута (как в такси): игнор дрожания <8°, плавный поворот. */
    private fun smoothAzimuth(bearing: Float): Float {
        var delta = bearing - smoothedAzimuth
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        if (kotlin.math.abs(delta) < 8f) return smoothedAzimuth
        smoothedAzimuth = ((smoothedAzimuth + delta * 0.5f) % 360f + 360f) % 360f
        return smoothedAzimuth
    }

    private fun startLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                curLat = loc.latitude; curLon = loc.longitude
                updateMe(loc.latitude, loc.longitude)
                followDriver(loc.latitude, loc.longitude)
            }
        }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L).build()
        try {
            fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Timber.tag("MaktabGo").d("location permission missing: %s", e.message)
        }
    }

    private fun stopLocation() {
        fused.removeLocationUpdates(locationCallback)
    }

    private fun updateMe(lat: Double, lon: Double) {
        val b = _binding ?: return
        val p = Point(lat, lon)
        val mk = meMark
        try {
            if (mk == null || !mk.isValid) {
                meMark = b.mapView.map.mapObjects.addPlacemark(p, ImageProvider.fromBitmap(navArrow()))
            } else {
                mk.geometry = p
            }
        } catch (e: Exception) {
            try {
                meMark = b.mapView.map.mapObjects.addPlacemark(p, ImageProvider.fromBitmap(navArrow()))
            } catch (e2: Exception) { Timber.tag("MaktabGo").d("me marker: %s", e2.message) }
        }
    }

    // ---- helpers ----

    private fun addRouteDetails(ll: android.widget.LinearLayout, r: BirgaRouteFull) {
        val avgKmh = 24.0
        val etaMin = if (r.distanceKm > 0) Math.ceil(r.distanceKm / avgKmh * 60.0).toInt() else 0
        val arrival = if (etaMin > 0) timePlusMinutes(etaMin) else "—"
        val tv = TextView(requireContext())
        tv.textSize = 14f
        tv.setPadding(0, 4, 0, 12)
        tv.setTextColor(Color.parseColor("#37474F"))
        val sb = StringBuilder("📏 ${r.distanceKm} км · ⏱ ~${etaMin} мин · 🏫 к школе ~${arrival}")
        if (!r.startDate.isNullOrBlank()) sb.append("\n📅 Старт рейса: ${r.startDate}")
        tv.text = sb.toString()
        ll.addView(tv)
    }

    private fun timePlusMinutes(min: Int): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.MINUTE, min)
        return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }

    private fun openSettings() {
        try { findNavController().navigate(R.id.action_maktabGoMapFragment_to_mapSettingsFragment) } catch (e: Exception) { }
    }

    private var gateChecked = false
    private fun maybeShowPermissionGate() {
        if (gateChecked) return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            gateChecked = true
            view?.post {
                try {
                    if (isAdded && findNavController().currentDestination?.id == R.id.maktabGoMapFragment)
                        findNavController().navigate(R.id.action_maktabGoMapFragment_to_accessPermissionsFragment)
                } catch (e: Exception) { }
            }
        }
    }

    private fun ensureLocationReady(): Boolean {
        val ctx = context ?: return false
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOC)
            showToast(getString(R.string.maktabgo_need_location))
            return false
        }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val on = lm?.let { it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } ?: false
        if (!on) {
            showToast(getString(R.string.maktabgo_enable_gps))
            try { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (e: Exception) {}
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOC && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            startLocation()
        }
    }


    private fun nextStop(): BirgaStop? =
        route?.stops?.firstOrNull { !isStopDone(it) }

    private fun isStopDone(stop: BirgaStop): Boolean =
        stop.pickups.isNotEmpty() && stop.pickups.all { it.status == "picked" || it.status == "dropped" }

    private fun stopColor(stop: BirgaStop): Int =
        if (isStopDone(stop)) Color.parseColor("#2E7D32") else Color.parseColor("#EF6C00")

    private fun navArrow(): Bitmap {
        val s = 88
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE; c.drawCircle(s / 2f, s / 2f, s / 2f - 3f, p)
        p.color = Color.parseColor("#1565C0"); c.drawCircle(s / 2f, s / 2f, s / 2f - 11f, p)
        p.color = Color.WHITE
        val path = android.graphics.Path()
        path.moveTo(s / 2f, s * 0.28f)
        path.lineTo(s * 0.70f, s * 0.72f)
        path.lineTo(s / 2f, s * 0.60f)
        path.lineTo(s * 0.30f, s * 0.72f)
        path.close()
        c.drawPath(path, p)
        return bmp
    }

    private fun dot(text: String, color: Int): Bitmap {
        val size = 72
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawCircle(size / 2f, size / 2f, size / 2f - 4f, p)
        p.color = Color.WHITE
        p.textSize = 34f
        p.textAlign = Paint.Align.CENTER
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(text, size / 2f, y, p)
        return bmp
    }

    private fun decodePolyline(encoded: String): List<Point> {
        val poly = ArrayList<Point>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            poly.add(Point(lat / 1e5, lng / 1e5))
        }
        return poly
    }

    private fun sendTrackingAction(action: String) {
        val ctx = context ?: return
        try {
            val i = Intent(ctx, MyTrackingService::class.java).apply {
                this.action = action
                putExtra("route_id", routeId)
            }
            if (action == Constants.ACTION_START_MAKTABGO_TRACKING)
                ContextCompat.startForegroundService(ctx, i)
            else
                ctx.startService(i)
        } catch (e: Exception) {
            Timber.tag("MaktabGo").d("svc action failed: %s", e.message)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLocation()
        _binding = null
    }

    companion object {
        const val ARG_ROUTE_ID = "routeId"
        private const val TRACK_INTERVAL_MS = 10_000L
        private const val REQ_LOC = 8010
    }
}
