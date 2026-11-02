package com.yeogirokarpoc.geo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class GeoMapActivity : Activity() {
    private lateinit var record: JSONObject
    private lateinit var map: KakaoWebMap
    private lateinit var save: Button
    private lateinit var status: TextView
    private var locationRequest: CancellationTokenSource? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        record = try { CloudRecord.parse(savedInstanceState?.getString("record") ?: intent.getStringExtra("record") ?: "") }
            catch (_: Exception) { finish(); return }
        val theme = ArTheme(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; fitsSystemWindows = true; setBackgroundColor(theme.card)
        }
        status = TextView(this).apply {
            text = "지도에서 팁을 찾아올 위치를 눌러 주세요. 실제 AR 위치와는 별도의 탐색 좌표예요."
            textSize = 14f; setTextColor(theme.foreground); setPadding(24, 24, 24, 24)
        }
        layout.addView(status)
        layout.addView(Button(this).apply { text = "현재 위치로 이동"; setOnClickListener { locate(true) } })
        val selected = record.optJSONObject("discoveryLocation")
        map = KakaoWebMap(this, selected, JSONArray().also { if (selected != null) it.put(selected) }) { lat, lng ->
            record.put("discoveryLocation", JSONObject().put("latitude", lat).put("longitude", lng)
                .put("source", "MAP_SELECTED").put("uncertaintyMeters", JSONObject.NULL)
                .put("capturedAt", Instant.now().toString()))
            save.isEnabled = true; status.text = "탐색 위치를 선택했어요. 저장하면 미니맵에 표시돼요."
        }
        layout.addView(map, LinearLayout.LayoutParams(-1, 0, 1f))
        save = Button(this).apply {
            text = "이 탐색 위치 저장"; isEnabled = selected != null
            setOnClickListener {
                CloudRecord.parse(record.toString())
                setResult(RESULT_OK, Intent().putExtra("record", record.toString())); finish()
            }
        }
        layout.addView(save)
        layout.addView(Button(this).apply { text = "취소"; setOnClickListener { finish() } })
        setContentView(layout)
        locate(true)
    }
    private fun locate(ask: Boolean) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ask) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 503)
            else status.text = "위치 권한이 없어요. 지도를 움직여 탐색 위치를 직접 선택할 수 있어요."
            return
        }
        locationRequest?.cancel()
        val request = CancellationTokenSource(); locationRequest = request
        LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, request.token)
            .addOnSuccessListener { location ->
                if (locationRequest !== request || isFinishing || isDestroyed) return@addOnSuccessListener
                if (location != null) map.updateLocation(location.latitude, location.longitude, null)
                else status.text = "현재 위치를 찾지 못했어요. 위치를 다시 측정하거나 지도에서 선택해 주세요."
            }.addOnFailureListener {
                if (!isFinishing && !isDestroyed) status.text = "위치를 확인하지 못했어요. 다시 시도해 주세요."
            }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 503) locate(false)
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("record", record.toString()); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); if (::map.isInitialized) map.resume() }
    override fun onPause() { locationRequest?.cancel(); locationRequest = null; if (::map.isInitialized) map.pause(); super.onPause() }
    override fun onDestroy() { if (::map.isInitialized) map.destroy(); super.onDestroy() }
}
