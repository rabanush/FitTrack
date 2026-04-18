package com.fittrack.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.provider.DocumentsContract
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.fittrack.app.ui.navigation.FitTrackNavGraph
import com.fittrack.app.ui.theme.FitTrackTheme
import com.fittrack.app.util.RestTimerNotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var resumeWorkoutId: Long? by mutableStateOf(null)

    // Registered as class properties – must be created before onCreate returns.
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Take a permanent read/write grant so the permission survives restarts.
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val app = application as FitTrackApplication
            lifecycleScope.launch {
                app.userPreferences.saveBackupFolderUri(uri.toString())
                // Run import now that the folder is known; data is only restored when the
                // DB is empty (fresh install / data-clear), so this is safe to call at any time.
                app.performImportAfterFolderSelected()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resumeWorkoutId = resolveResumeWorkoutId(intent)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // On first ever start (or after a reinstall that cleared preferences) ask the user to
        // pick a backup folder.  The picker opens pointing at the Documents folder as a
        // sensible default.  Once the user has been prompted we never show this automatically
        // again (the folder can be changed in Settings).
        val app = application as FitTrackApplication
        lifecycleScope.launch {
            val alreadyPrompted = app.userPreferences.hasPromptedBackupFolder.first()
            if (!alreadyPrompted) {
                app.userPreferences.markBackupFolderPrompted()
                val documentsHint = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Documents"
                )
                folderPickerLauncher.launch(documentsHint)
            }
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
