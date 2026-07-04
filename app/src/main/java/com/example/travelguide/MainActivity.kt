package com.example.travelguide

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.travelguide.theme.TravelGuideTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SharedLocationManager {
    private val _sharedText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sharedText: SharedFlow<String> = _sharedText.asSharedFlow()

    fun sendText(text: String) {
        _sharedText.tryEmit(text)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OsmDroid configuration before rendering content
        val osmdroidConfig = org.osmdroid.config.Configuration.getInstance()
        osmdroidConfig.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        osmdroidConfig.userAgentValue = "TravelGuideAndroidApp/1.0"
        osmdroidConfig.osmdroidBasePath = java.io.File(cacheDir, "osmdroid")
        osmdroidConfig.osmdroidTileCache = java.io.File(cacheDir, "osmdroid/tiles")

        enableEdgeToEdge()
        setContent {
            TravelGuideTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color(0xFF0F0E17)) {
                    MainNavigation()
                }
            }
        }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                SharedLocationManager.sendText(sharedText)
            }
        }
    }
}
