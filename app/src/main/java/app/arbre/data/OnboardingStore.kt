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
private val SPLASH_INTRO_SEEN_KEY = booleanPreferencesKey("splash_intro_seen")

/**
 * Deux flags one-shot par install :
 * - [onboardingDone] : pilote le `startDestination` du NavHost.
 * - [splashIntroSeen] : passe la rotation tips du `ColdStartSplash` du mode
 *   intro figé au shuffle. Posé à la *fin* du splash, pas au mount, pour
 *   qu'un kill prématuré ne consume pas l'intro.
 */
class OnboardingStore(private val context: Context) {

    val onboardingDone: Flow<Boolean> = context.onboardingDataStore.data
        .map { prefs -> prefs[ONBOARDING_DONE_KEY] ?: false }

    val splashIntroSeen: Flow<Boolean> = context.onboardingDataStore.data
        .map { prefs -> prefs[SPLASH_INTRO_SEEN_KEY] ?: false }

    suspend fun markDone() {
        context.onboardingDataStore.edit { it[ONBOARDING_DONE_KEY] = true }
    }

    suspend fun markSplashIntroSeen() {
        context.onboardingDataStore.edit { it[SPLASH_INTRO_SEEN_KEY] = true }
    }
}
