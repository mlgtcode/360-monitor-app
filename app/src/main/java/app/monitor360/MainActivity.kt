package app.monitor360

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monitor360.ui.AppRoot
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.theme.Monitor360Theme
import app.monitor360.work.Notifier

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = Regex("https?://\\S+").find(text)?.value ?: text.trim().takeIf { it.isNotEmpty() && ' ' !in it && '.' in it }
        if (url != null) vm.sharedUrl.value = url
    }

    override fun onStart() {
        super.onStart()
        vm.setActive(true)
    }

    override fun onStop() {
        vm.setActive(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        if (Build.VERSION.SDK_INT >= 33 && vm.settings.current.notificationsEnabled && !Notifier.canNotify(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            Monitor360Theme(prefs) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AppRoot(vm) }
            }
        }
    }
}
