package no.mwmai.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import no.mwmai.music.ui.MusicApp
import no.mwmai.music.ui.theme.MwmMusicTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun canReadAudio(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark bars on every Android version: the default gives three-button
        // navigation a light scrim and the status bar dark icons on a dark deck.
        val bar = SystemBarStyle.dark(0xFF1C2027.toInt())
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
        if (savedInstanceState == null) playFrom(intent)
        setContent {
            MwmMusicTheme {
                var granted by remember { mutableStateOf(canReadAudio()) }
                val ask = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    granted = canReadAudio()
                    if (granted) vm.refreshLibrary()
                }
                // Coming back from a file manager or from Settings: pick up new
                // files and a permission that was granted there.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    granted = canReadAudio()
                    if (granted) vm.refreshLibrary()
                }
                MusicApp(
                    vm = vm,
                    hasPermission = granted,
                    onAskPermission = {
                        // Notifications carry the lock-screen controls on Android 13+.
                        val wanted = buildList {
                            add(audioPermission)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        ask.launch(wanted.toTypedArray())
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        playFrom(intent)
    }

    /** "Open with MWM Music" from a file manager. */
    private fun playFrom(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        vm.openExternal(uri)
    }
}
