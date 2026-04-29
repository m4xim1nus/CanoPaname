package app.arbre.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore("onboarding")

private val ONBOARDING_DONE_KEY = booleanPreferencesKey("done")

/**
 * Stocke un seul booléen : « le WelcomeScreen a-t-il été vu au moins une
 * fois ». Première ouverture (flag absente) → false. Une fois validé
 * via [markDone], le flag reste true pour toute la durée de vie de
 * l'install.
 *
 * On lit via Flow pour que le NavHost puisse réagir reactively au
 * passage true → renavigation hors WelcomeScreen.
 */
class OnboardingStore(private val context: Context) {

    val onboardingDone: Flow<Boolean> = context.onboardingDataStore.data
        .map { prefs -> prefs[ONBOARDING_DONE_KEY] ?: false }

    suspend fun markDone() {
        context.onboardingDataStore.edit { it[ONBOARDING_DONE_KEY] = true }
    }
}
