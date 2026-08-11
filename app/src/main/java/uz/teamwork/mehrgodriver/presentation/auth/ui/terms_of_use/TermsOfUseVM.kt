package uz.teamwork.mehrgodriver.presentation.auth.ui.terms_of_use

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.TermsOfUse
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.auth.TermsOfUseUC
import javax.inject.Inject

@HiltViewModel
class TermsOfUseVM @Inject constructor(private val termsOfUseUC: TermsOfUseUC) : ViewModel() {
    fun termsOfUse(): Flow<Resource<BaseResponse<TermsOfUse>>> {
        return termsOfUseUC.invoke()
    }
}