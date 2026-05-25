# 明天接着干 / Resume Pointer

> 最后一次更新：2026-05-25 晚

## ✅ 已经做完的（v0.1 大致 Day 1-3）

- **Day 1**：Photo Picker（系统相册，无需权限）+ EXIF 读取（6 字段）+ Make/Model 去重
- **Day 2**：FrameDetector 边框检测算法（4 角采样 + 沿边扫描，85% 匹配率 + 3 行容差）+ De-frame 裁切
- **Day 2.5**：ClassicTemplate 渲染器（Magnum 风：5% 边、14% 底、italic serif + small caps + 0.18em 字距）
- **Day 3**：SolidTemplate 纯色模板（7% 等边、无文字）+ ImageExporter（MediaStore，写 Pictures/FilmFrame/，JPEG 95，最大 4096px）

## ⏭️ 明天的下一步（按顺序）

1. **质量校准**（不写代码，先看）：把今天导出的图拖到 Mac 大屏，确认 Classic 模板设计感是否到 Magnum 标准
   - 如果字太小/太大 → 改 `ClassicTemplate.titleSize` 系数（当前 `longEdge * 0.020f`）
   - 如果版心比例不对 → 改 `sideMarginPct` / `bottomMarginPct`
   - **lock 视觉质量再上 Bold/Minimal**，不要边扩边调

2. **Day 4 - Bold 模板**（`app/src/main/java/com/seanyuan/filmframe/frame/FrameRenderer.kt`）
   - 全黑宽边 + 底部居中两行 serif：大字相机名 + 小字参数
   - 参考富士机身底部水印那种排版

3. **Day 4 - Minimal 模板**
   - 极窄白边（长边 1-2%），无文字
   - 跟 Solid 的区别：Solid 是"框"，Minimal 是"线"

4. **Day 5 - 一产出多模板**
   - 把现在的"两个按钮" UX 升级成横向滑动 3 卡片预览
   - 用户挑一张选中再进参数微调
   - 这是 wedge 的关键 UX，要打磨

5. **Day 6 - 批处理 + 持久化**
   - 多选导入（已经支持 `PickMultipleVisualMedia`）
   - 单模板套用 N 张图 + 进度条
   - DataStore 存上次用过的模板

## 💡 如果重新打开 Claude Code 想接着干，第一句这样说

> 我是 FilmFrame 项目的作者。看 `~/filmframe/NEXT.md` 了解进度。
> 我现在想做 [Day 4 Bold 模板 / Day 5 多模板预览 / 别的]。

Claude 会自动读这个文件 + git log + 当前代码状态，就接上了。

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
```

仓库：https://github.com/SeanyuanShuai/filmframe
