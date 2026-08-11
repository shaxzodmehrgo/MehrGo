package uz.teamwork.mehrgodriver.domain.model.locale

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import uz.teamwork.mehrgodriver.common.model.MyLocation

@Entity(tableName = "calculations")
data class Calculation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "order_id")
    val orderId: Int,

    @ColumnInfo(name = "tracked_time")
    val trackedTime: Long,

    @ColumnInfo(name = "waited_time")
    val waitedTime: Long,

    @ColumnInfo(name = "waited_time_until_gone")
    val waitedTimeUntilGone: Long,

    @ColumnInfo(name = "locations")
    val locations: List<MyLocation> = emptyList()
)