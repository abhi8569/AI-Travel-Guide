package com.example.travelguide.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tour_guide_settings")

data class TourGuideSettings(
    val providerName: String,
    val apiKey: String,
    val baseUrl: String,
    val modelName: String,
    val searchRadius: Int,          // in meters
    val updateInterval: Long,       // in seconds
    val detailLevel: String,        // e.g., "Short", "Detailed", "Interesting Facts"
    val speechRate: Float,          // voice speed, e.g., 1.0f
    val speechPitch: Float,         // voice modulation/pitch, e.g. 1.0f
    val autoPlay: Boolean,          // auto-trigger narration when close
    val interests: String,          // comma-separated list of interests
    val popularOnly: Boolean,       // filter to show only popular spots
    val customPrompt: String,       // Custom guide instruction prompt template
    val mapLayer: String,           // map tile source layer, e.g. "dark", "light", "satellite"
    val promptShort: String,        // short detail instruction prompt
    val promptDetailed: String,     // detailed detail instruction prompt
    val promptInterestingFacts: String, // interesting facts detail instruction prompt
    val isGodModeActive: Boolean,   // whether God Mode is enabled
    val godModeSearchRadius: Int    // God Mode search radius in km
)

class SettingsRepository(private val context: Context) {

    companion object {
        const val DEFAULT_CUSTOM_PROMPT = "You are an expert local AI tour guide. Never greet the user or add unnecessary introductions—immediately explain the location. Focus on the most relevant and interesting information about the place, selecting the facts that best help the user understand its significance. Use the most likely interpretation if the location is ambiguous, and never ask follow-up questions or request clarification. Adapt the length, level of detail, and style of your response according to this: {info level goes here}"

        const val DEFAULT_PROMPT_SHORT = "short instruction: Keep your response extremely brief and concise. Write strictly around 2-3 sentences outlining the spot."
        const val DEFAULT_PROMPT_DETAILED = "guideline: You have free will: do not restrict the type or length of detail. Adjust your description to match the significance and history of the location, writing as much or as little as needed."
        const val DEFAULT_PROMPT_INTERESTING_FACTS = "guideline: State only a few highly interesting, surprising, or unusual facts about this place. Do not include greetings or standard commentary."
    }

    private object PreferencesKeys {
        val PROVIDER_NAME = stringPreferencesKey("provider_name")
        val API_KEY = stringPreferencesKey("api_key")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val SEARCH_RADIUS = intPreferencesKey("search_radius")
        val UPDATE_INTERVAL = longPreferencesKey("update_interval")
        val DETAIL_LEVEL = stringPreferencesKey("detail_level")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val INTERESTS = stringPreferencesKey("interests")
        val POPULAR_ONLY = booleanPreferencesKey("popular_only")
        val CUSTOM_PROMPT = stringPreferencesKey("custom_prompt")
        val MAP_LAYER = stringPreferencesKey("map_layer")
        val PROMPT_SHORT = stringPreferencesKey("prompt_short")
        val PROMPT_DETAILED = stringPreferencesKey("prompt_detailed")
        val PROMPT_INTERESTING_FACTS = stringPreferencesKey("prompt_interesting_facts")
        val IS_GOD_MODE_ACTIVE = booleanPreferencesKey("is_god_mode_active")
        val GOD_MODE_SEARCH_RADIUS = intPreferencesKey("god_mode_search_radius")
    }

    val settingsFlow: Flow<TourGuideSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            TourGuideSettings(
                providerName = preferences[PreferencesKeys.PROVIDER_NAME] ?: "OpenAI",
                apiKey = preferences[PreferencesKeys.API_KEY] ?: "",
                baseUrl = preferences[PreferencesKeys.BASE_URL] ?: "https://api.openai.com/v1",
                modelName = preferences[PreferencesKeys.MODEL_NAME] ?: "gpt-4o-mini",
                searchRadius = preferences[PreferencesKeys.SEARCH_RADIUS] ?: 1000,
                updateInterval = preferences[PreferencesKeys.UPDATE_INTERVAL] ?: 60L,
                detailLevel = preferences[PreferencesKeys.DETAIL_LEVEL] ?: "Short",
                speechRate = preferences[PreferencesKeys.SPEECH_RATE] ?: 1.0f,
                speechPitch = preferences[PreferencesKeys.SPEECH_PITCH] ?: 1.0f,
                autoPlay = preferences[PreferencesKeys.AUTO_PLAY] ?: false,
                interests = preferences[PreferencesKeys.INTERESTS] ?: "",
                popularOnly = preferences[PreferencesKeys.POPULAR_ONLY] ?: false,
                customPrompt = preferences[PreferencesKeys.CUSTOM_PROMPT] ?: DEFAULT_CUSTOM_PROMPT,
                mapLayer = preferences[PreferencesKeys.MAP_LAYER] ?: "dark",
                promptShort = preferences[PreferencesKeys.PROMPT_SHORT] ?: DEFAULT_PROMPT_SHORT,
                promptDetailed = preferences[PreferencesKeys.PROMPT_DETAILED] ?: DEFAULT_PROMPT_DETAILED,
                promptInterestingFacts = preferences[PreferencesKeys.PROMPT_INTERESTING_FACTS] ?: DEFAULT_PROMPT_INTERESTING_FACTS,
                isGodModeActive = preferences[PreferencesKeys.IS_GOD_MODE_ACTIVE] ?: false,
                godModeSearchRadius = preferences[PreferencesKeys.GOD_MODE_SEARCH_RADIUS] ?: 10
            )
        }

    suspend fun updateSettings(updater: (TourGuideSettings) -> TourGuideSettings) {
        context.dataStore.edit { preferences ->
            val current = TourGuideSettings(
                providerName = preferences[PreferencesKeys.PROVIDER_NAME] ?: "OpenAI",
                apiKey = preferences[PreferencesKeys.API_KEY] ?: "",
                baseUrl = preferences[PreferencesKeys.BASE_URL] ?: "https://api.openai.com/v1",
                modelName = preferences[PreferencesKeys.MODEL_NAME] ?: "gpt-4o-mini",
                searchRadius = preferences[PreferencesKeys.SEARCH_RADIUS] ?: 1000,
                updateInterval = preferences[PreferencesKeys.UPDATE_INTERVAL] ?: 60L,
                detailLevel = preferences[PreferencesKeys.DETAIL_LEVEL] ?: "Short",
                speechRate = preferences[PreferencesKeys.SPEECH_RATE] ?: 1.0f,
                speechPitch = preferences[PreferencesKeys.SPEECH_PITCH] ?: 1.0f,
                autoPlay = preferences[PreferencesKeys.AUTO_PLAY] ?: false,
                interests = preferences[PreferencesKeys.INTERESTS] ?: "",
                popularOnly = preferences[PreferencesKeys.POPULAR_ONLY] ?: false,
                customPrompt = preferences[PreferencesKeys.CUSTOM_PROMPT] ?: DEFAULT_CUSTOM_PROMPT,
                mapLayer = preferences[PreferencesKeys.MAP_LAYER] ?: "dark",
                promptShort = preferences[PreferencesKeys.PROMPT_SHORT] ?: DEFAULT_PROMPT_SHORT,
                promptDetailed = preferences[PreferencesKeys.PROMPT_DETAILED] ?: DEFAULT_PROMPT_DETAILED,
                promptInterestingFacts = preferences[PreferencesKeys.PROMPT_INTERESTING_FACTS] ?: DEFAULT_PROMPT_INTERESTING_FACTS,
                isGodModeActive = preferences[PreferencesKeys.IS_GOD_MODE_ACTIVE] ?: false,
                godModeSearchRadius = preferences[PreferencesKeys.GOD_MODE_SEARCH_RADIUS] ?: 10
            )
            val updated = updater(current)
            preferences[PreferencesKeys.PROVIDER_NAME] = updated.providerName
            preferences[PreferencesKeys.API_KEY] = updated.apiKey
            preferences[PreferencesKeys.BASE_URL] = updated.baseUrl
            preferences[PreferencesKeys.MODEL_NAME] = updated.modelName
            preferences[PreferencesKeys.SEARCH_RADIUS] = updated.searchRadius
            preferences[PreferencesKeys.UPDATE_INTERVAL] = updated.updateInterval
            preferences[PreferencesKeys.DETAIL_LEVEL] = updated.detailLevel
            preferences[PreferencesKeys.SPEECH_RATE] = updated.speechRate
            preferences[PreferencesKeys.SPEECH_PITCH] = updated.speechPitch
            preferences[PreferencesKeys.AUTO_PLAY] = updated.autoPlay
            preferences[PreferencesKeys.INTERESTS] = updated.interests
            preferences[PreferencesKeys.POPULAR_ONLY] = updated.popularOnly
            preferences[PreferencesKeys.CUSTOM_PROMPT] = updated.customPrompt
            preferences[PreferencesKeys.MAP_LAYER] = updated.mapLayer
            preferences[PreferencesKeys.PROMPT_SHORT] = updated.promptShort
            preferences[PreferencesKeys.PROMPT_DETAILED] = updated.promptDetailed
            preferences[PreferencesKeys.PROMPT_INTERESTING_FACTS] = updated.promptInterestingFacts
            preferences[PreferencesKeys.IS_GOD_MODE_ACTIVE] = updated.isGodModeActive
            preferences[PreferencesKeys.GOD_MODE_SEARCH_RADIUS] = updated.godModeSearchRadius
        }
    }

    val lastLocationFlow: Flow<Pair<Double, Double>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val lat = preferences[doublePreferencesKey("last_lat")] ?: 50.7333
            val lon = preferences[doublePreferencesKey("last_lon")] ?: 7.1000
            Pair(lat, lon)
        }

    suspend fun updateLastLocation(lat: Double, lon: Double) {
        context.dataStore.edit { preferences ->
            preferences[doublePreferencesKey("last_lat")] = lat
            preferences[doublePreferencesKey("last_lon")] = lon
        }
    }
}
