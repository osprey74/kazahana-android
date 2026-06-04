package com.kazahana.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kazahana.app.data.model.Prefecture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** 緯度経度。 */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * 位置情報・コンパスを集約する @Singleton。
 *
 * - 測位: FusedLocationProviderClient（フォアグラウンド・オンデマンドのみ。バックグラウンド常時測位はしない）。
 * - コンパス: SensorManager TYPE_ROTATION_VECTOR。
 * - 都道府県判定: Geocoder 逆ジオコーディング（オフライン不可）。
 *
 * 測位系メソッドの呼び出し側は ACCESS_FINE/COARSE_LOCATION 権限を保持していること。
 */
@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SensorEventListener {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** この端末がコンパス（回転ベクトルセンサ）を備えるか。 */
    val hasCompass: Boolean get() = rotationSensor != null

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    /** 端末の向き（真北基準、度、0–360）。 */
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    /** コンパス精度（SensorManager.SENSOR_STATUS_*。LOW/UNRELIABLE で警告表示）。 */
    private val _headingAccuracy = MutableStateFlow(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    val headingAccuracy: StateFlow<Int> = _headingAccuracy.asStateFlow()

    private var cancellationTokenSource: CancellationTokenSource? = null

    /** 現在地を一回取得（避難所一覧用）。失敗時は null。 */
    @SuppressLint("MissingPermission")
    suspend fun requestSingleLocation(): GeoPoint? {
        val cts = CancellationTokenSource()
        cancellationTokenSource = cts
        return suspendCancellableCoroutine { cont ->
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    val point = location?.let { GeoPoint(it.latitude, it.longitude) }
                    point?.let { _currentLocation.value = it }
                    cont.resume(point)
                }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }
    }

    /**
     * 連続測位を開始（コンパスナビ用、10m 間隔目安）。コンパスナビ画面表示中のみ有効化する。
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val request = com.google.android.gms.location.LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5_000L,
        )
            .setMinUpdateDistanceMeters(10f)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, context.mainLooper)
    }

    fun stopLocationUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private val locationCallback = object : com.google.android.gms.location.LocationCallback() {
        override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
            result.lastLocation?.let { _currentLocation.value = GeoPoint(it.latitude, it.longitude) }
        }
    }

    // ── Compass ──

    fun startCompass() {
        val sensor = rotationSensor ?: return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopCompass() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthRad = orientation[0]
        val azimuthDeg = ((Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0).toFloat()
        _heading.value = azimuthDeg
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            _headingAccuracy.value = accuracy
        }
    }

    // ── Reverse geocoding ──

    /** 緯度経度から都道府県を判定し jp-xxxx に変換。オフライン時は null。 */
    suspend fun resolvePrefecture(point: GeoPoint): String? {
        val geocoder = Geocoder(context, Locale.JAPAN)
        val adminArea: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(point.lat, point.lng, 1) { addresses ->
                    cont.resume(addresses.firstOrNull()?.adminArea)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(point.lat, point.lng, 1)?.firstOrNull()?.adminArea
                } catch (_: Exception) {
                    null
                }
            }
        }
        return Prefecture.fromJapaneseName(adminArea)?.rawValue
    }
}
