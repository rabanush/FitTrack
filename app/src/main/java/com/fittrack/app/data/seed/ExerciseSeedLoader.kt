package com.fittrack.app.data.seed

import android.content.Context
import com.fittrack.app.data.model.Exercise
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private data class ExerciseSeedItem(
    val id: Long,
    val name: String,
    val muscleGroup: String,
    val germanName: String,
    val description: String = ""
)

object ExerciseSeedLoader {
    private val gson = Gson()

    fun loadFromAssets(context: Context): List<Exercise> {
        val json = context.assets.open("exercises_seed.json").bufferedReader().use { it.readText() }
        val listType = object : TypeToken<List<ExerciseSeedItem>>() {}.type
        val seedItems: List<ExerciseSeedItem> = gson.fromJson(json, listType)
        return seedItems.map { item ->
            Exercise(
                id = item.id,
                name = item.name,
                muscleGroup = item.muscleGroup,
                germanName = item.germanName,
                description = item.description
            )
        }
    }
}
