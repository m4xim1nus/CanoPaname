package app.arbre.ui.map

/**
 * État d'une capture en cours, persisté dans le `SavedStateHandle` du
 * `MapViewModel` pour survivre à un process death entre le launch de l'intent
 * caméra et son résultat (typique sur appareils faibles en RAM).
 *
 * `photoBasename` stocke le seul nom de fichier (UUID.jpg). Le chemin absolu
 * est reconstitué côté consumer via `<externalFilesDir>/captures/<basename>`,
 * ce qui rend l'état pendant immune aux device-transfer / multi-user.
 */
data class PendingCapture(
    val arbreId: Long,
    val speciesIndex: Int,
    val remarquable: Boolean,
    val photoBasename: String,
    val captureLatitude: Double,
    val captureLongitude: Double,
    val captureTimestamp: Long,
)
