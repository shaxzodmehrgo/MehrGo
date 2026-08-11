package uz.teamwork.mehrgodriver.presentation.main.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.CheckPermissions
import uz.teamwork.mehrgodriver.common.shared_pref.AccessPermissionsManager
import uz.teamwork.mehrgodriver.databinding.FragmentAccessPermissionsBinding

class AccessPermissionsFragment : Fragment() {
    private var _binding: FragmentAccessPermissionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccessPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUi()
        onClick()
    }

    private fun setUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.clBackgroundLocationPermission.visibility = View.VISIBLE

            binding.ivBackgroundLocationPermissionGranted.setGrantedIcon(
                CheckPermissions.checkBackgroundLocationPermission(requireContext())
            )
        } else {
            binding.clBackgroundLocationPermission.visibility = View.GONE
        }

        binding.ivOtherPermissionsGranted.setGrantedIcon(
            CheckPermissions.isGrantedOtherPermission(requireContext())
        )

        if (CheckPermissions.isOverlayPermissionAvailable(requireContext())) {
            binding.clOpenOtherApps.visibility = View.VISIBLE

            binding.ivOpenOtherApps.setGrantedIcon(
                CheckPermissions.checkHasDrawOverlayPermissions(requireContext())
            )
        } else {
            binding.clOpenOtherApps.visibility = View.GONE
        }

        binding.ivBatteryOptimization.setGrantedIcon(
            CheckPermissions.checkBatteryOptimisation(requireContext())
        )
    }

    /** Green check-circle when granted, red x-circle when not. */
    private fun ImageView.setGrantedIcon(granted: Boolean) {
        if (granted) {
            setImageResource(R.drawable.baseline_check_circle_24)
            setColorFilter(ContextCompat.getColor(context, R.color.green))
        } else {
            setImageResource(R.drawable.baseline_cancel_24)
            setColorFilter(ContextCompat.getColor(context, R.color.red))
        }
    }

    private fun onClick() {
        binding.apply {
//            tvSubmit.setOnClickListener {
//                if (CheckPermissions.isGrantedAllPermission(requireContext())) {
//                    if (CheckPermissions.checkHasDrawOverlayPermissions(requireContext())) {
//                        if (CheckPermissions.checkBatteryOptimisation(requireContext())) {
//                            if (CheckPermissions.isGPSEnabled(requireContext())) {
//                                findNavController().navigateUp()
//                            } else {
//                                openGpsSettings()
//                            }
//                        } else {
//                            openBatteryOptimisations()
//                        }
//                    } else {
//                        openSettingsForUseOnApps()
//                    }
//                } else {
//                    openSystemSettings()
//                }
//            }

            tvSubmit.setOnClickListener {
                if (CheckPermissions.isGrantedAllPermission(requireContext())) {
                    if (CheckPermissions.checkBatteryOptimisation(requireContext())) {
                        if (CheckPermissions.isGPSEnabled(requireContext())) {
                            if (CheckPermissions.isOverlayPermissionAvailable(requireContext())) {
                                if (CheckPermissions.checkHasDrawOverlayPermissions(requireContext())) {
                                    // Remember the user finished the flow so the
                                    // post-login fragments don't bounce them back
                                    // here if canDrawOverlays misreports later.
                                    AccessPermissionsManager.markCompleted()
                                    findNavController().navigateUp()
                                } else {
                                    openSettingsForUseOnApps()
                                }
                            } else {
                                AccessPermissionsManager.markCompleted()
                                findNavController().navigateUp()
                            }
                        } else {
                            openGpsSettings()
                        }
                    } else {
                        openBatteryOptimisations()
                    }
                } else {
                    openSystemSettings()
                }
            }

            clBackgroundLocationPermission.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (!CheckPermissions.checkBackgroundLocationPermission(requireContext())) {
                        // Granting background location sends us to Settings and the OS kills the app;
                        // mark a clean restart so we don't come back to a blank/white screen.
                        AccessPermissionsManager.markPendingRestart()
                        requestBackgroundPermissionLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
            }

            clOtherPermissions.setOnClickListener {
                requestPermissions()
            }

            clOpenOtherApps.setOnClickListener {
                if (!CheckPermissions.checkHasDrawOverlayPermissions(requireContext())) {
                    openSettingsForUseOnApps()
                }
            }

            clBatteryOptimization.setOnClickListener {
                if (!CheckPermissions.checkBatteryOptimisation(requireContext())) {
                    openBatteryOptimisations()
                } else {
                    binding.ivBatteryOptimization.setGrantedIcon(true)
                }
            }
        }
    }

    private fun openBatteryOptimisations() {
        val intent = Intent()
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = Uri.parse("package:${requireContext().packageName}")
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()

        setUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
//                    Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            } else {
                requestPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.CALL_PHONE,
//                    Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        } else {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
//                Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            )
        }
    }

    private val requestBackgroundPermissionLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                openSystemSettings()
            }
        }


    // Permissions and GPS
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->

        var isDeniedAnyPermission = false
        permissions.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if (!isGranted) {
                isDeniedAnyPermission = true
            }
        }

        if (isDeniedAnyPermission) {
            openSystemSettings()
        }
    }

    private fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", requireContext().packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun openSettingsForUseOnApps() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${requireActivity().packageName}")
        )
        startActivity(intent)
    }

    private fun openGpsSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }
}