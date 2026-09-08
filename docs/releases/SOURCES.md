# Corresponding source and dependency sources

The application source, build scripts, Gradle Wrapper and npm lockfile for
v1.5.2 are available at https://github.com/RTXwcz/Class-Schedule/tree/v1.5.2
and in `class-schedule-v1.5.2-source.zip` next to its two ARM APKs.
For historical v1.3.0 ARM builds, use that release's ARM source attachment;
its notes identify the packaging commit separately from the original tag.
No proprietary signing
key is needed to build a modified version; sign it with your own Android key.

The source includes the adapted PaddleOCR Kotlin SDK. Its exact upstream
revision and modifications are recorded in the vendored `UPSTREAM.md` file.
OCR model weights are not distributed with this release.

Dependencies are unmodified published artifacts except for the explicitly
vendored PaddleOCR SDK. The exact resolved runtime coordinates and upstream
project URLs are in `licenses/dependency-notices.txt`. Source artifacts for
JVM/Android libraries are available alongside those coordinates in Google's
Maven repository or Maven Central, usually as `<artifact>-<version>-sources.jar`:

- AndroidX: https://dl.google.com/dl/android/maven2/ and
  https://android.googlesource.com/platform/frameworks/support/
- Kotlin, kotlinx libraries and Ktor: https://repo.maven.apache.org/maven2/,
  https://github.com/JetBrains/kotlin and https://github.com/Kotlin
- MCP SDK 0.15.0: https://github.com/modelcontextprotocol/kotlin-sdk/tree/0.15.0
- Guava ListenableFuture 1.0 (Guava 26.0 Android):
  https://github.com/google/guava/tree/v26.0
- SLF4J API 2.0.18: https://github.com/qos-ch/slf4j/tree/v_2.0.18

Native runtime source trees, their pinned submodules, build recipes and
third-party notices are available from their release tags:

- ONNX Runtime 1.21.1: https://github.com/microsoft/onnxruntime/tree/v1.21.1
- OpenCV 4.12.0: https://github.com/opencv/opencv/tree/4.12.0

The Android framework and platform toolchain are provided by Android SDK/NDK;
the required build versions are documented in `apk/构建APK.md`. If an upstream
source location becomes unavailable, open an issue in this repository so the
release source references can be repaired.
