package com.kazahana.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kazahana.app.R
import com.kazahana.app.ui.auth.LoginScreen
import com.kazahana.app.ui.auth.AuthViewModel
import com.kazahana.app.ui.compose.ComposeScreen
import com.kazahana.app.ui.thread.ThreadScreen
import com.kazahana.app.ui.timeline.TimelineScreen
import com.kazahana.app.ui.search.SearchScreen
import com.kazahana.app.ui.notification.NotificationScreen
import com.kazahana.app.ui.messages.MessagesScreen
import com.kazahana.app.ui.profile.ProfileScreen
import kotlinx.serialization.Serializable

// Routes
@Serializable object LoginRoute
@Serializable object HomeRoute
@Serializable object SearchRoute
@Serializable object NotificationsRoute
@Serializable object MessagesRoute
@Serializable object ProfileRoute
@Serializable object ComposeRoute
@Serializable data class ReplyRoute(
    val postUri: String,
    val postCid: String,
    val rootUri: String,
    val rootCid: String,
    val authorHandle: String,
    val authorDisplayName: String = "",
    val postText: String = "",
)
@Serializable data class ThreadRoute(
    val postUri: String,
)
@Serializable data class QuoteRoute(
    val postUri: String,
    val postCid: String,
    val authorHandle: String,
    val authorDisplayName: String = "",
    val postText: String = "",
)

data class BottomNavItem(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(HomeRoute, R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(SearchRoute, R.string.tab_search, Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem(NotificationsRoute, R.string.tab_notifications, Icons.Filled.Notifications, Icons.Outlined.Notifications),
    BottomNavItem(MessagesRoute, R.string.tab_messages, Icons.Filled.Email, Icons.Outlined.Email),
    BottomNavItem(ProfileRoute, R.string.tab_profile, Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun KazahanaNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    if (isLoggedIn) {
        MainScreen()
    } else {
        LoginScreen(authViewModel = authViewModel)
    }
}

@Composable
private fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Hide bottom bar and FAB on compose screens
    val isOnCompose = currentDestination?.hasRoute(ComposeRoute::class) == true
        || currentDestination?.hasRoute(ReplyRoute::class) == true
        || currentDestination?.hasRoute(QuoteRoute::class) == true

    Scaffold(
        bottomBar = {
            if (!isOnCompose) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(item.route::class)
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes),
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isOnCompose) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(ComposeRoute) {
                            launchSingleTop = true
                        }
                    },
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.compose_title))
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable<HomeRoute> {
                TimelineScreen(
                    onPostClick = { postUri ->
                        navController.navigate(ThreadRoute(postUri = postUri)) {
                            launchSingleTop = true
                        }
                    },
                    onReply = { postUri, postCid, rootUri, rootCid, authorHandle, authorDisplayName, postText ->
                        navController.navigate(
                            ReplyRoute(
                                postUri = postUri,
                                postCid = postCid,
                                rootUri = rootUri,
                                rootCid = rootCid,
                                authorHandle = authorHandle,
                                authorDisplayName = authorDisplayName,
                                postText = postText,
                            )
                        ) { launchSingleTop = true }
                    },
                    onQuote = { postUri, postCid, authorHandle, authorDisplayName, postText ->
                        navController.navigate(
                            QuoteRoute(
                                postUri = postUri,
                                postCid = postCid,
                                authorHandle = authorHandle,
                                authorDisplayName = authorDisplayName,
                                postText = postText,
                            )
                        ) { launchSingleTop = true }
                    },
                )
            }
            composable<SearchRoute> { SearchScreen() }
            composable<NotificationsRoute> { NotificationScreen() }
            composable<MessagesRoute> { MessagesScreen() }
            composable<ProfileRoute> { ProfileScreen() }
            composable<ThreadRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ThreadRoute>()
                ThreadScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onReply = { postUri, postCid, rootUri, rootCid, authorHandle, authorDisplayName, postText ->
                        navController.navigate(
                            ReplyRoute(
                                postUri = postUri,
                                postCid = postCid,
                                rootUri = rootUri,
                                rootCid = rootCid,
                                authorHandle = authorHandle,
                                authorDisplayName = authorDisplayName,
                                postText = postText,
                            )
                        ) { launchSingleTop = true }
                    },
                )
            }
            composable<ComposeRoute> {
                ComposeScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<ReplyRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ReplyRoute>()
                ComposeScreen(
                    onNavigateBack = { navController.popBackStack() },
                    replyTarget = com.kazahana.app.ui.compose.ReplyTarget(
                        uri = route.postUri,
                        cid = route.postCid,
                        rootUri = route.rootUri,
                        rootCid = route.rootCid,
                        authorHandle = route.authorHandle,
                        authorDisplayName = route.authorDisplayName.ifEmpty { null },
                        text = route.postText,
                    ),
                )
            }
            composable<QuoteRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<QuoteRoute>()
                ComposeScreen(
                    onNavigateBack = { navController.popBackStack() },
                    quoteTarget = com.kazahana.app.ui.compose.QuoteTarget(
                        uri = route.postUri,
                        cid = route.postCid,
                        authorHandle = route.authorHandle,
                        authorDisplayName = route.authorDisplayName.ifEmpty { null },
                        text = route.postText,
                    ),
                )
            }
        }
    }
}
