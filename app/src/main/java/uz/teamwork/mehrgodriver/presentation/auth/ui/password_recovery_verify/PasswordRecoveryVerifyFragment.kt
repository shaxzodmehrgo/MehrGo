package uz.teamwork.mehrgodriver.presentation.auth.ui.password_recovery_verify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.DRIVER_ACTIVE
import uz.teamwork.mehrgodriver.common.Constants.DRIVER_INFO_COMPLETED
import uz.teamwork.mehrgodriver.common.Constants.USER_INFO_COMPLETED
import uz.teamwork.mehrgodriver.common.Constants.VERIFICATION_CODE_SIZE
import uz.teamwork.mehrgodriver.common.Constants.VERIFY_CODE_CONFIRMED
import uz.teamwork.mehrgodriver.common.Constants.WAIT_TIME_VERIFY_CODE
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.common.Validate
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.FragmentPasswordRecoveryVerifyBinding

@AndroidEntryPoint
class PasswordRecoveryVerifyFragment : Fragment() {
    private var _binding: FragmentPasswordRecoveryVerifyBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val passwordRecoveryVerifyVM: PasswordRecoveryVerifyVM by viewModels()

    private var phoneNumber: String? = null
    private var password: String? = null
    private var authKey: String? = null
    private var timer: CountDownTimer? = null
    private var waitTime = WAIT_TIME_VERIFY_CODE
    private var autoSubmitted = false

    // Delivery channel chosen on the recovery screen — resend reuses it (Telegram gets the
    // shorter cooldown); drives the "code sent to <phone>" wording + the open-bot link.
    private var channel: String = Constants.CHANNEL_SMS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        phoneNumber = arguments?.getString("phone_number")
        password = arguments?.getString("password")
        authKey = arguments?.getString("auth_key_verify")
        channel = arguments?.getString("channel") ?: Constants.CHANNEL_SMS
        if (channel == Constants.CHANNEL_TELEGRAM) waitTime = Constants.TELEGRAM_RESEND_WAITING_TIME
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPasswordRecoveryVerifyBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyAuthInsets()
        onClick()
        setCountDownTimer()
        setupValidation()
        applyChannelUi()

        // Open the keyboard and focus the hidden OTP input as soon as the screen appears.
        binding.otpContainer.post { focusOtp() }
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
        binding.otpContainer.setOnClickListener { focusOtp() }
        binding.otpRow.setOnClickListener { focusOtp() }
        binding.etVerifyCode.addTextChangedListener {
            renderOtpCells()
            updateSubmitState()
            triggerAutoSubmitIfReady()
        }
        renderOtpCells()
        updateSubmitState()
    }

    private fun focusOtp() {
        if (_binding == null) return
        binding.etVerifyCode.requestFocus()
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etVerifyCode, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun renderOtpCells() {
        if (_binding == null) return
        val code = binding.etVerifyCode.text.toString()
        val cells = listOf(
            binding.otpCell0,
            binding.otpCell1,
            binding.otpCell2,
            binding.otpCell3
        )
        cells.forEachIndexed { index, cell ->
            val filled = index < code.length
            cell.text = if (filled) code[index].toString() else ""
            val bg = when {
                filled -> R.drawable.bg_otp_filled
                index == code.length -> R.drawable.bg_otp_active
                else -> R.drawable.bg_otp_empty
            }
            cell.setBackgroundResource(bg)
        }
    }

    private fun updateSubmitState() {
        if (_binding == null) return
        binding.tvSubmit.isEnabled =
            binding.etVerifyCode.text.toString().length >= VERIFICATION_CODE_SIZE
    }

    private fun triggerAutoSubmitIfReady() {
        if (_binding == null || autoSubmitted) return
        val text = binding.etVerifyCode.text.toString()
        if (text.length < VERIFICATION_CODE_SIZE) return
        val code = text.toIntOrNull() ?: return
        autoSubmitted = true
        passwordRecoveryChangePassword(code)
    }

    override fun onResume() {
        super.onResume()
        StatusBarHelper.applyAuthStyle(requireActivity())
    }

    private fun onClick() {
        binding.apply {
            tvSubmit.setOnClickListener {
                val verifyCode = binding.etVerifyCode.text.toString()

                val validate = Validate.verifyCode(verifyCode, requireContext())
                if (validate.valid) {
                    passwordRecoveryChangePassword(verifyCode.toInt())
                } else {
                    Toast.makeText(requireContext(), validate.message, Toast.LENGTH_SHORT).show()
                }
            }

            tvGetNewCode.setDebouncedClickListener {
                resendVia(channel)
            }

            // Re-request the code through the OTHER channel; the screen re-themes to it.
            tvSwitchChannel.setDebouncedClickListener {
                resendVia(
                    if (channel == Constants.CHANNEL_TELEGRAM) Constants.CHANNEL_SMS
                    else Constants.CHANNEL_TELEGRAM
                )
            }

            tvOpenBot.setOnClickListener { openBot() }

            ivBack.setOnClickListener { findNavController().navigateUp() }
        }
    }

    // Header icon + wording + the open-bot / switch buttons follow the current channel.
    // Bidirectional: the switch flow can re-theme Telegram → SMS too.
    private fun applyChannelUi() {
        val b = _binding ?: return
        val telegram = channel == Constants.CHANNEL_TELEGRAM
        if (telegram) {
            // Colored Telegram plane — clear the accent tint the SMS glyph uses.
            b.ivChannelIcon.setImageResource(R.drawable.icon_telegram)
            b.ivChannelIcon.imageTintList = null
            b.tvMessage.text = if (!phoneNumber.isNullOrBlank()) {
                getString(R.string.verify_telegram_message, phoneNumber)
            } else {
                getString(R.string.verify_telegram_message_short)
            }
        } else {
            b.ivChannelIcon.setImageResource(R.drawable.ic_sms_24)
            b.ivChannelIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.app_color)
            )
            b.tvMessage.setText(R.string.verification_code_subtitle)
        }
        b.tvOpenBot.visibility = if (telegram) View.VISIBLE else View.GONE

        // The switch chip offers the OTHER channel (its icon must keep the right tint:
        // the Telegram vector is pre-colored, the SMS glyph needs the accent).
        b.tvSwitchChannel.text = getString(
            if (telegram) R.string.receive_via_sms else R.string.receive_via_telegram
        )
        b.tvSwitchChannel.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (telegram) R.drawable.ic_sms_24 else R.drawable.icon_telegram, 0, 0, 0
        )
        TextViewCompat.setCompoundDrawableTintList(
            b.tvSwitchChannel,
            if (telegram) {
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.app_color))
            } else null
        )
    }

    // No-op when the brand has no bot configured.
    private fun openBot() {
        val bot = Constants.TELEGRAM_BOT_USERNAME.takeIf { it.isNotBlank() } ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$bot")))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Resend through [targetChannel] — the current one (plain resend) or the other one
     *  (channel switch). On success the screen re-themes and the cooldown restarts. */
    private fun resendVia(targetChannel: String) {
        // View-scoped: a late emit after onDestroyView would hit the null binding (NPE).
        viewLifecycleOwner.lifecycleScope.launch {
            passwordRecoveryVerifyVM.passwordRecoveryResendCode(authKey!!, targetChannel).collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                        // Block the sibling trigger too: a concurrent tap on the other
                        // button would race a second resend through the other channel.
                        binding.tvGetNewCode.isClickable = false
                        binding.tvSwitchChannel.isClickable = false
                    }

                    is Resource.Success -> {
                        screenVisible()
                        authKey = it.data?.data?.authKey!!
                        channel = targetChannel

                        binding.apply {
                            tvGetNewCode.isClickable = false
                            // Switching is only offered again once the new cooldown ends.
                            tvSwitchChannel.visibility = View.GONE

                            tvGetNewCode.background = ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.bg_resend_inactive
                            )
                            tvGetNewCode.setTextColor(
                                ContextCompat.getColor(
                                    requireContext(),
                                    R.color.gray_full
                                )
                            )

                            waitTime = if (targetChannel == Constants.CHANNEL_TELEGRAM) {
                                Constants.TELEGRAM_RESEND_WAITING_TIME
                            } else {
                                WAIT_TIME_VERIFY_CODE
                            }
                        }
                        applyChannelUi()
                        // Recreate rather than restart: the timer's total duration is fixed
                        // at construction, and the two channels have different cooldowns.
                        timer?.cancel()
                        setCountDownTimer()
                    }

                    is Resource.Error -> {
                        errorVisible()
                        binding.tvGetNewCode.isClickable = true
                        binding.tvSwitchChannel.isClickable = true
                        showToast(it.message!!)
                    }
                }
            }
        }
    }

    private fun passwordRecoveryChangePassword(verifyCode: Int) {
        lifecycleScope.launch {
            passwordRecoveryVerifyVM.passwordRecoveryChangePassword(
                password!!,
                verifyCode,
                authKey!!
            ).collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        val data = it.data!!.data!!

                        when (data.status?.valueNumber) {
                            USER_INFO_COMPLETED -> {
                                screenVisible()
                                showToast(getString(R.string.you_do_not_finished_sign_up))
                            }

                            VERIFY_CODE_CONFIRMED -> {
                                findNavController().navigate(
                                    PasswordRecoveryVerifyFragmentDirections.actionPasswordRecoveryVerifyFragmentToCompleteDriverInfoFragment(
                                        data.authKey!!
                                    )
                                )
                            }

                            DRIVER_INFO_COMPLETED -> {
                                UserManager.saveUser(data)
                                Timber.tag("FCM").d(
                                    "change-password OK, server device_token=%s",
                                    data.deviceToken
                                )
                                passwordRecoveryVerifyVM.syncFcmTokenIfNeeded(data.deviceToken)
                                findNavController().navigate(
                                    PasswordRecoveryVerifyFragmentDirections.actionPasswordRecoveryVerifyFragmentToNavigationHome()
                                )
                            }

                            DRIVER_ACTIVE -> {
                                UserManager.saveUser(data)
                                Timber.tag("FCM").d(
                                    "change-password OK, server device_token=%s",
                                    data.deviceToken
                                )
                                passwordRecoveryVerifyVM.syncFcmTokenIfNeeded(data.deviceToken)
                                findNavController().navigate(
                                    PasswordRecoveryVerifyFragmentDirections.actionPasswordRecoveryVerifyFragmentToNavigationHome()
                                )
                            }
                        }
                    }

                    is Resource.Error -> {
                        errorVisible()
                        autoSubmitted = false
                        showToast(it.message!!)
                    }
                }
            }
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
        }
        updateSubmitState()
    }

    private fun errorVisible() {
        binding.apply {
            tvSubmit.setText(R.string.submit)
            pbSubmit.visibility = View.GONE
            tvSubmit.isClickable = true
        }
        updateSubmitState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        timer?.cancel()
        timer = null
    }

    private fun setCountDownTimer() {
        binding.tvGetNewCode.isClickable = false
        timer = object : CountDownTimer((waitTime * 1000).toLong(), 1000) {
            override fun onTick(p0: Long) {
                waitTime -= 1
                if (waitTime >= 0) {
                    setTimerView(waitTime)
                }
            }

            override fun onFinish() {
                binding.apply {
                    tvGetNewCode.text = getString(R.string.get_new_code)
                    tvGetNewCode.isClickable = true

                    tvGetNewCode.background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_resend_active
                    )
                    tvGetNewCode.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.text_color
                        )
                    )
                    // Cooldown over — now the other channel may be offered (a switch also
                    // re-requests a rate-limited code).
                    tvSwitchChannel.visibility = View.VISIBLE
                }
            }

        }
        (timer as CountDownTimer).start()
    }

    private fun setTimerView(current: Int) {
        val textMinute = Helper.addNolIsNeeded(current / 60)
        val textSecond = Helper.addNolIsNeeded(current % 60)

        binding.tvGetNewCode.text =
            "${getString(R.string.get_new_code)}   $textMinute : $textSecond"
    }
}