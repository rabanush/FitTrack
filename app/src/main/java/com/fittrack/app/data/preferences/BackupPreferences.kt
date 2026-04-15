package com.fittrack.app.data.preferences

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

private const val PREFS_NAME = "fittrack_backup"
private const val KEY_TREE_URI = "backup_tree_uri"

/**
 * Stores the SAF tree URI chosen by the user for the backup folder.
 * SharedPreferences survive app data clears that do NOT come with a full uninstall,
 * but the URI permission grant itself survives reinstalls as long as the file system
 * path is the same. The combination of persistable URI permission + this preference
 * ensures the backup folder is remembered across reinstalls.
 */
class BackupPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTreeUri(): Uri? = prefs.getString(KEY_TREE_URI, null)?.let { Uri.parse(it) }

    fun saveTreeUri(uri: Uri) {
        prefs.edit { putString(KEY_TREE_URI, uri.toString()) }
    }
}
