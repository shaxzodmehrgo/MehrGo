package uz.teamwork.mehrgodriver.data.locale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import uz.teamwork.mehrgodriver.domain.model.locale.PendingGpsPoint

@Dao
interface PendingGpsPointsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: PendingGpsPoint): Long

    // FIFO drain: oldest first. `sent_at IS NULL` skips already-acked rows that
    // are still hanging around for diagnostics.
    @Query(
        "SELECT * FROM pending_gps_points " +
                "WHERE order_id = :orderId AND sent_at IS NULL " +
                "ORDER BY ts ASC, id ASC LIMIT :limit"
    )
    suspend fun nextBatch(orderId: Int, limit: Int): List<PendingGpsPoint>

    @Query("SELECT cpid FROM pending_gps_points WHERE order_id = :orderId ORDER BY cpid DESC LIMIT 1")
    suspend fun lastCpid(orderId: Int): Long?

    @Query("SELECT COUNT(*) FROM pending_gps_points WHERE order_id = :orderId AND sent_at IS NULL")
    suspend fun pendingCount(orderId: Int): Int

    @Query("DELETE FROM pending_gps_points WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM pending_gps_points WHERE order_id = :orderId")
    suspend fun deleteByOrder(orderId: Int)
}
