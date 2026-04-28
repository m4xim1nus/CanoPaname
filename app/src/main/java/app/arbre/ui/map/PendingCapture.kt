package app.arbre.ui.map

/**
 * État d'une capture en cours, persisté dans le `SavedStateHandle` du
 * `MapViewModel` pour survivre à un process death entre le launch de l'intent
 * caméra et son résultat (typique sur appareils faibles en RAM).
 */
data class PendingCapture(
    val arbreId: Long,
    val speciesIndex: Int,
    val remarquable: Boolean,
    val photoPath: String,
    val captureLatitude: Double,
    val captureLongitude: Double,
    val captureTimestamp: Long,
)
