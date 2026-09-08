# Third-party notices

Class Schedule as a whole is distributed under GPL-3.0-only. Third-party
components retain their original licenses and copyright notices. The project
license does not claim exclusive authorship or relicense upstream files.

| Component | License | Source / provenance |
| --- | --- | --- |
| PaddleOCR Android Kotlin SDK, adapted | Apache-2.0 | [Pinned source and local modifications](apk/android/app/src/main/java/com/kebiao/app/ocr/paddle/UPSTREAM.md); original headers and LICENSE retained |
| AndroidX / Jetpack Compose / Room / Glance / DataStore | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin, kotlinx.coroutines, kotlinx.serialization, Ktor | Apache-2.0 | https://github.com/JetBrains/kotlin and https://github.com/Kotlin |
| MCP Kotlin SDK 0.15.0 | Apache-2.0 with retained MIT contributions | https://github.com/modelcontextprotocol/kotlin-sdk/tree/0.15.0 (transition terms preserved in `licenses/MCP-Kotlin-SDK-LICENSE.txt`) |
| ONNX Runtime Android 1.21.1 | MIT, bundled third-party notices | https://github.com/microsoft/onnxruntime/tree/v1.21.1 |
| OpenCV 4.12.0 | Apache-2.0, bundled third-party components | https://github.com/opencv/opencv/tree/4.12.0 |
| Gradle Wrapper and Android build tooling | Apache-2.0 and their respective notices | https://github.com/gradle/gradle |

The runtime dependency inventory and embedded notices are in
[`licenses/dependency-notices.txt`](licenses/dependency-notices.txt). License
texts supplied by native library upstreams are retained in `licenses/`.
These files are also bundled in the APK's `assets/licenses/` directory and
can be read from Settings → 开源许可.

PP-OCRv6 model weights are **not bundled** in the repository or APK. Users
choose whether to download them from the official PaddlePaddle repositories;
the exact revisions, SHA-256 values, model licenses and sources are recorded
in [the model research report](docs/research/2026-09-07-mobile-chinese-timetable-ocr.md)
and `OcrModelManager.kt`. Model licenses remain independent of this application.

Development-only npm tools are not part of the native application runtime;
their package-level licenses are recorded in `apk/package-lock.json` and
installed package license files. Tests and build tools keep their own licenses.
