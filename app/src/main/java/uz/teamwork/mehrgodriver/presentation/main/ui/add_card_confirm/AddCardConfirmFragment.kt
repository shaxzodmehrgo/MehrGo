package uz.teamwork.mehrgodriver.presentation.main.ui.add_card_confirm

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.FragmentAddCardConfirmBinding

/**
 * Step 2 of adding a Paylov card: the SMS OTP sent to the card owner's phone. Resend
 * re-registers the same card (fresh cardId + OTP) behind a 60s cooldown.
 */
@AndroidEntryPoint
class AddCardConfirmFragment : Fragment() {
    private var _binding: FragmentAddCardConfirmBinding? = null
    private val binding get() = _binding!!

    private val confirmVM: AddCardConfirmVM by viewModels()

    private var cardId: String? = null
    private var phoneNumber: String = ""
    private var cardNumber: String = ""
    private var expiredDate: String = ""

    private var timer: CountDownTimer? = null
    private var waitTime = RESEND_WAIT_SECONDS
    private var resendClickable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cardId = arguments?.getString("card_id")
        phoneNumber = arguments?.getString("phone_number") ?: ""
        cardNumber = arguments?.getString("card_number") ?: ""
        expiredDate = arguments?.getString("expired_date") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddCardConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mcvBack.setDebouncedClickListener { findNavController().navigateUp() }

        if (phoneNumber.isNotBlank()) {
            binding.tvPhoneNumber.text = phoneNumber
            binding.tvPhoneNumber.visibility = View.VISIBLE
        }

        // OTP boxes (same interaction as the registration verify screen): the real input is the
        // invisible EditText, the cells are only a rendering of it. Tapping anywhere on the row
        // focuses that field and raises the keyboard.
        binding.otpContainer.setOnClickListener { focusOtp() }
        binding.otpRow.setOnClickListener { focusOtp() }
        binding.otpContainer.post { focusOtp() }

        binding.etCardOtp.addTextChangedListener {
            renderOtpCells()
            binding.tvSubmit.isEnabled = (it?.length ?: 0) >= 4
        }
        renderOtpCells()

        binding.tvSubmit.setDebouncedClickListener { confirm() }
        binding.tvGetNewCode.setOnClickListener { if (resendClickable) resend() }

        setCountDownTimer()
    }

    private fun focusOtp() {
        if (_binding == null) return
        binding.etCardOtp.requestFocus()
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etCardOtp, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Paints the six cells from the hidden field: filled digits, then the caret cell, then empty. */
    private fun renderOtpCells() {
        if (_binding == null) return
        val code = binding.etCardOtp.text.toString()
        val cells = listOf(
            binding.otpCell0,
            binding.otpCell1,
            binding.otpCell2,
            binding.otpCell3,
            binding.otpCell4,
            binding.otpCell5
        )
        cells.forEachIndexed { index, cell ->
            val filled = index < code.length
            cell.text = if (filled) code[index].toString() else ""
            cell.setBackgroundResource(
                when {
                    filled -> R.drawable.bg_otp_filled
                    index == code.length -> R.drawable.bg_otp_active
                    else -> R.drawable.bg_otp_empty
                }
            )
        }
    }

    private fun confirm() {
        val cid = cardId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            confirmVM.confirmCard(cid, binding.etCardOtp.text.toString()).collect {
                when (it) {
                    is Resource.Loading -> setLoading(true)

                    is Resource.Success -> {
                        showToast(getString(R.string.card_added))
                        // Back to the Hisob screen — its onViewCreated reloads the cards.
                        findNavController().popBackStack(R.id.myPaymentFragment, false)
                    }

                    is Resource.Error -> {
                        setLoading(false)
                        showToast(Helper.humanizeServerError(it.message))
                    }
                }
            }
        }
    }

    private fun resend() {
        viewLifecycleOwner.lifecycleScope.launch {
            confirmVM.resend(cardNumber, expiredDate).collect {
                when (it) {
                    is Resource.Loading -> setLoading(true)

                    is Resource.Success -> {
                        setLoading(false)
                        val cid = it.data?.data?.cid
                        if (cid == null) {
                            showToast(getString(R.string.error))
                        } else {
                            cardId = cid
                            binding.etCardOtp.setText("")
                            waitTime = RESEND_WAIT_SECONDS
                            setCountDownTimer()
                        }
                    }

                    is Resource.Error -> {
                        setLoading(false)
                        showToast(Helper.humanizeServerError(it.message))
                    }
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        val b = _binding ?: return
        b.tvSubmit.text = if (loading) "" else getString(R.string.submit)
        b.tvSubmit.isClickable = !loading
        b.pbSubmit.visibility = if (loading) View.VISIBLE else View.GONE
        if (!loading) b.tvSubmit.isEnabled = b.etCardOtp.text.toString().length >= 4
    }

    private fun setCountDownTimer() {
        resendClickable = false
        binding.tvGetNewCode.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.gray_full)
        )
        timer?.cancel()
        timer = object : CountDownTimer((waitTime * 1000).toLong(), 1000) {
            override fun onTick(p0: Long) {
                waitTime -= 1
                if (waitTime >= 0) setTimerView(waitTime)
            }

            override fun onFinish() {
                resendClickable = true
                val b = _binding ?: return
                b.tvGetNewCode.text = getString(R.string.get_new_verify_code)
                b.tvGetNewCode.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.app_color)
                )
            }
        }.also { it.start() }
    }

    @SuppressLint("SetTextI18n")
    private fun setTimerView(current: Int) {
        val b = _binding ?: return
        val minute = Helper.addNolIsNeeded(current / 60)
        val second = Helper.addNolIsNeeded(current % 60)
        b.tvGetNewCode.text = "${getString(R.string.get_new_verify_code)}  $minute : $second"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        timer = null
        _binding = null
    }

    companion object {
        private const val RESEND_WAIT_SECONDS = 60
    }
}
