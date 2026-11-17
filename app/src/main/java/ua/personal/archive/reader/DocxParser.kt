package ua.personal.archive.reader

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipFile

/**
 * Простий розбір DOCX (Office Open XML): абзаци, жирний/курсив/підкреслення,
 * розмір шрифту, заголовки, списки, таблиці (рядок таблиці = один абзац), зображення.
 */
object DocxParser {

    fun parse(zip: ZipFile): List<Item> {
        val rels = readRels(zip)
        val styles = readStyles(zip)
        val entry = zip.getEntry("word/document.xml")
            ?: throw DocException("Файл не схожий на документ Word (немає word/document.xml).")
        return zip.getInputStream(entry).use { input ->
            val p = newParser()
            p.setInput(input, null)
            Builder(rels, styles).run(p)
        }.also { items ->
            val hasContent = items.any { it is InlineImageItem || (it is TextItem && it.text.isNotBlank()) }
            if (!hasContent) throw DocException("У документі не знайдено тексту.")
        }
    }

    /**
     * Xml.newPullParser() на Android вмикає обробку просторів імен, і тоді getName()
     * повертає "p" замість "w:p". Розбір нижче спирається на префікси, тому вимикаємо її.
     */
    private fun newParser(): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    }

    private fun attr(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) {
            val n = p.getAttributeName(i)
            if (n == name || n.substringAfter(':') == name.substringAfter(':')) return p.getAttributeValue(i)
        }
        return null
    }

    private fun isOn(p: XmlPullParser): Boolean {
        val v = attr(p, "w:val") ?: return true
        return v != "0" && v != "false" && v != "none" && v != "off"
    }

    private class Styles(val headings: Map<String, Int>, val defaultSize: Int)

    private fun readStyles(zip: ZipFile): Styles {
        val headings = HashMap<String, Int>()
        var defSize = 22
        val entry = zip.getEntry("word/styles.xml") ?: return Styles(headings, defSize)
        try {
            zip.getInputStream(entry).use { input ->
                val p = newParser()
                p.setInput(input, null)
                var styleId: String? = null
                var inDefaults = false
                while (true) {
                    val ev = p.next()
                    if (ev == XmlPullParser.END_DOCUMENT) break
                    if (ev == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "w:style" -> styleId = attr(p, "w:styleId")
                            "w:name" -> {
                                val name = attr(p, "w:val")?.lowercase() ?: ""
                                val level = when {
                                    name == "title" -> 1
                                    name == "subtitle" -> 2
                                    name.startsWith("heading ") -> name.removePrefix("heading ").trim().toIntOrNull() ?: 0
                                    else -> 0
                                }
                                if (level > 0 && styleId != null) headings[styleId!!] = level
                            }
                            "w:outlineLvl" -> {
                                val lvl = attr(p, "w:val")?.toIntOrNull()
                                if (lvl != null && lvl < 9 && styleId != null && !headings.containsKey(styleId!!)) {
                                    headings[styleId!!] = lvl + 1
                                }
                            }
                            "w:rPrDefault" -> inDefaults = true
                            "w:sz" -> if (inDefaults) attr(p, "w:val")?.toIntOrNull()?.let { defSize = it }
                        }
                    } else if (ev == XmlPullParser.END_TAG) {
                        when (p.name) {
                            "w:style" -> styleId = null
                            "w:rPrDefault" -> inDefaults = false
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return Styles(headings, defSize)
    }

    private fun readRels(zip: ZipFile): Map<String, String> {
        val map = HashMap<String, String>()
        val entry = zip.getEntry("word/_rels/document.xml.rels") ?: return map
        try {
            zip.getInputStream(entry).use { input ->
                val p = newParser()
                p.setInput(input, null)
                while (true) {
                    val ev = p.next()
                    if (ev == XmlPullParser.END_DOCUMENT) break
                    if (ev == XmlPullParser.START_TAG && p.name == "Relationship") {
                        if (attr(p, "TargetMode") == "External") continue
                        val id = attr(p, "Id") ?: continue
                        val target = attr(p, "Target") ?: continue
                        map[id] = if (target.startsWith("/")) target.removePrefix("/") else "word/$target"
                    }
                }
            }
        } catch (_: Exception) {
        }
        return map
    }

    private class Builder(val rels: Map<String, String>, val styles: Styles) {
        val out = ArrayList<Item>()

        // абзац
        var para = SpannableStringBuilder()
        var pDepth = 0
        var align = TextItem.ALIGN_START
        var heading = 0
        var listItem = false
        var inPPr = false

        // фрагмент (run)
        var inRun = false
        var inRPr = false
        var runStart = 0
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var size = 0

        // таблиці
        var tblDepth = 0
        val cells = ArrayList<CharSequence>()
        var cell = SpannableStringBuilder()
        val pendingImages = ArrayList<String>()

        fun run(p: XmlPullParser): List<Item> {
            while (true) {
                val ev = p.next()
                if (ev == XmlPullParser.END_DOCUMENT) break
                if (ev == XmlPullParser.START_TAG) start(p) else if (ev == XmlPullParser.END_TAG) end(p.name)
            }
            return out
        }

        fun skip(p: XmlPullParser) {
            var depth = 1
            while (depth > 0) {
                when (p.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.END_DOCUMENT -> return
                }
            }
        }

        fun start(p: XmlPullParser) {
            when (p.name) {
                // альтернативний вміст дублює текст у текстових полях – беремо лише основний варіант
                "mc:Fallback", "w:delText", "w:instrText", "w:footnoteReference", "w:endnoteReference" -> skip(p)
                "w:p" -> {
                    pDepth++
                    if (pDepth == 1) {
                        para = SpannableStringBuilder()
                        align = TextItem.ALIGN_START
                        heading = 0
                        listItem = false
                    } else if (para.isNotEmpty()) {
                        para.append('\n')
                    }
                }
                "w:pPr" -> inPPr = true
                "w:pStyle" -> if (inPPr && pDepth == 1) {
                    attr(p, "w:val")?.let { heading = styles.headings[it] ?: heading }
                }
                "w:outlineLvl" -> if (inPPr && pDepth == 1) {
                    attr(p, "w:val")?.toIntOrNull()?.let { if (it < 9) heading = it + 1 }
                }
                "w:jc" -> if (inPPr && pDepth == 1) {
                    align = when (attr(p, "w:val")) {
                        "center" -> TextItem.ALIGN_CENTER
                        "right", "end" -> TextItem.ALIGN_END
                        "both", "distribute" -> TextItem.ALIGN_JUSTIFY
                        else -> TextItem.ALIGN_START
                    }
                }
                "w:numPr" -> if (inPPr && pDepth == 1) listItem = true
                "w:r" -> {
                    inRun = true
                    runStart = para.length
                    bold = false; italic = false; underline = false; strike = false; size = 0
                }
                "w:rPr" -> if (inRun && !inPPr) inRPr = true
                "w:b" -> if (inRPr) bold = isOn(p)
                "w:i" -> if (inRPr) italic = isOn(p)
                "w:u" -> if (inRPr) underline = isOn(p)
                "w:strike", "w:dstrike" -> if (inRPr) strike = isOn(p)
                "w:sz" -> if (inRPr) size = attr(p, "w:val")?.toIntOrNull() ?: 0
                "w:t" -> if (inRun) para.append(p.nextText())
                "w:tab" -> if (inRun && !inPPr) para.append('\t')
                "w:br", "w:cr" -> if (inRun) para.append('\n')
                "w:noBreakHyphen" -> if (inRun) para.append('-')
                "w:softHyphen" -> {}
                "w:sym" -> {
                    // символ зі шрифтів Symbol/Wingdings – показуємо як маркер
                    if (inRun) para.append('•')
                }
                "a:blip" -> attr(p, "r:embed")?.let { addImage(it) }
                "v:imagedata" -> attr(p, "r:id")?.let { addImage(it) }
                "w:tbl" -> tblDepth++
                "w:tc" -> if (tblDepth == 1) cell = SpannableStringBuilder()
                "w:tr" -> if (tblDepth == 1) cells.clear()
            }
        }

        fun addImage(relId: String) {
            val path = rels[relId] ?: return
            if (tblDepth > 0) {
                pendingImages += path
                return
            }
            // розриваємо абзац навколо зображення
            if (para.isNotBlank()) {
                out += makeItem(para)
                para = SpannableStringBuilder()
            }
            out += InlineImageItem(path)
        }

        fun end(name: String) {
            when (name) {
                "w:pPr" -> inPPr = false
                "w:rPr" -> inRPr = false
                "w:r" -> {
                    inRun = false
                    applyRunStyle()
                }
                "w:p" -> {
                    pDepth--
                    if (pDepth == 0) finishParagraph()
                }
                "w:tc" -> if (tblDepth == 1) cells += trimEnd(cell)
                "w:tr" -> if (tblDepth == 1) {
                    val row = SpannableStringBuilder()
                    cells.forEachIndexed { i, c ->
                        if (i > 0) row.append("   │   ")
                        row.append(c)
                    }
                    if (row.isNotBlank()) out += TextItem(row, TextItem.ALIGN_START, 0, tableRow = true)
                    cells.clear()
                    flushPendingImages()
                }
                "w:tbl" -> {
                    tblDepth--
                    if (tblDepth == 0) flushPendingImages()
                }
            }
        }

        fun flushPendingImages() {
            pendingImages.forEach { out += InlineImageItem(it) }
            pendingImages.clear()
        }

        fun applyRunStyle() {
            val endPos = para.length
            if (endPos <= runStart) return
            val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            val style = (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0)
            if (style != 0) para.setSpan(StyleSpan(style), runStart, endPos, flags)
            if (underline) para.setSpan(UnderlineSpan(), runStart, endPos, flags)
            if (strike) para.setSpan(StrikethroughSpan(), runStart, endPos, flags)
            if (size > 0 && size != styles.defaultSize && heading == 0) {
                val rel = (size.toFloat() / styles.defaultSize).coerceIn(0.6f, 2.5f)
                para.setSpan(RelativeSizeSpan(rel), runStart, endPos, flags)
            }
        }

        fun finishParagraph() {
            if (tblDepth > 0) {
                if (para.isNotBlank()) {
                    if (cell.isNotEmpty()) cell.append(' ')
                    cell.append(para)
                }
                return
            }
            out += makeItem(para)
        }

        fun makeItem(text: SpannableStringBuilder): TextItem {
            val t = trimEnd(text)
            if (listItem && t.isNotEmpty()) t.insert(0, "•  ")
            if (heading > 0 && t.isNotEmpty()) {
                val rel = when (heading) { 1 -> 1.6f; 2 -> 1.4f; 3 -> 1.2f; else -> 1.1f }
                t.setSpan(StyleSpan(Typeface.BOLD), 0, t.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                t.setSpan(RelativeSizeSpan(rel), 0, t.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return TextItem(t, align, heading)
        }

        fun trimEnd(s: SpannableStringBuilder): SpannableStringBuilder {
            var e = s.length
            while (e > 0 && (s[e - 1] == '\n' || s[e - 1] == ' ')) e--
            return if (e == s.length) s else SpannableStringBuilder(s, 0, e)
        }
    }
}
