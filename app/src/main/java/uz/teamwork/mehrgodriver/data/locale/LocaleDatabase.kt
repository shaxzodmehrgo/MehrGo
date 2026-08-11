package uz.teamwork.mehrgodriver.data.locale

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import uz.teamwork.mehrgodriver.common.LocationTypeConverter
import uz.teamwork.mehrgodriver.domain.model.locale.Calculation
import uz.teamwork.mehrgodriver.domain.model.locale.PendingGpsPoint

@Database(
    entities = [Calculation::class, PendingGpsPoint::class],
    version = 4
)
@TypeConverters(LocationTypeConverter::class)
abstract class LocaleDatabase : RoomDatabase() {
    abstract fun calculationDao(): CalculationsDao
    abstract fun pendingGpsPointsDao(): PendingGpsPointsDao

    companion object {
        const val DATABASE_NAME = "my_db"
    }
}
