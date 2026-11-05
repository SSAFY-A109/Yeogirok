package com.yeogirokarpoc.geo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.*
import android.location.Location
import android.os.*
import android.util.Log
import android.view.Gravity
import android.widget.*
import com.google.android.gms.location.*
import org.json.JSONObject

/** Main-thread location/map lifecycle. The AR render thread reads only immutable/volatile snapshots. */
class CloudNavigation(private val activity: Activity, viewport: FrameLayout, target: JSONObject?,
                      private val compact: Boolean = false, points: org.json.JSONArray = org.json.JSONArray()) : SensorEventListener {
    private val latitude = target?.getDouble("latitude")
    private val longitude = target?.getDouble("longitude")
    private val hasTarget = target != null
    private val handler = Handler(Looper.getMainLooper())
    private val sensors = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val fused = LocationServices.getFusedLocationProviderClient(activity)
    private var mapView: KakaoWebMap? = null
    private var active = false
    private var destroyed = false
    private var listeningLocation = false
    private var locationCallback: LocationCallback? = null
    private var location: Location? = null
    private var rotation: FloatArray? = null
    private var sensorAtNs = 0L
    private var sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var permissionAsked = false
    private var locationIssue = "현재 위치 측정 중"
    private var manualResolve = false
    @Volatile var canStartResolve = false
        private set
    private var lastStage = ""
    private var placing = false
    @Volatile var visitLocation: String? = null; private set
    private lateinit var mapCard: LinearLayout
    private lateinit var footer: LinearLayout
    private data class ArState(val text: String?, val busy: Boolean, val failed: Boolean, val trackingLost: Boolean, val at: Long,
                              val angle: Double? = null)
    @Volatile private var ar = ArState(null, false, false, false, 0)
    private val mapStatus = text("카카오 미니맵 준비 중…", 11f)
    private val hint = text("탐색 준비 중", 15f)
    private val directionArrow = DirectionArrowView(activity)
    private val locationButton = Button(activity).apply {
        text = "위치 권한 · 측정 재시도"
        setOnClickListener { requestLocation(true) }
    }
    private val startButton = Button(activity).apply {
        text = "현장 도착 · 주변 인식 시작"
        setOnClickListener {
            manualResolve = true
            Log.i("YeogirokCloud", "NAV_MANUAL_SCAN")
            tick()
        }
    }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(Color.WHITE); setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    private val ticker = object : Runnable {
        override fun run() { if (active && !destroyed) { tick(); handler.postDelayed(this, 250) } }
    }
    init {
        viewport.addView(directionArrow, FrameLayout.LayoutParams(dp(128), dp(154), Gravity.CENTER_VERTICAL or Gravity.START).apply {
            leftMargin = dp(12)
        })
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; background = ArTheme(activity).background(Color.WHITE, dp(12).toFloat()); clipToOutline = true; isClickable = true
            addView(mapStatus, LinearLayout.LayoutParams(-1, -2))
        }
        mapCard = card
        viewport.addView(card, FrameLayout.LayoutParams(dp(112), dp(112), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(8); rightMargin = dp(16)
        })
        footer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.argb(235, 17, 24, 39)); isClickable = true
            addView(hint); addView(startButton)
            addView(locationButton)
        }
        viewport.addView(footer, FrameLayout.LayoutParams(if (compact) activity.resources.displayMetrics.widthPixels - dp(96) else -1, -2, Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = dp(72); if (compact) leftMargin = dp(16)
        })
        mapView = KakaoWebMap(activity, target, points)
        card.removeView(mapStatus)
        card.addView(mapView, LinearLayout.LayoutParams(-1, -1))
        if (!hasTarget) { startButton.visibility = android.view.View.GONE; directionArrow.visibility = android.view.View.GONE }
        if (compact) { footer.visibility = android.view.View.GONE; directionArrow.visibility = android.view.View.GONE }
    }

    fun resume() {
        if (destroyed || active) return
        active = true; mapView?.resume()
        sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        requestLocation(!permissionAsked)
        handler.post(ticker)
    }
    fun pause() {
        active = false; handler.removeCallbacks(ticker); sensors.unregisterListener(this)
        visitLocation = null
        locationCallback?.let { fused.removeLocationUpdates(it) }; locationCallback = null
        listeningLocation = false; location = null; rotation = null
        sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        manualResolve = false; canStartResolve = false; ar = ArState(null, false, false, false, 0)
        directionArrow.update(null, false)
        mapView?.pause()
    }
    fun destroy() { pause(); destroyed = true; mapView?.destroy(); mapView = null }
    fun permissionResult() { if (active) requestLocation(false) }
    fun startScan() { manualResolve = true; if (active) tick() }
    fun setInsets(top: Int, bottom: Int) {
        (mapCard.layoutParams as FrameLayout.LayoutParams).apply { topMargin = top + dp(8) }.also { mapCard.layoutParams = it }
        (footer.layoutParams as FrameLayout.LayoutParams).apply { bottomMargin = bottom + dp(72) }.also { footer.layoutParams = it }
    }
    fun startPlacement() {
        placing = true
        mapCard.visibility = android.view.View.GONE
        footer.visibility = android.view.View.GONE
        directionArrow.visibility = android.view.View.GONE
    }
    private fun requestLocation(ask: Boolean) {
        if (!active || destroyed || listeningLocation) return
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationIssue = "위치 권한 없음 · 현장에서는 주변 인식을 직접 시작하세요"
            if (ask) {
                permissionAsked = true
                activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION), 503)
            }
            return
        }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!active || locationCallback !== this) return
                location = result.lastLocation?.let { Location(it) }
                locationIssue = "현재 위치 측정 중"
            }
        }
        locationCallback = callback
        try {
            listeningLocation = true
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L).setMaxUpdateDelayMillis(0).build()
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper()).addOnFailureListener {
                if (locationCallback === callback) {
                    listeningLocation = false; location = null; locationIssue = "현재 위치 요청 실패 · 위치 설정을 확인하고 재시도하세요"
                }
            }
        } catch (_: SecurityException) {
            listeningLocation = false; location = null; locationIssue = "위치 권한을 다시 허용하세요"
        }
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        sensorAccuracy = event.accuracy
        if (event.accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ||
            (event.values.size >= 5 && (!event.values[4].isFinite() || event.values[4] < 0 || event.values[4] > Math.toRadians(25.0)))) {
            rotation = null; return
        }
        rotation = FloatArray(9).also { SensorManager.getRotationMatrixFromVector(it, event.values) }
        sensorAtNs = event.timestamp
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensorAccuracy = accuracy
        if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) rotation = null
    }
    fun updateAr(preciseText: String?, busy: Boolean, failed: Boolean, trackingLost: Boolean, angle: Double?) {
        ar = ArState(preciseText, busy, failed, trackingLost, SystemClock.elapsedRealtime(), angle)
    }
    private fun tick() {
        if (!active || destroyed) return
        val nowNs = SystemClock.elapsedRealtimeNanos(); val now = nowNs / 1_000_000
        val sample = location
        val delta = sample?.let { nowNs - it.elapsedRealtimeNanos }
        val fresh = sample != null && delta != null && delta >= 0 &&
            NavigationPolicy.coordinates(sample.latitude, sample.longitude) &&
            NavigationPolicy.usableLocation(delta / 1_000_000, sample.accuracy, sample.hasAccuracy())
        val distance = if (fresh && hasTarget) NavigationPolicy.distance(sample!!.latitude, sample.longitude, latitude!!, longitude!!) else null
        val sensorAge = nowNs - sensorAtNs
        val heading = if (fresh && rotation != null && sensorAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
            sensorAge >= 0 && sensorAge / 1_000_000 <= NavigationPolicy.MAX_HEADING_AGE_MS) {
            val s = sample!!
            val declination = GeomagneticField(s.latitude.toFloat(), s.longitude.toFloat(),
                if (s.hasAltitude()) s.altitude.toFloat() else 0f, System.currentTimeMillis()).declination
            NavigationPolicy.cameraHeading(rotation!!, declination)
        } else null
        canStartResolve = !hasTarget || manualResolve || (distance != null && distance <= NavigationPolicy.RESOLVE_RADIUS_METERS)
        if (fresh) {
            mapView?.updateLocation(sample!!.latitude, sample.longitude, heading)
        } else mapView?.hideLocation()
        visitLocation = if (fresh && sample!!.time > 0) JSONObject().put("source", "DEVICE_GPS")
            .put("latitude", sample.latitude).put("longitude", sample.longitude).put("accuracyMeters", sample.accuracy.toDouble())
            .put("capturedAt", java.time.Instant.ofEpochMilli(sample.time).toString()).toString() else null
        if (placing) return
        val state = ar
        val liveAr = now - state.at in 0..1_000
        if (compact) {
            startButton.visibility = if (!hasTarget || liveAr && (state.text != null || state.busy || state.trackingLost || state.failed))
                android.view.View.GONE else android.view.View.VISIBLE
            locationButton.visibility = if (!fresh && !(liveAr && state.text != null))
                android.view.View.VISIBLE else android.view.View.GONE
        }
        val coarseAngle = if (fresh && distance != null && distance > sample!!.accuracy + 5 && heading != null)
            NavigationPolicy.wrap(NavigationPolicy.bearing(sample.latitude, sample.longitude, latitude!!, longitude!!) - heading)
            else null
        directionArrow.update(NavigationPolicy.arrowAngle(state.text != null, state.trackingLost, liveAr,
            state.angle, coarseAngle), state.text != null && liveAr)
        startButton.isEnabled = !(liveAr && (state.busy || state.text != null || state.failed || state.trackingLost))
        val coarse = when {
            !hasTarget -> if (fresh) "현재 위치 표시 중 · ＋로 팁을 남겨 보세요" else locationIssue
            !fresh -> if (sample == null) locationIssue else "위치 측정이 오래되었거나 부정확해요 · 현장에서 주변 인식을 시작하세요"
            distance!! <= sample!!.accuracy + 5 -> "탐색 위치 근처예요 · 주변을 비춰 팁을 찾으세요"
            heading == null -> "방향 확인 중 · 카메라를 수평에 가깝게 들고 금속 물체에서 떨어져 주세요"
            else -> NavigationPolicy.turn(NavigationPolicy.wrap(
                NavigationPolicy.bearing(sample.latitude, sample.longitude, latitude!!, longitude!!) - heading)) + " · 대략적인 방향"
        }
        hint.text = when {
            liveAr && state.text != null -> "Cloud 복원 완료\n${state.text}\n실제 앵커 기준 안내"
            liveAr && state.trackingLost -> "앵커 추적 회복 대기 · 원래 주변을 천천히 비춰 주세요"
            liveAr && state.failed -> "Cloud 복원 실패 · 아래 복원 재시도를 눌러 주세요\n$coarse"
            liveAr && state.busy -> "주변 인식 중 · 여러 각도에서 천천히 비춰 주세요\n$coarse"
            else -> coarse
        } + (if (distance != null) "\n탐색 위치까지 약 %.0fm · 현재 위치 추정 ±%.0fm".format(distance, sample!!.accuracy) else "")
        val stage = when {
            liveAr && state.text != null -> "RESOLVED"
            liveAr && state.trackingLost -> "TRACKING_LOST"
            liveAr && state.failed -> "FAILED"
            liveAr && state.busy -> "RESOLVING"
            canStartResolve -> "SCAN_READY"
            !fresh -> "LOCATION_PENDING"
            heading == null -> "HEADING_PENDING"
            else -> "COARSE_GUIDANCE"
        }
        if (stage != lastStage) {
            lastStage = stage
            Log.i("YeogirokCloud", "NAV_STAGE=$stage")
        }
    }
}
