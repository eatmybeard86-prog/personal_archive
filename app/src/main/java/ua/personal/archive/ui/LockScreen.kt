package ua.personal.archive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ua.personal.archive.security.AppLock
import ua.personal.archive.security.LockMethod
import ua.personal.archive.security.PasswordStore

@Composable
fun LockScreen(activity: FragmentActivity) {
    val app = LocalApp.current
    val lock = app.lock
    val method by app.settings.lockMethod.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(24.dp).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ShieldLogo(88.dp)
            Spacer(Modifier.height(16.dp))
            Text("Мої документи", style = MaterialTheme.typography.headlineMedium)
            Text("Надійне зберігання важливих файлів", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))

            // refresh змушує перевірити стан знову після повернення з налаштувань пристрою
            val needsSetup = remember(method, refresh) { lock.needsSetup() }
            when {
                needsSetup && method == LockMethod.DEVICE -> NoDeviceLock(
                    onOpenSettings = {
                        lock.expectingExternalActivity = true
                        runCatching { activity.startActivity(AppLock.securitySettingsIntent()) }
                    },
                    onUsePassword = { app.settings.setLockMethod(LockMethod.PASSWORD) },
                    onRetry = { refresh++ },
                )
                needsSetup -> PasswordSetup(onDone = { pwd ->
                    lock.password.set(pwd)
                    pwd.fill('\u0000')
                    lock.unlockedBySetup()
                })
                method == LockMethod.DEVICE -> {
                    LaunchedEffect(Unit) { lock.promptSystem(activity) { error = it } }
                    Button(onClick = { error = null; lock.promptSystem(activity) { error = it } }) {
                        Icon(Icons.Outlined.Fingerprint, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Розблокувати")
                    }
                    // Якщо задано тривожний PIN, його можна ввести й без пароля додатку.
                    if (lock.password.duressSet) {
                        var pinMode by remember { mutableStateOf(false) }
                        if (!pinMode) TextButton(onClick = { pinMode = true }) { Text("Ввести PIN") }
                        else {
                            Spacer(Modifier.height(16.dp))
                            PasswordEntry(
                                biometric = false, onBiometric = {},
                                onSubmit = { lock.unlockWithPassword(it) },
                                waitMs = { lock.password.waitMs() },
                                label = "PIN",
                            )
                        }
                    }
                }
                else -> PasswordEntry(
                    biometric = method == LockMethod.PASSWORD_BIOMETRIC && lock.biometricAvailable(),
                    onBiometric = { lock.promptSystem(activity) { error = it } },
                    onSubmit = { lock.unlockWithPassword(it) },
                    waitMs = { lock.password.waitMs() },
                )
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun NoDeviceLock(onOpenSettings: () -> Unit, onUsePassword: () -> Unit, onRetry: () -> Unit) {
    Text(
        "На пристрої не налаштовано блокування екрана чи біометрію. " +
            "Увімкніть його в налаштуваннях або задайте окремий пароль для додатку.",
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onOpenSettings, Modifier.fillMaxWidth()) { Text("Відкрити налаштування пристрою") }
    OutlinedButton(onClick = onUsePassword, Modifier.fillMaxWidth()) { Text("Використати пароль додатку") }
    TextButton(onClick = onRetry) { Text("Я вже налаштував — перевірити") }
}

@Composable
fun PasswordSetup(onDone: (CharArray) -> Unit, title: String = "Створіть пароль для входу") {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun submit() {
        err = when {
            p1.length < PasswordStore.MIN_LENGTH -> "Мінімум ${PasswordStore.MIN_LENGTH} символів"
            p1 != p2 -> "Паролі не збігаються"
            else -> null
        }
        if (err == null) onDone(p1.toCharArray()).also { p1 = ""; p2 = "" }
    }
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Text(
        "Відновити забутий пароль неможливо — запам'ятайте його.",
        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    PasswordField(p1, { p1 = it }, "Пароль", ImeAction.Next) {}
    Spacer(Modifier.height(8.dp))
    PasswordField(p2, { p2 = it }, "Повторіть пароль", ImeAction.Done) { submit() }
    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { submit() }, Modifier.fillMaxWidth()) { Text("Зберегти") }
}

@Composable
private fun PasswordEntry(
    biometric: Boolean,
    onBiometric: () -> Unit,
    onSubmit: (CharArray) -> Boolean,
    waitMs: () -> Long,
    label: String = "Пароль",
) {
    var value by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableStateOf(waitMs()) }
    LaunchedEffect(biometric) { if (biometric) onBiometric() }
    LaunchedEffect(wait > 0) {
        while (wait > 0) { delay(1000); wait = waitMs() }
    }
    fun submit() {
        if (wait > 0 || value.isEmpty()) return
        if (!onSubmit(value.toCharArray())) {
            err = if (label == "Пароль") "Неправильний пароль" else "Неправильний PIN"
            wait = waitMs()
        }
        value = ""
    }
    PasswordField(value, { value = it; err = null }, label, ImeAction.Done, enabled = wait == 0L) { submit() }
    if (wait > 0) Text("Забагато спроб. Зачекайте ${wait / 1000 + 1} с", color = MaterialTheme.colorScheme.error)
    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { submit() }, Modifier.fillMaxWidth(), enabled = wait == 0L) { Text("Увійти") }
    if (biometric) {
        TextButton(onClick = onBiometric) {
            Icon(Icons.Outlined.Fingerprint, null)
            Spacer(Modifier.size(8.dp))
            Text("Увійти за біометрією")
        }
    }
}

@Composable
fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    ime: ImeAction,
    enabled: Boolean = true,
    onIme: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        modifier = Modifier.fillMaxWidth(),
    )
}
