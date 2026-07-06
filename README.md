# AI Travel Guide 🌍🎙️

An interactive, AI-powered local tour guide application for Android. Built with Jetpack Compose, Kotlin, and native OsmDroid maps, this app generates dynamic, context-aware audio tours for landmarks and tourist spots anywhere in the world.

---

## 📲 Direct APK Download

For quick installation, the latest compiled and verified release of the app is available directly in the root of this repository:
👉 **[Download TravelGuide.apk](TravelGuide.apk)** (Simply copy it to your Android device and install).

---

## 🌟 Key Features

* **Consolidated Map UI Controls**:
  * Mode Selection, Filters, and Search are aligned in a single horizontal row for maximum map real estate.
  * **GPS / Map Center Toggles**: Easy switching between tracking your GPS location (`MyLocation` icon) or pinning standard searches to the viewport center (`PinDrop` icon).
  * **Static Target Pin**: A custom target pin overlay stays placed at the dead center of the screen when in Map Center mode.

* **Dual Scan Modes**:
  * **Standard Mode**: Scans tourist spots dynamically using the OSM Overpass API.
  * **God Mode**: Uses a locally queries SQLite Atlas Obscura database containing thousands of unusual, mysterious, and off-the-beaten-path locations globally.
  * **Dynamic DB Downloader**: Automatically downloads and installs the database in the background directly from the [Atlas Obscura Database Repository](https://github.com/abhi8569/Atlas-Obscura-Database).

* **Detailed Attraction Popup Card**:
  * Click on any marker to instantly open a bottom selection card.
  * Displays place name, distance (meters or kilometers), a **Directions Button** (which opens turn-by-turn navigation in the Google Maps app), a **Website Button** (for God Mode spots), and a **Hear Guide** button.
  * Tap anywhere on the map background to clear the active selection.

* **Dynamic AI Audio Guides**:
  * Generates high-fidelity descriptive narratives using LLM completions (OpenAI and custom APIs).
  * Zero hardcoded system prompts: Customize your AI system behavior directly in the prompt manager screen.
  * Text-to-Speech (TTS) narration with custom speed and pitch controls.

* **Dynamic Window Edge-to-Edge System**:
  * Full-bleed layout matching system Dark/Light mode windows with proper status bar and navigation bar padding.

---

## 🛠️ Technology Stack

* **UI Framework**: Jetpack Compose (Kotlin)
* **Map Engine**: OsmDroid (OpenStreetMap native views)
* **Architecture**: MVVM (Model-View-ViewModel) + StateFlow
* **Network Engine**: Ktor Client (ContentNegotiation JSON serialization)
* **Local Database**: SQLite (custom schema supporting Atlas Obscura data queries)
* **Data Storage**: Jetpack Preferences DataStore
* **Geocoding**: Photon Komoot API & Overpass OpenStreetMap API

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
4. Go to **Settings** (gear icon) in the app to configure your LLM Service Provider, API Key, and Base URL.
5. In Settings, download the Atlas Obscura database to enable **God Mode**.

---

## 📦 Atlas Obscura Database
The offline-first **God Mode** utilizes a compiled SQLite database of unique travel locations.
* **Database Repository**: [Atlas-Obscura-Database](https://github.com/abhi8569/Atlas-Obscura-Database)
* **Database Download Link**: [atlas_obscura.db](https://raw.githubusercontent.com/abhi8569/Atlas-Obscura-Database/main/atlas_obscura.db) (Handled automatically in the app's settings downloader).

---

## 📝 License
This project is licensed under the MIT License - see the LICENSE file for details.
