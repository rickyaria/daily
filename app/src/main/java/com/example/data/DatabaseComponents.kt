package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey val nomorUnit: String,
    val lastUpdated: Long = 0,
    val lastHM: Double = 0.0,
    val lastSektor: String = "",
    val lastArea: String = ""
)

@Entity(tableName = "hm_updates")
data class HMUpdateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val email: String,
    val nomorUnit: String,
    val hoursMeter: Double,
    val sektor: String,
    val area: String,
    val isSynced: Boolean = false
)

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY nomorUnit ASC")
    fun getAllUnits(): Flow<List<UnitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: UnitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<UnitEntity>)

    @Query("SELECT * FROM units WHERE nomorUnit = :nomorUnit")
    suspend fun getUnitById(nomorUnit: String): UnitEntity?

    @Query("DELETE FROM units")
    suspend fun deleteAllUnits()

    @Query("DELETE FROM units WHERE nomorUnit = :nomorUnit")
    suspend fun deleteUnit(nomorUnit: String)
}

@Dao
interface HMUpdateDao {
    @Query("SELECT * FROM hm_updates ORDER BY timestamp DESC")
    fun getAllUpdates(): Flow<List<HMUpdateEntity>>

    @Query("SELECT * FROM hm_updates WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getUpdatesToday(startOfDay: Long): Flow<List<HMUpdateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpdate(update: HMUpdateEntity)

    @Query("UPDATE hm_updates SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Int)

    @Query("SELECT * FROM hm_updates WHERE isSynced = 0")
    suspend fun getUnsyncedUpdates(): List<HMUpdateEntity>

    @Query("UPDATE hm_updates SET nomorUnit = :newNomorUnit WHERE nomorUnit = :oldNomorUnit")
    suspend fun updateHMUpdatesUnitName(oldNomorUnit: String, newNomorUnit: String)

    @Query("DELETE FROM hm_updates WHERE timestamp < :startOfDay AND isSynced = 1")
    suspend fun deleteOldSyncedUpdates(startOfDay: Long)

    @Query("DELETE FROM hm_updates WHERE nomorUnit = :nomorUnit")
    suspend fun deleteHMUpdatesForUnit(nomorUnit: String)

    @Query("DELETE FROM hm_updates")
    suspend fun deleteAllUpdates()
}

@Database(entities = [UnitEntity::class, HMUpdateEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun unitDao(): UnitDao
    abstract fun hmUpdateDao(): HMUpdateDao
}
