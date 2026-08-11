package uz.teamwork.mehrgodriver.presentation.main.ui.video

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import uz.teamwork.mehrgodriver.common.YoutubeHelper
import uz.teamwork.mehrgodriver.databinding.FragmentVideoBinding

class VideoFragment : Fragment() {
    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!

    private var title: String? = null
    private var videoUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = arguments?.getString("title")
        videoUrl = arguments?.getString("video_url")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mcvBack.setOnClickListener {
            findNavController().navigateUp()
        }

        playVideo()
    }

    // The embedded IFrame player is dead — YouTube rejects WebView embeds with an
    // instant UNKNOWN error on every video (verified with an embedding-enabled demo
    // video and the newest player library). Poster + watch-page dialog instead.
    private fun playVideo() {
        binding.tvTitle.text = title
        val videoId = YoutubeHelper.extractId(videoUrl!!)

        Glide.with(this)
            .load("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
            .into(binding.ivPoster)

        // Both the poster and the button hand off to the YouTube app (browser if absent).
        // In-app playback is not an option: the IFrame embed is blocked outright and the
        // WebView watch page didn't play either (both verified on-device 2026-07-20).
        val openInYoutube = View.OnClickListener {
            YoutubeHelper.openExternally(requireContext(), videoId)
        }
        binding.cvPoster.setOnClickListener(openInYoutube)
        binding.btnWatchOnYoutube.setOnClickListener(openInYoutube)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        videoUrl = null
        title = null
    }
}