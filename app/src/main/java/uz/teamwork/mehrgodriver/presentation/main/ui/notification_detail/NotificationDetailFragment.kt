package uz.teamwork.mehrgodriver.presentation.main.ui.notification_detail

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.MediaUrl
import uz.teamwork.mehrgodriver.databinding.FragmentNotificationDetailBinding

class NotificationDetailFragment : Fragment() {
    private var _binding: FragmentNotificationDetailBinding? = null
    private val binding get() = _binding!!

    private var title: String? = null
    private var text: String? = null
    private var photoUrl: String? = null
    private var dateTime: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = arguments?.getString("title")
        text = arguments?.getString("text")
        photoUrl = arguments?.getString("photo_url")
        dateTime = arguments?.getString("date_time")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            Glide.with(requireContext()).load(MediaUrl.of(photoUrl))
                .placeholder(R.drawable.ic_launcher_foreground).into(ivImage)

            tvTitle.text = title ?: ""
            tvText.text = Html.fromHtml(text ?: "")
            tvDate.text = dateTime ?: ""

            mcvBack.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        title = null
        text = null
        photoUrl = null
        dateTime = null
    }
}