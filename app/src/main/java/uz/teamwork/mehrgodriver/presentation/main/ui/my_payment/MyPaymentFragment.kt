package uz.teamwork.mehrgodriver.presentation.main.ui.my_payment

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
import uz.teamwork.mehrgodriver.common.Constants.MERCHANT_ID_CLICK
import uz.teamwork.mehrgodriver.common.Constants.MERCHANT_ID_PAY_ME
import uz.teamwork.mehrgodriver.common.Constants.MERCHANT_SERVICE_ID_CLICK
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.InfoPopup
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.hideSystemBars
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.DialogTopUpBinding
import uz.teamwork.mehrgodriver.databinding.DialogWithdrawBinding
import uz.teamwork.mehrgodriver.databinding.FragmentMyPaymentBinding
import uz.teamwork.mehrgodriver.databinding.ItemPaylovCardBinding
import uz.teamwork.mehrgodriver.domain.model.paylov.PaylovCard
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalInfo
import uz.teamwork.mehrgodriver.domain.model.paylov.WithdrawalRequest

@AndroidEntryPoint
class MyPaymentFragment : Fragment() {
    private var _binding: FragmentMyPaymentBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val myPaymentViewModel: MyPaymentViewModel by viewModels()

    // Paylov state: saved cards + withdrawal info (limits / withdrawable part / pending request).
    private var cards: List<PaylovCard> = emptyList()
    private var withdrawInfo: WithdrawalInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setView()

        binding.mcvBack.setDebouncedClickListener {
            findNavController().navigateUp()
        }
        binding.mcvRefresh.setDebouncedClickListener {
            getUser()
            loadPaylov()
        }
        binding.btnTopUp.setDebouncedClickListener {
            showTopUpSheet()
        }
        binding.btnWithdraw.setDebouncedClickListener {
            showWithdrawSheet()
        }
        binding.llAddCard.setDebouncedClickListener {
            // Navigated add-card flow (2 screens, like the client app) — not a sheet.
            findNavController().navigate(
                MyPaymentFragmentDirections.actionMyPaymentFragmentToAddCardFragment()
            )
        }
        binding.btnCancelWithdrawal.setDebouncedClickListener {
            cancelPendingWithdrawal()
        }
        binding.rowWithdrawalHistory.setDebouncedClickListener {
            findNavController().navigate(
                MyPaymentFragmentDirections.actionMyPaymentFragmentToPaylovHistoryFragment()
                    .setTab("requests")
            )
        }
        binding.rowBalanceHistory.setDebouncedClickListener {
            findNavController().navigate(
                MyPaymentFragmentDirections.actionMyPaymentFragmentToPaylovHistoryFragment()
                    .setTab("balance")
            )
        }

        getUser()
        loadPaylov()
    }

    /** iOS top-up sheet: amount field + quick-pick chips + Click/PayMe tiles. */
    private fun showTopUpSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val b = DialogTopUpBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        // Keep the sheet (Click/PayMe tiles) above the soft keyboard: resize the
        // dialog window and pad the content by the IME / nav-bar inset.
        sheet.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val basePaddingBottom = b.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.updatePadding(bottom = basePaddingBottom + maxOf(bars, ime))
            insets
        }

        fun currentAmount(): Long =
            b.etSumma.text.toString().filter { it.isDigit() }.toLongOrNull() ?: 0L

        fun updateTiles() {
            val enabled = currentAmount() > 0
            val a = if (enabled) 1f else 0.5f
            b.cvClickTile.alpha = a
            b.cvPayMeTile.alpha = a
            b.cvClickTile.isClickable = enabled
            b.cvPayMeTile.isClickable = enabled
        }

        updateTiles()

        // Live thousand-separator formatting while typing, e.g. 100000 -> 100 000.
        var formatting = false
        b.etSumma.addTextChangedListener(afterTextChanged = { editable ->
            if (!formatting && editable != null) {
                formatting = true

                val digits = editable.toString().filter { it.isDigit() }.trimStart('0')
                val formatted = if (digits.isEmpty()) "" else Helper.formatPrice(digits).trim()

                // Keep the caret after the same number of digits it sat behind before.
                val cursor = b.etSumma.selectionStart.coerceIn(0, editable.length)
                val digitsBeforeCursor = editable.subSequence(0, cursor).count { it.isDigit() }

                b.etSumma.setText(formatted)

                var newCursor = 0
                var seen = 0
                while (newCursor < formatted.length && seen < digitsBeforeCursor) {
                    if (formatted[newCursor].isDigit()) seen++
                    newCursor++
                }
                b.etSumma.setSelection(newCursor)

                formatting = false
                updateTiles()
            }
        })

        val setAmount: (String) -> Unit = { value ->
            b.etSumma.setText(value)
            b.etSumma.setSelection(b.etSumma.text.length)
        }
        b.chip50.setOnClickListener { setAmount("50000") }
        b.chip100.setOnClickListener { setAmount("100000") }
        b.chip200.setOnClickListener { setAmount("200000") }
        b.chip500.setOnClickListener { setAmount("500000") }

        b.cvClickTile.setDebouncedClickListener {
            val amount = currentAmount()
            if (amount > 0) {
                paymentByClick(amount)
                sheet.dismiss()
            }
        }
        b.cvPayMeTile.setDebouncedClickListener {
            val amount = currentAmount()
            if (amount > 0) {
                paymentByPayMe(amount)
                sheet.dismiss()
            }
        }

        sheet.setOnShowListener { sheet.hideSystemBars() }
        sheet.show()
    }

    private fun paymentByClick(summa: Long) {
        val merchantTransId = UserManager.getUserId()
        val url =
            "https://my.click.uz/services/pay/?service_id=${MERCHANT_SERVICE_ID_CLICK}&merchant_id=${MERCHANT_ID_CLICK}&amount=$summa&transaction_param=${merchantTransId}"
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        startActivity(i)
    }

    private fun paymentByPayMe(summa: Long) {
        val newSumma = summa * 100

        val userId = UserManager.getUserId()
        val url =
            "https://payme.uz/fallback/merchant/?id=${MERCHANT_ID_PAY_ME}&userid=${userId}&amount=$newSumma"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun setView() {
        binding.tvBalance.text = Helper.formatPrice(UserManager.getBalance().toString())
    }

    private fun getUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            myPaymentViewModel.getUser().collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        val data = it.data?.data!!

                        screenVisible()
                        UserManager.saveUser(data)

                        setView()
                    }

                    is Resource.Error -> {
                        errorVisible()
                    }
                }
            }
        }
    }

    private fun loadingVisible() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            content.visibility = View.GONE
        }
    }

    private fun screenVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
    }

    private fun errorVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
    }

    // region Paylov: cards + withdrawal (MOBILE.md)

    private fun fmt(value: Long?): String = Helper.formatPrice((value ?: 0L).toString()).trim()

    /** Parallel load of the saved cards + withdrawal info. Both tolerate a missing backend
     *  (404/error → the section just stays in its empty state, nothing crashes). */
    private fun loadPaylov() {
        viewLifecycleOwner.lifecycleScope.launch {
            myPaymentViewModel.getCards().collect {
                when (it) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {
                        cards = it.data?.data?.cards ?: emptyList()
                        renderCards()
                    }

                    is Resource.Error -> renderCards()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            myPaymentViewModel.withdrawalInfo().collect {
                when (it) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {
                        withdrawInfo = it.data?.data
                        renderWithdrawInfo()
                    }

                    is Resource.Error -> {}
                }
            }
        }
    }

    private fun renderCards() {
        val b = _binding ?: return
        b.llCards.removeAllViews()
        b.tvNoCards.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
        cards.forEachIndexed { index, card ->
            val row = ItemPaylovCardBinding.inflate(layoutInflater, b.llCards, false)
            row.tvCardNumber.text = card.number ?: ""
            row.tvBankName.text = card.bankName ?: card.vendor ?: ""
            row.tvBankName.visibility =
                if (row.tvBankName.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            row.ivSelected.visibility = View.GONE
            row.ivDeleteCard.setDebouncedClickListener {
                val cardId = card.cardId ?: return@setDebouncedClickListener
                InfoPopup.confirm(
                    context = requireContext(),
                    title = getString(R.string.delete_card_q),
                    message = card.number,
                    yesText = getString(R.string.yes),
                    noText = getString(R.string.no),
                    lifecycle = lifecycle,
                ) { loader -> deleteCard(cardId, loader) }
            }
            if (index > 0) {
                (row.root.layoutParams as? LinearLayout.LayoutParams)?.topMargin =
                    (8 * resources.displayMetrics.density).toInt()
            }
            b.llCards.addView(row.root)
        }
    }

    private fun renderWithdrawInfo() {
        val b = _binding ?: return
        val info = withdrawInfo ?: return
        b.tvWithdrawable.text =
            getString(R.string.withdrawable_amount, fmt(info.withdrawableBalance))
        b.tvWithdrawable.visibility = View.VISIBLE

        val bonus = info.bonusLeft ?: 0L
        val minLeft = info.minBalanceLeft ?: 0L
        val noteParts = mutableListOf<String>()
        if (bonus > 0) noteParts += getString(R.string.bonus_not_withdrawable, fmt(bonus))
        if (minLeft > 0) noteParts += getString(R.string.min_balance_note, fmt(minLeft))
        if (noteParts.isNotEmpty()) {
            b.tvBonusNote.text = noteParts.joinToString("\n")
            b.tvBonusNote.visibility = View.VISIBLE
        } else {
            b.tvBonusNote.visibility = View.GONE
        }

        val pending = info.pendingRequest
        if (pending != null) {
            b.cvPendingRequest.visibility = View.VISIBLE
            b.tvPendingInfo.text = pendingText(pending)
            b.btnWithdraw.alpha = 0.6f
        } else {
            b.cvPendingRequest.visibility = View.GONE
            b.btnWithdraw.alpha = 1f
        }
    }

    private fun pendingText(p: WithdrawalRequest): String =
        "${fmt(p.amount)} ${getString(R.string.sum)} · ${p.cardNumber ?: ""}"

    private fun deleteCard(cardId: String, loader: InfoPopup.Loader) {
        viewLifecycleOwner.lifecycleScope.launch {
            myPaymentViewModel.deleteCard(cardId).collect {
                when (it) {
                    // In-button spinner on the confirm dialog's "Ha" until the backend responds.
                    is Resource.Loading -> loader.setLoading(true)
                    is Resource.Success -> {
                        loader.dismiss()
                        loadPaylov()
                    }

                    is Resource.Error -> {
                        // Restore the button (retry stays possible) + show a friendly message.
                        loader.setLoading(false)
                        showToast(Helper.humanizeServerError(it.message))
                    }
                }
            }
        }
    }

    /** One active request per driver — cancelling frees the slot for a new one. */
    private fun cancelPendingWithdrawal() {
        val id = withdrawInfo?.pendingRequest?.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            myPaymentViewModel.cancelWithdrawal(id).collect {
                when (it) {
                    is Resource.Loading -> loadingVisible()
                    is Resource.Success -> {
                        screenVisible()
                        showToast(getString(R.string.withdrawal_cancelled))
                        loadPaylov()
                    }

                    is Resource.Error -> {
                        screenVisible()
                        showToast(it.message!!)
                    }
                }
            }
        }
    }

    /** In-button spinner for the sheet CTAs (same pattern as the auth screens). */
    private fun setSheetButtonLoading(
        button: TextView,
        progress: ProgressBar,
        loading: Boolean,
        textRes: Int
    ) {
        button.text = if (loading) "" else getString(textRes)
        button.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    /** Live thousand-separator formatting (100000 → 100 000) shared by the amount fields. */
    private fun attachAmountFormatting(field: EditText, onChanged: () -> Unit) {
        var formatting = false
        field.addTextChangedListener(afterTextChanged = { editable ->
            if (!formatting && editable != null) {
                formatting = true
                val digits = editable.toString().filter { it.isDigit() }.trimStart('0')
                val formatted = if (digits.isEmpty()) "" else Helper.formatPrice(digits).trim()
                if (formatted != editable.toString()) {
                    field.setText(formatted)
                    field.setSelection(formatted.length)
                }
                formatting = false
                onChanged()
            }
        })
    }

    /** Withdrawal request: pick a card, enter an amount within the limits, submit. */
    private fun showWithdrawSheet() {
        val info = withdrawInfo
        val pending = info?.pendingRequest
        if (pending != null) {
            // One active request per driver — show it instead of a second form.
            InfoPopup.show(
                context = requireContext(),
                title = getString(R.string.pending_request),
                message = pendingText(pending),
                lifecycle = lifecycle
            )
            return
        }
        if (cards.isEmpty()) {
            showToast(getString(R.string.no_cards_yet))
            return
        }

        val sheet = BottomSheetDialog(requireContext())
        val b = DialogWithdrawBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)
        sheet.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // Open fully expanded (and never fall back to a half/peek detent): otherwise, when the
        // keyboard raises to type the amount, the sheet stays low and the "Pul chiqarish" button +
        // the last limit line hide behind the keyboard. Expanded keeps the whole form above it.
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        val basePaddingBottom = b.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.updatePadding(bottom = basePaddingBottom + maxOf(bars, ime))
            insets
        }

        val withdrawable = info?.withdrawableBalance ?: 0L
        val minAmount = info?.minAmount ?: 0L
        val maxAmount = info?.maxAmount ?: 0L
        val dailyLimit = info?.dailyLimit ?: 0L
        val todayWithdrawn = info?.todayWithdrawn ?: 0L
        val minBalanceLeft = info?.minBalanceLeft ?: 0L

        // Input maximum per spec §2.1: min(withdrawable, max_amount if set, daily remainder
        // if a daily limit is set). 0 limit = no limit.
        val dailyRemainder =
            if (dailyLimit > 0) (dailyLimit - todayWithdrawn).coerceAtLeast(0L) else Long.MAX_VALUE
        val effectiveMax = minOf(
            withdrawable,
            if (maxAmount > 0) maxAmount else Long.MAX_VALUE,
            dailyRemainder
        )

        b.tvSheetWithdrawable.text = getString(R.string.withdrawable_amount, fmt(withdrawable))
        val limitLines = mutableListOf<String>()
        if (minAmount > 0) limitLines += getString(R.string.min_withdraw_amount, fmt(minAmount))
        // Per-transaction cap: was enforced (in effectiveMax) but never shown, so a driver hitting
        // it saw the button silently disable with no clue why. Surface it.
        if (maxAmount > 0) limitLines += getString(R.string.max_withdraw_amount, fmt(maxAmount))
        if (dailyLimit > 0) {
            limitLines += getString(
                R.string.daily_limit_today,
                fmt(dailyLimit),
                fmt(todayWithdrawn)
            )
        }
        if (minBalanceLeft > 0) {
            limitLines += getString(R.string.min_balance_note, fmt(minBalanceLeft))
        }
        if (limitLines.isNotEmpty()) {
            b.tvWithdrawLimits.text = limitLines.joinToString("\n")
            b.tvWithdrawLimits.visibility = View.VISIBLE
        }

        var selectedCardId: String? = cards.firstOrNull()?.cardId

        fun currentAmount(): Long =
            b.etWithdrawAmount.text.toString().filter { it.isDigit() }.toLongOrNull() ?: 0L

        fun updateSubmit() {
            val amount = currentAmount()
            // Spell out WHY an amount is rejected instead of silently greying the button. Order
            // matters: report the first bound the amount actually breaks.
            val reason: String? = when {
                amount <= 0 -> null // nothing entered yet — neutral, no error
                minAmount > 0 && amount < minAmount ->
                    getString(R.string.min_withdraw_amount, fmt(minAmount))

                amount > withdrawable ->
                    getString(R.string.withdrawable_amount, fmt(withdrawable))

                maxAmount > 0 && amount > maxAmount ->
                    getString(R.string.max_withdraw_amount, fmt(maxAmount))

                dailyLimit > 0 && amount > dailyRemainder ->
                    getString(R.string.withdraw_over_daily, fmt(dailyRemainder))

                else -> null
            }
            b.tvWithdrawError.text = reason ?: ""
            b.tvWithdrawError.visibility = if (reason != null) View.VISIBLE else View.GONE
            b.btnWithdrawSubmit.isEnabled =
                amount > 0 && reason == null && selectedCardId != null
        }

        fun renderSheetCards() {
            b.llSheetCards.removeAllViews()
            cards.forEachIndexed { index, card ->
                val row = ItemPaylovCardBinding.inflate(layoutInflater, b.llSheetCards, false)
                row.tvCardNumber.text = card.number ?: ""
                row.tvBankName.text = card.bankName ?: card.vendor ?: ""
                row.tvBankName.visibility =
                    if (row.tvBankName.text.isNullOrEmpty()) View.GONE else View.VISIBLE
                row.ivDeleteCard.visibility = View.GONE
                row.ivSelected.visibility =
                    if (card.cardId == selectedCardId) View.VISIBLE else View.GONE
                row.llCardRoot.setOnClickListener {
                    selectedCardId = card.cardId
                    renderSheetCards()
                    updateSubmit()
                }
                if (index > 0) {
                    (row.root.layoutParams as? LinearLayout.LayoutParams)?.topMargin =
                        (6 * resources.displayMetrics.density).toInt()
                }
                b.llSheetCards.addView(row.root)
            }
        }
        renderSheetCards()
        attachAmountFormatting(b.etWithdrawAmount) { updateSubmit() }

        b.btnWithdrawSubmit.setDebouncedClickListener {
            val cardId = selectedCardId ?: return@setDebouncedClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                myPaymentViewModel.createWithdrawal(cardId, currentAmount()).collect {
                    when (it) {
                        is Resource.Loading ->
                            setSheetButtonLoading(
                                b.btnWithdrawSubmit, b.pbWithdrawSubmit, true,
                                R.string.withdraw_money
                            )

                        is Resource.Success -> {
                            sheet.dismiss()
                            showToast(getString(R.string.withdraw_request_sent))
                            loadPaylov()
                        }

                        is Resource.Error -> {
                            setSheetButtonLoading(
                                b.btnWithdrawSubmit, b.pbWithdrawSubmit, false,
                                R.string.withdraw_money
                            )
                            showToast(it.message!!)
                        }
                    }
                }
            }
        }

        sheet.setOnShowListener { sheet.hideSystemBars() }
        sheet.show()
    }

    // endregion

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
