package uz.teamwork.mehrgodriver.domain.use_case.locale

import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.domain.model.locale.Calculation
import uz.teamwork.mehrgodriver.domain.repository.locale.CalculationsRepository
import javax.inject.Inject

class GetCalculationsUC @Inject constructor(private val repository: CalculationsRepository) {
    operator fun invoke(): Flow<List<Calculation>> {
        return repository.getCalculations()
    }
}