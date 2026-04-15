package com.fittrack.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.fittrack.app.ui.navigation.FitTrackNavGraph
import com.fittrack.app.ui.theme.FitTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as FitTrackApplication

        // On first launch (no backup folder selected yet) open the system folder picker
        // pre-pointed at the Documents directory so the user can confirm or navigate to
        // Documents/FitTrackerBackup. The resulting URI permission survives reinstalls.
        val backupFolderLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                app.backupPreferences.saveTreeUri(it)
            }
        }

        if (app.backupPreferences.getTreeUri() == null) {
            val initialUri = runCatching {
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Documents"
                )
            }.getOrNull()
            backupFolderLauncher.launch(initialUri)
        }

        setContent {
            FitTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    FitTrackNavGraph(navController = navController)
                }
            }
        }
    }
}
