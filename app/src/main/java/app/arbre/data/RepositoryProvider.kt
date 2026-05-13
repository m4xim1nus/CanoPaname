package app.arbre.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.arbre.ArbresApp
import app.arbre.backup.BackupExporter
import app.arbre.backup.BackupImporter

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

@Composable
fun rememberRemarquableInfoRepository(): RemarquableInfoRepository {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().remarquableInfoRepository }
}

@Composable
fun rememberGenreInfoRepository(): GenreInfoRepository {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().genreInfoRepository }
}

@Composable
fun rememberArrSpeciesIndex(): ArrSpeciesIndex {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().arrSpeciesIndex }
}

@Composable
fun rememberSeasonStore(): SeasonStore {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().seasonStore }
}

@Composable
fun rememberOnboardingStore(): OnboardingStore {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().onboardingStore }
}

@Composable
fun rememberRadarObscureStore(): RadarObscureStore {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().radarObscureStore }
}

@Composable
fun rememberSplashTipsRepository(): SplashTipsRepository {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().splashTipsRepository }
}

@Composable
fun rememberBadgeRepository(): BadgeRepository {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().badgeRepository }
}

@Composable
fun rememberBackupExporter(): BackupExporter {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().backupExporter }
}

@Composable
fun rememberBackupImporter(): BackupImporter {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.app().backupImporter }
}

fun Context.arbreRepository(): ArbreRepository = app().arbreRepository
fun Context.captureRepository(): CaptureRepository = app().captureRepository
fun Context.speciesIndex(): SpeciesIndex = app().speciesIndex
fun Context.datasetStats(): DatasetStats = app().datasetStats
fun Context.speciesInfoRepository(): SpeciesInfoRepository = app().speciesInfoRepository
fun Context.remarquableInfoRepository(): RemarquableInfoRepository = app().remarquableInfoRepository
fun Context.genreInfoRepository(): GenreInfoRepository = app().genreInfoRepository
fun Context.arrSpeciesIndex(): ArrSpeciesIndex = app().arrSpeciesIndex
fun Context.seasonStore(): SeasonStore = app().seasonStore
fun Context.onboardingStore(): OnboardingStore = app().onboardingStore
fun Context.radarObscureStore(): RadarObscureStore = app().radarObscureStore
fun Context.splashTipsRepository(): SplashTipsRepository = app().splashTipsRepository
fun Context.badgeRepository(): BadgeRepository = app().badgeRepository
