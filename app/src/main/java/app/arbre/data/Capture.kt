package app.arbre.data

import android.content.Context
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

@Entity(
    tableName = "capture",
    indices = [
        Index("arbreId"),
        Index("speciesIndex"),
    ],
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val arbreId: Long,
    val speciesIndex: Int,
    val remarquable: Boolean,
    val timestamp: Long,
    val latitudeDevice: Double,
    val longitudeDevice: Double,
    val photoPath: String,
    val season: Int,
) {
    fun toCapture(): Capture = Capture(
        id = id,
        arbreId = arbreId,
        speciesIndex = speciesIndex,
        remarquable = remarquable,
        timestamp = timestamp,
        latitudeDevice = latitudeDevice,
        longitudeDevice = longitudeDevice,
        photoPath = photoPath,
        season = Season.fromStored(season),
    )
}

data class Capture(
    val id: Long,
    val arbreId: Long,
    val speciesIndex: Int,
    val remarquable: Boolean,
    val timestamp: Long,
    val latitudeDevice: Double,
    val longitudeDevice: Double,
    val photoPath: String,
    val season: Season,
)

// `photoPath` stocke un basename (UUID.jpg) depuis la migration v3.
// Les fichiers vivent tous sous `<externalFilesDir>/captures/`.
fun Capture.resolvedFile(context: Context): File =
    File(File(context.getExternalFilesDir(null), "captures"), photoPath)

fun CaptureEntity.resolvedFile(context: Context): File =
    File(File(context.getExternalFilesDir(null), "captures"), photoPath)

