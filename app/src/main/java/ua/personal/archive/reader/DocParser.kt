package ua.personal.archive.reader

import java.nio.charset.Charset

/**
 * Витягує текст зі старого формату Word 97–2003 (.doc) через таблицю фрагментів (piece table).
 * Форматування не відтворюється – лише текст, абзаци та таблиці.
 */
object DocParser {

    fun parse(bytes: ByteArray): List<Item> {
        val cfb = Cfb(bytes)
        val wd = cfb.stream("WordDocument") ?: throw DocException("У файлі немає тексту Word.")
        if (wd.size < 0x200) throw DocException("Пошкоджений файл .doc.")
        val ident = Cfb.le16(wd, 0)
        if (ident != 0xA5EC && ident != 0xA5DC) throw DocException("Файл не є документом Word.")
        val nFib = Cfb.le16(wd, 2)
        val flags = Cfb.le16(wd, 0x0A)
        if (flags and 0x0100 != 0) throw DocException("Документ зашифровано паролем.")

        val text = if (nFib < 101) {
            // Word 6/95: текст лежить суцільно між fcMin і fcMac в однобайтовому кодуванні
            val fcMin = Cfb.le32(wd, 0x18)
            val fcMac = Cfb.le32(wd, 0x1C)
            if (fcMin < 0 || fcMac > wd.size || fcMac <= fcMin) throw DocException("Не вдалося прочитати текст.")
            String(wd, fcMin, fcMac - fcMin, Charset.forName("windows-1251"))
        } else {
            readPieces(cfb, wd, flags)
        }
        return toItems(text)
    }

    private fun readPieces(cfb: Cfb, wd: ByteArray, flags: Int): String {
        val table = cfb.stream(if (flags and 0x0200 != 0) "1Table" else "0Table")
            ?: throw DocException("Пошкоджений файл .doc (немає таблиці).")
        // FIB: FibBase(32) → csw + fibRgW → cslw + fibRgLw → cbRgFcLcb + fibRgFcLcb
        var pos = 32
        val csw = Cfb.le16(wd, pos); pos += 2 + csw * 2
        val cslw = Cfb.le16(wd, pos)
        val rgLw = pos + 2
        pos += 2 + cslw * 4
        val ccpText = Cfb.le32(wd, rgLw + 3 * 4)
        pos += 2 // cbRgFcLcb
        val fcClx = Cfb.le32(wd, pos + 33 * 8)
        val lcbClx = Cfb.le32(wd, pos + 33 * 8 + 4)
        if (fcClx < 0 || lcbClx <= 0 || fcClx + lcbClx > table.size) throw DocException("Не вдалося знайти текст у файлі.")

        var p = fcClx
        val end = fcClx + lcbClx
        while (p < end && table[p].toInt() == 0x01) {
            val cb = Cfb.le16(table, p + 1)
            p += 3 + cb
        }
        if (p >= end || table[p].toInt() != 0x02) throw DocException("Не вдалося знайти текст у файлі.")
        val lcb = Cfb.le32(table, p + 1)
        p += 5
        val n = (lcb - 4) / 12
        if (n <= 0 || p + lcb > table.size) throw DocException("Не вдалося знайти текст у файлі.")
        val cp1252 = Charset.forName("windows-1252")
        val sb = StringBuilder()
        val limit = if (ccpText > 0) ccpText else Int.MAX_VALUE
        for (i in 0 until n) {
            val cpStart = Cfb.le32(table, p + i * 4)
            var cpEnd = Cfb.le32(table, p + (i + 1) * 4)
            if (cpStart >= limit) break
            if (cpEnd > limit) cpEnd = limit
            val count = cpEnd - cpStart
            if (count <= 0) continue
            val pcd = p + (n + 1) * 4 + i * 8
            val fcRaw = Cfb.le32(table, pcd + 2)
            val compressed = fcRaw and 0x40000000 != 0
            if (compressed) {
                val off = (fcRaw and 0x3FFFFFFF) / 2
                val len = minOf(count, wd.size - off)
                if (off in 0 until wd.size && len > 0) sb.append(String(wd, off, len, cp1252))
            } else {
                val off = fcRaw
                val len = minOf(count * 2, wd.size - off)
                if (off in 0 until wd.size && len > 0) sb.append(String(wd, off, len and 1.inv(), Charsets.UTF_16LE))
            }
        }
        return sb.toString()
    }

    /** Перетворює спеціальні символи Word на звичайний текст і ділить на абзаци. */
    private fun toItems(raw: String): List<Item> {
        val sb = StringBuilder(raw.length)
        var fieldDepth = 0
        // для кожного рівня поля: true – вже показуємо результат (після роздільника 0x14)
        val showing = ArrayList<Boolean>()
        for (ch in raw) {
            when (ch.code) {
                0x13 -> { fieldDepth++; showing += false; continue }
                0x14 -> { if (showing.isNotEmpty()) showing[showing.size - 1] = true; continue }
                0x15 -> { if (fieldDepth > 0) { fieldDepth--; showing.removeAt(showing.size - 1) }; continue }
            }
            if (showing.any { !it }) continue
            when (ch.code) {
                0x0D -> sb.append('\r')
                0x07 -> sb.append('\u0007')
                0x0B -> sb.append('\n')
                0x0C -> sb.append('\r')
                0x09 -> sb.append('\t')
                0x1E -> sb.append('-')
                0x1F, 0x01, 0x08, 0x05, 0x02, 0x03, 0x04 -> {}
                else -> if (ch.code >= 0x20) sb.append(ch)
            }
        }
        // кінець рядка таблиці – два маркери 0x07 поспіль
        val text = sb.toString()
            .replace("\u0007\u0007", "\r")
            .replace("\u0007\r", "\r")
            .replace("\u0007", "   │   ")
        val items = ArrayList<Item>()
        for (para in text.split('\r')) {
            val t = para.trimEnd()
            items += TextItem(t, TextItem.ALIGN_START, 0, tableRow = t.contains("   │   "))
        }
        while (items.isNotEmpty() && (items.last() as TextItem).text.isBlank()) items.removeAt(items.size - 1)
        if (items.isEmpty()) throw DocException("Документ порожній або текст не вдалося прочитати.")
        return items
    }
}
