package com.seanyuan.filmframe.frame

/**
 * Per-template runtime tweaks the user can pull from the params sheet.
 * Stays minimal in v0.1 — three knobs that cover 90% of practical adjustment
 * without forcing a Figma-style editor.
 *
 *   borderWidthMultiplier  scales all four margins (0.7x → 1.5x)
 *   titleSizeMultiplier    scales the caption title text size (0.7x → 1.5x)
 *   showCaption            toggle the EXIF caption text on Classic / Bold /
 *                          Polaroid; ignored for Solid / Minimal which have
 *                          no caption to begin with
 */
data class TemplateAdjustments(
    val borderWidthMultiplier: Float = 1f,
    val titleSizeMultiplier: Float = 1f,
    val showCaption: Boolean = true,
) {
    companion object { val Default = TemplateAdjustments() }
}
