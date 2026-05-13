package app.arbre.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.radarDataStore: DataStore<Preferences> by preferencesDataStore("radar")

private val OBSCURED_KEY = booleanPreferencesKey("obscured")

/**
 * Flag persistant de l'easter egg radar : triple-tap sur le glyphe du
 * `HuntPanel` bascule l'état, qui masque ensuite nom d'espèce + glose
 * d'intérêt sous `???` pour jouer la chasse en énigme. Persisté cross-session
 * — modelé sur [OnboardingStore].
 */
class RadarObscureStore(private val context: Context) {

    val obscured: Flow<Boolean> = context.radarDataStore.data
        .map { prefs -> prefs[OBSCURED_KEY] ?: false }

    suspend fun toggle() {
        context.radarDataStore.edit { prefs ->
            prefs[OBSCURED_KEY] = !(prefs[OBSCURED_KEY] ?: false)
        }
    }
}
