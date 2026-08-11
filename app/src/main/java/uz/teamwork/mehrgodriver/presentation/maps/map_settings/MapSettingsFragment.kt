package uz.teamwork.mehrgodriver.presentation.maps.map_settings

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.CheckPermissions
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.APPLICATION_ID
import uz.teamwork.mehrgodriver.common.Constants.THEME_DAY
import uz.teamwork.mehrgodriver.common.Constants.THEME_NIGHT
import uz.teamwork.mehrgodriver.common.DriverAvatar
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.shared_pref.ThemeManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.DialogExitProfileBinding
import uz.teamwork.mehrgodriver.databinding.FragmentMapSettingsBinding

private const val PRIVACY_POLICY_URL = "https://mehrgo.uz/politics.html"

class MapSettingsFragment : Fragment() {
    private var _binding: FragmentMapSettingsBinding? = null
    private val binding get() = _binding!!

    private var _dialogExitProfileBinding: DialogExitProfileBinding? = null
    private val dialogExitProfileBinding get() = _dialogExitProfileBinding!!
    private var dialogExitProfile: Dialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            mcvBack.setDebouncedClickListener {
                findNavController().navigateUp()
            }

            tvTelegram.setDebouncedClickListener {
                telegram()
            }

            clSubscriptions.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapSettingsFragment_to_subscriptionsFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapSettingsFragment_to_subscriptionsFragment)
                    }
            }

            tvChooseMapType.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapSettingsFragment_to_chooseMapFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapSettingsFragment_to_chooseMapFragment)
                    }
            }

            tvUpdateApp.setDebouncedClickListener {
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=${APPLICATION_ID}")
                        )
                    )
                } catch (e: ActivityNotFoundException) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=${APPLICATION_ID}")
                        )
                    )
                }
            }

            clBalance.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapSettingsFragment_to_myPaymentFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapSettingsFragment_to_myPaymentFragment)
                    }
            }

            clDriverEarnings.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapSettingsFragment_to_driverEarningsFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapSettingsFragment_to_driverEarningsFragment)
                    }
            }

            clOrdersHistory.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapSettingsFragment_to_ordersHistoryFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapSettingsFragment_to_ordersHistoryFragment)
                    }
            }

            clMyProfile.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapSettingsFragment_to_myProfileFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapSettingsFragment_to_myProfileFragment)
                    }
            }

            clChangeLanguage.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapSettingsFragment_to_changeLanguageFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapSettingsFragment_to_changeLanguageFragment)
                    }
            }

            clChangeTheme.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapSettingsFragment_to_changeThemeFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapSettingsFragment_to_changeThemeFragment)
                    }
            }

            clHelpVideo.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapSettingsFragment_to_videosFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapSettingsFragment_to_videosFragment)
                    }
            }

            tvCommunicateOperator.setDebouncedClickListener {
                // Branch dispatcher first; brand support line only as a fallback.
                val number = Helper.dispatcherOrSupportNumber()
                if (number != null) {
                    call(number)
                } else {
                    showToast(getString(R.string.not_assigned_dispatcher))
                }
            }

            clOrdersInLocale.setDebouncedClickListener {
                findNavController().currentDestination?.getAction(R.id.action_mapSettingsFragment_to_ordersInLocaleFragment)
                    ?.let {
                        findNavController().navigate(R.id.action_mapSettingsFragment_to_ordersInLocaleFragment)
                    }
            }

            clOpenOtherApps.setDebouncedClickListener {
                openSettingsForUseOnApps()
            }

            clPrivacy.setDebouncedClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
            }

            mcvExit.setDebouncedClickListener {
                showDialogExitProfile()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Faded (neutral) system bars — same as the other screens, so the
        // navigation bar doesn't inherit a colored decor from the previous one.
        StatusBarHelper.applyAuthStyle(requireActivity())
        setView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Dismiss before releasing, or a dialog left on screen at teardown leaks its window.
        dialogExitProfile?.dismiss()
        _dialogExitProfileBinding = null
        dialogExitProfile = null
    }

    private fun showDialogExitProfile() {
        if (dialogExitProfile?.isShowing == true) return
        dialogExitProfile = Dialog(requireContext())
        _dialogExitProfileBinding = DialogExitProfileBinding.inflate(layoutInflater)
        dialogExitProfile!!.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogExitProfileBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // A custom-view dialog defaults to wrap_content width and clips its text;
            // pin it to 88% of the screen so the card lays out properly.
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialogExitProfileBinding.cvExit.setDebouncedClickListener {
            sendCommandToService(Constants.ACTION_STOP_SERVICE)
            UserManager.deleteUser()
            if (Constants.IS_MAKTABGO)
                findNavController().navigate(R.id.action_mapSettingsFragment_to_maktabGoLoginFragment)
            else
                findNavController().navigate(R.id.action_mapSettingsFragment_to_loginFragment)
            dialogExitProfile?.dismiss()
        }

        dialogExitProfileBinding.cvCancel.setOnClickListener {
            dialogExitProfile?.dismiss()
        }

        dialogExitProfile?.show()
    }

    private fun sendCommandToService(action: String) =
        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }

    // Functions
    @SuppressLint("SetTextI18n")
    private fun setView() {
        binding.apply {
            if (CheckPermissions.isOverlayPermissionAvailable(requireContext())) {
                clOpenOtherApps.visibility = View.VISIBLE
                vLineOpenOtherApps.visibility = View.VISIBLE

                if (CheckPermissions.checkHasDrawOverlayPermissions(requireContext())) {
                    ivOpenOtherApps.setImageResource(R.drawable.baseline_check_circle_24)
                    ivOpenOtherApps.setColorFilter(requireContext().getColor(R.color.green))
                } else {
                    ivOpenOtherApps.setImageResource(R.drawable.baseline_cancel_24)
                    ivOpenOtherApps.setColorFilter(requireContext().getColor(R.color.red))
                }
            } else {
                clOpenOtherApps.visibility = View.GONE
                vLineOpenOtherApps.visibility = View.GONE
            }

            tvBalance.text =
                Helper.formatPrice(UserManager.getBalance().toString()) + getString(R.string.sum)
            tvName.text = "${UserManager.getUser()?.firstName} ${UserManager.getUser()?.lastName}"
            tvId.text = "ID: ${UserManager.getUser()?.id}"
            // Cache only — this screen never calls user/me itself, same as tvName/tvId above.
            // Login, MyProfileFragment and MyPaymentFragment all saveUser() after their refresh,
            // so `photo` is in the cache by the time a driver gets here.
            DriverAvatar.bind(UserManager.getUser()?.photo, ivAvatar, ivAvatarPlaceholder)

            // Warn when this build is wired to the dev/staging backend (mirrors SettingsFragment).
            tvDevMode.visibility = if (Constants.IS_DEV_SERVER) View.VISIBLE else View.GONE

            tvVersion.text = buildString {
                append("${Constants.APP_VERSION_NAME} (${Constants.APP_VERSION})")
                if (Constants.IS_DEV_SERVER) append(" · ${Constants.BASE}")
            }

            when (ThemeManager.getTheme()) {
                THEME_DAY -> tvTheme.text = getString(R.string.theme_day)
                THEME_NIGHT -> tvTheme.text = getString(R.string.theme_night)
//                THEME_SYSTEM -> tvTheme.text = getString(R.string.theme_system)
                else -> tvTheme.text = getString(R.string.theme_system)
            }
        }
    }

    private fun openSettingsForUseOnApps() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${requireActivity().packageName}")
        )
        startActivity(intent)
    }

    private fun telegram() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("http://telegram.me/${requireContext().packageName}")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Not found Application", Toast.LENGTH_SHORT).show()
        }
    }

    private fun call(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$phoneNumber")
        startActivity(intent)
    }
}