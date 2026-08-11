package uz.teamwork.mehrgodriver.domain.use_case.locale

import uz.teamwork.mehrgodriver.domain.model.locale.Calculation
import uz.teamwork.mehrgodriver.domain.repository.locale.CalculationsRepository
import javax.inject.Inject

class GetCalculationUC @Inject constructor(private val repository: CalculationsRepository) {
    suspend operator fun invoke(orderId: Int): Calculation? {
        return repository.getCalculation(orderId)
    }
}