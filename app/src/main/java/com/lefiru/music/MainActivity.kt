package com.lefiru.music

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.lefiru.music.ui.LefiruScreen
import com.lefiru.music.ui.LibraryViewModel
import com.lefiru.music.ui.screens.IntroSplash
import com.lefiru.music.ui.theme.LefiruTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so keep light status/navigation icons even
        // when the phone itself is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        setContent {
            LefiruTheme {
                val context = LocalContext.current
                var permissionGranted by remember { mutableStateOf(hasAudioPermission(context)) }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    permissionGranted = hasAudioPermission(context)
                }

                val viewModel: LibraryViewModel = viewModel()

                var introVisible by rememberSaveable { mutableStateOf(true) }

                // Ask for audio access once the intro has finished, so the
                // system dialog does not cover the animation.
                LaunchedEffect(introVisible, permissionGranted) {
                    if (!introVisible && !permissionGranted) {
                        launcher.launch(requiredPermissions())
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LefiruScreen(
                        viewModel = viewModel,
                        permissionGranted = permissionGranted,
                        onRequestPermission = { launcher.launch(requiredPermissions()) },
                    )

                    if (introVisible) {
                        IntroSplash(onFinished = { introVisible = false })
                    }
                }
            }
        }
    }
}

private fun requiredPermissions(): Array<String> =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        // Android 10+ deletes media through the system consent dialog, so only
        // read access is needed.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }

private fun hasAudioPermission(context: Context): Boolean {
    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (ContextCompat.checkSelfPermission(context, readPermission) != PackageManager.PERMISSION_GRANTED) {
        return false
    }
    // Only Android 8-9 needs the write permission to delete files directly.
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
