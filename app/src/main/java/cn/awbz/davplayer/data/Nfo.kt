package cn.awbz.davplayer.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

data class MediaMetadata(val title: String = "", val plot: String = "", val year: String = "",
    val premiered: String = "", val runtimeMinutes: Int? = null, val rating: Double? = null,
    val genres: List<String> = emptyList(), val kind: String = "")

/** Read local Kodi-compatible metadata only. External artwork/trailer URLs are not fetched. */
object Nfo {
    const val LIMIT = 512 * 1024
    fun select(entries: List<DavEntry>, workName: String, direct: List<Episode>): DavEntry? {
        val files = entries.filter { !it.directory && it.name.endsWith(".nfo", true) }
        for (name in listOf("tvshow.nfo", "movie.nfo", "$workName.nfo") +
            if (direct.size == 1) listOf(direct[0].name.substringBeforeLast('.') + ".nfo") else emptyList()) {
            files.firstOrNull { it.name.equals(name, true) }?.let { return it }
        }
        return files.singleOrNull()
    }
    fun parse(xml: String): MediaMetadata {
        if (xml.length > LIMIT || Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml))
            throw DavFailure("NFO 过大或包含不允许的实体声明")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false) }
        parser.setInput(StringReader(xml.removePrefix("\uFEFF")))
        val fields = mutableMapOf<String, String>()
        val genres = mutableListOf<String>()
        val texts = mutableMapOf<Int, StringBuilder>()
        var root = ""; var closed = false
        var ratingMax = 10.0; var ratingDefault = false; var ratingValue: Double? = null
        val ratings = mutableListOf<Pair<Boolean, Double>>()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (parser.depth > 32 || event == XmlPullParser.DOCDECL) throw DavFailure("NFO 层级或声明不受支持")
                when (event) {
                    XmlPullParser.START_TAG -> {
                        texts[parser.depth] = StringBuilder()
                        if (parser.depth == 1) {
                            if (root.isNotEmpty() || parser.name !in setOf("movie", "tvshow", "episodedetails")) throw DavFailure("NFO 不是影片或剧集 XML")
                            root = parser.name
                        }
                        if (parser.depth == 3 && parser.name == "rating") {
                            ratingMax = parser.getAttributeValue(null, "max")?.toDoubleOrNull() ?: 10.0
                            ratingDefault = parser.getAttributeValue(null, "default").equals("true", true)
                            ratingValue = null
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> texts[parser.depth]?.append(parser.text)
                    XmlPullParser.ENTITY_REF -> {
                        if (parser.name !in setOf("amp", "lt", "gt", "quot", "apos") && !parser.name.startsWith("#")) throw DavFailure("NFO 包含不允许的实体")
                        texts[parser.depth]?.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        val value = texts.remove(parser.depth)?.toString()?.trim().orEmpty()
                        if (parser.depth == 2) {
                            if (parser.name == "genre" && value.isNotEmpty()) genres.add(value)
                            else if (parser.name in setOf("title", "plot", "year", "premiered", "aired", "runtime", "rating")) fields[parser.name] = value
                        }
                        if (parser.depth == 4 && parser.name == "value") ratingValue = value.toDoubleOrNull()
                        if (parser.depth == 3 && parser.name == "rating") {
                            val score = ratingValue
                            if (score != null && score.isFinite() && ratingMax.isFinite() && ratingMax > 0 && score in 0.0..ratingMax)
                                ratings.add(ratingDefault to score * 10.0 / ratingMax)
                        }
                        if (parser.depth == 1) closed = true
                    }
                }
                event = parser.nextToken()
            }
        } catch (e: DavFailure) { throw e } catch (_: Exception) { throw DavFailure("NFO XML 无法解析") }
        if (root.isEmpty() || !closed) throw DavFailure("NFO XML 不完整")
        val rating = ratings.firstOrNull { it.first }?.second ?: ratings.firstOrNull()?.second
            ?: fields["rating"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..10.0 }
        val date = (fields["premiered"] ?: fields["aired"]).orEmpty().takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }.orEmpty()
        val year = fields["year"].orEmpty().takeIf { Regex("\\d{4}").matches(it) } ?: date.take(4)
        return MediaMetadata(fields["title"].orEmpty(), fields["plot"].orEmpty(), year, date,
            fields["runtime"]?.toIntOrNull()?.takeIf { it in 1..100000 }, rating, genres.distinct(), root)
    }
}
