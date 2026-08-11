package uz.teamwork.mehrgodriver.presentation.main.ui.maktabgo_routes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.MaktabGoSession
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.FragmentMaktabgoRoutesBinding
import uz.teamwork.mehrgodriver.databinding.ItemMaktabgoRouteBinding
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRouteBrief
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRouteFull
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRoutesResponse
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.presentation.main.ui.maktabgo_map.MaktabGoMapFragment

/**
 * Список рейсов водителя MaktabGo — первый экран ПОСЛЕ входа (исправляет «внутри не открывается»).
 * «Мои рейсы» (mine) + «Доступные рейсы» (available), переключатель «На линии» (/driver/online)
 * и приём рейса (/driver/routes/{id}/accept). Обновление — поллинг раз в 7с (без WS: MVP-вариант
 * из ANDROID_DRIVER_INTEGRATION.md §3). Экран рейса (карта + мультистоп + Яндекс.Навигатор) — далее.
 */
@AndroidEntryPoint
class MaktabGoRoutesFragment : Fragment() {

    private var _binding: FragmentMaktabgoRoutesBinding? = null
    private val binding get() = _binding!!
    private val vm: MaktabGoRoutesVM by viewModels()

    private var pollJob: Job? = null

    // Android 14+ (targetSdk 36): a foregroundServiceType=location service crashes on
    // startForeground unless location permission is already granted. Ask first (classic
    // requestPermissions API — совместимо со всеми версиями androidx проекта), then start.

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMaktabgoRoutesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivRefresh.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { loadRoutesOnce(showSpinner = true) }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // «Рейсы» — подраздел: назад всегда возвращает на карту, не сворачивает приложение.
                try {
                    if (!findNavController().popBackStack(R.id.maktabGoMapFragment, false)) {
                        findNavController().navigate(R.id.action_maktabGoRoutesFragment_to_maktabGoMapFragment)
                    }
                } catch (e: Exception) { requireActivity().moveTaskToBack(true) }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        pollJob?.cancel()
        pollJob = viewLifecycleOwner.lifecycleScope.launch {
            var first = true
            while (isActive) {
                loadRoutesOnce(showSpinner = first)
                first = false
                delay(POLL_MS)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun loadRoutesOnce(showSpinner: Boolean) {
        vm.routes().collect { res ->
            val b = _binding ?: return@collect
            when (res) {
                is Resource.Loading -> if (showSpinner) b.pbRoutes.visibility = View.VISIBLE
                is Resource.Success -> {
                    b.pbRoutes.visibility = View.GONE
                    render(res.data)
                }
                is Resource.Error -> {
                    b.pbRoutes.visibility = View.GONE
                    if (showSpinner) showToast(res.message ?: getString(R.string.error))
                    Timber.tag("MaktabGo").d("routes error: %s", res.message)
                }
            }
        }
    }

    private fun render(data: BirgaRoutesResponse?) {
        val mine = data?.mine ?: emptyList()
        val available = data?.available ?: emptyList()
        renderInto(binding.llMine, mine, mineSection = true)
        renderInto(binding.llAvailable, available, mineSection = false)
        binding.tvMineHeader.visibility = if (mine.isEmpty()) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = if (mine.isEmpty() && available.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderInto(container: LinearLayout, routes: List<BirgaRouteBrief>, mineSection: Boolean) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (r in routes) {
            val item = ItemMaktabgoRouteBinding.inflate(inflater, container, false)
            item.tvSchool.text = r.school ?: getString(R.string.maktabgo_route_no_school)
            item.tvStatus.text = statusLabel(r.status)
            item.tvMeta.text = getString(R.string.maktabgo_route_meta, r.children, r.capacity, r.distanceKm)
            val sd = r.startDate
            if (sd.isNullOrEmpty()) {
                item.tvStart.visibility = View.GONE
            } else {
                item.tvStart.visibility = View.VISIBLE
                item.tvStart.text = sd
            }
            if (mineSection) {
                item.tvAction.visibility = View.VISIBLE
                item.tvAction.setText(R.string.maktabgo_open)
                item.tvAction.setOnClickListener { openRoute(r.id) }
                item.root.setOnClickListener { openRoute(r.id) }
            } else {
                item.tvAction.visibility = View.VISIBLE
                item.tvAction.setText(R.string.maktabgo_details)
                item.tvAction.setOnClickListener { showRouteDetails(r.id) }
            }
            container.addView(item.root)
        }
    }

    private fun statusLabel(status: String?): String = when (status) {
        "forming" -> getString(R.string.maktabgo_status_forming)
        "active" -> getString(R.string.maktabgo_status_active)
        "running" -> getString(R.string.maktabgo_status_running)
        "done" -> getString(R.string.maktabgo_status_done)
        "cancelled" -> getString(R.string.maktabgo_status_cancelled)
        else -> status ?: ""
    }

    private fun acceptRoute(id: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.accept(id).collect { res ->
                val b = _binding ?: return@collect
                when (res) {
                    is Resource.Loading -> b.pbRoutes.visibility = View.VISIBLE
                    is Resource.Success -> {
                        b.pbRoutes.visibility = View.GONE
                        showToast(getString(R.string.maktabgo_accepted))
                        openRoute(id)
                    }
                    is Resource.Error -> {
                        b.pbRoutes.visibility = View.GONE
                        showToast(res.message ?: getString(R.string.error))
                    }
                }
            }
        }
    }

    private fun showRouteDetails(id: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.route(id).collect { res ->
                val b = _binding ?: return@collect
                when (res) {
                    is Resource.Loading -> b.pbRoutes.visibility = View.VISIBLE
                    is Resource.Success -> {
                        b.pbRoutes.visibility = View.GONE
                        res.data?.route?.let { showDetailsDialog(it) }
                    }
                    is Resource.Error -> {
                        b.pbRoutes.visibility = View.GONE
                        showToast(res.message ?: getString(R.string.error))
                    }
                }
            }
        }
    }

    private fun showDetailsDialog(r: BirgaRouteFull) {
        val ctx = context ?: return
        val msg = buildString {
            append(getString(R.string.maktabgo_det_to, r.school?.name ?: "—")).append("\n")
            append(getString(R.string.maktabgo_det_children, r.children, r.capacity)).append("\n")
            append(getString(R.string.maktabgo_det_points, r.stops.size)).append("\n")
            append(getString(R.string.maktabgo_det_distance, r.distanceKm))
            if (!r.startDate.isNullOrBlank()) { append("\n"); append(getString(R.string.maktabgo_det_start, r.startDate!!)) }
        }
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(r.school?.name ?: getString(R.string.maktabgo_route_no_school))
            .setMessage(msg)
            .setPositiveButton(R.string.maktabgo_accept) { d, _ -> d.dismiss(); acceptRoute(r.id) }
            .setNegativeButton(R.string.maktabgo_close) { d, _ -> d.dismiss() }
            .show()
    }

    private fun openRoute(id: Int) {
        // Возврат на карту; домашняя карта сама подхватит активный рейс (mine). Без падений навигации.
        try {
            if (!findNavController().popBackStack(R.id.maktabGoMapFragment, false)) {
                findNavController().navigate(
                    R.id.action_maktabGoRoutesFragment_to_maktabGoMapFragment,
                    bundleOf(MaktabGoMapFragment.ARG_ROUTE_ID to id)
                )
            }
        } catch (e: Exception) { showToast(getString(R.string.error)) }
    }

    private fun toggleTrackingService(online: Boolean) {
        if (!online) { stopTrackingService(); return }
        if (hasLocationPermission()) startTrackingService()
        else requestPermissions(locationPerms(), REQ_LOCATION)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun locationPerms(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    private fun startTrackingService() {
        val ctx = context ?: return
        try {
            val intent = Intent(ctx, MyTrackingService::class.java).apply {
                action = Constants.ACTION_START_SERVICE
            }
            ContextCompat.startForegroundService(ctx, intent)
        } catch (e: Exception) {
            Timber.tag("MaktabGo").e("start service failed: %s", e.message)
            showToast(getString(R.string.error))
        }
    }

    private fun stopTrackingService() {
        val ctx = context ?: return
        try {
            val intent = Intent(ctx, MyTrackingService::class.java).apply {
                action = Constants.ACTION_STOP_SERVICE
            }
            ctx.startService(intent)
        } catch (e: Exception) {
            Timber.tag("MaktabGo").d("stop service failed: %s", e.message)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            val granted = grantResults.isNotEmpty() &&
                grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            if (granted) startTrackingService()
            else showToast(getString(R.string.maktabgo_need_location))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollJob?.cancel()
        pollJob = null
        _binding = null
    }

    companion object {
        private const val POLL_MS = 7000L
        private const val REQ_LOCATION = 7001
    }
}
