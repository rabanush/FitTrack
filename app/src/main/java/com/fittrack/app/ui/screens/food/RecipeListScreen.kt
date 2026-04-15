package com.fittrack.app.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.model.Recipe
import com.fittrack.app.data.model.RecipeItem
import com.fittrack.app.data.model.RecipeWithItems
import com.fittrack.app.ui.components.DeleteConfirmDialog
import com.fittrack.app.ui.components.NameInputDialog
import com.fittrack.app.viewmodel.RecipeBarcodeLookupState
import com.fittrack.app.viewmodel.RecipeViewModel

/**
 * Lists all saved recipes.
 *
 * When [selectMealId] is non-null the screen is in "select mode": each recipe
 * shows a "Zur Mahlzeit hinzufügen" button instead of editing controls, and
 * [onRecipeAdded] is called (then the caller navigates back).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    viewModel: RecipeViewModel,
    selectMealId: Long?,
    selectMealName: String,
    onBack: () -> Unit,
    onRecipeAdded: () -> Unit = {},
    onScanBarcode: ((Long) -> Unit)? = null
) {
    val recipes by viewModel.recipes.collectAsState()
    val barcodeLookupState by viewModel.barcodeLookupState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var barcodeAddForRecipeId by remember { mutableStateOf<Long?>(null) }
    var barcodePrefilledData by remember { mutableStateOf<RecipeBarcodeLookupState.Found?>(null) }

    LaunchedEffect(barcodeLookupState) {
        when (val state = barcodeLookupState) {
            is RecipeBarcodeLookupState.Found -> {
                barcodeAddForRecipeId = state.recipeId
                barcodePrefilledData = state
                viewModel.clearBarcodeLookupState()
            }
            is RecipeBarcodeLookupState.NotFound -> {
                barcodeAddForRecipeId = state.recipeId
                barcodePrefilledData = null
                viewModel.clearBarcodeLookupState()
            }
            is RecipeBarcodeLookupState.Idle -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectMealId != null) "Rezept wählen" else "Rezepte") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (selectMealId == null) {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Rezept erstellen")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (recipes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Noch keine Rezepte", style = MaterialTheme.typography.titleMedium)
                    if (selectMealId == null) {
                        Button(onClick = { showCreateDialog = true }) {
                            Text("Rezept erstellen")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recipes, key = { it.recipe.id }) { recipeWithItems ->
                    RecipeCard(
                        recipeWithItems = recipeWithItems,
                        selectMode = selectMealId != null,
                        onAddToMeal = {
                            viewModel.addRecipeToMeal(recipeWithItems, selectMealId!!)
                            onRecipeAdded()
                        },
                        onDelete = { viewModel.deleteRecipe(recipeWithItems.recipe) },
                        onAddItem = { name, kcal, protein, carbs, fat, amount ->
                            viewModel.addRecipeItem(
                                recipeWithItems.recipe.id,
                                name, kcal, protein, carbs, fat, amount
                            )
                        },
                        onDeleteItem = { viewModel.deleteRecipeItem(it) },
                        onScanBarcode = onScanBarcode?.let { callback ->
                            { callback(recipeWithItems.recipe.id) }
                        }
                    )
                }
            }
        }
    }

    if (barcodeAddForRecipeId != null) {
        AddRecipeItemDialog(
            initialName = barcodePrefilledData?.name ?: "",
            initialKcal = barcodePrefilledData?.kcalPer100 ?: 0f,
            initialProtein = barcodePrefilledData?.proteinPer100 ?: 0f,
            initialCarbs = barcodePrefilledData?.carbsPer100 ?: 0f,
            initialFat = barcodePrefilledData?.fatPer100 ?: 0f,
            onDismiss = { barcodeAddForRecipeId = null; barcodePrefilledData = null },
            onConfirm = { name, kcal, protein, carbs, fat, amount ->
                viewModel.addRecipeItem(barcodeAddForRecipeId!!, name, kcal, protein, carbs, fat, amount)
                barcodeAddForRecipeId = null
                barcodePrefilledData = null
            }
        )
    }

    if (showCreateDialog) {
        NameInputDialog(
            title = "Rezept erstellen",
            label = "Rezeptname",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createRecipe(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun RecipeCard(
    recipeWithItems: RecipeWithItems,
    selectMode: Boolean,
    onAddToMeal: () -> Unit,
    onDelete: () -> Unit,
    onAddItem: (String, Float, Float, Float, Float, Float) -> Unit,
    onDeleteItem: (RecipeItem) -> Unit,
    onScanBarcode: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(!selectMode) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        recipeWithItems.recipe.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${recipeWithItems.totalCalories.toInt()} kcal · " +
                            "${recipeWithItems.items.size} Zutaten",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (selectMode) {
                    Button(
                        onClick = onAddToMeal,
                        enabled = recipeWithItems.items.isNotEmpty()
                    ) { Text("Hinzufügen") }
                } else {
                    if (onScanBarcode != null) {
                        IconButton(onClick = onScanBarcode) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode scannen", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { showAddItemDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Zutat hinzufügen", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Einklappen" else "Ausklappen"
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Rezept löschen", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Items list (in select mode always show items; in manage mode only when expanded)
            if (expanded || selectMode) {
                if (recipeWithItems.items.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    recipeWithItems.items.forEach { item ->
                        RecipeItemRow(
                            item = item,
                            showDelete = !selectMode,
                            onDelete = { onDeleteItem(item) }
                        )
                    }
                }
                if (recipeWithItems.items.isEmpty() && !selectMode) {
                    Text(
                        "Noch keine Zutaten — tippe + um eine hinzuzufügen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            title = "Rezept löschen",
            message = "\"${recipeWithItems.recipe.name}\" und alle Zutaten löschen?",
            onConfirm = { onDelete(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showAddItemDialog) {
        AddRecipeItemDialog(
            onDismiss = { showAddItemDialog = false },
            onConfirm = { name, kcal, protein, carbs, fat, amount ->
                onAddItem(name, kcal, protein, carbs, fat, amount)
                showAddItemDialog = false
            }
        )
    }
}

@Composable
private fun RecipeItemRow(item: RecipeItem, showDelete: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${item.amount.toInt()} g · ${item.calories.toInt()} kcal · " +
                    "P:${"%.1f".format(item.protein)}g K:${"%.1f".format(item.carbs)}g F:${"%.1f".format(item.fat)}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Entfernen", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddRecipeItemDialog(
    initialName: String = "",
    initialKcal: Float = 0f,
    initialProtein: Float = 0f,
    initialCarbs: Float = 0f,
    initialFat: Float = 0f,
    onDismiss: () -> Unit,
    onConfirm: (String, Float, Float, Float, Float, Float) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var kcalText by remember { mutableStateOf(if (initialKcal > 0f) "%.1f".format(initialKcal) else "") }
    var proteinText by remember { mutableStateOf(if (initialProtein > 0f) "%.1f".format(initialProtein) else "") }
    var carbsText by remember { mutableStateOf(if (initialCarbs > 0f) "%.1f".format(initialCarbs) else "") }
    var fatText by remember { mutableStateOf(if (initialFat > 0f) "%.1f".format(initialFat) else "") }
    var amountText by remember { mutableStateOf("100") }

    val kcal = kcalText.toFloatOrNull() ?: 0f
    val protein = proteinText.toFloatOrNull() ?: 0f
    val carbs = carbsText.toFloatOrNull() ?: 0f
    val fat = fatText.toFloatOrNull() ?: 0f
    val amount = amountText.toFloatOrNull() ?: 0f
    val isValid = name.isNotBlank() && amount > 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zutat hinzufügen") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Zutatname *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Nährwerte pro 100 g", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = kcalText,
                        onValueChange = { kcalText = it },
                        label = { Text("kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it },
                        label = { Text("Protein g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = { carbsText = it },
                        label = { Text("Kohlenh. g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = { fatText = it },
                        label = { Text("Fett g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Menge (g) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) onConfirm(name.trim(), kcal, protein, carbs, fat, amount)
                },
                enabled = isValid
            ) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
