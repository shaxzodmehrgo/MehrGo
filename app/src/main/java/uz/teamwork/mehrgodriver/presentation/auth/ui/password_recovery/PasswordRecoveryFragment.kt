package uz.teamwork.mehrgodriver.presentation.auth.ui.password_recovery

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
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
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.PHONE_NUMBER_SIZE
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.common.Validate
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.FragmentPasswordRecoveryBinding
import uz.teamwork.mehrgodriver.domain.model.TelegramAuthStatus

@AndroidEntryPoint
class PasswordRecoveryFragment : Fragment() {
    private var _binding: FragmentPasswordRecoveryBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val passwordRecoveryVM: PasswordRecoveryVM by viewModels()

    // Telegram-OTP status for the entered phone (see SignUpFragment for the full rationale).
    private var telegramStatus: TelegramAuthStatus? = null
    private var telegramStatusPhone: String? = null
    private var pendingTelegramLink = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPasswordRecoveryBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyAuthInsets()
        onClick()
        setupValidation()

        // Early probe with an empty phone: learns the global `enabled` flag so the Telegram
        // button can hide before a number is typed. Per-phone `linked` refreshes on completion.
        fetchTelegramStatus("")
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
            tvSubmit.isEnabled = false
            val watcher: () -> Unit = { updateSubmitState() }
            etPhoneNumber.addTextChangedListener {
                watcher()
                // Warm up `linked`/`botUrl` for the completed number so the Telegram tap
                // can skip the bot round-trip when already linked.
                if (etPhoneNumber.unMasked.length >= PHONE_NUMBER_SIZE - 1) {
                    fetchTelegramStatus(currentFormattedPhone())
                }
            }
            etPassword.addTextChangedListener { watcher() }
            etConfirmPassword.addTextChangedListener { watcher() }
        }
        updateSubmitState()
    }

    private fun updateSubmitState() {
        if (_binding == null) return
        binding.apply {
            val phoneOk = etPhoneNumber.unMasked.length >= PHONE_NUMBER_SIZE - 1
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()
            val passwordOk = password.length > 6 && password == confirmPassword
            tvSubmit.isEnabled = phoneOk && passwordOk
        }
    }

    override fun onResume() {
        super.onResume()
        StatusBarHelper.applyAuthStyle(requireActivity())

        // Returned from the bot after being asked to link — re-check and, if now linked,
        // request the Telegram code (don't re-open the bot). Only while the form is valid.
        if (pendingTelegramLink) {
            pendingTelegramLink = false
            if (_binding != null && binding.tvSubmit.isEnabled) {
                requestTelegramCode(currentFormattedPhone(), allowBotOpen = false)
            }
        }
    }

    private fun onClick() {
        binding.apply {
            tvSubmit.setDebouncedClickListener { submit(Constants.CHANNEL_SMS) }

            // Same validation as the primary button — only the delivery channel differs.
            flTelegramSignUp.setDebouncedClickListener { submit(Constants.CHANNEL_TELEGRAM) }

            flAsk.setOnClickListener {
                findNavController().navigate(
                    PasswordRecoveryFragmentDirections.actionPasswordRecoveryFragmentToInstructionFragment(
                        Constants.VIDEO_PASSWORD_RECOVERY
                    )
                )
            }

            ivPasswordToggle.setOnClickListener {
                togglePasswordVisibility(etPassword, ivPasswordToggle)
            }

            ivConfirmPasswordToggle.setOnClickListener {
                togglePasswordVisibility(etConfirmPassword, ivConfirmPasswordToggle)
            }

            ivBack.setOnClickListener { findNavController().navigateUp() }
        }
    }

    private fun togglePasswordVisibility(field: EditText, toggle: ImageView) {
        val visible = field.inputType ==
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        val cursor = field.selectionEnd
        if (visible) {
            field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            toggle.setImageResource(R.drawable.ic_visibility_off_24)
        } else {
            field.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            toggle.setImageResource(R.drawable.ic_visibility_24)
        }
        field.setSelection(cursor.coerceAtLeast(0))
    }

    // Shared entry for both CTAs: same validation, only the delivery channel differs.
    private fun submit(channel: String) {
        if (_binding == null) return
        val phoneNumber = "+${binding.etPhoneNumber.unMasked}"
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        val validate =
            Validate.passwordRecovery(phoneNumber, password, confirmPassword, requireContext())
        if (!validate.valid) {
            Toast.makeText(requireContext(), validate.message, Toast.LENGTH_SHORT).show()
            return
        }
        if (channel == Constants.CHANNEL_TELEGRAM) {
            requestTelegramCode(currentFormattedPhone(), allowBotOpen = true)
        } else {
            sendRequest(Constants.CHANNEL_SMS)
        }
    }

    private fun currentFormattedPhone(): String =
        Helper.formatPhoneNumber("+${binding.etPhoneNumber.unMasked}")

    /** Telegram code request gated on link status — see SignUpFragment for the full flow. */
    private fun requestTelegramCode(phone: String, allowBotOpen: Boolean) {
        setTelegramLoading(true)
        telegramStatusPhone = null
        // View-scoped: a late emit after onDestroyView would hit the null binding (NPE).
        viewLifecycleOwner.lifecycleScope.launch {
            passwordRecoveryVM.telegramAuthStatus(phone).collect {
                when (it) {
                    is Resource.Loading -> {}

                    is Resource.Success -> {
                        telegramStatus = it.data?.data
                        telegramStatusPhone = phone
                        applyTelegramAvailability()
                        if (telegramStatus?.linked == true) {
                            sendRequest(Constants.CHANNEL_TELEGRAM)
                        } else {
                            setTelegramLoading(false)
                            if (allowBotOpen) {
                                openTelegramBot()
                                pendingTelegramLink = true
                                showToast(getString(R.string.telegram_link_prompt))
                            } else {
                                showToast(getString(R.string.telegram_not_linked_yet))
                            }
                        }
                    }

                    is Resource.Error -> {
                        // Status endpoint unreachable: fire the request anyway (the backend
                        // SMS-falls-back when not linked). Deliberately do NOT open the bot
                        // here — leaving the app mid-request would drop the success
                        // navigation (FragmentManager isStateSaved) and strand the spinner.
                        sendRequest(Constants.CHANNEL_TELEGRAM)
                    }
                }
            }
        }
    }

    private fun sendRequest(channel: String) {
        val phoneNumber = currentFormattedPhone()
        val password = binding.etPassword.text.toString()
        val telegram = channel == Constants.CHANNEL_TELEGRAM

        // View-scoped: a late emit after onDestroyView would hit the null binding (NPE).
        viewLifecycleOwner.lifecycleScope.launch {
            passwordRecoveryVM.passwordRecovery(phoneNumber, channel).collect {
                when (it) {
                    is Resource.Loading -> {
                        if (telegram) setTelegramLoading(true) else loadingVisible()
                    }

                    is Resource.Success -> {
                        val authKeyVerify = it.data?.data?.authKeyVerify!!
                        findNavController().navigate(
                            PasswordRecoveryFragmentDirections
                                .actionPasswordRecoveryFragmentToPasswordRecoveryVerifyFragment(
                                    phoneNumber, password, authKeyVerify
                                )
                                .setChannel(channel)
                        )
                    }

                    is Resource.Error -> {
                        if (telegram) setTelegramLoading(false) else errorVisible()
                        showToast(it.message!!)
                    }
                }
            }
        }
    }

    private fun setTelegramLoading(loading: Boolean) {
        val b = _binding ?: return
        b.flTelegramSignUp.isClickable = !loading
        b.llTelegramContent.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        b.pbTelegram.visibility = if (loading) View.VISIBLE else View.GONE
    }

    /**
     * Passive availability probe (no UI side effects beyond show/hide): refreshes
     * `enabled`/`linked`/`botUrl`. Fail-open — an error keeps the last known state, since
     * channel=telegram is SMS-safe on the backend. Deduped per phone value.
     */
    private fun fetchTelegramStatus(phone: String) {
        if (telegramStatusPhone == phone) return
        viewLifecycleOwner.lifecycleScope.launch {
            passwordRecoveryVM.telegramAuthStatus(phone).collect {
                if (it is Resource.Success) {
                    telegramStatus = it.data?.data
                    telegramStatusPhone = phone
                    applyTelegramAvailability()
                }
            }
        }
    }

    // Hidden only when the backend explicitly disables Telegram OTP; unknown/failed → shown.
    private fun applyTelegramAvailability() {
        val b = _binding ?: return
        val show = telegramStatus?.enabled != false
        b.llOrDivider.visibility = if (show) View.VISIBLE else View.GONE
        b.flTelegramSignUp.visibility = if (show) View.VISIBLE else View.GONE
    }

    // No-op when the brand has no bot configured or Telegram isn't installed.
    private fun openTelegramBot() {
        val url = telegramStatus?.botUrl?.takeIf { it.isNotBlank() }
            ?: Constants.TELEGRAM_BOT_USERNAME.takeIf { it.isNotBlank() }
                ?.let { "https://t.me/$it" }
            ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun loadingVisible() {
        binding.apply {
            tvSubmit.text = ""
            tvSubmit.isClickable = false
            pbSubmit.visibility = View.VISIBLE
        }
    }

    private fun screenVisible() {
        binding.apply {
            tvSubmit.setText(R.string.submit)
            pbSubmit.visibility = View.GONE
            tvSubmit.isClickable = true
            updateSubmitState()
        }
    }

    private fun errorVisible() {
        binding.apply {
            tvSubmit.setText(R.string.submit)
            pbSubmit.visibility = View.GONE
            tvSubmit.isClickable = true
            updateSubmitState()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}