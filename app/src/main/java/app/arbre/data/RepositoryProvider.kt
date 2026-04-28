package app.arbre.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.arbre.ArbresApp

private fun Context.app(): ArbresApp = applicationContext as ArbresApp

@Composable
fun rememberArbreRepository(): ArbreRepository {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().arbreRepository }
}

@Composable
fun rememberCaptureRepository(): CaptureRepository {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().captureRepository }
}

@Composable
fun rememberSpeciesIndex(): SpeciesIndex {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().speciesIndex }
}

@Composable
fun rememberDatasetStats(): DatasetStats {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().datasetStats }
}

@Composable
fun rememberSpeciesInfoRepository(): SpeciesInfoRepository {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().speciesInfoRepository }
}

fun Context.arbreRepository(): ArbreRepository = app().arbreRepository
fun Context.captureRepository(): CaptureRepository = app().captureRepository
fun Context.speciesIndex(): SpeciesIndex = app().speciesIndex
fun Context.datasetStats(): DatasetStats = app().datasetStats
fun Context.speciesInfoRepository(): SpeciesInfoRepository = app().speciesInfoRepository
