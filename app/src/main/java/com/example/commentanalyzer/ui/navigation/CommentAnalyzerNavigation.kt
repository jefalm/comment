package com.example.commentanalyzer.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.commentanalyzer.R

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
                UnavailableDestination(
                    titleRes = R.string.comments_unavailable_title,
                    messageRes = R.string.comments_unavailable_message,
                    tag = "comments_unavailable"
                )
            }
            composable(AnalyzerDestination.ANALYSIS.route) {
                UnavailableDestination(
                    titleRes = R.string.analysis_unavailable_title,
                    messageRes = R.string.analysis_unavailable_message,
                    tag = "analysis_unavailable"
                )
            }
            composable(AnalyzerDestination.SETTINGS.route) {
                UnavailableDestination(
                    titleRes = R.string.settings_unavailable_title,
                    messageRes = R.string.settings_unavailable_message,
                    tag = "settings_unavailable"
                )
            }
        }
    }
}

@Composable
private fun UnavailableDestination(
    titleRes: Int,
    messageRes: Int,
    tag: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(messageRes), style = MaterialTheme.typography.bodyLarge)
    }
}