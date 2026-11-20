package ua.personal.archive.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Argon2Test {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** Еталонний вектор Argon2id v1.3 з реалізації-зразка (password / somesalt, t=2, m=64 МБ, p=1). */
    @Test fun referenceVector() {
        val out = PasswordStore.argon2id("password".toCharArray(), "somesalt".toByteArray(), memoryKb = 65_536, iterations = 2)
        assertEquals("09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7", hex(out))
    }

    @Test fun saltMatters() {
        val a = PasswordStore.argon2id("1234".toCharArray(), ByteArray(16) { 1 }, memoryKb = 1024, iterations = 1)
        val b = PasswordStore.argon2id("1234".toCharArray(), ByteArray(16) { 2 }, memoryKb = 1024, iterations = 1)
        assertFalse(a.contentEquals(b))
    }
}
