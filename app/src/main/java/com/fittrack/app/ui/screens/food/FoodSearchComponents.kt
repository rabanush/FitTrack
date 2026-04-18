package com.fittrack.app.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.network.OFFProduct

@Composable
internal fun CustomFoodSearchCard(food: CustomFood, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(food.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            food.barcode?.let { Text("Barcode: $it", style = MaterialTheme.typography.bodySmall) }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroChip("${food.caloriesPer100.toInt()} kcal")
                MacroChip("P: ${food.proteinPer100.toInt()}g")
                MacroChip("K: ${food.carbsPer100.toInt()}g")
                MacroChip("F: ${food.fatPer100.toInt()}g")
            }
        }
    }
}

@Composable
internal fun ProductSearchCard(product: OFFProduct, onClick: () -> Unit) {
    val n = product.nutriments
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(product.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            product.brands?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (n != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MacroChip("${n.kcalPer100g.toInt()} kcal")
                    MacroChip("P: ${n.proteins100g?.toInt() ?: 0}g")
                    MacroChip("K: ${n.carbohydrates100g?.toInt() ?: 0}g")
                    MacroChip("F: ${n.fat100g?.toInt() ?: 0}g")
                }
            }
        }
    }
}

@Composable
internal fun MacroChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
internal fun AddCustomFoodDialog(
    food: CustomFood,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var amountText by remember { mutableStateOf("100") }
    val amount = amountText.toFloatOrNull() ?: 0f
    val ratio = if (amount > 0f) amount / 100f else 0f

    AmountInputDialog(
        title = food.name,
        amountText = amountText,
        onAmountChange = { amountText = it },
        onDismiss = onDismiss,
        onConfirm = { onConfirm(amount) },
        isValid = amount > 0f
    ) {
        if (amount > 0f) {
            NutritionPreview(
                amountG = amount,
                kcal = food.caloriesPer100 * ratio,
                protein = food.proteinPer100 * ratio,
                carbs = food.carbsPer100 * ratio,
                fat = food.fatPer100 * ratio
            )
        }
    }
}

@Composable
internal fun AddProductDialog(
    product: OFFProduct,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var amountText by remember { mutableStateOf("100") }
    val amount = amountText.toFloatOrNull() ?: 0f
    val n = product.nutriments

    AmountInputDialog(
        title = product.displayName,
        amountText = amountText,
        onAmountChange = { amountText = it },
        onDismiss = onDismiss,
        onConfirm = { onConfirm(amount) },
        isValid = amount > 0f
    ) {
        if (n != null && amount > 0f) {
            val ratio = amount / 100f
            NutritionPreview(
                amountG = amount,
                kcal = n.kcalPer100g * ratio,
                protein = n.proteins100g?.times(ratio) ?: 0f,
                carbs = n.carbohydrates100g?.times(ratio) ?: 0f,
                fat = n.fat100g?.times(ratio) ?: 0f
            )
        }
    }
}

/** Shared dialog shell used by [AddCustomFoodDialog] and [AddProductDialog]. */
@Composable
internal fun AmountInputDialog(
    title: String,
    amountText: String,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isValid: Boolean,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Menge (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                extraContent()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = isValid) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/** Displays pre-calculated nutrition values for a given gram amount. */
@Composable
internal fun NutritionPreview(amountG: Float, kcal: Float, protein: Float, carbs: Float, fat: Float) {
    Text("Nährwerte für ${amountG.toInt()} g:", style = MaterialTheme.typography.labelMedium)
    Text("Kalorien: ${kcal.toInt()} kcal")
    Text("Protein: ${"%.1f".format(protein)} g")
    Text("Kohlenhydrate: ${"%.1f".format(carbs)} g")
    Text("Fett: ${"%.1f".format(fat)} g")
}

/**
 * Dialog for creating a new custom food product.
 * [prefillBarcode] is non-null and non-empty when launched from a failed barcode scan.
 */
@Composable
internal fun CreateCustomFoodDialog(
    prefillBarcode: String,
    onDismiss: () -> Unit,
    onScanBarcode: () -> Unit = {},
    onConfirm: (name: String, barcode: String?, kcal: Float, protein: Float, carbs: Float, fat: Float, amount: Float) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf(prefillBarcode) }
    var kcalText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var carbsText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("100") }

    val kcal = kcalText.toFloatOrNull() ?: 0f
    val protein = proteinText.toFloatOrNull() ?: 0f
    val carbs = carbsText.toFloatOrNull() ?: 0f
    val fat = fatText.toFloatOrNull() ?: 0f
    val amount = amountText.toFloatOrNull() ?: 0f
    val isValid = name.isNotBlank() && amount > 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eigenes Produkt erstellen") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Produktname *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode (optional)") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onScanBarcode) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode scannen")
                        }
                    },
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
                    if (isValid) onConfirm(
                        name.trim(),
                        barcode.trim().takeIf { it.isNotEmpty() },
                        kcal, protein, carbs, fat, amount
                    )
                },
                enabled = isValid
            ) { Text("Erstellen & Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
