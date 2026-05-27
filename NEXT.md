# 明天接着干 / Resume Pointer

> 最后一次更新：2026-05-27 晚

## ✅ 当前进度（一周冲刺）

**v0.1 主流程 + UX v2 完成**：

- **Day 1-3**：Photo Picker + EXIF + Classic 模板 + 导出
- **Day 4**：5 模板 + 字体（Cormorant Garamond / DM Serif Display / Inter，全 OFL 商用）
- **Day 5**：批处理多选 + 每图独立模板预览
- **Day 6**：PNG/JPEG/WebP 格式跟随源 + 水印 + 设置
- **P0 + P1**：EXIF 旋转修复 + 持久化上次模板 + 降采样提示 + EXIF metadata 保留 + 水印 4 角 + 模板参数微调 + App 图标
- **新设置**：画质 4 档 + 自动去除原边框 toggle
- **UX v2**（今天大改）：WindowInsets 兼容 + iOS 26 Liquid Glass + 完整动画 + Gallery 沉浸式 ResultScreen + 自建 MediaStore picker

最新 commit：`fb88c7e feat(picker): custom MediaStore gallery picker with big thumbnails`

## ⏭️ 明天的下一步候选（按你昨天提到的 + dogfood 反馈定）

### 实测优先级（先测今天的 5 大改造，看哪个反馈最差就先修）
1. **状态栏 / 顶栏兼容** — 各机型测
2. **Picker 2 列大缩略图** — 是否够清晰
3. **路由动画 + 玻璃质感** — 是否够 iOS 26 范儿
4. **Gallery 结果页沉浸感** — 点照片 chrome toggle
5. **5 个模板 + 参数面板** — 视觉质量到没到 Magnum

### 还能雕的细节（按需挑）
- 长按 Picker 缩略图 → 全屏预览大图再选
- ResultScreen 左右滑切换最近 5 张已保存图
- Polaroid 模板字体/比例再调
- Onboarding 三步首启介绍
- shared element transition（标题文字飞跃式跨页）
- v0.1 tag + 构建 release APK 给自己用

## 🗂 当前架构（明天接手用）

```
ui/
├── theme/                    Compose theme (FilmFrameTheme)
├── glass/Glass.kt            Liquid Glass v2 primitives (Surface / Button)
├── home/HomeScreen.kt        Landing + Editor (Crossfade 二态)
├── picker/PhotoPickerScreen  自建 MediaStore picker，单/多选模式
├── batch/BatchScreen.kt      多图批处理 + 每图模板预览
├── settings/SettingsScreen.kt 画质 / 原图处理 / 水印 / 关于 4 section
├── params/TemplateParamsSheet.kt 边框宽度 / 字号 / EXIF 字段开关
├── common/ProcessingOverlay.kt 全屏渲染中 modal
└── result/ResultScreen.kt    Gallery 沉浸式结果页 + 模糊背板 + chrome toggle

frame/
├── FrameTemplate (interface) + 5 实现（Classic/Bold/Solid/Minimal/Polaroid）
├── FrameRenderer             matCanvas 工具 + 水印绘制
├── FrameDetector             边框检测算法（4 角 + 沿边扫描）
├── FrameProcessor            统一 load/detect/render pipeline
├── Fonts                     懒加载 Typeface（Cormorant/DM Serif/Inter）
└── TemplateAdjustments       borderWidthMult / titleSizeMult / showCaption

data/
├── BitmapLoader              + EXIF 旋转 + 内存自适应降采样
├── ExifReader                6 字段读取 + rational 解析
├── ImageExporter             4 档画质 + 格式跟随 + EXIF 复制
├── MediaGallery              MediaStore 相册查询
├── PhotoExif (data class)
└── Settings                  DataStore: 水印 + 上次模板 + 画质 + 自动去边框
```

## 🔧 速查

```bash
# 编译装机
cd ~/filmframe
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug

# 启动 App
~/Library/Android/sdk/platform-tools/adb shell monkey -p com.seanyuan.filmframe -c android.intent.category.LAUNCHER 1

# 截手机屏
~/Library/Android/sdk/platform-tools/adb exec-out screencap -p > /tmp/screen.png

# 卸载重装（刷新桌面图标缓存等）
~/Library/Android/sdk/platform-tools/adb uninstall com.seanyuan.filmframe
```

## 💡 重新打开 Claude Code 接手

```bash
cd ~/filmframe
claude
```

第一句：

> 看 `NEXT.md`，今天测了 UX v2 改造，[反馈]。我想 [继续雕 / 进 v0.1 release / 别的]。

Claude 通过 memory 认识这个项目，读 NEXT.md + git log + 当前代码自动接续。

仓库：https://github.com/SeanyuanShuai/filmframe
