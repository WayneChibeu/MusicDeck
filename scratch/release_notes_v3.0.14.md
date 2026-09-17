MusicDeck v3.0.14 - Shuffle Persistence, Earbud Smart Controls & Dashboard Proportions

What's New in v3.0.14:
- Shuffle Mode Persistence & Lifecycle Resilience:
  * Persistent Storage: Shuffle state (isShuffleEnabled) and Repeat mode are now persistently stored in MMKV via SettingsManager.
  * Zero Session Loss: Leaving the app, multitasking, or system background memory reclamation will never wipe out your active shuffle or reset playback to alphabetical.
  * Unified Shake to Shuffle: Shaking the phone while inside MainActivity now seamlessly synchronizes with the background audio engine's persistent shuffle state.
  * Queue Status Badge: Queue Bottom Sheet dynamically reflects active playback with a "Shuffle Active" indicator.

- Earbud & In-Line Remote Smart Controls:
  * Double-Tap Next Support: Added a 1000ms window where two consecutive Next Track commands trigger a queue shuffle.
  * Wired Headsets: Rapidly pressing the in-line middle button 4 times (which Android OS translates to two Next events) now reliably triggers queue shuffle.
  * Wireless TWS Earbuds: Doing two consecutive Next gestures (e.g. triple-tapping the Right earbud twice on Oraimo SpaceBuds Neo+) shuffles the queue directly over Bluetooth AVRCP.
  * Forgiving Hardware Debounce: Relaxed click timeout to 500ms and allowed 4+ clicks so fast in-line remote presses are never dropped.
  * Clear Settings Text: Updated subtitle to "Double-tap Next or 4x center click to shuffle".

- Portrait Lock & Vehicle Dashboard Proportions:
  * Browsing Screen Portrait Lock: Locked MainActivity and InsightsActivity to portrait orientation, matching Spotify and Apple Music to prevent unscrollable or distorted horizontal views.
  * Car Mode Landscape Two-Pane Refinement: Balanced horizontal vehicle dashboard layout with a 28% screen-width square album cover and 72% spacious driving controls with 20dp padding.
