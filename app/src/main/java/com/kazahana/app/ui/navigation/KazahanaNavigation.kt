package com.kazahana.app.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
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
import com.kazahana.app.ui.auth.AccountPickerScreen
import com.kazahana.app.ui.auth.LoginScreen
import com.kazahana.app.ui.auth.AuthViewModel
import com.kazahana.app.ui.compose.ComposeScreen
import com.kazahana.app.ui.messages.ChatScreen
import com.kazahana.app.ui.messages.MessagesScreen
import com.kazahana.app.ui.messages.NewConversationScreen
import com.kazahana.app.ui.notification.NotificationScreen
import com.kazahana.app.ui.notification.NotificationViewModel
import com.kazahana.app.ui.profile.ProfileScreen
import com.kazahana.app.ui.search.SearchScreen
import com.kazahana.app.ui.settings.BsafBotsScreen
import com.kazahana.app.ui.settings.FeedManagementScreen
import com.kazahana.app.ui.settings.SettingsScreen
import com.kazahana.app.ui.thread.ThreadScreen
import com.kazahana.app.ui.timeline.QuotesListScreen
import com.kazahana.app.ui.timeline.TimelineScreen
import kotlinx.coroutines.flow.Flow
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
@Serializable data class QuotesListRoute(val postUri: String)
@Serializable data class ShareComposeRoute(val sharedText: String = "")
@Serializable object SettingsRoute
@Serializable object FeedManagementRoute
@Serializable object BsafBotsRoute
@Serializable data class ChatRoute(val convoId: String)
@Serializable object NewConversationRoute
@Serializable data class SearchWithQueryRoute(val query: String)

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
    deepLinkFlow: Flow<DeepLink>? = null,
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val savedAccounts by authViewModel.savedAccounts.collectAsState()
    val activeAccountDID by authViewModel.activeAccountDID.collectAsState()
    val showAddAccountLogin by authViewModel.showAddAccountLogin.collectAsState()

    // Collect moderation settings
    val adultEnabled by settingsStore?.adultContentEnabled?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
    val nudityPref by settingsStore?.nudityPref?.collectAsState(initial = ModerationPref.WARN) ?: remember { mutableStateOf(ModerationPref.WARN) }
    val sexualPref by settingsStore?.sexualPref?.collectAsState(initial = ModerationPref.WARN) ?: remember { mutableStateOf(ModerationPref.WARN) }
    val pornPref by settingsStore?.pornPref?.collectAsState(initial = ModerationPref.WARN) ?: remember { mutableStateOf(ModerationPref.WARN) }
    val graphicMediaPref by settingsStore?.graphicMediaPref?.collectAsState(initial = ModerationPref.WARN) ?: remember { mutableStateOf(ModerationPref.WARN) }
    val gorePref by settingsStore?.gorePref?.collectAsState(initial = ModerationPref.WARN) ?: remember { mutableStateOf(ModerationPref.WARN) }

    val moderationSettings = ModerationSettings(
        adultContentEnabled = adultEnabled,
        nudityPref = nudityPref,
        sexualPref = sexualPref,
        pornPref = pornPref,
        graphicMediaPref = graphicMediaPref,
        gorePref = gorePref,
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
            true -> {
                // key() forces full recomposition (all child ViewModels recreated) on account switch
                androidx.compose.runtime.key(activeAccountDID) {
                    MainScreen(authViewModel = authViewModel, deepLinkFlow = deepLinkFlow)
                }
            }
            false -> {
                if (savedAccounts.isNotEmpty()) {
                    // Multiple accounts saved but none active yet → account picker
                    AccountPickerScreen(authViewModel = authViewModel)
                } else {
                    LoginScreen(authViewModel = authViewModel)
                }
            }
        }

        // Add-account login shown as a full-screen dialog over the current screen
        if (showAddAccountLogin) {
            LoginScreen(
                authViewModel = authViewModel,
                onDismiss = { authViewModel.dismissAddAccountLogin() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    authViewModel: AuthViewModel,
    deepLinkFlow: Flow<DeepLink>? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val scope = rememberCoroutineScope()

    // Handle deep link / share intent navigation
    androidx.compose.runtime.LaunchedEffect(deepLinkFlow) {
        deepLinkFlow?.collect { deepLink ->
            when (deepLink) {
                is DeepLink.Profile -> navController.navigate(
                    ProfileDetailRoute(actorDid = deepLink.actor)
                ) { launchSingleTop = true }
                is DeepLink.Post -> navController.navigate(
                    ThreadRoute(postUri = deepLink.atUri)
                ) { launchSingleTop = true }
                is DeepLink.Compose -> navController.navigate(
                    ShareComposeRoute(sharedText = deepLink.text)
                ) { launchSingleTop = true }
                is DeepLink.Search -> navController.navigate(
                    SearchWithQueryRoute(query = deepLink.query)
                ) { launchSingleTop = true }
            }
        }
    }

    // Shared NotificationViewModel for unread badge
    val notificationViewModel: NotificationViewModel = hiltViewModel()
    val unreadCount by notificationViewModel.unreadCount.collectAsState()

    // Current user DID for messages
    val myDid = authViewModel.currentDid

    // Active handle for account switcher button
    val savedAccounts by authViewModel.savedAccounts.collectAsState()
    val activeHandle = remember(myDid, savedAccounts) {
        savedAccounts.find { it.did == myDid }?.handle
    }
    var showAccountSwitcher by remember { mutableStateOf(false) }

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
        || currentDestination?.hasRoute(ShareComposeRoute::class) == true

    val isOnSettings = currentDestination?.hasRoute(SettingsRoute::class) == true
        || currentDestination?.hasRoute(FeedManagementRoute::class) == true
        || currentDestination?.hasRoute(BsafBotsRoute::class) == true

    val isOnMessages = currentDestination?.hasRoute(MessagesRoute::class) == true
        || currentDestination?.hasRoute(NewConversationRoute::class) == true

    val isOnProfileDetail = currentDestination?.hasRoute(ProfileDetailRoute::class) == true

    val hideChrome = isOnCompose
    val hideFab = hideChrome || isOnSettings || isOnMessages || isOnProfileDetail

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
                                if (selected) {
                                    // Re-tap on tab root: refresh + scroll to top
                                    scope.launch {
                                        tabRetapMap[item.labelRes]?.emit(Unit)
                                    }
                                } else {
                                    // Navigate to tab: pop everything back to start, then go to tab root
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                    }
                                    // Also trigger refresh when returning from a detail screen
                                    scope.launch {
                                        tabRetapMap[item.labelRes]?.emit(Unit)
                                    }
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
            if (!hideFab) {
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
        // Shared navigation callbacks for hashtag/mention clicks
        val navigateToHashtag: (String) -> Unit = { tag ->
            navController.navigate(SearchWithQueryRoute(query = "#$tag")) {
                launchSingleTop = true
            }
        }
        val navigateToMention: (String) -> Unit = { didOrHandle ->
            navController.navigate(ProfileDetailRoute(actorDid = didOrHandle)) {
                launchSingleTop = true
            }
        }
        val navigateToQuotesList: (String) -> Unit = { postUri ->
            navController.navigate(QuotesListRoute(postUri = postUri)) {
                launchSingleTop = true
            }
        }

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
                    onViewQuotes = navigateToQuotesList,
                    onHashtagClick = navigateToHashtag,
                    onMentionClick = navigateToMention,
                    activeHandle = if (savedAccounts.size >= 2) activeHandle else null,
                    onAccountSwitcherClick = if (savedAccounts.size >= 2) {
                        { showAccountSwitcher = true }
                    } else null,
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
                    onViewQuotes = navigateToQuotesList,
                    onHashtagClick = navigateToHashtag,
                    onMentionClick = navigateToMention,
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
                    onHashtagClick = navigateToHashtag,
                    onMentionClick = navigateToMention,
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
                    onProfileClick = { did ->
                        navController.navigate(ProfileDetailRoute(actorDid = did)) {
                            launchSingleTop = true
                        }
                    },
                    onNewConversation = {
                        navController.navigate(NewConversationRoute) {
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
                    onViewQuotes = navigateToQuotesList,
                    onSettingsClick = {
                        navController.navigate(SettingsRoute) {
                            launchSingleTop = true
                        }
                    },
                    onHashtagClick = navigateToHashtag,
                    onMentionClick = navigateToMention,
                    onCompose = { text ->
                        if (text.isNullOrEmpty()) {
                            navController.navigate(ComposeRoute) { launchSingleTop = true }
                        } else {
                            navController.navigate(ShareComposeRoute(sharedText = text)) {
                                launchSingleTop = true
                            }
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
                    onViewQuotes = navigateToQuotesList,
                    onHashtagClick = navigateToHashtag,
                    onMentionClick = navigateToMention,
                    onCompose = { text ->
                        if (text.isNullOrEmpty()) {
                            navController.navigate(ComposeRoute) { launchSingleTop = true }
                        } else {
                            navController.navigate(ShareComposeRoute(sharedText = text)) {
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
            composable<ThreadRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ThreadRoute>()
                ThreadScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPostClick = { postUri ->
                        navController.navigate(ThreadRoute(postUri = postUri))
                    },
                    onProfileClick = { did ->
                        navController.navigate(ProfileDetailRoute(actorDid = did))
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
                            QuoteRoute(postUri = postUri, postCid = postCid, authorHandle = authorHandle, authorDisplayName = authorDisplayName, postText = postText)
                        ) { launchSingleTop = true }
                    },
                    onViewQuotes = navigateToQuotesList,
                    onHashtagClick = navigateToHashtag,
                    onMentionClick = navigateToMention,
                )
            }
            composable<ComposeRoute> {
                ComposeScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<ShareComposeRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ShareComposeRoute>()
                ComposeScreen(
                    onNavigateBack = { navController.popBackStack() },
                    initialText = route.sharedText,
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
            composable<QuotesListRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<QuotesListRoute>()
                QuotesListScreen(
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
                    onQuote = { postUri, postCid, authorHandle, authorDisplayName, postText ->
                        navController.navigate(
                            QuoteRoute(postUri = postUri, postCid = postCid, authorHandle = authorHandle, authorDisplayName = authorDisplayName, postText = postText)
                        ) { launchSingleTop = true }
                    },
                )
            }
            composable<SettingsRoute> {
                val savedAccounts by authViewModel.savedAccounts.collectAsState()
                val activeAccountDID by authViewModel.activeAccountDID.collectAsState()
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onLogout = {
                        authViewModel.logout()
                    },
                    onFeedManagement = {
                        navController.navigate(FeedManagementRoute) {
                            launchSingleTop = true
                        }
                    },
                    onBsafBots = {
                        navController.navigate(BsafBotsRoute) {
                            launchSingleTop = true
                        }
                    },
                    savedAccounts = savedAccounts,
                    activeAccountDID = activeAccountDID,
                    onSwitchAccount = { session -> authViewModel.switchAccount(session) },
                    onRemoveAccount = { did -> authViewModel.removeAccount(did) },
                    onAddAccount = { authViewModel.showAddAccountLogin() },
                )
            }
            composable<FeedManagementRoute> {
                FeedManagementScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<BsafBotsRoute> {
                BsafBotsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<NewConversationRoute> {
                NewConversationScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onConvoCreated = { convoId ->
                        navController.popBackStack()
                        navController.navigate(ChatRoute(convoId = convoId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<ChatRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ChatRoute>()
                ChatScreen(
                    myDid = myDid,
                    onNavigateBack = { navController.popBackStack() },
                    onHashtagClick = { tag ->
                        navController.navigate(SearchWithQueryRoute(query = "#$tag")) {
                            launchSingleTop = true
                        }
                    },
                    onProfileClick = { handleOrDid ->
                        navController.navigate(ProfileDetailRoute(actorDid = handleOrDid)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<SearchWithQueryRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<SearchWithQueryRoute>()
                SearchScreen(
                    initialQuery = route.query,
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
                    onViewQuotes = navigateToQuotesList,
                    onHashtagClick = navigateToHashtag,
                    onMentionClick = navigateToMention,
                )
            }
        }
    }

    // Account switcher modal bottom sheet
    if (showAccountSwitcher) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showAccountSwitcher = false },
        ) {
            AccountSwitcherSheet(
                accounts = savedAccounts,
                activeAccountDID = myDid,
                onSwitchAccount = { session ->
                    showAccountSwitcher = false
                    authViewModel.switchAccount(session)
                },
                onAddAccount = {
                    showAccountSwitcher = false
                    authViewModel.showAddAccountLogin()
                },
            )
        }
    }
}

@Composable
private fun AccountSwitcherSheet(
    accounts: List<com.kazahana.app.data.model.Session>,
    activeAccountDID: String,
    onSwitchAccount: (com.kazahana.app.data.model.Session) -> Unit,
    onAddAccount: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.auth_account_picker_title),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        accounts.forEach { account ->
            val isActive = account.did == activeAccountDID
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!isActive) Modifier.clickable { onSwitchAccount(account) }
                        else Modifier
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "@${account.handle}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold
                    else androidx.compose.ui.text.font.FontWeight.Normal,
                )
                if (isActive) {
                    Text(
                        text = stringResource(R.string.settings_active_account),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider()
        }
        Text(
            text = stringResource(R.string.settings_add_account),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddAccount)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
