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

                // The intro plays on the first composition while the main screen
                // is already built underneath, so revealing it is instantaneous.
                var introVisible by rememberSaveable { mutableStateOf(true) }
                var permissionRequested by rememberSaveable { mutableStateOf(false) }

                // Ask for the audio permission only once the intro has finished,
                // so the system dialog does not cover the animation.
                LaunchedEffect(introVisible, permissionGranted) {
                    if (!introVisible && !permissionGranted && !permissionRequested) {
                        permissionRequested = true
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasAudioPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
