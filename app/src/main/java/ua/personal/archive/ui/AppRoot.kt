package ua.personal.archive.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import ua.personal.archive.data.Category

val LocalApp = staticCompositionLocalOf<ArchiveApp> { error("no app") }

@Composable
fun AppRoot(app: ArchiveApp, activity: FragmentActivity) {
    val locked by app.lock.locked.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalApp provides app) {
        LaunchedEffect(locked) { if (locked) clearThumbnails() }
        if (locked) LockScreen(activity) else Unlocked(app)
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
        NavHost(nav, startDestination = "home") {
            composable("home") {
                MainScreen(
                    onOpen = { nav.navigate("doc/$it") },
                    onAdd = { c -> nav.navigate("edit?id=&cat=${c?.name.orEmpty()}") },
                    onBackup = { nav.navigate("backup") },
                )
            }
            composable(
                "edit?id={id}&cat={cat}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("cat") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                EditScreen(
                    docId = it.arguments?.getString("id").orEmpty().ifBlank { null },
                    initialCategory = it.arguments?.getString("cat")?.let { c -> Category.entries.firstOrNull { e -> e.name == c } },
                    onDone = { id ->
                        nav.popBackStack()
                        if (id != null && nav.currentDestination?.route == "home") nav.navigate("doc/$id")
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("doc/{id}") {
                val id = it.arguments?.getString("id").orEmpty()
                DetailScreen(
                    docId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("edit?id=$id") },
                    onView = { attId -> nav.navigate("view/$id/${Uri.encode(attId)}") },
                )
            }
            composable("view/{doc}/{att}") {
                ViewerScreen(
                    docId = it.arguments?.getString("doc").orEmpty(),
                    attachmentId = it.arguments?.getString("att").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
            composable("backup") { BackupScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
