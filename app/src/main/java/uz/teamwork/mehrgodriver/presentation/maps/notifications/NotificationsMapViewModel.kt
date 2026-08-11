package uz.teamwork.mehrgodriver.presentation.maps.notifications

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.Notification
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.main.NotificationsUC
import javax.inject.Inject

@HiltViewModel
class NotificationsMapViewModel @Inject constructor(private val notificationsUC: NotificationsUC) :
    ViewModel() {
    fun getNotifications(): Flow<Resource<BaseResponse<List<Notification>>>> {
        return notificationsUC.invoke()
    }
}