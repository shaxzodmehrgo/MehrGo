package uz.teamwork.mehrgodriver.presentation.main.ui.not_active_user

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.common.services.MyTrackingService
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.DialogExitProfileBinding
import uz.teamwork.mehrgodriver.databinding.FragmentNotActiveUserBinding

@AndroidEntryPoint
class NotActiveUserFragment : Fragment() {
    private var _binding: FragmentNotActiveUserBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val notActiveViewModel: NotActiveViewModel by viewModels()

    private var exitDialog: Dialog? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotActiveUserBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        getUser()

        binding.tvRefresh.setDebouncedClickListener {
            getUser()
        }

        binding.mcvExit.setDebouncedClickListener {
            showExitDialog()
        }

        // A driver under review is not attached to a branch yet, so branch.dispatcherNumber is
        // normally null HERE — this button used to just toast "no operator assigned" on the one
        // screen where they most need help. Branch dispatcher first, support line as fallback.
        binding.cvDispatcherCall.setDebouncedClickListener {
            val number = Helper.dispatcherOrSupportNumber()
            if (number != null) {
                call(number)
            } else {
                showToast(getString(R.string.not_assigned_dispatcher))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Paint the system bars the same page color so the whole top blends in.
        StatusBarHelper.applyAuthStyle(requireActivity())
    }

    private fun getUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            notActiveViewModel.getUser().collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        val data = it.data?.data
                        // Persist the latest user so the operator (dispatcher) number is current
                        // for both the displayed number and the call action — even while under review.
                        if (data != null) UserManager.saveUser(data)

                        if (data?.status?.valueNumber == Constants.DRIVER_ACTIVE) {
                            findNavController().navigate(R.id.action_notActiveUserFragment_to_navigation_home)
                        } else {
                            screenVisible()
                        }
                    }

                    is Resource.Error -> {
                        errorVisible()
                    }
                }
            }
        }
    }

    private fun call(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$phoneNumber")
        startActivity(intent)
    }

    fun loadingVisible() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            content.visibility = View.GONE
        }
    }

    fun errorVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
    }

    fun screenVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
        bindOperatorNumber()
    }

    /** Shows the operator (dispatcher) phone number from the same source the main app uses. */
    private fun bindOperatorNumber() {
        val b = _binding ?: return
        val number = UserManager.getUser()?.branch?.dispatcherNumber
        if (number.isNullOrEmpty()) {
            b.tvOperatorNumber.visibility = View.GONE
        } else {
            b.tvOperatorNumber.text = number
            b.tvOperatorNumber.visibility = View.VISIBLE
        }
    }

    /** Branded logout confirm (same dialog used in Settings/Profile) instead of the plain AlertDialog. */
    private fun showExitDialog() {
        if (exitDialog?.isShowing == true) return
        exitDialog = Dialog(requireContext())
        val dialogBinding = DialogExitProfileBinding.inflate(layoutInflater)
        exitDialog!!.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // A custom-view dialog defaults to wrap_content width and clips its text;
            // pin it to 88% of the screen so the card lays out properly.
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialogBinding.cvExit.setDebouncedClickListener {
            // MUST stop tracking BEFORE clearing the user. This was the one logout site of six
            // that did not: MyTrackingService kept running under the deleted driver's token —
            // authenticated socket still open, GPS still uploading, notification still showing.
            // The service's socket Request is built once in onCreate from UserManager.getToken(),
            // so the stale token also survived every reconnect.
            sendCommandToService(Constants.ACTION_STOP_SERVICE)
            UserManager.deleteUser()
            findNavController().navigate(R.id.action_notActiveUserFragment_to_loginFragment)
            exitDialog?.dismiss()
        }

        dialogBinding.cvCancel.setOnClickListener {
            exitDialog?.dismiss()
        }

        exitDialog?.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exitDialog?.dismiss()
        exitDialog = null
        _binding = null
    }

    /** Same shape as the other logout sites (SettingsFragment / MapSettingsFragment). */
    private fun sendCommandToService(action: String) =
        Intent(requireContext(), MyTrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }
}
