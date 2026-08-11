package uz.teamwork.mehrgodriver.data.repository.locale

import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.model.MyLocation
import uz.teamwork.mehrgodriver.data.locale.CalculationsDao
import uz.teamwork.mehrgodriver.domain.model.locale.Calculation
import uz.teamwork.mehrgodriver.domain.repository.locale.CalculationsRepository

class CalculationRepositoryImpl(private val calculationsDao: CalculationsDao) :
    CalculationsRepository {
    override suspend fun addCalculation(calculation: Calculation) {
        calculationsDao.addCalculation(calculation)
    }

    override fun getCalculations(): Flow<List<Calculation>> {
        return calculationsDao.getCalculations()
    }

    override suspend fun getCalculation(orderId: Int): Calculation? {
        return calculationsDao.getCalculation(orderId)
    }

    override suspend fun updateTrackedTime(orderId: Int, trackedTime: Long) {
        calculationsDao.updateTrackedTime(orderId, trackedTime)
    }

    override suspend fun updateWaitedTime(orderId: Int, waitedTime: Long) {
        calculationsDao.updateWaitedTime(orderId, waitedTime)
    }

    override suspend fun updateWaitedTimeUntilGone(orderId: Int, waitedTimeUntilGone: Long) {
        calculationsDao.updateWaitedTimeUntilGone(orderId, waitedTimeUntilGone)
    }

    override suspend fun updateLocations(orderId: Int, locations: List<MyLocation>) {
        calculationsDao.updateLocations(orderId, locations)
    }

    override suspend fun deleteCalculation(orderId: Int) {
        calculationsDao.deleteCalculation(orderId)
    }
}