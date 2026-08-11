package uz.teamwork.mehrgodriver.domain.model.locale

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline GPS buffer row. Captured by [MyTrackingService] the instant a sample
 * arrives so the point survives network loss / app kill, then drained FIFO by
 * the batch uploader. `cpid` is the server-side idempotency key — keeping it
 * stable across retries is what stops distance/fare from inflating.
 *
 * The `(orderId, sentAt)` index supports the hot-path query:
 * "oldest N un-sent points for this order".
 */
@Entity(
    tableName = "pending_gps_points",
    indices = [Index(value = ["order_id", "sent_at"])]
)
data class PendingGpsPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "order_id")
    val orderId: Int,

    @ColumnInfo(name = "cpid")
    val cpid: Long,

    @ColumnInfo(name = "lat")
    val lat: Double,

    @ColumnInfo(name = "lon")
    val lon: Double,

    @ColumnInfo(name = "accuracy")
    val accuracy: Float?,

    @ColumnInfo(name = "speed")
    val speed: Float?,

    @ColumnInfo(name = "bearing")
    val bearing: Float?,

    @ColumnInfo(name = "altitude")
    val altitude: Double?,

    @ColumnInfo(name = "provider")
    val provider: String?,

    @ColumnInfo(name = "ts")
    val ts: Long,

    // Order state (Constants.ORDER_STATE_*) at CAPTURE time — stamped at enqueue so a
    // point that drains after a state transition still bills under the state it was
    // driven in (see FareGpsPoint.state). Null = captured by a pre-update version.
    @ColumnInfo(name = "state")
    val state: Int? = null,

    // Null while queued. Set to System.currentTimeMillis() after server 200 — kept
    // briefly for diagnostics, then purged. Filtering on NULL gives us FIFO drain.
    @ColumnInfo(name = "sent_at")
    val sentAt: Long? = null
)
