package uz.teamwork.mehrgodriver.presentation.main.ui.videos

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.Video
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.main.VideosUC
import javax.inject.Inject

@HiltViewModel
class VideosViewModel @Inject constructor(private val videosUC: VideosUC) : ViewModel() {
    fun getVideos(): Flow<Resource<BaseResponse<List<Video>>>> {
        return videosUC.invoke()
    }
}