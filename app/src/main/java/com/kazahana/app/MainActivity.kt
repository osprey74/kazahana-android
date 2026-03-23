package com.kazahana.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.local.ThemeMode
import com.kazahana.app.ui.navigation.DeepLink
import com.kazahana.app.ui.navigation.DeepLinkHandler
import com.kazahana.app.ui.navigation.KazahanaNavHost
import com.kazahana.app.ui.theme.KazahanaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    private val _deepLinkChannel = Channel<DeepLink>(capacity = Channel.BUFFERED)
    val deepLinkFlow = _deepLinkChannel.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved locale before rendering
        val savedLocale = runBlocking { settingsStore.appLocale.first() }
        if (savedLocale.isNotEmpty()) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(savedLocale)
            )
        }

        // Parse initial deep link / share intent
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by settingsStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            KazahanaTheme(themeMode = themeMode) {
                KazahanaNavHost(
                    settingsStore = settingsStore,
                    deepLinkFlow = deepLinkFlow,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        DeepLinkHandler.parse(intent)?.let { deepLink ->
            _deepLinkChannel.trySend(deepLink)
        }
    }
}
