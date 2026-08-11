package uz.teamwork.mehrgodriver.data.repository.locale

import uz.teamwork.mehrgodriver.data.locale.PendingGpsPointsDao
import uz.teamwork.mehrgodriver.domain.model.locale.PendingGpsPoint
import uz.teamwork.mehrgodriver.domain.repository.locale.PendingGpsPointsRepository

class PendingGpsPointsRepositoryImpl(
    private val dao: PendingGpsPointsDao
) : PendingGpsPointsRepository {

    override suspend fun enqueue(point: PendingGpsPoint): Long = dao.insert(point)

    override suspend fun nextBatch(orderId: Int, limit: Int): List<PendingGpsPoint> =
        dao.nextBatch(orderId, limit)

    override suspend fun lastCpid(orderId: Int): Long? = dao.lastCpid(orderId)

    override suspend fun pendingCount(orderId: Int): Int = dao.pendingCount(orderId)

    override suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteByIds(ids)
    }

    override suspend fun deleteByOrder(orderId: Int) = dao.deleteByOrder(orderId)
}
