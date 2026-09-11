package com.mastercompanion

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import android.view.KeyEvent
import com.mastercompanion.data.spotify.SpotifyAuthManager
import com.mastercompanion.data.spotify.SpotifyRepository
import com.mastercompanion.ui.dashboard.DashboardHost
import com.mastercompanion.ui.theme.MasterCompanionTheme
import com.mastercompanion.ui.theme.PureBlack
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var spotifyAuthManager: SpotifyAuthManager

    @Inject
    lateinit var spotifyRepository: SpotifyRepository

    private var orientationListener: OrientationEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ═══ Default to Horizontal (Landscape) for Smart Desk Standby Companion ═══
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Dynamically allow vertical orientation if user holds phone upright
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // Held upright in portrait (330° - 360° or 0° - 30°)
                if (orientation in 330..360 || orientation in 0..30) {
                    if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                    }
                } else if (orientation in 60..120 || orientation in 240..300) {
                    // Landscape / Horizontal (resting on dock or held sideways)
                    if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
            }
        }
        if (orientationListener?.canDetectOrientation() == true) {
            orientationListener?.enable()
        }

        // Allow app to display and turn screen on when docked/charging
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Keep screen on while docked on desk
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Setup edge-to-edge immersive landscape view
        enableImmersiveMode()

        // Automatically start core companion foreground services
        startCoreServices()

        // Handle incoming intent (e.g. Spotify OAuth callback deep link)
        handleIntent(intent)

        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.lifecycle.compose.LocalLifecycleOwner provides this,
                androidx.compose.ui.platform.LocalLifecycleOwner provides this
            ) {
                MasterCompanionTheme {
                    var isSplashVisible by remember { mutableStateOf(true) }

                    androidx.compose.animation.Crossfade(
                        targetState = isSplashVisible,
                        animationSpec = androidx.compose.animation.core.tween(500),
                        label = "splash_crossfade"
                    ) { showSplash: Boolean ->
                        if (showSplash) {
                            com.mastercompanion.ui.common.SplashScreen(
                                onLoaded = { isSplashVisible = false }
                            )
                        } else {
                            com.mastercompanion.ui.dashboard.DashboardHost()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "mastercompanion" && uri.host == "spotify") {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")
            Timber.i("Spotify OAuth Callback received. code present: ${code != null}, error: $error")
            if (!code.isNullOrBlank()) {
                lifecycleScope.launch {
                    spotifyAuthManager.handleAuthCallback(code)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationListener?.disable()
        orientationListener = null
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                lifecycleScope.launch {
                    spotifyRepository.adjustVolume(5)
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                lifecycleScope.launch {
                    spotifyRepository.adjustVolume(-5)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startCoreServices() {
        try {
            val services = listOf(
                com.mastercompanion.service.BatteryGuardService::class.java,
                com.mastercompanion.service.CommandBridgeService::class.java,
                com.mastercompanion.service.AudioReceiverService::class.java
            )
            for (serviceClass in services) {
                val intent = Intent(this, serviceClass)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
            Timber.i("Core companion foreground services launched successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error launching core companion services")
        }
    }
}

