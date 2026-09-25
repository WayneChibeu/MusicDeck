# MusicDeck

A modern, ad-free Android music player powered by the proprietary **DeckAcoustix™ Native C++ DSP Audio Engine** and Jetpack Media3.

## Download
[**Download Latest APK (v3.1.6)**](https://github.com/WayneChibeu/MusicDeck/releases/download/v3.1.6/MusicDeck-v3.1.6.apk)

*Alternatively, view all [Releases](https://github.com/WayneChibeu/MusicDeck/releases).*

---

## Flagship Features

### Audio & DeckAcoustix™ DSP Engine
- **Headphone Crossfeed (Bauer Binaural DSP / BS2B)**: Native C++ acoustic head-shadow and delay compensation filter network. Recreates the natural acoustic soundstage of nearfield studio monitor speakers and eliminates extreme headphone listener fatigue on hard-panned stereo mixes (The Beatles, Queen, Pink Floyd, Jimi Hendrix). Includes 3 reference acoustic presets:
  - **Subtle (Meier)**: 650 Hz cutoff, -9.5 dB feed (Jan Meier curve; wide, natural, transparent)
  - **Standard (Bauer)**: 700 Hz cutoff, -6.0 dB feed (classic Bauer BS2B reference for rock and jazz)
  - **Studio (Chu Moy)**: 700 Hz cutoff, -4.5 dB feed (nearfield monitor control room simulation)
- **DeckAcoustix™ 64-Bit Native Audio Engine**: High-performance C++ DSP processing pipeline running at zero-allocation in-place execution with anti-pumping coupled peak limiter, dynamic headroom, and smooth parameter slewing.
- **Parametric Equalizer with Interactive Frequency Response Graph**: 5-band cascaded biquad IIR filters with real-time smooth Bezier frequency curve visualization.
- **Real-Time 60 FPS Spectrum Visualizer**: Radix-2 Fast Fourier Transform (FFT) 32-band ballistic decay analyzer rendered at silky 60 FPS.
- **EQ Preset Community Sharing (`.deck` Files)**: Export, import, and trade custom sound profiles via the native Android Share sheet (WhatsApp, Telegram, Drive) with full crossfeed and EQ curve bundling.
- **Bit-Perfect USB-DAC Passthrough**: High-resolution audio output with automatic sample-rate synchronization for external audiophile DACs and headphone amplifiers.
- **Dual-Crossover Karaoke Cut**: Mid/side vocal cancellation preserving sub-bass punch and high-frequency acoustic air.
- **Independent Tempo & Pitch Shifter**: High-quality WSOLA time-stretching (0.5x – 2.0x) and independent semitone pitch shifting (-12 to +12 semitones).
- **Studio Algorithmic Reverb**: Schroeder-Freeverb algorithmic reverberator with adjustable room size, damping, and wet mix.
- **Resonant Bass Boost & Volume Booster**: Analog-modeled resonant low-shelf boost and clean pre-amp volume booster.

### Vehicle & Driving
- **Android Auto Integration**: Native in-dash browsing via `MediaLibraryService` across All Tracks, Playlists, Favorites, and Fresh Arrivals with steering wheel controls and Google Assistant voice search.
- **Dedicated In-App Car Mode**: High-contrast, distraction-free driving dashboard with oversized touch targets (78dp Play/Pause, 60dp skip, 52dp seek) and 1-tap Favorite liking.
- **Two-Pane Landscape Layout**: Purpose-built horizontal layout for vehicle dashboard mounts with a 1:1 album art display on the left and full driving controls on the right.

### Visuals, Library & Smart Management
- **Smart Anti-Repeat Shuffle**: History-aware two-tier shuffling that prioritizes songs you haven't heard today and pushes recently played tracks to the back, ensuring large music libraries cycle through all unplayed songs before repeating.
- **Persistent Active Queue**: MMKV-backed queue state that preserves exact track sequences and playback positions across background process reclaims.
- **Synchronized Dual-Deck Crossfade**: Coordinated timeline shuffle ordering across dual ExoPlayer engines to prevent repeat tracks during transitions.
- **Universal Wallpaper Dynamic Theming (AndroidX Palette)**: Direct wallpaper sampling guaranteeing rich, vibrant dynamic theming on all Android ROMs (ColorOS, HyperOS/MIUI, HiOS/XOS, OriginOS).
- **Smart Playlists**: Intelligent multi-attribute keyword scoring for **Energy Boost** (high-tempo, dance, rock, workout) and **Chill Mode** (acoustic, ambient, ballads, piano) with custom vector iconography.
- **Shake to Shuffle (Pocket-Safe)**: Proximity-guarded motion detection that sleeps inside pockets or bags to prevent accidental walking/jogging shuffles.
- **Intelligent Lyrics Engine**: Synchronized LRC lyrics fetching with confidence scoring and anti-hallucination verification.
- **Floating Desktop Lyrics**: Picture-in-picture lyric overlay for multitasking across apps.
- **Floating Glass Alphabet Jumper**: Fast letter jumping with a glowing preview bubble and tactile mechanical haptics.
- **Glassmorphic Sleep Deck**: Quick preset timer pills (15m to 90m), custom minute slider, and "Stop After Current Song" mode.
- **Listening Insights**: Total plays, weekly plays, artist counts, and listening streak tracking.

### Home Screen Widgets
- **Vinyl Turntable Widget**: Rotating vinyl record with full album art encasing the platter and center spindle controls.
- **Master Deck Widget**: Comprehensive DJ console widget featuring live-ticking seek progress, timestamp, track metadata, and quick favorite toggle.
- **Minimal Pill Widget**: Compact floating pill displaying current cover, song title, artist, and responsive transport controls.
- **Native Launcher Previews**: Integrated XML preview layouts ensuring crisp previews in launcher widget pickers across ColorOS, HeyTap, OneUI, and stock launchers.

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin, C++20 |
| Native DSP Engine | Custom C++ DSP pipeline (Bauer Binaural BS2B, RBJ Biquads, Freeverb, WSOLA) via NDK & CMake |
| Media & Playback | Jetpack Media3 (ExoPlayer + MediaSession + MediaLibraryService) |
| Vehicle Integration | Android Auto Automotive Media App Descriptor |
| Dynamic Theming | AndroidX Palette (WallpaperManager extraction), Material Design 3 |
| UI Architecture | MVVM with ViewModel, LiveData, and View Binding |
| Database | Room SQLite |
| Local Storage & Prefs | MMKV, SharedPreferences |
| Dependency Injection | Koin |
| Image Loading | Coil |
| Code Hardening | R8 / ProGuard bytecode optimization and resource shrinking |

---

## Getting Started

### Prerequisites
- Android Studio Ladybug / Hedgehog or later
- Android SDK 34+
- Android NDK (Side by side) & CMake 3.22+
- JDK 17+

### Build Instructions

```bash
# Clone the repository
git clone https://github.com/WayneChibeu/MusicDeck.git

# Open in Android Studio, sync Gradle, and assemble release APK
./gradlew assembleRelease
```

---

## Technical Breakdown

<details>
<summary>Click to view Engineering Highlights & Architecture</summary>

### 1. DeckAcoustix™ Native C++ Audio DSP Pipeline
**Challenge:** Standard Android `AudioEffect` framework causes platform inconsistencies, latency spikes, and audio dropouts across OEM Android skins (Xiaomi HyperOS, Oppo ColorOS, Samsung OneUI).

**Solution:** Engineered an in-process native C++ audio processing engine running directly inside Media3's audio pipeline. Audio buffers are processed zero-copy in-place using continuous bilinear transforms with sample-rate pre-warping, 5-band cascaded biquads, and an anti-pumping coupled peak limiter that prevents digital clipping transparently without audio distortion.

### 2. Bauer Binaural Crossfeed (BS2B) Implementation
**Challenge:** Extreme stereo panning on headphones causes unnatural "in-head" localization and severe one-sided ear pressure fatigue.

**Solution:** Implemented Benjamin Bauer's acoustic head-shadow model and Boris Mikhaylov's BS2B algorithm using continuous 1st-order IIR direct high-boost and cross-delay low-pass filters in Transposed Direct Form II. The DSP continuously simulates acoustic interaural time delay (ITD) and head-related transfer functions (HRTF) with zero runtime memory allocation.

### 3. Universal Wallpaper Palette Dynamic Theming
**Challenge:** Standard Material You / Monet theming engines frequently fail or produce muted, grayed-out accent colors on non-Google Android ROMs.

**Solution:** Bypassed OEM theme engines by directly accessing the active device wallpaper via `WallpaperManager`, downsampling the bitmap in-memory, and extracting dominant, vibrant, and muted swatches using `androidx.palette`. Palettes are applied dynamically across all glassmorphic surfaces with instant live preview.

### 4. Vehicle MediaLibraryService & Distraction-Free Dashboard
**Challenge:** Enabling safe vehicle media playback across both in-dash Android Auto head units and standalone mounted phone dashboards.

**Solution:** Implemented an automotive `MediaLibraryService` conforming to Android Auto's strict media browsing guidelines, paired with a dedicated `CarModeActivity` featuring oversized driver-safe touch targets (78dp center control, 60dp skips), automatic `FLAG_KEEP_SCREEN_ON` wake locks, and a horizontal two-pane landscape layout for dashboard docks.

### 5. In-App Automated Release Distribution
**Challenge:** Keeping sideloaded and open-source installs up to date without third-party store dependencies.

**Solution:** Engineered an in-app updater querying the GitHub Releases API. Releases are parsed, semantic versions are compared against `BuildConfig.VERSION_NAME`, and APKs stream directly to application storage with real-time download progress before handing over to the Android package installer via `FileProvider`.

</details>

---

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) and [NOTICE](NOTICE) files for details.

## Author

**Wayne Chibeu** ([@WayneChibeu](https://github.com/WayneChibeu))
