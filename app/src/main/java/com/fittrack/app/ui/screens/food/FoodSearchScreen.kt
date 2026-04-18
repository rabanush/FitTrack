package com.fittrack.app.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.network.OFFProduct
import com.fittrack.app.viewmodel.FoodSearchViewModel
import com.fittrack.app.viewmodel.SearchState
import kotlinx.coroutines.delay

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
    var selectedCustomFood by remember { mutableStateOf<CustomFood?>(null) }
    // When not null, shows the create-product dialog (barcode may be pre-filled)
    var createWithBarcode by remember { mutableStateOf<String?>(null) }
    val triggerSearch: () -> Unit = { viewModel.search(query) }

    // Auto-search: trigger 500 ms after the user stops typing so the search
    // button never needs to be pressed multiple times.
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            delay(500)
            viewModel.search(query)
        } else {
            viewModel.search("")   // resets to RecentlyUsed / Idle
        }
    }

    // Auto-show the create dialog when the barcode scan returns no result
    LaunchedEffect(searchState) {
        if (searchState is SearchState.BarcodeNotFound) {
            createWithBarcode = (searchState as SearchState.BarcodeNotFound).barcode
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lebensmittel suchen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { triggerSearch() },
                    onDone = { triggerSearch() },
                    onGo = { triggerSearch() },
                    onSend = { triggerSearch() }
                ),
                trailingIcon = {
                    IconButton(onClick = triggerSearch) {
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
                is SearchState.RecentlyUsed -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Text(
                                "Zuletzt verwendet",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(state.foods) { food ->
                            CustomFoodSearchCard(
                                food = food,
                                onClick = { selectedCustomFood = food }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { createWithBarcode = "" },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Eigenes Produkt erstellen")
                            }
                        }
                    }
                }
                is SearchState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is SearchState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { createWithBarcode = "" }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Eigenes Produkt erstellen")
                        }
                    }
                }
                is SearchState.BarcodeNotFound -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is SearchState.Success -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Previously logged foods matching the query (e.g. scanned "Natural SKyr")
                        if (state.recentFoods.isNotEmpty()) {
                            item {
                                Text(
                                    "Zuletzt verwendet",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(state.recentFoods) { food ->
                                CustomFoodSearchCard(
                                    food = food,
                                    onClick = { selectedCustomFood = food }
                                )
                            }
                        }
                        if (state.customFoods.isNotEmpty()) {
                            item {
                                Text(
                                    "Eigene Produkte",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(state.customFoods) { food ->
                                CustomFoodSearchCard(
                                    food = food,
                                    onClick = { selectedCustomFood = food }
                                )
                            }
                        }
                        if (state.products.isNotEmpty()) {
                            item {
                                Text(
                                    "OpenFoodFacts",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(state.products) { product ->
                                ProductSearchCard(
                                    product = product,
                                    onClick = { selectedProduct = product }
                                )
                            }
                        }
                        if (state.recentFoods.isEmpty() && state.customFoods.isEmpty() && state.products.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Keine Produkte gefunden")
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { createWithBarcode = "" }) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Eigenes Produkt erstellen")
                                    }
                                }
                            }
                        }
                        // Always offer "create" at the bottom of any result list
                        if (state.recentFoods.isNotEmpty() || state.customFoods.isNotEmpty() || state.products.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { createWithBarcode = "" },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Eigenes Produkt erstellen")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add OFf product to meal
    selectedProduct?.let { product ->
        AddProductDialog(
            product = product,
            onDismiss = { selectedProduct = null },
            onConfirm = { amount ->
                viewModel.addProductToMeal(product, mealId, amount)
                selectedProduct = null
                onBack()
            }
        )
    }

    // Add local custom food to meal
    selectedCustomFood?.let { food ->
        AddCustomFoodDialog(
            food = food,
            onDismiss = { selectedCustomFood = null },
            onConfirm = { amount ->
                viewModel.addCustomFoodToMeal(food, mealId, amount)
                selectedCustomFood = null
                onBack()
            }
        )
    }

    // Create a new custom product (barcode may be pre-filled from a failed scan)
    createWithBarcode?.let { prefilledBarcode ->
        CreateCustomFoodDialog(
            prefillBarcode = prefilledBarcode,
            onDismiss = {
                createWithBarcode = null
                viewModel.resetState()
            },
            onScanBarcode = {
                createWithBarcode = null
                viewModel.resetState()
                onScanBarcode()
            },
            onConfirm = { name, barcode, kcal, protein, carbs, fat, amount ->
                viewModel.createCustomFoodAndAddToMeal(
                    name, barcode, kcal, protein, carbs, fat, mealId, amount
                )
                createWithBarcode = null
                onBack()
            }
        )
    }
}

