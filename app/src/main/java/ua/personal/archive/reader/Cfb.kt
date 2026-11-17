package ua.personal.archive.reader

/**
 * Мінімальний читач OLE2 / Compound File Binary (контейнер старих .doc).
 */
class Cfb(private val data: ByteArray) {
    private val sectorShift: Int
    private val sectorSize: Int
    private val miniShift: Int
    private val miniCutoff: Long
    private val fat: IntArray
    private val miniFat: IntArray
    private val entries = ArrayList<Entry>()
    private val miniStream: ByteArray

    class Entry(val name: String, val type: Int, val start: Int, val size: Long)

    init {
        if (data.size < 512 || u32(0) != 0xE011CFD0.toInt() || u32(4) != 0xE11AB1A1.toInt()) {
            throw DocException("Файл не є документом Word 97–2003.")
        }
        sectorShift = u16(0x1E)
        if (sectorShift !in 7..16) throw DocException("Пошкоджений файл .doc.")
        sectorSize = 1 shl sectorShift
        miniShift = u16(0x20)
        val numFat = u32(0x2C)
        val firstDir = u32(0x30)
        miniCutoff = u32(0x38).toLong() and 0xFFFFFFFFL
        val firstMiniFat = u32(0x3C)
        var difatSector = u32(0x44)

        // список секторів FAT
        val fatSectors = ArrayList<Int>()
        for (i in 0 until 109) {
            val s = u32(0x4C + i * 4)
            if (s >= 0) fatSectors += s
        }
        var guard = 0
        while (difatSector >= 0 && guard++ < 100_000) {
            val off = sectorOffset(difatSector)
            val per = sectorSize / 4 - 1
            for (i in 0 until per) {
                val s = u32(off + i * 4)
                if (s >= 0) fatSectors += s
            }
            difatSector = u32(off + per * 4)
        }
        if (fatSectors.size > numFat && numFat > 0) fatSectors.subList(numFat, fatSectors.size).clear()
        val per = sectorSize / 4
        fat = IntArray(fatSectors.size * per)
        fatSectors.forEachIndexed { idx, s ->
            val off = sectorOffset(s)
            for (i in 0 until per) {
                fat[idx * per + i] = if (off + i * 4 + 4 <= data.size) u32(off + i * 4) else -2
            }
        }

        // каталог
        val dir = readChain(firstDir, fat, sectorSize) { sectorOffset(it) }
        var pos = 0
        while (pos + 128 <= dir.size) {
            val nameLen = (dir[pos + 64].toInt() and 0xFF) or ((dir[pos + 65].toInt() and 0xFF) shl 8)
            val chars = (nameLen / 2 - 1).coerceIn(0, 31)
            val sb = StringBuilder()
            for (i in 0 until chars) {
                sb.append(((dir[pos + i * 2].toInt() and 0xFF) or ((dir[pos + i * 2 + 1].toInt() and 0xFF) shl 8)).toChar())
            }
            val type = dir[pos + 66].toInt()
            val start = le32(dir, pos + 116)
            val size = le32(dir, pos + 120).toLong() and 0xFFFFFFFFL
            entries += Entry(sb.toString(), type, start, size)
            pos += 128
        }

        miniFat = if (firstMiniFat >= 0) {
            val b = readChain(firstMiniFat, fat, sectorSize) { sectorOffset(it) }
            IntArray(b.size / 4) { le32(b, it * 4) }
        } else IntArray(0)
        val root = entries.firstOrNull { it.type == 5 }
        miniStream = if (root != null && root.start >= 0) readChain(root.start, fat, sectorSize) { sectorOffset(it) } else ByteArray(0)
    }

    fun stream(name: String): ByteArray? {
        val e = entries.firstOrNull { it.type == 2 && it.name == name } ?: return null
        val bytes = if (e.size < miniCutoff) {
            val miniSize = 1 shl miniShift
            // малі потоки зберігаються всередині miniStream
            readChain(e.start, miniFat, miniSize) { it shl miniShift }
        } else {
            readChain(e.start, fat, sectorSize) { sectorOffset(it) }
        }
        return if (bytes.size > e.size) bytes.copyOf(e.size.toInt()) else bytes
    }

    private fun sectorOffset(sid: Int): Int = (sid + 1) shl sectorShift

    private fun readChain(start: Int, table: IntArray, size: Int, offset: (Int) -> Int): ByteArray {
        val isMini = table === miniFat && table.isNotEmpty()
        val source = if (isMini) miniStream else data
        val out = java.io.ByteArrayOutputStream()
        var s = start
        var guard = 0
        while (s >= 0 && s < table.size && guard++ < table.size + 1) {
            val off = offset(s)
            if (off < 0 || off >= source.size) break
            out.write(source, off, minOf(size, source.size - off))
            s = table[s]
        }
        return out.toByteArray()
    }

    private fun u16(off: Int): Int = (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8)
    private fun u32(off: Int): Int = le32(data, off)

    companion object {
        fun le32(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

        fun le16(b: ByteArray, off: Int): Int = (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }
}
