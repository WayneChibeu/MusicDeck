# MusicDeck

A modern, ad-free Android music player built with Kotlin and Jetpack Media3.

## Features

- **Local Music Playback** – Plays all local music files (MP3, FLAC, WAV, etc.)
- **Smart Search** – Full-screen search with history
- **Modern UI** – Material 3 design with smooth animations
- **Organization** – Tabs for Tracks, Artists, Albums, Playlists, and Favorites
- **Shuffle & Play All** – Distinct controls for ordered and shuffled playback
- **Queue Management** – Drag-to-reorder, clear queue, and more
- **Lyrics Support** – Import and display LRC lyrics files
- **Sleep Timer** – Set a timer to pause playback automatically
- **Equalizer** – System equalizer integration
- **Widget Support** – Home screen playback controls
- **Favorites** – Quick access to your favorite tracks
- **Dark Theme** – Easy on the eyes

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Media | Jetpack Media3 (ExoPlayer + MediaSession) |
| UI | Material Design 3, View Binding |
| Database | Room |
| Image Loading | Coil |
| Architecture | MVVM with ViewModel and LiveData |

## Technical Challenges & Solutions

 1. Background Service & MediaSession Lifecycle

Challenge: Managing the transition between a foreground activity and a background MediaSessionService was a major hurdle. Ensuring that playback continued seamlessly when the app was minimized—while adhering to Android’s strict foreground service limitations—required deep dives into service lifecycles.


Solution: I implemented a dedicated MediaSessionService using Jetpack Media3 to decouple playback logic from the UI. This involved configuring the MediaSession to persist even when the Activity was destroyed, ensuring the notification controls remained responsive.

2. UI State Synchronization (MVVM)

Challenge: Syncing the real-time playback state (position, shuffle mode, favorite status) across multiple UI components—like the Mini Player and the Full Player—without causing UI lag or memory leaks.

Solution: I utilized LiveData and ViewModels to create a single source of truth for the playback state. By observing the Player.Listener events within the ViewModel, I ensured that UI updates were reactive and that data remained consistent across different navigation tabs.

3. Dynamic Theming & Performance

Challenge: Implementing Material 3 dynamic theming while handling high-resolution image loading for album art via Coil, which initially caused minor stuttering during list scrolls.

Solution: I optimized image loading by implementing proper caching strategies with Coil and ensured that dynamic color extraction occurred on a background thread to prevent blocking the main UI thread.

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

## Screenshots

*Coming soon*

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Author

**Wayne Chibeu** – [@WayneChibeu](https://github.com/WayneChibeu)
