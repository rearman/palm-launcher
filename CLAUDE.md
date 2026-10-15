# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

An Android home-screen launcher, a companion to `../datebook`. Kotlin, plain Android Views (no AndroidX, no Compose, no XML layouts), no dependencies. minSdk 26, compileSdk and targetSdk 34, JDK 17. Version control is jj.

## Build

The repository has no Gradle wrapper. CI (`.github/workflows/build-apk.yml`) uses Gradle 8.7 on JDK 17:

```sh
gradle assembleDebug --stacktrace
gradle testDebugUnitTest
gradle testDebugUnitTest --tests com.rearman.palmlauncher.SomeTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. CI runs on each push to `main` or `master`, on each push of a `v*` tag, and on manual dispatch. It renames the APK to `palm-launcher.apk` (`palm-launcher-<tag>.apk` on a tag build) and uploads it unzipped (`archive: false`, which accepts only one file). A tag build also attaches the APK to the GitHub Release of that tag, and creates the release when it does not exist.

`app/debug.keystore` is committed on purpose. Both build types sign with it, so a new build installs over the old one and keeps the user's data. Do not remove it or change the signing config.

## Architecture

The function of the Palm III Applications screen: one A to Z list of all apps, filtered by category, with a dock of 4 apps at the bottom. All source is in `app/src/main/java/com/rearman/palmlauncher/`.

- **`MainActivity`** is the home activity (`MAIN` + `HOME` + `DEFAULT` in the manifest, `singleTask`). `render()` rebuilds all views from `state` and `apps`. Call `save()` then `render()` after each state change. The app list is cached and reloads only from the package receiver (`Apps.watch`). Home pressed while the launcher has focus goes to the next category (`onNewIntent`, Launcher3 test: window focus and no `FLAG_ACTIVITY_BROUGHT_TO_FRONT`). Back does nothing.
- **`Model.kt`** is pure Kotlin with no Android imports, so the JVM tests in `app/src/test/` cover it. `LauncherState` holds the categories, the app to category map, the 4 dock slots, the selected category and the view mode. Apps are keyed by `ComponentName.flattenToString()`. Categories have fixed ids, so a rename keeps the assignments. `UNFILED` (0) is fixed and last. `ALL` (-1) is a filter value only. An app with no assignment is in Unfiled.
- **`Store.kt`** saves `LauncherState` as one JSON string in SharedPreferences `launcher`. Bad or missing data gives `LauncherState.defaults()`.
- **`Apps.kt`** loads the `MAIN`/`LAUNCHER` activities and launches them. The `<queries>` element in the manifest is required for that query on Android 11 and later. `REQUEST_DELETE_PACKAGES` is required for the Delete (uninstall) menu item.
- **`Dialogs.kt`** has the category picker and Edit Categories dialogs (platform `AlertDialog`).
- Grid view has 4 columns, list view has 2. Both fill across, then down.
