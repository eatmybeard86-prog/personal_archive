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
        LaunchedEffect(locked) { if (locked) clearThumbnails() }
        if (locked) LockScreen(activity) else {
            val decoy by app.lock.decoy.collectAsStateWithLifecycle()
            // Перемикаємо сховище ще до першого кадру, щоб дані іншого сховища не промайнули.
            remember(decoy) { app.repo.useVault(decoy); decoy }
            key(decoy) { Unlocked(app) }
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
        }
        val nav = rememberNavController()
        // Повернення назад ніколи не знімає головний екран (інакше лишається чорний порожній екран).
        val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
        val incoming by app.incoming.collectAsStateWithLifecycle()
        LaunchedEffect(incoming) {
            if (incoming.isNotEmpty()) nav.navigate("edit?id=&cat=&shared=1")
        }
        NavHost(nav, startDestination = "home") {
            composable("home") {
                MainScreen(
                    onOpen = { nav.navigate("doc/$it") },
                    onAdd = { c -> nav.navigate("edit?id=&cat=${c.orEmpty()}") },
                    onBackup = { nav.navigate("backup") },
                    onSections = { nav.navigate("sections") },
                    onScan = { c -> nav.navigate("edit?id=&cat=$c&scan=1") },
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
                )
            }
            composable("view/{doc}/{att}") {
                ViewerScreen(
                    docId = it.arguments?.getString("doc").orEmpty(),
                    attachmentId = it.arguments?.getString("att").orEmpty(),
                    onBack = back,
                )
            }
            composable("backup") { BackupScreen(onBack = back) }
            composable("sections") { SectionsScreen(onBack = back) }
        }
    }
}
