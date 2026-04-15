package com.fittrack.app.data.network

import com.google.gson.annotations.SerializedName

data class OpenFoodFactsSearchResponse(
    val count: Int = 0,
    val products: List<OFFProduct> = emptyList()
)

data class OpenFoodFactsProductResponse(
    val status: Int = 0,
    val product: OFFProduct? = null
)

data class OFFProduct(
    val code: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("product_name_de") val productNameDe: String? = null,
    val nutriments: OFFNutriments? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    val brands: String? = null,
    val quantity: String? = null
) {
    val displayName: String
        get() = productNameDe?.takeIf { it.isNotBlank() }
            ?: productName?.takeIf { it.isNotBlank() }
            ?: "Unbekanntes Produkt"
}

data class OFFNutriments(
    @SerializedName("energy-kcal_100g") val energyKcal100g: Float? = null,
    @SerializedName("energy_100g") val energyKj100g: Float? = null,
    @SerializedName("proteins_100g") val proteins100g: Float? = null,
    @SerializedName("carbohydrates_100g") val carbohydrates100g: Float? = null,
    @SerializedName("fat_100g") val fat100g: Float? = null
) {
    val kcalPer100g: Float
        get() = energyKcal100g
            ?: energyKj100g?.let { it / 4.184f }
            ?: 0f
}
