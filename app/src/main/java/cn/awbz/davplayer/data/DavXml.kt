package cn.awbz.davplayer.data

import okhttp3.HttpUrl
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** Namespace-aware bounded DAV multistatus; failed propstat values never become entries. */
object DavXml {
    fun parse(xml: String, parent: HttpUrl): List<DavEntry> {
        if (xml.length > 2 * 1024 * 1024) throw DavFailure("目录响应过大，请缩小分类目录")
        // Fail closed even when a vendor parser doesn't implement the external-entity switch.
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml))
            throw DavFailure("服务器 XML 包含不允许的外部实体声明")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false) }
        parser.setInput(StringReader(xml))
        val entries = LinkedHashMap<String, DavEntry>()
        var href: String? = null
        var inResponse = false
        var inPropstat = false
        var success = false
        var collection = false
        var acceptedCollection: Boolean? = null
        var responseCount = 0
        var sawRoot = false
        var closedRoot = false
        var event = parser.eventType
        try {
            while (event != XmlPullParser.END_DOCUMENT) {
                if (parser.depth > 32) throw DavFailure("服务器 XML 层级过深")
                if (event == XmlPullParser.DOCDECL || event == XmlPullParser.ENTITY_REF)
                    throw DavFailure("服务器 XML 包含不允许的实体")
                val dav = parser.namespace == "DAV:"
                if (event == XmlPullParser.END_TAG && parser.depth == 1 && dav && parser.name == "multistatus") closedRoot = true
                if (event == XmlPullParser.START_TAG && parser.depth == 1) {
                    if (!dav || parser.name != "multistatus") throw DavFailure("服务器未返回 WebDAV 目录响应")
                    sawRoot = true
                }
                if (dav && event == XmlPullParser.START_TAG) when (parser.name) {
                    "response" -> { inResponse = true; href = null; acceptedCollection = null
                        responseCount++; if (responseCount > 5000) throw DavFailure("目录项目过多，请拆分分类") }
                    "href" -> if (inResponse && !inPropstat) href = parser.nextText()
                    "propstat" -> { inPropstat = true; success = false; collection = false }
                    "collection" -> if (inPropstat) collection = true
                    "status" -> if (inPropstat) success = Regex("^HTTP/\\S+\\s+2\\d\\d(?:\\s|$)").containsMatchIn(parser.nextText().trim())
                }
                if (dav && event == XmlPullParser.END_TAG) when (parser.name) {
                    "propstat" -> { if (success) acceptedCollection = (acceptedCollection == true || collection); inPropstat = false }
                    "response" -> {
                        val raw = href
                        if (raw != null && acceptedCollection != null) {
                            var url = parent.resolve(raw.trim())
                            if (url != null && DavUrls.directChild(parent, url)) {
                                if (acceptedCollection == true) url = DavUrls.directory(url)
                                val name = url.pathSegments().lastOrNull { it.isNotEmpty() } ?: ""
                                if (name.isNotEmpty()) entries[url.encodedPath()] = DavEntry(url, name, acceptedCollection == true)
                            }
                        }
                        inResponse = false
                    }
                }
                event = parser.nextToken()
            }
        } catch (e: DavFailure) { throw e } catch (_: Exception) { throw DavFailure("服务器目录 XML 无法解析") }
        if (!sawRoot || !closedRoot || inResponse || inPropstat) throw DavFailure("服务器未返回 WebDAV 目录响应")
        return entries.values.sortedWith(Comparator { a, b -> NaturalOrder.compare(a.name, b.name) })
    }
}
