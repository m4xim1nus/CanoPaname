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
    /**
     * Décision « 1re espèce de la saison » figée AVANT le launch de l'intent
     * caméra : rien ne peut insérer une capture pendant l'intent (modal,
     * single-player, l'import backup vit sur l'écran Profil), donc le snapshot
     * précède strictement l'insert. Pilote à la fois le voile de transition
     * ([app.arbre.ui.map.MapHost.captureTransitionSk]) et la nav vers la fiche
     * espèce au retour — une seule source, jamais de divergence voile/nav.
     * `false` par défaut : clé absente du `SavedStateHandle` (pending posé par
     * une version antérieure) → fallback halo carte, pas de nav.
     */
    val willCelebrate: Boolean = false,
)
