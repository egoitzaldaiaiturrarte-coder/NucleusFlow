package com.nucleusflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nucleusflow.ui.screens.cores.CoresScreen
import com.nucleusflow.ui.screens.diag.DiagScreen
import com.nucleusflow.ui.screens.home.HomeScreen
import com.nucleusflow.ui.screens.integration.IntegrationScreen
import com.nucleusflow.ui.screens.memory.MemoryScreen
import com.nucleusflow.ui.screens.perapp.PerAppScreen
import com.nucleusflow.ui.screens.processes.ProcessesScreen
import com.nucleusflow.shizuku.ShizukuBridge
import com.nucleusflow.ui.theme.NucleusFlowTheme

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Screen("home", "Inicio", Icons.Default.Home)
    data object Cores : Screen("cores", "Núcleos", Icons.Default.Memory)
    data object Processes : Screen("processes", "Hilos", Icons.Default.List)
    data object PerApp : Screen("perapp", "Por app", Icons.Default.Apps)
    data object Memory : Screen("memory", "Memoria", Icons.Default.Storage)
    data object Integration : Screen("integration", "Integración", Icons.Default.Sync)
    data object Diag : Screen("diag", "Diagnóstico", Icons.Default.BugReport)
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        ShizukuBridge.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NucleusFlowTheme {
                val navController = rememberNavController()
                val items = listOf(Screen.Home, Screen.Cores, Screen.Processes, Screen.PerApp, Screen.Memory, Screen.Integration, Screen.Diag)
                Scaffold(
                    topBar = { TopAppBar(title = { Text("NucleusFlow") }) },
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                                    label = { Text(screen.label) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(navController, startDestination = Screen.Home.route, modifier = Modifier.padding(innerPadding)) {
                        composable(Screen.Home.route) { HomeScreen() }
                        composable(Screen.Cores.route) { CoresScreen() }
                        composable(Screen.Processes.route) { ProcessesScreen() }
                        composable(Screen.PerApp.route) { PerAppScreen() }
                        composable(Screen.Memory.route) { MemoryScreen() }
                        composable(Screen.Integration.route) { IntegrationScreen() }
                        composable(Screen.Diag.route) { DiagScreen() }
                    }
                }
            }
        }
    }
}
