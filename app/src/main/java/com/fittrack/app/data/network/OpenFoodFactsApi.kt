package com.fittrack.app.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    @GET("cgi/search.pl")
    suspend fun searchProducts(
        @Query("search_terms") query: String,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("lc") language: String = "de",
        @Query("fields") fields: String = "code,product_name,product_name_de,nutriments,image_url,brands,quantity"
    ): OpenFoodFactsSearchResponse

    @GET("api/v0/product/{barcode}.json")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "code,product_name,product_name_de,nutriments,image_url,brands,quantity"
    ): OpenFoodFactsProductResponse
}
