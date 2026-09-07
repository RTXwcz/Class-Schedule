# Android 本地中文课表 OCR 选型与集成证据

核实日期：2026-09-07。范围：只研究公开一手资料和现有数据契约，不修改应用、构建配置或安装模型推理环境。

## 结论

推荐采用 **Paddle 官方 PP-OCRv6 small ONNX + ONNX Runtime Android** 作为中文课表主候选，提供 **PP-OCRv6 tiny** 作为节省下载量的候选。small 三个必需文件合计 31,190,469 字节，tiny 合计 6,298,800 字节；这是模型与字典配置的下载量，不是 APK 大小或运行内存。[S2][S4-S7]

PP-OCRv6 的存在已核实：PaddleOCR 官方 v3.7.0 于 2026-06-11 发布该系列，官方 Android 文档列出 tiny、small 和 v5 mobile 的 ONNX 下载及 Kotlin SDK。此前设计文档中的版本名称现在有一手证据，但仍不能据此声称本项目已实现或验证离线识别。[S1][S2]

选择 small 的依据是官方同一内部基准中识别准确率 81.3%、tiny 73.5%，中文印刷文本 90.5% 对 86.7%；这些数字只表明选型方向，**不是课表字段准确率，不是本项目实测，也不能推导手机耗时**。tiny 下载量更小，适合作为用户主动选择的轻量模式。[S3]

ML Kit 非捆绑中文模型可以主动请求下载并在设备端识别，但由 Google Play Services 管理，不提供本项目所需的模型文件 URL、SHA-256 和应用私有目录安装接口；不能冒充“本应用自主管理的本地模型”。捆绑版则不满足“选用后才下载”。[S11]

“完整课表”需要 OCR 之后的表格布局解释及字段确认。PP-OCR 输出文字、坐标和分数；不会直接提供课程、星期、节次、教师和周次的可靠结构化数据。轻量 VLM 也不能免除逐字段确认。[S2][S14]

## 一手证据版本

- PaddleOCR 仓库核实时的 `main`：`2661c7c0ef5c613e8f93c6e93b2e052399f0f854`。本报告 SDK 源码引用固定到此提交。[S2][S8]
- RapidOCR 仓库核实时的 `main`：`09f4e3b185de0074de94d4e9c26a3ca2fb67fa8f`，模型目录指定 `v3.9.2`。[S10]
- 以下 Hugging Face 模型均为 `PaddlePaddle` 官方组织，模型卡标记 `apache-2.0`；每个模型自己的仓库 revision 必须独立固定，不可混用。

## 模型比较

| 方案 | 已核实资源与 Android 路径 | 大小与许可 | 本项目判断 |
| --- | --- | --- | --- |
| PP-OCRv6 small | 官方 ONNX；官方 Kotlin SDK、ORT 1.21.1、OpenCV 4.5.3、minSdk 26 | 必需资源 31.19 MB，Apache-2.0 | 首选精度候选；手机效果待测 |
| PP-OCRv6 tiny | 同上，tiny 有独立字典 | 必需资源 6.30 MB，Apache-2.0 | 低下载量候选；不可复用 small 字典 |
| PP-OCRv5 mobile | 官方 Android 文档仍列支持，官方 ONNX | 必需资源 21,509,645 字节，Apache-2.0 | 可作为同设备比较基线，不因较旧而假定更快 |
| PP-OCRv4 mobile | Paddle 官方仓库有 Paddle 权重，RapidOCR 有 ONNX 转换件和校验值 | 本次官方 `pdiparams` 检测 4,692,937、识别 10,766,823 字节；这不是 ONNX 总下载量。Apache-2.0 | 旧部署基线；本次未验证官方新 Android SDK 对 v4 的兼容性 |
| RapidOCR + ORT | RapidAI 提供 v4/v5/v6 ONNX、SHA-256、多运行时参考代码；Android 可直接用 ORT 原生接口 | 框架 Apache-2.0；转换模型须保留 Paddle 来源 | 是分发与部署实现，不是另一种 OCR 算法；优先官方原始 ONNX，转换件可作备选 |
| ML Kit Chinese unbundled | `com.google.android.gms:play-services-mlkit-text-recognition-chinese:16.0.1`；`ModuleInstallClient` 显式下载 | 文档称每脚本架构约增加 260 KB；模型动态下载大小不是该数值；Google SDK 条款 | 不满足应用私有目录自主管理要求；不列入本次承诺路径 |
| ML Kit Chinese bundled | `com.google.mlkit:text-recognition-chinese:16.0.1` | 文档称每脚本每架构约增加 4 MB | APK 自带模型，不满足选用后下载 |
| PaddleOCR-VL-1.6 | 官方文档解析模型；llama.cpp 已有 PaddleOCR-VL 家族多模态支持，但本次未验证 1.6 Android 端到端支持 | 官方 HF 元数据 958,588,736 个 BF16 参数，裸参数约 1.917 GB；另有配置/运行缓存。Apache-2.0 | 文档表格能力强，但下载、内存和生成延迟明显不适合当前首版目标；无本项目手机实测 |
| SmolVLM-256M-Instruct | 官方 HF 模型、llama.cpp 多模态支持 | 256,484,928 个 BF16 参数，裸参数约 513 MB；模型卡语言只标 `en`，Apache-2.0 | 不能把“小 VLM”直接当作已验证中文课表解析器 |

资料来源：[S2-S7][S9-S13][S15-S17]。上述 MB 为十进制，模型参数字节只是按元数据计算的下限，不是量化包大小或峰值内存。Google 主文要求 API 23+；网页生成摘要曾出现 API 21+，应以正式接入正文及依赖元数据为准。[S11]

## 可直接录入清单的官方模型

下载 URL 不使用 `main`，SHA-256 固化到应用内受版本管理的清单。以下 `inference.yml` 同时包含识别配置和有序字典，不能丢弃、随意排序或替换。官方 Android 接入只要求 det ONNX、rec ONNX、rec YAML，不需要下载训练权重、`inference.json` 或 Python 包。[S2]

### PP-OCRv6 tiny

1. 检测模型，1,780,590 字节：
   - URL：<https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_det_onnx/resolve/2ba1506c0380b8f0b03dd142459aac66d4421f6c/inference.onnx>
   - SHA-256：`193bab7a04fca699a6c82e6abb5b81bdb28177f0abd4062552b04908dafb19f8`
2. 识别模型，4,462,639 字节：
   - URL：<https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_rec_onnx/resolve/2612ab37152ae0a677521bae4e1e3d4fb4cf7c30/inference.onnx>
   - SHA-256：`9ef676d6ed3c88256a2d92c640c44f25b0c40947e111b14b8be8f594091563e6`
3. 识别配置及字典，55,571 字节：
   - URL：<https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_rec_onnx/resolve/2612ab37152ae0a677521bae4e1e3d4fb4cf7c30/inference.yml>
   - SHA-256：`66170210bad538e83fff3c4a3867e547d6bf20b50d64b20347c4b913f3034ea1`

验证级别：两个 ONNX 和 YAML 均实际通过 HTTPS 读取完整字节，在内存中计算 SHA-256；ONNX 与官方 LFS 元数据一致。没有运行模型推理，没有把这些字节保存到项目或设备。[S4][S5]

### PP-OCRv6 small

1. 检测模型，9,880,512 字节：
   - URL：<https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/28fe5895c24fd108c19eb3e8479f4ab385fbfc62/inference.onnx>
   - SHA-256：`d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e`
2. 识别模型，21,159,378 字节：
   - URL：<https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/b8f84f0b80c529de40b4fbb3544b84fa7233a513/inference.onnx>
   - SHA-256：`5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634`
3. 识别配置及字典，150,579 字节：
   - URL：<https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/b8f84f0b80c529de40b4fbb3544b84fa7233a513/inference.yml>
   - SHA-256：`ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1`

验证级别：ONNX 大小和摘要来自官方 HF API 的 LFS 元数据，未完整下载 small ONNX。YAML 已实际读取完整字节并计算 SHA-256。[S6][S7]

### PP-OCRv5 mobile 比较基线

- 检测 URL：<https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/e6f4fa85f00e168c862bc462aebca69eef9b3d3d/inference.onnx>；4,826,518 字节；SHA-256 `a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d`。
- 识别 URL：<https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/ed152b8b495f84de93cda5709d768548a9127622/inference.onnx>；16,534,782 字节；SHA-256 `da72dc72ca4dc220df0dfde68c1dedc31c58d3e76a25871122e5056227d50092`。
- 识别配置 URL：<https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/ed152b8b495f84de93cda5709d768548a9127622/inference.yml>；148,345 字节；SHA-256 `5dfeb2777f6d0db8177d8128a8acfcf6e6276dc4ac73ea3bf0dc06d6a5e85d8e`。
- 验证级别同 small：ONNX 取官方 LFS 元数据，YAML 实际读取并计算摘要。[S9]

### 可访问性与备选来源

官方 Android 文档还给出 BOS tar 下载，例如 small 检测：<https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv6_small_det_onnx_infer.tar>。本次没有下载该 tar 或计算 tar 摘要；不要把 ONNX 文件摘要当成 tar 摘要。[S2]

RapidOCR 的转换件有不同字节和摘要。例如 v4 检测 <https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.9.2/onnx/PP-OCRv4/det/ch_PP-OCRv4_det_mobile.onnx>，SHA-256 `d2a7720d45a54257208b1e13e36a8479894cb74155a5efe29462512d42f49da9`；v4 识别 <https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.9.2/onnx/PP-OCRv4/rec/ch_PP-OCRv4_rec_mobile.onnx>，SHA-256 `48fc40f24f6d2a207a2b1091d3437eb3cc3eb6b676dc3ef9c37384005483683b`。其目录还列 v5/v6，不能套用本报告 Paddle 官方文件摘要。[S10]

本机能访问 HF 不代表所有用户网络可访问。重试、取消与手动再试属于下载界面必要状态；可增设自己控制且逐字节相同的镜像，以同一固定摘要核验，不在运行时从任意网页抓取最新模型。

## 官方 Android 代码可以复用的边界

官方 SDK 支持 `PaddleOCR.create(...)`、`recognize(Bitmap)`、`recognize(imageBytes)`、`release()`，输出 `OCRResult(box, text, confidence)` 及分阶段计时。可源码集成或自行构建 AAR；官方文档没有给出可直接引用的独立 Paddle SDK Maven 发布坐标。[S2]

**必须适配模型读取路径**：目前 `ORTSessionManager.loadModels()` 从 `context.assets` 读两个 ONNX，`ModelConfig.parse()` 从 assets 读 YAML。原样复制示例并把模型放到 APK assets，会违反本任务的主动下载要求。应在保留原有处理流程的基础上增加接收私有 `File` 或已校验模型包的入口，ORT 使用文件路径创建 session，配置读取对应同一版本目录。[S8]

运行时和模型是两回事：ORT/OpenCV 原生库可以随 APK 安装，模型必须等待用户选择。运行时增加的 APK 体积与支持 ABI 应用构建工具实测，不用模型文件大小代替。

建议的下载与激活契约属于本项目设计，而非上游自动提供：

1. 初始 `NOT_INSTALLED`，不在 Application、启动页初始化、选择图片或打开设置时触发模型网络请求。
2. 用户明确选择 tiny/small 并点击下载后，保存选择，进入 `DOWNLOADING`。清单包含 model ID、revision、文件名、固定 URL、字节数、SHA-256、许可、预处理版本。
3. 下载至 `context.noBackupFilesDir/ocr/<id>/<revision>.staging/`，流式写入并更新摘要；支持取消、空间不足、网络失败。目录位于应用私有存储且不随自动备份携带大文件。
4. 所有文件摘要、长度和字典/模型维度一致后再构建临时 session 验证；成功后原子切换目录或活动指针。失败不留下“已安装”状态。
5. 后续 `recognize()` 只读取已激活版本；断网可用。删除先结束/等待现有推理、释放 session，再删除受管理目录，状态回到未安装。用户重新安装需要重新主动操作。

## 预处理、字典与输出契约

以下来自当前官方 Android 源码，接入时需固定该版本并用参考输出验证：[S8]

- 检测：Bitmap 转 BGR；默认 `detImgMode="BGR"`；保持比例并将尺寸处理成 32 的倍数。输入 FLOAT NCHW `[1,3,H,W]`，按通道 `(pixel/255 - mean)/std`，mean `[0.485,0.456,0.406]`，std `[0.229,0.224,0.225]`。尺寸上限来自明确配置，不可对密集小字无条件缩到极低分辨率。
- DB 后处理：概率图阈值、连通轮廓、框分数、unclip、四边形裁剪；官方默认 `detThresh=0.3`、`detBoxThresh=0.6`、`detUnclipRatio=1.5`。坐标还原到输入图像，不应只返回按行拼接的大字符串。
- 识别：透视裁剪的文字行保持比例缩放到高度 48，动态宽度上限 3200，batch 内补零到共同宽度；归一化为 `pixel/127.5 - 1`；输入 FLOAT NCHW `[N,3,48,W]`。
- CTC 输出预期 `[N,T,C]`。每个时间步取最大类别，去除 blank `0` 和相邻重复；字典字符索引为类别 `i-1`，保留字典原有顺序；空格按对应配置追加且不得重复。行置信度是保留字符分数均值，不是课程字段正确概率。
- 启动 session 时读取实际 input/output metadata 校验类型、秩、类数。不能仅凭上述预期假定每一个 ONNX 转换件结构相同。

**已发现的上游差异，尚未做推理判定**：官方 Android `RecPreprocessor.kt` 显式将 BGR 转 RGB；官方 tiny/small/v5 rec YAML 的 `DecodeImage.img_mode` 都是 `BGR`。因此不能同时把“复制 Android 当前实现”与“严格按 YAML 通道顺序”宣称为已对齐。接入时应对固定彩色文字图片分别做参考输出比对，记录采用的通道约定和版本；黑白图可能掩盖此问题。本次无推理环境，没有判定哪条路径在目标模型上准确率更高。[S5][S7-S9]

建议应用 OCR 中间结果保留 `imageWidth/imageHeight/orientationTransform/modelId/modelRevision`，每个文本块保留 `rawText/quad/axisAlignedBox/textConfidence`。字段还应区分 `ocrConfidence` 与 `parseConfidence`，并记录多个 `sourceBoxes`，因为星期来自列头、节次来自行头、课程名来自单元格。确认后的任何字段编辑应重置该字段的确认状态。

## 完整课表解析与当前数据缺口

本段是从现有项目代码和目标推导的集成建议，不是模型具备的自动能力。

现有 `CourseDraft` 与 `Course` 只支持课程名、星期、开始/结束节次、单双周、教学楼、教室及地点备注。没有教师、起止周次、非连续周集合、课程备注字段；`CourseDraft.confirmAll()` 也不能独自证明 UI 已完成逐字段确认。若保留此数据模型，即使 OCR 读出“张老师、3-8/10-16周”，导入时仍会丢失，不能称完整导入。

必要字段与不确定性应在 DTO、确认 UI、Room、JSON 迁移、日程计算中一致承载：

| 字段 | 证据来源与解析原则 | 确认规则 |
| --- | --- | --- |
| 课程名、教师 | 单元格原文及相应框；不要把未知第二行一律当教室 | 用户逐字段确认或明确空值 |
| 星期 | 七列或五列表头坐标、列范围；不能依 OCR 行文本的全局阅读顺序 | 无可靠列头时保持未知 |
| 起止节次 | 节次行头、跨行单元格边界，必要时结合网格线 | 不把垂直居中的文字框高度当课程占据行数 |
| 周次 | `1-16周`、`3,5,7周`、单双周以及多个范围 | 缺少周次不应静默变成全学期；需明确确认 |
| 教学楼、教室、地点原文 | 可拆分地点，同时保留原文 | 不确定拆分可保留在未解析原文，不丢失 |
| 其他课程信息 | 学分、班级、线上链接、备注等原文 | 未承载字段至少保留课程级原文备注 |

建议先定位课表区域、提取表头和节次轴、根据网格/颜色区域或坐标聚类划分单元格，再将 OCR 文本分配至单元格。每格可能有多门课程、不同周次或同名课程的多次排课，不能简单一格一条或者按课程名去重。倾斜拍照、合并单元格、无边框表格、周次列与单元格内容混排需要专门样本；无法解释时显示原图裁剪和未知字段，阻止静默猜测。

Paddle 官方另有 SLANet/SLANet_plus/SLANeXt 表格结构模型，输出 HTML 结构和单元格位置；这是独立模型与产线，不是打开 PP-OCR 参数就获得的功能。它们仍不提供“课程字段语义”，并会增加模型管理、推理和验证范围。首版可先支持明确课表模板和可纠正布局，复杂表格另行基准比较。[S14]

## 必须完成的集成验收

1. 下载前与模型未选用时，抓取应用网络行为并确认无模型请求；APK 中无模型权重。断网启动后可读取已有模型、识别图片。
2. 分别验证下载取消、断网重试、摘要不符、空间不足、应用被杀后恢复、删除与重选。损坏文件不能进入可用状态。
3. 使用匿名课表样本分别记录字符错误率和课程级/字段级准确率；覆盖彩色小字、周次范围、单双周、教师、跨行课程、五天/七天表、合并单元格和拍照倾斜。框坐标须能回显至原图。
4. tiny/small/v5 在同一实体设备与图片集合上比较冷加载、热运行中位数/P90、峰值内存、课程遗漏与字段错误。模拟器只能辅助功能验证。
5. 验证字段修改后需重新确认，未确认或缺少必填字段不可写入；教师/周次等信息保存、重启及 JSON 导出后仍存在且实际调度生效。

本次已完成：官方版本/Android SDK/模型卡/文件元数据核实，tiny 完整下载与哈希校验，三套 rec YAML 实际字节哈希校验，以及现有 DTO 字段检查。本次未完成：Android/CPU 推理、SDK 构建、速度与内存测量、实际课表准确率评估、small/v5 ONNX 完整下载、BOS tar 校验。

## 后续集成记录

以上“现有字段缺口”和“未完成推理”描述研究采集时的状态。后续开发已补充教师、明确周次集合与课程备注，并完成官方 Android SDK 接入、tiny/small 下载校验及模拟器中文推理；tiny 另做了断网推理。真实课表准确率、实体机速度/内存和 BGR/RGB 彩色样本对照仍未测量。以 [最终验证报告](../superpowers/verification/2026-09-07-native-final-verification.md) 为当前集成状态。

## 来源

- [S1] [PaddleOCR v3.7.0 官方 release](https://github.com/PaddlePaddle/PaddleOCR/releases/tag/v3.7.0)
- [S2] [固定提交的官方 Android 部署文档](https://github.com/PaddlePaddle/PaddleOCR/blob/2661c7c0ef5c613e8f93c6e93b2e052399f0f854/docs/version3.x/inference_deployment/cross_platform/android_deployment.md)
- [S3] [固定提交的 PP-OCRv6 介绍与内部基准](https://github.com/PaddlePaddle/PaddleOCR/blob/2661c7c0ef5c613e8f93c6e93b2e052399f0f854/docs/version3.x/algorithm/PP-OCRv6/PP-OCRv6.en.md)
- [S4] [tiny det 官方元数据 API](https://huggingface.co/api/models/PaddlePaddle/PP-OCRv6_tiny_det_onnx?blobs=true)
- [S5] [tiny rec 官方元数据 API](https://huggingface.co/api/models/PaddlePaddle/PP-OCRv6_tiny_rec_onnx?blobs=true)
- [S6] [small det 官方元数据 API](https://huggingface.co/api/models/PaddlePaddle/PP-OCRv6_small_det_onnx?blobs=true)
- [S7] [small rec 官方元数据 API](https://huggingface.co/api/models/PaddlePaddle/PP-OCRv6_small_rec_onnx?blobs=true)
- [S8] [固定提交的官方 Kotlin SDK 源码目录](https://github.com/PaddlePaddle/PaddleOCR/tree/2661c7c0ef5c613e8f93c6e93b2e052399f0f854/deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr)，重点文件 `engine/ORTSessionManager.kt`、`model/ModelConfig.kt`、`preprocess/DetPreprocessor.kt`、`preprocess/RecPreprocessor.kt`、`postprocess/CTCDecoder.kt`。
- [S9] [v5 det 官方元数据](https://huggingface.co/api/models/PaddlePaddle/PP-OCRv5_mobile_det_onnx?blobs=true)、[v5 rec 官方元数据](https://huggingface.co/api/models/PaddlePaddle/PP-OCRv5_mobile_rec_onnx?blobs=true)、[v4 det 官方元数据](https://huggingface.co/api/models/PaddlePaddle/PP-OCRv4_mobile_det?blobs=true)、[v4 rec 官方元数据](https://huggingface.co/api/models/PaddlePaddle/PP-OCRv4_mobile_rec?blobs=true)。API 查询为核实时点；生产下载 URL 见上方固定 revision。
- [S10] [RapidOCR 固定提交模型清单](https://github.com/RapidAI/RapidOCR/blob/09f4e3b185de0074de94d4e9c26a3ca2fb67fa8f/python/rapidocr/default_models.yaml)、[许可](https://github.com/RapidAI/RapidOCR/blob/09f4e3b185de0074de94d4e9c26a3ca2fb67fa8f/LICENSE)
- [S11] [ML Kit Text Recognition v2 Android 官方接入文档](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)、[ModuleInstallClient 官方文档](https://developers.google.com/android/guides/module-install-apis)、[ML Kit 条款](https://developers.google.com/ml-kit/terms)
- [S12] [PaddleOCR-VL-1.6 官方模型卡](https://huggingface.co/PaddlePaddle/PaddleOCR-VL-1.6/tree/c5630abae1d940eafe0697512a0325494b02ab42)、[参数元数据](https://huggingface.co/api/models/PaddlePaddle/PaddleOCR-VL-1.6)
- [S13] [SmolVLM-256M-Instruct 官方模型卡](https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct/tree/7e3e67edbbed1bf9888184d9df282b700a323964)、[参数元数据](https://huggingface.co/api/models/HuggingFaceTB/SmolVLM-256M-Instruct)
- [S14] [Paddle 官方表格结构识别说明](https://github.com/PaddlePaddle/PaddleOCR/blob/2661c7c0ef5c613e8f93c6e93b2e052399f0f854/docs/version3.x/module_usage/table_structure_recognition.md)
- [S15] [ONNX Runtime Mobile 官方说明](https://onnxruntime.ai/docs/get-started/with-mobile.html)
- [S16] [llama.cpp 多模态支持与模型清单](https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md)
- [S17] [llama.cpp Android 官方构建与绑定说明](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md)
