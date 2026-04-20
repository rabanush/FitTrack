package com.fittrack.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.preferences.ActivityLevel
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.data.preferences.UserProfile
import com.fittrack.app.util.normalizeHueDegrees
import com.fittrack.app.viewmodel.SettingsViewModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
    var timerVolumePercent by remember(profile.timerVolumePercent) { mutableIntStateOf(profile.timerVolumePercent) }
    var themeHueDegrees by remember(profile.themeHueDegrees) { mutableFloatStateOf(profile.themeHueDegrees) }

    fun saveAndBack() {
        viewModel.save(
            weightKg = weightText.toFloatOrNull() ?: profile.weightKg,
            heightCm = heightText.toFloatOrNull() ?: profile.heightCm,
            ageYears = ageText.toIntOrNull() ?: profile.ageYears,
            gender = gender,
            activityLevel = activityLevel,
            timerVolumePercent = timerVolumePercent,
            themeHueDegrees = themeHueDegrees
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("App-Designfarbe", style = MaterialTheme.typography.titleSmall)
                    Text("Hue: ${themeHueDegrees.toInt()}°", style = MaterialTheme.typography.bodyMedium)
                    ThemeHueWheel(
                        hue = themeHueDegrees,
                        onHueChange = { themeHueDegrees = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeHueWheel(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val wheelColors = remember {
        listOf(
            0f, 60f, 120f, 180f, 240f, 300f, 360f
        ).map { wheelHue -> Color.hsv(wheelHue, 1f, 1f) }
    }
    val indicatorColor = remember(hue) { Color.hsv(normalizeHueDegrees(hue), 1f, 1f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onHueChange(offsetToHue(down.position, canvasSize))
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            onHueChange(offsetToHue(change.position, canvasSize))
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        canvasSize = IntSize(size.width.toInt(), size.height.toInt())
        val diameter = size.minDimension
        val strokeWidth = diameter * 0.17f
        val radius = diameter / 2f - strokeWidth / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.sweepGradient(wheelColors, center = center),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        val angleRadians = ((normalizeHueDegrees(hue) - 90f) * PI / 180f).toFloat()
        val indicatorCenter = Offset(
            x = center.x + cos(angleRadians) * radius,
            y = center.y + sin(angleRadians) * radius
        )
        drawCircle(
            color = Color.White,
            radius = strokeWidth * 0.36f,
            center = indicatorCenter
        )
        drawCircle(
            color = indicatorColor,
            radius = strokeWidth * 0.24f,
            center = indicatorCenter
        )
    }
}

private fun offsetToHue(offset: Offset, size: IntSize): Float {
    if (size.width == 0 || size.height == 0) return 0f
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val dx = offset.x - centerX
    val dy = offset.y - centerY
    val angle = (atan2(dy, dx) * 180f / PI.toFloat()) + 90f
    return normalizeHueDegrees(angle)
}
