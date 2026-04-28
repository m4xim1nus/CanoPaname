package app.arbre.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "arbre",
    indices = [Index(value = ["latitude", "longitude"])],
)
data class ArbreEntity(
    @PrimaryKey val id: Long,
    val genre: String?,
    val espece: String?,
    val varieteCultivar: String?,
    val nomCommun: String?,
    val hauteurM: Int?,
    val circonferenceCm: Int?,
    val remarquable: Boolean,
    val adresse: String?,
    val latitude: Double,
    val longitude: Double,
) {
    fun toArbre(): Arbre = Arbre(
        id = id,
        genre = genre,
        espece = espece,
        varieteCultivar = varieteCultivar,
        nomCommun = nomCommun,
        hauteurM = hauteurM,
        circonferenceCm = circonferenceCm,
        remarquable = remarquable,
        adresse = adresse,
        latitude = latitude,
        longitude = longitude,
    )
}
