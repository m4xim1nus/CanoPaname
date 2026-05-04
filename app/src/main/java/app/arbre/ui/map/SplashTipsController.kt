package app.arbre.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.arbre.data.CaptureRepository
import app.arbre.data.OnboardingStore
import app.arbre.data.SplashTip
import app.arbre.data.SplashTipsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay

/** Cadence de rotation des tips. 7 s ≈ 2 cycles de la couronne mini-platanes (3500 ms). */
private const val ROTATION_INTERVAL_MS = 7_000L

/** Placeholders runtime supportés. Doit rester aligné avec `tools/build_dataset.py`. */
private val SUPPORTED_PLACEHOLDERS = setOf(
    "captureCount", "speciesCount", "remarquableCount", "daysSinceFirst",
)

private val PLACEHOLDER_REGEX = Regex("""\{([a-zA-Z]+)\}""")

/**
 * Pilote l'affichage des tips informatifs du `ColdStartSplash` :
 *
 * - Au mount : décide du mode `intro` (séquence figée 10 tips d'accueil) vs
 *   `random` (shuffle non-répétitif sur tout le pool éligible) selon le flag
 *   persistant `splashIntroSeen` (DataStore Preferences `onboarding`).
 * - Snapshot **une seule fois** les Flows joueur (captureCount, espèces vues,
 *   remarquables, première capture). Les valeurs servent à filtrer les tips
 *   `requires` et à substituer les placeholders `{captureCount}`, etc. Si Room
 *   est lent, le splash affiche déjà des tips dataset/history pendant ce temps.
 * - Rotation : tick de [ROTATION_INTERVAL_MS] ms, démarrée seulement quand
 *   `canRotate == true` (= l'`Animatable` du hero a fini son fade-in, sinon
 *   double-fade visuel au tout 1er affichage).
 * - Persistance : pose `splashIntroSeen=true` après la **1re rotation** en
 *   mode intro — l'utilisateur a vu au moins 2 tips d'accueil. Un kill avant
 *   ce seuil garde l'intro pour la session suivante.
 *
 * @param canRotate `false` tant que l'animation hero (`intro.value < 1f`) tourne.
 *                  `true` une fois posée → la rotation démarre.
 */
@Composable
fun rememberSplashTipText(
    repository: SplashTipsRepository,
    captureRepository: CaptureRepository,
    onboardingStore: OnboardingStore,
    canRotate: Boolean,
): State<String?> {
    val state = remember { mutableStateOf<String?>(null) }
    val playerSnapshot = remember { mutableStateOf<Map<String, Int>?>(null) }
    // Mode (intro vs random) figé une seule fois au mount via `.first()`.
    // Surtout PAS via `collectAsState` : on appelle `markSplashIntroSeen()`
    // pendant la session, ce qui ferait re-emit le Flow → re-launch du
    // LaunchedEffect rotation → reset de la séquence en plein milieu.
    // Bug constaté : la séquence intro était cassée dès la 1re rotation.
    val isIntroMode = remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        isIntroMode.value = !onboardingStore.splashIntroSeen.first()
    }

    // 1. Snapshot une-fois des stats joueur. Les Flows Room sont cold ; un
    //    `.first()` suffit, pas besoin de re-collect — pendant le splash,
    //    aucune capture n'est possible (la map n'est pas encore prête), la
    //    photo est figée pour toute la session.
    LaunchedEffect(repository) {
        try {
            val (captures, speciesCount, remarquableCount, firstTs) = combine(
                captureRepository.captureCount(),
                captureRepository.capturedSpeciesIndices(),
                captureRepository.capturedRemarquableIds(),
                captureRepository.firstCaptureTimestamp(),
            ) { c, s, r, f ->
                listOf<Any?>(c, s.size, r.size, f)
            }.first()

            val cFirstTs = firstTs as Long?
            val daysSince = if (cFirstTs != null) {
                ((System.currentTimeMillis() - cFirstTs) / 86_400_000L)
                    .toInt()
                    .coerceAtLeast(0)
            } else 0

            playerSnapshot.value = mapOf(
                "captureCount" to (captures as Int),
                "speciesCount" to (speciesCount as Int),
                "remarquableCount" to (remarquableCount as Int),
                "daysSinceFirst" to daysSince,
            )
        } catch (_: Exception) {
            // Room indisponible : on continue avec un snapshot vide → seuls
            // les tips sans `requires` sont éligibles. Pas de crash splash.
            playerSnapshot.value = emptyMap()
        }
    }

    // 2. Rotation. Keys volontairement réduites :
    //    - `isIntroMode.value` change au plus une fois (null → true/false).
    //    - PAS de `playerSnapshot.value` en key : en mode intro le pool est
    //      figé sur `repository.intro`, en mode random le shuffle initial est
    //      OK même si le snapshot arrive après. Sinon on perdrait l'idx
    //      courant à chaque arrivée du snapshot Room.
    LaunchedEffect(repository, isIntroMode.value, canRotate) {
        val mode = isIntroMode.value ?: return@LaunchedEffect

        val sequence: List<SplashTip> = if (mode) {
            // Mode intro : ordre figé des 10 ids d'accueil.
            repository.intro.mapNotNull { repository.tipsById[it] }
        } else {
            // Mode random : pool basé sur le snapshot courant. Les tips
            // restent éligibles sur tout le splash (snapshot évolue rarement
            // pendant cette fenêtre).
            val player = playerSnapshot.value
            val pool = if (player == null) {
                repository.unconditionalTips
            } else {
                repository.tips.filter { tip ->
                    tip.requires.all { req -> (player[req] ?: 0) > 0 }
                }
            }
            pool.shuffled()
        }

        if (sequence.isEmpty()) {
            state.value = null
            return@LaunchedEffect
        }

        // Affiche le 1er tip immédiatement, **avant** d'attendre `canRotate` —
        // l'utilisateur voit du contenu dès le fade-in du hero.
        state.value = render(sequence[0], playerSnapshot.value ?: emptyMap())

        // Tant que le hero anime, on ne tourne pas (évite un crossfade dans
        // un fade-in déjà en cours = double-fade gênant).
        if (!canRotate) return@LaunchedEffect

        var idx = 0
        var rotations = 0
        while (true) {
            delay(ROTATION_INTERVAL_MS)
            idx = (idx + 1) % sequence.size
            state.value = render(sequence[idx], playerSnapshot.value ?: emptyMap())
            rotations++

            // Pose le flag persistant après la 1re rotation en mode intro :
            // l'utilisateur a vu au moins 2 tips d'accueil (≈ 7-14 s). Avant
            // ça, kill app = on rejoue l'intro à la prochaine session.
            // Le LaunchedEffect ne re-fire PAS sur cette écriture car le
            // mode est figé via `remember` (cf. `isIntroMode`).
            if (mode && rotations == 1) {
                onboardingStore.markSplashIntroSeen()
            }
        }
    }

    return state
}

/**
 * Substitue les placeholders `{xxx}` par les valeurs entières du snapshot.
 * Une clé absente est rendue par `"0"` — en pratique le filtre `requires`
 * empêche déjà ce cas, mais on reste défensif (une phrase mal taggée ne
 * crashe pas, elle affiche juste "0").
 */
private fun render(tip: SplashTip, substitutions: Map<String, Int>): String {
    return PLACEHOLDER_REGEX.replace(tip.text) { match ->
        val key = match.groupValues[1]
        if (key !in SUPPORTED_PLACEHOLDERS) match.value else (substitutions[key] ?: 0).toString()
    }
}
