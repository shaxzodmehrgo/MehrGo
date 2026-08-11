package uz.teamwork.mehrgodriver.presentation.auth.ui.sign_up

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
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
import androidx.core.widget.NestedScrollView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.PHONE_NUMBER_SIZE
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.common.Validate
import uz.teamwork.mehrgodriver.common.hideSystemBars
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.DialogPrivacyPolicyBinding
import uz.teamwork.mehrgodriver.databinding.FragmentSignUpBinding
import uz.teamwork.mehrgodriver.domain.model.TelegramAuthStatus

@AndroidEntryPoint
class SignUpFragment : Fragment() {
    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val signUpVM: SignUpVM by viewModels()

    // Latest Telegram-OTP availability for the entered phone (null until the status endpoint
    // answers, or if it 404s — then we keep showing Telegram, since channel=telegram is
    // SMS-safe). `linked` decides whether tapping Telegram needs the bot first.
    private var telegramStatus: TelegramAuthStatus? = null
    private var telegramStatusPhone: String? = null

    // Set when we send the driver to the bot to link; onResume re-checks and, if now linked,
    // registers with channel=telegram (no premature SMS).
    private var pendingTelegramLink = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
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
            val watcher: () -> Unit = { updateSubmitState() }
            etPhoneNumber.addTextChangedListener {
                watcher()
                // Warm up `linked`/`botUrl` for the completed number so the Telegram tap
                // can skip the bot round-trip when already linked.
                if (etPhoneNumber.unMasked.length >= PHONE_NUMBER_SIZE - 1) {
                    fetchTelegramStatus(currentFormattedPhone())
                }
            }
            etFirstName.addTextChangedListener { watcher() }
            etFatherName.addTextChangedListener { watcher() }
            etLastName.addTextChangedListener { watcher() }
            etPassword.addTextChangedListener { watcher() }
            etConfirmPassword.addTextChangedListener { watcher() }
            chbTermOfUse.setOnCheckedChangeListener { _, _ -> watcher() }
        }
        updateSubmitState()
    }

    private fun updateSubmitState() {
        if (_binding == null) return
        binding.apply {
            val phoneOk = etPhoneNumber.unMasked.length >= PHONE_NUMBER_SIZE - 1
            val firstNameOk = etFirstName.text.toString().isNotBlank()
            val fatherNameOk = etFatherName.text.toString().isNotBlank()
            val lastNameOk = etLastName.text.toString().isNotBlank()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()
            val passwordOk = password.length > 6 && password == confirmPassword
            val termsOk = chbTermOfUse.isChecked
            tvSubmit.isEnabled =
                phoneOk && firstNameOk && fatherNameOk && lastNameOk && passwordOk && termsOk
        }
    }

    override fun onResume() {
        super.onResume()
        StatusBarHelper.applyAuthStyle(requireActivity())

        // Returned from the bot after being asked to link — re-check and, if now linked,
        // register via Telegram (don't re-open the bot). Only when the form is still complete.
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

            // Same form validation as the primary button — only the delivery channel differs.
            flTelegramSignUp.setDebouncedClickListener { submit(Constants.CHANNEL_TELEGRAM) }

            chbTermOfUse.setDebouncedClickListener {
                if (chbTermOfUse.isChecked) {
                    chbTermOfUse.isChecked = false
                    getPrivacyPolicy()
                }
            }

            tvTermsOfUse.setDebouncedClickListener {
                getPrivacyPolicy()
            }

            flAsk.setDebouncedClickListener {
                findNavController().navigate(
                    SignUpFragmentDirections.actionSignUpFragmentToInstructionFragment(
                        Constants.VIDEO_SIGN_UP
                    )
                )
            }

            ivPasswordToggle.setOnClickListener {
                togglePasswordVisibility(etPassword, ivPasswordToggle)
            }

            ivConfirmPasswordToggle.setOnClickListener {
                togglePasswordVisibility(etConfirmPassword, ivConfirmPasswordToggle)
            }

            ivBack.setDebouncedClickListener { findNavController().navigateUp() }
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

    // Shared entry for both CTAs: same form validation, only the delivery channel differs.
    private fun submit(channel: String) {
        if (_binding == null) return
        val phoneNumber = "+${binding.etPhoneNumber.unMasked}"
        val firstName = binding.etFirstName.text.toString()
        val fatherName = binding.etFatherName.text.toString()
        val lastName = binding.etLastName.text.toString()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        val validate = Validate.signUp(
            phoneNumber,
            firstName,
            fatherName,
            lastName,
            password,
            confirmPassword,
            requireContext()
        )
        if (!validate.valid) {
            Toast.makeText(requireContext(), validate.message, Toast.LENGTH_SHORT).show()
            return
        }
        if (!binding.chbTermOfUse.isChecked) {
            Toast.makeText(
                requireContext(), getString(R.string.let_privacy_policy), Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (channel == Constants.CHANNEL_TELEGRAM) {
            // Don't fire the code before the number is linked, or the backend SMS-falls-back.
            requestTelegramCode(currentFormattedPhone(), allowBotOpen = true)
        } else {
            sendRequest(Constants.CHANNEL_SMS)
        }
    }

    private fun currentFormattedPhone(): String =
        Helper.formatPhoneNumber("+${binding.etPhoneNumber.unMasked}")

    /**
     * Telegram code request gated on link status: linked → register now (code arrives in
     * Telegram); not-linked → open the bot to link, deferring the send to onResume so no
     * premature SMS goes out; status failure → best-effort register (backend SMS-falls-back).
     */
    private fun requestTelegramCode(phone: String, allowBotOpen: Boolean) {
        setTelegramLoading(true)
        telegramStatusPhone = null
        viewLifecycleOwner.lifecycleScope.launch {
            signUpVM.telegramAuthStatus(phone).collect {
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
        val firstName = binding.etFirstName.text.toString()
        val fatherName = binding.etFatherName.text.toString()
        val lastName = binding.etLastName.text.toString()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        val telegram = channel == Constants.CHANNEL_TELEGRAM

        // View-scoped: a late emit after onDestroyView would hit the null binding (NPE).
        viewLifecycleOwner.lifecycleScope.launch {
            signUpVM.signUp(
                phoneNumber, firstName, fatherName, lastName, password, confirmPassword, channel
            ).collect {
                when (it) {
                    is Resource.Loading -> {
                        if (telegram) setTelegramLoading(true) else loadingVisible()
                    }

                    is Resource.Success -> {
                        val authKey = it.data?.data?.authKey!!
                        findNavController().navigate(
                            SignUpFragmentDirections
                                .actionSignUpFragmentToSignUpVerifyFragment(authKey)
                                .setPhoneNumber(phoneNumber)
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
            signUpVM.telegramAuthStatus(phone).collect {
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

    // Opens the OTP bot so the driver can link via the bot's "Share contact". Prefers the
    // backend bot_url; falls back to the plain username. No-op when the brand has no bot
    // configured or Telegram isn't installed.
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

    private fun getPrivacyPolicy() {
        // View-scoped: a fragment-scope collect outlives onDestroyView, and the late emit
        // then hits binding (=_binding!!) → NPE (prod crash). The view exists at launch
        // (click-triggered), so cancel the collect with the view.
        viewLifecycleOwner.lifecycleScope.launch {
            signUpVM.termsOfUse().collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        screenVisible()
                        showPrivacyPolicyDialog(it.data?.data?.text ?: getString(R.string.no_data))
                    }

                    is Resource.Error -> {
                        errorVisible()
                        showToast(it.message ?: "")
                    }
                }
            }
        }
    }

    private fun showPrivacyPolicyDialog(text: String) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogPrivacyPolicyBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Apply navigation-bar inset as bottom padding so the accept button isn't clipped.
        ViewCompat.setOnApplyWindowInsetsListener(dialogBinding.dialogRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }

        // Force the sheet to ~92% of the screen height and lock it expanded.
        val sheetHeight = (Resources.getSystem().displayMetrics.heightPixels * 0.92).toInt()
        dialog.setOnShowListener {
            dialog.hideSystemBars()
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.layoutParams.height = sheetHeight
            sheet.requestLayout()
            BottomSheetBehavior.from(sheet).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                peekHeight = sheetHeight
            }
        }

        dialogBinding.tvText.text = text

        var reachedEnd = false

        fun enableAccept() {
            if (reachedEnd) return
            reachedEnd = true
            dialogBinding.mcvClose.alpha = 1f
            dialogBinding.mcvClose.isEnabled = true
            dialogBinding.tvCloseLabel.text = getString(R.string.i_agree)
            dialogBinding.mcvScrollDown.visibility = View.GONE
        }

        fun isAtBottom(scrollY: Int): Boolean {
            val nsv = dialogBinding.nsvPrivacyPolicy
            val child = nsv.getChildAt(0) ?: return false
            val diff = child.bottom - (nsv.height + scrollY)
            return diff <= 0
        }

        dialogBinding.nsvPrivacyPolicy.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                if (isAtBottom(scrollY)) enableAccept()
            }
        )

        // Content might be short enough that no scrolling is needed — enable immediately in that case.
        dialogBinding.nsvPrivacyPolicy.post {
            if (isAtBottom(dialogBinding.nsvPrivacyPolicy.scrollY)) enableAccept()
        }

        dialogBinding.mcvScrollDown.setOnClickListener {
            val nsv = dialogBinding.nsvPrivacyPolicy
            val child = nsv.getChildAt(0) ?: return@setOnClickListener
            nsv.smoothScrollTo(0, child.bottom - nsv.height)
        }

        dialogBinding.mcvClose.setOnClickListener {
            if (!reachedEnd) return@setOnClickListener
            binding.chbTermOfUse.isChecked = true
            dialog.dismiss()
        }

        dialog.show()
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
