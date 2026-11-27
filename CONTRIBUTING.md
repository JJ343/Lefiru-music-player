# Contributing to Lefiru Music Player

Thank you for your interest in contributing to Lefiru, a local music player for Android
(Kotlin + Jetpack Compose + Media3/ExoPlayer). The app has no internet access and never
uploads your files to any server.

## Getting started

1. Open the project in **Android Studio** (Ladybug 2024.2 or newer).
2. Wait for the Gradle sync (the first time it downloads dependencies and may take several minutes).
3. Connect a phone with Android 8.0 (API 26) or newer and USB debugging enabled.
4. Run the app with **Run ▶** and grant the music and audio permission.

## Workflow

1. Create a branch from `main` with a descriptive name (`feat/...`, `fix/...`, `chore/...`).
2. Make your changes, write or update tests if applicable.
3. Run `./gradlew testDebugUnitTest lintDebug` and verify everything passes.
4. Open a pull request against `main` describing the change and the tests performed.

## Code style

* Use **Kotlin** with the official style (`kotlin.code.style=official` in `gradle.properties`).
* Follow Jetpack Compose conventions and the existing project architecture.
* Do not introduce network or analytics dependencies.

## Testing

Unit tests live in `app/src/test`. Run them with:

```
./gradlew testDebugUnitTest
```

## Reporting issues

Use the issue templates in `.github/ISSUE_TEMPLATE`. For security issues, see `SECURITY.md`.

## License

By contributing, you agree that your contributions will be licensed under the
[MIT License](LICENSE).
