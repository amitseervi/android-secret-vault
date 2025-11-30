package com.rignis.mysecrets.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rignis.auth.domain.CipherManager
import com.rignis.core.ui.routes.detail.DetailRoute
import com.rignis.core.ui.routes.home.HomeRoute
import com.rignis.core.ui.viewmodels.detail.DetailViewModel
import com.rignis.core.ui.viewmodels.home.HomeViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import timber.log.Timber

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController, AppDestination.Home.route) {
        composable(AppDestination.Home.route) {
            val viewModel: HomeViewModel = koinViewModel()
            val state = viewModel.state.collectAsStateWithLifecycle()
            HomeRoute(state, viewModel::onAction, navigateToAddSecret = {
                navController.navigate("detail")
            }, openDetailPage = { id ->
                navController.navigate("detail?id=$id")
            })
        }

        composable(
            AppDestination.Detail.route, arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    defaultValue = null   // makes it truly optional
                    nullable = true
                })
        ) { backStack ->
            val id = backStack.arguments?.getString("id")
            val viewModel: DetailViewModel = koinViewModel()
            if (id.isNullOrEmpty()) {
                viewModel.initializeForNewEntry()
            } else {
                Timber.tag("amittest").i("Initialize viewmodel with id =$id")
                viewModel.initializeWithEditMode(id)
            }
            Timber.tag("amittest").i("Provided id= $id")
            val state = viewModel.state.collectAsStateWithLifecycle()
            val cipherManager: CipherManager = koinInject<CipherManager>()
            DetailRoute(state, cipherManager, viewModel::onAction, onNavigateBack = {
                navController.navigateUp()
            })

        }
    }
}