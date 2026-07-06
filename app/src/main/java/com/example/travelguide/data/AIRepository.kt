package com.example.travelguide.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = ""
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int? = null
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList()
)

@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatMessage
)

class AIRepository {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    suspend fun generateGuideContent(
        settings: TourGuideSettings,
        place: PlaceOfInterest
    ): String {
        val endpointUrl = if (settings.baseUrl.endsWith("/chat/completions")) {
            settings.baseUrl
        } else {
            val cleanBase = settings.baseUrl.trimEnd('/')
            "$cleanBase/chat/completions"
        }

        // Prompt formatting based on information detail level
        val detailInstruction = when (settings.detailLevel) {
            "Short" -> settings.promptShort
            "Detailed" -> settings.promptDetailed
            "Interesting Facts" -> settings.promptInterestingFacts
            else -> settings.promptShort
        }

        val cityAndCountry = PoiRepository().reverseGeocode(place.lat, place.lon)
        val city = cityAndCountry?.first ?: "Unknown City"
        val country = cityAndCountry?.second ?: "Unknown Country"

        val isGodModePlace = place.tags["godmode"] == "true"
        val tagsText = if (isGodModePlace) {
            val desc = place.tags["description"] ?: ""
            val subt = place.tags["subtitle"] ?: ""
            val rawTags = place.tags["tags"] ?: ""
            "Description: $desc\nSubtitle: $subt\nRaw Tags: $rawTags"
        } else {
            place.tags.entries.joinToString { "${it.key}=${it.value}" }
        }
        
        val customPromptString = settings.customPrompt.trim()
        val systemPrompt = when {
            customPromptString.contains("{info level goes here}") -> {
                customPromptString.replace("{info level goes here}", detailInstruction)
            }
            customPromptString.contains("{information level goes here}") -> {
                customPromptString.replace("{information level goes here}", detailInstruction)
            }
            customPromptString.contains("{information_level}") -> {
                customPromptString.replace("{information_level}", detailInstruction)
            }
            customPromptString.endsWith(".") -> {
                "$customPromptString $detailInstruction"
            }
            else -> {
                "$customPromptString. $detailInstruction"
            }
        }

        val prompt = """
            System Instructions: $systemPrompt

            Context information:
            The traveler is near this place of interest:
            Spot Name: ${place.name}
            City: $city
            Country: $country
            Category: ${place.category}
            Location Details: $tagsText

            Narrator Constraints:
            1. Speak directly to the traveler.
            2. Do not include markdown formatting, bold headers, or titles in your voice text (since this will be read aloud by TTS).
        """.trimIndent()

        val maxTokens = when (settings.detailLevel) {
            "Short" -> 100
            "Interesting Facts" -> 150
            else -> 500
        }

        return try {
            val requestBody = ChatCompletionRequest(
                model = settings.modelName,
                messages = listOf(
                    ChatMessage(role = "user", content = prompt)
                ),
                max_tokens = maxTokens
            )

            val response: ChatCompletionResponse = client.post(endpointUrl) {
                contentType(ContentType.Application.Json)
                if (settings.apiKey.isNotEmpty()) {
                    header("Authorization", "Bearer ${settings.apiKey}")
                }
                setBody(requestBody)
            }.body()

            response.choices.firstOrNull()?.message?.content ?: "Sorry, I couldn't generate a guide for this location."
        } catch (e: Exception) {
            e.printStackTrace()
            "Error contacting AI Tour Guide service: ${e.localizedMessage ?: "Connection failed"}. Please verify your API settings."
        }
    }

    suspend fun chatWithGuide(
        settings: TourGuideSettings,
        place: PlaceOfInterest,
        chatHistory: List<ChatMessage>,
        newQuestion: String
    ): String {
        val endpointUrl = if (settings.baseUrl.endsWith("/chat/completions")) {
            settings.baseUrl
        } else {
            val cleanBase = settings.baseUrl.trimEnd('/')
            "$cleanBase/chat/completions"
        }

        val systemPrompt = "You are a charismatic, helpful AI Tour Guide currently guiding the traveler at ${place.name}. Answer their questions conversationally and naturally. Keep answers relatively short and suitable to be read aloud."
        
        val messages = mutableListOf<ChatMessage>().apply {
            add(ChatMessage(role = "system", content = systemPrompt))
            addAll(chatHistory)
            add(ChatMessage(role = "user", content = newQuestion))
        }

        return try {
            val requestBody = ChatCompletionRequest(
                model = settings.modelName,
                messages = messages,
                max_tokens = 150
            )

            val response: ChatCompletionResponse = client.post(endpointUrl) {
                contentType(ContentType.Application.Json)
                if (settings.apiKey.isNotEmpty()) {
                    header("Authorization", "Bearer ${settings.apiKey}")
                }
                setBody(requestBody)
            }.body()

            response.choices.firstOrNull()?.message?.content ?: "I don't know the answer to that."
        } catch (e: Exception) {
            e.printStackTrace()
            "Failed to reach AI service: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String): List<String> {
        return try {
            val cleanBase = baseUrl.trimEnd('/')
            val endpointUrl = if (cleanBase.endsWith("/models")) cleanBase else "$cleanBase/models"
            
            val response: String = client.get(endpointUrl) {
                if (apiKey.isNotEmpty()) {
                    header("Authorization", "Bearer $apiKey")
                }
            }.body()
            
            val jsonParser = Json { ignoreUnknownKeys = true }
            val parsed = jsonParser.decodeFromString<ModelsResponse>(response)
            parsed.data.map { it.id }.sorted()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

@Serializable
data class ModelItem(val id: String)

@Serializable
data class ModelsResponse(val data: List<ModelItem>)
