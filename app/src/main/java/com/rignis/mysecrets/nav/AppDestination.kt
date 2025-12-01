package com.rignis.mysecrets.nav

sealed class AppDestination(val route: String) {
    data object Login : AppDestination("login")
    data object Home : AppDestination("home")

    data object Detail : AppDestination("detail?id={id}")

    data object About : AppDestination("about")

    data object Setting : AppDestination("setting")

    data object OpenSourceLicences : AppDestination("licences")
}