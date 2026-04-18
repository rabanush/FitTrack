package com.fittrack.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.fittrack.app.ui.navigation.FitTrackNavGraph
import com.fittrack.app.ui.theme.FitTrackTheme
import com.fittrack.app.util.RestTimerNotificationHelper

class MainActivity : ComponentActivity() {
    private var resumeWorkoutId: Long? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resumeWorkoutId = resolveResumeWorkoutId(intent)

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* no-op */ }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            FitTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    FitTrackNavGraph(
                        navController = navController,
                        initialWorkoutId = resumeWorkoutId,
                        onInitialWorkoutHandled = { resumeWorkoutId = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resumeWorkoutId = resolveResumeWorkoutId(intent)
    }

    private fun resolveResumeWorkoutId(intent: Intent?): Long? {
        val app = application as FitTrackApplication
        val fromNotification = intent
            ?.getLongExtra(RestTimerNotificationHelper.EXTRA_WORKOUT_ID, -1L)
            ?.takeIf { it > 0L }
        return fromNotification ?: app.activeWorkoutSessionPreferences.getSession()?.workoutId
    }
}
