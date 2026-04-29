package app.arbre.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArbreDao {

    @Query(
        """
        SELECT * FROM arbre
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLon AND :maxLon
        LIMIT :limit
        """
    )
    fun arbresDansBbox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        limit: Int,
    ): Flow<List<ArbreEntity>>

    @Query("SELECT * FROM arbre WHERE id = :id")
    suspend fun arbreParId(id: Long): ArbreEntity?

    @Query("SELECT * FROM arbre WHERE id IN (:ids)")
    suspend fun arbresParIds(ids: List<Long>): List<ArbreEntity>

    @Query("SELECT * FROM arbre WHERE remarquable = 1")
    suspend fun arbresRemarquables(): List<ArbreEntity>

    @Query("SELECT COUNT(*) FROM arbre WHERE genre = :genre AND espece = :espece")
    suspend fun compterParEspece(genre: String, espece: String): Int

    @Query("SELECT * FROM arbre WHERE genre = :genre AND espece = :espece LIMIT 1")
    suspend fun unArbreParEspece(genre: String, espece: String): ArbreEntity?
}
