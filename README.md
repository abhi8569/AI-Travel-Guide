# AI Travel Guide 🌍🎙️

An interactive, AI-powered local tour guide application for Android. Built with Jetpack Compose, Kotlin, and Web-based interactive maps, this app generates dynamic, context-aware audio tours for landmarks and tourist spots anywhere in the world.

---

## 🌟 Key Features

* **Interactive Map View**:
  * Powered by Leaflet JS running in a seamless WebView layout.
  * Supports multiple layers: **Dark Mode**, **Light Mode**, and **Satellite Imagery**.
  * Persistent style choices across app tab transitions and restarts.
  
* **Dynamic AI Audio Guides**:
  * Automatically detects nearby tourist attractions, historical landmarks, and scenic spots.
  * Generates high-fidelity descriptive narratives using LLM completions (OpenAI and custom APIs).
  * Out-loud text-to-speech (TTS) narration with customizable speed settings.

* **Editable AI Prompt Manager**:
  * **Zero Hardcoded System Prompts**: Customize how your AI behaves directly in the Settings sub-panel.
  * Supports three user-friendly detail levels on-the-fly:
    * **Short**: Restricts descriptions to brief 2-3 sentence summaries.
    * **Detailed (Free Will)**: Allows the AI to decide narrative length based on landmark significance.
    * **Interesting Facts Only**: States unusual, surprising, or mysterious local facts.
  * Direct placeholder interpolation (`{info level goes here}`) inside system instructions.

* **Advanced Location Services**:
  * Supports real-time GPS tracking or custom map-center selection.
  * Dynamic global reverse-geocoding (powered by Photon Komoot API) to resolve exact cities and countries for landmarks.
  * Radius-based attraction scanning (100m - 5000m).
  * Filter chips sync automatically to only show categories of active/visible popular attractions.

---

## 🛠️ Technology Stack

* **UI Framework**: Jetpack Compose (Kotlin)
* **Architecture**: MVVM (Model-View-ViewModel) + StateFlow
* **Network Client**: Ktor (HTTP client with OkHttp engine and ContentNegotiation JSON serialization)
* **Data Storage**: Jetpack Preferences DataStore (settings persistence)
* **Maps**: Leaflet JS integration (HTML5 WebView interface)
* **Geocoding**: Photon Komoot API / Overpass OpenStreetMap API

---

## 🚀 Getting Started

### Prerequisites
* Android Studio (Koala or newer recommended)
* Android SDK (API Level 24 minimum, target SDK 36)
* An active OpenAI API key (or custom compatible LLM completion endpoint)

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/abhi8569/AI-Travel-Guide.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and run the application on your Android device or emulator.
4. Go to **Settings** in the app to configure your LLM Service Provider, API Key, and Base URL.

---

## ⚙️ Configuration & Customization
To configure the AI Narrator:
1. Tap the gear icon in the toolbar.
2. Set your **AI Service Provider** (OpenAI / Custom).
3. Insert your **API Key** and **Base URL**.
4. Tap **Customize AI System Prompts** to edit system instructions or detail-level behaviors.

---

## 📝 License
This project is licensed under the MIT License - see the LICENSE file for details.
