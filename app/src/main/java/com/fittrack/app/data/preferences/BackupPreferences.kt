package com.fittrack.app.data.preferences

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

private const val PREFS_NAME = "fittrack_backup"
private const val KEY_TREE_URI = "backup_tree_uri"

/**
 * Stores the SAF tree URI chosen by the user for the backup folder.
 * SharedPreferences are cleared on a full uninstall, but the system-level persistable
 * URI permission grant can survive reinstalls on many devices (Android 11+). MainActivity
 * checks ContentResolver.persistedUriPermissions on startup and restores the URI here
 * if the SharedPreferences entry is missing, so the user does not need to re-select the
 * folder after reinstalling the app.
 */
class BackupPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTreeUri(): Uri? = prefs.getString(KEY_TREE_URI, null)?.let { Uri.parse(it) }

    fun saveTreeUri(uri: Uri) {
        prefs.edit { putString(KEY_TREE_URI, uri.toString()) }
    }
}
