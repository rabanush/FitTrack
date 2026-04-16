package com.fittrack.app.data.preferences

import android.content.Context
import android.net.Uri

class BackupPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBackupTreeUri(): Uri? = prefs.getString(KEY_BACKUP_TREE_URI, null)?.let(Uri::parse)

    fun saveBackupTreeUri(uri: Uri) {
        prefs.edit().putString(KEY_BACKUP_TREE_URI, uri.toString()).apply()
    }

    fun hasShownInitialBackupPicker(): Boolean =
        prefs.getBoolean(KEY_INITIAL_BACKUP_PICKER_SHOWN, false)

    fun markInitialBackupPickerShown() {
        prefs.edit().putBoolean(KEY_INITIAL_BACKUP_PICKER_SHOWN, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "backup_preferences"
        private const val KEY_BACKUP_TREE_URI = "backup_tree_uri"
        private const val KEY_INITIAL_BACKUP_PICKER_SHOWN = "initial_backup_picker_shown"
    }
}
