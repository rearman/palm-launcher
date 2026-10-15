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

`MainActivity` is the home activity (`MAIN` + `HOME` + `DEFAULT` in the manifest, `singleTask`). It queries all `MAIN`/`LAUNCHER` activities and rebuilds the list in `onResume`. The `<queries>` element in the manifest is required for that query on Android 11 and later.
