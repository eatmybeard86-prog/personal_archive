package ua.personal.archive.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ua.personal.archive.ArchiveApp

val LocalApp = staticCompositionLocalOf<ArchiveApp> { error("no app") }

@Composable
fun AppRoot(app: ArchiveApp, activity: FragmentActivity) {
    val locked by app.lock.locked.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalApp provides app) {
        LaunchedEffect(locked) {
            if (locked) {
                clearThumbnails()
                // Паролі прибираємо з пам'яті, скопійований пароль — з буфера обміну.
                app.passwords.unload()
                clearCopiedSecrets(app)
            }
        }
        if (locked) LockScreen(activity) else {
            val decoy by app.lock.decoy.collectAsStateWithLifecycle()
            // Перемикаємо сховище ще до першого кадру, щоб дані іншого сховища не промайнули.
            remember(decoy) { app.repo.useVault(decoy); app.passwords.useVault(decoy); decoy }
            key(decoy) { Unlocked(app) }
            // Вхід тривожним PIN-ом: тихі фото фронтальною камерою одразу, через 10 і через 30 секунд.
            if (decoy) LaunchedEffect(Unit) {
                if (!ua.personal.archive.security.IntruderCamera.permitted(activity)) return@LaunchedEffect
                var last = 0L
                for (at in listOf(0L, 10_000L, 30_000L)) {
                    kotlinx.coroutines.delay(at - last); last = at
                    val p = ua.personal.archive.security.IntruderCamera.captureReliable(activity) ?: continue
                    app.accessLog.add(ua.personal.archive.security.AccessLog.Kind.DURESS, "фото через ${at / 1000} с після входу", p.first, p.second)
                }
            }
        }
    }
}

@Composable
private fun Unlocked(app: ArchiveApp) {
    run {
        LaunchedEffect(Unit) {
            // Після розблокування прибираємо тимчасові розшифровані копії, якими вже поділилися.
            app.exporter.clearTemp()
            app.repo.load()
            // Перевірка цілісності раз на тиждень, у фоні.
            val prefs = app.getSharedPreferences("integrity", android.content.Context.MODE_PRIVATE)
            if (System.currentTimeMillis() - prefs.getLong("last", 0) > 7 * 24 * 3600_000L) {
                app.repo.verifyIntegrity()
                prefs.edit().putLong("last", System.currentTimeMillis()).apply()
            }
            app.ocr.indexMissing()
        }
        val nav = rememberNavController()
        // Повернення назад ніколи не знімає головний екран (інакше лишається чорний порожній екран).
        val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
        val incoming by app.incoming.collectAsStateWithLifecycle()
        LaunchedEffect(incoming) {
            if (incoming.isEmpty()) return@LaunchedEffect
            // Захищена копія «.mydoc» відкривається окремо — у «Отримані копії», не в сховище.
            val first = incoming.first()
            val isCopy = incoming.size == 1 && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.copies.isPack(app, first) }
            if (isCopy) {
                app.incoming.value = emptyList()
                app.pendingCopy.value = first
                nav.navigate("copies")
            } else nav.navigate("edit?id=&cat=&shared=1")
        }
        NavHost(nav, startDestination = "home") {
            composable("home") {
                MainScreen(
                    onOpen = { nav.navigate("doc/$it") },
                    onAdd = { c -> nav.navigate("edit?id=&cat=${c.orEmpty()}") },
                    onBackup = { nav.navigate("backup") },
                    onSections = { nav.navigate("sections") },
                    onScan = { c -> nav.navigate("edit?id=&cat=$c&scan=1") },
                    onAccessLog = { nav.navigate("accesslog") },
                    onBundle = { nav.navigate("bundle") },
                    onCopies = { nav.navigate("copies") },
                    onFamily = { nav.navigate("family") },
                    onPasswords = { nav.navigate("passwords") },
                )
            }
            composable(
                "edit?id={id}&cat={cat}&shared={shared}&scan={scan}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("cat") { type = NavType.StringType; defaultValue = "" },
                    navArgument("shared") { type = NavType.StringType; defaultValue = "" },
                    navArgument("scan") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                val shared = remember {
                    if (it.arguments?.getString("shared") == "1") app.incoming.value.also { app.incoming.value = emptyList() } else emptyList()
                }
                EditScreen(
                    sharedUris = shared,
                    docId = it.arguments?.getString("id").orEmpty().ifBlank { null },
                    initialCategory = it.arguments?.getString("cat")?.ifBlank { null },
                    autoScan = it.arguments?.getString("scan") == "1",
                    onDone = { id ->
                        back()
                        if (id != null && nav.currentDestination?.route == "home") nav.navigate("doc/$id")
                    },
                    onBack = back,
                )
            }
            composable("doc/{id}") {
                val id = it.arguments?.getString("id").orEmpty()
                DetailScreen(
                    docId = id,
                    onBack = back,
                    onEdit = { nav.navigate("edit?id=$id") },
                    onView = { attId -> nav.navigate("view/$id/${Uri.encode(attId)}") },
                    onShow = { attId -> nav.navigate("show/$id/${Uri.encode(attId)}") },
                )
            }
            composable("view/{doc}/{att}") {
                ViewerScreen(
                    docId = it.arguments?.getString("doc").orEmpty(),
                    attachmentId = it.arguments?.getString("att").orEmpty(),
                    onBack = back,
                )
            }
            composable("show/{doc}/{att}") {
                ViewerScreen(
                    docId = it.arguments?.getString("doc").orEmpty(),
                    attachmentId = it.arguments?.getString("att").orEmpty(),
                    onBack = back,
                    show = true,
                )
            }
            composable("copies") { CopiesScreen(onBack = back, onOpen = { id -> nav.navigate("copyview/$id") }) }
            composable("copyview/{id}") { CopyViewerScreen(it.arguments?.getString("id").orEmpty(), onBack = back) }
            composable("family") { FamilyScreen(onBack = back) }
            // ── Паролі: кожен екран за окремою перевіркою обличчям ──
            composable("passwords") {
                PasswordGate(onBack = back) {
                    PasswordsScreen(
                        onBack = back,
                        onOpen = { nav.navigate("pw/$it") },
                        onAdd = { cat, scan -> nav.navigate("pwedit?id=&cat=${cat.orEmpty()}&scan=${if (scan) "1" else ""}") },
                    )
                }
            }
            composable("pw/{id}") {
                val id = it.arguments?.getString("id").orEmpty()
                PasswordGate(onBack = back) { PasswordDetailScreen(id, onBack = back, onEdit = { nav.navigate("pwedit?id=$id") }) }
            }
            composable(
                "pwedit?id={id}&cat={cat}&scan={scan}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("cat") { type = NavType.StringType; defaultValue = "" },
                    navArgument("scan") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                PasswordGate(onBack = back) {
                    PasswordEditScreen(
                        id = it.arguments?.getString("id").orEmpty().ifBlank { null },
                        initialCategory = it.arguments?.getString("cat")?.ifBlank { null },
                        autoScan = it.arguments?.getString("scan") == "1",
                        onDone = { newId ->
                            back()
                            if (newId != null) nav.navigate("pw/$newId")
                        },
                        onBack = back,
                    )
                }
            }
            composable("bundle") { BundleScreen(onBack = back) }
            composable("accesslog") { AccessLogScreen(onBack = back) }
            composable("backup") { BackupScreen(onBack = back) }
            composable("sections") { SectionsScreen(onBack = back) }
        }
    }
}
