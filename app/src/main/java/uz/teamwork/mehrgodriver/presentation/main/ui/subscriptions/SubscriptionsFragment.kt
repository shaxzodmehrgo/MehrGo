package uz.teamwork.mehrgodriver.presentation.main.ui.subscriptions

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.DialogSubscribeBinding
import uz.teamwork.mehrgodriver.databinding.FragmentSubscriptionsBinding
import uz.teamwork.mehrgodriver.presentation.main.adapter.SubscriptionAdapter

@AndroidEntryPoint
class SubscriptionsFragment : Fragment(), SubscriptionAdapter.OnSubscriptionClickListener {
    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private var _dialogSubscribeBinding: DialogSubscribeBinding? = null
    private val dialogSubscribeBinding get() = _dialogSubscribeBinding!!
    private var dialogSubscribe: Dialog? = null

    // For ViewModel
    private val subscriptionsViewModel: SubscriptionsViewModel by viewModels()

    private var subscriptionAdapter: SubscriptionAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadData()

        binding.mcvBack.setDebouncedClickListener {
            findNavController().navigateUp()
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            subscriptionsViewModel.getSubscriptions().collectLatest { ordersHistory ->
                subscriptionAdapter = SubscriptionAdapter(this@SubscriptionsFragment)
                binding.rvSubscriptions.adapter = subscriptionAdapter
                subscriptionAdapter?.submitData(ordersHistory)
            }
        }

        subscriptionAdapter?.addLoadStateListener { loadState ->
            when (loadState.source.refresh) {
                is LoadState.NotLoading -> {
                    screenVisible()
                }

                is LoadState.Loading -> {
                    loadingVisible()
                }

                is LoadState.Error -> {
                    errorVisible()
                }
            }

            if (loadState.append.endOfPaginationReached) {
                if (_binding != null) {
                    if ((subscriptionAdapter?.itemCount ?: 0) < 1) {
                        binding.tvSubscriptionsEmpty.visibility = View.VISIBLE
                    } else {
                        binding.tvSubscriptionsEmpty.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun showDialogSubscribe(id: Int) {
        if (dialogSubscribe?.isShowing == true) return
        dialogSubscribe = Dialog(requireContext())
        _dialogSubscribeBinding = DialogSubscribeBinding.inflate(layoutInflater)
        dialogSubscribe!!.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogSubscribeBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }


        dialogSubscribeBinding.cvExit.setDebouncedClickListener {
            purchaseSubscribe(id)
            dialogSubscribe?.dismiss()
        }

        dialogSubscribeBinding.cvCancel.setOnClickListener {
            dialogSubscribe?.dismiss()
        }

        dialogSubscribe?.show()
    }


    private fun purchaseSubscribe(id: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            subscriptionsViewModel.purchaseSubscription(id).collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        loadData()
                    }

                    is Resource.Error -> {
                        showToast(it.message!!)
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
            tvSubscriptionsEmpty.visibility = View.GONE
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Dismiss before releasing, or a dialog left on screen at teardown leaks its window.
        dialogSubscribe?.dismiss()
        _dialogSubscribeBinding = null
        dialogSubscribe = null
        subscriptionAdapter = null
    }

    override fun onSubscriptionClick(id: Int) {
        showDialogSubscribe(id)
    }
}