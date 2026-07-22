# CineShot 🎬

**CineShot** is an Android camera app that simulates cinematic camera movement and film-like zoom aesthetics.

> 🏗️ **Early stage** — currently a camera preview shell. Photo capture, video recording, OpenGL filters, and MediaCodec processing are coming next.

## Tech Stack

- **Language:** Kotlin
- **Camera:** CameraX
- **Graphics:** OpenGL ES (planned)
- **Encoding:** MediaCodec (planned)
- **Min SDK:** 26 &nbsp;|&nbsp; **Target SDK:** 34

## Build

This project is built exclusively via **GitHub Actions**. No local builds are required — push to `main` or open a PR and CI will produce the APK.

[![Build](https://github.com/YOUR_ORG/CineShot/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_ORG/CineShot/actions/workflows/build.yml)

### CI workflow

Every push to `main` and every pull request triggers `.github/workflows/build.yml`:

1. Checkout
2. Set up JDK 17 (Temurin)
3. Generate Gradle Wrapper (8.5)
4. `./gradlew assembleDebug`
5. Upload `app-debug.apk` as a workflow artifact

## Getting Started (local development)

```bash
# 1. Clone
git clone https://github.com/YOUR_ORG/CineShot.git
cd CineShot

# 2. Let CI build for you, or generate the wrapper locally (one-time):
#    Download Gradle 8.5, unzip, then run:
#    /path/to/gradle-8.5/bin/gradle wrapper --gradle-version 8.5

# 3. Open in Android Studio — it will sync and you can edit / lint as usual.
```

## License

MIT
