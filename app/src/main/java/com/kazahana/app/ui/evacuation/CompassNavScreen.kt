package com.kazahana.app.ui.evacuation

import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.data.evacuation.ShelterRepository
import com.kazahana.app.data.model.Shelter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassNavScreen(
    shelterId: String,
    onNavigateBack: () -> Unit,
    viewModel: EvacuationViewModel = hiltViewModel(),
) {
    val shelter by produceState<Shelter?>(initialValue = null, shelterId) {
        value = viewModel.shelterById(shelterId)
    }
    val location by viewModel.currentLocation.collectAsState()
    val heading by viewModel.heading.collectAsState()
    val headingAccuracy by viewModel.headingAccuracy.collectAsState()

    // 画面表示中のみセンサ・連続測位を有効化（離脱で停止＝バッテリー節約）
    DisposableEffect(Unit) {
        viewModel.startCompassNav()
        onDispose { viewModel.stopCompassNav() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.evacuation_compass_nav)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!viewModel.hasCompass) {
                // コンパス非搭載端末: 地図委譲へ誘導
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.evacuation_compass_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.evacuation_use_map_app_instead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                return@Column
            }

            if (s != null) {
                Text(
                    text = s.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(32.dp))

                val bearing = location?.let {
                    ShelterRepository.bearing(it.lat, it.lng, s.lat, s.lng)
                }
                val rotation = if (bearing != null) (bearing - heading).toFloat() else 0f
                val animatedRotation by animateFloatAsState(
                    targetValue = rotation,
                    animationSpec = tween(durationMillis = 300),
                    label = "arrow",
                )

                Icon(
                    Icons.Default.Navigation,
                    contentDescription = null,
                    tint = if (location != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(120.dp)
                        .rotate(animatedRotation),
                )

                Spacer(Modifier.height(24.dp))

                val distance = location?.let {
                    ShelterRepository.haversineDistance(it.lat, it.lng, s.lat, s.lng)
                }
                if (distance != null) {
                    Text(
                        text = formatDistance(distance),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.evacuation_locating),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                // キャリブレーション不良警告
                if (headingAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
                    headingAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.evacuation_compass_calibration),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEA580C),
                    )
                }

                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.evacuation_compass_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
