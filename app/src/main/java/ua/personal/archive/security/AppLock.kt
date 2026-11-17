package ua.personal.archive.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Блокування додатку. Спосіб входу обирається в налаштуваннях ([LockMethod]):
 *  - біометрія або PIN / пароль екрана блокування пристрою;
 *  - власний пароль додатку;
 *  - власний пароль + біометрія для швидкого входу.
 * Після переходу у фон додаток блокується, якщо його не було видно довше за заданий час.
 */
class AppLock(
    private val context: Context,
    private val settings: SettingsStore,
    val password: PasswordStore,
) : DefaultLifecycleObserver {

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _decoy = MutableStateFlow(false)
    /** true — увійшли «тривожним» PIN-ом: показується окреме порожнє сховище. */
    val decoy: StateFlow<Boolean> = _decoy.asStateFlow()

    private var backgroundAt = 0L
    /** Поки відкрито системне вікно (вибір файлу, камера, надсилання), не блокуємо одразу. */
    var expectingExternalActivity = false

    private val timeoutMs get() = settings.autoLockSeconds.value * 1000L

    private val deviceAuthenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun deviceLockAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(deviceAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun biometricAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Чи треба спершу налаштувати вхід (немає ні блокування екрана, ні пароля додатку). */
    fun needsSetup(): Boolean = when (settings.lockMethod.value) {
        LockMethod.DEVICE -> !deviceLockAvailable()
        else -> !password.isSet
    }

    fun usesPassword(): Boolean = settings.lockMethod.value != LockMethod.DEVICE

    fun lockNow() { _locked.value = true }

    /** Позначає додаток розблокованим одразу після першого налаштування пароля. */
    fun unlockedBySetup() { _decoy.value = false; _locked.value = false }

    /** Запис у журнал доступу (задається з ArchiveApp). */
    var onAccess: ((kind: String, method: String) -> Unit)? = null

    fun unlockWithPassword(input: CharArray): Boolean {
        val r = password.check(input)
        input.fill('\u0000')
        onAccess?.invoke(r.name, "пароль / PIN")
        when (r) {
            PasswordStore.Check.MAIN -> { _decoy.value = false; _locked.value = false }
            PasswordStore.Check.DURESS -> { _decoy.value = true; _locked.value = false }
            PasswordStore.Check.WRONG -> return false
        }
        return true
    }

    /** Системне вікно біометрії (або PIN пристрою). Повертає false, якщо для обраного способу воно не потрібне. */
    fun promptSystem(activity: FragmentActivity, onError: (String) -> Unit): Boolean {
        val method = settings.lockMethod.value
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Мої документи")
            .setSubtitle("Підтвердіть, що це ви")
        when (method) {
            LockMethod.DEVICE -> builder.setAllowedAuthenticators(deviceAuthenticators)
            LockMethod.PASSWORD_BIOMETRIC, LockMethod.PIN_BIOMETRIC -> {
                if (!biometricAvailable()) return false
                builder.setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .setNegativeButtonText(if (method.numeric) "Ввести PIN" else "Ввести пароль")
            }
            LockMethod.PASSWORD, LockMethod.PIN -> return false
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                _decoy.value = false
                _locked.value = false
                onAccess?.invoke("MAIN", "біометрія / PIN пристрою")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) onError(errString.toString())
            }
        })
        prompt.authenticate(builder.build())
        return true
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundAt = System.currentTimeMillis()
        if (!expectingExternalActivity && timeoutMs == 0L) lockNow()
    }

    override fun onStart(owner: LifecycleOwner) {
        val away = System.currentTimeMillis() - backgroundAt
        // Для системних вікон (камера, вибір файлу, надсилання) даємо до 10 хвилин.
        val limit = if (expectingExternalActivity) maxOf(timeoutMs, 10 * 60_000L) else timeoutMs
        if (backgroundAt != 0L && away > limit) lockNow()
        expectingExternalActivity = false
    }

    companion object {
        fun securitySettingsIntent(): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            else Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
}
