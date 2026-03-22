package com.kazahana.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.data.local.ModerationPref
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.ui.common.LocalModerationSettings
import com.kazahana.app.ui.common.ModerationSettings
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
import com.kazahana.app.ui.messages.ChatScreen
import com.kazahana.app.ui.messages.MessagesScreen
import com.kazahana.app.ui.notification.NotificationScreen
import com.kazahana.app.ui.notification.NotificationViewModel
import com.kazahana.app.ui.profile.ProfileScreen
import com.kazahana.app.ui.search.SearchScreen
import com.kazahana.app.ui.settings.SettingsScreen
import com.kazahana.app.ui.thread.ThreadScreen
import com.kazahana.app.ui.timeline.TimelineScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable

// Routes
@Serializable object LoginRoute
@Serializable object HomeRoute
@Serializable object SearchRoute
@Serializable object NotificationsRoute
@Serializable object MessagesRoute
@Serializable object ProfileRoute
@Serializable data class ProfileDetailRoute(val actorDid: String)
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
@Serializable object SettingsRoute
@Serializable data class ChatRoute(val convoId: String)

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
    settingsStore: SettingsStore? = null,
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    // Collect moderation settings
    val adultEnabled by settingsStore?.adultContentEnabled?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
    val nudityPref by settingsStore?.nudityPref?.collectAsState(initial = ModerationPref.WARN) ?: remember { mutableStateOf(ModerationPref.WARN) }
    val sexualPref by settingsStore?.sexualPref?.collectAsState(initial = ModerationPref.WARN) ?: remember { mutableStateOf(ModerationPref.WARN) }
    val pornPref by settingsStore?.pornPref?.collectAsState(initial = ModerationPref.WARN) ?: remember { mutableStateOf(ModerationPref.WARN) }
    val graphicMediaPref by settingsStore?.graphicMediaPref?.collectAsState(initial = ModerationPref.WARN) ?: remember { mutableStateOf(ModerationPref.WARN) }

    val moderationSettings = ModerationSettings(
        adultContentEnabled = adultEnabled,
        nudityPref = nudityPref,
        sexualPref = sexualPref,
        pornPref = pornPref,
        graphicMediaPref = graphicMediaPref,
    )

    CompositionLocalProvider(LocalModerationSettings provides moderationSettings) {
        when (isLoggedIn) {
            null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            true -> MainScreen(authViewModel = authViewModel)
            false -> LoginScreen(authViewModel = authViewModel)
        }
    }
}

@Composable
private fun MainScreen(
    authViewModel: AuthViewModel,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val scope = rememberCoroutineScope()

    // Shared NotificationViewModel for unread badge
    val notificationViewModel: NotificationViewModel = hiltViewModel()
    val unreadCount by notificationViewModel.unreadCount.collectAsState()

    // Current user DID for messages
    val myDid = authViewModel.currentDid

    // Tab re-tap events for refresh + scroll to top (keyed by route label)
    val homeRetap = remember { MutableSharedFlow<Unit>() }
    val searchRetap = remember { MutableSharedFlow<Unit>() }
    val notificationsRetap = remember { MutableSharedFlow<Unit>() }
    val messagesRetap = remember { MutableSharedFlow<Unit>() }
    val profileRetap = remember { MutableSharedFlow<Unit>() }

    val tabRetapMap = remember {
        mapOf<Int, MutableSharedFlow<Unit>>(
            R.string.tab_home to homeRetap,
            R.string.tab_search to searchRetap,
            R.string.tab_notifications to notificationsRetap,
            R.string.tab_messages to messagesRetap,
            R.string.tab_profile to profileRetap,
        )
    }

    // Hide bottom bar and FAB only on compose screens
    val isOnCompose = currentDestination?.hasRoute(ComposeRoute::class) == true
        || currentDestination?.hasRoute(ReplyRoute::class) == true
        || currentDestination?.hasRoute(QuoteRoute::class) == true

    val hideChrome = isOnCompose

    Scaffold(
        bottomBar = {
            if (!hideChrome) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(item.route::class)
                        } == true

                        val isNotifications = item.route is NotificationsRoute

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // Pop back to the graph root, then navigate to the tab
                                navController.popBackStack(
                                    navController.graph.findStartDestination().id,
                                    inclusive = false,
                                )
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                    }
                                }
                                // Trigger refresh + scroll to top
                                scope.launch {
                                    tabRetapMap[item.labelRes]?.emit(Unit)
                                }
                            },
                            icon = {
                                if (isNotifications && unreadCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(
                                                    if (unreadCount > 99) "99+" else unreadCount.toString()
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes),
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes),
                                    )
                                }
                            },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!hideChrome) {
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
                    retapFlow = homeRetap,
                    onPostClick = { postUri ->
                        navController.navigate(ThreadRoute(postUri = postUri)) {
                            launchSingleTop = true
                        }
                    },
                    onProfileClick = { did ->
                        navController.navigate(ProfileDetailRoute(actorDid = did)) {
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
            composable<SearchRoute> {
                SearchScreen(
                    retapFlow = searchRetap,
                    onPostClick = { postUri ->
                        navController.navigate(ThreadRoute(postUri = postUri)) {
                            launchSingleTop = true
                        }
                    },
                    onProfileClick = { did ->
                        navController.navigate(ProfileDetailRoute(actorDid = did)) {
                            launchSingleTop = true
                        }
                    },
                    onReply = { postUri, postCid, rootUri, rootCid, authorHandle, authorDisplayName, postText ->
                        navController.navigate(
                            ReplyRoute(postUri = postUri, postCid = postCid, rootUri = rootUri, rootCid = rootCid, authorHandle = authorHandle, authorDisplayName = authorDisplayName, postText = postText)
                        ) { launchSingleTop = true }
                    },
                    onQuote = { postUri, postCid, authorHandle, authorDisplayName, postText ->
                        navController.navigate(
                            QuoteRoute(postUri = postUri, postCid = postCid, authorHandle = authorHandle, authorDisplayName = authorDisplayName, postText = postText)
                        ) { launchSingleTop = true }
                    },
                )
            }
            composable<NotificationsRoute> {
                NotificationScreen(
                    retapFlow = notificationsRetap,
                    viewModel = notificationViewModel,
                    onPostClick = { postUri ->
                        navController.navigate(ThreadRoute(postUri = postUri)) {
                            launchSingleTop = true
                        }
                    },
                    onProfileClick = { did ->
                        navController.navigate(ProfileDetailRoute(actorDid = did)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<MessagesRoute> {
                MessagesScreen(
                    retapFlow = messagesRetap,
                    myDid = myDid,
                    onConvoClick = { convoId ->
                        navController.navigate(ChatRoute(convoId = convoId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<ProfileRoute> {
                ProfileScreen(
                    retapFlow = profileRetap,
                    onPostClick = { postUri ->
                        navController.navigate(ThreadRoute(postUri = postUri)) {
                            launchSingleTop = true
                        }
                    },
                    onProfileClick = { did ->
                        navController.navigate(ProfileDetailRoute(actorDid = did)) {
                            launchSingleTop = true
                        }
                    },
                    onReply = { postUri, postCid, rootUri, rootCid, authorHandle, authorDisplayName, postText ->
                        navController.navigate(
                            ReplyRoute(postUri = postUri, postCid = postCid, rootUri = rootUri, rootCid = rootCid, authorHandle = authorHandle, authorDisplayName = authorDisplayName, postText = postText)
                        ) { launchSingleTop = true }
                    },
                    onQuote = { postUri, postCid, authorHandle, authorDisplayName, postText ->
                        navController.navigate(
                            QuoteRoute(postUri = postUri, postCid = postCid, authorHandle = authorHandle, authorDisplayName = authorDisplayName, postText = postText)
                        ) { launchSingleTop = true }
                    },
                    onSettingsClick = {
                        navController.navigate(SettingsRoute) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<ProfileDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ProfileDetailRoute>()
                ProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPostClick = { postUri ->
                        navController.navigate(ThreadRoute(postUri = postUri)) {
                            launchSingleTop = true
                        }
                    },
                    onProfileClick = { did ->
                        navController.navigate(ProfileDetailRoute(actorDid = did)) {
                            launchSingleTop = true
                        }
                    },
                    onReply = { postUri, postCid, rootUri, rootCid, authorHandle, authorDisplayName, postText ->
                        navController.navigate(
                            ReplyRoute(postUri = postUri, postCid = postCid, rootUri = rootUri, rootCid = rootCid, authorHandle = authorHandle, authorDisplayName = authorDisplayName, postText = postText)
                        ) { launchSingleTop = true }
                    },
                    onQuote = { postUri, postCid, authorHandle, authorDisplayName, postText ->
                        navController.navigate(
                            QuoteRoute(postUri = postUri, postCid = postCid, authorHandle = authorHandle, authorDisplayName = authorDisplayName, postText = postText)
                        ) { launchSingleTop = true }
                    },
                )
            }
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
            composable<SettingsRoute> {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onLogout = {
                        authViewModel.logout()
                    },
                )
            }
            composable<ChatRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ChatRoute>()
                ChatScreen(
                    myDid = myDid,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}
