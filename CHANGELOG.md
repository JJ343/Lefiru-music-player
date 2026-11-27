# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/lang/en/).

## [1.1] - 2026-11-27

### Added
* Animated startup screen with the emblem and the "LeFiru" wordmark (skippable by tapping).
* App-level volume control with an extra boost (100–200%) persisted across sessions.
* Multi-song selection with "select all" and multi-delete.
* GitHub CI: tests, lint, and debug build on every push and pull request.

### Changed
* Opted in to Media3 unstable APIs for lint compliance.
* App version bumped to 1.1 (code 2).

### Fixed
* Duration formatting and "select all" selection covered by unit tests.

## [1.0] - 2026-11-17

### Added
* Initial release: local music player for Android.
* Library read from MediaStore (songs, albums, artists).
* Full player: play/pause, next/previous, progress bar, shuffle, and repeat.
* Persistent mini player above the navigation bar.
* Notification with controls and lock-screen integration (Media3 MediaSession).
* Album art and search by title/artist/album.
* Favorites and custom playlists (saved on the phone).
* Multi-band equalizer, Bass Boost, and playback speed (0.5x to 2x).
* Sleep timer (5, 10, 15, 30, 45, or 60 minutes) with a visible countdown in the player.
* Standalone stopwatch ("Crono" tab): start, pause, resume, reset, and record laps
  with split times.
* Two-stage volume: normal app volume (0–100%) plus an extra boost (100–200%).
* Native decoding of FLAC, ALAC, WAV, MP3, AAC, OGG, and Opus without re-encoding.
  The file plays exactly as stored on the phone.