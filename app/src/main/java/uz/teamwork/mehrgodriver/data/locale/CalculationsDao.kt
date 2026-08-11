package uz.teamwork.mehrgodriver.data.locale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.model.MyLocation
import uz.teamwork.mehrgodriver.domain.model.locale.Calculation

@Dao
interface CalculationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addCalculation(calculation: Calculation)

    @Query("SELECT * FROM calculations WHERE order_id = :orderId LIMIT 1")
    suspend fun getCalculation(orderId: Int): Calculation?

    // Newest first: the auto-increment id grows with each saved trip, so id DESC == most-recent first
    // (the table stores no explicit timestamp).
    @Query("SELECT * FROM calculations ORDER BY id DESC")
    fun getCalculations(): Flow<List<Calculation>>

    @Query("UPDATE calculations SET locations = :locations WHERE order_id = :orderId")
    suspend fun updateLocations(orderId: Int, locations: List<MyLocation>)

    @Query("UPDATE calculations SET tracked_time = :trackedTime WHERE order_id = :orderId")
    suspend fun updateTrackedTime(orderId: Int, trackedTime: Long)

    @Query("UPDATE calculations SET waited_time = :waitedTime WHERE order_id = :orderId")
    suspend fun updateWaitedTime(orderId: Int, waitedTime: Long)

    @Query("UPDATE calculations SET waited_time_until_gone = :waitedTimeUntilGone WHERE order_id = :orderId")
    suspend fun updateWaitedTimeUntilGone(orderId: Int, waitedTimeUntilGone: Long)

    @Query("DELETE FROM calculations WHERE order_id = :orderId")
    suspend fun deleteCalculation(orderId: Int)
}