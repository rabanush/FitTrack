package com.fittrack.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    onBack: () -> Unit,
    backupFolderUri: Uri? = null,
    onChangeBackupFolder: () -> Unit = {}
) {
    val profile by viewModel.userProfile.collectAsState()

    var weightText by remember(profile.weightKg) { mutableStateOf(profile.weightKg.toString()) }
    var heightText by remember(profile.heightCm) { mutableStateOf(profile.heightCm.toString()) }
    var ageText by remember(profile.ageYears) { mutableStateOf(profile.ageYears.toString()) }
    var gender by remember(profile.gender) { mutableStateOf(profile.gender) }
    var activityLevel by remember(profile.activityLevel) { mutableStateOf(profile.activityLevel) }
    var timerVolumePercent by remember(profile.timerVolumePercent) { mutableIntStateOf(profile.timerVolumePercent) }

    fun saveAndBack() {
        viewModel.save(
            weightKg = weightText.toFloatOrNull() ?: profile.weightKg,
            heightCm = heightText.toFloatOrNull() ?: profile.heightCm,
            ageYears = ageText.toIntOrNull() ?: profile.ageYears,
            gender = gender,
            activityLevel = activityLevel,
            timerVolumePercent = timerVolumePercent
        )
        onBack()
    }

    BackHandler { saveAndBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = ::saveAndBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Timer-Lautstärke", style = MaterialTheme.typography.titleSmall)
                    Text("$timerVolumePercent%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = timerVolumePercent.toFloat(),
                        onValueChange = { timerVolumePercent = it.toInt() },
                        valueRange = 0f..100f
                    )
                }
            }

            // ── Backup folder ───────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup-Ordner", style = MaterialTheme.typography.titleSmall)
                    if (backupFolderUri != null) {
                        Text(
                            text = backupFolderDisplayName(backupFolderUri),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Kein Ordner ausgewählt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedButton(
                        onClick = onChangeBackupFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Ordner ändern")
                    }
                }
            }
        }
    }
}

private fun backupFolderDisplayName(uri: Uri): String {
    val lastSegment = uri.lastPathSegment ?: return uri.toString()
    val decoded = Uri.decode(lastSegment)
    return decoded.substringAfter(':').ifBlank { decoded }
}
