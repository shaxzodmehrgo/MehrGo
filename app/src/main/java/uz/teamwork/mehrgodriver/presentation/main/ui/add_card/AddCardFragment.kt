package uz.teamwork.mehrgodriver.presentation.main.ui.add_card

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import uz.teamwork.mehrgodriver.databinding.FragmentAddCardBinding

/**
 * Step 1 of adding a Paylov card (navigated screen, mirrors the client app): card number +
 * expiry. Registering sends an SMS OTP to the card owner's phone; step 2 confirms it.
 */
@AndroidEntryPoint
class AddCardFragment : Fragment() {
    private var _binding: FragmentAddCardBinding? = null
    private val binding get() = _binding!!

    private val addCardVM: AddCardVM by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddCardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mcvBack.setDebouncedClickListener { findNavController().navigateUp() }

        binding.etCardNumber.addTextChangedListener { updateSubmitState() }
        binding.etExpiredDate.addTextChangedListener { updateSubmitState() }
        updateSubmitState()

        binding.tvSubmit.setDebouncedClickListener { submit() }
    }

    private fun cardDigits(): String = binding.etCardNumber.unMasked
    private fun expiryDigits(): String = binding.etExpiredDate.unMasked

    private fun updateSubmitState() {
        if (_binding == null) return
        binding.tvSubmit.isEnabled = cardDigits().length == 16 && expiryDigits().length == 4
    }

    private fun submit() {
        val expiry = expiryDigits()
        val month = expiry.substring(0, 2).toIntOrNull() ?: 0
        if (month !in 1..12) {
            showToast(getString(R.string.card_expiry_hint))
            return
        }
        // Paylov expects YYMM (e.g. 2712 = Dec 2027); the field is typed as MM/YY.
        val expireDate = expiry.substring(2) + expiry.substring(0, 2)
        val cardNumber = cardDigits()

        viewLifecycleOwner.lifecycleScope.launch {
            addCardVM.createCard(cardNumber, expireDate).collect {
                when (it) {
                    is Resource.Loading -> setLoading(true)

                    is Resource.Success -> {
                        setLoading(false)
                        val cid = it.data?.data?.cid
                        if (cid == null) {
                            showToast(getString(R.string.error))
                        } else {
                            findNavController().navigate(
                                AddCardFragmentDirections
                                    .actionAddCardFragmentToAddCardConfirmFragment(
                                        cid, cardNumber, expireDate
                                    )
                                    .setPhoneNumber(it.data?.data?.otpSentPhone ?: "")
                            )
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
        b.tvSubmit.text = if (loading) "" else getString(R.string.to_continue)
        b.tvSubmit.isClickable = !loading
        b.pbSubmit.visibility = if (loading) View.VISIBLE else View.GONE
        if (!loading) updateSubmitState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
