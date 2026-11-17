package ua.personal.archive.ui

import androidx.compose.foundation.layout.Box
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
import ua.personal.archive.data.Category

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
    onAdd: (Category?) -> Unit,
    onBackup: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var folderCategory by rememberSaveable { mutableStateOf<Category?>(null) }
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.FOLDERS) {
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
                    onCategory = { folderCategory = it; tab = Tab.FOLDERS },
                    onAll = { folderCategory = null; tab = Tab.FOLDERS },
                    onSecurity = { tab = Tab.SECURITY },
                    onSettings = { tab = Tab.MORE },
                )
                Tab.FOLDERS -> FoldersTab(folderCategory, { folderCategory = it }, onOpen)
                Tab.SECURITY -> SecurityTab(onBackup)
                Tab.MORE -> MoreTab(onBackup)
            }
        }
    }
}
