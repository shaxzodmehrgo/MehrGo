package uz.teamwork.mehrgodriver.presentation.main.ui.instuction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.common.YoutubeHelper
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.FragmentInstructionBinding
import uz.teamwork.mehrgodriver.domain.model.Instruction

@AndroidEntryPoint
class InstructionFragment : Fragment() {
    private var _binding: FragmentInstructionBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val instructionViewModel: InstructionViewModel by viewModels()

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
        _binding = FragmentInstructionBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        getInstructions()
    }

    override fun onResume() {
        super.onResume()
        StatusBarHelper.applyAuthStyle(requireActivity())
    }

    private fun getInstructions() {
        lifecycleScope.launch {
            instructionViewModel.getInstruction().collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        screenVisible()

                        val instructions = it.data?.data ?: emptyList()
                        for (item in instructions) {
                            if (item.position == videoPosition) {
                                playVideo(item)
                                break
                            }
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

    // The embedded IFrame player is dead — YouTube rejects WebView embeds with an
    // instant UNKNOWN error on every video (verified with an embedding-enabled demo
    // video and the newest player library). Poster + watch-page dialog instead.
    private fun playVideo(item: Instruction) {
        binding.tvTitle.text = item.name
        val videoId = YoutubeHelper.extractId(item.video)

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

        videoPosition = null
    }
}