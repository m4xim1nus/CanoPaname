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
 * - Persistance : pose `splashIntroSeen=true` dès l'affichage du 1er tip
 *   d'intro **dans une session post-onboarding** (`onboardingDone == true`).
 *   Le mount transient du fallback `MAP` du NavHost (round-trip DataStore
 *   initial pré-Welcome) ne consume pas l'intro. Un crash *avant* le mount
 *   post-Welcome garde l'intro pour la session suivante.
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
    // Mode figé une fois au mount via `.first()` — surtout PAS `collectAsState` :
    // `markSplashIntroSeen()` ferait re-emit le Flow → re-launch du
    // LaunchedEffect rotation → reset de la séquence en plein milieu.
    val isIntroMode = remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        // Suspend jusqu'à `onboardingDone == true`. Deux cas couverts :
        // - Mount transient du fallback `MAP` du `ArbresNavHost` pré-Welcome :
        //   `onboardingDone` reste à `false` jusqu'à cancellation par démontage,
        //   l'intro n'est pas consommée.
        // - Mount post-Welcome : `WelcomeScreen.onContinue` lance `markDone()`
        //   en parallèle de `nav.navigate(MAP)` — un `.first()` nu attraperait
        //   souvent encore `false`. `.first { it }` attend l'edit DataStore.
        onboardingStore.onboardingDone.first { it }
        isIntroMode.value = !onboardingStore.splashIntroSeen.first()
    }

    // Snapshot unique des stats joueur. Pendant le splash, aucune capture
    // n'est possible (la map n'est pas prête), la photo est donc figée.
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

    // Keys minimales : `isIntroMode` change au plus une fois ; surtout PAS
    // `playerSnapshot.value` ici sinon on perdrait l'idx courant à chaque
    // arrivée du snapshot Room.
    LaunchedEffect(repository, isIntroMode.value, canRotate) {
        val mode = isIntroMode.value ?: return@LaunchedEffect

        val sequence: List<SplashTip> = if (mode) {
            repository.intro.mapNotNull { repository.tipsById[it] }
        } else {
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

        // 1er tip avant `canRotate` : du contenu pendant le fade-in du hero.
        state.value = render(sequence[0], playerSnapshot.value ?: emptyMap())

        // Consomme l'intro dès le 1er tip posé : un splash court suffit à
        // faire basculer en mode random aux sessions suivantes. Le mode est
        // figé via `remember`, cette écriture ne re-fire pas le LaunchedEffect.
        if (mode) {
            onboardingStore.markSplashIntroSeen()
        }

        // Pas de rotation pendant l'animation hero — sinon double-fade.
        if (!canRotate) return@LaunchedEffect

        var idx = 0
        while (true) {
            delay(ROTATION_INTERVAL_MS)
            idx = (idx + 1) % sequence.size
            state.value = render(sequence[idx], playerSnapshot.value ?: emptyMap())
        }
    }

    return state
}

/**
 * Substitue les placeholders `{xxx}`. Clé absente → `"0"` (défensif : une
 * phrase mal taggée n'explose pas le splash, elle affiche `"0"`).
 */
private fun render(tip: SplashTip, substitutions: Map<String, Int>): String {
    return PLACEHOLDER_REGEX.replace(tip.text) { match ->
        val key = match.groupValues[1]
        if (key !in SUPPORTED_PLACEHOLDERS) match.value else (substitutions[key] ?: 0).toString()
    }
}
