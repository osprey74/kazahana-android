package com.kazahana.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.data.local.AppLocale
import com.kazahana.app.data.local.ModerationPref
import com.kazahana.app.data.local.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onLogout: () -> Unit = {},
    onFeedManagement: () -> Unit = {},
    onBsafBots: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Section 1: Display ──
            SectionHeader(stringResource(R.string.settings_theme))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.themeMode == ThemeMode.SYSTEM,
                    onClick = { viewModel.setTheme(ThemeMode.SYSTEM) },
                    label = { Text(stringResource(R.string.settings_system)) },
                )
                FilterChip(
                    selected = uiState.themeMode == ThemeMode.LIGHT,
                    onClick = { viewModel.setTheme(ThemeMode.LIGHT) },
                    label = { Text(stringResource(R.string.settings_light)) },
                )
                FilterChip(
                    selected = uiState.themeMode == ThemeMode.DARK,
                    onClick = { viewModel.setTheme(ThemeMode.DARK) },
                    label = { Text(stringResource(R.string.settings_dark)) },
                )
            }

            HorizontalDivider()

            // ── Section 2: Language ──
            SectionHeader(stringResource(R.string.settings_language))
            LanguageDropdown(
                selected = uiState.appLocale,
                onSelect = { viewModel.setAppLocale(it) },
            )

            HorizontalDivider()

            // ── Section 3: Feed Management ──
            SectionHeader(stringResource(R.string.settings_feed_management))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onFeedManagement)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_feed_management),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            HorizontalDivider()

            // ── Section: Polling Interval ──
            SectionHeader(stringResource(R.string.settings_polling_interval))
            PollingIntervalDropdown(
                selectedSeconds = uiState.pollIntervalSeconds,
                onSelect = { viewModel.setPollInterval(it) },
            )

            HorizontalDivider()

            // ── Section: Post ──
            SectionHeader(stringResource(R.string.settings_post))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setShowVia(!uiState.showVia) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_show_via),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = uiState.showVia,
                    onCheckedChange = { viewModel.setShowVia(it) },
                )
            }

            HorizontalDivider()

            // ── Section: BSAF ──
            SectionHeader("BSAF")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setBsafEnabled(!uiState.bsafEnabled) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.bsaf_enable),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = uiState.bsafEnabled,
                    onCheckedChange = { viewModel.setBsafEnabled(it) },
                )
            }
            if (uiState.bsafEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onBsafBots)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bsaf_manage_bots),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            HorizontalDivider()

            // ── Section: Claude API ──
            SectionHeader(stringResource(R.string.settings_claude_api))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    value = uiState.claudeApiKey,
                    onValueChange = { viewModel.setClaudeApiKey(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-ant-...") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (uiState.claudeApiKey.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.clearClaudeApiKey() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.settings_claude_api_revoke))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_claude_api_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            HorizontalDivider()

            // ── Section 4: Content Moderation ──
            SectionHeader(stringResource(R.string.settings_moderation))

            // ── Adult Content category ──
            Text(
                text = stringResource(R.string.settings_moderation_adult),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Adult content toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setAdultContentEnabled(!uiState.adultContentEnabled) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_adult_content),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = uiState.adultContentEnabled,
                    onCheckedChange = { viewModel.setAdultContentEnabled(it) },
                )
            }

            // Adult content labels (conditional)
            if (uiState.adultContentEnabled) {
                ModerationRow(
                    label = stringResource(R.string.settings_label_porn),
                    pref = uiState.pornPref,
                    onPrefChange = { viewModel.setModerationPref("porn", it) },
                )
                ModerationRow(
                    label = stringResource(R.string.settings_label_sexual),
                    pref = uiState.sexualPref,
                    onPrefChange = { viewModel.setModerationPref("sexual", it) },
                )
                ModerationRow(
                    label = stringResource(R.string.settings_label_nudity),
                    pref = uiState.nudityPref,
                    onPrefChange = { viewModel.setModerationPref("nudity", it) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Graphic Content category ──
            Text(
                text = stringResource(R.string.settings_moderation_graphic),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            ModerationRow(
                label = stringResource(R.string.settings_label_graphic_media),
                pref = uiState.graphicMediaPref,
                onPrefChange = { viewModel.setModerationPref("graphic-media", it) },
            )
            ModerationRow(
                label = stringResource(R.string.settings_label_gore),
                pref = uiState.gorePref,
                onPrefChange = { viewModel.setModerationPref("gore", it) },
            )

            HorizontalDivider()

            // ── Section 4: Account ──
            SectionHeader(stringResource(R.string.settings_account))
            Text(
                text = stringResource(R.string.settings_logout),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLogout)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            // ── Section 5: Support ──
            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_support))
            Text(
                text = stringResource(R.string.settings_support_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            KofiWidget(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Section 6: App Info ──
            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_app_info))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    text = "0.1.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Bluesky",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    text = "@app-kazahana.bsky.social",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LanguageDropdown(
    selected: AppLocale,
    onSelect: (AppLocale) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localeDisplayName(selected),
                style = MaterialTheme.typography.bodyLarge,
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppLocale.entries.forEach { locale ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = localeDisplayName(locale),
                                fontWeight = if (locale == selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (locale == selected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(locale)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun localeDisplayName(locale: AppLocale): String {
    return when (locale) {
        AppLocale.SYSTEM -> stringResource(R.string.lang_system)
        AppLocale.JA -> stringResource(R.string.lang_ja)
        AppLocale.EN -> stringResource(R.string.lang_en)
        AppLocale.PT -> stringResource(R.string.lang_pt)
        AppLocale.DE -> stringResource(R.string.lang_de)
        AppLocale.ZH_TW -> stringResource(R.string.lang_zh_tw)
        AppLocale.ZH_CN -> stringResource(R.string.lang_zh_cn)
        AppLocale.FR -> stringResource(R.string.lang_fr)
        AppLocale.KO -> stringResource(R.string.lang_ko)
        AppLocale.ES -> stringResource(R.string.lang_es)
        AppLocale.RU -> stringResource(R.string.lang_ru)
        AppLocale.ID -> stringResource(R.string.lang_id)
    }
}

@Composable
private fun PollingIntervalDropdown(
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(0, 30, 60, 90, 120)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pollingIntervalLabel(selectedSeconds),
                style = MaterialTheme.typography.bodyLarge,
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { seconds ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pollingIntervalLabel(seconds),
                                fontWeight = if (seconds == selectedSeconds) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (seconds == selectedSeconds) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(seconds)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun pollingIntervalLabel(seconds: Int): String {
    return when (seconds) {
        0 -> stringResource(R.string.settings_polling_off)
        30 -> stringResource(R.string.settings_polling_30s)
        60 -> stringResource(R.string.settings_polling_60s)
        90 -> stringResource(R.string.settings_polling_90s)
        120 -> stringResource(R.string.settings_polling_120s)
        else -> stringResource(R.string.settings_polling_60s)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ModerationRow(
    label: String,
    pref: ModerationPref,
    onPrefChange: (ModerationPref) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = pref == ModerationPref.HIDE,
                onClick = { onPrefChange(ModerationPref.HIDE) },
                label = { Text(stringResource(R.string.settings_pref_hide)) },
            )
            FilterChip(
                selected = pref == ModerationPref.WARN,
                onClick = { onPrefChange(ModerationPref.WARN) },
                label = { Text(stringResource(R.string.settings_pref_warn)) },
            )
            FilterChip(
                selected = pref == ModerationPref.SHOW,
                onClick = { onPrefChange(ModerationPref.SHOW) },
                label = { Text(stringResource(R.string.settings_pref_show)) },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KofiWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            WebView(it).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        request?.url?.let { uri ->
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            )
                        }
                        return true
                    }
                }
                loadDataWithBaseURL(
                    "https://ko-fi.com",
                    """
                    <!DOCTYPE html>
                    <html><head>
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <style>body{margin:0;background:transparent;text-align:right;}</style>
                    </head><body>
                    <script type='text/javascript' src='https://storage.ko-fi.com/cdn/widget/Widget_2.js'></script>
                    <script type='text/javascript'>kofiwidget2.init('Support me on Ko-fi', '#72a4f2', 'A0A71UNW9H');kofiwidget2.draw();</script>
                    </body></html>
                    """.trimIndent(),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
    )
}
