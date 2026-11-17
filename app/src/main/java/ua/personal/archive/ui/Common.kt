package ua.personal.archive.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val dateFormat get() = SimpleDateFormat("dd.MM.yyyy", Locale("uk", "UA"))

fun formatDate(ms: Long): String = dateFormat.format(Date(ms))

/** Скільки днів лишилося до закінчення строку дії (від'ємне — прострочено). */
fun daysLeft(expiresAt: Long): Long {
    val now = System.currentTimeMillis()
    return TimeUnit.MILLISECONDS.toDays(expiresAt - now) + if (expiresAt >= now) 0 else -1
}

fun expiryLabel(expiresAt: Long): String {
    val d = daysLeft(expiresAt)
    return when {
        d < 0 -> "Прострочено ${formatDate(expiresAt)}"
        d == 0L -> "Спливає сьогодні"
        d <= 60 -> "Спливає через $d дн. (${formatDate(expiresAt)})"
        else -> "Дійсний до ${formatDate(expiresAt)}"
    }
}
