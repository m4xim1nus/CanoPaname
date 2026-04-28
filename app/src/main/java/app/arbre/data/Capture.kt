package app.arbre.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

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
        season = season,
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
    val season: Int,
)

object Season {
    const val WINTER = 0
    const val SPRING = 1
    const val SUMMER = 2
    const val AUTUMN = 3

    fun fromTimestamp(epochMillis: Long): Int {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { time = Date(epochMillis) }
        return when (cal.get(Calendar.MONTH)) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> WINTER
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> SPRING
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> SUMMER
            else -> AUTUMN
        }
    }
}
