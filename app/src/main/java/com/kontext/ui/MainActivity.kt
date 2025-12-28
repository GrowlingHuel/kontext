package com.kontext.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kontext.ui.navigation.Screen
import com.kontext.ui.screens.drill.DrillScreen
import com.kontext.ui.screens.immerse.ImmerseScreen
import com.kontext.ui.screens.profile.ProfileScreen
import com.kontext.ui.theme.KontextTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KontextTheme {
                KontextApp()
            }
        }
    }
}

@Composable
fun KontextApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Book, contentDescription = "Immerse") },
                    label = { Text("Immerse") },
                    selected = currentRoute == Screen.Immerse.route,
                    onClick = { 
                        if (currentRoute != Screen.Immerse.route) {
                            navController.navigate(Screen.Immerse.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.School, contentDescription = "Drill") },
                    label = { Text("Drill") },
                    selected = currentRoute == Screen.Drill.route,
                    onClick = {
                         if (currentRoute != Screen.Drill.route) {
                            navController.navigate(Screen.Drill.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = currentRoute == Screen.Profile.route,
                    onClick = {
                        if (currentRoute != Screen.Profile.route) {
                            navController.navigate(Screen.Profile.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Drill.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Immerse.route) { ImmerseScreen() }
            composable(Screen.Drill.route) { DrillScreen() }
            composable(Screen.Profile.route) { ProfileScreen() }
        }
    }
}
