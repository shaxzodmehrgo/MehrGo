package uz.teamwork.mehrgodriver.presentation.main.ui.orders_in_locale

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.domain.model.locale.Calculation
import uz.teamwork.mehrgodriver.domain.use_case.locale.GetCalculationsUC
import javax.inject.Inject

@HiltViewModel
class OrdersInLocaleViewModel @Inject constructor(private val getCalculationsUC: GetCalculationsUC) :
    ViewModel() {
    fun getCalculations(): Flow<List<Calculation>> {
        return getCalculationsUC.invoke()
    }
}