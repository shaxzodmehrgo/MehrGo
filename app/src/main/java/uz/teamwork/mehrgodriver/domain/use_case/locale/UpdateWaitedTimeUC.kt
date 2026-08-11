package uz.teamwork.mehrgodriver.domain.use_case.locale

import uz.teamwork.mehrgodriver.domain.repository.locale.CalculationsRepository
import javax.inject.Inject

class UpdateWaitedTimeUC @Inject constructor(private val repository: CalculationsRepository) {
    suspend operator fun invoke(orderId: Int, waitedTime: Long) {
        repository.updateWaitedTime(orderId, waitedTime)
    }
}