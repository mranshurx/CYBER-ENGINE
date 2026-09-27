# Copy Paster

A clean Android app that copies files you pick to any destination folder
you type in, using [Shizuku](https://shizuku.rikka.app/) (no root needed)
to write outside the app's normal sandbox.

## How it works

1. Install and open the [Shizuku app](https://shizuku.rikka.app/) and
   start its service (wireless debugging / ADB, or root).
2. Open Copy Paster — it checks Shizuku status on launch.
3. Tap **Grant Shizuku Permission**.
4. Tap **Choose Files to Copy** and pick one or more files.
5. Type a destination path, e.g. `/sdcard/Download/`.
6. Tap **ACTIVATE** — the app copies each file there and logs the result.

No remote license checks, no hidden menus, no auto-deletion of copied
files. What you copy stays where you put it.

## Requirements

- Android Studio (Giraffe or newer)
- A device/emulator with the Shizuku app installed and running
- minSdk 24, compileSdk 34

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Or open the folder in Android Studio and hit Run.

## Project structure

```
ShizukuFileCopier/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/shizukufilecopier/MainActivity.kt
│       └── res/
│           ├── layout/activity_main.xml
│           ├── drawable/bg.png
│           ├── mipmap-xxxhdpi/ic_launcher.png
│           └── values/ (strings.xml, styles.xml)
├── all-images/         # source copies of icon.png and bg.png
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Push to GitHub

```bash
cd ShizukuFileCopier
git init
git add .
git commit -m "Initial commit: Copy Paster app"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```
