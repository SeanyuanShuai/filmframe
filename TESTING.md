# FilmFrame · Manual + Static QA Matrix

Test cases collected for v0.1 single-photo + batch flows. Format:
**ID | Scenario | Expected | Risk** with HIGH / MED / LOW priority.

Static-only analysis on dev machine (no device attached); this is a code
review pass paired with structural defensive fixes. Real-device runs by
the user.

---

## 1. Landing & Navigation

| ID | Scenario | Expected | Risk |
|---|---|---|---|
| N1 | App cold start | Landing page shows in <1s on modern phones | LOW |
| N2 | Status bar overlap | TopBar respects status bar inset, no clipped buttons on notched/punch-hole devices | HIGH (was bug) |
| N3 | Nav bar overlap (gesture) | Bottom actions sit above the gesture indicator | MED |
| N4 | Navigate Home → Picker → Home | Horizontal slide animation in/out + fade. Smooth, no flicker. | LOW |
| N5 | Settings ← Back twice | Back from Settings → Home, no stale data | LOW |
| N6 | Result page tap to toggle chrome | Top pill + bottom actions fade in/out | LOW |
| N7 | Rotation while in Editor | State preserved or graceful loss (we don't rotate-lock) | MED |

## 2. Photo Picker (Custom)

| ID | Scenario | Expected | Risk |
|---|---|---|---|
| P1 | First entry to PickerSingle | Permission prompt → grant → grid loads 2 columns | HIGH |
| P2 | Permission denied once | Permission prompt screen with "授权访问相册" CTA, re-request works | HIGH |
| P3 | Permission denied permanently | System dialog won't show; user stuck on prompt — **need fallback to system settings** | HIGH (BUG) |
| P4 | Library with 50k photos | Load capped at 800, scroll smooth | HIGH (BUG: LIMIT ignored on API 30+) |
| P5 | Empty library | "0 张照片" + still shows grid (empty) | LOW |
| P6 | Multi-select 30 | Cap at 30 enforced, "进入批处理 (30)" appears | LOW |
| P7 | Multi-select tap selected cell | Deselects, counter decrements | LOW |
| P8 | "清空" button | All checks removed, counter = 0 | LOW |
| P9 | Single-mode tap a cell | Returns to Home with selectedUri set, editor opens | LOW |
| P10 | Re-enter after granted | No prompt, grid loads directly | LOW |

## 3. Editor (Single Photo)

| ID | Scenario | Expected | Risk |
|---|---|---|---|
| E1 | Pick a JPG with full EXIF | All 5 templates render, Classic shows "OPPO Find X9 Ultra · 70mm · f/2.2 · 1/265s · ISO 50" | LOW |
| E2 | Pick a screenshot (no EXIF) | Templates render with empty caption (or PhotoExif() defaults) | MED |
| E3 | Pick a HEIC | Loaded via BitmapFactory + EXIF tags read; output is JPEG | MED |
| E4 | Pick 50 MP photo | Source loads 1600px for preview; full-res render only on export | HIGH |
| E5 | Pick photo with EXIF Orientation=6 (rotated 90°) | Bitmap auto-rotates to display correctly; frame wraps correct axis | HIGH (was bug, fixed) |
| E6 | Switch template (Classic→Bold) | Preview re-renders with crossfade animation | LOW |
| E7 | Toggle "调整" | Params panel slides up from bottom | LOW |
| E8 | Drag border-width slider rapidly | **Should debounce** — currently fires 60 renders/sec, lags | HIGH (BUG) |
| E9 | Toggle "显示 EXIF" off | Caption disappears | LOW |
| E10 | Watermark on, drag text input | Real-time preview update (acceptable lag) | MED |
| E11 | "保存" with full EXIF JPEG | ProcessingOverlay shows, then ResultScreen with EXIF preserved in output | LOW |
| E12 | OOM during full-res render | Should fallback to smaller cap (chain in BitmapLoader covers source; render output not caught) | HIGH (BUG) |
| E13 | "再来一张" from Result | Back to PickerSingle directly, fresh state | LOW |
| E14 | Frame-detection banner on OPPO HASSELBLAD photo | Banner shows "自动移除" + chip lets you toggle to "保留" | LOW |

## 4. Batch Flow

| ID | Scenario | Expected | Risk |
|---|---|---|---|
| B1 | Pick 30 photos | Batch grid loads all previews progressively | HIGH (BUG: 525 MB peak memory) |
| B2 | Pick 5 photos | Loads in 2-3 seconds | MED |
| B3 | Tap template chip on item card | Per-image selection updates instantly | LOW |
| B4 | "全部 Classic" | All items switch to Classic, chip border on every card | LOW |
| B5 | "导出全部" | Sequential export with progress bar | MED |
| B6 | Export fails midway (OOM) | Failed items marked; remaining continue or stop? | MED |
| B7 | Batch with mixed JPG/PNG/HEIC | Each item exported in source format (or JPEG fallback for HEIC) | MED |
| B8 | Re-enter Batch from "返回首页" | Previews persisted in memory? No — fresh state on each entry | LOW |

## 5. Settings

| ID | Scenario | Expected | Risk |
|---|---|---|---|
| S1 | First entry | Defaults: Original / Auto-remove ON / Watermark OFF | LOW |
| S2 | Tap "中" quality row | Persists immediately via DataStore | LOW |
| S3 | Toggle "自动去除原边框" off | Persists immediately | LOW |
| S4 | Toggle watermark on, type text, tap "保存水印 & 返回" | Persists then navigates back | LOW |
| S5 | Toggle watermark on, type text, press system back | Watermark text changes LOST (only quality/frame autosaved) | MED (BUG: inconsistent save semantics) |
| S6 | Set watermark position (4 corners), save, export | Watermark renders in chosen corner | LOW |
| S7 | Reopen app | All settings persisted from last session | LOW |

## 6. Export & Picture Quality

| ID | Scenario | Expected | Risk |
|---|---|---|---|
| Q1 | Original on JPG source | Same dims as source, JPEG q=100, EXIF metadata copied | LOW |
| Q2 | High on 50 MP source | Caps at 4096px long edge, source format preserved | LOW |
| Q3 | Medium on JPG | Forces JPEG q=92, ~500KB-2MB | LOW |
| Q4 | Low on JPG | Forces JPEG q=85, ~500KB-1.5MB | LOW |
| Q5 | PNG source + Medium | Forces JPEG q=92, ext .jpg, no EXIF (PNG doesn't have full EXIF anyway) | LOW |
| Q6 | WebP source + Original | WEBP_LOSSLESS on API 30+, PNG fallback on older | LOW |
| Q7 | HEIC source any quality | Output is JPEG (Bitmap.compress can't write HEIC) | LOW |
| Q8 | OOM at full-res Original | Falls back through 8192/6144/4096; downsample reported in ResultScreen | HIGH (covered, but render OOM uncaught) |
| Q9 | Output preserves original EXIF (JPEG) | Lightroom/Apple Photos reads camera/lens/GPS/datetime | LOW |
| Q10 | Output filename | `FilmFrame_<timestamp>.<ext>` in Pictures/FilmFrame/ | LOW |

## 7. Frame Templates

| ID | Scenario | Expected | Risk |
|---|---|---|---|
| T1 | Classic on landscape JPG | 5% sides, 14% bottom, italic Cormorant title + Inter params | LOW |
| T2 | Bold on portrait JPG | 8% sides, 18% bottom, DM Serif Display title | LOW |
| T3 | Solid on square crop | Equal 7% margins, no text | LOW |
| T4 | Minimal | 1.2% hairline white, no text | LOW |
| T5 | Polaroid | 4.5% top/sides, 24% bottom, italic title + date | LOW |
| T6 | Adjustments borderWidth 50% | All margins shrunk to half | LOW |
| T7 | Adjustments borderWidth 150% | All margins grown to 1.5x | LOW |
| T8 | Adjustments titleSize 60% | Caption text shrunk | LOW |
| T9 | Adjustments showCaption off | Caption hidden on Classic/Bold/Polaroid; Solid/Minimal unaffected | LOW |
| T10 | Watermark + 4 corners | Renders in chosen corner with contrast color per template | LOW |

## 8. Frame Detection

| ID | Scenario | Expected | Risk |
|---|---|---|---|
| D1 | OPPO HASSELBLAD photo | Detects with high confidence, isBottomHeavy=true | LOW |
| D2 | NOMO film photo | Detects square white border | LOW |
| D3 | Plain landscape photo | No frame detected | LOW |
| D4 | Photo with white sky (uniform top edge) | Doesn't false-positive (all-4-sides requirement saves us) | MED |
| D5 | Photo with gradient frame (rare) | Not detected (we only handle solid color) | LOW |
| D6 | Very small image (200×200) | Either detects or skips; doesn't crash | MED |

## 9. Memory & Performance

| ID | Scenario | Expected | Risk |
|---|---|---|---|
| M1 | Idle landing → pick photo → preview rendered | <2 sec total on modern phone | LOW |
| M2 | Pick 5 large photos in batch | Loads in <10s, doesn't OOM | HIGH (BUG) |
| M3 | Slider drag in adjustments | Smooth, doesn't lag UI | HIGH (BUG) |
| M4 | Multiple rapid back/forward navigation | No state leak, GC clears bitmaps | MED |
| M5 | Picker scrolling | 60fps even at 800 entries | LOW |
| M6 | Result page blur backdrop | Renders without dropped frames | LOW |
| M7 | Switch from Original to Low quality mid-edit | Setting changes, next export uses Low | LOW |

---

## Bugs identified (will fix in this pass)

1. **B1 [HIGH]** MediaGallery LIMIT in sortOrder string ignored on API 30+
2. **B2 [HIGH]** Slider rapid renders not debounced — laggy
3. **B3 [HIGH]** Batch preview memory 525 MB peak for 30 images
4. **B5 [MED]** Permanent permission denial dead-ends
5. **B6 [MED]** SettingsScreen mixed save semantics — watermark text loss on back
6. **B15 [MED]** Export render path no OOM catch on output bitmap allocation

## Performance optimizations applied

- P1: Render LaunchedEffect debounce via `delay(80)` — slider drags settle before render
- P2: Batch preview source reduced 900px → 360px (~6× less memory)
- P3: MediaGallery uses ContentResolver.QUERY_ARG_LIMIT (also adds `QUERY_ARG_SQL_SORT_ORDER`)
- P4: Render output OOM caught with downgrade to lower quality

Coverage of edge cases handled by code (no fix needed):

- ✓ Empty EXIF / screenshots — PhotoExif() defaults, composeTitle/composeParams produce empty strings
- ✓ Failed bitmap decode — loadSampled returns null, callers null-check
- ✓ Source format detection — explicit MIME sniffing with fallback to JPEG
- ✓ EXIF Orientation rotation — applyExifRotation handles all 8 cases
- ✓ Photo with frame on small input — coerceAtLeast(1) on deframe dims
- ✓ Watermark text empty — `.active` requires non-blank, never draws empty
