package ua.personal.archive

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.personal.archive.ui.AppRoot
import ua.personal.archive.ui.ArchiveTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Заборона скріншотів, запису екрана та показу вмісту в списку останніх додатків.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val app = application as ArchiveApp
        setContent {
            val mode by app.settings.themeMode.collectAsStateWithLifecycle()
            val palette by app.settings.palette.collectAsStateWithLifecycle()
            ArchiveTheme(mode, palette) { AppRoot(app, this) }
        }
    }
}
