package com.yeogirokarpoc.geo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ViewManager

class GeoValidationPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(GeoValidationModule(context), TipExperienceModule(context))
    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}

class GeoValidationModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    private var pending: Promise? = null
    private var pendingCloud = false
    private var publicReply: ResultReceiver? = null
    private val requestCode = 9107
    private val listener = object : BaseActivityEventListener() {
        override fun onActivityResult(activity: Activity, request: Int, result: Int, data: Intent?) {
            if (request != requestCode) return
            val promise = pending ?: return
            pending = null
            publicReply = null
            if (result == Activity.RESULT_OK) {
                val raw = data?.getStringExtra("record")
                try {
                    require(raw != null)
                    val record = if (pendingCloud) CloudRecord.parse(raw).toString() else GeoRecord.parse(raw).toString()
                    context.getSharedPreferences(GeoRecord.PREFS, 0).edit()
                        .putString(if (pendingCloud) CloudRecord.LAST_RECORD else GeoRecord.LAST_RECORD, record).apply()
                    promise.resolve(record)
                } catch (_: Exception) {
                    promise.reject("INVALID_RESULT", "유효한 지리 좌표 기록을 받지 못했습니다.")
                }
            } else {
                promise.resolve(null)
            }
        }
    }
    init { context.addActivityEventListener(listener) }
    override fun getName() = "GeoValidation"

    @ReactMethod fun getStatus(promise: Promise) {
        val result = Arguments.createMap()
        // Build authentication mode only; successful Cloud operations verify OAuth registration.
        result.putString("arcoreAuthMode", if (GeoRecord.key(context, "com.google.android.ar.API_KEY").isBlank()) "KEYLESS" else "API_KEY")
        result.putBoolean("kakaoKeyConfigured", GeoRecord.key(context, "com.yeogirokarpoc.KAKAO_NATIVE_APP_KEY").isNotBlank())
        result.putString("arcoreVersion", "1.57.0")
        result.putString("kakaoVersion", "javascript-webview")
        result.putString("deviceLabel", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        promise.resolve(result)
    }

    @ReactMethod fun getLastRecord(promise: Promise) {
        promise.resolve(context.getSharedPreferences(GeoRecord.PREFS, 0).getString(GeoRecord.LAST_RECORD, null))
    }

    @ReactMethod fun getLastCloudRecord(promise: Promise) {
        promise.resolve(context.getSharedPreferences(GeoRecord.PREFS, 0).getString(CloudRecord.LAST_RECORD, null))
    }

    @ReactMethod fun openCloud(restoreJson: String?, promise: Promise) {
        launch(GeoArActivity::class.java, restoreJson, "restoreRecord", promise, cloudMode = true)
    }

    @ReactMethod fun openTipPlacement(promise: Promise) {
        launch(GeoArActivity::class.java, null, "restoreRecord", promise, cloudMode = true, productMode = true)
    }

    @ReactMethod fun openTipExplorer(selectedId: String?, promise: Promise) {
        launch(GeoArActivity::class.java, null, "restoreRecord", promise, cloudMode = true, productMode = true, explorerMode = true, selectedTipId = selectedId)
    }

    private fun publicTips(raw: String): org.json.JSONArray {
        val tips = org.json.JSONArray(raw)
        require(tips.length() <= 10)
        for (i in 0 until tips.length()) {
            val tip = tips.getJSONObject(i)
            require(tip.getString("id").toLong() > 0 && tip.getBoolean("publicTip"))
            CloudRecord.parse(tip.getJSONObject("cloudRecord").toString(), forResolve = true)
        }
        return tips
    }

    @ReactMethod fun openPublicTipExplorer(raw: String, location: String, sessionId: String, promise: Promise) {
        try {
            val tips = publicTips(raw)
            val origin = org.json.JSONObject(location)
            require(NavigationPolicy.coordinates(origin.getDouble("latitude"), origin.getDouble("longitude")))
            launch(GeoArActivity::class.java, null, "restoreRecord", promise, cloudMode = true,
                productMode = true, explorerMode = true, publicTips = tips.toString(), searchLocation = location, publicSessionId = sessionId)
        } catch (_: Exception) { promise.reject("INVALID_RECORD", "공개 팁의 공간 정보를 확인하지 못했어요.") }
    }

    @ReactMethod fun updatePublicTips(requestId: String, raw: String?, error: String?, promise: Promise) {
        try {
            val tips = raw?.let { publicTips(it) }
            context.runOnUiQueueThread {
                val receiver = publicReply
                receiver?.send(0, Bundle().apply {
                    putString("requestId", requestId); putString("tips", tips?.toString()); putString("error", error)
                })
                promise.resolve(receiver != null)
            }
        } catch (_: Exception) { promise.reject("INVALID_RECORD", "공개 팁의 갱신 정보를 확인하지 못했어요.") }
    }

    @ReactMethod fun openCloudMap(raw: String, promise: Promise) {
        if (GeoRecord.key(context, "com.yeogirokarpoc.KAKAO_NATIVE_APP_KEY").isBlank()) {
            promise.reject("KAKAO_KEY_MISSING", "카카오 키 설정 후 Android 재빌드가 필요합니다."); return
        }
        launch(GeoMapActivity::class.java, raw, "record", promise, cloudMode = true)
    }

    @ReactMethod fun openCloudNavigation(raw: String, promise: Promise) {
        launch(GeoArActivity::class.java, raw, "restoreRecord", promise, cloudMode = true, navigationMode = true)
    }

    @ReactMethod fun clearLastRecord(promise: Promise) {
        context.getSharedPreferences(GeoRecord.PREFS, 0).edit().remove(GeoRecord.LAST_RECORD).apply()
        promise.resolve(null)
    }

    @ReactMethod fun saveRecord(raw: String, promise: Promise) {
        try {
            val validated = GeoRecord.parse(raw).toString()
            context.getSharedPreferences(GeoRecord.PREFS, 0).edit().putString(GeoRecord.LAST_RECORD, validated).apply()
            promise.resolve(validated)
        } catch (_: Exception) { promise.reject("INVALID_RECORD", "기록을 저장할 수 없습니다.") }
    }
    @ReactMethod fun openGeo(restoreJson: String?, promise: Promise) {
        launch(GeoArActivity::class.java, restoreJson, "restoreRecord", promise)
    }

    @ReactMethod fun openMap(recordJson: String, promise: Promise) {
        if (GeoRecord.key(context, "com.yeogirokarpoc.KAKAO_NATIVE_APP_KEY").isBlank()) {
            promise.reject("KAKAO_KEY_MISSING", "android/local.properties에 KAKAO_NATIVE_APP_KEY를 설정하고 다시 빌드하세요.")
            return
        }
        launch(GeoMapActivity::class.java, recordJson, "record", promise)
    }

    private fun launch(target: Class<out Activity>, raw: String?, extra: String, promise: Promise, cloudMode: Boolean = false,
                       navigationMode: Boolean = false, productMode: Boolean = false, explorerMode: Boolean = false, selectedTipId: String? = null,
                       publicTips: String? = null, searchLocation: String? = null, publicSessionId: String? = null) {
        val activity = context.currentActivity
        if (activity == null) { promise.reject("NO_ACTIVITY", "앱 화면이 준비되지 않았습니다."); return }
        val validated = try { raw?.let {
            if (cloudMode) CloudRecord.parse(it, forResolve = target == GeoArActivity::class.java).also { record ->
                if (navigationMode) require(record.has("discoveryLocation"))
            }.toString() else GeoRecord.parse(it).toString()
        } } catch (_: Exception) {
            promise.reject("INVALID_RECORD", "JSON 형식·값 또는 Cloud Anchor의 예상 수명을 확인하세요."); return
        }
        activity.runOnUiThread {
            if (pending != null) { promise.reject("BUSY", "이미 검증 화면이 열려 있습니다."); return@runOnUiThread }
            pending = promise
            pendingCloud = cloudMode
            publicReply = null
            try {
                activity.startActivityForResult(Intent(activity, target).apply {
                    validated?.let { putExtra(extra, it) }
                    putExtra("cloudMode", cloudMode)
                    putExtra("navigationMode", navigationMode)
                    putExtra("productMode", productMode)
                    putExtra("explorerMode", explorerMode)
                    if (explorerMode) putExtra("tips", publicTips ?: LocalTipStore.list(context).toString())
                    putExtra("publicTips", publicTips != null)
                    searchLocation?.let { putExtra("searchLocation", it) }
                    publicSessionId?.let { putExtra("publicSessionId", it) }
                    if (publicTips != null) putExtra("publicEvents", object : ResultReceiver(Handler(Looper.getMainLooper())) {
                        @Suppress("DEPRECATION")
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            if (pending !== promise) return
                            val event = resultData?.getString("event") ?: return
                            publicReply = resultData.getParcelable("reply") ?: return
                            // React exists only in the main process; the AR process communicates over Binder.
                            context.emitDeviceEvent("ArTipNearbyRefresh", event)
                        }
                    }.forIpc())
                    selectedTipId?.let { putExtra("selectedTipId", it) }
                }, requestCode)
            } catch (_: Exception) {
                pending = null
                publicReply = null
                promise.reject("OPEN_FAILED", "검증 화면을 열지 못했습니다.")
            }
        }
    }

    override fun invalidate() {
        context.removeActivityEventListener(listener)
        pending?.reject("RELOADED", "앱이 다시 로드되어 검증 요청이 취소됐습니다.")
        pending = null
        publicReply = null
        super.invalidate()
    }
}
