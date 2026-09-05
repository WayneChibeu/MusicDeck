# MusicDeck

A modern, ad-free Android music player built with Kotlin and Jetpack Media3.

## Download
[**Download Latest APK (v2.10.2)**](https://github.com/WayneChibeu/MusicDeck/releases/download/v2.10.2/MusicDeck-v2.10.2.apk)

*Alternatively, view all [Releases](https://github.com/WayneChibeu/MusicDeck/releases).*

## Features

- **Audio Engine & Volume Booster**: Hardware-accelerated DSP featuring a clean digital volume booster up to +12 dB, 3D Spatial Virtualizer for wide stereo soundstage, and 5 curated acoustic presets (MusicDeck Signature, Cinema 3D, Vocal Clarity, Night Warmth, and Live Stage).
- **Automated In-App Updates**: Native GitHub Releases update checker inspired by Seal, supporting background checks on launch, manual checks in About & Legal, and in-app download progress with seamless package installer handover via FileProvider.
- **Cellular Data Awareness**: Automatic network metering detection before fetching lyrics, with real-time indicators and courteous confirmation prompts to protect your cellular data plan.
- **Local Music Playback**: High-fidelity playback for local audio files (MP3, FLAC, WAV, AAC, and more).
- **Floating Glass Alphabet Jumper**: Fast letter jumping with a glowing preview bubble and tactile mechanical haptics.
- **Intelligent Lyrics Engine**: Synchronized LRC lyrics fetching with confidence scoring and anti-hallucination verification.
- **Floating Desktop Lyrics**: Picture-in-picture lyric overlay for multitasking across apps.
- **Fluid 120fps Queue Drag Reordering**: Instant drag-to-reorder with elevation shadows and tactile haptic releases.
- **Glassmorphic Sleep Deck**: Quick preset timer pills (15m to 90m), custom minute slider, and "Stop After Current Song" mode.
- **Listening Insights**: Total plays, weekly plays, artist counts, and listening streak tracking.
- **Smart Playlists & Cover Collages**: Auto-generated mood mixes and dynamic 2x2 grid collage artwork.
- **Studio-Grade AMOLED High Contrast**: Pure `#FFFFFF` typography over pitch-black glass surfaces.
- **Personal Notes & Earbud Controls**: Personal song annotations and headset shortcuts.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| Media & DSP | Jetpack Media3 (ExoPlayer + MediaSession), Android AudioFX |
| UI | Material Design 3, View Binding |
| Database | Room |
| Storage & Prefs | MMKV |
| Dependency Injection | Koin |
| Image Loading | Coil |
| Architecture | MVVM with ViewModel and LiveData |
| Code Hardening | R8 / ProGuard bytecode obfuscation and resource shrinking |

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34+
- JDK 17+

### Build Instructions

```bash
# Clone the repository
git clone https://github.com/WayneChibeu/MusicDeck.git

# Open in Android Studio, sync Gradle, and run
```

## Technical Breakdown

<details>
<summary>Click to view Engineering Highlights & Architecture</summary>

### 1. Hardware-Accelerated Sound Engine & DSP Architecture
**Challenge:** Providing high-fidelity volume gain and spatial widening across various Android hardware configurations without digital clipping, audio dropouts, or native media server crashes when switching tracks.

**Solution:** Integrated Android's hardware-accelerated `LoudnessEnhancer` and `Virtualizer` into `AudioEffectManager`. Dynamic gain is calculated with millivolt threshold precision up to +12 dB, synchronized directly with ExoPlayer's active `audioSessionId`, and guarded by lifecycle listeners to prevent stale audio effect attachments. Custom frequency curves and acoustic profiles are persisted in MMKV for instant restoration across app restarts.

### 2. In-App Automated Release Distribution
**Challenge:** Keeping sideloaded and open-source installs up to date without relying on external browser redirections or third-party store dependencies.

**Solution:** Engineered an in-app updater querying the GitHub Releases API. Releases are parsed and semantic versions are compared against the active `BuildConfig.VERSION_NAME`. When a newer version is confirmed, APKs stream directly to application storage with real-time download progress and launch the Android package installer using secure `FileProvider` authorities and install permissions.

### 3. Intelligent Lyrics Matching & Anti-Hallucination Engine
**Challenge:** Online lyric APIs often return inaccurate or hallucinated lyrics when querying common song titles (e.g., *King* or *You*), or fail when tracks include featuring artist tags (`feat.`, `ft.`).

**Solution:** Designed an intelligent preprocessing and candidate-scoring engine in `LyricsApiService`. The pipeline sanitizes featuring artists from track titles and evaluates candidate search results across multi-factor criteria (title similarity, artist match confidence, and duration delta). Low-confidence matches are rejected to cleanly report *"Lyrics not found online"* rather than serving mismatched lyrics.

### 4. Background MediaSession & Audio Lifecycle
**Challenge:** Managing the transition between foreground UI activity and background `MediaSessionService` while ensuring instant scrub/seek responsiveness without audio stutter or silence gaps.

**Solution:** Implemented a decoupled Jetpack Media3 `MediaSessionService` with a custom forwarding player (`AutoPlayForwardingPlayer`). Seek operations utilize exact synchronization (`CLOSEST_SYNC`) and instant full-volume resume, while Sunset Mode isolates fade-out transitions strictly to pause events.

### 5. Studio-Grade High-Contrast UI & Dynamic Theming
**Challenge:** Maintaining crisp text legibility and visual hierarchy over vibrant, constantly shifting album art ambient gradients.

**Solution:** Standardized core typography, synchronized lyric lines, and transport controls on pure `#FFFFFF` white contrast tokens against dynamic dark-gradient backdrops, ensuring WCAG-grade readability without sacrificing aesthetic vibrancy.

### 6. UI State Synchronization (MVVM)
**Challenge:** Synchronizing real-time playback state across independent components (Mini Player bar, Full Player Bottom Sheet, and notifications) without UI lag.

**Solution:** Established a single reactive source of truth using `ViewModel` and `LiveData` observing Jetpack Media3 `Player.Listener` events. Mini Player interaction logic decouples session initialization (`autoPlay = false`) from playback trigger events to prevent abrupt startup jumps.

</details>

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) and [NOTICE](NOTICE) files for details.

## Contributing

Contributions, issues, and feature requests are welcome! Feel free to check the [issues page](https://github.com/WayneChibeu/MusicDeck/issues) if you want to contribute.

## Author

**Wayne Chibeu** ([@WayneChibeu](https://github.com/WayneChibeu))
