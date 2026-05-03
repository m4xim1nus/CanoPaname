package app.arbre.ui.map

import app.arbre.ui.theme.MapColors
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

// Couleurs des pins, ré-exposées localement pour ne pas polluer les sites
// d'usage avec un préfixe long. Source : `app.arbre.ui.theme.MapColors`.
private val PIN_GREEN: String = MapColors.PIN_GREEN
private val PIN_ORANGE: String = MapColors.PIN_ORANGE
private val PIN_GREY: String = MapColors.PIN_GREY

private const val EMPTY_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"

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
            .withClusterRadius(60),
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

    // Bulles de clusters : rayon fixe pour démarrer, on graduera après.
    // Limite assumée : la couleur cluster ne reflète pas la progression.
    val clusters = CircleLayer(CLUSTERS_LAYER_ID, ARBRES_SOURCE_ID).withProperties(
        PropertyFactory.circleColor(PIN_GREEN),
        PropertyFactory.circleStrokeColor("#FFFFFF"),
        PropertyFactory.circleStrokeWidth(2f),
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
 * Pré-filtre le GeoJSON pour ne garder que les features dont `properties.sk`
 * vaut `sk`. But : éviter le `std::bad_alloc` qu'on déclenchait côté natif
 * MapLibre quand on tentait de servir 217k features non clusterisées au z11
 * (crash reproduit 2026-04-29). Filtrer en amont ramène le corpus à ~max 38k
 * (Platanus) ou bien moins pour la plupart des espèces, et permet de garder
 * le clustering sur la source filtrée — donc une carte lisible avec
 * clusters d'espèce au dezoom et pins individuels au z14+.
 *
 * Implémentation : la sortie de `tools/build_dataset.py` est très régulière
 * (`json.dumps(separators=(",", ":"))`, ordre des clés stable), donc on peut
 * tokeniser sur `,{"type":"Feature"` et tester le suffixe `"sk":N}}` au lieu
 * de parser/reconstruire 32 Mo de JSON via `JSONObject` (qui exploserait la
 * heap). Coût : un seul scan linéaire de la string + StringBuilder.
 */
internal fun filterGeoJsonBySpecies(json: String, sk: Int): String {
    val featureSeparator = ",{\"type\":\"Feature\""
    val skSuffix = "\"sk\":$sk}}"
    val featuresMarker = "\"features\":["
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
        if (json.regionMatches(end - skSuffix.length, skSuffix, 0, skSuffix.length)) {
            if (!first) sb.append(",")
            sb.append(json, pos, end)
            first = false
        }
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
