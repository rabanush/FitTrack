package com.fittrack.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.preferences.ActivityLevel
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.data.preferences.UserProfile
import com.fittrack.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()
    val backupFolderUri by viewModel.backupFolderUri.collectAsState()

    var weightText by remember(profile.weightKg) { mutableStateOf(profile.weightKg.toString()) }
    var heightText by remember(profile.heightCm) { mutableStateOf(profile.heightCm.toString()) }
    var ageText by remember(profile.ageYears) { mutableStateOf(profile.ageYears.toString()) }
    var gender by remember(profile.gender) { mutableStateOf(profile.gender) }
    var activityLevel by remember(profile.activityLevel) { mutableStateOf(profile.activityLevel) }

    // Launcher for the system folder picker (ACTION_OPEN_DOCUMENT_TREE).
    // The result URI carries a persistable permission that survives app reinstalls.
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.saveBackupFolder(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Backup folder ──────────────────────────────────────────────────
            Text("Backup", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Backup-Ordner",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (backupFolderUri != null)
                            Uri.decode(backupFolderUri.toString())
                        else "Noch kein Ordner gewählt",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backupFolderUri != null)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(
                        onClick = {
                            // Hint the picker toward Documents/FitTrackerBackup.
                            val initialUri = runCatching {
                                DocumentsContract.buildDocumentUri(
                                    "com.android.externalstorage.documents",
                                    "primary:Documents"
                                )
                            }.getOrNull()
                            folderPickerLauncher.launch(initialUri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (backupFolderUri != null) "Backup-Ordner ändern" else "Backup-Ordner wählen")
                    }
                    Text(
                        "Die App legt eine Datei namens \"fittrack_workouts.json\" im Unterordner " +
                                "\"FitTrackerBackup\" des gewählten Ordners ab. Der Ordner bleibt " +
                                "nach einer Neuinstallation erhalten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Body data ──────────────────────────────────────────────────────
            Text("Körperdaten", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("Gewicht (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it },
                label = { Text("Größe (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ageText,
                onValueChange = { ageText = it },
                label = { Text("Alter (Jahre)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Geschlecht", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gender.entries.forEach { g ->
                    FilterChip(
                        selected = gender == g,
                        onClick = { gender = g },
                        label = { Text(if (g == Gender.MALE) "Männlich" else "Weiblich") }
                    )
                }
            }

            Text("Aktivitätsniveau", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActivityLevel.entries.forEach { level ->
                    FilterChip(
                        selected = activityLevel == level,
                        onClick = { activityLevel = level },
                        label = { Text(level.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val tdee = remember(weightText, heightText, ageText, gender, activityLevel) {
                UserProfile(
                    weightKg = weightText.toFloatOrNull() ?: profile.weightKg,
                    heightCm = heightText.toFloatOrNull() ?: profile.heightCm,
                    ageYears = ageText.toIntOrNull() ?: profile.ageYears,
                    gender = gender,
                    activityLevel = activityLevel
                ).tdee
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Geschätzter Kalorienbedarf", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${tdee.toInt()} kcal/Tag",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.save(
                        weightKg = weightText.toFloatOrNull() ?: profile.weightKg,
                        heightCm = heightText.toFloatOrNull() ?: profile.heightCm,
                        ageYears = ageText.toIntOrNull() ?: profile.ageYears,
                        gender = gender,
                        activityLevel = activityLevel
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Speichern")
            }
        }
    }
}
