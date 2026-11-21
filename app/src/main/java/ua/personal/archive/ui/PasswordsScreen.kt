package ua.personal.archive.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.personal.archive.data.PasswordCategory
import ua.personal.archive.data.PasswordEntry
import ua.personal.archive.data.PasswordGroup
import ua.personal.archive.passwords.ScanFields
import ua.personal.archive.passwords.ScanParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val PasswordCategory.icon: ImageVector
    get() = when (this) {
        PasswordCategory.SOCIAL -> Icons.Filled.Groups
        PasswordCategory.EMAIL -> Icons.Filled.Email
        PasswordCategory.WORK -> Icons.Filled.Work
        PasswordCategory.SHOPS -> Icons.Filled.ShoppingCart
        PasswordCategory.GAMES -> Icons.Filled.SportsEsports
        PasswordCategory.SERVICES -> Icons.Filled.Cloud
        PasswordCategory.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
        PasswordCategory.WIFI -> Icons.Filled.Wifi
        PasswordCategory.NOTES -> Icons.Filled.Lock
        PasswordCategory.LICENSES -> Icons.Filled.VpnKey
        PasswordCategory.SERVERS -> Icons.Filled.Dns
        PasswordCategory.CONTACTS -> Icons.Filled.Contacts
        PasswordCategory.OTHER -> Icons.Filled.MoreHoriz
    }

val PasswordCategory.tint: Color get() = Color(color)

/** Розділ для показу: вбудований або власний. [kind] — чиї поля й поведінку він використовує. */
data class CatView(
    val id: String,
    val title: String,
    val hint: String,
    val tint: Color,
    val icon: ImageVector,
    val kind: PasswordCategory,
    val custom: Boolean,
)

/** Іконки для власних розділів. */
val GROUP_ICONS: List<Pair<String, ImageVector>> = listOf(
    "key" to Icons.Filled.Key, "bank" to Icons.Filled.AccountBalance, "card" to Icons.Filled.CreditCard,
    "gov" to Icons.Filled.Gavel, "home" to Icons.Filled.Home, "car" to Icons.Filled.DirectionsCar,
    "school" to Icons.Filled.School, "medical" to Icons.Filled.MedicalServices, "flight" to Icons.Filled.Flight,
    "shield" to Icons.Filled.Shield, "phone" to Icons.Filled.PhoneAndroid, "tv" to Icons.Filled.Tv,
    "work" to Icons.Filled.Work, "cloud" to Icons.Filled.Cloud, "lock" to Icons.Filled.Lock,
    "star" to Icons.Filled.Star, "heart" to Icons.Filled.Favorite, "pets" to Icons.Filled.Pets,
    "shop" to Icons.Filled.ShoppingCart, "game" to Icons.Filled.SportsEsports, "folder" to Icons.Filled.Folder,
)
val GROUP_COLORS: List<Long> = listOf(0xFF60A5FA, 0xFF34D399, 0xFFFBBF24, 0xFFF87171, 0xFFA78BFA, 0xFFF472B6, 0xFF2DD4BF, 0xFFFB923C, 0xFF94A3B8)

/** Шаблони полів для власного розділу. */
private val GROUP_BASES = listOf(
    PasswordCategory.OTHER to "Логін і пароль", PasswordCategory.WIFI to "Wi-Fi", PasswordCategory.NOTES to "Нотатка",
    PasswordCategory.LICENSES to "Ліцензія", PasswordCategory.SERVERS to "Сервер", PasswordCategory.SUBSCRIPTIONS to "Підписка",
    PasswordCategory.CONTACTS to "Контакт",
)

fun groupIcon(key: String): ImageVector = GROUP_ICONS.firstOrNull { it.first == key }?.second ?: Icons.Filled.Key

fun PasswordCategory.view() = CatView(name, title, hint, tint, icon, this, custom = false)

fun PasswordGroup.view(): CatView {
    val kind = PasswordCategory.of(base)
    val fields = GROUP_BASES.firstOrNull { it.first == kind }?.second ?: "Логін і пароль"
    return CatView(id, title, "Власний розділ · поля: $fields", Color(color), groupIcon(icon), kind, custom = true)
}

/** Вбудовані розділи, далі власні, «Інше» — останнім. */
fun catViews(groups: List<PasswordGroup>): List<CatView> =
    PasswordCategory.entries.filter { it != PasswordCategory.OTHER }.map { it.view() } + groups.map { it.view() } + PasswordCategory.OTHER.view()

/** Розділ за id; невідомий (наприклад, видалений) — «Інше». */
fun List<CatView>.of(id: String?): CatView = firstOrNull { it.id == id } ?: PasswordCategory.OTHER.view()

@Composable
fun rememberCatViews(): List<CatView> {
    val groups by LocalApp.current.passwords.groups.collectAsStateWithLifecycle()
    return remember(groups) { catViews(groups) }
}

private fun dateTime(ms: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(Date(ms))

// ─────────────────────────────── буфер обміну ───────────────────────────────

private var clipGeneration = 0
private var clipPending = false

/**
 * Копіює в буфер обміну й через [CLIP_CLEAR_MS] очищує його (і логін, і пароль), якщо за цей час
 * з додатку нічого іншого не копіювали. Пароль позначається як «чутливий» — Android 13+
 * не показує його в підказці буфера й не синхронізує на інші пристрої.
 * Буфер також очищується одразу при блокуванні додатку ([clearCopiedSecrets]).
 */
fun copyToClipboard(context: Context, label: String, text: String, secret: Boolean) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    if (secret) clip.description.extras = PersistableBundle().apply {
        putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
    }
    cm.setPrimaryClip(clip)
    clipPending = true
    val gen = ++clipGeneration
    Handler(Looper.getMainLooper()).postDelayed({
        if (gen == clipGeneration && clipPending) { clipPending = false; runCatching { cm.clearPrimaryClip() } }
    }, CLIP_CLEAR_MS)
    Toast.makeText(context, "$label скопійовано. Буфер очиститься через ${CLIP_CLEAR_MS / 1000} с", Toast.LENGTH_SHORT).show()
}

/** Очищує буфер обміну, якщо там ще лежить скопійований з «Паролів» логін чи пароль. */
fun clearCopiedSecrets(context: Context) {
    if (!clipPending) return
    clipPending = false
    clipGeneration++
    runCatching { context.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip() }
}

private const val CLIP_CLEAR_MS = 30_000L

// ─────────────────────────────── перевірка обличчям ───────────────────────────────

/**
 * Вхід у «Паролі» — окрема перевірка обличчям. Android сам вибирає, що показати:
 * розпізнавання обличчя (якщо налаштоване), інакше відбиток або PIN/графічний ключ телефона.
 * Якщо на телефоні немає жодного блокування екрана, а в додатку свій пароль/PIN — просимо його.
 */
@Composable
fun PasswordGate(onBack: () -> Unit, content: @Composable () -> Unit) {
    val app = LocalApp.current
    val enabled by app.settings.passwordsFaceLock.collectAsStateWithLifecycle()
    val unlocked by app.passwords.unlocked.collectAsStateWithLifecycle()
    if (!enabled || unlocked) { content(); return }

    val activity = LocalContext.current as FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    var appPassword by remember { mutableStateOf("") }
    val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    val canSystem = remember { BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS }
    val canAppPassword = remember { app.lock.password.isSet }

    fun prompt() {
        error = null
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Паролі")
            .setSubtitle("Подивіться в камеру, щоб відкрити паролі")
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = app.passwords.markUnlocked()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                error = if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) null else errString.toString()
            }
        }).authenticate(info)
    }
    LaunchedEffect(Unit) { if (canSystem) prompt() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Face, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Паролі захищено", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (canSystem) "Підтвердіть, що це ви: обличчя, відбиток пальця або PIN телефона."
            else "На телефоні не налаштовано розпізнавання обличчя чи блокування екрана.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(20.dp))
        if (canSystem) Button(onClick = ::prompt) { Icon(Icons.Filled.Face, null); Spacer(Modifier.width(8.dp)); Text("Розпізнати обличчя") }
        else {
            OutlinedButton(onClick = {
                app.lock.expectingExternalActivity = true
                runCatching { activity.startActivity(ua.personal.archive.security.AppLock.securitySettingsIntent()) }
            }) { Text("Налаштувати розпізнавання обличчя") }
            if (canAppPassword) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    appPassword, { appPassword = it }, label = { Text("Пароль / PIN додатку") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val r = app.lock.password.check(appPassword.toCharArray())
                    appPassword = ""
                    if (r == ua.personal.archive.security.PasswordStore.Check.WRONG) error = "Неправильний пароль"
                    else app.passwords.markUnlocked()
                }) { Text("Відкрити") }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Назад") }
    }
}


// ─────────────────────────────── підписи полів за категорією ───────────────────────────────

/** Які поля показувати й як їх підписати для категорії. null — поле приховано. */
data class PasswordForm(
    val titleLabel: String,
    val loginLabel: String?,
    val passwordLabel: String?,
    val siteLabel: String?,
    val noteLabel: String = "Примітка",
    val server: Boolean = false,
    val subscription: Boolean = false,
    val phone: Boolean = false,
)

fun formFor(c: PasswordCategory): PasswordForm = when (c) {
    PasswordCategory.WIFI -> PasswordForm("Назва", "Назва мережі (SSID)", "Пароль Wi-Fi", null)
    PasswordCategory.NOTES -> PasswordForm("Заголовок", null, "PIN / секретний код", null, noteLabel = "Текст нотатки")
    PasswordCategory.LICENSES -> PasswordForm("Програма", "Зареєстровано на (e-mail)", "Ключ ліцензії", "Сайт виробника")
    PasswordCategory.SERVERS -> PasswordForm("Назва сервера", "Користувач", "Пароль / ключ", "Адреса / IP", server = true)
    PasswordCategory.SUBSCRIPTIONS -> PasswordForm("Сервіс", "Логін або e-mail", "Пароль", "Адреса сайту", subscription = true)
    PasswordCategory.CONTACTS -> PasswordForm("Ім'я", "E-mail", null, "Адреса", phone = true)
    else -> PasswordForm("Назва сервісу", "Логін або e-mail", "Пароль", "Адреса сайту")
}

private val PROTOCOLS = listOf("SSH" to "22", "SFTP" to "22", "FTP" to "21", "RDP" to "3389", "VNC" to "5900", "HTTPS" to "443", "Інше" to "")

private fun periodTitle(p: String) = if (p == "YEAR") "щороку" else "щомісяця"

/** «199,99 ₴» → 199.99; не число — null. */
private fun priceValue(price: String): Double? =
    Regex("\\d[\\d\\s]*(?:[.,]\\d+)?").find(price)?.value?.replace(Regex("\\s"), "")?.replace(',', '.')?.toDoubleOrNull()

private fun subtitleOf(e: PasswordEntry, kind: PasswordCategory): String {
    return when (kind) {
        PasswordCategory.SERVERS -> buildString {
            if (e.protocol.isNotBlank()) append(e.protocol.lowercase()).append(" ")
            if (e.login.isNotBlank()) append(e.login).append("@")
            append(e.site)
            if (e.port.isNotBlank()) append(":").append(e.port)
        }
        PasswordCategory.SUBSCRIPTIONS -> listOfNotNull(
            e.price.takeIf { it.isNotBlank() }?.let { "$it ${periodTitle(e.period)}" },
            e.nextPayment?.let { "оплата ${formatDate(it)}" },
        ).joinToString(" · ")
        PasswordCategory.CONTACTS -> listOf(e.phone, e.login).filter { it.isNotBlank() }.joinToString(" · ")
        PasswordCategory.NOTES -> e.note.lineSequence().firstOrNull().orEmpty()
        else -> e.login.ifBlank { e.site }
    }.ifBlank { "—" }
}

// ─────────────────────────────── список ───────────────────────────────

private const val VIEW_FAVORITES = "★"
private const val VIEW_ARCHIVE = "ARCHIVE"
private const val VIEW_TRASH = "TRASH"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: (category: String?, scan: Boolean) -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by app.passwords.entries.collectAsStateWithLifecycle()
    val error by app.passwords.error.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<PasswordGroup?>(null) }
    var deleteGroup by remember { mutableStateOf<PasswordGroup?>(null) }
    val groups by app.passwords.groups.collectAsStateWithLifecycle()
    val cats = remember(groups) { catViews(groups) }
    LaunchedEffect(Unit) { app.passwords.load(); app.passwords.purgeTrash() }

    val active = remember(entries) { entries.filter { it.deletedAt == null && !it.archived } }
    val shown = remember(entries, query, view) {
        val q = query.trim().lowercase()
        val base = when (view) {
            null -> active
            VIEW_FAVORITES -> active.filter { it.favorite }
            VIEW_ARCHIVE -> entries.filter { it.deletedAt == null && it.archived }
            VIEW_TRASH -> entries.filter { it.deletedAt != null }
            else -> active.filter { cats.of(it.category).id == view }
        }
        base.filter { e ->
            q.isEmpty() || listOf(e.title, e.login, e.site, e.note, e.phone).any { it.lowercase().contains(q) }
        }.sortedWith(
            if (view == VIEW_TRASH) compareByDescending<PasswordEntry> { it.deletedAt ?: 0L }
            else compareByDescending<PasswordEntry> { it.favorite }.thenBy { it.title.lowercase() },
        )
    }
    val addCategory = cats.firstOrNull { it.id == view }?.id
    val viewCat = cats.firstOrNull { it.id == view }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Паролі") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
            )
        },
        floatingActionButton = {
            if (view != VIEW_TRASH && view != VIEW_ARCHIVE) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SmallFloatingActionButton(
                    onClick = { onAdd(addCategory, true) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) { Icon(Icons.Outlined.DocumentScanner, "Сканувати камерою") }
                Spacer(Modifier.height(12.dp))
                FloatingActionButton(onClick = { onAdd(addCategory, false) }, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                    Icon(Icons.Filled.Add, "Додати запис")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Пошук: сервіс, логін, сайт") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = view == null, onClick = { view = null }, label = { Text("Усі (${active.size})") })
                    FilterChip(
                        selected = view == VIEW_FAVORITES, onClick = { view = if (view == VIEW_FAVORITES) null else VIEW_FAVORITES },
                        label = { Text("Обране") }, leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(18.dp), tint = Color(0xFFFBBF24)) },
                    )
                    cats.forEach { c ->
                        val n = active.count { cats.of(it.category).id == c.id }
                        FilterChip(
                            selected = view == c.id, onClick = { view = if (view == c.id) null else c.id },
                            label = { Text(if (n > 0) "${c.title} ($n)" else c.title) },
                            leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp), tint = c.tint) },
                        )
                    }
                    // Власний розділ для конкретного типу паролів («Банки», «Держпослуги»…).
                    FilterChip(
                        selected = false, onClick = { editGroup = PasswordGroup(title = "") },
                        label = { Text("Свій розділ") }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = view == VIEW_ARCHIVE, onClick = { view = if (view == VIEW_ARCHIVE) null else VIEW_ARCHIVE },
                        label = { Text("Архів") }, leadingIcon = { Icon(Icons.Outlined.Archive, null, Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = view == VIEW_TRASH, onClick = { view = if (view == VIEW_TRASH) null else VIEW_TRASH },
                        label = { Text("Кошик") }, leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)) },
                    )
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            viewCat?.let { c ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        val g = groups.firstOrNull { it.id == c.id }
                        if (g != null) {
                            IconButton(onClick = { editGroup = g }) { Icon(Icons.Outlined.Edit, "Змінити розділ") }
                            IconButton(onClick = { deleteGroup = g }) { Icon(Icons.Outlined.Delete, "Видалити розділ") }
                        }
                    }
                }
            }
            if (viewCat?.kind == PasswordCategory.SUBSCRIPTIONS && shown.isNotEmpty()) item {
                val monthly = shown.sumOf { e -> (priceValue(e.price) ?: 0.0) / if (e.period == "YEAR") 12 else 1 }
                Text(
                    "Разом ≈ ${"%.2f".format(Locale.ROOT, monthly)} на місяць (${"%.2f".format(Locale.ROOT, monthly * 12)} на рік)",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (view == VIEW_TRASH) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Записи з кошика видаляються назавжди через ${ua.personal.archive.data.PasswordVault.TRASH_DAYS} днів.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                    if (shown.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Очистити") }
                }
            }
            if (view == VIEW_ARCHIVE) item {
                Text("Старі записи, які не потрібні щодня. Вони не показуються в загальному списку.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (shown.isEmpty()) item {
                Text(
                    when {
                        query.isNotBlank() -> "Нічого не знайдено"
                        view == VIEW_TRASH -> "Кошик порожній"
                        view == VIEW_ARCHIVE -> "В архіві нічого немає"
                        view == VIEW_FAVORITES -> "Позначте запис зірочкою, щоб він був тут"
                        else -> "Тут поки порожньо. Натисніть «+», щоб додати запис, або кнопку зі сканером — і наведіть камеру на логін і пароль на папері чи екрані іншого пристрою."
                    },
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            if (view == null && query.isBlank()) {
                cats.forEach { c ->
                    val list = shown.filter { cats.of(it.category).id == c.id }
                    if (list.isNotEmpty()) {
                        item(key = "h-" + c.id) {
                            Row(Modifier.padding(top = 8.dp).clickable { view = c.id }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(c.icon, null, tint = c.tint, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("${c.title} · ${list.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        items(list, key = { it.id }) { e -> PasswordRow(e, cats.of(e.category), ctx) { onOpen(e.id) } }
                    }
                }
            } else items(shown, key = { it.id }) { e -> PasswordRow(e, cats.of(e.category), ctx) { onOpen(e.id) } }
        }
    }
    editGroup?.let { g ->
        GroupDialog(g, onDismiss = { editGroup = null }, onSaved = { saved -> editGroup = null; view = saved.id })
    }
    deleteGroup?.let { g ->
        val target = PasswordCategory.of(g.base)
        val n = entries.count { it.category == g.id }
        AlertDialog(
            onDismissRequest = { deleteGroup = null },
            title = { Text("Видалити розділ «${g.title}»?") },
            text = { Text(if (n > 0) "Записи з нього ($n) не зникнуть — вони перейдуть у «${target.title}»." else "Розділ порожній.") },
            confirmButton = {
                TextButton(onClick = { deleteGroup = null; view = null; scope.launch { app.passwords.deleteGroup(g.id) } }) { Text("Видалити") }
            },
            dismissButton = { TextButton(onClick = { deleteGroup = null }) { Text("Скасувати") } },
        )
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text("Очистити кошик?") },
        text = { Text("Усі записи з кошика буде видалено назавжди.") },
        confirmButton = { TextButton(onClick = { confirmEmpty = false; scope.launch { app.passwords.emptyTrash() } }) { Text("Очистити") } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Скасувати") } },
    )
}

@Composable
private fun PasswordRow(e: PasswordEntry, c: CatView, ctx: Context, onClick: () -> Unit) {
    val form = formFor(c.kind)
    val dueSoon = e.nextPayment != null && daysLeft(e.nextPayment) in 0..3
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.tint.copy(alpha = 0.18f))
                    .border(1.dp, c.tint.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(c.icon, null, tint = c.tint) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text((if (e.favorite) "★ " else "") + e.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitleOf(e, c.kind), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (dueSoon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (e.deletedAt == null) {
                if (form.loginLabel != null && e.login.isNotEmpty()) IconButton(onClick = { copyToClipboard(ctx, form.loginLabel, e.login, secret = false) }) {
                    Icon(Icons.Outlined.ContentCopy, "Копіювати: ${form.loginLabel}", Modifier.size(20.dp))
                }
                if (form.passwordLabel != null && e.password.isNotEmpty()) IconButton(onClick = { copyToClipboard(ctx, form.passwordLabel, e.password, secret = true) }) {
                    Icon(Icons.Filled.Key, "Копіювати: ${form.passwordLabel}", Modifier.size(20.dp))
                }
            }
        }
    }
}

// ─────────────────────────────── картка ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDetailScreen(id: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by app.passwords.entries.collectAsStateWithLifecycle()
    val loaded by app.passwords.loaded.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.passwords.load() }
    val e = entries.firstOrNull { it.id == id }
    LaunchedEffect(loaded, e) { if (loaded && e == null) onBack() }
    val cats = rememberCatViews()
    if (e == null) return
    val c = cats.of(e.category)
    val form = formFor(c.kind)
    val inTrash = e.deletedAt != null
    var show by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun openExternal(intent: Intent) {
        app.lock.expectingExternalActivity = true
        runCatching { ctx.startActivity(intent) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
                actions = {
                    if (!inTrash) {
                        IconButton(onClick = { scope.launch { app.passwords.save(e.copy(favorite = !e.favorite)) } }) {
                            Icon(if (e.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, if (e.favorite) "Прибрати з обраного" else "В обране",
                                tint = if (e.favorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Редагувати") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(c.icon, null, tint = c.tint, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(e.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        c.title + when { inTrash -> " · у кошику"; e.archived -> " · в архіві"; else -> "" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            if (form.loginLabel != null) InfoRow(form.loginLabel, e.login.ifEmpty { "—" }) {
                if (e.login.isNotEmpty()) IconButton(onClick = { copyToClipboard(ctx, form.loginLabel, e.login, secret = false) }) { Icon(Icons.Outlined.ContentCopy, "Копіювати") }
            }
            if (form.passwordLabel != null) InfoRow(
                form.passwordLabel,
                if (e.password.isEmpty()) "—" else if (show) e.password else "•".repeat(e.password.length.coerceIn(8, 16)), mono = show,
            ) {
                if (e.password.isNotEmpty()) {
                    IconButton(onClick = { show = !show }) {
                        Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Сховати" else "Показати")
                    }
                    IconButton(onClick = { copyToClipboard(ctx, form.passwordLabel, e.password, secret = true) }) { Icon(Icons.Outlined.ContentCopy, "Копіювати") }
                }
            }
            if (form.phone) InfoRow("Телефон", e.phone.ifEmpty { "—" }) {
                if (e.phone.isNotEmpty()) {
                    IconButton(onClick = { openExternal(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + e.phone.filter { it.isDigit() || it == '+' }))) }) { Icon(Icons.Outlined.Call, "Подзвонити") }
                    IconButton(onClick = { copyToClipboard(ctx, "Телефон", e.phone, secret = false) }) { Icon(Icons.Outlined.ContentCopy, "Копіювати") }
                }
            }
            if (form.siteLabel != null) InfoRow(form.siteLabel, e.site.ifEmpty { "—" }) {
                if (e.site.isNotEmpty()) {
                    if (form.server) IconButton(onClick = { copyToClipboard(ctx, form.siteLabel, e.site, secret = false) }) { Icon(Icons.Outlined.ContentCopy, "Копіювати") }
                    else if (!form.phone) IconButton(onClick = {
                        openExternal(Intent(Intent.ACTION_VIEW, Uri.parse(if (e.site.contains("://")) e.site else "https://${e.site}")))
                    }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Відкрити сайт") }
                }
            }
            if (form.server) {
                InfoRow("Протокол і порт", listOf(e.protocol, e.port).filter { it.isNotBlank() }.joinToString(" : ").ifEmpty { "—" }) {}
                if (e.site.isNotEmpty() && e.protocol in listOf("SSH", "SFTP")) {
                    val cmd = "${e.protocol.lowercase()} " + (if (e.port.isNotBlank() && e.port != "22") (if (e.protocol == "SSH") "-p ${e.port} " else "-P ${e.port} ") else "") +
                        (if (e.login.isNotBlank()) "${e.login}@" else "") + e.site
                    InfoRow("Команда", cmd, mono = true) {
                        IconButton(onClick = { copyToClipboard(ctx, "Команда", cmd, secret = false) }) { Icon(Icons.Outlined.ContentCopy, "Копіювати") }
                    }
                }
            }
            if (form.subscription) {
                InfoRow("Вартість", e.price.takeIf { it.isNotBlank() }?.let { "$it ${periodTitle(e.period)}" } ?: "—") {}
                InfoRow("Наступна оплата", e.nextPayment?.let { formatDate(it) + if (daysLeft(it) in 0..3) " (скоро!)" else "" } ?: "—") {}
            }
            if (e.note.isNotEmpty()) InfoRow(form.noteLabel, e.note) {
                if (c.kind == PasswordCategory.NOTES) IconButton(onClick = { copyToClipboard(ctx, "Нотатку", e.note, secret = true) }) { Icon(Icons.Outlined.ContentCopy, "Копіювати") }
            }
            HorizontalDivider()
            Text("Створено: ${dateTime(e.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Змінено: ${dateTime(e.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            e.deletedAt?.let { Text("У кошику з ${dateTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (inTrash) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { app.passwords.restore(e.id) } }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.RestoreFromTrash, null); Spacer(Modifier.width(6.dp)); Text("Відновити")
                }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.DeleteForever, null); Spacer(Modifier.width(6.dp)); Text("Назавжди")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(6.dp)); Text("Редагувати") }
                    OutlinedButton(onClick = { scope.launch { app.passwords.trash(e.id); onBack() } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text("Видалити")
                    }
                }
                OutlinedButton(onClick = { scope.launch { app.passwords.save(e.copy(archived = !e.archived)) } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (e.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null); Spacer(Modifier.width(6.dp))
                    Text(if (e.archived) "Повернути з архіву" else "Перемістити в архів")
                }
                Text(
                    "Видалений запис потрапляє в кошик — його можна відновити протягом ${ua.personal.archive.data.PasswordVault.TRASH_DAYS} днів.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Видалити «${e.title}» назавжди?") },
        text = { Text("Відновити запис буде неможливо (хіба що з резервної копії).") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { app.passwords.delete(e.id); onBack() } }) { Text("Видалити") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") } },
    )
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false, actions: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = if (mono) FontFamily.Monospace else null)
        }
        actions()
    }
}

// ─────────────────────────────── редагування ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PasswordEditScreen(id: String?, initialCategory: String?, autoScan: Boolean, onDone: (String?) -> Unit, onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val original = remember { id?.let { app.passwords.find(it) } }
    var title by rememberSaveable { mutableStateOf(original?.title.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(original?.category ?: initialCategory ?: PasswordCategory.OTHER.name) }
    var login by rememberSaveable { mutableStateOf(original?.login.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(original?.password.orEmpty()) }
    var site by rememberSaveable { mutableStateOf(original?.site.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var port by rememberSaveable { mutableStateOf(original?.port.orEmpty()) }
    var protocol by rememberSaveable { mutableStateOf(original?.protocol.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(original?.price.orEmpty()) }
    var period by rememberSaveable { mutableStateOf(original?.period?.ifEmpty { null } ?: "MONTH") }
    var nextPayment by rememberSaveable { mutableStateOf(original?.nextPayment) }
    var phone by rememberSaveable { mutableStateOf(original?.phone.orEmpty()) }
    var show by remember { mutableStateOf(original == null) }
    var scanning by rememberSaveable { mutableStateOf(autoScan) }
    var pickDate by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    // Категорію підбираємо самі, поки користувач не вибрав її вручну.
    var categoryTouched by rememberSaveable { mutableStateOf(original != null || initialCategory != null) }
    val cats = rememberCatViews()
    val kind = cats.of(category).kind
    val form = formFor(kind)
    val dirty = original?.let {
        it.title != title || it.login != login || it.password != password || it.site != site || it.note != note || it.category != category ||
            it.port != port || it.protocol != protocol || it.price != price || it.nextPayment != nextPayment || it.phone != phone
    } ?: listOf(title, login, password, site, note, phone, price).any { it.isNotBlank() }

    fun autoCategory() {
        if (!categoryTouched) ScanParser.guessCategory(title, site, login)?.let { category = it }
    }

    fun apply(f: ScanFields) {
        if (f.title.isNotBlank()) title = f.title
        if (f.login.isNotBlank()) login = f.login
        if (f.password.isNotBlank()) password = f.password
        if (f.site.isNotBlank()) site = f.site
        if (f.note.isNotBlank()) note = listOf(note.trimEnd(), f.note).filter { it.isNotEmpty() }.joinToString("\n")
        if (title.isBlank()) title = ScanParser.titleFor(site).ifBlank { login.substringAfter('@', "").substringBefore('.') }
        if (f.category != null && !categoryTouched) category = f.category else autoCategory()
        show = true
    }

    fun save() {
        val t = title.trim().ifEmpty { ScanParser.titleFor(site).ifEmpty { login.trim() } }
        if (t.isEmpty() && password.isEmpty() && note.isBlank()) { error = "Вкажіть назву або хоча б одне поле"; return }
        busy = true
        scope.launch {
            try {
                val entry = (original ?: PasswordEntry(title = t)).copy(
                    title = t.ifEmpty { "Без назви" }, category = category, login = login.trim(), password = password,
                    site = site.trim(), note = note.trim(), port = port.trim(), protocol = protocol,
                    price = price.trim(), period = if (form.subscription) period else "", nextPayment = nextPayment, phone = phone.trim(),
                )
                app.passwords.save(entry)
                onDone(if (original == null) entry.id else null)
            } catch (e: Exception) {
                error = "Не вдалося зберегти: ${e.message}"
                busy = false
            }
        }
    }

    if (scanning) {
        PasswordScanner(onDone = { scanning = false; apply(it) }, onClose = { scanning = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Новий запис" else "Редагування") },
                navigationIcon = { IconButton(onClick = { if (dirty) confirmExit = true else onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
                actions = { TextButton(onClick = ::save, enabled = !busy) { Text("Зберегти") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DocumentScanner, null); Spacer(Modifier.width(8.dp))
                Text("Сканувати камерою (текст або QR)")
            }
            Text(
                "Наведіть камеру на логін і пароль — на папірці, екрані комп'ютера чи іншого телефона. QR-код Wi-Fi чи 2FA теж підійде.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Категорія", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c ->
                    FilterChip(
                        selected = cats.of(category).id == c.id, onClick = { category = c.id; categoryTouched = true },
                        label = { Text(c.title) }, leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp), tint = c.tint) },
                    )
                }
            }
            OutlinedTextField(
                title, { title = it; autoCategory() }, label = { Text("${form.titleLabel} *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.loginLabel != null) OutlinedTextField(
                login, { login = it; autoCategory() }, label = { Text(form.loginLabel) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
            if (form.phone) OutlinedTextField(
                phone, { phone = it }, label = { Text("Телефон") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            if (form.passwordLabel != null) OutlinedTextField(
                password, { password = it }, label = { Text(form.passwordLabel) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = {
                    Row {
                        if (kind != PasswordCategory.LICENSES && kind != PasswordCategory.NOTES) {
                            IconButton(onClick = { password = ScanParser.generate(); show = true }) { Icon(Icons.Outlined.AutoAwesome, "Згенерувати надійний пароль") }
                        }
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Сховати" else "Показати")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.siteLabel != null) OutlinedTextField(
                site, { site = it; autoCategory() }, label = { Text(form.siteLabel) }, singleLine = !form.phone,
                keyboardOptions = KeyboardOptions(keyboardType = if (form.phone) KeyboardType.Text else KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            )
            if (form.server) {
                Text("Протокол", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROTOCOLS.forEach { (name, defPort) ->
                        FilterChip(selected = protocol == name, onClick = {
                            val oldDefault = PROTOCOLS.firstOrNull { it.first == protocol }?.second
                            protocol = name
                            if (port.isBlank() || port == oldDefault) port = defPort
                        }, label = { Text(name) })
                    }
                }
                OutlinedTextField(
                    port, { v -> port = v.filter { it.isDigit() }.take(5) }, label = { Text("Порт") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
            if (form.subscription) {
                OutlinedTextField(
                    price, { price = it }, label = { Text("Вартість") }, placeholder = { Text("199 ₴") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = period == "MONTH", onClick = { period = "MONTH" }, label = { Text("Щомісяця") })
                    FilterChip(selected = period == "YEAR", onClick = { period = "YEAR" }, label = { Text("Щороку") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate = true }) {
                        Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                        Text(nextPayment?.let { "Наступна оплата ${formatDate(it)}" } ?: "Дата наступної оплати")
                    }
                    if (nextPayment != null) IconButton(onClick = { nextPayment = null }) { Icon(Icons.Outlined.Close, "Прибрати дату") }
                }
            }
            OutlinedTextField(note, { note = it }, label = { Text(form.noteLabel) }, minLines = if (kind == PasswordCategory.NOTES) 6 else 3, modifier = Modifier.fillMaxWidth())
            if (original != null) Text(
                "Створено: ${dateTime(original.createdAt)} · Змінено: ${dateTime(original.updatedAt)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Зберегти") }
            Text(
                "Записи шифруються AES-256-GCM окремим ключем з Android Keystore і зберігаються лише в цьому телефоні (та в зашифрованих резервних копіях).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = nextPayment)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { nextPayment = state.selectedDateMillis; pickDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Скасувати") } },
        ) { DatePicker(state) }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Вийти без збереження?") },
        confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Вийти") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Залишитися") } },
    )
    error?.let {
        AlertDialog(onDismissRequest = { error = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
    }
}

// ─────────────────────────────── власний розділ ───────────────────────────────

/** Створення або зміна власного розділу: назва, які поля, іконка й колір. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupDialog(initial: PasswordGroup, onDismiss: () -> Unit, onSaved: (PasswordGroup) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val isNew = app.passwords.groups.value.none { it.id == initial.id }
    var title by remember { mutableStateOf(initial.title) }
    var base by remember { mutableStateOf(PasswordCategory.of(initial.base)) }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableStateOf(initial.color) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Новий розділ" else "Змінити розділ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    title, { title = it; error = null }, label = { Text("Назва розділу") }, singleLine = true,
                    placeholder = { Text("Банки, Держпослуги, Діти…") }, isError = error != null,
                    supportingText = if (error != null) { { Text(error.orEmpty()) } } else null, modifier = Modifier.fillMaxWidth(),
                )
                Text("Які поля потрібні", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GROUP_BASES.forEach { (c, label) ->
                        FilterChip(selected = base == c, onClick = { base = c }, label = { Text(label) })
                    }
                }
                Text("Іконка", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GROUP_ICONS.forEach { (key, vector) ->
                        val selected = icon == key
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(color).copy(alpha = 0.25f) else Color.Transparent)
                                .border(if (selected) 2.dp else 1.dp, if (selected) Color(color) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vector, null, tint = Color(color)) }
                    }
                }
                Text("Колір", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GROUP_COLORS.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .border(if (color == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = title.trim()
                val taken = catViews(app.passwords.groups.value).any { it.id != initial.id && it.title.equals(t, ignoreCase = true) }
                when {
                    t.isEmpty() -> error = "Вкажіть назву"
                    taken -> error = "Розділ з такою назвою вже є"
                    else -> {
                        val g = initial.copy(title = t, base = base.name, icon = icon, color = color)
                        scope.launch { app.passwords.saveGroup(g); onSaved(g) }
                    }
                }
            }) { Text(if (isNew) "Створити" else "Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}
