package app.arbre.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.arbre.ArbresApp

@Composable
fun rememberArbreRepository(): ArbreRepository {
    val ctx = LocalContext.current
    return remember(ctx) {
        (ctx.applicationContext as ArbresApp).arbreRepository
    }
}

fun Context.arbreRepository(): ArbreRepository =
    (applicationContext as ArbresApp).arbreRepository
