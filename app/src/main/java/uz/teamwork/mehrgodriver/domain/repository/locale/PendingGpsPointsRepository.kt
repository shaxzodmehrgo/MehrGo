package uz.teamwork.mehrgodriver.domain.repository.locale

import uz.teamwork.mehrgodriver.domain.model.locale.PendingGpsPoint

interface PendingGpsPointsRepository {
    suspend fun enqueue(point: PendingGpsPoint): Long
    suspend fun nextBatch(orderId: Int, limit: Int): List<PendingGpsPoint>
    suspend fun lastCpid(orderId: Int): Long?
    suspend fun pendingCount(orderId: Int): Int
    suspend fun deleteByIds(ids: List<Long>)
    suspend fun deleteByOrder(orderId: Int)
}
