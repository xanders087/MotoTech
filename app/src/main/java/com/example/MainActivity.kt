package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.AppDatabase
import com.example.data.DiagnosticRepository
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val database = AppDatabase.getDatabase(context)
                val repository = DiagnosticRepository(database.diagnosticDao())
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(repository)
                )

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

                Scaffold(
                    containerColor = MotoBg,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MotoSurface,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = currentRoute == "dashboard",
                                onClick = { navController.navigate("dashboard") },
                                icon = {
                                    Icon(
                                        Icons.Default.Dashboard,
                                        contentDescription = "Cockpit",
                                        tint = if (currentRoute == "dashboard") MotoPrimary else MotoTextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        "Cockpit",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentRoute == "dashboard") FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentRoute == "dashboard") MotoPrimary else MotoTextSecondary
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "history",
                                onClick = { navController.navigate("history") },
                                icon = {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        contentDescription = "Hoja de Vida",
                                        tint = if (currentRoute == "history") MotoPrimary else MotoTextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        "Hoja Vida",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentRoute == "history") FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentRoute == "history") MotoPrimary else MotoTextSecondary
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "workshops",
                                onClick = { navController.navigate("workshops") },
                                icon = {
                                    Icon(
                                        Icons.Default.Storefront,
                                        contentDescription = "Talleres",
                                        tint = if (currentRoute == "workshops") MotoPrimary else MotoTextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        "Talleres",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentRoute == "workshops") FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentRoute == "workshops") MotoPrimary else MotoTextSecondary
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "premium",
                                onClick = { navController.navigate("premium") },
                                icon = {
                                    Icon(
                                        Icons.Default.LocalShipping,
                                        contentDescription = "Flotas & Planes",
                                        tint = if (currentRoute == "premium") MotoPrimary else MotoTextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        "Flotas/SaaS",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentRoute == "premium") FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentRoute == "premium") MotoPrimary else MotoTextSecondary
                                    )
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") {
                            MotoTechCockpit(viewModel, onNavigateToWorkshops = {
                                navController.navigate("workshops")
                            }, onNavigateToPremium = {
                                navController.navigate("premium")
                            })
                        }
                        composable("history") {
                            HistoryScreen(viewModel)
                        }
                        composable("workshops") {
                            WorkshopsScreen(viewModel)
                        }
                        composable("premium") {
                            PremiumScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
