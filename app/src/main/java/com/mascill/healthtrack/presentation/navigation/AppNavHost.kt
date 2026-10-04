package com.mascill.healthtrack.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mascill.healthtrack.presentation.history.HistoryScreen
import com.mascill.healthtrack.presentation.profile.ProfileScreen
import com.mascill.healthtrack.presentation.result.ResultScreen
import com.mascill.healthtrack.presentation.session.SessionScreen

private enum class BottomNavItem(
    val key: NavKey,
    val label: String,
    val icon: ImageVector,
) {
    Profile(ProfileRoute, "Profil", Icons.Filled.Person),
    Session(SessionRoute, "Sesi", Icons.Filled.PlayArrow),
    History(HistoryRoute, "Riwayat", Icons.AutoMirrored.Filled.List),
}

/**
 * Bottom navigation for profile, session, and history.
 */
@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(ProfileRoute)
    val currentKey = backStack.lastOrNull()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    NavigationBarItem(
                        selected = currentKey == item.key,
                        onClick = { backStack.navigateToTopLevel(item.key) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.navigateBack() },
            modifier = Modifier.padding(innerPadding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<ProfileRoute> {
                    ProfileScreen()
                }
                entry<SessionRoute> {
                    SessionScreen()
                }
                entry<HistoryRoute> {
                    HistoryScreen()
                }
                entry<ResultRoute> { key ->
                    ResultScreen(measurementId = key.measurementId)
                }
            },
        )
    }
}
