package ua.personal.archive.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val title: String) { SYSTEM("Як у системі"), LIGHT("Світла"), DARK("Темна") }

enum class LockMethod(val title: String, val hint: String) {
    DEVICE("Біометрія або PIN пристрою", "Відбиток, обличчя або PIN / пароль / ключ екрана блокування телефона"),
    PIN_BIOMETRIC("Біометрія або PIN додатку", "Відбиток чи обличчя, а запасний варіант — окремий PIN-код цього додатку (не PIN телефона)"),
    PIN("PIN-код додатку", "Лише окремий числовий PIN для цього додатку"),
    PASSWORD_BIOMETRIC("Біометрія або пароль додатку", "Відбиток чи обличчя, а запасний варіант — пароль додатку з літер і цифр"),
    PASSWORD("Пароль додатку", "Лише окремий пароль із літер і цифр"),
    ;

    /** Секрет додатку — числовий PIN (інакше пароль). */
    val numeric: Boolean get() = this == PIN || this == PIN_BIOMETRIC
    /** Біометрія як швидкий вхід поверх секрету додатку. */
    val biometric: Boolean get() = this == PIN_BIOMETRIC || this == PASSWORD_BIOMETRIC
}

/** Стиль іконок (набори з макетів): змінює плитки розділів і значки файлів. */
enum class IconStyle(val title: String) {
    CLASSIC("Класична"), NEON("Неонова"), DARK("Темна"), LIGHT("Світла"),
    COLORFUL("Кольорова"), GRADIENT("Градієнт"), METAL("Метал"), CAMO("Камуфляж"),
}

enum class Palette(val title: String) {
    NAVY("Темно-синя"),
    DYNAMIC("Кольори шпалер (Android 12+)"),
    BLUE("Синя"),
    GREEN("Зелена"),
    BURGUNDY("Бордова"),
    AMBER("Бурштинова"),
    VIOLET("Фіолетова"),
    GRAPHITE("Графітова"),
}

/** Налаштування інтерфейсу. Тут немає нічого секретного, тож звичайні SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(enumOr(prefs.getString("theme_mode", null), ThemeMode.DARK))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _palette = MutableStateFlow(enumOr(prefs.getString("palette", null), Palette.NAVY))
    val palette: StateFlow<Palette> = _palette.asStateFlow()

    init {
        // З версії 21 за замовчуванням: згорнули додаток — при поверненні знову вхід (одноразово для старих налаштувань).
        if (!prefs.getBoolean("lock_default_v2", false)) prefs.edit().putInt("auto_lock", 0).putBoolean("lock_default_v2", true).apply()
    }

    private val _autoLockSeconds = MutableStateFlow(prefs.getInt("auto_lock", 0))
    val autoLockSeconds: StateFlow<Int> = _autoLockSeconds.asStateFlow()

    private val _lockMethod = MutableStateFlow(enumOr(prefs.getString("lock_method", null), LockMethod.DEVICE))
    val lockMethod: StateFlow<LockMethod> = _lockMethod.asStateFlow()

    private val _readerTheme = MutableStateFlow(prefs.getString("reader_theme", null) ?: "DAY")
    /** Колір сторінки у вбудованій читалці (назва з ReaderTheme). */
    val readerTheme: StateFlow<String> = _readerTheme.asStateFlow()

    fun setReaderTheme(v: String) { prefs.edit().putString("reader_theme", v).apply(); _readerTheme.value = v }

    private val _iconStyle = MutableStateFlow(enumOr(prefs.getString("icon_style", null), IconStyle.CLASSIC))
    val iconStyle: StateFlow<IconStyle> = _iconStyle.asStateFlow()

    fun setIconStyle(v: IconStyle) { prefs.edit().putString("icon_style", v.name).apply(); _iconStyle.value = v }

    fun setLockMethod(v: LockMethod) { prefs.edit().putString("lock_method", v.name).apply(); _lockMethod.value = v }
    fun setThemeMode(v: ThemeMode) { prefs.edit().putString("theme_mode", v.name).apply(); _themeMode.value = v }
    fun setPalette(v: Palette) { prefs.edit().putString("palette", v.name).apply(); _palette.value = v }
    private val _lockOnScreenOff = MutableStateFlow(prefs.getBoolean("lock_screen_off", true))
    /** Блокувати, щойно гасне екран (сам або кнопкою живлення). */
    val lockOnScreenOff: StateFlow<Boolean> = _lockOnScreenOff.asStateFlow()
    fun setLockOnScreenOff(v: Boolean) { prefs.edit().putBoolean("lock_screen_off", v).apply(); _lockOnScreenOff.value = v }

    private val _passwordsFaceLock = MutableStateFlow(prefs.getBoolean("passwords_face_lock", true))
    /** Окреме підтвердження обличчям (або іншою біометрією / PIN пристрою) при вході в «Паролі». */
    val passwordsFaceLock: StateFlow<Boolean> = _passwordsFaceLock.asStateFlow()
    fun setPasswordsFaceLock(v: Boolean) { prefs.edit().putBoolean("passwords_face_lock", v).apply(); _passwordsFaceLock.value = v }

    private val _intruderPhoto = MutableStateFlow(prefs.getBoolean("intruder_photo", false))
    /** Фотографувати фронтальною камерою після неправильного пароля. */
    val intruderPhoto: StateFlow<Boolean> = _intruderPhoto.asStateFlow()
    fun setIntruderPhoto(v: Boolean) { prefs.edit().putBoolean("intruder_photo", v).apply(); _intruderPhoto.value = v }

    fun setAutoLockSeconds(v: Int) { prefs.edit().putInt("auto_lock", v).apply(); _autoLockSeconds.value = v }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        runCatching { enumValueOf<T>(name!!) }.getOrDefault(default)
}
