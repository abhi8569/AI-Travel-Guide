package com.example.travelguide.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelguide.data.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TourGuideUiState(
    val settings: TourGuideSettings = TourGuideSettings(
        providerName = "OpenAI",
        apiKey = "",
        baseUrl = "https://api.openai.com/v1",
        modelName = "gpt-4o-mini",
        searchRadius = 1000,
        updateInterval = 60L,
        detailLevel = "Short",
        speechRate = 1.0f,
        speechPitch = 1.0f,
        autoPlay = false,
        interests = "",
        popularOnly = false,
        customPrompt = SettingsRepository.DEFAULT_CUSTOM_PROMPT,
        mapLayer = "dark",
        promptShort = SettingsRepository.DEFAULT_PROMPT_SHORT,
        promptDetailed = SettingsRepository.DEFAULT_PROMPT_DETAILED,
        promptInterestingFacts = SettingsRepository.DEFAULT_PROMPT_INTERESTING_FACTS,
        isGodModeActive = false,
        godModeSearchRadius = 10
    ),
    val currentLocation: UserLocation? = null,
    val nearbyPlaces: List<PlaceOfInterest> = emptyList(),
    val activePlace: PlaceOfInterest? = null,
    val guideContent: String? = null,
    val isSpeaking: Boolean = false,
    val chatHistory: List<ChatMessage> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val isGeneratingGuide: Boolean = false,
    val isSendingChatMessage: Boolean = false,
    val locationTrackingActive: Boolean = false,
    val error: String? = null,
    val fetchedModels: List<String> = emptyList(),
    val isFetchingModels: Boolean = false,
    val availableInterests: List<String> = emptyList(), // Extracted from nearby options dynamically
    val useMapCenter: Boolean = true,
    val mapCenterLocation: UserLocation? = null,
    val dbDownloadProgress: Float? = null,
    val dbDownloadError: String? = null,
    val isDatabaseDownloaded: Boolean = false,
    val mapResetTrigger: Int = 0
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val locationTracker = LocationTracker(application)
    private val poiRepository = PoiRepository()
    private val aiRepository = AIRepository()
    private val ttsManager = TtsManager(application)

    private val _uiState = MutableStateFlow(TourGuideUiState())
    val uiState: StateFlow<TourGuideUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private val narratedPlaceIds = mutableSetOf<String>()
    private var lastNarrationTime = 0L
    private val guideCache = mutableMapOf<String, String>() // Local cache to save API bills
    private var rawNearbyPlaces = emptyList<PlaceOfInterest>()
    private var activeRadius = 0
    private var activeInterval = 0L

    init {
        val isDbDownloaded = poiRepository.isDatabaseDownloaded(application)
        _uiState.update { it.copy(isDatabaseDownloaded = isDbDownloaded) }

        // Collect settings and update state
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { newSettings ->
                _uiState.update { it.copy(settings = newSettings) }
                // Update TTS speed and pitch
                ttsManager.setPitchAndSpeed(newSettings.speechRate, newSettings.speechPitch)
                
                // Re-filter currently loaded raw places based on new settings (interests, popularity)
                if (rawNearbyPlaces.isNotEmpty()) {
                    val filtered = filterPlaces(rawNearbyPlaces, newSettings)
                    val popularPlaces = if (newSettings.popularOnly) {
                        rawNearbyPlaces.filter { place ->
                            val hasWiki = place.tags.containsKey("wikipedia") || place.tags.containsKey("wikidata")
                            val isMajorAttraction = place.category.lowercase().contains("museum") || 
                                                    place.category.lowercase().contains("castle") || 
                                                    place.category.lowercase().contains("monument")
                            hasWiki || isMajorAttraction
                        }
                    } else {
                        rawNearbyPlaces
                    }
                    val categories = popularPlaces.map { it.category }.distinct().sorted()
                    _uiState.update { it.copy(nearbyPlaces = filtered, availableInterests = categories) }
                }

                // Restart location tracking ONLY if active tracking parameters (radius or interval) changed
                if (_uiState.value.locationTrackingActive) {
                    if (newSettings.updateInterval != activeInterval || newSettings.searchRadius != activeRadius) {
                        activeInterval = newSettings.updateInterval
                        activeRadius = newSettings.searchRadius
                        startLocationTracking()
                    }
                }
            }
        }

        // Collect TTS status
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }
        // Collect shared location share intents from MainActivity
        viewModelScope.launch {
            com.example.travelguide.SharedLocationManager.sharedText.collect { text ->
                handleSharedLocationText(text)
            }
        }

        // Collect last panned location on startup
        viewModelScope.launch {
            settingsRepository.lastLocationFlow.first().let { (lat, lon) ->
                _uiState.update { it.copy(mapCenterLocation = UserLocation(lat, lon, 0f), mapResetTrigger = it.mapResetTrigger + 1) }
            }
        }
    }

    private fun filterPlaces(places: List<PlaceOfInterest>, settings: TourGuideSettings): List<PlaceOfInterest> {
        val disabledInterests = settings.interests.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        
        return places.filter { place ->
            val matchesInterest = !disabledInterests.contains(place.category.lowercase().trim())

            // Filter by popularity score
            val hasWiki = place.tags.containsKey("wikipedia") || place.tags.containsKey("wikidata")
            val isMajorAttraction = place.category.lowercase().contains("museum") || 
                                    place.category.lowercase().contains("castle") || 
                                    place.category.lowercase().contains("monument")
            val isPopular = hasWiki || isMajorAttraction

            val matchesPopularity = !settings.popularOnly || isPopular

            matchesInterest && matchesPopularity
        }
    }

    fun toggleLocationTracking() {
        val currentActive = _uiState.value.locationTrackingActive
        if (currentActive) {
            stopLocationTracking()
        } else {
            startLocationTracking()
        }
    }

    private fun startLocationTracking() {
        locationJob?.cancel()
        
        if (!locationTracker.hasLocationPermission()) {
            _uiState.update { it.copy(error = "Location permissions are not granted. Please enable them in settings.") }
            return
        }

        _uiState.update { it.copy(locationTrackingActive = true, error = null) }
        
        val settings = _uiState.value.settings
        activeRadius = settings.searchRadius
        activeInterval = settings.updateInterval
        // We set distance filter to radius / 10 to trigger new search on significant movement
        val minDistance = (settings.searchRadius / 10).coerceAtLeast(10).toFloat()

        locationJob = viewModelScope.launch {
            locationTracker.getLocationUpdates(settings.updateInterval, minDistance)
                .catch { e ->
                    _uiState.update { it.copy(error = "Location updates failed: ${e.message}", locationTrackingActive = false) }
                }
                .collect { location ->
                    _uiState.update { 
                        if (!it.useMapCenter) {
                            it.copy(currentLocation = location, mapCenterLocation = location, mapResetTrigger = it.mapResetTrigger + 1)
                        } else {
                            it.copy(currentLocation = location)
                        }
                    }
                    if (!_uiState.value.useMapCenter) {
                        searchPlacesNear(location.latitude, location.longitude, isAutoTrigger = true)
                    }
                }
        }
    }

    private fun stopLocationTracking() {
        locationJob?.cancel()
        locationJob = null
        _uiState.update { it.copy(locationTrackingActive = false) }
    }

    fun refreshPlacesManually() {
        if (_uiState.value.useMapCenter) {
            scanMapCenterArea()
        } else {
            val loc = _uiState.value.currentLocation
            if (loc != null) {
                viewModelScope.launch {
                    searchPlacesNear(loc.latitude, loc.longitude, isAutoTrigger = false)
                }
            } else {
                viewModelScope.launch {
                    val lastLoc = locationTracker.getLastKnownLocation()
                    if (lastLoc != null) {
                        _uiState.update { it.copy(currentLocation = lastLoc, mapCenterLocation = lastLoc, mapResetTrigger = it.mapResetTrigger + 1) }
                        searchPlacesNear(lastLoc.latitude, lastLoc.longitude, isAutoTrigger = false)
                    } else {
                        _uiState.update { it.copy(error = "No location coordinates found. Enable GPS or search for a city.") }
                    }
                }
            }
        }
    }

    private suspend fun searchPlacesNear(lat: Double, lon: Double, isAutoTrigger: Boolean = false) {
        _uiState.update { it.copy(isSearchingPlaces = true, error = null) }
        val settings = _uiState.value.settings
        val places = if (settings.isGodModeActive) {
            poiRepository.fetchLocalAtlasObscuraPlaces(
                context = getApplication(),
                centerLat = lat,
                centerLon = lon,
                radiusInKm = settings.godModeSearchRadius.toDouble()
            )
        } else {
            poiRepository.fetchNearbyPlaces(lat, lon, settings.searchRadius)
        }
        
        rawNearbyPlaces = places
        
        val popularPlaces = if (settings.popularOnly && !settings.isGodModeActive) {
            places.filter { place ->
                val hasWiki = place.tags.containsKey("wikipedia") || place.tags.containsKey("wikidata")
                val isMajorAttraction = place.category.lowercase().contains("museum") || 
                                         place.category.lowercase().contains("castle") || 
                                         place.category.lowercase().contains("monument")
                hasWiki || isMajorAttraction
            }
        } else {
            places
        }
        val categories = popularPlaces.map { it.category }.distinct().sorted()
        
        _uiState.update { 
            it.copy(
                nearbyPlaces = if (settings.isGodModeActive) places else filterPlaces(places, settings),
                availableInterests = categories,
                isSearchingPlaces = false
            ) 
        }

        // Auto-play narration for the closest place if enabled and credentials are ready
        val filtered = _uiState.value.nearbyPlaces
        if (isAutoTrigger && settings.autoPlay && filtered.isNotEmpty()) {
            val isLocal = settings.baseUrl.contains("localhost") || 
                          settings.baseUrl.contains("127.0.0.1") || 
                          settings.baseUrl.contains("10.0.2.2")
            val hasCredentials = isLocal || settings.apiKey.isNotBlank()

            if (hasCredentials) {
                val now = System.currentTimeMillis()
                // 3 minutes (180,000 ms) cooldown between auto-narrations
                if (now - lastNarrationTime >= 180000L) {
                    val closest = filtered.first()
                    // Auto play if within 100 meters and not already read
                    if (closest.distance < 100f && !narratedPlaceIds.contains(closest.id)) {
                        lastNarrationTime = now
                        selectPlaceAndGenerateGuide(closest)
                    }
                }
            }
        }
    }

    fun selectPlaceAndGenerateGuide(place: PlaceOfInterest) {
        ttsManager.stop()
        
        // 1. Check local guide cache first to completely eliminate duplicate API costs!
        val cachedContent = guideCache[place.id]
        if (cachedContent != null) {
            _uiState.update { 
                it.copy(
                    activePlace = place,
                    guideContent = cachedContent,
                    chatHistory = emptyList(),
                    isGeneratingGuide = false,
                    error = null
                ) 
            }
            narratedPlaceIds.add(place.id)
            ttsManager.speak(cachedContent, _uiState.value.settings.speechRate, _uiState.value.settings.speechPitch)
            return
        }

        // 2. Not cached - generate new guide
        _uiState.update { 
            it.copy(
                activePlace = place,
                guideContent = null,
                chatHistory = emptyList(),
                isGeneratingGuide = true,
                error = null
            ) 
        }

        viewModelScope.launch {
            val content = aiRepository.generateGuideContent(_uiState.value.settings, place)
            
            // Save to local cache
            guideCache[place.id] = content
            
            _uiState.update { it.copy(guideContent = content, isGeneratingGuide = false) }
            
            // Mark as narrated
            narratedPlaceIds.add(place.id)
            
            // Read guide content aloud
            ttsManager.speak(content, _uiState.value.settings.speechRate, _uiState.value.settings.speechPitch)
        }
    }

    fun selectPlaceWithoutNarration(place: PlaceOfInterest) {
        ttsManager.stop()
        _uiState.update { 
            it.copy(
                activePlace = place,
                guideContent = null,
                chatHistory = emptyList()
            ) 
        }
    }

    fun speakGuideAgain() {
        val content = _uiState.value.guideContent
        if (!content.isNullOrEmpty()) {
            ttsManager.speak(content, _uiState.value.settings.speechRate, _uiState.value.settings.speechPitch)
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun updateSpeechRate(rate: Float) {
        val currentPitch = _uiState.value.settings.speechPitch
        ttsManager.setPitchAndSpeed(rate, currentPitch)
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(speechRate = rate)
            }
        }
    }

    fun sendChatMessage(question: String) {
        val activePlace = _uiState.value.activePlace ?: return
        if (question.isBlank()) return

        val userMessage = ChatMessage(role = "user", content = question)
        val updatedHistory = _uiState.value.chatHistory + userMessage
        
        _uiState.update { 
            it.copy(
                chatHistory = updatedHistory,
                isSendingChatMessage = true,
                error = null
            ) 
        }

        viewModelScope.launch {
            val reply = aiRepository.chatWithGuide(
                _uiState.value.settings,
                activePlace,
                _uiState.value.chatHistory, // includes userMessage
                question
            )
            
            val guideMessage = ChatMessage(role = "assistant", content = reply)
            _uiState.update { 
                it.copy(
                    chatHistory = it.chatHistory + guideMessage,
                    isSendingChatMessage = false
                ) 
            }
            
            // Read guide's answer aloud
            ttsManager.speak(reply, _uiState.value.settings.speechRate, _uiState.value.settings.speechPitch)
        }
    }

    fun fetchModelsList(baseUrl: String, apiKey: String) {
        _uiState.update { it.copy(isFetchingModels = true) }
        viewModelScope.launch {
            val models = aiRepository.fetchAvailableModels(baseUrl, apiKey)
            if (models.isNotEmpty()) {
                _uiState.update { it.copy(fetchedModels = models, isFetchingModels = false) }
            } else {
                _uiState.update { 
                    it.copy(
                        isFetchingModels = false, 
                        error = "Failed to fetch models. Check Base URL, API Key, and network connection."
                    ) 
                }
            }
        }
    }

    fun updateSettings(
        providerName: String,
        apiKey: String,
        baseUrl: String,
        modelName: String,
        searchRadius: Int,
        updateInterval: Long,
        detailLevel: String,
        speechRate: Float,
        speechPitch: Float,
        autoPlay: Boolean,
        interests: String,
        popularOnly: Boolean,
        customPrompt: String
    ) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(
                    providerName = providerName,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    modelName = modelName,
                    searchRadius = searchRadius,
                    updateInterval = updateInterval,
                    detailLevel = detailLevel,
                    speechRate = speechRate,
                    speechPitch = speechPitch,
                    autoPlay = autoPlay,
                    interests = interests,
                    popularOnly = popularOnly,
                    customPrompt = customPrompt,
                    mapLayer = current.mapLayer,
                    promptShort = current.promptShort,
                    promptDetailed = current.promptDetailed,
                    promptInterestingFacts = current.promptInterestingFacts,
                    isGodModeActive = current.isGodModeActive,
                    godModeSearchRadius = current.godModeSearchRadius
                )
            }
        }
    }

    fun updateCustomPrompts(
        customPrompt: String,
        promptShort: String,
        promptDetailed: String,
        promptInterestingFacts: String
    ) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(
                    customPrompt = customPrompt,
                    promptShort = promptShort,
                    promptDetailed = promptDetailed,
                    promptInterestingFacts = promptInterestingFacts
                )
            }
        }
    }

    fun changeDetailLevelAndRegenerate(newLevel: String) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(detailLevel = newLevel))
        }
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(detailLevel = newLevel)
            }
        }
        val activePlace = _uiState.value.activePlace
        if (activePlace != null) {
            guideCache.remove(activePlace.id)
            selectPlaceAndGenerateGuide(activePlace)
        }
    }

    fun updateMapLayer(layer: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(mapLayer = layer)
            }
        }
    }

    fun handleSharedLocationText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingPlaces = true, error = null) }
            
            // Extract URL from text (usually Google Maps shared text has a URL)
            val urlPattern = """https?://[^\s]+""".toRegex()
            val match = urlPattern.find(text)
            
            var lat: Double? = null
            var lon: Double? = null
            
            if (match != null) {
                val url = match.value
                try {
                    // Make a network call using Ktor client to expand redirect short URLs
                    val client = io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp)
                    val response: io.ktor.client.statement.HttpResponse = client.get(url)
                    val finalUrl = response.call.request.url.toString()
                    client.close()
                    
                    // Match pattern like @lat,lon
                    val coordsRegex = """@(-?\d+\.\d+),(-?\d+\.\d+)""".toRegex()
                    val matchCoords = coordsRegex.find(finalUrl)
                    if (matchCoords != null) {
                        lat = matchCoords.groupValues[1].toDoubleOrNull()
                        lon = matchCoords.groupValues[2].toDoubleOrNull()
                    } else {
                        // Fallback parsing query parameters like ?q=lat,lon
                        val queryRegex = """q=(-?\d+\.\d+),(-?\d+\.\d+)""".toRegex()
                        val matchQuery = queryRegex.find(finalUrl)
                        if (matchQuery != null) {
                            lat = matchQuery.groupValues[1].toDoubleOrNull()
                            lon = matchQuery.groupValues[2].toDoubleOrNull()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Fallback: If URL coordinates resolution failed, geocode the text using Komoot Photon!
            if (lat == null || lon == null) {
                val cleanLines = text.replace(urlPattern, "")
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                if (cleanLines.isNotEmpty()) {
                    var coords = poiRepository.geocodeAddress(cleanLines.first())
                    if (coords == null && cleanLines.size > 1) {
                        coords = poiRepository.geocodeAddress(cleanLines.joinToString(" "))
                    }
                    if (coords != null) {
                        lat = coords.first
                        lon = coords.second
                    }
                }
            }

            if (lat != null && lon != null) {
                stopLocationTracking()
                
                val mockLocation = UserLocation(lat, lon, 0f)
                _uiState.update { 
                    it.copy(
                        currentLocation = mockLocation,
                        mapCenterLocation = mockLocation,
                        locationTrackingActive = false,
                        error = "Shared location resolved! Showing spots nearby."
                    ) 
                }
                searchPlacesNear(lat, lon, isAutoTrigger = false)
            } else {
                _uiState.update { 
                    it.copy(
                        isSearchingPlaces = false,
                        error = "Could not resolve shared location. Try searching for the place manually."
                    ) 
                }
            }
        }
    }

    fun searchCustomLocation(query: String) {
        if (query.isBlank()) return
        
        _uiState.update { it.copy(isSearchingPlaces = true, error = null) }
        viewModelScope.launch {
            val coords = poiRepository.geocodeAddress(query)
            if (coords != null) {
                stopLocationTracking()
                
                val mockLocation = UserLocation(coords.first, coords.second, 0f)
                _uiState.update { 
                    it.copy(
                        currentLocation = mockLocation,
                        mapCenterLocation = mockLocation,
                        locationTrackingActive = false,
                        error = "Search resolved! Showing spots nearby.",
                        mapResetTrigger = it.mapResetTrigger + 1
                    ) 
                }
                searchPlacesNear(coords.first, coords.second)
            } else {
                _uiState.update { 
                    it.copy(
                        isSearchingPlaces = false,
                        error = "Could not find any location matching '$query'. Try another query."
                    ) 
                }
            }
        }
    }

    fun updateMapCenterLocation(lat: Double, lon: Double) {
        _uiState.update { it.copy(mapCenterLocation = UserLocation(lat, lon, 0f)) }
        viewModelScope.launch {
            settingsRepository.updateLastLocation(lat, lon)
        }
    }

    fun toggleMapSearchMode() {
        val nextMode = !_uiState.value.useMapCenter
        _uiState.update { 
            if (!nextMode && it.currentLocation != null) {
                it.copy(useMapCenter = nextMode, mapCenterLocation = it.currentLocation, mapResetTrigger = it.mapResetTrigger + 1)
            } else {
                it.copy(useMapCenter = nextMode)
            }
        }
        if (nextMode) {
            stopLocationTracking()
        } else {
            startLocationTracking()
        }
    }

    fun scanMapCenterArea() {
        val center = _uiState.value.mapCenterLocation ?: return
        viewModelScope.launch {
            searchPlacesNear(center.latitude, center.longitude, isAutoTrigger = false)
        }
    }

    fun toggleGodMode(active: Boolean) {
        val isDbDownloaded = poiRepository.isDatabaseDownloaded(getApplication())
        if (active && !isDbDownloaded) {
            _uiState.update { it.copy(dbDownloadProgress = 0f, dbDownloadError = null) }
            return
        }
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(isGodModeActive = active)
            }
            refreshPlacesForCurrentState()
        }
    }

    fun downloadDatabase() {
        _uiState.update { it.copy(dbDownloadProgress = 0f, dbDownloadError = null) }
        viewModelScope.launch {
            val dbFile = poiRepository.getDatabaseFile(getApplication())
            val url = "https://raw.githubusercontent.com/abhi8569/Atlas-Obscura-Database/main/atlas_obscura.db"
            DatabaseDownloader.downloadFile(url, dbFile).collect { state ->
                when (state) {
                    is DownloadState.Progress -> {
                        _uiState.update { it.copy(dbDownloadProgress = state.progress) }
                    }
                    is DownloadState.Success -> {
                        _uiState.update { 
                            it.copy(
                                dbDownloadProgress = null,
                                isDatabaseDownloaded = true
                            ) 
                        }
                        settingsRepository.updateSettings { current ->
                            current.copy(isGodModeActive = true)
                        }
                        refreshPlacesForCurrentState()
                    }
                    is DownloadState.Error -> {
                        _uiState.update { 
                            it.copy(
                                dbDownloadProgress = null,
                                dbDownloadError = "Failed to download database: ${state.message}"
                            ) 
                        }
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        _uiState.update { it.copy(dbDownloadProgress = null, dbDownloadError = null) }
    }

    fun updateGodModeSearchRadius(radiusInKm: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(godModeSearchRadius = radiusInKm)
            }
            refreshPlacesForCurrentState()
        }
    }

    fun updateSearchRadius(radiusInMeters: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(searchRadius = radiusInMeters)
            }
            refreshPlacesForCurrentState()
        }
    }

    fun refreshPlacesForCurrentState() {
        val center = if (_uiState.value.useMapCenter) {
            _uiState.value.mapCenterLocation
        } else {
            _uiState.value.currentLocation
        }
        if (center != null) {
            viewModelScope.launch {
                searchPlacesNear(center.latitude, center.longitude, isAutoTrigger = false)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        ttsManager.shutdown()
    }
}
