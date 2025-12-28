package com.kontext.ui.navigation

sealed class Screen(val route: String) {
    object Immerse : Screen("immerse")
    object Drill : Screen("drill")
    object Profile : Screen("profile")
}
