package com.example.travelguide.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import android.location.Location

@Serializable
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

@Serializable
data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String>? = null
)

@Serializable
data class OverpassCenter(
    val lat: Double,
    val lon: Double
)

data class PlaceOfInterest(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val distance: Float,
    val tags: Map<String, String> = emptyMap()
)

class PoiRepository {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    suspend fun fetchNearbyPlaces(
        userLat: Double,
        userLon: Double,
        radiusInMeters: Int
    ): List<PlaceOfInterest> {
        val query = """
            [out:json][timeout:25];
            (
              node["tourism"](around:$radiusInMeters,$userLat,$userLon);
              way["tourism"](around:$radiusInMeters,$userLat,$userLon);
              node["historic"](around:$radiusInMeters,$userLat,$userLon);
              way["historic"](around:$radiusInMeters,$userLat,$userLon);
              node["landmark"](around:$radiusInMeters,$userLat,$userLon);
            );
            out center;
        """.trimIndent()

        return try {
            val response: OverpassResponse = client.post("https://overpass-api.de/api/interpreter") {
                setBody(query)
            }.body()

            response.elements
                .filter { it.tags != null && it.tags.containsKey("name") }
                .map { element ->
                    val name = element.tags?.get("name") ?: "Unknown Spot"
                    val lat = element.lat ?: element.center?.lat ?: userLat
                    val lon = element.lon ?: element.center?.lon ?: userLon
                    
                    val category = when {
                        element.tags?.containsKey("historic") == true -> "Historic: " + (element.tags["historic"]?.replaceFirstChar { it.uppercase() } ?: "")
                        element.tags?.containsKey("tourism") == true -> element.tags["tourism"]?.replaceFirstChar { it.uppercase() } ?: "Tourism"
                        else -> "Attraction"
                    }

                    val results = FloatArray(1)
                    Location.distanceBetween(userLat, userLon, lat, lon, results)
                    val distance = results[0]

                    PlaceOfInterest(
                        id = element.id.toString(),
                        name = name,
                        lat = lat,
                        lon = lon,
                        category = category,
                        distance = distance,
                        tags = element.tags ?: emptyMap()
                    )
                }
                .sortedBy { it.distance }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun geocodeAddress(query: String): Pair<Double, Double>? {
        return try {
            val rawClient = HttpClient(OkHttp)
            val response: String = rawClient.get("https://photon.komoot.io/api/") {
                parameter("q", query)
                parameter("limit", "1")
            }.body()
            rawClient.close()

            val jsonObject = Json { ignoreUnknownKeys = true }
                .parseToJsonElement(response).jsonObject
            val features = jsonObject["features"]?.jsonArray
            if (features != null && features.isNotEmpty()) {
                val firstFeature = features[0].jsonObject
                val geometry = firstFeature["geometry"]?.jsonObject
                val coordinates = geometry?.get("coordinates")?.jsonArray
                if (coordinates != null && coordinates.size >= 2) {
                    val lon = coordinates[0].jsonPrimitive.content.toDoubleOrNull()
                    val lat = coordinates[1].jsonPrimitive.content.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        Pair(lat, lon)
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): Pair<String, String>? {
        return try {
            val rawClient = HttpClient(OkHttp)
            val response: String = rawClient.get("https://photon.komoot.io/reverse") {
                parameter("lat", lat)
                parameter("lon", lon)
            }.body()
            rawClient.close()

            val jsonObject = Json { ignoreUnknownKeys = true }
                .parseToJsonElement(response).jsonObject
            val features = jsonObject["features"]?.jsonArray
            if (features != null && features.isNotEmpty()) {
                val properties = features[0].jsonObject["properties"]?.jsonObject
                val city = properties?.get("city")?.jsonPrimitive?.content ?: 
                           properties?.get("town")?.jsonPrimitive?.content ?: 
                           properties?.get("village")?.jsonPrimitive?.content ?: "Unknown City"
                val country = properties?.get("country")?.jsonPrimitive?.content ?: "Unknown Country"
                Pair(city, country)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getDatabaseFile(context: android.content.Context): java.io.File {
        return context.getDatabasePath("atlas_obscura.db")
    }

    fun isDatabaseDownloaded(context: android.content.Context): Boolean {
        val file = getDatabaseFile(context)
        return file.exists() && file.length() > 0
    }

    fun fetchLocalAtlasObscuraPlaces(
        context: android.content.Context,
        centerLat: Double,
        centerLon: Double,
        radiusInKm: Double,
        minLat: Double? = null,
        maxLat: Double? = null,
        minLon: Double? = null,
        maxLon: Double? = null
    ): List<PlaceOfInterest> {
        val dbFile = getDatabaseFile(context)
        if (!dbFile.exists()) return emptyList()

        val finalMinLat: Double
        val finalMaxLat: Double
        val finalMinLon: Double
        val finalMaxLon: Double

        if (minLat != null && maxLat != null && minLon != null && maxLon != null) {
            finalMinLat = minLat
            finalMaxLat = maxLat
            finalMinLon = minLon
            finalMaxLon = maxLon
        } else {
            val latDelta = radiusInKm / 111.0
            val lonDelta = radiusInKm / (111.0 * Math.cos(Math.toRadians(centerLat)))
            finalMinLat = centerLat - latDelta
            finalMaxLat = centerLat + latDelta
            finalMinLon = centerLon - lonDelta
            finalMaxLon = centerLon + lonDelta
        }

        val list = mutableListOf<PlaceOfInterest>()
        var db: android.database.sqlite.SQLiteDatabase? = null
        var cursor: android.database.Cursor? = null

        try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            cursor = db.rawQuery(
                "SELECT id, title, subtitle, lat, lng, description, tags, url FROM places WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?",
                arrayOf(finalMinLat.toString(), finalMaxLat.toString(), finalMinLon.toString(), finalMaxLon.toString())
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                val subtitle = cursor.getString(cursor.getColumnIndexOrThrow("subtitle")) ?: ""
                val lat = cursor.getDouble(cursor.getColumnIndexOrThrow("lat"))
                val lng = cursor.getDouble(cursor.getColumnIndexOrThrow("lng"))
                val description = cursor.getString(cursor.getColumnIndexOrThrow("description")) ?: ""
                val tags = cursor.getString(cursor.getColumnIndexOrThrow("tags")) ?: ""
                val url = cursor.getString(cursor.getColumnIndexOrThrow("url")) ?: ""

                val results = FloatArray(1)
                Location.distanceBetween(centerLat, centerLon, lat, lng, results)
                val distance = results[0]

                if (distance <= radiusInKm * 1000) {
                    list.add(
                        PlaceOfInterest(
                            id = "godmode_$id",
                            name = title,
                            lat = lat,
                            lon = lng,
                            category = "Unusual Spot",
                            distance = distance,
                            tags = mapOf(
                                "description" to description,
                                "subtitle" to subtitle,
                                "tags" to tags,
                                "url" to url,
                                "godmode" to "true"
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
            db?.close()
        }

        return list.sortedBy { it.distance }
    }
}
