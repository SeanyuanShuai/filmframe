# CLAUDE.md — FilmFrame Project Memory

> 给后续 Claude session 用的项目记忆。打开这个仓库的任何对话，先读这里再动手。

---

## Context

- **作者**：SeanyuanShuai（TikTok LIVE PM，工作语言中文）
- **项目**：开源 Android 摄影边框 App。作者人生第一个 Android App，**vibe coding 学编程的载体**
- **时间线**：2026-05-25 启动，v0.1 主流程 + UX 已完成，处于真机 dogfood 调优阶段
- **仓库**：https://github.com/SeanyuanShuai/filmframe（本地 `~/filmframe`）
- **调试机**：OPPO Find X9 Ultra（型号 PMA110）
- **未来计划**：iOS 版本要做，架构需提前考虑可移植性

---

## 用户的产品要求（写在心里别忘）

### 审美灯塔
**Magnum / Aperture 摄影集风**。印刷品质感、宽边、底部 caption、衡体 italic 标题 + sans-serif 参数。
反对：
- ❌ 黄油相机式胶卷齿边 + 序列号 + 怀旧贴纸
- ❌ NOMO 式相机机身拟物
- ❌ 醒图式花哨贴纸 + 多色边框

### 核心 wedge（按用户重要度排）
1. **批处理 + 本地处理 + 开源免费 + EXIF 读取 + 模板可编辑**
2. 单图先做精，批处理是 v0.2 扩展（**已挪到 Day 5 完成**，但仍保留单图优先级）
3. 边框检测 + 自动移除原边框（OPPO HASSELBLAD / 小米 Leica 等）是隐形 hero feature

### 画质要求是绝对的
- **完全不压缩**。输出格式跟随源：JPEG → JPEG 100，PNG → PNG，WebP → WEBP_LOSSLESS，HEIC → JPEG 100
- **EXIF metadata 必须保留**（机身 / 镜头 / 焦距 / 光圈 / 快门 / ISO / GPS / 时间）
- **画质 4 档**（原画 / 高 / 中 / 低）让用户选，但即使"低"也要保持发朋友圈级（不要几百 KB）

### 字体要求
- **高规格、优雅、可商用**
- 已选：Cormorant Garamond Italic + DM Serif Display Regular + Inter Variable
- 全部 SIL OFL（OFL.txt 已附在 res/font 旁的 license-text 中）
- **不支持中文字体** — 用户明确说英文 caption 即可

### 边框检测必须裁干净
不允许残留 1px 的原边框。当前用 **FrameDetector v3 双 pass**：
1. STRICT (0.92 / 3 miss) 确认有边框
2. LOOSE (0.75 / 10 miss) 测量真正范围
3. loose ≤ strict×3 防过裁
4. 0.6% safety margin 咬过 AA fringe
5. 50% 长度硬上限

### 批处理 UX 必须有创意
- **不要列表形态**
- 当前方案：**HorizontalPager Carousel + 拍立得风格模板瓦片**
- 拟物感 + 弹簧动画 + parallax depth

### 玻璃风
**iOS 26 Liquid Glass 美学**：
- multi-layer translucency + specular 高光 + spring 按压反馈
- 在 Android 上没有真 backdrop blur（API 31+ 限制），用近似版

### v0.1 成功标准
> **作者本人连续 4 周自用不烦**

不追用户数 / star / 朋友推荐。

---

## 经验教训（不要重复犯）

### Compose / UI
1. **layout bug 必须真机验证** — 静态阅读看不出 `fillMaxWidth()` 在 wrap-content 父级中的传播副作用。曾导致 ParamsPanel 标题被挤成 "B/o/l/d" 竖排
2. **高光 / overlay 用 `Modifier.drawWithContent`** — 而非嵌套 `fillMaxWidth()` Box。drawWithContent 只画不参与测量
3. **路由型 App 每个 sub-screen 必须 BackHandler** — 否则系统返回 bubble up 到 Activity finish = "退出 App"
4. **Slider state 重渲染要 debounce** — `LaunchedEffect(...) { delay(80); render() }`。key 变化 cancel 旧协程，只渲染最终值

### 算法
5. **单一阈值检测器不够鲁棒**。严了停在文字行，松了误判暗部。**双 pass + 比例 cap** 是当前最优
6. **角落 variance gate (`CORNER_INTRA_VARIANCE_LIMIT = 18`) 是关键守门员** — 防止四角不一致的图被当成有边框
7. **MIN_FRAME_RATIO 1.2%** — 典型边框 >5% dim，1.2% 过滤随机匹配但保留细边框

### Bitmap / IO
8. **`MediaStore.LIMIT N` 字符串在 API 30+ 静默丢弃** — 必须用 `Bundle` + `ContentResolver.QUERY_ARG_LIMIT`（API 26+ 可用）
9. **EXIF Orientation 旋转必须显式处理** — 大量手机以横向存储 + 旋转 tag。否则竖拍照片渲染会横躺
10. **PNG quality int 被 Android 忽略** — 永远无损，传啥都一样
11. **JPEG quality 100 仍是有损** — DCT 块变换是 JPEG 规定。真无损要用 PNG / WEBP_LOSSLESS（但牺牲 EXIF）
12. **OOM 必须每个 Bitmap 创建路径都包** — `loadForExport` 有 fallback 链，但 render 的 output bitmap 是独立 OOM 面
13. **批处理预览源 360px 就够** — 1:1 对应 120dp tile 在 3x density 屏，再大都是浪费内存（v0.1 早期用 900px 导致 30 张批处理 OOM）

### 设计
14. **OFL 字体下载用变量字体路径** — `github.com/google/fonts/raw/main/ofl/<name>/<Name>[wght].ttf`。`static/` 子文件夹大多已废弃
15. **拟物感关键是物理层次**：drop shadow + paper-white 边 + 微旋转。selected = 立直 + 弹簧
16. **Liquid Glass 在 Android 无真 backdrop blur 也可逼近** — multi-layer translucency + specular 高光 + 弹簧按压

### Vibe Coding 工程节奏
17. **每个 milestone 后立即 commit + push** — `feat(...)` / `fix(...)` 形式，message 包含动机不只是"what"
18. **TESTING.md 矩阵 + 真机回归是 QA 双保险**
19. **不要绕过用户的产品方向** — 用户多次明确批处理优先级 / 字体规格 / 不要 chinese caption。每次出现似乎要绕过的诱惑（如"先简化用 ASCII")，回头看 CLAUDE.md

---

## 项目架构

### 文件结构
```
app/src/main/java/com/seanyuan/filmframe/
├── MainActivity.kt              # AppRoot + AnimatedContent 路由
├── data/                        # 纯逻辑 + Android API binding
│   ├── BitmapLoader.kt          # decode + EXIF 旋转 + 多档 fallback
│   ├── ExifReader.kt            # androidx.exifinterface 包装
│   ├── ImageExporter.kt         # MediaStore 写入 + EXIF 复制 + 格式跟随
│   ├── MediaGallery.kt          # MediaStore.Images 查询
│   ├── PhotoExif.kt             # data class（纯 Kotlin） ★ iOS 可直接搬
│   └── Settings.kt              # DataStore Preferences 存储
├── frame/                       # 边框处理核心
│   ├── FrameDetector.kt         # 双 pass 检测；exposes detectFromPixels() ★ 纯算法可移植
│   ├── FrameRenderer.kt         # 5 模板渲染（Canvas）
│   ├── FrameProcessor.kt        # 流水线编排
│   ├── TemplateAdjustments.kt   # 调整 data class ★ iOS 可搬
│   └── Fonts.kt                 # Typeface lazy load
└── ui/                          # 全 Compose
    ├── glass/                   # Liquid Glass 组件
    ├── home/                    # 首页 + 编辑器
    ├── picker/                  # 自建 MediaStore 选择器
    ├── batch/                   # Carousel 批处理
    ├── settings/                # 设置页
    ├── result/                  # Gallery 沉浸结果页
    ├── common/                  # ProcessingOverlay
    └── params/                  # 模板参数面板
```

### 路由
```
Home (Landing 或 Editor 模式)
  ├─ PickerSingle ─► consumes URI → Home (Editor)
  ├─ PickerMulti ─► uris → Batch
  ├─ Settings
  ├─ Batch (Carousel)
  └─ Result (Gallery 沉浸)
```

### Compose 关键模式
- **AppRoot.AnimatedContent** + transition spec 按 depth 决定方向滑动
- **`Crossfade`** 用于 Landing ↔ Editor 切换 + preview 原图 ↔ 渲染图切换
- **`AnimatedVisibility`** 用于 banner / chip / modal 进出
- **`animateColorAsState` + `animateDpAsState` + `spring()`** 用于交互反馈

---

## iOS 移植策略

### 现在已经原生可移植（纯 Kotlin / 算法）
| 模块 | 备注 |
|---|---|
| 所有 data class | `PhotoExif`、`FrameInsets`、`FrameDetectionResult`、`TemplateAdjustments`、`ExportQuality`、`WatermarkSettings`、`WatermarkPosition` |
| `FrameDetector.detectFromPixels(IntArray, w, h)` | ★ 已显式暴露纯 API。iOS / KMP 直接调用 |
| 模板比例常数 | `ClassicTemplate.sideMarginPct = 0.05f` 等 |
| 字符串格式化 | `composeTitle`、`composeParams`、formatFocal/Aperture/Shutter |
| EXIF tag 列表 | `EXIF_TAGS_TO_COPY` 数组 |

### Android-specific → iOS 等价物
| Android | iOS 等价 |
|---|---|
| `android.graphics.Bitmap` | `UIImage` / `CGImage` |
| `android.graphics.Canvas` + `Paint` | Core Graphics / `CGContext` |
| `BitmapFactory.decodeStream` | `UIImage(contentsOf:)` / `CGImageSourceCreateWithData` |
| `MediaStore.Images` | `PHAsset` + `PHPhotoLibrary` |
| `androidx.exifinterface` | `CGImageMetadata` / `ImageIO` |
| `ContentResolver.openInputStream` | `PHImageManager.requestImageData` |
| `Modifier.blur(API 31+)` | `UIVisualEffectView`（iOS 真 backdrop blur，反而更好）|
| `DataStore Preferences` | `UserDefaults` 或 `Codable` + FileManager |
| Compose UI | SwiftUI |
| `Coil 3 AsyncImage` | `AsyncImage`（SwiftUI 自带，iOS 15+）|
| `BackHandler` | `NavigationStack` + push/pop |
| `HorizontalPager` | `TabView(.page)` 或 ScrollView + LazyHStack + paging |
| `WindowInsets.statusBars` | `safeAreaInsets` |
| `Bitmap.compress(JPEG, 100)` | `UIImage.jpegData(compressionQuality: 1.0)` |

### 推荐策略（不阻塞当前开发）
| 阶段 | 做法 |
|---|---|
| **v0.1 - v0.3** | Android 集中开发，不引入 KMP 复杂度。算法已显式暴露 pure API（如 `detectFromPixels`），data class 全部 KMP-ready |
| **v0.3 之后评估** | 看 iOS demand 是否真出现。如果出现：先写 `IOS_PORTING_SPEC.md`（数据模型 + 算法伪代码 + 设计 token），iOS 用 Swift 手抄，约 50% 代码量重复 |
| **更激进**（不推荐 v0.1）| KMP shared module 把 `frame/`（去掉 Canvas 调用）+ `data/` 纯逻辑做成 multiplatform。需 3 天搭基础 + 持续维护成本 |

### 不应该 share 的
- ❌ UI 层 — iOS 用户期待 SwiftUI 原生手感
- ❌ 平台 IO（MediaStore vs PHAsset 模型差距大）
- ❌ 字体加载 / 系统 picker
- ❌ Canvas / Paint 渲染调用

### iOS 26 Liquid Glass 在 iOS 反而更好
- 直接用 `.glassEffect()` Modifier（iOS 26 SDK）
- 不需要我们 Android 上的多层 translucency hack
- 设计 token（颜色、形状、margin %）可直接复用

---

## 构建 / 调试 cheat sheet

```bash
# 装机
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/filmframe && ./gradlew installDebug

# 启动
adb shell monkey -p com.seanyuan.filmframe -c android.intent.category.LAUNCHER 1

# 截图
adb exec-out screencap -p > /tmp/ff.png

# UI 节点 dump（找精确坐标）
adb shell uiautomator dump /sdcard/d.xml
adb shell cat /sdcard/d.xml | grep -oE 'text="[^"]*"|bounds="[^"]*"'

# 卸载（清状态）
adb uninstall com.seanyuan.filmframe

# 只打 APK 不装机（手机没连时）
./gradlew assembleDebug
# 输出在 app/build/outputs/apk/debug/app-debug.apk
```

---

## Open Tickets（待办）

- App 图标对比度 — 桌面上偏暗看不清
- Picker 长按预览大图（用户原始"看不清楚"复杂场景的进一步缓解）
- Polaroid 模板字体位置精修
- Onboarding 首启三步介绍
- v0.1 release APK + tag
- `LocalLifecycleOwner` deprecated 警告（要加 `lifecycle-runtime-compose` dep）

## 永远不做（用户明示）

- ❌ RAW 支持
- ❌ App 内拍照入口（系统相机够用）
- ❌ 账号 / 云端 / 内购
- ❌ 中文字体
- ❌ 自由组件设计器（模板参数微调到顶）

---

## How to apply this memory

后续 Claude session 开始时：
1. 读这份 CLAUDE.md
2. 跑 `git log --oneline -10` 看最近 commit
3. 读 `NEXT.md` 看上次结束状态
4. 改代码前真机回归 — Compose layout 不真机不放心
5. 每个 milestone 后 `git add -A && git commit -m "feat(...): ..."` + push
