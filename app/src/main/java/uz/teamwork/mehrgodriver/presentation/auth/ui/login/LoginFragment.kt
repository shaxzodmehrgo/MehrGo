package uz.teamwork.mehrgodriver.presentation.auth.ui.login

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.CheckPermissions
import uz.teamwork.mehrgodriver.common.Constants.DRIVER_ACTIVE
import uz.teamwork.mehrgodriver.common.Constants.DRIVER_INFO_COMPLETED
import uz.teamwork.mehrgodriver.common.Constants.DRIVER_TURNED_NOT_ACTIVE
import uz.teamwork.mehrgodriver.common.Constants.PHONE_NUMBER_SIZE
import uz.teamwork.mehrgodriver.common.Constants.USER_INFO_COMPLETED
import uz.teamwork.mehrgodriver.common.Constants.USER_INFO_DELETED
import uz.teamwork.mehrgodriver.common.Constants.VERIFY_CODE_CONFIRMED
import uz.teamwork.mehrgodriver.common.Constants.VIDEO_SIGN_UP
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.common.Validate
import uz.teamwork.mehrgodriver.common.shared_pref.AccessPermissionsManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.FragmentLoginBinding

@AndroidEntryPoint
class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val loginVM: LoginVM by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyAuthInsets()
        onClick()
        setupValidation()

//        if (!CheckPermissions.isGrantedAllPermission(requireContext()) ||
//            !CheckPermissions.checkHasDrawOverlayPermissions(requireContext()) ||
//            !CheckPermissions.checkBatteryOptimisation(requireContext())) {
//
//            findNavController().navigate(R.id.action_loginFragment_to_accessPermissionsFragment)
//        }

        if (!CheckPermissions.isGrantedAllPermission(requireContext()) ||
            !CheckPermissions.checkBatteryOptimisation(requireContext())
        ) {

            findNavController().navigate(R.id.action_loginFragment_to_accessPermissionsFragment)
        } else if (!AccessPermissionsManager.isCompleted() &&
            CheckPermissions.isOverlayPermissionAvailable(requireContext()) &&
            !CheckPermissions.checkHasDrawOverlayPermissions(requireContext())
        ) {
            // First-run overlay prompt only. Once AccessPermissionsManager is
            // marked completed we trust the user's earlier choice and don't
            // re-route here — some OEM builds (Transsion / Xiaomi) report
            // canDrawOverlays=false until the next process restart even after
            // the user granted the permission, which would otherwise trap them
            // in an infinite Login → AccessPermissions loop.
            findNavController().navigate(R.id.action_loginFragment_to_accessPermissionsFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        StatusBarHelper.applyAuthStyle(requireActivity())
    }

    private fun applyAuthInsets() {
        val basePaddingBottom = binding.scrollContent.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // Status bar is HIDDEN in immersive mode, so getInsets() returns top=0 — use
            // getInsetsIgnoringVisibility so the top bar still clears the notch/status area.
            binding.topBar.updatePadding(
                top = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
            )
            binding.scrollContent.updatePadding(
                bottom = basePaddingBottom + maxOf(bars.bottom, ime.bottom)
            )
            insets
        }
    }

    private fun setupValidation() {
        binding.apply {
            etPhoneNumber.addTextChangedListener { updateSubmitState() }
            etPassword.addTextChangedListener { updateSubmitState() }
        }
        updateSubmitState()
    }

    private fun updateSubmitState() {
        if (_binding == null) return
        binding.apply {
            val phoneOk = etPhoneNumber.unMasked.length >= PHONE_NUMBER_SIZE - 1
            val passwordOk = etPassword.text.toString().length > 6
            tvLogin.isEnabled = phoneOk && passwordOk
        }
    }

    private fun onClick() {
        binding.apply {
            tvLogin.setOnClickListener {
                if (CheckPermissions.isGPSEnabled(requireContext())) {
                    val phoneNumber = "+${binding.etPhoneNumber.unMasked}"
                    val password = binding.etPassword.text.toString()

                    val validate = Validate.login(phoneNumber, password, requireContext())
                    if (validate.valid) {
                        sendRequest(Helper.formatPhoneNumber(phoneNumber), password)
                    } else {
                        Toast.makeText(requireContext(), validate.message, Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    openGpsSettings()
                }

//                findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToCompleteDriverInfoFragment("scjsjksa"))
            }

            tvSignUp.setOnClickListener {
                if (CheckPermissions.isGPSEnabled(requireContext())) {
                    findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToSignUpFragment())
                } else {
                    openGpsSettings()
                }
            }

            tvPasswordRecovery.setOnClickListener {
                if (CheckPermissions.isGPSEnabled(requireContext())) {
                    findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToPasswordRecoveryFragment())
                } else {
                    openGpsSettings()
                }
            }

            flAsk.setOnClickListener {
                findNavController().navigate(
                    LoginFragmentDirections.actionLoginFragmentToInstructionFragment(
                        VIDEO_SIGN_UP
                    )
                )
            }

            ivPasswordToggle.setOnClickListener {
                val isVisible = etPassword.inputType ==
                        (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                val cursor = etPassword.selectionEnd
                if (isVisible) {
                    etPassword.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off_24)
                } else {
                    etPassword.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    ivPasswordToggle.setImageResource(R.drawable.ic_visibility_24)
                }
                etPassword.setSelection(cursor.coerceAtLeast(0))
            }
        }
    }

    private fun sendRequest(phoneNumber: String, password: String) {
        // View-scoped: a fragment-scope collect outlives onDestroyView, and a late emit
        // then hits binding (=_binding!!) → NPE (prod crash at errorVisible).
        viewLifecycleOwner.lifecycleScope.launch {
            loginVM.login(phoneNumber, password).collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        when (it.data?.data?.status?.valueNumber) {
                            USER_INFO_COMPLETED -> {
                                screenVisible()
                                showToast(getString(R.string.you_do_not_finished_sign_up))
                            }

                            VERIFY_CODE_CONFIRMED -> {
                                findNavController().navigate(
                                    LoginFragmentDirections.actionLoginFragmentToCompleteDriverInfoFragment(
                                        it.data.data?.authKey!!
                                    )
                                )
                            }

                            DRIVER_INFO_COMPLETED -> {
                                UserManager.saveUser(it.data.data!!)
                                Timber.tag("FCM").d(
                                    "login OK status=%s, server device_token=%s",
                                    it.data.data?.status?.valueNumber,
                                    it.data?.data?.deviceToken
                                )
                                loginVM.syncFcmTokenIfNeeded(it.data?.data?.deviceToken)
                                findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToNavigationHome())
                            }

                            DRIVER_ACTIVE -> {
                                UserManager.saveUser(it.data.data!!)
                                Timber.tag("FCM").d(
                                    "login OK status=%s, server device_token=%s",
                                    it.data.data?.status?.valueNumber,
                                    it.data?.data?.deviceToken
                                )
                                loginVM.syncFcmTokenIfNeeded(it.data?.data?.deviceToken)
                                findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToNavigationHome())
                            }

                            DRIVER_TURNED_NOT_ACTIVE -> {
                                UserManager.saveUser(it.data.data!!)
                                Timber.tag("FCM").d(
                                    "login OK status=%s, server device_token=%s",
                                    it.data.data?.status?.valueNumber,
                                    it.data?.data?.deviceToken
                                )
                                loginVM.syncFcmTokenIfNeeded(it.data?.data?.deviceToken)
                                findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToNavigationHome())
                            }

                            USER_INFO_DELETED -> {
                                UserManager.saveUser(it.data.data!!)
                                Timber.tag("FCM").d(
                                    "login OK status=%s, server device_token=%s",
                                    it.data.data?.status?.valueNumber,
                                    it.data?.data?.deviceToken
                                )
                                loginVM.syncFcmTokenIfNeeded(it.data?.data?.deviceToken)
                                findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToNavigationHome())
                            }

                            else -> {
                                screenVisible()
                                showToast(getString(R.string.error))
                            }
                        }
                    }

                    is Resource.Error -> {
                        showToast(it.message!!)
                        errorVisible()
                    }
                }
            }
        }
    }

    private fun openGpsSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }

    // All three null-guard _binding: they're reached from flow collects that can fire
    // around view teardown.
    private fun loadingVisible() {
        val b = _binding ?: return
        b.apply {
            tvLogin.text = ""
            tvLogin.isClickable = false
            pbLogin.visibility = View.VISIBLE
        }
    }

    private fun screenVisible() {
        val b = _binding ?: return
        b.apply {
            tvLogin.setText(R.string.login)
            pbLogin.visibility = View.GONE
            tvLogin.isClickable = true
            updateSubmitState()
        }
    }

    private fun errorVisible() {
        val b = _binding ?: return
        b.apply {
            tvLogin.setText(R.string.login)
            pbLogin.visibility = View.GONE
            tvLogin.isClickable = true
            updateSubmitState()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}