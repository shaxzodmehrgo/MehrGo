package uz.teamwork.mehrgodriver.domain.use_case.locale

import uz.teamwork.mehrgodriver.common.model.MyLocation
import uz.teamwork.mehrgodriver.domain.repository.locale.CalculationsRepository
import javax.inject.Inject

class UpdateLocationsUC @Inject constructor(private val repository: CalculationsRepository) {
    suspend operator fun invoke(orderId: Int, locations: List<MyLocation>) {
        repository.updateLocations(orderId, locations)
    }
}