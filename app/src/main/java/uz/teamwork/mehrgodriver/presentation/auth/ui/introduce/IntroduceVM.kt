package uz.teamwork.mehrgodriver.presentation.auth.ui.introduce

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.Introduce
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.auth.IntroduceUC
import javax.inject.Inject

@HiltViewModel
class IntroduceVM @Inject constructor(private val introduceUC: IntroduceUC) : ViewModel() {
    fun getIntroduceData(): Flow<Resource<BaseResponse<List<Introduce>>>> {
        return introduceUC.invoke()
    }
}