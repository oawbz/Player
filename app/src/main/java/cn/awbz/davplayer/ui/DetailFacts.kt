package cn.awbz.davplayer.ui

import cn.awbz.davplayer.data.MediaMetadata

/** Only populated display fields; IDs and remote links are not part of the detail UI. */
internal object DetailFacts {
    data class Fact(val label: String, val value: String, val wide: Boolean = false)
    fun of(meta: MediaMetadata): List<Fact> {
        fun clean(value: String) = value.trim().takeUnless { it.lowercase(java.util.Locale.ROOT) in setOf("", "-", "--", "n/a", "null", "未知", "暂无") }.orEmpty()
        fun names(values: List<String>) = values.map(::clean).filter { it.isNotEmpty() }.distinct().joinToString(" / ")
        val facts = mutableListOf<Fact>()
        fun add(label: String, value: String, wide: Boolean = false) {
            clean(value).takeIf { it.isNotEmpty() }?.let { facts.add(Fact(label, it, wide)) }
        }
        if (meta.originalTitle.trim() != meta.title.trim()) add("原名", meta.originalTitle)
        add("首映日期", meta.premiered)
        add("导演", names(meta.directors))
        add("编剧", names(meta.writers))
        add("国家 / 地区", names(meta.countries))
        add("制作公司", names(meta.studios))
        add("分级", meta.certification)
        add("一句话简介", meta.tagline, true)
        add("演员", meta.cast.mapNotNull {
            val name = clean(it.name)
            val role = clean(it.role)
            if (name.isEmpty()) null else if (role.isEmpty()) name else "$name（饰 $role）"
        }.distinct().joinToString("  ·  "), true)
        return facts
    }
}
