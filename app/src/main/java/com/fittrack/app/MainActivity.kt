package com.fittrack.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
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
    private var backupFolderUri: Uri? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as FitTrackApplication
        resumeWorkoutId = resolveResumeWorkoutId(intent)
        backupFolderUri = app.backupPreferences.getBackupTreeUri()

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* no-op */ }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val backupFolderLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
                app.backupPreferences.saveBackupTreeUri(uri)
                backupFolderUri = uri
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
                        onInitialWorkoutHandled = { resumeWorkoutId = null },
                        backupFolderUri = backupFolderUri,
                        onChangeBackupFolder = { backupFolderLauncher.launch(getDocumentsInitialUri()) }
                    )
                }
            }
        }

        if (app.backupPreferences.getBackupTreeUri() == null) {
            window.decorView.doOnPreDraw {
                backupFolderLauncher.launch(getDocumentsInitialUri())
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

    private fun getDocumentsInitialUri(): Uri? {
        val baseTreeUri = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val storageManager = getSystemService(StorageManager::class.java)
                storageManager?.primaryStorageVolume
                    ?.createOpenDocumentTreeIntent()
                    ?.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI)
            } else null
        }.getOrNull() ?: return null

        val treeDocumentId = DocumentsContract.getTreeDocumentId(baseTreeUri)
        if (!treeDocumentId.contains(":")) return null
        val volumeId = treeDocumentId.substringBefore(':').ifBlank { return null }
        val documentsDocumentId = "$volumeId:${Environment.DIRECTORY_DOCUMENTS}"
        return DocumentsContract.buildDocumentUriUsingTree(baseTreeUri, documentsDocumentId)
    }
}
