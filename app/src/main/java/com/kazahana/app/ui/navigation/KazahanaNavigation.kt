package com.kazahana.app.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.lifecycle.repeatOnLifecycle
import com.kazahana.app.R
import com.kazahana.app.ui.auth.AccountPickerScreen
import com.kazahana.app.ui.auth.LoginScreen
import com.kazahana.app.ui.auth.AuthViewModel
import com.kazahana.app.ui.compose.ComposeScreen
import com.kazahana.app.ui.messages.ChatScreen
import com.kazahana.app.ui.messages.CreateGroupScreen
import com.kazahana.app.ui.messages.GroupSettingsScreen
import com.kazahana.app.ui.messages.JoinGroupScreen
import com.kazahana.app.ui.messages.MessagesScreen
import com.kazahana.app.ui.messages.MessagesViewModel
import com.kazahana.app.ui.messages.NewConversationScreen
import com.kazahana.app.ui.notification.NotificationScreen
import com.kazahana.app.ui.notification.NotificationViewModel
import com.kazahana.app.ui.profile.ProfileScreen
import com.kazahana.app.ui.search.SearchScreen
import com.kazahana.app.ui.settings.BsafBotsScreen
import com.kazahana.app.ui.settings.FeedManagementScreen
import com.kazahana.app.ui.settings.SettingsScreen
import com.kazahana.app.ui.settings.WatermarkSettingsScreen
import com.kazahana.app.ui.evacuation.CompassNavScreen
import com.kazahana.app.ui.evacuation.EvacuationBannerView
import com.kazahana.app.ui.evacuation.EvacuationViewModel
import com.kazahana.app.ui.evacuation.NearestSheltersScreen
import com.kazahana.app.ui.evacuation.ShelterDetailScreen
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
@Serializable object WatermarkSettingsRoute
@Serializable data class ChatRoute(val convoId: String)
@Serializable object NewConversationRoute
@Serializable object CreateGroupRoute
@Serializable data class GroupSettingsRoute(val convoId: String)
@Serializable data class JoinGroupRoute(val code: String)
@Serializable data class SearchWithQueryRoute(val query: String)
@Serializable object NearestSheltersRoute
@Serializable data class ShelterDetailRoute(val shelterId: String)
@Serializable data class CompassNavRoute(val shelterId: String)

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

    // Shared NotificationViewModel for unread badge
    val notificationViewModel: NotificationViewModel = hiltViewModel()
    val unreadCount by notificationViewModel.unreadCount.collectAsState()

    // Shared MessagesViewModel — hoisted so its polling drives the Messages tab
    // badge and the app-wide group join-request notification (not just while the
    // Messages screen is open). Passed into MessagesScreen so there's one instance.
    val messagesViewModel: MessagesViewModel = hiltViewModel()
    val messagesUiState by messagesViewModel.uiState.collectAsState()
    val pendingJoinRequests = messagesUiState.conversations.sumOf {
        it.groupInfo?.unreadJoinRequestCount ?: 0
    }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        messagesViewModel.joinRequestNotices.collect { notice ->
            val name = notice.groupName.ifBlank { appContext.getString(R.string.messages_group_unnamed) }
            com.kazahana.app.service.GroupRequestNotifier.notify(
                context = appContext,
                convoId = notice.convoId,
                title = name,
                body = appContext.getString(R.string.group_join_requests_count, notice.count),
            )
        }
    }

    // Current user DID for messages
    val myDid = authViewModel.currentDid

    // Active handle for account switcher button
    val savedAccounts by authViewModel.savedAccounts.collectAsState()
    val activeHandle = remember(myDid, savedAccounts) {
        savedAccounts.find { it.did == myDid }?.handle
    }
    var showAccountSwitcher by remember { mutableStateOf(false) }

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
                is DeepLink.Notification -> {
                    // Switch account if target_did differs from current active
                    val targetDid = deepLink.targetDid
                    if (targetDid != null && targetDid != myDid) {
                        val targetSession = savedAccounts.find { it.did == targetDid }
                        if (targetSession != null) {
                            authViewModel.switchAccount(targetSession)
                        }
                    }
                    // Navigate to notifications tab
                    navController.navigate(NotificationsRoute) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
                is DeepLink.JoinGroup -> navController.navigate(
                    JoinGroupRoute(code = deepLink.code)
                ) { launchSingleTop = true }
                is DeepLink.GroupRequests -> navController.navigate(
                    GroupSettingsRoute(convoId = deepLink.convoId)
                ) { launchSingleTop = true }
            }
        }
    }

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
        || currentDestination?.hasRoute(WatermarkSettingsRoute::class) == true

    // 避難所関連画面（最寄り避難所一覧・避難所詳細・コンパスナビ）ではバナーは冗長なので出さない
    val isOnEvacuation = currentDestination?.hasRoute(NearestSheltersRoute::class) == true
        || currentDestination?.hasRoute(ShelterDetailRoute::class) == true
        || currentDestination?.hasRoute(CompassNavRoute::class) == true

    // 新規投稿 FAB はホーム・プロフィールタブのみ表示（許可リスト方式）。
    // その他のタブ（検索・通知・メッセージ）や、設定・避難所系・スレッド等のあらゆる詳細画面では表示しない。
    val isOnHome = currentDestination?.hasRoute(HomeRoute::class) == true
    val isOnProfileTab = currentDestination?.hasRoute(ProfileRoute::class) == true

    val hideChrome = isOnCompose
    val showFab = (isOnHome || isOnProfileTab) && !hideChrome

    // Evacuation Assist: banner state is owned by a @Singleton manager, so it survives the
    // key(activeAccountDID) recomposition that recreates MainScreen on account switch.
    // Hoisted above Scaffold so the FAB can shift up when the banner is shown.
    val evacuationViewModel: EvacuationViewModel = hiltViewModel()
    val bannerState by evacuationViewModel.bannerState.collectAsState()
    val evacPrefectureOverride by evacuationViewModel.prefectureOverride.collectAsState()
    val evacEnabled by evacuationViewModel.evacuationEnabled.collectAsState()
    val evacContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(evacEnabled) {
        if (evacEnabled) {
            evacuationViewModel.ensurePrefectureResolved(
                com.kazahana.app.ui.evacuation.hasLocationPermission(evacContext)
            )
        }
    }
    val evacLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        evacLifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            evacuationViewModel.expireStaleAlerts()
            while (true) {
                kotlinx.coroutines.delay(com.kazahana.app.data.evacuation.EvacuationConstants.EXPIRY_CHECK_INTERVAL_MS)
                evacuationViewModel.expireStaleAlerts()
            }
        }
    }
    val evacBannerVisible = bannerState.visible && bannerState.highestLevel != null &&
        !hideChrome && !isOnSettings && !isOnEvacuation

    // Measured banner height, provided to screens with bottom-anchored content (e.g. chat)
    // so they can reserve space and avoid being covered by the overlay banner.
    var evacBannerHeightPx by remember { mutableIntStateOf(0) }
    val evacBannerInset = if (evacBannerVisible) {
        with(LocalDensity.current) { evacBannerHeightPx.toDp() }
    } else {
        0.dp
    }

    Scaffold(
        bottomBar = {
            if (!hideChrome) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(item.route::class)
                        } == true

                        val isNotifications = item.route is NotificationsRoute
                        val isMessages = item.route is MessagesRoute
                        val badgeCount = when {
                            isNotifications -> unreadCount
                            isMessages -> pendingJoinRequests
                            else -> 0
                        }

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
                                if (badgeCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(
                                                    if (badgeCount > 99) "99+" else badgeCount.toString()
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
            if (showFab) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(ComposeRoute) {
                            launchSingleTop = true
                        }
                    },
                    // バナー表示中は FAB を上にずらして重なりを避ける
                    modifier = if (evacBannerVisible) {
                        Modifier.offset(y = (-76).dp)
                    } else {
                        Modifier
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

        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.kazahana.app.ui.common.LocalEvacBannerInset provides evacBannerInset,
        ) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
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
                    viewModel = messagesViewModel,
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
                    onNewGroup = {
                        navController.navigate(CreateGroupRoute) {
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
                    onWatermarkSettings = {
                        navController.navigate(WatermarkSettingsRoute) {
                            launchSingleTop = true
                        }
                    },
                    onViewShelters = {
                        navController.navigate(NearestSheltersRoute) {
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
            composable<WatermarkSettingsRoute> {
                WatermarkSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<NearestSheltersRoute> {
                NearestSheltersScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onShelterClick = { shelterId ->
                        navController.navigate(ShelterDetailRoute(shelterId = shelterId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<ShelterDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ShelterDetailRoute>()
                ShelterDetailScreen(
                    shelterId = route.shelterId,
                    onNavigateBack = { navController.popBackStack() },
                    onCompassNav = { shelterId ->
                        navController.navigate(CompassNavRoute(shelterId = shelterId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<CompassNavRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<CompassNavRoute>()
                CompassNavScreen(
                    shelterId = route.shelterId,
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
            composable<CreateGroupRoute> {
                CreateGroupScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onGroupCreated = { convoId ->
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
                    onJoinLink = { code ->
                        navController.navigate(JoinGroupRoute(code = code)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenGroupSettings = {
                        navController.navigate(GroupSettingsRoute(convoId = route.convoId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<GroupSettingsRoute> {
                GroupSettingsScreen(
                    myDid = myDid,
                    onNavigateBack = { navController.popBackStack() },
                    onLeft = {
                        // Pop the settings + chat screens back to the conversation list.
                        navController.popBackStack(MessagesRoute, inclusive = false)
                    },
                    onProfileClick = { did ->
                        navController.navigate(ProfileDetailRoute(actorDid = did)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<JoinGroupRoute> {
                JoinGroupScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onJoined = { convoId ->
                        navController.popBackStack()
                        navController.navigate(ChatRoute(convoId = convoId)) {
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
        } // CompositionLocalProvider(LocalEvacBannerInset)

        // Evacuation banner overlay (above the bottom navigation bar)
        // 設定画面・避難所関連画面では非表示（evacBannerVisible に集約）
        val bannerLevel = bannerState.highestLevel
        if (evacBannerVisible && bannerLevel != null) {
            EvacuationBannerView(
                highestLevel = bannerLevel,
                prefecture = evacPrefectureOverride.ifEmpty { null },
                onClick = {
                    navController.navigate(NearestSheltersRoute) { launchSingleTop = true }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { evacBannerHeightPx = it.height },
            )
        }

        // 初回オンボーディング: 一度だけ控えめに案内（オンは強制しない）
        val onboardingShown by evacuationViewModel.onboardingShown.collectAsState()
        if (!onboardingShown && !evacEnabled) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { evacuationViewModel.markOnboardingShown() },
                title = { Text(stringResource(R.string.evacuation_onboarding_title)) },
                text = { Text(stringResource(R.string.evacuation_onboarding_message)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { evacuationViewModel.markOnboardingShown() },
                    ) {
                        Text(stringResource(R.string.evacuation_onboarding_dismiss))
                    }
                },
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
