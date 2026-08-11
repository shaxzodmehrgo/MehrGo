package uz.teamwork.mehrgodriver.presentation.activity.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.FcmTokenSync
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.UpdateApp
import uz.teamwork.mehrgodriver.domain.use_case.auth.RegisterDeviceTokenUC
import uz.teamwork.mehrgodriver.domain.use_case.main.UpdateAppUC
import javax.inject.Inject

@HiltViewModel
class FirstViewModel @Inject constructor(
    private val updateAppUC: UpdateAppUC,
    private val registerDeviceTokenUC: RegisterDeviceTokenUC
) : ViewModel() {

    fun updateApp(token: String?, deviceToken: String?): Flow<Resource<UpdateApp>> {
        return updateAppUC.invoke(token, deviceToken)
    }

    fun syncFcmTokenIfNeeded(serverDeviceToken: String?) {
        FcmTokenSync.syncIfNeeded(registerDeviceTokenUC, viewModelScope, serverDeviceToken)
    }
}
