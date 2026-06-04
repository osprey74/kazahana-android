package com.kazahana.app.ui.evacuation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.evacuation.EvacuationAlertManager
import com.kazahana.app.data.evacuation.EvacuationConstants
import com.kazahana.app.data.evacuation.ShelterRepository
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.location.GeoPoint
import com.kazahana.app.data.location.LocationService
import com.kazahana.app.data.model.EvacuationBannerState
import com.kazahana.app.data.model.Hazard
import com.kazahana.app.data.model.Shelter
import com.kazahana.app.data.model.ShelterWithDistance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NearestSheltersUiState(
    val isLocating: Boolean = false,
    val location: GeoPoint? = null,
    val prefecture: String? = null,
    val selectedHazards: Set<Hazard> = Hazard.entries.toSet(),
    val results: List<ShelterWithDistance> = emptyList(),
    val needsLocationPermission: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class EvacuationViewModel @Inject constructor(
    private val alertManager: EvacuationAlertManager,
    private val shelterRepository: ShelterRepository,
    private val locationService: LocationService,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val bannerState: StateFlow<EvacuationBannerState> = alertManager.bannerState

    val hasCompass: Boolean get() = locationService.hasCompass

    val currentLocation = locationService.currentLocation
    val heading = locationService.heading
    val headingAccuracy = locationService.headingAccuracy

    private val _uiState = MutableStateFlow(NearestSheltersUiState())
    val uiState: StateFlow<NearestSheltersUiState> = _uiState.asStateFlow()

    val prefectureOverride: StateFlow<String> = settingsStore.evacuationPrefectureOverride
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val evacuationEnabled: StateFlow<Boolean> = settingsStore.evacuationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // 初期値 true で初回フレームの誤表示を防ぐ（実値 false 到着でダイアログ表示）
    val onboardingShown: StateFlow<Boolean> = settingsStore.evacuationOnboardingShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun markOnboardingShown() {
        viewModelScope.launch { settingsStore.setEvacuationOnboardingShown(true) }
    }

    /**
     * 機能有効時、手動設定が無く権限がある場合に現在地から都道府県を一度だけ解決する。
     * これによりバナーの先回り表示（避難所一覧を開かなくても都道府県が分かる）が成立する。
     */
    fun ensurePrefectureResolved(hasPermission: Boolean) {
        viewModelScope.launch {
            val override = settingsStore.evacuationPrefectureOverride.first()
            if (override.isNotEmpty()) return@launch // override はマネージャが直接参照
            if (!hasPermission) return@launch
            val point = locationService.requestSingleLocation() ?: return@launch
            val pref = locationService.resolvePrefecture(point) ?: return@launch
            alertManager.setLocationDerivedPrefecture(pref)
        }
    }

    init {
        // バナーに有効なアラートがあれば、その災害種別を初期フィルタにする（安全側）
        val alerts = alertManager.bannerState.value.alerts
        if (alerts.isNotEmpty()) {
            val hazards = alerts.flatMap { EvacuationConstants.hazardFilters(it.type) }.toSet()
            if (hazards.isNotEmpty()) {
                _uiState.update { it.copy(selectedHazards = hazards) }
            }
        }
    }

    fun setSelectedHazards(hazards: Set<Hazard>) {
        _uiState.update { it.copy(selectedHazards = hazards) }
        if (_uiState.value.location != null || _uiState.value.prefecture != null) {
            search()
        }
    }

    fun toggleHazard(hazard: Hazard) {
        val current = _uiState.value.selectedHazards
        val next = if (hazard in current) current - hazard else current + hazard
        setSelectedHazards(next)
    }

    /**
     * 位置情報取得 → 都道府県判定 → 最寄り探索。
     * 手動設定の都道府県があればそれを優先。測位拒否時は手動設定にフォールバック。
     */
    fun locateAndSearch(hasPermission: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, error = null, needsLocationPermission = false) }
            val override = settingsStore.evacuationPrefectureOverride.first().ifEmpty { null }

            var point: GeoPoint? = null
            if (hasPermission) {
                point = locationService.requestSingleLocation()
            }

            // 都道府県の解決: 手動設定優先 → 測位逆ジオコーディング
            val prefecture = override ?: point?.let { locationService.resolvePrefecture(it) }
            prefecture?.let { alertManager.setLocationDerivedPrefecture(it) }

            if (point == null && override == null) {
                // 測位できず手動設定もない → 権限案内 or 都道府県設定を促す
                _uiState.update {
                    it.copy(
                        isLocating = false,
                        needsLocationPermission = !hasPermission,
                        loaded = true,
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(location = point, prefecture = prefecture) }
            search()
        }
    }

    private fun search() {
        viewModelScope.launch {
            val state = _uiState.value
            val point = state.location
            val prefecture = state.prefecture
            val hazards = state.selectedHazards.toList()

            // 探索の基準座標: 実測位があればそれ、なければ都道府県の代表点が無いため
            // 測位必須。測位無しで都道府県のみの場合は一覧を出せないため空とする。
            val results = when {
                point != null && prefecture != null ->
                    shelterRepository.findNearest(prefecture, point.lat, point.lng, hazards)
                point != null ->
                    shelterRepository.findNearestAll(point.lat, point.lng, hazards)
                else -> emptyList()
            }
            _uiState.update { it.copy(isLocating = false, results = results, loaded = true) }
        }
    }

    suspend fun shelterById(id: String): Shelter? {
        shelterRepository.ensureLoaded()
        return shelterRepository.shelterById(id)
    }

    /**
     * OS 地図アプリで徒歩ナビを起動。google.navigation 優先、未解決時は geo: にフォールバック。
     *
     * Android 11+ のパッケージ可視性制限により Intent.resolveActivity() は null を返しうるため、
     * resolveActivity でのゲートはせず startActivity を try/catch で直接実行する。
     */
    fun openInMaps(context: Context, shelter: Shelter) {
        val label = Uri.encode(shelter.name)
        val uris = listOf(
            "google.navigation:q=${shelter.lat},${shelter.lng}&mode=w",
            "geo:${shelter.lat},${shelter.lng}?q=${shelter.lat},${shelter.lng}($label)",
        )
        for (uri in uris) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // 次の URI スキームを試す
            }
        }
    }

    // ── Compass nav passthrough ──

    fun startCompassNav() {
        locationService.startCompass()
        locationService.startLocationUpdates()
    }

    fun stopCompassNav() {
        locationService.stopCompass()
        locationService.stopLocationUpdates()
    }

    fun distanceTo(shelter: Shelter): Double? {
        val loc = currentLocation.value ?: return null
        return ShelterRepository.haversineDistance(loc.lat, loc.lng, shelter.lat, shelter.lng)
    }

    fun bearingTo(shelter: Shelter): Double? {
        val loc = currentLocation.value ?: return null
        return ShelterRepository.bearing(loc.lat, loc.lng, shelter.lat, shelter.lng)
    }

    // ── Demo ──

    fun injectTestAlert(level: com.kazahana.app.data.model.AlertLevel, type: String) {
        alertManager.injectTestAlert(level, type)
    }

    fun clearAlerts() {
        alertManager.clearAll()
    }

    fun expireStaleAlerts() {
        alertManager.expireStaleAlerts()
    }
}
