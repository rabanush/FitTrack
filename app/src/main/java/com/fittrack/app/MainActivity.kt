package com.fittrack.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.navigation.compose.rememberNavController
import com.fittrack.app.ui.navigation.FitTrackNavGraph
import com.fittrack.app.ui.theme.FitTrackTheme
import com.fittrack.app.util.RestTimerNotificationHelper

class MainActivity : ComponentActivity() {
    private var resumeWorkoutId: Long? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as FitTrackApplication
        resumeWorkoutId = resolveResumeWorkoutId(intent)

        val backupFolderLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                app.backupPreferences.saveTreeUri(it)
                // The database may already be open (onOpen fired with a null URI before
                // the user picked the folder), so trigger an explicit import now.
                app.importBackupNow()
            }
        }

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* no-op */ }

        // If SharedPreferences was cleared by a reinstall, try to recover the backup
        // folder URI from the system-level persistable permission grants, which can
        // survive reinstalls on many devices.
        // Only consider tree URIs from the external-storage document provider, since
        // that is the only authority the app ever grants SAF access to.
        if (app.backupPreferences.getTreeUri() == null) {
            val restoredUri = contentResolver.persistedUriPermissions
                .firstOrNull {
                    it.isReadPermission && it.isWritePermission &&
                        it.uri.authority == "com.android.externalstorage.documents"
                }
                ?.uri
            if (restoredUri != null) {
                app.backupPreferences.saveTreeUri(restoredUri)
            }
        }

        // Only prompt the user to pick a backup folder if none is configured yet.
        if (app.backupPreferences.getTreeUri() == null && savedInstanceState == null) {
            val initialUri = runCatching {
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Documents"
                )
            }.getOrNull()
            backupFolderLauncher.launch(initialUri)
        }

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
