package com.fittrack.app.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.viewmodel.FoodTrackerViewModel
import com.fittrack.app.viewmodel.MealWithEntries

private val ExpandedMealIdsStateSaver: Saver<Set<Long>, Any> = Saver(
    save = { it.toList() },
    restore = { (it as List<*>).filterIsInstance<Long>().toSet() }
)

/**
 * Content of the Food Tracker tab. Does NOT include a Scaffold — it is
 * designed to be embedded inside another Scaffold (the WorkoutListScreen pager).
 */
@Composable
fun FoodTrackerScreen(
    viewModel: FoodTrackerViewModel,
    sessionKey: Int,
    pendingExpandMealId: Long?,
    onPendingExpandHandled: (Long) -> Unit,
    onAddFood: (mealId: Long, mealName: String) -> Unit,
    onAddRecipeToMeal: (mealId: Long, mealName: String) -> Unit
) {
    val mealsWithEntries by viewModel.mealsWithEntries.collectAsState()
    val dailyConsumed by viewModel.dailyConsumed.collectAsState()
    val totalBurned by viewModel.totalBurnedToday.collectAsState()
    val netCalories by viewModel.netCalories.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    var expandedMealIds by rememberSaveable(sessionKey, stateSaver = ExpandedMealIdsStateSaver) {
        mutableStateOf(emptySet<Long>())
    }

    LaunchedEffect(mealsWithEntries, sessionKey, pendingExpandMealId) {
        val existingMealIds = mealsWithEntries.map { it.meal.id }.toSet()
        expandedMealIds = expandedMealIds.filterTo(mutableSetOf()) { it in existingMealIds }
        if (pendingExpandMealId != null && pendingExpandMealId in existingMealIds) {
            expandedMealIds = expandedMealIds + pendingExpandMealId
            onPendingExpandHandled(pendingExpandMealId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CalorieSummaryCard(
                    target = userProfile.tdee,
                    consumed = dailyConsumed.calories,
                    burned = totalBurned,
                    net = netCalories,
                    protein = dailyConsumed.protein,
                    carbs = dailyConsumed.carbs,
                    fat = dailyConsumed.fat
                )
            }

            if (mealsWithEntries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Restaurant,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Noch keine Mahlzeiten", style = MaterialTheme.typography.titleMedium)
                            Text("Standardmahlzeiten werden erstellt …", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                items(mealsWithEntries) { mealWithEntries ->
                    MealCard(
                        mealWithEntries = mealWithEntries,
                        expanded = mealWithEntries.meal.id in expandedMealIds,
                        onToggleExpanded = {
                            expandedMealIds = if (it in expandedMealIds) {
                                expandedMealIds - it
                            } else {
                                expandedMealIds + it
                            }
                        },
                        onAddFood = { onAddFood(mealWithEntries.meal.id, mealWithEntries.meal.name) },
                        onAddRecipe = { onAddRecipeToMeal(mealWithEntries.meal.id, mealWithEntries.meal.name) },
                        onDeleteEntry = { viewModel.deleteFoodEntry(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalorieSummaryCard(
    target: Float,
    consumed: Float,
    burned: Float,
    net: Float,
    protein: Float,
    carbs: Float,
    fat: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tagesübersicht", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CalorieItem("Ziel", "${target.toInt()} kcal")
                CalorieItem("Konsumiert", "${consumed.toInt()} kcal")
                CalorieItem("Verbrannt", "${burned.toInt()} kcal")
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Verbleibend", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${net.toInt()} kcal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            LinearProgressIndicator(
                progress = { (consumed / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroItem("Protein", protein)
                MacroItem("Kohlenhydrate", carbs)
                MacroItem("Fett", fat)
            }
        }
    }
}

@Composable
private fun CalorieItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MacroItem(label: String, grams: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${"%.1f".format(grams)} g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MealCard(
    mealWithEntries: MealWithEntries,
    expanded: Boolean,
    onToggleExpanded: (Long) -> Unit,
    onAddFood: () -> Unit,
    onAddRecipe: () -> Unit,
    onDeleteEntry: (FoodEntry) -> Unit
) {
    val totalKcal = mealWithEntries.entries.sumOf { it.calories.toDouble() }.toFloat()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(mealWithEntries.meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${totalKcal.toInt()} kcal", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onAddFood) {
                    Icon(Icons.Default.Add, contentDescription = "Lebensmittel hinzufügen", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onAddRecipe) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Rezept hinzufügen", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = { onToggleExpanded(mealWithEntries.meal.id) }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Einklappen" else "Ausklappen"
                    )
                }
            }

            if (expanded && mealWithEntries.entries.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                mealWithEntries.entries.forEach { entry ->
                    FoodEntryRow(entry = entry, onDelete = { onDeleteEntry(entry) })
                }
            }
        }
    }
}

@Composable
private fun FoodEntryRow(entry: FoodEntry, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${entry.amount.toInt()} g · ${entry.calories.toInt()} kcal · P:${"%.1f".format(entry.protein)}g K:${"%.1f".format(entry.carbs)}g F:${"%.1f".format(entry.fat)}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Entfernen", modifier = Modifier.size(16.dp))
        }
    }
}
