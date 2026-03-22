package com.kazahana.app

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
import com.kazahana.app.ui.navigation.KazahanaNavHost
import com.kazahana.app.ui.theme.KazahanaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved locale before rendering
        val savedLocale = runBlocking { settingsStore.appLocale.first() }
        if (savedLocale.isNotEmpty()) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(savedLocale)
            )
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by settingsStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            KazahanaTheme(themeMode = themeMode) {
                KazahanaNavHost(settingsStore = settingsStore)
            }
        }
    }
}
