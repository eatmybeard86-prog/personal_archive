package ua.personal.archive.reader

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** Дуже простий конвертер RTF → текст (часто трапляються .doc, які насправді є RTF). */
object RtfParser {
    private val skipDestinations = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "headerl", "headerr",
        "headerf", "footerl", "footerr", "footerf", "footnote", "object", "themedata", "colorschememapping",
        "latentstyles", "datastore", "xmlnstbl", "listtable", "listoverridetable", "rsidtbl", "generator",
        "filetbl", "revtbl", "pgdsctbl", "fldinst", "bkmkstart", "bkmkend", "shppict", "nonshppict",
    )

    fun parse(bytes: ByteArray): List<Item> {
        val src = String(bytes, Charsets.ISO_8859_1)
        var charset: Charset = Charset.forName("windows-1252")
        Regex("\\\\ansicpg(\\d+)").find(src)?.groupValues?.get(1)?.let { cp ->
            try { charset = Charset.forName("windows-$cp") } catch (_: Exception) {}
        }
        if (charset.name().endsWith("1252") && src.contains("\\fcharset204")) charset = Charset.forName("windows-1251")

        val out = StringBuilder()
        val pending = ByteArrayOutputStream() // байти \'hh, що декодуються разом
        fun flushBytes() {
            if (pending.size() > 0) {
                out.append(String(pending.toByteArray(), charset)); pending.reset()
            }
        }

        // стан групи: (пропускати, ucN)
        val skipStack = ArrayList<Boolean>()
        val ucStack = ArrayList<Int>()
        var skip = false
        var uc = 1
        var toSkip = 0 // символів для пропуску після \\uN
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            when (c) {
                '{' -> { skipStack += skip; ucStack += uc; i++ }
                '}' -> {
                    flushBytes()
                    if (skipStack.isNotEmpty()) { skip = skipStack.removeAt(skipStack.size - 1); uc = ucStack.removeAt(ucStack.size - 1) }
                    i++
                }
                '\\' -> {
                    i++
                    if (i >= n) break
                    val d = src[i]
                    if (d == '\'') {
                        val hex = src.substring(i + 1, minOf(i + 3, n))
                        i += 3
                        if (toSkip > 0) { toSkip--; continue }
                        if (!skip) hex.toIntOrNull(16)?.let { pending.write(it) }
                        continue
                    }
                    if (!d.isLetter()) {
                        i++
                        if (d == '*') { skip = true; continue }
                        if (toSkip > 0) { toSkip--; continue }
                        flushBytes()
                        if (!skip) when (d) {
                            '\\', '{', '}' -> out.append(d)
                            '~' -> out.append(' ')
                            '-' -> {}
                            '_' -> out.append('-')
                            '\n', '\r' -> out.append('\n')
                        }
                        continue
                    }
                    val startWord = i
                    while (i < n && src[i].isLetter()) i++
                    val word = src.substring(startWord, i)
                    val startNum = i
                    if (i < n && (src[i] == '-' || src[i].isDigit())) {
                        i++
                        while (i < n && src[i].isDigit()) i++
                    }
                    val num = src.substring(startNum, i).toIntOrNull()
                    if (i < n && src[i] == ' ') i++
                    flushBytes()
                    if (word in skipDestinations) { skip = true; continue }
                    if (skip) continue
                    when (word) {
                        "par", "line", "sect", "page" -> out.append('\n')
                        "row" -> out.append('\n')
                        "cell" -> out.append("   │   ")
                        "tab" -> out.append('\t')
                        "emdash" -> out.append('—')
                        "endash" -> out.append('–')
                        "bullet" -> out.append('•')
                        "lquote" -> out.append('‘')
                        "rquote" -> out.append('’')
                        "ldblquote" -> out.append('“')
                        "rdblquote" -> out.append('”')
                        "uc" -> uc = num ?: 1
                        "u" -> {
                            var code = num ?: 0
                            if (code < 0) code += 65536
                            out.append(code.toChar())
                            toSkip = uc
                        }
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    i++
                    if (toSkip > 0) { toSkip--; continue }
                    if (!skip) { flushBytes(); out.append(c) }
                }
            }
        }
        flushBytes()
        val items = out.toString().split('\n').map { line ->
            val t = line.trimEnd()
            TextItem(t, TextItem.ALIGN_START, 0, tableRow = t.contains("   │   "))
        }.toMutableList<Item>()
        while (items.isNotEmpty() && (items.last() as TextItem).text.isBlank()) items.removeAt(items.size - 1)
        if (items.isEmpty()) throw DocException("Документ порожній.")
        return items
    }
}
