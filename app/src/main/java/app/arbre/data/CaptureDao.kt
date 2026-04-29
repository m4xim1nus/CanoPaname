package app.arbre.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    @Insert
    suspend fun insert(capture: CaptureEntity): Long

    @Query("SELECT DISTINCT speciesIndex FROM capture WHERE remarquable = 0")
    fun capturedSpeciesIndices(): Flow<List<Int>>

    @Query("SELECT DISTINCT arbreId FROM capture WHERE remarquable = 1")
    fun capturedRemarquableIds(): Flow<List<Long>>

    @Query("SELECT * FROM capture WHERE arbreId = :arbreId ORDER BY timestamp DESC")
    fun capturesPourArbre(arbreId: Long): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM capture ORDER BY timestamp DESC")
    fun toutesLesCaptures(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM capture WHERE remarquable = 1 ORDER BY timestamp DESC")
    fun capturesRemarquables(): Flow<List<CaptureEntity>>

    @Query("SELECT MIN(timestamp) FROM capture")
    fun firstCaptureTimestamp(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM capture")
    fun captureCount(): Flow<Int>
}
