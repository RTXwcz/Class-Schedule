# 移动端本地 OCR 调研

更新时间：2026-09-06

目标是识别中文课表截图，提取课程名、星期、节次/时间、周次、单双周、教学楼、教室及备注，并在 Android 原生应用中离线运行。课表图片通常是规则表格，但图片来源可能是截图、导出 PDF 的页面或手机拍照，因此“文字识别”和“表格/布局恢复”要分开评估。

## 结论

第一版建议采用 **PaddleOCR 官方 Android 示例中的 PP-OCRv6 tiny/small ONNX 模型 + ONNX Runtime Android**，并在模型上层实现课表专用的几何分组、表头识别和字段解析。PP-OCRv5_mobile ONNX 作为兼容/回退模型保留。模型只有在用户在首次进入界面明确选择“使用本地 OCR”后才下载到应用私有目录；下载前展示模型版本、预计大小和离线说明。

原因：

1. PaddleOCR 官方仓库已经提供 `deploy/ppocr-android`，包含 SDK 与 Compose Demo 分离结构、ONNX Runtime 推理、AAR 集成方式、模型资源目录和性能脚本，能显著降低 Android 集成风险。
2. PP-OCRv5 文档明确覆盖简体中文、拼音、繁体中文、英文和日文，并提供 mobile 检测/识别模型；官方 Android 示例列出了 PP-OCRv6 tiny/small 和 PP-OCRv5_mobile 的 ONNX 模型入口。
3. 课表导入需要每个文本框的坐标和置信度，PP-OCR 的检测 + 识别结果能提供文本框；表格单元格和合并关系仍应由应用自己的课表解析器确定，不能把通用文档模型的输出直接当作课程数据。

ML Kit Text Recognition v2 是适合快速验证的替代方案。如果优先级是尽快交付首个可用版本，可以先接入其中文识别器；但它只负责文本识别，不提供课表表格结构，且中文模型是 Google SDK 的黑盒依赖，不适合作为后续可定制的唯一引擎。

## 方案比较

| 方案 | 中文能力与布局 | Android/离线 | 体积与性能证据 | 许可证/风险 | 适合本项目的判断 |
| --- | --- | --- | --- | --- | --- |
| **ML Kit Text Recognition v2（中文）** | 官方提供 Chinese script recognizer；返回文本、块、行、元素及边界框，可用于自己做网格分组；没有表格单元格/合并关系识别 | Android API 23+；bundled 模式随 APK 携带，unbundled 模式由 Google Play services 动态下载；模型下载完成后可离线识别 | Google 文档给出每个 script/ABI 约 4 MB 的 bundled 增量、约 260 KB 的 unbundled 增量；非 Latin 脚本在多数设备上可能慢于 Latin | Google ML Kit SDK，不是本地可训练的开放模型；需处理 Google Play services 可用性和模型下载失败 | **快速 MVP/回退**；集成最省事，但可定制性和表格能力弱 |
| **PaddleOCR PP-OCRv6 tiny/small ONNX** | 官方 Android SDK 是检测+识别流水线，结果含文本、置信度、四点框；PP-OCRv5 文档给出简体/繁体中文等能力；需在应用层恢复课表网格 | 官方 `deploy/ppocr-android` 使用 ONNX Runtime Android，模型放 `assets/models`，支持 source module 或 AAR；可把模型放应用私有目录实现完全离线 | Android 示例给出 tiny/small 与 v5_mobile 模型；示例设备上完整流水线约数百毫秒量级，但这是官方示例设备数据，不应当作本项目承诺 | PaddleOCR 工程代码 Apache-2.0；模型版权/再分发条款需按对应模型页面核对，发布时保留许可证和 NOTICE | **首选**；官方 Android 路径最完整，支持模型替换、量化和后续课表专用微调 |
| **PaddleOCR PP-OCRv5_mobile ONNX** | PP-OCRv5 mobile 检测/识别模型，官方指标覆盖打印中文、手写中文、繁体中文等场景；仍需应用层表格解析 | 同上；官方 Android 示例明确列出 v5_mobile ONNX 下载和 `inference.yml` | 官方 PP-OCRv5 文档给出 mobile/server 对比和 CPU/GPU基准；mobile 更适合资源受限设备，但 v5 字典扩大后推理变慢 | 同 PaddleOCR；模型条款需随具体权重核对 | **稳定回退/第一批兼容模型**；先于 v6 tiny/small 可作为验证基线 |
| **RapidOCR + ONNX Runtime** | RapidOCR 官方说明默认支持中英文，模型源自 PaddleOCR 的 ONNX 转换；输出文本和检测框；表格结构仍需自行实现 | 官方说明支持 Python、C++、Java、C# 跨平台和离线部署；Android 端要选用/维护 Java 或 C++ 组件并自行封装生命周期 | 采用 ONNX Runtime 等推理后端；模型大小和 Android 延迟依赖具体权重/运行时，官方 README 未给本项目可直接采用的 Android 体积承诺 | 工程仓库 Apache-2.0；README 明确 OCR 模型版权归百度，发布时必须核对模型权重许可 | **备选**；当需要统一 ONNX/C++/Java 运行时或更细的工程控制时使用，但官方 Android SDK 路径不如 PaddleOCR 当前示例直接 |
| **Tesseract 5 + chi_sim fast** | 官方引擎以 LSTM 为主，支持 100+ 语言和 TSV/hOCR 等输出；`tessdata_fast` 是速度/精度折中模型，中文可用 `chi_sim`；没有表格结构模型 | 官方核心是 C/C++ `libtesseract`，Android 需自己做 NDK 集成或引入第三方 wrapper；模型文件可完全离线 | fast traineddata 是 8-bit 整数模型；官方没有针对 Android 课表图片的端到端基准 | 引擎和 tessdata_fast Apache-2.0，但依赖 Leptonica 等另有许可证 | **不作为首选**；依赖与中文复杂场景精度通常需要更多调参，适合极简/保底路径 |
| **PP-StructureV3 / PaddleOCR-VL** | PP-StructureV3 包含布局检测、通用 OCR、表格识别等可选子流水线；PaddleOCR-VL-0.9B 面向文档元素、表格和结构化输出，能力最强 | 官方文档主要面向桌面/服务器或服务化部署；不应假设可直接在普通 Android 手机上实时运行 | PP-StructureV3 包含多个模型，官方表格列出从数 MB 到百 MB 级模型；PaddleOCR-VL 是 0.9B 参数级模型，远超本项目默认下载体验 | 模型/组件许可需逐项核对；运行内存、首包大小、耗电和延迟风险高 | **后续高级模式/云端**；不作为默认本地模型，可在高端设备实验或 OpenAI 云端路径中使用 |

## 一手来源与核验要点

### Google ML Kit

- Android Text Recognition v2 文档：<https://developers.google.com/ml-kit/vision/text-recognition/v2/android>
  - 说明 API 可提取图像/视频文字，Android API level 21 的总览描述与当前 Android 集成页的 API 23 要求需以集成页为准。
  - 列出 `text-recognition-chinese` 中文脚本库，以及 bundled/unbundled 两种安装方式。
  - 文档给出 bundled 约每脚本/ABI 增加 4 MB、unbundled 约 260 KB，并说明 unbundled 模型由 Google Play services 动态下载。
  - 中文识别器返回层级化文本及边界框；应用需要自己按坐标重建课表网格。
- 支持脚本列表：<https://developers.google.com/ml-kit/vision/text-recognition/v2/languages>

### PaddleOCR / PP-OCR

- 官方仓库：<https://github.com/PaddlePaddle/PaddleOCR>
- PP-OCRv5 算法与指标：<https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/algorithm/PP-OCRv5/PP-OCRv5.en.md>
  - 文档列出 PP-OCRv5 mobile/server 检测与识别模型，以及中文、拼音、繁体中文等评测类别。
  - 同一文档的 benchmark 是官方指定硬件上的参考数据，不能直接替代 Android 真机测试。
- Android 部署示例：<https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/inference_deployment/cross_platform/android_deployment.en.md>
  - 示例位于 `deploy/ppocr-android`，使用 ONNX Runtime、Jetpack Compose、minSdk 26，提供 SDK/AAR 两种集成方式。
  - 示例列出 `PP-OCRv6_small`、`PP-OCRv6_tiny` 和 `PP-OCRv5_mobile` 的检测/识别 ONNX 权重入口，并要求识别模型附带 `inference.yml`。
  - 示例结果含文字、置信度和四点框，支持检测、识别阶段耗时。
- PP-StructureV3：<https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/pipeline_usage/PP-StructureV3.en.md>
  - 官方说明该流水线由布局检测、通用 OCR、表格识别等多个子流水线组成，可输出 Markdown/结构化结果；它不是轻量移动端 OCR 的单一模型。
- PaddleOCR 许可证：<https://github.com/PaddlePaddle/PaddleOCR/blob/main/LICENSE>

### RapidOCR / ONNX Runtime

- RapidOCR 官方仓库：<https://github.com/RapidAI/RapidOCR>
  - README 声明默认支持中英文、离线部署，并将 PaddleOCR 模型转换为 ONNX；列出 Python、C++、Java、C# 等语言方向。
  - README 声明工程采用 Apache 2.0，同时 OCR 模型版权归百度，模型权重许可需要单独核对。
- RapidOCR 许可证：<https://github.com/RapidAI/RapidOCR/blob/main/LICENSE>
- ONNX Runtime Android 移动端示例：<https://onnxruntime.ai/docs/tutorials/mobile/deploy-android.html>
  - 官方示例说明 Android 可使用发布的 ONNX Runtime 包，模型可转为 ORT 格式；支持通过 custom build 仅包含模型所需算子以缩小应用。
- ONNX Runtime Java/Android 入口：<https://onnxruntime.ai/docs/get-started/with-java.html>

### Tesseract

- 引擎官方仓库：<https://github.com/tesseract-ocr/tesseract>
  - 官方 README 说明 Tesseract 4/5 的 LSTM 引擎、100+ 语言、TSV/hOCR/PDF 等输出及 C/C++ API；Android 需要自行编译/封装。
- fast 模型：<https://github.com/tesseract-ocr/tessdata_fast>
  - 官方说明这是速度/精度折中、8-bit 整数化的 LSTM traineddata；采用 Apache-2.0。
- 数据文件说明：<https://tesseract-ocr.github.io/tessdoc/Data-Files-in-different-versions.html>

## 面向本项目的落地设计

### 模型包

1. 首次启动让用户选择：不使用本地 OCR、下载推荐模型、稍后决定。默认不下载大模型。
2. 下载 `PP-OCRv6_tiny` 或 `PP-OCRv6_small` 的 det/rec ONNX 及识别字典配置；网络失败时保留重试，不影响手工录入和 Web 版。
3. 模型写入 `files/ocr/<modelId>/<version>/`，完成后校验 SHA-256，再原子切换 `current` 指针；升级保留旧版本直到新版本通过自检。
4. 记录 `modelId`、`modelVersion`、文件哈希、下载时间和来源，便于回滚、导出和未来云同步 schema。

### 解析流水线

```text
图片选择/相机
  -> 方向校正与裁剪
  -> det/rec 得到 text + confidence + quadrilateral
  -> 按 y/x 坐标聚类成行和列
  -> 识别星期表头、节次表头、周次/单双周标签
  -> 将单元格候选映射为 CourseDraft
  -> 规则校验（星期、节次、周次、时间、地点）
  -> 逐项确认后写入 Room
```

课表解析必须保留每个字段的原始文本、来源框坐标和置信度。低置信度或无法映射的字段进入“待确认”列表；不能静默写入课程表。表格线、合并单元格和跨行课程应由坐标规则处理，必要时允许用户拖拽/点选修正。

### 测试与选型门槛

建立小型匿名样本集，至少覆盖：规则导出表格、深色主题截图、手机拍照透视、单双周标记、跨多节课程、教学楼/教室混排。对每张图记录字段级准确率、整行课程准确率、端到端导入耗时、峰值内存和 APK/模型增量。

- MVP 通过门槛：常见导出课表的课程名/星期/节次/地点字段达到可接受的字段准确率，且所有低置信度字段都能被用户发现和修正。
- 只有在真机测试显示 ML Kit 中文识别满足门槛时，才把它作为轻量默认引擎；否则直接使用 PaddleOCR ONNX。
- PP-StructureV3/VL 只在收集到真实失败样本、确认规则解析不足后评估，避免为少数复杂图片把默认模型变成百 MB 级甚至 0.9B 级负担。

## 最终建议

**第一阶段**：PaddleOCR 官方 Android SDK 路径 + PP-OCRv6 tiny/small ONNX + 自研课表坐标解析器；模型按用户选择下载，完全离线推理。

**第二阶段**：加入 PP-OCRv5_mobile 回退/对比，针对真实课表样本做阈值、裁剪和中文字段规则调优；必要时对 det/rec 做量化或 ORT format 优化。

**第三阶段**：复杂表格尝试 PP-StructureV3 的轻量子模块或云端 OpenAI 图片解析；云端结果必须与本地 OCR 一样经过字段校验和逐项确认。

ML Kit 中文识别器保留为快速原型或低存储设备选项，Tesseract 只作为极简离线保底，不把任何通用 OCR 的未经校验文本直接写入正式课表。
