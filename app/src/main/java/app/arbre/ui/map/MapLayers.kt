package app.arbre.ui.map

import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import app.arbre.ui.theme.MapColors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

internal const val ARBRES_SOURCE_ID = "arbres-source"
internal const val POINTS_LAYER_ID = "arbres-points"
internal const val CLUSTERS_LAYER_ID = "arbres-clusters"
internal const val CLUSTER_COUNT_LAYER_ID = "arbres-cluster-count"
internal const val PULSE_SOURCE_ID = "arbres-pulse-source"
internal const val PULSE_LAYER_ID = "arbres-pulse-layer"

private val PIN_GREEN: String = MapColors.PIN_GREEN
private val PIN_ORANGE: String = MapColors.PIN_ORANGE
private val PIN_GREY: String = MapColors.PIN_GREY
private val CLUSTER_MIXED: String = MapColors.CLUSTER_MIXED

internal const val EMPTY_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"

/**
 * Construit le `GeoJsonSource` ET attache layers. DOIT être appelé sur le
 * thread UI : MapLibre throw `CalledFromWorkerThreadException` dès le ctor
 * de `GeoJsonSource` si on essaye de paralléliser. Le parse 30 Mo se fait
 * donc sur Main — la stratégie « anti-freeze » est ailleurs (ne pas démarrer
 * tant que le splash n'a pas eu le temps de se rendre).
 */
internal fun addArbresLayers(style: Style, json: String) {
    val source = GeoJsonSource(
        ARBRES_SOURCE_ID,
        json,
        GeoJsonOptions()
            .withCluster(true)
            .withClusterMaxZoom(14)
            .withClusterRadius(60)
            // Accumule la propriété `discovered` (0|1 par feature, posée par
            // `enrichGeoJsonWithDiscovery`) dans `discovered_count`. Couplé
            // au `point_count` natif Supercluster, ça donne 3 buckets de
            // couleur sur la layer cluster (cf. `circleColor` ci-dessous).
            // L'accumulation se fait au moment du clustering : pour qu'elle
            // reflète l'état actuel des captures, il faut re-pousser le
            // GeoJSON enrichi via `setArbresGeoJson` à chaque changement.
            .withClusterProperty(
                "discovered_count",
                Expression.sum(
                    Expression.accumulated(),
                    Expression.toNumber(Expression.get("discovered_count"))
                ),
                Expression.toNumber(Expression.get("discovered"))
            )
            // Accumule le sous-ensemble « remarquables capturés » pour
            // déclencher le ring orange sur la layer cluster (cf. plus bas).
            // Sémantique : célébrer les remarquables découverts dans la zone,
            // pas signaler ce qui reste à trouver — distinct du FAB ★ qui lui
            // pointe vers les non découverts.
            .withClusterProperty(
                "discovered_remarquable_count",
                Expression.sum(
                    Expression.accumulated(),
                    Expression.toNumber(Expression.get("discovered_remarquable_count"))
                ),
                Expression.toNumber(Expression.get("discovered_remarquable"))
            ),
    )
    style.addSource(source)

    // Points individuels (pas dans un cluster). Couleur initiale = gris : la
    // vraie expression case/match est appliquée par `applyDiscoveryColor` dès
    // que le LaunchedEffect collecte les Flows captures (sub-frame).
    val points = CircleLayer(POINTS_LAYER_ID, ARBRES_SOURCE_ID).withProperties(
        PropertyFactory.circleRadius(5f),
        PropertyFactory.circleColor(PIN_GREY),
        PropertyFactory.circleStrokeColor("#FFFFFF"),
        PropertyFactory.circleStrokeWidth(1f),
    )
    points.setFilter(not(has("point_count")))
    style.addLayer(points)

    // Bulles de clusters : 3 buckets selon `discovered_count` vs `point_count`.
    //   - 0 capturé          → gris (`PIN_GREY`)
    //   - mixte              → vert clair (`CLUSTER_MIXED` = vert du splash)
    //   - tous capturés      → vert foncé (`PIN_GREEN`, = pins identifiés)
    // Sémantique « progression dans la zone ». Default en DERNIER (cf. note
    // `buildDiscoveryExpression` plus bas — un default mal placé serait pris
    // pour un label et l'expression silencieusement ignorée).
    val clusters = CircleLayer(CLUSTERS_LAYER_ID, ARBRES_SOURCE_ID).withProperties(
        PropertyFactory.circleColor(
            Expression.switchCase(
                Expression.eq(
                    Expression.toNumber(Expression.get("discovered_count")),
                    Expression.literal(0)
                ),
                Expression.color(android.graphics.Color.parseColor(PIN_GREY)),
                Expression.eq(
                    Expression.toNumber(Expression.get("discovered_count")),
                    Expression.toNumber(Expression.get("point_count"))
                ),
                Expression.color(android.graphics.Color.parseColor(PIN_GREEN)),
                Expression.color(android.graphics.Color.parseColor(CLUSTER_MIXED))
            )
        ),
        // Ring orange ssi ≥1 remarquable a été capturé dans le cluster.
        // Empilé par-dessus le bucket gris/vert clair/vert foncé du cluster.
        // L'expression `switchCase` sur stroke n'est pas utilisée ailleurs dans
        // le projet — si MapLibre Native 11.11.0 venait à ne pas la supporter,
        // fallback double-layer (ring orange filtré dédié, sous la principale).
        PropertyFactory.circleStrokeColor(
            Expression.switchCase(
                Expression.gt(
                    Expression.toNumber(Expression.get("discovered_remarquable_count")),
                    Expression.literal(0)
                ),
                Expression.color(android.graphics.Color.parseColor(PIN_ORANGE)),
                Expression.color(android.graphics.Color.parseColor("#FFFFFF"))
            )
        ),
        PropertyFactory.circleStrokeWidth(
            Expression.switchCase(
                Expression.gt(
                    Expression.toNumber(Expression.get("discovered_remarquable_count")),
                    Expression.literal(0)
                ),
                Expression.literal(3f),
                Expression.literal(2f)
            )
        ),
        PropertyFactory.circleOpacity(0.85f),
        PropertyFactory.circleRadius(20f),
    )
    clusters.setFilter(has("point_count"))
    style.addLayer(clusters)

    // Compte du cluster, en blanc, centré.
    // textFont DOIT pointer une fontstack que le style sert : OpenFreeMap
    // "liberty" sert "Noto Sans Regular". Une fontstack absente déclenche un
    // 404 sur /fonts/ qui invalide le rendu de toute la source côté natif.
    val count = SymbolLayer(CLUSTER_COUNT_LAYER_ID, ARBRES_SOURCE_ID).withProperties(
        PropertyFactory.textField(Expression.toString(get("point_count"))),
        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
        PropertyFactory.textSize(12f),
        PropertyFactory.textColor("#FFFFFF"),
        PropertyFactory.textAllowOverlap(true),
        PropertyFactory.textIgnorePlacement(true),
    )
    count.setFilter(has("point_count"))
    style.addLayer(count)
}

/**
 * Réinjecte le GeoJSON dans la source existante. Pilier de la stratégie
 * 2-passes du cold-start : on pose `addArbresLayers(style, EMPTY_GEOJSON)`
 * pour libérer le splash, puis on appelle ceci pour injecter les 217 k
 * features. Le parse 32 Mo bloque ~700 ms le UI thread, mais à ce moment
 * la carte est déjà visible — le freeze passe pour un « pas encore
 * d'arbres » plutôt que pour un splash figé. DOIT être appelé sur le
 * thread UI (même contrainte MapLibre que `GeoJsonSource`).
 */
internal fun setArbresGeoJson(style: Style, json: String) {
    val source = style.getSourceAs<GeoJsonSource>(ARBRES_SOURCE_ID) ?: return
    source.setGeoJson(json)
}

/**
 * Pousse le GeoJSON puis suspend jusqu'à ce que la source l'ait **réellement
 * intégré** : `setGeoJson` rend la main immédiatement (parse + re-clustering
 * en background côté natif, 1-3 s sur le corpus complet), alors que l'event
 * `onSourceChanged` de la MapView signale la bascule effective. Le listener
 * est posé AVANT le push (aucun event raté) ; timeout de sécurité car une
 * view en pause (hors-écran) gèle les events du render thread. Sert au
 * runner du filtre rapide : le spinner du `QuickFilterBanner` ne s'éteint
 * qu'au moment où l'utilisateur voit le nouveau contenu.
 */
internal suspend fun pushArbresGeoJsonAndAwait(
    mapView: MapView,
    style: Style,
    json: String,
    timeoutMs: Long = 6_000L,
) {
    withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val listener = object : MapView.OnSourceChangedListener {
                override fun onSourceChangedListener(id: String) {
                    if (id == ARBRES_SOURCE_ID) {
                        mapView.removeOnSourceChangedListener(this)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
            mapView.addOnSourceChangedListener(listener)
            cont.invokeOnCancellation { mapView.removeOnSourceChangedListener(listener) }
            setArbresGeoJson(style, json)
        }
    }
}

/**
 * Pré-filtre le GeoJSON pour ne garder que les features dont `properties.sk`
 * vaut `sk`. But : éviter le `std::bad_alloc` qu'on déclenchait côté natif
 * MapLibre quand on tentait de servir 217k features non clusterisées au z11
 * (crash reproduit 2026-04-29). Filtrer en amont ramène le corpus à ~max 38k
 * (Platanus) ou bien moins pour la plupart des espèces, et permet de garder
 * le clustering sur la source filtrée — donc une carte lisible avec
 * clusters d'espèce au dezoom et pins individuels au z14+.
 *
 * Filtre additionnel : sur cette carte filtrée, on retire aussi les arbres
 * remarquables que le joueur n'a pas encore capturés. Sans cela, leur seule
 * présence (même rendue en gris) trahit leur position et rend la chasse
 * triviale. La carte principale (mode non filtré) n'utilise pas cette
 * fonction et garde donc tous les remarquables visibles (gris ou orange).
 *
 * Implémentation : la sortie de `tools/build_dataset.py` est très régulière
 * (`json.dumps(separators=(",", ":"))`, ordre des clés stable), donc on peut
 * tokeniser sur `,{"type":"Feature"` et tester le suffixe `"sk":N}}` au lieu
 * de parser/reconstruire 32 Mo de JSON via `JSONObject` (qui exploserait la
 * heap). Coût : un seul scan linéaire de la string + StringBuilder.
 */
internal fun filterGeoJsonBySpecies(
    json: String,
    sks: Set<Int>,
    capturedRemarquables: Set<Long>,
): String {
    if (sks.isEmpty()) return EMPTY_GEOJSON
    val featureSeparator = ",{\"type\":\"Feature\""
    // Pré-calcule les suffixes `"sk":N}}` pour chaque sk du set. La fiche
    // genre passe un set polymorphe `{sk_sp.} ∪ {sks_du_genre_capturés}` ;
    // les fiches-espèces normales passent toujours un singleton.
    val skSuffixes = sks.map { "\"sk\":$it}}" }
    val featuresMarker = "\"features\":["
    val idMarker = "\"id\":"
    val remarquableMarker = "\"remarquable\":"
    val openIdx = json.indexOf(featuresMarker).let {
        if (it == -1) return EMPTY_GEOJSON else it + featuresMarker.length
    }
    val closeIdx = json.lastIndexOf("]}")
    if (openIdx >= closeIdx) return EMPTY_GEOJSON

    val sb = StringBuilder(64 * 1024)
    sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
    var first = true
    var pos = openIdx
    while (pos < closeIdx) {
        val nextSep = json.indexOf(featureSeparator, pos + 1)
        val end = if (nextSep == -1 || nextSep >= closeIdx) closeIdx else nextSep
        // `endsWith` est sûr : `sk` est la DERNIÈRE clé de `properties` dans
        // le build script (Python 3.7+ préserve l'ordre d'insertion, et le
        // dump JSON l'utilise). Si on change l'ordre côté Python, casser ce
        // contrat ici se traduit par une carte filtrée vide — ne pas rater.
        val matches = skSuffixes.any { suffix ->
            json.regionMatches(end - suffix.length, suffix, 0, suffix.length)
        }
        if (matches) {
            // Skip les remarquables non encore capturés. Mêmes markers que
            // `enrichGeoJsonWithDiscovery` ci-dessous : `id` puis `remarquable`
            // dans l'ordre d'insertion stable.
            val idStart = json.indexOf(idMarker, pos) + idMarker.length
            val idEnd = json.indexOf(',', idStart)
            val id = json.substring(idStart, idEnd).toLong()
            val remStart = json.indexOf(remarquableMarker, idEnd) + remarquableMarker.length
            val remarquable = json[remStart] == 't'
            val keep = !remarquable || id in capturedRemarquables
            if (keep) {
                if (!first) sb.append(",")
                sb.append(json, pos, end)
                first = false
            }
        }
        if (nextSep == -1 || nextSep >= closeIdx) break
        pos = nextSep + 1
    }
    sb.append("]}")
    return sb.toString()
}

/**
 * Réécrit le GeoJSON brut en injectant deux propriétés par feature, calculées
 * d'après les sets de captures du joueur :
 *   - `discovered: 0|1` :
 *       feature `remarquable: true` → 1 ssi `id ∈ capturedRemarquables`
 *       feature `remarquable: false` → 1 ssi `sk ∈ capturedSpecies`
 *   - `discovered_remarquable: 0|1` : 1 ssi `remarquable && id ∈ capturedRemarquables`.
 *
 * Les deux flags sont ensuite accumulés par Supercluster via les
 * `clusterProperty` `discovered_count` et `discovered_remarquable_count`
 * (cf. `addArbresLayers`), pour colorer le cluster en 3 buckets selon la
 * progression locale + un ring orange si ≥1 remarquable y a été capturé.
 *
 * Pourquoi un scan linéaire string et pas un parse JSON : la sortie de
 * `tools/build_dataset.py` est très régulière (`json.dumps(separators=(",", ":"))`,
 * ordre des clés stable `id`/`remarquable`/`sk`), donc on peut tokeniser sur
 * `,{"type":"Feature"` et extraire les 3 valeurs par `indexOf` ciblé. Coût ~150–
 * 300 ms en background sur 32 Mo / 217k features ; un parse JSON complet
 * exploserait la heap. Mêmes contrats que `filterGeoJsonBySpecies`.
 *
 * **Contrat** : la feature en entrée a la forme
 *   `{"type":"Feature","geometry":{...},"properties":{"id":X,"remarquable":bool,"sk":N}}`
 * — `sk` est la dernière clé. Si tu changes l'ordre côté Python, casser ce
 * contrat ici se traduit par des clusters tous gris (le flag `discovered`
 * mal injecté serait silencieusement ignoré par MapLibre) — ne pas rater.
 */
internal fun enrichGeoJsonWithDiscovery(
    json: String,
    capturedSpecies: Set<Int>,
    capturedRemarquables: Set<Long>,
): String {
    val featureSeparator = ",{\"type\":\"Feature\""
    val featuresMarker = "\"features\":["
    val openIdx = json.indexOf(featuresMarker).let {
        if (it == -1) return EMPTY_GEOJSON else it + featuresMarker.length
    }
    val closeIdx = json.lastIndexOf("]}")
    if (openIdx >= closeIdx) return EMPTY_GEOJSON

    // Sortie ≈ entrée + ~17 octets par feature découverte. Pré-allocation
    // conservatrice à json.length + 1 Mo.
    val sb = StringBuilder(json.length + 1_000_000)
    sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
    var first = true
    var pos = openIdx
    val idMarker = "\"id\":"
    val remarquableMarker = "\"remarquable\":"
    val skMarker = "\"sk\":"
    while (pos < closeIdx) {
        val nextSep = json.indexOf(featureSeparator, pos + 1)
        val end = if (nextSep == -1 || nextSep >= closeIdx) closeIdx else nextSep

        val idStart = json.indexOf(idMarker, pos) + idMarker.length
        val idEnd = json.indexOf(',', idStart)
        val id = json.substring(idStart, idEnd).toLong()

        val remStart = json.indexOf(remarquableMarker, idEnd) + remarquableMarker.length
        val remarquable = json[remStart] == 't' // "true" vs "false"

        // `sk` se termine juste avant le `}}` final de la feature. `end`
        // pointe sur la virgule du séparateur (ou sur le `]` du `]}` final
        // pour la dernière feature) ; dans les deux cas les 2 derniers
        // chars de la feature sont `}}`.
        val skStart = json.indexOf(skMarker, remStart) + skMarker.length
        val sk = json.substring(skStart, end - 2).toInt()

        val remarquableCaptured = remarquable && id in capturedRemarquables
        val discovered = if (remarquable) {
            if (remarquableCaptured) 1 else 0
        } else {
            if (sk in capturedSpecies) 1 else 0
        }
        val discoveredRemarquable = if (remarquableCaptured) 1 else 0

        if (!first) sb.append(",")
        sb.append(json, pos, end - 2)
        sb.append(",\"discovered\":")
        sb.append(discovered)
        sb.append(",\"discovered_remarquable\":")
        sb.append(discoveredRemarquable)
        sb.append("}}")
        first = false

        if (nextSep == -1 || nextSep >= closeIdx) break
        pos = nextSep + 1
    }
    sb.append("]}")
    return sb.toString()
}

internal fun applyDiscoveryColor(
    style: Style,
    capturedSpecies: Set<Int>,
    capturedRemarquables: Set<Long>,
) {
    val pointsLayer = style.getLayer(POINTS_LAYER_ID) as? CircleLayer ?: return
    pointsLayer.setProperties(
        PropertyFactory.circleColor(
            buildDiscoveryExpression(capturedSpecies, capturedRemarquables)
        )
    )
}

/**
 * `case(remarquable, match-id, match-sk)` :
 *   - pour un pin remarquable capturé, orange ssi son `id` est dans le set ;
 *   - pour un pin normal capturé, vert ssi son `sk` est dans le set ;
 *   - défaut = gris.
 *
 * L'ordre des args du `match` est `[input, label1, out1, …, default]` — default
 * en DERNIER (cf. spec MapLibre style). Ne pas inverser : un default placé en
 * 2e position serait pris pour un label string et l'expression silencieusement
 * ignorée (les pins resteraient à leur couleur initiale).
 *
 * Quand le set est vide, `match` ne tolère pas zéro stop : on retombe sur un
 * `literal(grey)` direct.
 */
private fun buildDiscoveryExpression(
    capturedSpecies: Set<Int>,
    capturedRemarquables: Set<Long>,
): Expression {
    val speciesExpr = if (capturedSpecies.isEmpty()) {
        literal(PIN_GREY)
    } else {
        val stops = mutableListOf<Expression>()
        for (sk in capturedSpecies) {
            stops += literal(sk)
            stops += literal(PIN_GREEN)
        }
        stops += literal(PIN_GREY)
        match(get("sk"), *stops.toTypedArray())
    }
    val remarquableExpr = if (capturedRemarquables.isEmpty()) {
        literal(PIN_GREY)
    } else {
        val stops = mutableListOf<Expression>()
        for (id in capturedRemarquables) {
            // Cast Int : tous les `idbase` parisiens tiennent dans 32 bits,
            // évite les quirks de boxing Long de l'API Java MapLibre.
            stops += literal(id.toInt())
            stops += literal(PIN_ORANGE)
        }
        stops += literal(PIN_GREY)
        match(get("id"), *stops.toTypedArray())
    }
    return switchCase(
        eq(get("remarquable"), literal(true)),
        remarquableExpr,
        speciesExpr,
    )
}

/**
 * Halo "pulse" autour d'un arbre cible, déclenché après un fly-to depuis la
 * fiche-remarquable ou la `PhotoLightbox`. On pose une source GeoJSON à 1
 * feature centrée sur l'arbre, puis
 * une `CircleLayer` au-dessus de `POINTS_LAYER_ID` (anneau visible par-dessus
 * le pin). L'animation `circleRadius` + `circleOpacity` est portée par un
 * `ValueAnimator` JVM standard, repeats configurable.
 *
 * Approche layer plutôt que Compose overlay : le halo suit naturellement la
 * caméra si l'utilisateur dezoome/pan pendant l'animation — pas de listener
 * `OnCameraMove` à câbler. Coût négligeable (1 feature, ~120 ticks JVM sur 2 s).
 */
internal fun addOrUpdatePulseSource(style: Style, lat: Double, lon: Double) {
    val featureJson = """{"type":"FeatureCollection","features":[""" +
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{}}""" +
        """]}"""
    val existing = style.getSourceAs<GeoJsonSource>(PULSE_SOURCE_ID)
    if (existing != null) {
        existing.setGeoJson(featureJson)
    } else {
        style.addSource(GeoJsonSource(PULSE_SOURCE_ID, featureJson))
    }
    if (style.getLayer(PULSE_LAYER_ID) == null) {
        val layer = CircleLayer(PULSE_LAYER_ID, PULSE_SOURCE_ID).withProperties(
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleOpacity(0.0f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeOpacity(0.0f),
        )
        // Au-dessus de la layer pins pour que l'anneau soit visible par-dessus
        // le cercle plein du pin (et au-dessus des clusters s'il y en a).
        style.addLayerAbove(layer, POINTS_LAYER_ID)
    }
}

/**
 * Animate radius 6→32 et opacity 0.6→0 sur la layer pulse. `repeats = 1` →
 * 2 itérations totales (~2 s avec `durationMs = 1000`). Au terme, retire la
 * layer + source via `removePulseLayer` pour ne pas polluer le style.
 */
internal fun animatePulse(
    style: Style,
    durationMs: Long = 1000L,
    repeats: Int = 1,
    onEnd: () -> Unit = {},
): ValueAnimator {
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        repeatCount = repeats
        addUpdateListener { ev ->
            val t = ev.animatedFraction
            val radius = 6f + 26f * t
            val opacity = 0.6f * (1f - t)
            val layer = style.getLayer(PULSE_LAYER_ID) as? CircleLayer ?: return@addUpdateListener
            layer.setProperties(
                PropertyFactory.circleRadius(radius),
                PropertyFactory.circleOpacity(opacity),
                PropertyFactory.circleStrokeOpacity(opacity),
            )
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                removePulseLayer(style)
                onEnd()
            }
        })
    }
    animator.start()
    return animator
}

internal fun removePulseLayer(style: Style) {
    style.getLayer(PULSE_LAYER_ID)?.let { style.removeLayer(it) }
    style.getSourceAs<GeoJsonSource>(PULSE_SOURCE_ID)?.let { style.removeSource(it) }
}
