package com.example.commentanalyzer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.commentanalyzer.R
import com.example.commentanalyzer.ui.components.UiPresentationState
import com.example.commentanalyzer.ui.components.UiStatusContent
import androidx.compose.foundation.layout.padding

private enum class AnalyzerDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    HOME("home", R.string.navigation_home, Icons.Filled.Home),
    COMMENTS("comments", R.string.navigation_comments, Icons.AutoMirrored.Filled.List),
    ANALYSIS("analysis", R.string.navigation_analysis, Icons.Filled.Info),
    SETTINGS("settings", R.string.navigation_settings, Icons.Filled.Settings)
}

@Composable
fun CommentAnalyzerNavigation(
    homeContent: @Composable () -> Unit,
    commentsContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedRoute = backStackEntry?.destination?.route ?: AnalyzerDestination.HOME.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                AnalyzerDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("destination_${destination.route}"),
                        selected = selectedRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AnalyzerDestination.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AnalyzerDestination.HOME.route) {
                homeContent()
            }
            composable(AnalyzerDestination.COMMENTS.route) {
                commentsContent()
            }
            composable(AnalyzerDestination.ANALYSIS.route) {
                UiStatusContent(
                    status = UiPresentationState.Unavailable(
                        titleRes = R.string.analysis_unavailable_title,
                        messageRes = R.string.analysis_unavailable_message
                    ),
                    modifier = Modifier.testTag("analysis_unavailable")
                )
            }
            composable(AnalyzerDestination.SETTINGS.route) {
                settingsContent()
            }
        }
    }
}