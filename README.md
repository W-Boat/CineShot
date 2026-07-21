# CineShot 🎬

模拟电影运镜与电影变焦质感的 Android 相机 App。

## 构建方式

本项目通过 **GitHub Actions** 自动构建，不在本地编译。

每次 push 到 `main` 分支或发起 Pull Request 时，CI 会自动执行 `./gradlew assembleDebug` 并上传 `app-debug.apk`。

## 技术栈

- **Kotlin** — 主语言
- **CameraX** — 相机预览与采集
- **OpenGL ES** — 实时滤镜与运镜模拟（计划中）
- **MediaCodec** — 视频编码（计划中）
- **Android 8.0+** (API 26)

## 项目结构

```
CineShot/
├── .github/workflows/build.yml   # CI 配置
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/cineshot/app/
│           └── MainActivity.kt   # 相机预览入口
├── build.gradle.kts              # 项目级构建
├── settings.gradle.kts
└── gradle.properties
```

## 本地开发

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle sync 完成
3. 选择设备运行

## License

MIT
