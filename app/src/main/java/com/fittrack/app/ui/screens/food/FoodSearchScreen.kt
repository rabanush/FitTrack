package com.fittrack.app.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.network.OFFProduct
import com.fittrack.app.viewmodel.FoodSearchViewModel
import com.fittrack.app.viewmodel.SearchState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(
    viewModel: FoodSearchViewModel,
    mealId: Long,
    mealName: String,
    onBack: () -> Unit,
    onScanBarcode: () -> Unit
) {
    val searchState by viewModel.searchState.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<OFFProduct?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lebensmittel suchen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = onScanBarcode) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode scannen")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Mahlzeit: $mealName", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Produkt suchen…") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.search(query) }) {
                        Icon(Icons.Default.Search, contentDescription = "Suchen")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (val state = searchState) {
                is SearchState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Gib einen Produktnamen ein oder scanne einen Barcode")
                    }
                }
                is SearchState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is SearchState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is SearchState.Success -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.products) { product ->
                            ProductSearchCard(
                                product = product,
                                onClick = { selectedProduct = product }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedProduct != null) {
        AddProductDialog(
            product = selectedProduct!!,
            onDismiss = { selectedProduct = null },
            onConfirm = { amount ->
                viewModel.addProductToMeal(selectedProduct!!, mealId, amount)
                selectedProduct = null
                onBack()
            }
        )
    }
}

@Composable
private fun ProductSearchCard(product: OFFProduct, onClick: () -> Unit) {
    val n = product.nutriments
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
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
private fun MacroChip(text: String) {
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
private fun AddProductDialog(
    product: OFFProduct,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var amountText by remember { mutableStateOf("100") }
    val amount = amountText.toFloatOrNull() ?: 0f
    val n = product.nutriments

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Menge (g)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (n != null && amount > 0f) {
                    Text("Nährwerte für ${amount.toInt()} g:", style = MaterialTheme.typography.labelMedium)
                    Text("Kalorien: ${(n.kcalPer100g * amount / 100f).toInt()} kcal")
                    Text("Protein: ${"%.1f".format(n.proteins100g?.times(amount / 100f) ?: 0f)} g")
                    Text("Kohlenhydrate: ${"%.1f".format(n.carbohydrates100g?.times(amount / 100f) ?: 0f)} g")
                    Text("Fett: ${"%.1f".format(n.fat100g?.times(amount / 100f) ?: 0f)} g")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (amount > 0f) onConfirm(amount) },
                enabled = amount > 0f
            ) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
