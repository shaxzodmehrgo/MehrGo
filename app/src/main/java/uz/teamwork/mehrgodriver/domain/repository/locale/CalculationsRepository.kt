package uz.teamwork.mehrgodriver.domain.repository.locale

import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.model.MyLocation
import uz.teamwork.mehrgodriver.domain.model.locale.Calculation

interface CalculationsRepository {
    suspend fun addCalculation(calculation: Calculation)

    fun getCalculations(): Flow<List<Calculation>>

    suspend fun getCalculation(orderId: Int): Calculation?

    suspend fun updateTrackedTime(orderId: Int, trackedTime: Long)

    suspend fun updateWaitedTime(orderId: Int, waitedTime: Long)

    suspend fun updateWaitedTimeUntilGone(orderId: Int, waitedTimeUntilGone: Long)

    suspend fun updateLocations(orderId: Int, locations: List<MyLocation>)

    suspend fun deleteCalculation(orderId: Int)
}