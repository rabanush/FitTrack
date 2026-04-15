package com.fittrack.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fittrack.app.FitTrackApplication
import com.fittrack.app.ui.screens.*
import com.fittrack.app.ui.screens.food.BarcodeScannerScreen
import com.fittrack.app.ui.screens.food.FoodSearchScreen
import com.fittrack.app.viewmodel.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext

sealed class Screen(val route: String) {
    object WorkoutList : Screen("workout_list")
    object WorkoutDetail : Screen("workout_detail/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_detail/$workoutId"
    }
    object ActiveWorkout : Screen("active_workout/{workoutId}") {
        fun createRoute(workoutId: Long) = "active_workout/$workoutId"
    }
    object ExerciseList : Screen("exercise_list")
    object History : Screen("history")
    object FoodTracker : Screen("food_tracker")
    object FoodSearch : Screen("food_search/{mealId}/{mealName}") {
        fun createRoute(mealId: Long, mealName: String) =
            "food_search/$mealId/${java.net.URLEncoder.encode(mealName, "UTF-8")}"
    }
    object BarcodeScanner : Screen("barcode_scanner/{mealId}/{mealName}") {
        fun createRoute(mealId: Long, mealName: String) =
            "barcode_scanner/$mealId/${java.net.URLEncoder.encode(mealName, "UTF-8")}"
    }
    object Settings : Screen("settings")
}

@Composable
fun FitTrackNavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as FitTrackApplication
    val repository = app.repository
    val foodRepository = app.foodRepository

    NavHost(
        navController = navController,
        startDestination = Screen.WorkoutList.route
    ) {
        composable(Screen.WorkoutList.route) {
            val vm: WorkoutListViewModel = viewModel(
                factory = WorkoutListViewModelFactory(repository)
            )
            val foodVm: FoodTrackerViewModel = viewModel(
                factory = FoodTrackerViewModelFactory(foodRepository)
            )
            WorkoutListScreen(
                viewModel = vm,
                foodTrackerViewModel = foodVm,
                onWorkoutClick = { workoutId ->
                    navController.navigate(Screen.WorkoutDetail.createRoute(workoutId))
                },
                onStartWorkout = { workoutId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(workoutId))
                },
                onExercisesClick = { navController.navigate(Screen.ExerciseList.route) },
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onAddFood = { mealId, mealName ->
                    navController.navigate(Screen.FoodSearch.createRoute(mealId, mealName))
                },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.WorkoutDetail.route,
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
            val vm: WorkoutDetailViewModel = viewModel(
                factory = WorkoutDetailViewModelFactory(repository, workoutId)
            )
            WorkoutDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onStartWorkout = {
                    navController.navigate(Screen.ActiveWorkout.createRoute(workoutId))
                }
            )
        }

        composable(
            route = Screen.ActiveWorkout.route,
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
            val vm: ActiveWorkoutViewModel = viewModel(
                factory = ActiveWorkoutViewModelFactory(repository, workoutId, foodRepository)
            )
            ActiveWorkoutScreen(
                viewModel = vm,
                onFinish = { navController.popBackStack() }
            )
        }

        composable(Screen.ExerciseList.route) {
            val vm: ExerciseViewModel = viewModel(
                factory = ExerciseViewModelFactory(repository)
            )
            ExerciseListScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            val vm: HistoryViewModel = viewModel(
                factory = HistoryViewModelFactory(repository)
            )
            HistoryScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.FoodSearch.route,
            arguments = listOf(
                navArgument("mealId") { type = NavType.LongType },
                navArgument("mealName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mealId = backStackEntry.arguments?.getLong("mealId") ?: return@composable
            val mealName = backStackEntry.arguments?.getString("mealName")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: ""
            val vm: FoodSearchViewModel = viewModel(
                factory = FoodSearchViewModelFactory(foodRepository)
            )

            // Pick up barcode scanned in BarcodeScannerScreen via savedStateHandle
            val scannedBarcode by backStackEntry.savedStateHandle
                .getStateFlow<String?>("scanned_barcode", null)
                .collectAsState()
            LaunchedEffect(scannedBarcode) {
                scannedBarcode?.let { barcode ->
                    vm.lookupBarcode(barcode)
                    backStackEntry.savedStateHandle.remove<String>("scanned_barcode")
                }
            }

            FoodSearchScreen(
                viewModel = vm,
                mealId = mealId,
                mealName = mealName,
                onBack = { navController.popBackStack() },
                onScanBarcode = {
                    navController.navigate(Screen.BarcodeScanner.createRoute(mealId, mealName))
                }
            )
        }

        composable(
            route = Screen.BarcodeScanner.route,
            arguments = listOf(
                navArgument("mealId") { type = NavType.LongType },
                navArgument("mealName") { type = NavType.StringType }
            )
        ) {
            BarcodeScannerScreen(
                onBarcodeDetected = { barcode ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("scanned_barcode", barcode)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(app.userPreferences, app.backupPreferences)
            )
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
