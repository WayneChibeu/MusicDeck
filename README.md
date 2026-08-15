# MusicDeck 

A modern, ad-free Android music player built with Kotlin and Jetpack Media3.

## Download
[**Download Latest APK (v2.8.0)**](https://github.com/WayneChibeu/MusicDeck/releases/download/v2.8.0/MusicDeck-v2.8.0.apk)

*Alternatively, view all [Releases](https://github.com/WayneChibeu/MusicDeck/releases).*


## Features

- **Local Music Playback** – Plays all local music files (MP3, FLAC, WAV, etc.)
- **Smart Search** – Full-screen search with history
- **Modern UI** – Material 3 design with smooth animations
- **Organization** – Tabs for Tracks, Artists, Albums, Playlists, and Favorites
- **Shuffle & Play All** – Distinct controls for ordered and shuffled playback
- **Queue Management** – Drag-to-reorder, clear queue, and more
- **Intelligent Lyrics Engine** – Synchronized LRC lyrics fetching with confidence scoring and anti-hallucination verification
- **Sleep & Sunset Timer** – Dedicated audio fade-out transitions on pause
- **Equalizer** – System equalizer integration
- **Widget Support** – Home screen playback controls
- **Favorites** – Quick access to your favorite tracks
- **Studio-Grade High Contrast** – Crisp pure-white legibility across dynamic album art gradients

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Media | Jetpack Media3 (ExoPlayer + MediaSession) |
| UI | Material Design 3, View Binding |
| Database | Room |
| Image Loading | Coil |
| Architecture | MVVM with ViewModel and LiveData |

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

### 1. Intelligent Lyrics Matching & Anti-Hallucination Engine
**Challenge:** Online lyric APIs often return inaccurate or "hallucinated" lyrics when querying common song titles (e.g., *King* or *You*), or fail when tracks include featuring artist tags (`feat.`, `ft.`).

**Solution:** Designed an intelligent preprocessing and candidate-scoring engine in `LyricsApiService`. The pipeline sanitizes featuring artists from track titles and evaluates candidate search results across multi-factor criteria (title similarity, artist match confidence, and duration delta). Low-confidence matches are rejected to cleanly report *"Lyrics not found online"* rather than serving mismatched lyrics.

### 2. Background MediaSession & Audio Lifecycle
**Challenge:** Managing the transition between foreground UI activity and background `MediaSessionService` while ensuring instant scrub/seek responsiveness without audio stutter or silence gaps.

**Solution:** Implemented a decoupled Jetpack Media3 `MediaSessionService` with a custom forwarding player (`AutoPlayForwardingPlayer`). Seek operations utilize exact synchronization (`CLOSEST_SYNC`) and instant full-volume resume, while Sunset Mode isolates fade-out transitions strictly to pause events.

### 3. Studio-Grade High-Contrast UI & Dynamic Theming
**Challenge:** Maintaining crisp text legibility and visual hierarchy over vibrant, constantly shifting album art ambient gradients.

**Solution:** Standardized core typography, synchronized lyric lines, and transport controls on pure `#FFFFFF` white contrast tokens against dynamic dark-gradient backdrops, ensuring WCAG-grade readability without sacrificing aesthetic vibrancy.

### 4. UI State Synchronization (MVVM)
**Challenge:** Synchronizing real-time playback state across independent components (Mini Player bar, Full Player Bottom Sheet, and notifications) without UI lag.

**Solution:** Established a single reactive source of truth using `ViewModel` and `LiveData` observing Jetpack Media3 `Player.Listener` events. Mini Player interaction logic decouples session initialization (`autoPlay = false`) from playback trigger events to prevent abrupt startup jumps.

</details>

## License

This project is licensed under the GNU GPLv3 License - see the [LICENSE](LICENSE) and [NOTICE](NOTICE) files for details.

## Contributing

Contributions, issues, and feature requests are welcome! Feel free to check the [issues page](https://github.com/WayneChibeu/MusicDeck/issues) if you want to contribute.

## Author

**Wayne Chibeu** – [@WayneChibeu](https://github.com/WayneChibeu)
