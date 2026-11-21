package ua.personal.archive.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ua.personal.archive.crypto.PasswordCrypto
import java.io.File
import java.util.UUID

/**
 * Категорії розділу «Паролі». Для деяких поля підписані інакше (див. [PasswordForm]):
 * у ліцензії «пароль» — це ключ, у сервера «сайт» — адреса чи IP, у нотатки головне — текст.
 */
enum class PasswordCategory(val title: String, val hint: String, val color: Long) {
    SOCIAL("Соцмережі", "Facebook, Instagram, TikTok", 0xFF3B82F6),
    EMAIL("Електронна пошта", "Gmail, Outlook, Yahoo", 0xFFF87171),
    WORK("Робота", "Корпоративні акаунти", 0xFF6366F1),
    SHOPS("Інтернет-магазини", "AliExpress, Amazon", 0xFFFBBF24),
    GAMES("Ігри", "Steam, Epic Games, PSN", 0xFFA78BFA),
    SERVICES("Сервіси", "VPN, хмари, акаунти", 0xFF38BDF8),
    SUBSCRIPTIONS("Підписки", "Сервіси, вартість, дати оплати", 0xFFFB923C),
    WIFI("Wi-Fi", "Назви мереж і паролі", 0xFF34D399),
    NOTES("Захищені нотатки", "Секретні записи, PIN-коди", 0xFFE879F9),
    LICENSES("Ліцензії", "Ключі програм і активації", 0xFF2DD4BF),
    SERVERS("Сервери", "IP, SSH, FTP, доступи", 0xFF818CF8),
    CONTACTS("Контакти", "Захищені контактні дані", 0xFF4ADE80),
    OTHER("Інше", "Власні записи", 0xFF94A3B8);

    companion object {
        fun of(name: String?): PasswordCategory = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * Власний розділ користувача («Банки», «Держпослуги»…). [base] — вбудована категорія,
 * чиї поля використовуються (звичайний пароль, Wi-Fi, сервер, підписка тощо).
 */
@Serializable
data class PasswordGroup(
    val id: String = "g-" + UUID.randomUUID().toString(),
    val title: String,
    val icon: String = "key",
    val color: Long = 0xFF60A5FA,
    val base: String = PasswordCategory.OTHER.name,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Вміст файлу паролів. Старі версії зберігали просто список записів — його теж читаємо. */
@Serializable
private data class PasswordStoreFile(
    val entries: List<PasswordEntry> = emptyList(),
    val groups: List<PasswordGroup> = emptyList(),
)

/** Запис у розділі «Паролі». */
@Serializable
data class PasswordEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    /** Назва з [PasswordCategory] або id власного розділу ([PasswordGroup.id]). */
    val category: String = PasswordCategory.OTHER.name,
    /** Сайт; для сервера — адреса / IP; для контакту — поштова адреса. */
    val site: String = "",
    /** Логін / e-mail; для Wi-Fi — назва мережі; для контакту — e-mail. */
    val login: String = "",
    /** Пароль; для ліцензії — ключ; для нотатки — PIN / секретний код. */
    val password: String = "",
    val note: String = "",
    /** Сервер: порт і протокол (SSH, FTP…). */
    val port: String = "",
    val protocol: String = "",
    /** Підписка: вартість (як ввели, напр. «199 ₴»), період MONTH / YEAR і дата наступної оплати. */
    val price: String = "",
    val period: String = "",
    val nextPayment: Long? = null,
    /** Контакт: телефон. */
    val phone: String = "",
    val favorite: Boolean = false,
    /** В архіві: не показується в основному списку. */
    val archived: Boolean = false,
    /** У кошику з цього часу; через [PasswordVault.TRASH_DAYS] днів видаляється назавжди. */
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Зашифроване сховище паролів.
 *
 * Увесь список лежить в одному файлі files/vault/passwords.bin (у «тривожному» сховищі — files/vault2),
 * зашифрованому AES-256-GCM окремим ключем ([PasswordCrypto]), не тим, що документи.
 * Після блокування додатку список вивантажується з пам'яті ([unload]).
 */
class PasswordVault(private val context: Context, cryptoFactory: () -> PasswordCrypto) {
    private val crypto by lazy(cryptoFactory)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val listSerializer = ListSerializer(PasswordEntry.serializer())

    @Volatile private var root = File(context.filesDir, "vault")
    @Volatile private var decoyMode = false
    private val file get() = File(root, "passwords.bin")

    private val _entries = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val entries: StateFlow<List<PasswordEntry>> = _entries.asStateFlow()

    private val _groups = MutableStateFlow<List<PasswordGroup>>(emptyList())
    /** Власні розділи користувача. */
    val groups: StateFlow<List<PasswordGroup>> = _groups.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Справжнє чи «тривожне» сховище — як у [VaultRepository.useVault]. */
    @Synchronized
    fun useVault(decoy: Boolean) {
        if (decoy == decoyMode) return
        decoyMode = decoy
        root = File(context.filesDir, if (decoy) "vault2" else "vault")
        unload()
    }

    private val _unlocked = MutableStateFlow(false)
    /** Чи пройдено перевірку обличчям для «Паролів» у цьому сеансі (скидається при блокуванні). */
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    fun markUnlocked() { _unlocked.value = true }

    /** Прибирає розшифрований список з пам'яті й вимагає нову перевірку (при блокуванні). */
    fun unload() {
        _unlocked.value = false
        _entries.value = emptyList()
        _groups.value = emptyList()
        _loaded.value = false
        _error.value = null
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_loaded.value) return@withLock
            try {
                val f = file
                val store = if (!f.exists()) PasswordStoreFile() else {
                    val text = crypto.open(f.readBytes(), LABEL).decodeToString()
                    if (text.trimStart().startsWith("[")) PasswordStoreFile(entries = json.decodeFromString(listSerializer, text))
                    else json.decodeFromString(PasswordStoreFile.serializer(), text)
                }
                _entries.value = store.entries
                _groups.value = store.groups
                _loaded.value = true
                _error.value = null
                purgeNeeded = _entries.value.any { it.expired() }
            } catch (e: Exception) {
                _error.value = "Не вдалося відкрити паролі: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private var purgeNeeded = false
    private fun PasswordEntry.expired() = deletedAt != null && System.currentTimeMillis() - deletedAt > TRASH_DAYS * 24 * 3600_000L

    /** Записи з кошика, старші за [TRASH_DAYS] днів, видаляються назавжди (викликається після [load]). */
    suspend fun purgeTrash() { if (purgeNeeded) { purgeNeeded = false; mutate { list -> list.filterNot { it.expired() } } } }

    /** У кошик (можна відновити протягом [TRASH_DAYS] днів). */
    suspend fun trash(id: String) = mutate { list -> list.map { if (it.id == id) it.copy(deletedAt = System.currentTimeMillis()) else it } }

    suspend fun restore(id: String) = mutate { list -> list.map { if (it.id == id) it.copy(deletedAt = null) else it } }

    suspend fun emptyTrash() = mutate { list -> list.filter { it.deletedAt == null } }

    fun find(id: String): PasswordEntry? = _entries.value.firstOrNull { it.id == id }

    suspend fun save(entry: PasswordEntry) = mutate { list ->
        val updated = entry.copy(updatedAt = System.currentTimeMillis())
        if (list.any { it.id == entry.id }) list.map { if (it.id == entry.id) updated else it } else list + updated
    }

    /** Видалити назавжди. */
    suspend fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    /** Новий або змінений власний розділ. */
    suspend fun saveGroup(group: PasswordGroup) = mutateAll { list, groups ->
        list to if (groups.any { it.id == group.id }) groups.map { if (it.id == group.id) group else it } else groups + group
    }

    /** Видаляє власний розділ; його записи переносяться в [moveTo] (за замовчуванням — у вбудовану категорію, на якій розділ базувався). */
    suspend fun deleteGroup(id: String, moveTo: String? = null) = mutateAll { list, groups ->
        val target = moveTo ?: groups.firstOrNull { it.id == id }?.base ?: PasswordCategory.OTHER.name
        list.map { if (it.category == id) it.copy(category = target) else it } to groups.filterNot { it.id == id }
    }

    /** Додає записи з резервної копії; ті, що вже є (той самий id), пропускаються. Повертає кількість доданих. */
    suspend fun merge(incoming: List<PasswordEntry>, incomingGroups: List<PasswordGroup> = emptyList()): Int {
        var added = 0
        mutateAll { list, groups ->
            val knownGroups = groups.map { it.id }.toSet()
            val nextGroups = groups + incomingGroups.filter { it.id !in knownGroups }
            val known = list.map { it.id }.toSet()
            val fresh = incoming.filter { it.id !in known }
            added = fresh.size
            (list + fresh) to nextGroups
        }
        return added
    }

    private suspend fun mutate(change: (List<PasswordEntry>) -> List<PasswordEntry>) = mutateAll { list, groups -> change(list) to groups }

    private suspend fun mutateAll(change: (List<PasswordEntry>, List<PasswordGroup>) -> Pair<List<PasswordEntry>, List<PasswordGroup>>) = withContext(Dispatchers.IO) {
        load()
        mutex.withLock {
            check(_loaded.value) { _error.value ?: "Паролі недоступні" }
            val dir = root
            val (next, nextGroups) = change(_entries.value, _groups.value)
            dir.mkdirs()
            val tmp = File(dir, "passwords.tmp")
            val plain = json.encodeToString(PasswordStoreFile.serializer(), PasswordStoreFile(next, nextGroups))
            tmp.writeBytes(crypto.seal(plain.encodeToByteArray(), LABEL))
            val target = File(dir, "passwords.bin")
            if (!tmp.renameTo(target)) {
                target.delete()
                check(tmp.renameTo(target)) { "Не вдалося зберегти паролі" }
            }
            if (dir === root) { _entries.value = next; _groups.value = nextGroups }
        }
    }

    companion object {
        private const val LABEL = "passwords-v1"
        const val TRASH_DAYS = 30
    }
}
