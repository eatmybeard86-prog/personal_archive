package ua.personal.archive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.personal.archive.BuildConfig
import ua.personal.archive.reader.ReaderTheme
import ua.personal.archive.security.IconStyle
import ua.personal.archive.security.LockMethod
import ua.personal.archive.security.Palette
import ua.personal.archive.security.ThemeMode

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, cardBorder()),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RadioRow(selected: Boolean, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Вкладка «Безпека»: спосіб входу, автоблокування, резервні копії. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SecurityTab(onBackup: () -> Unit) {
    val app = LocalApp.current
    val lock = app.lock
    val method by app.settings.lockMethod.collectAsStateWithLifecycle()
    val autoLock by app.settings.autoLockSeconds.collectAsStateWithLifecycle()
    var setPasswordFor by remember { mutableStateOf<LockMethod?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun choose(m: LockMethod) {
        when {
            m == method -> Unit
            m == LockMethod.DEVICE && !lock.deviceLockAvailable() ->
                message = "На пристрої не налаштовано блокування екрана чи біометрію. Спершу увімкніть їх у налаштуваннях Android."
            m == LockMethod.PASSWORD_BIOMETRIC && !lock.biometricAvailable() ->
                message = "На пристрої немає налаштованого відбитка чи обличчя. Можна вибрати просто «Пароль додатку»."
            m != LockMethod.DEVICE && !lock.password.isSet -> setPasswordFor = m
            else -> app.settings.setLockMethod(m)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Безпека") }) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(SafeGreen.copy(alpha = 0.3f), SafeGreen.copy(alpha = 0.08f))))
                    .border(1.dp, SafeGreen.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShieldBadge(40.dp, SafeGreen) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Шифрування увімкнено", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Кожен файл зашифровано AES-256-GCM. Ключ зберігається в захищеному сховищі Android (Keystore) і не покидає телефон. " +
                            "Скріншоти й запис екрана в додатку заборонені.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Section("Спосіб входу") {
                LockMethod.entries.forEach { m -> RadioRow(method == m, m.title, m.hint) { choose(m) } }
                if (method != LockMethod.DEVICE) {
                    OutlinedButton(onClick = { setPasswordFor = method }) {
                        Icon(Icons.Filled.Key, null); Spacer(Modifier.width(8.dp)); Text("Змінити пароль додатку")
                    }
                }
            }

            Section("Автоматичне блокування") {
                Text("Коли додаток згорнуто довше ніж:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Одразу", 30 to "30 с", 60 to "1 хв", 300 to "5 хв").forEach { (s, label) ->
                        FilterChip(selected = autoLock == s, onClick = { app.settings.setAutoLockSeconds(s) }, label = { Text(label) })
                    }
                }
                OutlinedButton(onClick = { lock.lockNow() }) {
                    Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(8.dp)); Text("Заблокувати зараз")
                }
            }

            Section("Резервні копії") {
                NavRow(Icons.Filled.Backup, "Резервні копії", "Зашифрований архів усіх документів або вибраних розділів", onBackup)
            }
        }
    }

    setPasswordFor?.let { target ->
        AlertDialog(
            onDismissRequest = { setPasswordFor = null },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { setPasswordFor = null }) { Text("Скасувати") } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PasswordSetup(
                        title = if (lock.password.isSet) "Новий пароль додатку" else "Створіть пароль додатку",
                        onDone = { pwd ->
                            lock.password.set(pwd)
                            pwd.fill('\u0000')
                            app.settings.setLockMethod(target)
                            setPasswordFor = null
                            message = "Пароль збережено."
                        },
                    )
                }
            },
        )
    }
    message?.let {
        AlertDialog(onDismissRequest = { message = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
}

/** Вкладка «Більше»: оформлення, читалка, про додаток. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoreTab(onBackup: () -> Unit) {
    val app = LocalApp.current
    val mode by app.settings.themeMode.collectAsStateWithLifecycle()
    val palette by app.settings.palette.collectAsStateWithLifecycle()
    val readerTheme by app.settings.readerTheme.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Більше") }) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Тема оформлення") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { app.settings.setThemeMode(m) }, label = { Text(m.title) })
                    }
                }
                Text("Кольори", style = MaterialTheme.typography.labelLarge)
                Palette.entries.forEach { p ->
                    if (p == Palette.DYNAMIC && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return@forEach
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { app.settings.setPalette(p) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(swatch(p)), contentAlignment = Alignment.Center) {
                            if (palette == p) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(p.title)
                    }
                }
            }

            Section("Стиль іконок") {
                val iconStyle by app.settings.iconStyle.collectAsStateWithLifecycle()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconStyle.entries.forEach { st ->
                        val selected = st == iconStyle
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else cardBorder(),
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable { app.settings.setIconStyle(st) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StyledIcon(Icons.Filled.Shield, Color(0xFF22C55E), 34.dp, style = st)
                            StyledIcon(Icons.Filled.Folder, Color(0xFF3B82F6), 34.dp, style = st)
                            StyledIcon(Icons.Filled.DirectionsCar, Color(0xFF38BDF8), 34.dp, style = st)
                            Text(st.title, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Section("Колір сторінки в читалці") {
                ReaderTheme.entries.forEach { t ->
                    RadioRow(readerTheme == t.name, t.title) { app.settings.setReaderTheme(t.name) }
                }
            }

            Section("Резервні копії") {
                NavRow(Icons.Filled.Backup, "Створити або відновити", "Відкривається в 7-Zip / WinRAR на Windows за паролем", onBackup)
            }

            Section("Про додаток") {
                Text("Мої документи ${BuildConfig.VERSION_NAME}")
                Text(
                    "Усі документи зберігаються лише на цьому телефоні у зашифрованому вигляді. " +
                        "Додаток не має доступу до інтернету й нічого нікуди не надсилає без вашої дії.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun swatch(p: Palette): Brush = when (p) {
    Palette.NAVY -> Brush.linearGradient(listOf(Color(0xFF07101F), Color(0xFF2F80FF)))
    Palette.DYNAMIC -> Brush.sweepGradient(listOf(Color(0xFFE57373), Color(0xFFFFD54F), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFE57373)))
    Palette.BLUE -> Brush.linearGradient(listOf(Color(0xFF1F4E79), Color(0xFF1F4E79)))
    Palette.GREEN -> Brush.linearGradient(listOf(Color(0xFF2E6B3A), Color(0xFF2E6B3A)))
    Palette.BURGUNDY -> Brush.linearGradient(listOf(Color(0xFF8C1D40), Color(0xFF8C1D40)))
    Palette.AMBER -> Brush.linearGradient(listOf(Color(0xFF8A5100), Color(0xFF8A5100)))
    Palette.VIOLET -> Brush.linearGradient(listOf(Color(0xFF6538A8), Color(0xFF6538A8)))
    Palette.GRAPHITE -> Brush.linearGradient(listOf(Color(0xFF3C4858), Color(0xFF3C4858)))
}
