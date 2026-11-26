# Lefiru Music Player

Local music player for Android, built with **Kotlin + Jetpack Compose + Media3 (ExoPlayer)**.

## Included features

- Animated startup intro: the app icon appears above the "LeFiru" wordmark on a
  GitHub-style dark background before the library appears (tap anywhere to skip it).
- Local library read through MediaStore (songs, albums, artists).
- Full player: play/pause, next/previous, progress bar, shuffle, and repeat.
- Persistent mini player above the navigation bar.
- Notification with controls and lock-screen integration (Media3 MediaSession).
- Album art and search by title/artist/album.
- Favorites and custom playlists (saved on the phone).
- Multi-band equalizer, Bass Boost, and playback speed (0.5x to 2x).
- Sleep timer: pauses the music after 5, 10, 15, 30, 45, or 60 minutes, with a
  visible countdown in the player.
- Standalone stopwatch ("Crono" tab): start, pause, resume, reset, and record laps
  with split times.
- Two-stage volume: normal app volume (0–100%) plus an extra boost (100–200%)
  in the player, both persisted across sessions.
- Audio quality: native decoding of FLAC, ALAC, WAV, MP3, AAC, OGG, and Opus without
  re-encoding. The file plays exactly as stored on the phone.

## Requirements

1. **Android Studio** (Ladybug 2024.2 or newer) — includes the SDK and the JDK.
   Download: https://developer.android.com/studio
2. An Android phone running **8.0 (API 26) or newer** and a USB cable.
3. Optional: `adb` in the PATH to install from the terminal.

> The project ships with the Gradle wrapper, so you do not need to install Gradle manually.
> The Android SDK (platform 35 + build-tools) is already installed on this machine at
> `%LOCALAPPDATA%\Android\Sdk`, so Android Studio will detect it automatically.

## How to run the app

1. Open **Android Studio**.
2. `File > Open...` and choose the `Lefiru music player` folder.
3. Wait for the **Gradle Sync** (the first time it downloads dependencies and may take several minutes).
4. Connect the phone with **USB debugging** enabled.
5. In the top-right corner, select your phone and press **Run ▶**.
6. On the phone, grant the **music and audio** permission.

To build the installable package (APK):

```bash
gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

> The app has already been built successfully once: that APK exists and weighs ~20 MB.

### Installing the APK on the phone without Android Studio

With the phone connected and USB debugging enabled:

```bash
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

## How to add music

Copy your files (`.flac`, `.mp3`, `.m4a`, `.wav`, `.ogg`, `.opus`) to the phone's
`Music` folder (over USB, MTP, or by downloading them). Then, inside the app, tap the
**refresh** button (circular icon) in the top bar.

If a song does not show up, check that the file is at least 5 seconds long and that the
phone detects it as music. You can force the system to rescan by restarting the phone or
using a media rescan app.

## Project structure

```
app/src/main/java/com/lefiru/music/
├── MainActivity.kt              # Main screen and permissions
├── LefiruApp.kt                 # Application class
├── data/
│   ├── Track.kt                 # Track / Album / Artist model + utilities
│   ├── Playlist.kt              # Playlist model (serializable)
│   ├── MediaScanner.kt          # Library reading through MediaStore
│   └── UserDataRepository.kt    # Favorites/playlists/settings with DataStore
├── playback/
│   ├── MusicService.kt           # Background service + MediaSession
│   ├── PlaybackConnection.kt     # Service connection (MediaController)
│   └── EqualizerController.kt    # Equalizer, bass boost, loudness
└── ui/
    ├── LibraryViewModel.kt       # App state and actions
    ├── LefiruScreen.kt           # Scaffold, tabs, mini player, dialogs
    ├── theme/Theme.kt            # Colors and dark theme
    ├── components/Common.kt      # AlbumArt, SongRow, MiniPlayer
    └── screens/
        ├── IntroSplash.kt        # Animated startup wordmark
        ├── LibraryTabs.kt        # Songs, albums, artists, playlists, favorites
        ├── PlaylistDetailScreen.kt # Playlist detail and reordering
        ├── PlayerScreen.kt       # Full-screen player + equalizer
        └── StopwatchScreen.kt    # Stopwatch tab
```

## Permissions used

| Permission | Purpose |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 12-) | Read your music files |
| `POST_NOTIFICATIONS` | Show playback controls in the notification |
| `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)` | Keep playing while the app is in the background |
| `MODIFY_AUDIO_SETTINGS` | Equalizer and audio effects |

The app has no internet access and never uploads your files to any server.

## Common issues

**"Sin canciones" (No songs) even though there is music**
- Grant the audio permission (Settings > Apps > Lefiru > Permissions).
- Verify that the files are in `Music/` or that the system indexed them.
- Tap the refresh button.

**The playback notification does not appear**
- On Android 13+, grant the notification permission.
- Some manufacturers (Xiaomi, Samsung) require disabling battery optimization for
  background apps.

**The equalizer says "not available"**
- Some devices/firmware do not expose audio effects. Try with headphones connected or
  restart the app.

## Suggested next steps

- Folder browsing.
- Visible, reorderable playback queue.
- ReplayGain / volume normalization.
- Advanced tag reading with `MediaStore` (genre, BPM, lyrics).
