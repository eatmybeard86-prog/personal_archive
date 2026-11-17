package ua.personal.archive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
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
        if (savedInstanceState == null) receive(intent)
        setContent {
            val mode by app.settings.themeMode.collectAsStateWithLifecycle()
            val palette by app.settings.palette.collectAsStateWithLifecycle()
            ArchiveTheme(mode, palette) { AppRoot(app, this) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    /** «Поділитися» / «Відкрити за допомогою» з інших додатків: файли чекають розблокування. */
    private fun receive(intent: Intent?) {
        intent ?: return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }.filter { it.scheme == "content" || it.scheme == "file" }
        if (uris.isNotEmpty()) (application as ArchiveApp).incoming.value = uris
    }
}
