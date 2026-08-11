package uz.teamwork.mehrgodriver.data.di

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.teamwork.mehrgodriver.data.locale.LocaleDatabase
import uz.teamwork.mehrgodriver.data.repository.locale.CalculationRepositoryImpl
import uz.teamwork.mehrgodriver.data.repository.locale.PendingGpsPointsRepositoryImpl
import uz.teamwork.mehrgodriver.domain.repository.locale.CalculationsRepository
import uz.teamwork.mehrgodriver.domain.repository.locale.PendingGpsPointsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocaleDatabaseModule {

    // v3→v4: per-point order-state stamp on the offline GPS queue. Explicit migration
    // (not the destructive fallback) so an app update mid-trip keeps the queued track.
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_gps_points ADD COLUMN state INTEGER")
        }
    }

    @Provides
    @Singleton
    fun provideLocaleDatabase(app: Application): LocaleDatabase {
        return Room.databaseBuilder(app, LocaleDatabase::class.java, LocaleDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideCalculationsRepo(db: LocaleDatabase): CalculationsRepository {
        return CalculationRepositoryImpl(db.calculationDao())
    }

    @Provides
    @Singleton
    fun providePendingGpsPointsRepo(db: LocaleDatabase): PendingGpsPointsRepository {
        return PendingGpsPointsRepositoryImpl(db.pendingGpsPointsDao())
    }
}
