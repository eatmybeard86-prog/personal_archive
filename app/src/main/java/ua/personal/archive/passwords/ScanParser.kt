package ua.personal.archive.passwords

import java.net.URLDecoder
import java.security.SecureRandom

/** Що вдалося дістати зі скану: порожні поля — не знайдено. */
data class ScanFields(
    val title: String = "",
    val site: String = "",
    val login: String = "",
    val password: String = "",
    val note: String = "",
    /** Назва з PasswordCategory, якщо зрозуміло з коду (наприклад, Wi-Fi). */
    val category: String? = null,
) {
    val isEmpty get() = title.isBlank() && site.isBlank() && login.isBlank() && password.isBlank() && note.isBlank()
}

/**
 * Розбір тексту з камери (QR-код або розпізнані рядки) на поля запису пароля.
 * Без залежностей від Android — перевіряється звичайними юніт-тестами.
 */
object ScanParser {

    /** QR-коди: Wi-Fi, 2FA (otpauth), посилання з логіном/паролем, «логін: … пароль: …» або просто текст. */
    fun parseQr(raw: String): ScanFields {
        val text = raw.trim()
        return when {
            text.startsWith("WIFI:", ignoreCase = true) -> parseWifi(text)
            text.startsWith("otpauth://", ignoreCase = true) -> parseOtp(text)
            URL_WITH_USER.matches(text) -> parseUrlWithUser(text)
            else -> guess(text.lines()).let { if (it.isEmpty) ScanFields(password = text) else it }
        }
    }

    /**
     * Розпізнані рядки з паперу чи екрана: шукаємо підписи «Логін:», «Пароль:», «Сайт:» (укр./рос./англ.),
     * а також e-mail і посилання. Значення може стояти після двокрапки або в наступному рядку.
     */
    fun guess(lines: List<String>): ScanFields {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        var login = ""; var password = ""; var site = ""
        for ((i, line) in clean.withIndex()) {
            // «Пароль: x» або підпис окремим рядком («Пароль»), а значення — у наступному.
            val m = LABELED.find(line) ?: LABEL_ONLY.find(line)
            if (m != null) {
                val label = m.groupValues[1].lowercase()
                val value = line.substring(m.range.last + 1).trim().ifEmpty { clean.getOrNull(i + 1)?.takeIf { LABELED.find(it) == null && LABEL_ONLY.find(it) == null }.orEmpty() }
                if (value.isEmpty()) continue
                when {
                    PASSWORD_WORDS.any { label.startsWith(it) } -> if (password.isEmpty()) password = value
                    SITE_WORDS.any { label.startsWith(it) } -> if (site.isEmpty()) site = value
                    else -> if (login.isEmpty()) login = value
                }
            }
        }
        if (login.isEmpty()) clean.firstNotNullOfOrNull { EMAIL.find(it)?.value }?.let { login = it }
        if (site.isEmpty()) clean.firstNotNullOfOrNull { URL.find(it)?.value }?.let { site = it }
        return ScanFields(title = titleFor(site), site = site, login = login, password = password)
    }

    /** «Пароль: abc123» → «abc123»; рядок без підпису повертається як є. */
    fun stripLabel(line: String): String {
        val m = LABELED.find(line) ?: return line.trim()
        return line.substring(m.range.last + 1).trim().ifEmpty { line.trim() }
    }

    /** Коротка назва із сайту: «https://www.google.com/x» → «google.com». */
    fun titleFor(site: String): String {
        if (site.isBlank()) return ""
        return site.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringAfter('@')
            .removePrefix("www.").substringBefore(':')
    }

    private fun parseWifi(text: String): ScanFields {
        // WIFI:T:WPA;S:Назва;P:пароль;H:false;;  (символи \ ; , : можуть бути екрановані «\»)
        val fields = HashMap<String, String>()
        val body = text.substring(5)
        var key = StringBuilder(); var value = StringBuilder(); var inValue = false; var escape = false
        for (c in body) {
            when {
                escape -> { (if (inValue) value else key).append(c); escape = false }
                c == '\\' -> escape = true
                !inValue && c == ':' -> inValue = true
                c == ';' -> {
                    if (key.isNotEmpty()) fields[key.toString().uppercase()] = value.toString()
                    key = StringBuilder(); value = StringBuilder(); inValue = false
                }
                else -> (if (inValue) value else key).append(c)
            }
        }
        if (key.isNotEmpty()) fields[key.toString().uppercase()] = value.toString()
        val ssid = fields["S"].orEmpty()
        return ScanFields(
            title = if (ssid.isNotEmpty()) "Wi-Fi $ssid" else "Wi-Fi",
            category = "WIFI",
            login = ssid,
            password = fields["P"].orEmpty(),
            note = fields["T"]?.takeIf { it.isNotBlank() }?.let { "Мережа Wi-Fi, захист: $it" } ?: "Мережа Wi-Fi",
        )
    }

    private fun parseOtp(text: String): ScanFields {
        // otpauth://totp/Issuer:account@mail.com?secret=ABC&issuer=Issuer
        val path = decode(text.substringAfter("://").substringAfter('/').substringBefore('?'))
        val params = text.substringAfter('?', "").split('&').mapNotNull {
            val k = it.substringBefore('=', ""); if (k.isEmpty()) null else k.lowercase() to decode(it.substringAfter('='))
        }.toMap()
        val issuer = params["issuer"] ?: path.substringBefore(':', "")
        val account = path.substringAfter(':')
        val secret = params["secret"].orEmpty()
        return ScanFields(
            title = issuer.ifBlank { "Двофакторний код" },
            login = account,
            note = if (secret.isNotEmpty()) "Ключ 2FA (TOTP): $secret" else "",
        )
    }

    private fun parseUrlWithUser(text: String): ScanFields {
        val m = URL_WITH_USER.matchEntire(text)!!
        val (scheme, user, pass, rest) = m.destructured
        val site = scheme + rest
        return ScanFields(title = titleFor(site), site = site, login = decode(user), password = decode(pass))
    }

    /** Категорія за назвою чи сайтом: «instagram.com» → SOCIAL. null — не зрозуміло. */
    fun guessCategory(vararg texts: String): String? {
        val t = texts.joinToString(" ").lowercase()
        if (t.isBlank()) return null
        return CATEGORY_WORDS.entries.firstOrNull { (_, words) -> words.any { it in t } }?.key
    }

    private val CATEGORY_WORDS = linkedMapOf(
        "WIFI" to listOf("wi-fi", "wifi", "вайфай", "роутер", "router"),
        "EMAIL" to listOf("gmail", "outlook", "hotmail", "yahoo", "ukr.net", "i.ua", "meta.ua", "proton", "mail.", "пошта", "почта"),
        "SOCIAL" to listOf("facebook", "instagram", "tiktok", "twitter", "x.com", "telegram", "viber", "whatsapp", "linkedin", "youtube", "snapchat", "threads", "reddit", "pinterest", "vk.com", "соцмереж"),
        "SHOPS" to listOf("aliexpress", "amazon", "rozetka", "prom.ua", "olx", "ebay", "temu", "shein", "epicentr", "allo", "comfy", "foxtrot", "нова пошта", "nova poshta", "магазин", "shop"),
        "GAMES" to listOf("steam", "epic", "psn", "playstation", "xbox", "nintendo", "battle.net", "blizzard", "riot", "origin", "ubisoft", "gog", "roblox", "minecraft", "ігр", "game"),
        "SERVICES" to listOf("vpn", "icloud", "dropbox", "drive", "onedrive", "mega", "netflix", "spotify", "megogo", "sweet.tv", "apple", "google", "microsoft", "adobe", "chatgpt", "openai", "claude", "zoom", "cloud", "хмар", "підписк"),
        "WORK" to listOf("corp", "jira", "slack", "confluence", "gitlab", "github", "1c", "bitrix", "робот", "работ", "work", "office"),
    )

    private fun decode(s: String) = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    /** Надійний випадковий пароль. */
    fun generate(length: Int = 20, symbols: Boolean = true): String {
        val rnd = SecureRandom()
        val sets = buildList {
            add("ABCDEFGHJKLMNPQRSTUVWXYZ"); add("abcdefghijkmnopqrstuvwxyz"); add("23456789")
            if (symbols) add("!@#\$%^&*-_=+?")
        }
        val all = sets.joinToString("")
        val chars = MutableList(length) { all[rnd.nextInt(all.length)] }
        // По одному символу з кожної групи — щоб пароль проходив вимоги сайтів.
        sets.forEachIndexed { i, set -> if (i < length) chars[i] = set[rnd.nextInt(set.length)] }
        chars.shuffle(rnd)
        return chars.joinToString("")
    }

    private val LOGIN_WORDS = listOf("логін", "login", "user", "username", "користувач", "пользователь", "e-mail", "email", "пошта", "почта", "акаунт", "account", "id")
    private val PASSWORD_WORDS = listOf("пароль", "password", "pass", "pwd", "pin", "код доступу", "passwort")
    private val SITE_WORDS = listOf("сайт", "site", "url", "адреса", "адрес", "website", "web")
    private val LABELED = Regex(
        "^\\s*(" + (LOGIN_WORDS + PASSWORD_WORDS + SITE_WORDS).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")\\s*[:=\\-–—]\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val LABEL_ONLY = Regex(
        "^\\s*(" + (LOGIN_WORDS + PASSWORD_WORDS + SITE_WORDS).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val URL = Regex("(https?://[^\\s]+|www\\.[^\\s]+)", RegexOption.IGNORE_CASE)
    private val URL_WITH_USER = Regex("^([a-z][a-z0-9+.\\-]*://)([^:/@\\s]+):([^@/\\s]+)@(\\S+)$", RegexOption.IGNORE_CASE)
}
