package uz.teamwork.mehrgodriver.presentation.main.ui.videos

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
import uz.teamwork.mehrgodriver.databinding.FragmentVideosBinding
import uz.teamwork.mehrgodriver.domain.model.Video
import uz.teamwork.mehrgodriver.presentation.main.adapter.VideoAdapter

@AndroidEntryPoint
class VideosFragment : Fragment(), VideoAdapter.OnVideoClickListener {
    private var _binding: FragmentVideosBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val videosViewModel: VideosViewModel by viewModels()
    private var videoAdapter: VideoAdapter? = null

    private var videoPosition: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoPosition = arguments?.getString("video_position")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideosBinding.inflate(inflater, container, false)

        videoAdapter = VideoAdapter(this@VideosFragment)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mcvBack.setOnClickListener {
            findNavController().navigateUp()
        }

        getInstructions()
    }

    private fun getInstructions() {
        lifecycleScope.launch {
            videosViewModel.getVideos().collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        screenVisible()

                        val videos = it.data?.data ?: emptyList()
                        binding.rvVideos.adapter = videoAdapter
                        videoAdapter?.submitList(videos)

                        if (videos.isEmpty()) {
                            binding.tvVideoEmpty.visibility = View.VISIBLE
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

    fun loadingVisible() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            content.visibility = View.GONE
        }
    }

    fun screenVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
    }

    fun errorVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        videoPosition = null
    }

    override fun onVideoClick(video: Video) {
        findNavController().navigate(
            VideosFragmentDirections.actionVideosFragmentToVideoFragment(
                video.url,
                video.title
            )
        )
    }
}