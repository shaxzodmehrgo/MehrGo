package uz.teamwork.mehrgodriver.presentation.maps.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.FragmentNotificationsMapBinding
import uz.teamwork.mehrgodriver.domain.model.Notification
import uz.teamwork.mehrgodriver.presentation.main.adapter.NotificationAdapter

@AndroidEntryPoint
class NotificationsMapFragment : Fragment(), NotificationAdapter.OnNotificationClickListener {
    private var _binding: FragmentNotificationsMapBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val notificationsViewModel: NotificationsMapViewModel by viewModels()

    private var notificationAdapter: NotificationAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsMapBinding.inflate(inflater, container, false)

        notificationAdapter = NotificationAdapter(this@NotificationsMapFragment)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mcvBack.setOnClickListener {
            findNavController().navigateUp()
        }

        getNotifications()
    }

    private fun getNotifications() {
        lifecycleScope.launch {
            notificationsViewModel.getNotifications().collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        screenVisible()

                        val notifications = it.data?.data ?: emptyList()
                        binding.rvNotifications.adapter = notificationAdapter
                        notificationAdapter?.submitList(notifications)

                        if (notifications.isEmpty()) {
                            binding.tvNotificationsEmpty.visibility = View.VISIBLE
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        notificationAdapter = null
    }

    override fun onNotificationClick(notification: Notification) {
        findNavController().navigate(
            NotificationsMapFragmentDirections.actionNotificationsMapFragmentToNotificationDetailFragment(
                notification.message,
                notification.text,
                notification.photoUrl,
                notification.createdAt.dateTime
            )
        )
    }
}