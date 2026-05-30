package com.seanyuan.filmframe.ui

/**
 * Chinese display labels + swatch colors for the 5 frame templates, used by the
 * Create carousel and the Edit border picker. The render layer (FrameRenderer)
 * keeps its own Latin displayName for the printed caption; these are the UI
 * chrome names from the v4.2 design (经典留白 / 高反差 / …).
 */
object TemplateLabels {
    data class Meta(val zh: String, val en: String, val swatch: Long)

    val byId = mapOf(
        "classic" to Meta("经典留白", "Classic Border", 0xFFFFFFFF),
        "bold" to Meta("高反差", "High Contrast", 0xFF111111),
        "solid" to Meta("纯色底", "Solid Background", 0xFF2A2A2A),
        "minimal" to Meta("极简无界", "Minimalist Layout", 0xFFFFFFFF),
        "polaroid" to Meta("宝丽来复古", "Vintage Film", 0xFFF9F9F6),
    )

    fun zh(id: String): String = byId[id]?.zh ?: id
    fun en(id: String): String = byId[id]?.en ?: ""
    fun swatch(id: String): Long = byId[id]?.swatch ?: 0xFFFFFFFF
}
