package com.example.travelguide.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.travelguide.data.ChatMessage
import com.example.travelguide.data.PlaceOfInterest

fun openGoogleMaps(context: Context, place: PlaceOfInterest) {
    val uri = "geo:${place.lat},${place.lon}?q=${Uri.encode(place.name)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback browser URL if Google Maps app is not installed
        val webUri = "https://www.google.com/maps/search/?api=1&query=${Uri.encode(place.name)}&query_place_id=${place.lat},${place.lon}"
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri))
        context.startActivity(webIntent)
    }
}

fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon = Math.toRadians(lon2 - lon1)
    val rLat1 = Math.toRadians(lat1)
    val rLat2 = Math.toRadians(lat2)
    val y = Math.sin(dLon) * Math.cos(rLat2)
    val x = Math.cos(rLat1) * Math.sin(rLat2) - Math.sin(rLat1) * Math.cos(rLat2) * Math.cos(dLon)
    val brng = Math.atan2(y, x)
    return ((Math.toDegrees(brng) + 360) % 360).toFloat()
}

fun getCardinalDirection(bearing: Float): String {
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
    val index = Math.round(((bearing % 360) / 45.0)).toInt()
    return directions[index]
}

fun Context.findActivity(): androidx.activity.ComponentActivity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is androidx.activity.ComponentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun LeafletMapView(
    userLat: Double?,
    userLon: Double?,
    places: List<PlaceOfInterest>,
    cardBgColor: Color,
    secondaryGlow: Color,
    initialCenterLat: Double?,
    initialCenterLon: Double?,
    currentLayer: String,
    onLayerChanged: (String) -> Unit,
    mapResetTrigger: Int,
    activePlace: PlaceOfInterest? = null,
    isGodModeActive: Boolean = false,
    modifier: Modifier = Modifier,
    onMarkerClick: (PlaceOfInterest) -> Unit = {},
    onMapCenterChanged: (Double, Double, Double, Double?, Double?, Double?, Double?) -> Unit = { _, _, _, _, _, _, _ -> },
    onMapClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentInitialLat by rememberUpdatedState(initialCenterLat)
    val currentInitialLon by rememberUpdatedState(initialCenterLon)

    // 1. Create a beautiful native user location marker icon (Teal circle with white border)
    val userMarkerIcon = remember {
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.parseColor("#03DAC6")) // Teal accent
            setStroke(5, android.graphics.Color.WHITE)
            setSize(40, 40)
        }
    }

    // 2. Create attraction markers (Deep purple circle with white border)
    val placeMarkerIcon = remember {
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.parseColor("#6200EE")) // Purple theme accent
            setStroke(4, android.graphics.Color.WHITE)
            setSize(32, 32)
        }
    }

    // 3. Highlighted active selected place marker icon (Neon pink circle with white border)
    val activePlaceMarkerIcon = remember {
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.parseColor("#FF007F")) // Highlight Pink
            setStroke(5, android.graphics.Color.WHITE)
            setSize(42, 42)
        }
    }

    // Initialize the MapView widget natively
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.5)
            isTilesScaledToDpi = true // Crisp, high-resolution rendering
            addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) {
                    v.invalidate()
                    post {
                        val lat = currentInitialLat
                        val lon = currentInitialLon
                        if (lat != null && lon != null) {
                            controller.setCenter(GeoPoint(lat, lon))
                        }
                    }
                }
                override fun onViewDetachedFromWindow(v: android.view.View) {}
            })
        }
    }

    val darkFilter = remember {
        val matrix = floatArrayOf(
            -1.0f, 0.0f, 0.0f, 0.0f, 255.0f, // Red
            0.0f, -1.0f, 0.0f, 0.0f, 255.0f, // Green
            0.0f, 0.0f, -1.0f, 0.0f, 255.0f, // Blue
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f     // Alpha
        )
        android.graphics.ColorMatrixColorFilter(matrix)
    }

    LaunchedEffect(currentLayer) {
        when (currentLayer) {
            "dark" -> {
                mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                mapView.overlayManager.tilesOverlay.setColorFilter(darkFilter)
            }
            "light" -> {
                mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                mapView.overlayManager.tilesOverlay.setColorFilter(null)
            }
            "satellite" -> {
                mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.USGS_SAT)
                mapView.overlayManager.tilesOverlay.setColorFilter(null)
            }
        }
        mapView.invalidate()
    }

    var lastCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var zoomLevel by remember { mutableDoubleStateOf(mapView.zoomLevelDouble) }

    val activeMarkers = remember { mutableMapOf<String, Marker>() }
    var currentActiveMarker by remember { mutableStateOf<Marker?>(null) }
    var currentUserMarker by remember { mutableStateOf<Marker?>(null) }
    var currentEventsOverlay by remember { mutableStateOf<org.osmdroid.views.overlay.MapEventsOverlay?>(null) }

    DisposableEffect(mapView) {
        val listener = object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                mapView.post {
                    val center = mapView.mapCenter
                    lastCenter = GeoPoint(center.latitude, center.longitude)
                    val box = mapView.boundingBox
                    onMapCenterChanged(center.latitude, center.longitude, mapView.zoomLevelDouble, box.latSouth, box.latNorth, box.lonWest, box.lonEast)
                }
                return true
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                mapView.post {
                    val center = mapView.mapCenter
                    lastCenter = GeoPoint(center.latitude, center.longitude)
                    zoomLevel = mapView.zoomLevelDouble
                    val box = mapView.boundingBox
                    onMapCenterChanged(center.latitude, center.longitude, mapView.zoomLevelDouble, box.latSouth, box.latNorth, box.lonWest, box.lonEast)
                }
                return true
            }
        }
        mapView.addMapListener(listener)
        onDispose {
            // Cleared internally
        }
    }

    // Centering viewport: snap map center only when a programmatic reset is triggered from outside
    LaunchedEffect(mapResetTrigger) {
        if (initialCenterLat != null && initialCenterLon != null) {
            val target = GeoPoint(initialCenterLat, initialCenterLon)
            lastCenter = target
            mapView.controller.setCenter(target)
        }
    }

    val currentOnMapClick by rememberUpdatedState(onMapClick)
    // Handle state updates natively and map updates smoothly on UI thread
    LaunchedEffect(userLat, userLon, places, activePlace) {
        // Ensure events overlay is present
        if (currentEventsOverlay == null) {
            val eventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint?): Boolean {
                    currentOnMapClick()
                    return true
                }
                override fun longPressHelper(p: org.osmdroid.util.GeoPoint?): Boolean {
                    return false
                }
            })
            mapView.overlays.add(eventsOverlay)
            currentEventsOverlay = eventsOverlay
        }

        // Manage user marker
        if (userLat != null && userLon != null) {
            val userPoint = GeoPoint(userLat, userLon)
            val userM = currentUserMarker
            if (userM == null) {
                val newUserM = Marker(mapView).apply {
                    position = userPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "You"
                    icon = userMarkerIcon
                }
                mapView.overlays.add(newUserM)
                currentUserMarker = newUserM
            } else {
                userM.position = userPoint
            }
        } else {
            currentUserMarker?.let {
                mapView.overlays.remove(it)
                currentUserMarker = null
            }
        }

        // Manage active place marker
        currentActiveMarker?.let {
            mapView.overlays.remove(it)
            currentActiveMarker = null
        }
        if (activePlace != null) {
            val activePoint = GeoPoint(activePlace.lat, activePlace.lon)
            val newActiveM = Marker(mapView).apply {
                position = activePoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = activePlace.name
                subDescription = activePlace.category
                icon = activePlaceMarkerIcon
                setOnMarkerClickListener { marker, map ->
                    onMarkerClick(activePlace)
                    true
                }
            }
            mapView.overlays.add(newActiveM)
            currentActiveMarker = newActiveM
        }

        // Manage regular place markers
        val newPlaceIds = places.map { it.id }.toSet()
        val iterator = activeMarkers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!newPlaceIds.contains(entry.key) || entry.key == activePlace?.id) {
                mapView.overlays.remove(entry.value)
                iterator.remove()
            }
        }

        places.forEach { place ->
            if (place.id == activePlace?.id) return@forEach
            if (!activeMarkers.containsKey(place.id)) {
                val placePoint = GeoPoint(place.lat, place.lon)
                val placeMarker = Marker(mapView).apply {
                    position = placePoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = place.name
                    subDescription = place.category
                    icon = placeMarkerIcon
                    setOnMarkerClickListener { marker, map ->
                        onMarkerClick(place)
                        true
                    }
                }
                mapView.overlays.add(placeMarker)
                activeMarkers[place.id] = placeMarker
            }
        }

        mapView.invalidate()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                it.onDetach() // Cleanup tile downloading on dispose
            }
        )
        if (isGodModeActive && zoomLevel < 11.5) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1F1D2C).copy(alpha = 0.9f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🔍 Zoom in to view spots",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
        // Floating top-right controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Center on user position manually
            if (userLat != null && userLon != null) {
                IconButton(
                    onClick = {
                        mapView.controller.animateTo(GeoPoint(userLat, userLon))
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF1F1D2C).copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Center on me",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }


    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(app) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }

    // Compass device orientation streaming
    var deviceHeading by remember { mutableFloatStateOf(0f) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager }
    
    DisposableEffect(sensorManager) {
        if (sensorManager == null) {
            onDispose {}
        } else {
            val rotationSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
            if (rotationSensor == null) {
                onDispose {}
            } else {
                val listener = object : android.hardware.SensorEventListener {
                    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                        if (event != null && event.sensor.type == android.hardware.Sensor.TYPE_ROTATION_VECTOR) {
                            val rotationMatrix = FloatArray(9)
                            android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            val orientationValues = FloatArray(3)
                            android.hardware.SensorManager.getOrientation(rotationMatrix, orientationValues)
                            val azimuthDegrees = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                            // convert heading to 0..360 range
                            deviceHeading = (azimuthDegrees + 360) % 360
                        }
                    }
                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(listener, rotationSensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
                onDispose {
                    sensorManager.unregisterListener(listener)
                }
            }
        }
    }

    // Intercept Back Press to go back from Settings instead of exiting the app
    if (showSettings) {
        BackHandler {
            showSettings = false
        }
    } else {
        var lastBackPressTime by remember { mutableLongStateOf(0L) }
        val activity = context as? android.app.Activity
        BackHandler {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000L) {
                activity?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Request permissions launcher
    val hasPermission = remember { mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasPermission.value = fineGranted || coarseGranted
        if (hasPermission.value) {
            if (state.useMapCenter) {
                viewModel.toggleMapSearchMode()
            } else {
                viewModel.toggleLocationTracking()
            }
        } else {
            Toast.makeText(context, "Location permission is required for real-time tour guide", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger initial permission check or setup
    LaunchedEffect(Unit) {
        val fine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        hasPermission.value = fine || coarse
    }

    // Toast error messages if they occur
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Premium styling constants
    val isLightMode = !state.settings.isDarkMode
    val darkBgColor = if (isLightMode) Color(0xFFF3F4F6) else Color(0xFF000000) // Light grey-blue vs OLED black
    val cardBgColor = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF121212) // Pristine white vs Slate grey
    val primaryGlow = if (state.settings.isGodModeActive) {
        if (isLightMode) Color(0xFFD500F9) else Color(0xFFE040FB)
    } else {
        if (isLightMode) Color(0xFF4F46E5) else Color(0xFF7C4DFF)
    }
    val secondaryGlow = if (state.settings.isGodModeActive) {
        if (isLightMode) Color(0xFFC2185B) else Color(0xFFFF4081)
    } else {
        if (isLightMode) Color(0xFF0D9488) else Color(0xFF00E5FF)
    }
    val textColor = if (isLightMode) Color(0xFF111827) else Color(0xFFFFFFFF) // Dark Charcoal vs Crisp White
    val subTextColor = if (isLightMode) Color(0xFF4B5563) else Color(0xFFB3B3B3) // Deep Muted Gray vs High-contrast Silver

    LaunchedEffect(isLightMode) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        activity.enableEdgeToEdge(
            statusBarStyle = if (isLightMode) {
                androidx.activity.SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            } else {
                androidx.activity.SystemBarStyle.dark(
                    android.graphics.Color.TRANSPARENT
                )
            },
            navigationBarStyle = if (isLightMode) {
                androidx.activity.SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            } else {
                androidx.activity.SystemBarStyle.dark(
                    android.graphics.Color.TRANSPARENT
                )
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkBgColor)
    ) {
        // Gradient glow elements in background
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .background(Brush.radialGradient(colors = listOf(primaryGlow.copy(alpha = 0.15f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .background(Brush.radialGradient(colors = listOf(secondaryGlow.copy(alpha = 0.12f), Color.Transparent)))
        )

        if (state.dbDownloadProgress != null) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelDownload() },
                title = {
                    Text(
                        text = if (state.dbDownloadProgress == 0f) "Download Required" else "Downloading Database",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (state.dbDownloadProgress == 0f) {
                            Text(
                                text = "God Mode requires the offline Atlas Obscura database (approx 45MB). Download it now to unlock 31,000+ unusual spots offline?",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                text = "Downloading database files from GitHub...",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                            LinearProgressIndicator(
                                progress = { state.dbDownloadProgress ?: 0f },
                                modifier = Modifier.fillMaxWidth(),
                                color = primaryGlow,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                            Text(
                                text = "${((state.dbDownloadProgress ?: 0f) * 100).toInt()}%",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    if (state.dbDownloadProgress == 0f) {
                        TextButton(onClick = { viewModel.downloadDatabase() }) {
                            Text("Download", color = primaryGlow, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    if (state.dbDownloadProgress == 0f) {
                        TextButton(onClick = { viewModel.cancelDownload() }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                },
                containerColor = cardBgColor,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (state.dbDownloadError != null) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelDownload() },
                title = { Text("Download Failed", color = Color.Red, fontWeight = FontWeight.Bold) },
                text = { Text(state.dbDownloadError ?: "Unknown error", color = Color.LightGray) },
                confirmButton = {
                    TextButton(onClick = { viewModel.cancelDownload() }) {
                        Text("OK", color = primaryGlow, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = cardBgColor,
                shape = RoundedCornerShape(16.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "AI TOUR GUIDE",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (state.locationTrackingActive) Color.Green else Color.Gray,
                                    RoundedCornerShape(4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.locationTrackingActive) "Tracking Active" else "Tracking Stopped",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = {
                            if (!hasPermission.value) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                viewModel.toggleLocationTracking()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (state.locationTrackingActive) primaryGlow.copy(alpha = 0.3f) else cardBgColor
                        )
                    ) {
                        Icon(
                            imageVector = if (state.locationTrackingActive) Icons.Default.LocationOn else Icons.Default.LocationOff,
                            contentDescription = "Toggle Tracking",
                            tint = if (state.locationTrackingActive) secondaryGlow else textColor
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            viewModel.updateDarkMode(!state.settings.isDarkMode)
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = cardBgColor
                        )
                    ) {
                        Icon(
                            imageVector = if (state.settings.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark/Light Mode",
                            tint = textColor
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (state.settings.isGodModeActive) primaryGlow.copy(alpha = 0.2f) else cardBgColor)
                            .border(
                                1.dp,
                                if (state.settings.isGodModeActive) primaryGlow.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                viewModel.toggleGodMode(!state.settings.isGodModeActive)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (state.settings.isGodModeActive) Icons.Default.AutoAwesome else Icons.Default.Explore,
                                contentDescription = "Mode",
                                tint = if (state.settings.isGodModeActive) secondaryGlow else textColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (state.settings.isGodModeActive) "God Mode" else "Standard",
                                color = if (state.settings.isGodModeActive) Color.White else textColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { showSettings = !showSettings },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (showSettings) primaryGlow else cardBgColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (showSettings) Color.White else textColor
                        )
                    }
                }
            }



            // Main Content Area
            ContentArea(
                showSettings = showSettings,
                onSettingsDismiss = { showSettings = false },
                state = state,
                vm = viewModel,
                deviceHeading = deviceHeading,
                onToggleMapSearchMode = {
                    if (state.useMapCenter && !hasPermission.value) {
                        permissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        viewModel.toggleMapSearchMode()
                    }
                },
                cardBgColor = cardBgColor,
                primaryGlow = primaryGlow,
                secondaryGlow = secondaryGlow,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ContentArea(
    showSettings: Boolean,
    onSettingsDismiss: () -> Unit,
    state: TourGuideUiState,
    vm: MainScreenViewModel,
    deviceHeading: Float,
    onToggleMapSearchMode: () -> Unit,
    cardBgColor: Color,
    primaryGlow: Color,
    secondaryGlow: Color,
    modifier: Modifier = Modifier
) {
    val isLightMode = !state.settings.isDarkMode
    val textColor = if (isLightMode) Color(0xFF111827) else Color(0xFFFFFFFF)
    val subTextColor = if (isLightMode) Color(0xFF4B5563) else Color(0xFFB3B3B3)

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !showSettings,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            DashboardView(
                state = state,
                deviceHeading = deviceHeading,
                onPlaceSelect = { vm.selectPlaceAndGenerateGuide(it) },
                onSelectPlaceWithoutNarration = { vm.selectPlaceWithoutNarration(it) },
                onSpeakAgain = { vm.speakGuideAgain() },
                onStopSpeaking = { vm.stopSpeaking() },
                onSendMessage = { vm.sendChatMessage(it) },
                onSpeechRateChange = { vm.updateSpeechRate(it) },
                onSearchCustomLocation = { vm.searchCustomLocation(it) },
                onMapCenterChanged = { lat, lon, zoom, minLat, maxLat, minLon, maxLon -> vm.updateMapCenterLocation(lat, lon, zoom, minLat, maxLat, minLon, maxLon) },
                onToggleMapSearchMode = onToggleMapSearchMode,
                onScanMapCenterArea = { vm.scanMapCenterArea() },
                onDetailLevelChange = { vm.changeDetailLevelAndRegenerate(it) },
                onLayerChanged = { vm.updateMapLayer(it) },
                onInterestsChange = { newInterests ->
                    vm.updateSettings(
                        providerName = state.settings.providerName,
                        apiKey = state.settings.apiKey,
                        baseUrl = state.settings.baseUrl,
                        modelName = state.settings.modelName,
                        searchRadius = state.settings.searchRadius,
                        updateInterval = state.settings.updateInterval,
                        detailLevel = state.settings.detailLevel,
                        speechRate = state.settings.speechRate,
                        speechPitch = state.settings.speechPitch,
                        autoPlay = state.settings.autoPlay,
                        interests = newInterests,
                        popularOnly = state.settings.popularOnly,
                        customPrompt = state.settings.customPrompt,
                        isDarkMode = state.settings.isDarkMode
                    )
                },
                cardBgColor = cardBgColor,
                primaryGlow = primaryGlow,
                secondaryGlow = secondaryGlow,
                vm = vm
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            SettingsView(
                settings = state.settings,
                fetchedModels = state.fetchedModels,
                isFetchingModels = state.isFetchingModels,
                onFetchModels = { url, key -> vm.fetchModelsList(url, key) },
                onSave = { provider, apiKey, baseUrl, model, radius, freq, detail, rate, pitch, auto, interests, popular, customPrompt, isDarkMode ->
                    vm.updateSettings(provider, apiKey, baseUrl, model, radius, freq, detail, rate, pitch, auto, interests, popular, customPrompt, isDarkMode)
                    onSettingsDismiss()
                },
                onSavePrompts = { base, sh, dt, inf ->
                    vm.updateCustomPrompts(base, sh, dt, inf)
                },
                cardBgColor = cardBgColor,
                primaryGlow = primaryGlow
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardView(
    state: TourGuideUiState,
    deviceHeading: Float,
    onPlaceSelect: (PlaceOfInterest) -> Unit,
    onSelectPlaceWithoutNarration: (PlaceOfInterest?) -> Unit,
    onSpeakAgain: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSearchCustomLocation: (String) -> Unit,
    onMapCenterChanged: (Double, Double, Double, Double?, Double?, Double?, Double?) -> Unit,
    onToggleMapSearchMode: () -> Unit,
    onScanMapCenterArea: () -> Unit,
    onDetailLevelChange: (String) -> Unit,
    onLayerChanged: (String) -> Unit,
    onInterestsChange: (String) -> Unit,
    cardBgColor: Color,
    primaryGlow: Color,
    secondaryGlow: Color,
    vm: MainScreenViewModel
) {
    val isLightMode = !state.settings.isDarkMode
    val textColor = if (isLightMode) Color(0xFF111827) else Color(0xFFFFFFFF)
    val subTextColor = if (isLightMode) Color(0xFF4B5563) else Color(0xFFB3B3B3)

    androidx.activity.compose.BackHandler(enabled = state.activePlace != null) {
        onSelectPlaceWithoutNarration(null)
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0: Map, 1: Discover, 2: Audio Guide
    var showFiltersPanel by remember { mutableStateOf(false) }

    // Auto-switch to Audio Guide tab ONLY when narration is active or is generating
    LaunchedEffect(state.guideContent, state.isGeneratingGuide) {
        if (state.guideContent != null || state.isGeneratingGuide) {
            activeTab = 2
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // High-aesthetic Glassmorphic Tabs
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = secondaryGlow,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = secondaryGlow
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                icon = { Icon(Icons.Default.Map, contentDescription = "Map View", modifier = Modifier.size(20.dp)) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = "List View", modifier = Modifier.size(20.dp)) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                icon = { Icon(Icons.Default.Hearing, contentDescription = "Audio Guide", modifier = Modifier.size(20.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab 0: Dedicated Full-Screen Map View (Retained in composition tree to preserve zoom level and coordinates)
        Box(
            modifier = if (activeTab == 0) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
            } else {
                Modifier.size(0.dp)
            }
        ) {
                if (!state.settings.isGodModeActive) {
                    key("standard_map") {
                        LeafletMapView(
                            userLat = state.currentLocation?.latitude,
                            userLon = state.currentLocation?.longitude,
                            places = state.nearbyPlaces,
                            cardBgColor = cardBgColor,
                            secondaryGlow = secondaryGlow,
                            initialCenterLat = if (state.useMapCenter) {
                                state.mapCenterLocation?.latitude ?: state.currentLocation?.latitude ?: 51.5074
                            } else {
                                state.currentLocation?.latitude ?: state.mapCenterLocation?.latitude ?: 51.5074
                            },
                            initialCenterLon = if (state.useMapCenter) {
                                state.mapCenterLocation?.longitude ?: state.currentLocation?.longitude ?: -0.1278
                            } else {
                                state.currentLocation?.longitude ?: state.mapCenterLocation?.longitude ?: -0.1278
                            },
                            currentLayer = state.settings.mapLayer,
                            onLayerChanged = onLayerChanged,
                            mapResetTrigger = state.mapResetTrigger,
                            activePlace = state.activePlace,
                            isGodModeActive = false,
                            modifier = Modifier.fillMaxSize(),
                            onMarkerClick = { place ->
                                onSelectPlaceWithoutNarration(place)
                            },
                            onMapCenterChanged = onMapCenterChanged,
                            onMapClick = { onSelectPlaceWithoutNarration(null) }
                        )
                    }
                } else {
                    key("god_map") {
                        LeafletMapView(
                            userLat = state.currentLocation?.latitude,
                            userLon = state.currentLocation?.longitude,
                            places = state.nearbyPlaces,
                            cardBgColor = cardBgColor,
                            secondaryGlow = secondaryGlow,
                            initialCenterLat = if (state.useMapCenter) {
                                state.mapCenterLocation?.latitude ?: state.currentLocation?.latitude ?: 51.5074
                            } else {
                                state.currentLocation?.latitude ?: state.mapCenterLocation?.latitude ?: 51.5074
                            },
                            initialCenterLon = if (state.useMapCenter) {
                                state.mapCenterLocation?.longitude ?: state.currentLocation?.longitude ?: -0.1278
                            } else {
                                state.currentLocation?.longitude ?: state.mapCenterLocation?.longitude ?: -0.1278
                            },
                            currentLayer = state.settings.mapLayer,
                            onLayerChanged = onLayerChanged,
                            mapResetTrigger = state.mapResetTrigger,
                            activePlace = state.activePlace,
                            isGodModeActive = true,
                            modifier = Modifier.fillMaxSize(),
                            onMarkerClick = { place ->
                                onSelectPlaceWithoutNarration(place)
                            },
                            onMapCenterChanged = onMapCenterChanged,
                            onMapClick = { onSelectPlaceWithoutNarration(null) }
                        )
                    }
                }

                // Dead Center Pin (only in Map Center search mode to indicate calculation center)
                if (state.useMapCenter) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = (-16).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PinDrop,
                            contentDescription = "Map Center Target Pin",
                            tint = primaryGlow,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Floating Controls Card at the Top of Map View
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Map Mode Toggle Icon (Location / Pin mode)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBgColor.copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .clickable { onToggleMapSearchMode() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.useMapCenter) Icons.Default.PinDrop else Icons.Default.MyLocation,
                                contentDescription = "Toggle Map Mode",
                                tint = secondaryGlow,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 2. Filter Icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (showFiltersPanel) primaryGlow else cardBgColor.copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .clickable { showFiltersPanel = !showFiltersPanel },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Toggle Filters",
                                tint = if (showFiltersPanel) Color.White else secondaryGlow,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 3. Search Bar
                        var searchQuery by remember { mutableStateOf("") }
                        val keyboardController = LocalSoftwareKeyboardController.current
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search places...", color = Color.Gray, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (searchQuery.isNotBlank()) {
                                            onSearchCustomLocation(searchQuery)
                                            keyboardController?.hide()
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = secondaryGlow)
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    onSearchCustomLocation(searchQuery)
                                    keyboardController?.hide()
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = primaryGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = cardBgColor.copy(alpha = 0.85f),
                                unfocusedContainerColor = cardBgColor.copy(alpha = 0.85f)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current Location snap/tracking button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (state.locationTrackingActive) primaryGlow else cardBgColor.copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .clickable {
                                    // Snaps to user location and starts tracking
                                    if (state.useMapCenter) {
                                        onToggleMapSearchMode()
                                    } else {
                                        vm.toggleLocationTracking()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.locationTrackingActive) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                contentDescription = "My Location Tracking",
                                tint = if (state.locationTrackingActive) Color.White else secondaryGlow,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Map Layers Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBgColor.copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .clickable {
                                    val nextLayer = when (state.settings.mapLayer) {
                                        "dark" -> "light"
                                        "light" -> "satellite"
                                        else -> "dark"
                                    }
                                    onLayerChanged(nextLayer)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Map Layers",
                                tint = secondaryGlow,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 4. Expanded Filter & Radius panel!
                    if (showFiltersPanel) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor.copy(alpha = 0.95f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, primaryGlow.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (state.settings.isGodModeActive) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("GOD MODE SEARCH RADIUS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("${state.settings.godModeSearchRadius} km", color = primaryGlow, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                    }
                                    Slider(
                                        value = state.settings.godModeSearchRadius.toFloat(),
                                        onValueChange = { vm.updateGodModeSearchRadius(it.toInt()) },
                                        valueRange = 1f..50f,
                                        steps = 49,
                                        colors = SliderDefaults.colors(thumbColor = primaryGlow, activeTrackColor = primaryGlow, inactiveTrackColor = textColor.copy(alpha = 0.15f))
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("SEARCH RADIUS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("${state.settings.searchRadius} meters", color = primaryGlow, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                    }
                                    Slider(
                                        value = state.settings.searchRadius.toFloat(),
                                        onValueChange = { vm.updateSearchRadius(it.toInt()) },
                                        valueRange = 100f..5000f,
                                        steps = 49,
                                        colors = SliderDefaults.colors(thumbColor = primaryGlow, activeTrackColor = primaryGlow, inactiveTrackColor = textColor.copy(alpha = 0.15f))
                                    )
                                }

                                if (state.availableInterests.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("FILTER BY TOPICS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("Select All", color = secondaryGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onInterestsChange("") })
                                            Text("Clear All", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onInterestsChange(state.availableInterests.joinToString(",")) })
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val activeInterests = state.settings.interests.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        state.availableInterests.forEach { interest ->
                                            val isSelected = !activeInterests.contains(interest)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    val newDisabledList = if (isSelected) activeInterests + interest else activeInterests.filter { it != interest }
                                                    onInterestsChange(newDisabledList.joinToString(","))
                                                },
                                                label = { Text(interest, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = primaryGlow,
                                                    selectedLabelColor = Color.White,
                                                    labelColor = textColor.copy(alpha = 0.8f),
                                                    containerColor = cardBgColor.copy(alpha = 0.5f)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Stacked Controls Column at the bottom center to prevent overlaps
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Scan Area Button Floating overlay (Visible only when no spot is selected and NOT in God Mode to maximize map space)
                    if (state.activePlace == null && !state.settings.isGodModeActive) {
                        Button(
                            onClick = { onScanMapCenterArea() },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryGlow),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan this Area", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Floating Detailed Card at bottom of map for active spot selection (Sleek and highly optimized for size)
                    state.activePlace?.let { place ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor.copy(alpha = 0.95f)),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(6.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(place.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(place.category, color = Color.Gray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Distance & Direction info
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val distText = if (place.distance >= 1000f) {
                                            String.format("%.1f km", place.distance / 1000f)
                                        } else {
                                            String.format("%.0f m", place.distance)
                                        }
                                        Text(
                                            text = distText,
                                            color = secondaryGlow,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )

                                        val context = LocalContext.current
                                        IconButton(
                                            onClick = { openGoogleMaps(context, place) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Directions,
                                                contentDescription = "Get Directions",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Action buttons (Website link in God Mode, Hear Guide in both)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (state.settings.isGodModeActive) {
                                            val url = place.tags["url"] ?: place.tags["website"]
                                            if (!url.isNullOrBlank()) {
                                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                                IconButton(
                                                    onClick = { uriHandler.openUri(url) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Language,
                                                        contentDescription = "Open Web Link",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Button(
                                            onClick = { 
                                                onPlaceSelect(place) // triggers narration and guide generation
                                                activeTab = 2 // switches to Audio Guide tab
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = primaryGlow),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Hear Guide", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        if (activeTab == 1) {
            // Tab 1: Discover List View
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Nearby Places Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Places Icon",
                            tint = secondaryGlow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "NEARBY PLACES (${state.nearbyPlaces.size})",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (state.isSearchingPlaces) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = secondaryGlow
                            )
                        }
                    }
                }

                if (state.nearbyPlaces.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBgColor)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val emptyText = when {
                                state.useMapCenter -> "No spots found in this map region.\nPan the map and click 'Scan this Area' to discover attractions."
                                state.locationTrackingActive -> "No spots match your interests nearby.\nTry enabling other interests above or modifying search radius."
                                else -> "Location tracking is stopped.\nClick tracking icon above to start discovering places."
                            }
                            Text(
                                text = emptyText,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(state.nearbyPlaces, key = { it.id }) { place ->
                        PlaceItem(
                            place = place,
                            isActive = state.activePlace?.id == place.id,
                            onClick = { 
                                onPlaceSelect(place) // triggers narration and guide generation
                                activeTab = 2 // switches to Audio Guide tab
                            },
                            cardBgColor = cardBgColor,
                            secondaryGlow = secondaryGlow
                        )
                    }
                }
            }
        }

        if (activeTab == 2) {
            // Guide View - Structured to fill screen height efficiently
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.activePlace == null && !state.isGeneratingGuide) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBgColor)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active audio guide.\n\nGo to the 'Discover' tab and select a location to start your guided narration.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    ActiveGuideCard(
                        place = state.activePlace,
                        guideContent = state.guideContent,
                        isSpeaking = state.isSpeaking,
                        isGenerating = state.isGeneratingGuide,
                        deviceHeading = deviceHeading,
                        userLocation = state.currentLocation,
                        settings = state.settings,
                        onSpeakAgain = onSpeakAgain,
                        onStopSpeaking = onStopSpeaking,
                        onSpeechRateChange = onSpeechRateChange,
                        onDetailLevelChange = onDetailLevelChange,
                        cardBgColor = cardBgColor,
                        primaryGlow = primaryGlow,
                        secondaryGlow = secondaryGlow,
                        modifier = Modifier.weight(1f)
                    )

                    // Interactive Chat Guide at bottom
                    if (state.guideContent != null && !state.isGeneratingGuide) {
                        ChatGuideCard(
                            chatHistory = state.chatHistory,
                            isSending = state.isSendingChatMessage,
                            onSendMessage = onSendMessage,
                            cardBgColor = cardBgColor,
                            primaryGlow = primaryGlow,
                            modifier = Modifier.wrapContentHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveGuideCard(
    place: PlaceOfInterest?,
    guideContent: String?,
    isSpeaking: Boolean,
    isGenerating: Boolean,
    deviceHeading: Float,
    userLocation: com.example.travelguide.data.UserLocation?,
    settings: com.example.travelguide.data.TourGuideSettings,
    onSpeakAgain: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onDetailLevelChange: (String) -> Unit,
    cardBgColor: Color,
    primaryGlow: Color,
    secondaryGlow: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = place?.name ?: "Generating Tour Guide...",
                    color = if (cardBgColor == Color.White) Color(0xFF1C1B1F) else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // equalizer voice visualizer
                VoiceEqualizer(
                    isSpeaking = isSpeaking,
                    primaryColor = primaryGlow,
                    secondaryColor = secondaryGlow
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = place?.category ?: "",
                    color = secondaryGlow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 2.dp)
                )


            }

            // Detail Level Segmented Selection Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Short", "Detailed", "Interesting Facts").forEach { level ->
                    val selected = settings.detailLevel == level
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) secondaryGlow.copy(alpha = 0.15f) else Color.Transparent)
                            .border(1.dp, if (selected) secondaryGlow.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { onDetailLevelChange(level) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = level,
                            color = if (selected) (if (cardBgColor == Color.White) Color(0xFF1C1B1F) else Color.White) else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = secondaryGlow)
                }
            } else {
                // Scrollable text box using weight(1f) to occupy screen efficiently
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = guideContent ?: "",
                        color = if (cardBgColor == Color.White) Color(0xFF1C1B1F) else Color.LightGray,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time Audio Speech Speed Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NARRATION SPEED: ${String.format("%.2f", settings.speechRate)}x",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { onSpeechRateChange(1.0f) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsBackupRestore,
                                contentDescription = "Reset Speed",
                                tint = Color.Gray,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Slider(
                        value = settings.speechRate,
                        onValueChange = onSpeechRateChange,
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = secondaryGlow,
                            activeTrackColor = secondaryGlow,
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Google Maps integration button (Icon-only to save horizontal space)
                    IconButton(
                        onClick = { place?.let { openGoogleMaps(context, it) } },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Directions, contentDescription = "Show Route", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    val url = place?.tags?.get("url")
                    if (!url.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        IconButton(
                            onClick = { uriHandler.openUri(url) },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Language, contentDescription = "Atlas Obscura", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row {
                        if (isSpeaking) {
                            Button(
                                onClick = onStopSpeaking,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Stop", tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Voice", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val useDarkText = secondaryGlow == Color(0xFF03DAC6)
                            Button(
                                onClick = onSpeakAgain,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = secondaryGlow,
                                    contentColor = if (useDarkText) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Listen",
                                    tint = if (useDarkText) Color.Black else Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Listen", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatGuideCard(
    chatHistory: List<ChatMessage>,
    isSending: Boolean,
    onSendMessage: (String) -> Unit,
    cardBgColor: Color,
    primaryGlow: Color,
    modifier: Modifier = Modifier
) {
    val isLightMode = cardBgColor == Color.White
    val textColor = if (isLightMode) Color(0xFF111827) else Color(0xFFFFFFFF)
    val subTextColor = if (isLightMode) Color(0xFF4B5563) else Color(0xFFB3B3B3)

    var questionText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ask Tour Guide",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chat history window
            if (chatHistory.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(chatHistory.size) {
                        if (chatHistory.isNotEmpty()) {
                            listState.animateScrollToItem(chatHistory.size - 1)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(chatHistory) { msg ->
                            val isUser = msg.role == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 12.dp,
                                                bottomStart = if (isUser) 12.dp else 0.dp,
                                                bottomEnd = if (isUser) 0.dp else 12.dp
                                            )
                                        )
                                        .background(if (isUser) primaryGlow else (if (isLightMode) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f)))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = msg.content ?: "",
                                        color = if (isUser) Color.White else textColor,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    placeholder = { Text("Ask about history, directions...", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = textColor.copy(alpha = 0.3f),
                        unfocusedBorderColor = textColor.copy(alpha = 0.15f)
                    ),
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (questionText.isNotBlank()) {
                            onSendMessage(questionText)
                            questionText = ""
                            keyboardController?.hide()
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (questionText.isNotBlank()) {
                            onSendMessage(questionText)
                            questionText = ""
                            keyboardController?.hide()
                        }
                    },
                    enabled = !isSending && questionText.isNotBlank(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = primaryGlow,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceItem(
    place: PlaceOfInterest,
    isActive: Boolean,
    onClick: () -> Unit,
    cardBgColor: Color,
    secondaryGlow: Color
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) cardBgColor.copy(alpha = 0.9f) else cardBgColor)
            .border(
                1.dp,
                if (isActive) secondaryGlow.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    color = if (cardBgColor == Color.White) Color(0xFF1C1B1F) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = place.category,
                    color = if (cardBgColor == Color.White) Color(0xFF6B6A7A) else Color.Gray,
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Open in Google Maps icon button
                IconButton(
                    onClick = { openGoogleMaps(context, place) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Open in Google Maps",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${place.distance.toInt()} m",
                        color = secondaryGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Tap to narrate",
                        color = Color.Gray.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    settings: com.example.travelguide.data.TourGuideSettings,
    fetchedModels: List<String>,
    isFetchingModels: Boolean,
    onFetchModels: (String, String) -> Unit,
    onSave: (String, String, String, String, Int, Long, String, Float, Float, Boolean, String, Boolean, String, Boolean) -> Unit,
    onSavePrompts: (String, String, String, String) -> Unit,
    cardBgColor: Color,
    primaryGlow: Color
) {
    var providerName by remember { mutableStateOf(settings.providerName) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var modelName by remember { mutableStateOf(settings.modelName) }
    var searchRadius by remember { mutableFloatStateOf(settings.searchRadius.toFloat()) }
    var updateInterval by remember { mutableFloatStateOf(settings.updateInterval.toFloat()) }
    var detailLevel by remember { mutableStateOf(settings.detailLevel) }
    var autoPlay by remember { mutableStateOf(settings.autoPlay) }
    var speechRate by remember { mutableFloatStateOf(settings.speechRate) }
    var speechPitch by remember { mutableFloatStateOf(settings.speechPitch) }
    var interests by remember { mutableStateOf(settings.interests) }
    var popularOnly by remember { mutableStateOf(settings.popularOnly) }
    var customPrompt by remember { mutableStateOf(settings.customPrompt) }
    var isDarkModeActive by remember { mutableStateOf(settings.isDarkMode) }

    var promptShort by remember { mutableStateOf(settings.promptShort) }
    var promptDetailed by remember { mutableStateOf(settings.promptDetailed) }
    var promptInterestingFacts by remember { mutableStateOf(settings.promptInterestingFacts) }
    var showPromptManager by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        if (showPromptManager) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showPromptManager = false }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PROMPTS MANAGER",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                customPrompt = com.example.travelguide.data.SettingsRepository.DEFAULT_CUSTOM_PROMPT
                                promptShort = com.example.travelguide.data.SettingsRepository.DEFAULT_PROMPT_SHORT
                                promptDetailed = com.example.travelguide.data.SettingsRepository.DEFAULT_PROMPT_DETAILED
                                promptInterestingFacts = com.example.travelguide.data.SettingsRepository.DEFAULT_PROMPT_INTERESTING_FACTS
                            }
                        ) {
                            Text("Reset All", color = primaryGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SYSTEM GUIDE INSTRUCTIONS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Guidelines for guide persona, tone of voice, greeting restrictions, and facts focus.",
                            color = Color.LightGray.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                        OutlinedTextField(
                            value = customPrompt,
                            onValueChange = { customPrompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6,
                            maxLines = 10,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = primaryGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SHORT DETAIL CONSTRAINT PROMPT", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = promptShort,
                            onValueChange = { promptShort = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = primaryGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DETAILED (FREE WILL) CONSTRAINT PROMPT", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = promptDetailed,
                            onValueChange = { promptDetailed = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = primaryGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("INTERESTING FACTS CONSTRAINT PROMPT", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = promptInterestingFacts,
                            onValueChange = { promptInterestingFacts = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = primaryGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            onSavePrompts(customPrompt, promptShort, promptDetailed, promptInterestingFacts)
                            showPromptManager = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGlow)
                    ) {
                        Text("Save & Apply Prompts", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                Text(
                    text = "SETTINGS",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
            }

            // API Configuration
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI SERVICE PROVIDER", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    // Provider Dropdown Selector
                    var providerDropdownExpanded by remember { mutableStateOf(false) }
                    val providers = listOf(
                        "OpenAI",
                        "OpenRouter",
                        "Groq",
                        "Gemini (OpenAI Proxy)",
                        "Anthropic (OpenAI Proxy)",
                        "Local Ollama",
                        "Custom Endpoint"
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = providerName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("AI Provider") },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (providerDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Expand",
                                    modifier = Modifier.clickable { providerDropdownExpanded = !providerDropdownExpanded }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { providerDropdownExpanded = !providerDropdownExpanded },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = primaryGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )

                        DropdownMenu(
                            expanded = providerDropdownExpanded,
                            onDismissRequest = { providerDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(cardBgColor)
                        ) {
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider, color = Color.White) },
                                    onClick = {
                                        providerName = provider
                                        providerDropdownExpanded = false
                                        // Update default Base URL and Model Name based on provider selection
                                        when (provider) {
                                            "OpenAI" -> {
                                                baseUrl = "https://api.openai.com/v1"
                                                modelName = "gpt-4o-mini"
                                            }
                                            "OpenRouter" -> {
                                                baseUrl = "https://openrouter.ai/api/v1"
                                                modelName = "meta-llama/llama-3-8b-instruct:free"
                                            }
                                            "Groq" -> {
                                                baseUrl = "https://api.groq.com/openai/v1"
                                                modelName = "llama3-8b-8192"
                                            }
                                            "Gemini (OpenAI Proxy)" -> {
                                                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/"
                                                modelName = "gemini-1.5-flash"
                                            }
                                            "Anthropic (OpenAI Proxy)" -> {
                                                baseUrl = "https://api.anthropic.com/v1"
                                                modelName = "claude-3-5-sonnet-20240620"
                                            }
                                            "Local Ollama" -> {
                                                baseUrl = "http://10.0.2.2:11434/v1"
                                                modelName = "llama3"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key" + if (providerName == "Local Ollama") " (Optional for local)" else "") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = primaryGlow,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("API Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = primaryGlow,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )

                    // Model Name drop-down (if we have pre-defined list) or text field
                    val modelsList = when (providerName) {
                        "OpenAI" -> listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
                        "OpenRouter" -> listOf("meta-llama/llama-3-8b-instruct:free", "mistralai/mistral-7b-instruct:free", "google/gemma-2-9b-it:free", "anthropic/claude-3-haiku")
                        "Groq" -> listOf("llama3-8b-8192", "llama3-70b-8192", "mixtral-8x7b-32768", "gemma-7b-it")
                        "Gemini (OpenAI Proxy)" -> listOf("gemini-1.5-flash", "gemini-1.5-pro")
                        "Anthropic (OpenAI Proxy)" -> listOf("claude-3-5-sonnet-20240620", "claude-3-haiku-20240307")
                        "Local Ollama" -> listOf("llama3", "mistral", "gemma")
                        else -> emptyList()
                    }

                    val finalModelsList = (modelsList + fetchedModels).distinct()

                    if (finalModelsList.isNotEmpty()) {
                        var modelDropdownExpanded by remember { mutableStateOf(false) }
                        var showManualModelInput by remember(providerName) { mutableStateOf(false) }

                        if (showManualModelInput || !finalModelsList.contains(modelName)) {
                            // If user chose to enter manually or custom model is set
                            OutlinedTextField(
                                value = modelName,
                                onValueChange = { modelName = it },
                                label = { Text("Model Name (Manual)") },
                                trailingIcon = {
                                    IconButton(onClick = { showManualModelInput = false }) {
                                        Icon(imageVector = Icons.Default.List, contentDescription = "Use list")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = primaryGlow,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        } else {
                            // Model dropdown menu
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = modelName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("AI Model") },
                                    trailingIcon = {
                                        Row {
                                            Icon(
                                                imageVector = if (modelDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                contentDescription = "Expand",
                                                modifier = Modifier.clickable { modelDropdownExpanded = !modelDropdownExpanded }
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Type Manually",
                                                modifier = Modifier.clickable { showManualModelInput = true }
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { modelDropdownExpanded = !modelDropdownExpanded },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = primaryGlow,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                                    )
                                )

                                DropdownMenu(
                                    expanded = modelDropdownExpanded,
                                    onDismissRequest = { modelDropdownExpanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .background(cardBgColor)
                                ) {
                                    finalModelsList.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model, color = Color.White) },
                                            onClick = {
                                                modelName = model
                                                modelDropdownExpanded = false
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Other (Type manually...)", color = Color(0xFF03DAC6)) },
                                        onClick = {
                                            showManualModelInput = true
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // Standard field for custom endpoints
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("AI Model Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = primaryGlow,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    // Dynamically fetch models list from API
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (fetchedModels.isNotEmpty()) "Found ${fetchedModels.size} models" else "Need more models?",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        TextButton(
                            onClick = { onFetchModels(baseUrl, apiKey) },
                            enabled = !isFetchingModels && baseUrl.isNotBlank()
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = primaryGlow
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Fetch Models from API", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryGlow)
                        }
                    }
                }
            }

            // Custom Prompt Style Link
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI PROMPTS & SYSTEM TEMPLATES", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { showPromptManager = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Customize AI System Prompts", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Open Prompts Manager",
                                tint = primaryGlow
                            )
                        }
                    }
                }
            }

            // Location Configuration
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LOCATION & DETECT CONFIG", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    Text("GPS Update Frequency: ${updateInterval.toInt()} seconds", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = updateInterval,
                        onValueChange = { updateInterval = it },
                        valueRange = 10f..300f,
                        steps = 29,
                        colors = SliderDefaults.colors(thumbColor = primaryGlow, activeTrackColor = primaryGlow)
                    )
                }
            }

            // Guide customization & Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI TOUR GUIDE NARRATION & FILTERS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    // Popular spots toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Popular Spots Only", color = if (cardBgColor == Color.White) Color(0xFF1C1B1F) else Color.White, fontSize = 13.sp)
                        Switch(
                            checked = popularOnly,
                            onCheckedChange = { popularOnly = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = primaryGlow)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Auto-Play Narration Proximity", color = if (cardBgColor == Color.White) Color(0xFF1C1B1F) else Color.White, fontSize = 13.sp)
                        Switch(
                            checked = autoPlay,
                            onCheckedChange = { autoPlay = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = primaryGlow)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dark Mode Theme", color = if (cardBgColor == Color.White) Color(0xFF1C1B1F) else Color.White, fontSize = 13.sp)
                        Switch(
                            checked = isDarkModeActive,
                            onCheckedChange = { isDarkModeActive = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = primaryGlow)
                        )
                    }
                }
            }

            // Action Buttons
            item {
                Button(
                    onClick = {
                        onSave(
                            providerName,
                            apiKey,
                            baseUrl,
                            modelName,
                            searchRadius.toInt(),
                            updateInterval.toLong(),
                            detailLevel,
                            speechRate,
                            1.0f, // Reset pitch to 1.0f (no longer dynamic/useful)
                            autoPlay,
                            interests,
                            popularOnly,
                            customPrompt,
                            isDarkModeActive
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryGlow)
                ) {
                    Text("Save & Apply Settings", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }
}

@Composable
fun VoiceEqualizer(
    isSpeaking: Boolean,
    primaryColor: Color = Color(0xFF6200EE),
    secondaryColor: Color = Color(0xFF03DAC6),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val count = 5
        val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

        for (i in 0 until count) {
            val duration = 400 + i * 150
            val heightScale by if (isSpeaking) {
                infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(duration, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_$i"
                )
            } else {
                remember { mutableFloatStateOf(0.1f) }
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp * heightScale)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor,
                                secondaryColor
                            )
                        ),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
