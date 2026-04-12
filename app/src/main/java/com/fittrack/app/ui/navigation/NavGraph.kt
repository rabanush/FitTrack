package com.fittrack.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fittrack.app.FitTrackApplication
import com.fittrack.app.ui.screens.*
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
}

@Composable
fun FitTrackNavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as FitTrackApplication
    val repository = app.repository

    NavHost(
        navController = navController,
        startDestination = Screen.WorkoutList.route
    ) {
        composable(Screen.WorkoutList.route) {
            val vm: WorkoutListViewModel = viewModel(
                factory = WorkoutListViewModelFactory(repository)
            )
            WorkoutListScreen(
                viewModel = vm,
                onWorkoutClick = { workoutId ->
                    navController.navigate(Screen.WorkoutDetail.createRoute(workoutId))
                },
                onStartWorkout = { workoutId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(workoutId))
                },
                onExercisesClick = { navController.navigate(Screen.ExerciseList.route) },
                onHistoryClick = { navController.navigate(Screen.History.route) }
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
                factory = ActiveWorkoutViewModelFactory(repository, workoutId)
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
    }
}
