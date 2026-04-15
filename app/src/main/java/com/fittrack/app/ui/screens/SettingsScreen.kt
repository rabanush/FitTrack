package com.fittrack.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.preferences.ActivityLevel
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()

    var weightText by remember(profile.weightKg) { mutableStateOf(profile.weightKg.toString()) }
    var heightText by remember(profile.heightCm) { mutableStateOf(profile.heightCm.toString()) }
    var ageText by remember(profile.ageYears) { mutableStateOf(profile.ageYears.toString()) }
    var gender by remember(profile.gender) { mutableStateOf(profile.gender) }
    var activityLevel by remember(profile.activityLevel) { mutableStateOf(profile.activityLevel) }

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
                val w = weightText.toFloatOrNull() ?: profile.weightKg
                val h = heightText.toFloatOrNull() ?: profile.heightCm
                val a = ageText.toIntOrNull() ?: profile.ageYears
                val base = 10f * w + 6.25f * h - 5f * a
                val bmr = if (gender == Gender.MALE) base + 5f else base - 161f
                bmr * activityLevel.multiplier
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
