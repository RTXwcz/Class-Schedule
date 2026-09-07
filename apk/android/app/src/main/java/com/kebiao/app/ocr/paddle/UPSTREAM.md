# PaddleOCR Android SDK source provenance

Source: https://github.com/PaddlePaddle/PaddleOCR/tree/2661c7c0ef5c613e8f93c6e93b2e052399f0f854/deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr

License: Apache-2.0, preserved in LICENSE and original source headers.

This directory vendors the 26 Kotlin SDK files at the fixed commit above. Local adaptations:

- Relocated `com.paddle.ocr` to `com.kebiao.app.ocr.paddle`.
- ORTSessionManager opens verified application-private absolute model paths instead of APK assets.
- ModelConfig reads its paired dictionary YAML from a private file instead of APK assets.
- CTCDecoder validates tensor shape, output length, and model/dictionary class agreement.

Detection, polygon expansion, perspective crops, recognition preprocessing, decoding, and result ordering remain based on the upstream implementation. Recognition deliberately preserves the upstream Android BGR-to-RGB conversion; its difference from model YAML is documented in `docs/research/2026-09-07-mobile-chinese-timetable-ocr.md` and requires color-image parity validation. No weights are included in this source directory or APK assets.

Runtime coordinates: `com.microsoft.onnxruntime:onnxruntime-android:1.21.1` and `org.opencv:opencv:4.12.0`. The upstream QuickBird 4.5.3 x86_64 binary failed to load on Android 15 (`__lttf2` missing), so this app uses the official OpenCV distribution. The wrapper bounds detection at 2048 pixels, uses two CPU threads and recognition batch size one. No latency or accuracy guarantee is inferred from upstream desktop benchmarks.
