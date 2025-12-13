package com.rignis.mysecrets.nav

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rignis.auth.domain.CipherManager
import com.rignis.core.ui.routes.about.AboutRoute
import com.rignis.core.ui.routes.detail.DetailRoute
import com.rignis.core.ui.routes.home.HomeRoute
import com.rignis.core.ui.routes.home.HomeScreenDrawer
import com.rignis.core.ui.routes.settings.SettingsRoute
import com.rignis.core.ui.viewmodels.detail.DetailViewModel
import com.rignis.core.ui.viewmodels.home.HomeViewModel
import com.rignis.core.ui.viewmodels.settings.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    LocalActivity.current as Activity
    NavHost(navController, AppDestination.Home.route) {
        composable(AppDestination.Home.route) {
            val viewModel: HomeViewModel = koinViewModel()
            HomeScreenDrawer(navigateToSetting = {
                navController.navigate(AppDestination.Setting.route)
            }, navigateToAbout = {
                navController.navigate(AppDestination.About.route)
            }, navigateToOss = {

            }) { modifier, openDrawer ->
                HomeRoute(
                    viewModel,
                    navigateToAddSecret = {
                        navController.navigate("detail")
                    },
                    { id ->
                        navController.navigate("detail?id=$id")
                    },
                    openDrawer,
                    analytics = koinInject(),
                )
            }
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
                viewModel.initializeWithEditMode(id)
            }
            val cipherManager: CipherManager = koinInject<CipherManager>()
            DetailRoute(viewModel, cipherManager, viewModel::onAction, onNavigateBack = {
                navController.navigateUp()
            }, koinInject())
        }

        composable(AppDestination.About.route) {
            AboutRoute({ navController.navigateUp() }, koinInject(), koinInject())
        }

        composable(AppDestination.Setting.route) {
            val settingViewModel: SettingsViewModel = koinViewModel()
            SettingsRoute(settingViewModel, {
                navController.navigateUp()
            }, koinInject())
        }
    }
}