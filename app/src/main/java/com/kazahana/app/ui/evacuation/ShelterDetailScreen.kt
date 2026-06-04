package com.kazahana.app.ui.evacuation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.data.model.Hazard
import com.kazahana.app.data.model.Shelter

private fun isDeviceOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelterDetailScreen(
    shelterId: String,
    onNavigateBack: () -> Unit,
    onCompassNav: (String) -> Unit,
    viewModel: EvacuationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val online = isDeviceOnline(context)
    val currentLocation by viewModel.currentLocation.collectAsState()

    val shelter by produceState<Shelter?>(initialValue = null, shelterId) {
        value = viewModel.shelterById(shelterId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.evacuation_nearest_shelters)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val s = shelter
        if (s == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {}
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = s.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            val distance = currentLocation?.let {
                com.kazahana.app.data.evacuation.ShelterRepository.haversineDistance(
                    it.lat, it.lng, s.lat, s.lng,
                )
            }
            if (distance != null) {
                Text(
                    text = "${stringResource(R.string.evacuation_straight_line_distance)}: ${formatDistance(distance)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
            }

            // Supported hazards
            Text(
                text = stringResource(R.string.evacuation_supported_hazards),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            val supported = Hazard.entries.filter { s.hazards.supports(it) }
            // map はインライン関数なので stringResource を @Composable コンテキストで呼べる。
            // joinToString は非インラインのため lambda 内では呼べない。
            val supportedLabels = supported.map { stringResource(hazardLabelRes(it)) }
            Text(
                text = if (supportedLabels.isEmpty()) "—" else supportedLabels.joinToString("、"),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(24.dp))

            // Navigation buttons
            if (!online) {
                Text(
                    text = stringResource(R.string.evacuation_offline_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.openInMaps(context, s) },
                enabled = online,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Place, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.evacuation_open_in_maps))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onCompassNav(s.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.evacuation_compass_nav))
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            EvacuationDisclaimer()
        }
    }
}
