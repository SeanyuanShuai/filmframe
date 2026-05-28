# JustFrame — Design Brief for Visual + Interaction Redesign

> **Audience**: A design agent (Claude or otherwise) tasked with redesigning JustFrame's visual identity and interaction model end-to-end.
> **Goal**: produce a new VI + interaction system that ships in `app/src/main/java/com/seanyuan/filmframe/ui/` as Jetpack Compose.
> **Format**: this single file should be enough to brief the redesign without reading code. Reference screenshots are in `docs/screens/`.

---

## 1. Product, in one paragraph

JustFrame is a single-purpose Android app that adds a **clean editorial frame** (Magnum / Aperture monograph style) to a photo, reads its EXIF, captions the print, and saves it back to the gallery. It is **open source, fully local, free**. The author is the primary user. The wedge is: *batch + offline + open + readable templates + EXIF*. Everything else — filters, stickers, AI, cloud, accounts — is explicitly out of scope.

---

## 2. Author / target user

- One person: a photographer / Tech-company PM who uses an OPPO Find X9 Ultra (HASSELBLAD) plus a Sony A7
- Working language: 中文 (CN); UI is CN
- Posts shots to Xiaohongshu / Instagram / personal blog with editorial borders
- Hates: cluttered editors, NOMO-style camera-body skeuomorphism, 黄油相机 stickers, sponsorship lockups
- **v0.2 success criterion**: "author uses it for 4 weeks without irritation" — no user count, no DAU

---

## 3. Aesthetic north star — call this the **Magnum bar**

| ✅ Aim for | ❌ Reject |
|---|---|
| Magnum / Aperture printed monograph | Polaroid sticker UI |
| 1980s Italian design typography | NOMO retro-camera lockscreens |
| Aperture magazine page proportions | Cartoon film-strip sprocket holes |
| Black & cream paper, gold restraint | Multi-color borders, neon |
| Sharp corners, generous mat | Rounded squircle cards everywhere |
| English caption (Cormorant italic) | Chinese display fonts on the photo |
| Silence, breath, asymmetry | Symmetric grid of busy chips |

The single best mental reference is an **Aperture quarterly** spread or the back-matter of a Magnum catalog.

---

## 4. Tech & constraints (these are hard floors)

- **Stack**: Jetpack Compose, Kotlin, Coil 3 for async image, androidx.exifinterface
- **Min SDK 26 / Target 36**
- **No OpenCV, no MLKit** — frame detection is a Kotlin pixel walk in `frame/FrameDetector.kt`
- **No backend, no network, no analytics** — 100% local
- **Fonts** (already loaded; all SIL OFL commercial-safe):
  - **DM Serif Display** — display / brand mark
  - **Cormorant Garamond** italic + regular — captions on the printed photo
  - **Inter** — UI body
  - **No Chinese typography on the rendered print** — caption is always English
- **Bitmap budget**: editor preview source caps at 3200 px long edge. Output is full-res. Avoid > 60 MB per allocation on mid-range devices.
- **No Material 3 stock chrome**. The current visual layer is a custom "Liquid Glass" approximation (`ui/glass/Glass.kt`). Redesign may replace it but must not regress to default M3 buttons.

---

## 5. Information architecture

```
Home  ──┬─ Picker (1 → Editor, 2+ → Batch)
        │
        ├─ Editor ── Save ─► Result ── (Re-template) ─► Editor (same source)
        │                          └─ Home / Picker / Share
        │
        ├─ Batch (Carousel) ── Export All ─► BatchResult ─► Home / Picker / Share
        │
        └─ Settings (Quality / Watermark / About)
```

There are **exactly 7 routes** in `MainActivity.kt`:

| Route | Depth | Lives in |
|---|---|---|
| `Home` | 0 | `ui/home/HomeScreen.kt` (Landing + Editor are two states of same screen) |
| `Picker` | 1 | `ui/picker/PhotoPickerScreen.kt` |
| `Batch` | 2 | `ui/batch/BatchScreen.kt` |
| `Settings` | 2 | `ui/settings/SettingsScreen.kt` |
| `Result` | 3 | `ui/result/ResultScreen.kt` |
| `BatchResult` | 3 | `ui/result/BatchResultScreen.kt` |
| *(implicit: Editor)* | — | nested in `HomeScreen` — toggled when a URI is selected |

The router uses depth to pick slide direction: forward = slide-in-from-right, back = slide-from-left. **Animation contract**: 320 ms tween, both fade + slide.

---

## 6. Screen-by-screen spec

Each section follows the same template:
- **Purpose** — why this screen exists
- **Layout** — what's where (ASCII)
- **States** — empty / loading / loaded / error
- **Interactions** — what the user can do
- **Reference screenshot** — file path in this repo
- **Known pain** — what's wrong today that the redesign should fix

### 6.1 Home — Landing state

**Purpose**: Daily entry point. The author opens this 4× per week and wants to see their last work + a single CTA to start.

**Layout** (current, simplified):
```
┌──────────────────────────────────┐
│ [JustFrame masthead]   [设置]    │  ← top bar (statusBarsPadding)
│                                  │
│  Gallery 级的胶卷边框              │  ← subtitle (Inter Medium 18)
│  本地处理 · EXIF 自动识别          │  ← tagline (Inter 13 faint)
│                                  │
│  最近作品 · N                     │  ← small caption
│  ┌──────┐ ┌──────┐ ┌──────┐      │  ← marquee row, auto-scroll left @ ~0.6px/frame
│  │ work │ │ work │ │ work │ →    │     no rounded corners (sharp edges, like prints on a wall)
│  │ port │ │ land │ │ port │      │     aspect ratio = source's own w:h
│  └──────┘ └──────┘ └──────┘      │
│                                  │
│  [   选照片   (accent)         ]  │  ← primary CTA fillMaxWidth
└──────────────────────────────────┘
+ behind everything: AmbientBackdrop — 2 low-saturation radial gradients
  (warm amber 0x3A2A1A @ 32%, cool slate 0x15202E @ 40%) drifting on
  22s and 31s periods, very slow.
```

**States**:
- **No READ_MEDIA_IMAGES permission** → marquee replaced by `EmptyExhibitHint("还没有作品", "选一张照片，给它一个胶卷感的边框")`
- **Permission granted, zero exports** → `EmptyExhibitHint("还没有作品", "你导出的作品会展示在这里，像挂在画廊里")`
- **Permission granted, N exports** → marquee with `virtualCount = N * 100` so it never reaches end

**Interactions**:
- Tap any artwork tile → navigates to `Result` in **saved-exhibit mode** (Coil-loads the file from disk; no in-session preview bitmap)
- Tap "选照片" → `Picker`
- Tap "设置" → `Settings`
- User may pan the marquee manually; auto-scroll resumes after gesture ends (via `scrollState.isScrollInProgress` guard)

**Reference**: [`docs/screens/01_landing.png`](screens/01_landing.png)

**Known pain to address in redesign**:
- The ambient backdrop is subtle to a fault — easy to miss
- Single fillMaxWidth CTA "选照片" feels like a button-button, not a print-shop counter
- Subtitle / tagline / "最近作品 · N" all use the same Inter weight — no typographic hierarchy
- Marquee tile spacing is uniform 18 dp; might want variable gap or "passe-partout" mats

---

### 6.2 Home — Editor state

**Purpose**: User picked one image. Choose a template, optionally tweak, save.

**Layout**:
```
┌──────────────────────────────────┐
│ [JustFrame masthead]    [保存]    │  ← save lives top-right as accent
│                                  │
│ ┌──────────────────────────────┐ │
│ │                              │ │
│ │   PREVIEW (Glass surface)    │ │  ← Image bitmap rendered with full template
│ │   FilterQuality = High       │ │     Crossfade(rendered, asyncImage(selectedUri))
│ │                              │ │     no rounded corner cropping on the bitmap
│ │                              │ │
│ └──────────────────────────────┘ │
│                                  │
│ ● 原图已有边框 · 自动移除  [保留] │  ← banner, only when FrameDetector says hasFrame
│                                  │
│ [Classic][Bold][纯色][Minimal][Polaroid]→  ← LazyRow of TemplateChip
│                                  │
│ [  换一张  ] [  调整  ]            │  ← 2 buttons (50/50)
└──────────────────────────────────┘
```

**States**:
- **Loading source** → preview area blank (Coil placeholder kicks in via AsyncImage fallback)
- **Rendered** → sharp bitmap, FilterQuality.High
- **Frame detected on source** → banner appears with toggle "保留" / "移除"
- **No template selected** → "调整" disabled; "保存" disabled
- **`showParams = true`** → `TemplateParamsPanel` slides up from bottom (border width, font sizes, EXIF toggle), covers nothing but the chips/buttons

**Interactions**:
- Tap a TemplateChip → re-renders (debounced 80 ms LaunchedEffect)
- Tap "保存" → opens ProcessingOverlay, exports at user-chosen quality, then `Result(summary)`
- Tap "换一张" → back to `Picker`
- Tap "调整" → toggle params sheet
- Tap "保留" / "移除" in banner → re-renders with `stripFrameChoice`
- BackHandler closes params sheet first, then resets all editor state, then bubbles up

**Reference**: [`docs/screens/02_editor.png`](screens/02_editor.png)

**Known pain**:
- The 5 template chips are text-only — user has to imagine each style
- Strip-frame banner is verbose; could become a single thin gold horizontal rule with hover-style hint
- "调整" panel is a separate sheet; might want inline parameter scrubbers under the preview

---

### 6.3 Picker

**Purpose**: Choose one or many photos. Picker is **mode-agnostic** — 1 selection → Editor; 2+ → Batch.

**Layout**:
```
┌──────────────────────────────────┐
│ [← 返回]  选择照片                 │
│           N 张照片 · 新到旧 / 已选… │
│                                  │
│ ┌────┐ ┌────┐ ┌────┐             │
│ │    │ │ ✓  │ │    │ ← 3-col grid │  selected = gold check chip
│ │    │ │    │ │    │              │
│ └────┘ └────┘ └────┘             │
│ ┌────┐ ┌────┐ ┌────┐             │  AsyncImage(Coil) for each tile
│ │    │ │    │ │    │             │
│ └────┘ └────┘ └────┘             │
│        ⋮                         │
│ [清空]  [选照片 / 编辑这张 / 进入批处理·N]  │ ← bottom action bar
└──────────────────────────────────┘
```

**States**:
- **No permission yet** → `PermissionPrompt` with "去授权" / "去设置" fallback
- **Permission denied, requestedOnce=true** → adds "去系统设置" button
- **Loading** → subtitle says "加载中…"
- **Grid loaded, 0 selected** → confirm disabled, "选照片" greyed
- **1 selected** → confirm reads "编辑这张"
- **N≥2 selected** → confirm reads "进入批处理 · N"; max select = 30

**Interactions**:
- Tap photo → toggle selection
- Tap "清空" → clear
- Tap confirm → calls `onConfirm(List<Uri>)` which the router maps:
  - 0 → back to Home
  - 1 → Home with prefilled URI → goes straight to Editor state
  - 2+ → Batch route

**Reference**: [`docs/screens/04_picker.png`](screens/04_picker.png) and [`docs/screens/05_picker_multi.png`](screens/05_picker_multi.png)

**Known pain**:
- 3-column grid is fine for utility, dull for the brand — competitor inspiration (Glass, Lapse) shows 1- or 2-up large tiles for browse
- Selected check is gold but the rest of the tile is unchanged; might want sepia inversion / desaturation of unselected tiles
- "选择照片" title is too generic; brand wordmark could anchor this screen too

---

### 6.4 Batch (Carousel)

**Purpose**: User picked 2+ images. Lay them out as a Polaroid-feel carousel; per-image template selection; bulk export.

**Layout** (already creative — keep the spirit):
```
┌──────────────────────────────────┐
│ [← 返回] 批处理 · N 张            │
│          x / N · TemplateName    │
│                                  │
│   ┌─────────────────────┐        │
│   │                     │        │  HorizontalPager with parallax depth
│   │     PhotoCard       │        │  scale/alpha varies with pageOffset
│   │   (current image)   │        │
│   │                     │        │
│   └─────────────────────┘        │
│        ● ●  ● ●                  │
│                                  │
│ [Tmpl strip — per-image]         │  TemplateStrip — Polaroid-style tiles
│                                  │
│ [全部应用…]      [导出全部]        │  Bottom action bar
└──────────────────────────────────┘
```

**States**:
- **Loading previews** → "加载中…" subtitle, spinner inside each PhotoCard
- **Ready** → full carousel interactive
- **Exporting** → `ProcessingOverlay` with progress (i/N)
- **Done** → routes to `BatchResult` (this commit just landed)

**Interactions**:
- Pager swipe to switch image
- Tap a template tile → applies to **current** image only
- "全部应用…" → opens a sheet listing templates; tap one → applies to all images
- "导出全部" → starts batch render; each image rendered at full-res, saved, summary collected, route to BatchResult

**Reference**: [`docs/screens/06_batch.png`](screens/06_batch.png)

**Known pain**:
- Carousel pageOffset parallax is subtle on small phones; might want stronger 3D feel
- TemplateStrip is at 360 px source thumb — looks slightly soft on bigger displays (intentional memory ceiling)
- "全部应用…" copy is awkward; might just call it "应用模板到所有"

---

### 6.5 Settings

**Purpose**: Picture quality, source-frame removal toggle, watermark, about.

**Layout** (vertically scrolling):
```
┌──────────────────────────────────┐
│ [← 返回]  设置                    │
│                                  │
│ 画质                              │
│ ┌──────────────────────────────┐ │
│ │ 原画 [?]                  ●  │ │ ← ? opens AlertDialog explaining
│ │ 保留全部像素 · 跟随源格式      │ │   JPEG re-encoding chroma 4:2:0 etc
│ │ 高    长边 4096…          ○  │ │
│ │ 中    长边 2800 · JPEG 92 ○  │ │
│ │ 低    长边 2000 · JPEG 85 ○  │ │
│ └──────────────────────────────┘ │
│                                  │
│ 原图处理                          │
│ ┌──────────────────────────────┐ │
│ │ 自动去除原边框              [⬤] │ │ ← Switch
│ │ 如 OPPO HASSELBLAD / 小米 Leica │ │
│ └──────────────────────────────┘ │
│                                  │
│ 水印                              │
│ ┌──────────────────────────────┐ │
│ │ 启用水印                   [⬤] │ │
│ │ 水印文字  [Sean Yuan       ]  │ │
│ │ 位置  [左上][右上][左下][右下] │ │
│ └──────────────────────────────┘ │
│                                  │
│ 关于                              │
│ ┌──────────────────────────────┐ │
│ │ 项目  JustFrame · v0.2 dev   │ │
│ │ 协议  开源 (MIT, 计划中)      │ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```

**Reference**: [`docs/screens/03_settings.png`](screens/03_settings.png) and the `?` dialog at [`docs/screens/10_settings_quality_help.png`](screens/10_settings_quality_help.png)

**Known pain**:
- All 4 sections look the same (white glass card on dark) — could lean into typography hierarchy
- "原图处理" name is engineering jargon; rename to "源图边框" or similar
- Watermark position is a 4-button grid; could be a small visual diagram showing the 4 corners

---

### 6.6 Result (single-photo save)

**Purpose**: Confirm the save, show what was written, offer next moves.

**Layout** (immersive gallery view):
```
┌──────────────────────────────────┐
│      [● 已保存 · ClassicTemplate]  │  ← top floating glass pill
│                                  │
│ ████████ (blurred 60dp backdrop) │  ← blurred copy of the same bitmap, 45% alpha
│ ████  ┌──────────────┐  ████     │
│ ████  │              │  ████     │
│ ████  │ SHARP PHOTO  │  ████     │  ← the same bitmap, sharp, ContentScale.Fit
│ ████  │              │  ████     │     padding(horizontal=18, vertical=96)
│ ████  └──────────────┘  ████     │
│ ████████████████████████         │
│                                  │
│ ┌──────────────────────────────┐ │  ← bottom glass card
│ │ 格式  JPG                     │ │
│ │ 尺寸  9088 × 7048             │ │
│ │ 画质  原画                    │ │
│ │ [首页] [分享] [换模板] [再来]  │ │  ← 4 GlassButton, compact=true
│ └──────────────────────────────┘ │
└──────────────────────────────────┘

Tap-anywhere toggles `chromeVisible` → AnimatedVisibility hides
the pill + bottom card for pure print view.
```

**Reference**: [`docs/screens/08_result.png`](screens/08_result.png)

**Saved-exhibit fallback** (entry from Landing marquee):
- `summary.previewBitmap = null`, `summary.sourceUri = null`
- Backdrop + foreground both use `coil3.compose.AsyncImage(summary.savedUri)` so the screen has content
- The `换模板` button **hides** (we don't have the in-session source to re-edit)
- The top pill drops the template suffix → just "● 已保存"
- Meta panel hides rows that contain `"—"` for a cleaner exhibit feel

Reference: [`docs/screens/09_saved_exhibit.png`](screens/09_saved_exhibit.png)

**Known pain**:
- Bottom card is a giant glass slab; competitor refs (Glass app) use a single floating row of icons
- Meta info (format/size/quality) is information-dense but rarely interesting at this moment
- The 4 buttons are functional but visually flat — could differentiate by importance (大 accent for primary action, ghost for secondary)

---

### 6.7 BatchResult

**Purpose**: After bulk export, show N thumbnails grid + status pill + 3 actions.

```
┌──────────────────────────────────┐
│ [● 批处理完成 · N / total]        │  ← top pill (green dot)
│                                  │
│ 已保存到 Pictures / JustFrame    │  ← headline
│ TemplateName · 原画               │  ← subtitle
│                                  │
│ ┌────┐ ┌────┐ ┌────┐             │  LazyVerticalGrid 3 cols
│ │    │ │    │ │    │             │  thumbnails from items[i].templatePreviews
│ └────┘ └────┘ └────┘             │
│ ┌────┐ ┌────┐ …                   │
│                                  │
│ [首页]  [分享全部]  [再选]         │  3 GlassButton compact=true
└──────────────────────────────────┘
```

**Reference**: [`docs/screens/07_batchresult.png`](screens/07_batchresult.png)

**Known pain**:
- Grid is uniform 1:1 cells — variable aspect would be more "gallery"
- Status pill / headline / subtitle could be more interesting typographically

---

## 7. Core components

These are reused across screens. Live in `ui/glass/` and shared composables in each screen.

### 7.1 GlassSurface
```kotlin
GlassSurface(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    intensity: Float = 1f,         // 0.5 = subtle bg, 1.5+ = focal modal
    content: @Composable () -> Unit,
)
```
A multi-layer translucency stack:
1. Base translucent fill (cool dark tint, `0.05f * intensity` alpha on `DeepSurfaceTint`)
2. Vertical gradient — brighter top, near-zero bottom
3. Outer border with vertical gradient
4. **Specular sheen drawn via `drawWithContent`** — a 1.4 dp horizontal highlight at the top edge. NEVER use nested `Box(fillMaxWidth())` for this — it wrecks Row weights.

### 7.2 GlassButton
```kotlin
GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,        // gold fill
    compact: Boolean = false,       // bodyMedium + 12dp padding (instead of bodyLarge + 22dp)
)
```
- Spring press scale 0.97 → 1.0
- Sheen highlight at top edge
- `accent = true` → gold gradient fill, dark text
- Text is **always single-line**: `maxLines=1, softWrap=false, overflow=Ellipsis`

### 7.3 TemplateChip (in HomeScreen)
- Currently text-only: "Classic", "Bold", "纯色", "Minimal", "Polaroid"
- Animated border, bg, text color
- 18dp horizontal padding × 10dp vertical
- **Top redesign candidate**: add visual preview thumbnail

### 7.4 EmptyExhibitHint
- 2-line centered text in `OnSurfaceMuted` + `OnSurfaceFaint`
- Used for picker empty, landing empty, etc.

### 7.5 ProcessingOverlay
- Full-screen modal with title + subtitle + optional progress bar
- Used for save-in-progress and batch export

---

## 8. Design tokens (current — feel free to overhaul)

```kotlin
// ui/glass/Glass.kt
object GlassColors {
    val DeepBackground   = Color(0xFF050507)  // near-black canvas
    val DeepSurfaceTint  = Color(0xFF1A1E25)  // subtle bluish glass tint
    val Accent           = Color(0xFFE4B86E)  // warm gold (Magnum sand)
    val AccentDeep       = Color(0xFFB47F2F)  // deeper gold for gradient bottom
    val AccentSoft       = Color(0x33D4A24A)  // 20% gold for chip backgrounds
    val OnSurface        = Color(0xFFFAFAFA)  // bone white
    val OnSurfaceMuted   = Color(0xCCFAFAFA)  // 80%
    val OnSurfaceFaint   = Color(0x77FAFAFA)  // 47%
}
```

Typography (`ui/theme/Type.kt`):
- `BrandMark` — DM Serif Display Italic 30sp, letterSpacing -0.5 (used in HomeTopBar)
- Body — system Inter via Material 3 Typography defaults
- Captions on the rendered print — Cormorant Garamond italic + DM Serif Display regular (chosen per-template inside `frame/FrameRenderer.kt`)

Spacing rhythm (from current code):
- Screen edge padding: **20dp** (top bars), **24dp** (Landing), **18dp** (Editor)
- Card inner padding: **16dp**
- Glass card shape: **RoundedCornerShape(22.dp)** (smaller for buttons: 14dp)
- Button vertical padding: 14dp normal, 12dp compact

Animation:
- Route transitions: 320 ms tween, slide + fade
- Crossfade(rendered) in Editor: 260 ms
- Marquee scroll: ~0.6 px / 16 ms frame
- Ambient backdrop sweeps: 22s + 31s LinearEasing infinite-reverse
- GlassButton press: spring(DampingRatioMediumBouncy, StiffnessMedium), scale 0.97↔1.0

---

## 9. Frame templates (the printed output spec)

Lives in `frame/FrameRenderer.kt`. **Do not redesign these without product approval** — they're the wedge.

| Template id | Display | Description |
|---|---|---|
| `classic` | Classic | Full white mat. Bottom caption: title (Cormorant italic) + EXIF params (Inter small) |
| `bold` | Bold | Bigger top mat, all-cream, large serif title + thin gold dividing rule |
| `solid` | 纯色 | Edge-to-edge minimal — black 1-2% inset on a near-black border (subtle "vignette") |
| `minimal` | Minimal | Hairline frame + small bottom caption only |
| `polaroid` | Polaroid | Square outer, bottom-heavy mat, vertical italic title |

Adjustable per template (via `TemplateAdjustments`):
- Border width ratio
- Title font size
- Which EXIF fields show (focal length, aperture, shutter, ISO)

---

## 10. Critical user flows (end-to-end)

### Flow A — "Frame yesterday's shot"
1. Open app → see masthead + marquee of recent works
2. Tap "选照片" → Picker grants permission if needed → grid loads
3. Tap one photo → "编辑这张" → Editor opens with Classic pre-selected
4. (If HASSELBLAD detected) banner shows "原图已有边框 · 自动移除"
5. Tap 2-3 template chips, settle on one
6. Tap "保存" top-right → ProcessingOverlay → Result
7. (Optional) Tap "换模板" → back to Editor same source → save again

### Flow B — "Review last week's work"
1. Open app → marquee drifts past 4-5 prints
2. Tap any print → Result in saved-exhibit mode (Coil-loaded, no in-session metadata)
3. Tap-anywhere toggles chrome → pure print view
4. Back → Home

### Flow C — "Process a session of 20 photos"
1. Open app → "选照片" → Picker
2. Long-tap-and-select 20 photos
3. "进入批处理 · 20" → Batch
4. Apply Classic to all via "全部应用…"
5. Optionally swipe through and override individual templates
6. "导出全部" → progress overlay → BatchResult grid
7. "分享全部" → ACTION_SEND_MULTIPLE chooser

---

## 11. Open-source / personality cues for the redesign

- The masthead **"JustFrame"** is the brand — set in DM Serif Display Italic, hugged tightly
- Author signature (in EXIF watermark) lives on the printed photo, not in the app chrome
- App is **dogfood-first**: any micro-interaction the author touches 40 times a week deserves spring physics; rarely-touched ones can be plain
- Reject the "Material" look — no Material 3 default chrome, no FAB, no bottom-tab navigation
- The frame app should feel like opening a **leather-bound contact-sheet binder** more than a phone editor

---

## 12. What the redesign must not break

- **Pixel preservation**: editor preview FilterQuality.High + 3200 source cap; export = full res, EXIF copied. Visual changes that lower preview sharpness or require down-sampling for layout are rejected.
- **Backward compat for the export folder**: scan both `Pictures/JustFrame/` (current) and `Pictures/FilmFrame/` (legacy).
- **Frame detector**: don't add UI states that hide the "原图已有边框" banner — the algorithm is the hidden hero feature.
- **No Chinese on the rendered photo**: caption typography stays Latin only.
- **BackHandler discipline**: every screen has its own BackHandler. Don't let system back accidentally exit the app from inside the Editor.
- **OOM resilience**: bitmap allocation always wrapped in retry-with-smaller-cap. Don't introduce new code paths that allocate without fallback.

---

## 13. Reference screenshots

Numbered to follow the flow:

| # | File | Shows |
|---|---|---|
| 00 | `docs/screens/00_logo.png` | Adaptive launcher icon |
| 01 | `docs/screens/01_landing.png` | Home / Landing |
| 02 | `docs/screens/02_editor.png` | Home / Editor state |
| 03 | `docs/screens/03_settings.png` | Settings |
| 04 | `docs/screens/04_picker.png` | Picker — fresh |
| 05 | `docs/screens/05_picker_multi.png` | Picker — multi-select |
| 06 | `docs/screens/06_batch.png` | Batch carousel |
| 07 | `docs/screens/07_batchresult.png` | BatchResult |
| 08 | `docs/screens/08_result.png` | Result (single, fresh save) |
| 09 | `docs/screens/09_saved_exhibit.png` | Result (saved-exhibit fallback from Landing) |
| 10 | `docs/screens/10_settings_quality_help.png` | Settings quality `?` dialog |

---

## 14. Asks for the redesign

In order of importance:

1. **Re-imagine the Landing** as a gallery wall — sharper hierarchy between marquee and CTA; richer empty state; ambient backdrop that actually reads as light
2. **Editor preview area** — make the photo float (heavier shadow, gallery-lit), drop the GlassSurface chrome around the bitmap if it's competing with the print
3. **Template selection** — keep efficiency (text chips) but add a way for new users to *see* what each template does to the current photo (hover-preview? a peek strip?)
4. **Result** — strip the meta-data table down to one line; make the photo the hero; the buttons should feel like museum placards, not action chips
5. **BatchResult** — variable aspect grid, not 1:1
6. **Brand application** — propose a logo evolution (or argue the current one is fine); apply the masthead consistently to every top bar
7. **Color palette** — keep gold restraint but propose 1-2 additional tones (e.g., a deep ink blue for state) so the UI has more than "black + gold + white"
8. **Motion language** — define what motion *means* (entrance, action, ambient) so we stop one-offing it

---

## 15. Deliverables (what to give back)

- New Compose composables under `ui/` — replace files, don't dual-track
- Updated `Glass.kt` color tokens + any new typography styles in `Type.kt`
- Optional: updated `ic_launcher_foreground.xml` if the logo evolves
- A short `docs/REDESIGN_NOTES.md` explaining choices

**Do not touch**: `frame/*`, `data/*` (frame detector + bitmap loader + media gallery + image exporter + settings). Those are the engine; redesign is chrome.

---

*End of brief. Pair with `CLAUDE.md` in the repo root for the project memory and engineering lessons.*
