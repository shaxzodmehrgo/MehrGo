package uz.teamwork.mehrgodriver.presentation.main.ui.not_active_user

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.main.UserUC
import javax.inject.Inject

@HiltViewModel
class NotActiveViewModel @Inject constructor(private val userUC: UserUC) : ViewModel() {
    fun getUser(): Flow<Resource<BaseResponse<User>>> {
        return userUC.invoke()
    }
}