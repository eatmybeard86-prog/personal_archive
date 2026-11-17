package ua.personal.archive.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Головна", Icons.Filled.Home),
    FOLDERS("Папки", Icons.Filled.Folder),
    SECURITY("Безпека", Icons.Filled.Security),
    MORE("Більше", Icons.Filled.Menu),
}

/** Головний екран з нижнім меню, як на макеті. */
@Composable
fun MainScreen(
    onOpen: (String) -> Unit,
    onAdd: (String?) -> Unit,
    onBackup: () -> Unit,
    onSections: () -> Unit,
    onScan: (String) -> Unit,
    onAccessLog: () -> Unit = {},
    onBundle: () -> Unit = {},
    onCopies: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var folderCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    var pickScanSection by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = cs.background,
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.FOLDERS) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Сканер: спершу розділ (якщо відкрито розділ — одразу в нього), потім камера.
                SmallFloatingActionButton(
                    onClick = {
                        val c = if (tab == Tab.FOLDERS) folderCategory else null
                        if (c != null) onScan(c) else pickScanSection = true
                    },
                    containerColor = cs.secondaryContainer,
                    contentColor = cs.onSecondaryContainer,
                ) { Icon(Icons.Outlined.DocumentScanner, "Сканувати документ") }
                Spacer(Modifier.height(12.dp))
                FloatingActionButton(
                    onClick = { onAdd(if (tab == Tab.FOLDERS) folderCategory else null) },
                    shape = CircleShape,
                    containerColor = cs.primary,
                    contentColor = Color.White,
                    modifier = Modifier.size(64.dp),
                ) { Icon(Icons.Filled.Add, "Додати документ", Modifier.size(32.dp)) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = cs.surfaceContainerLow) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = cs.primary,
                            selectedTextColor = cs.primary,
                            indicatorColor = cs.primary.copy(alpha = 0.14f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding()).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeTab(
                    onOpen = onOpen,
                    onCategory = { folderCategory = it.id; tab = Tab.FOLDERS },
                    onManageSections = onSections,
                    onAll = { folderCategory = null; tab = Tab.FOLDERS },
                    onSecurity = { tab = Tab.SECURITY },
                    onSettings = { tab = Tab.MORE },
                )
                Tab.FOLDERS -> FoldersTab(folderCategory, { folderCategory = it }, onOpen, onSections)
                Tab.SECURITY -> SecurityTab(onBackup, onAccessLog)
                Tab.MORE -> MoreTab(onBackup, onReceive = { receiving = true }, onBundle = onBundle, onCopies = onCopies)
            }
        }
    }
    if (receiving) ReceiveDialog(onClose = { receiving = false }, onOpen = onOpen)

    if (pickScanSection) {
        val sections = rememberSections()
        AlertDialog(
            onDismissRequest = { pickScanSection = false },
            title = { Text("У який розділ сканувати?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    sections.forEach { s ->
                        TextButton(onClick = { pickScanSection = false; onScan(s.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(s.title, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickScanSection = false }) { Text("Скасувати") } },
        )
    }
}
