package ua.personal.archive.passwords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanParserTest {

    @Test fun wifiQr() {
        val f = ScanParser.parseQr("WIFI:T:WPA;S:Home\\;Net;P:s3cr3t\\:pw;H:false;;")
        assertEquals("Home;Net", f.login)
        assertEquals("s3cr3t:pw", f.password)
        assertEquals("Wi-Fi Home;Net", f.title)
        assertEquals("WIFI", f.category)
    }

    @Test fun otpQr() {
        val f = ScanParser.parseQr("otpauth://totp/GitHub:mark%40mail.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub")
        assertEquals("GitHub", f.title)
        assertEquals("mark@mail.com", f.login)
        assertTrue(f.note.contains("JBSWY3DPEHPK3PXP"))
    }

    @Test fun urlWithCredentials() {
        val f = ScanParser.parseQr("ftp://admin:p%40ss@192.168.1.10:21/")
        assertEquals("admin", f.login)
        assertEquals("p@ss", f.password)
        assertEquals("ftp://192.168.1.10:21/", f.site)
    }

    @Test fun labeledLinesUkrainian() {
        val f = ScanParser.guess(listOf("Сайт: https://www.example.com/login", "Логін: ivan.petrenko", "Пароль: Qw3rty!9"))
        assertEquals("ivan.petrenko", f.login)
        assertEquals("Qw3rty!9", f.password)
        assertEquals("https://www.example.com/login", f.site)
        assertEquals("example.com", f.title)
    }

    @Test fun labelOnSeparateLineAndEmail() {
        val f = ScanParser.guess(listOf("Password", "Xy7-long-pass", "contact: me@gmail.com"))
        assertEquals("Xy7-long-pass", f.password)
        assertEquals("me@gmail.com", f.login)
    }

    @Test fun stripLabel() {
        assertEquals("abc123", ScanParser.stripLabel("Пароль: abc123"))
        assertEquals("plain text", ScanParser.stripLabel("plain text"))
    }

    @Test fun categories() {
        assertEquals("SOCIAL", ScanParser.guessCategory("Instagram"))
        assertEquals("EMAIL", ScanParser.guessCategory("", "https://mail.google.com", "x@gmail.com"))
        assertEquals("GAMES", ScanParser.guessCategory("Steam"))
        assertNull(ScanParser.guessCategory("Моя бабуся"))
    }

    @Test fun generator() {
        val p = ScanParser.generate(20)
        assertEquals(20, p.length)
        assertTrue(p.any { it.isUpperCase() } && p.any { it.isLowerCase() } && p.any { it.isDigit() })
    }
}
