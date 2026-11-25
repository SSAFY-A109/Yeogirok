package com.yeogirokarpoc.geo

import android.content.Context
import android.content.pm.PackageManager
import com.google.ar.core.GeospatialPose
import com.google.ar.core.Pose
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

object GeoRecord {
    const val KIND = "yeogirok-geospatial-placement"
    const val PREFS = "yeogirok_geo_validation"
    // Only the geospatial process reads/writes consent; records remain main-process owned.
    const val CONSENT_PREFS = "yeogirok_geo_consent"
    const val LAST_RECORD = "last_confirmed_record"

    fun key(context: Context, name: String): String =
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString(name).orEmpty().trim()

    private fun finite(value: Double): Double {
        require(value.isFinite()) { "유효하지 않은 좌표/정확도입니다." }
        return value
    }

    fun jsonArray(values: FloatArray): JSONArray = JSONArray(values.map { finite(it.toDouble()) })

    private fun coordinate(pose: GeospatialPose) = JSONObject()
        .put("latitude", finite(pose.latitude))
        .put("longitude", finite(pose.longitude))
        .put("altitude", finite(pose.altitude))
        .put("altitudeReference", "WGS84_ELLIPSOID")
        .put("eusQuaternion", jsonArray(pose.eastUpSouthQuaternion))

    fun create(sessionId: String, local: Pose, tip: GeospatialPose, camera: GeospatialPose,
               frameTimestamp: Long, restoredFrom: String?): JSONObject {
        return JSONObject()
            .put("schemaVersion", 2).put("kind", KIND)
            .put("recordId", UUID.randomUUID().toString()).put("sessionId", sessionId)
            .put("confirmedAt", Instant.now().toString())
            .put("frameTimestampNs", frameTimestamp.toString())
            .put("source", "ARCore.Earth.getGeospatialPose(anchor.pose)")
            .put("tip", coordinate(tip))
            .put("cameraAtCapture", coordinate(camera))
            .put("accuracy", JSONObject()
                .put("horizontalMeters", finite(tip.horizontalAccuracy))
                .put("verticalMeters", finite(tip.verticalAccuracy))
                .put("yawDegrees", finite(tip.orientationYawAccuracy))
                .put("confidence", 0.68).put("source", "ARCore-GeospatialPose")
                .put("measuredError", JSONObject.NULL))
            .put("localPose", JSONObject().put("frame", "native-arcore-session")
                .put("positionMeters", jsonArray(local.translation))
                .put("rotationQuaternionXYZW", jsonArray(local.rotationQuaternion))
                .put("referencePoint", "pin-tip"))
            .put("tracking", JSONObject().put("camera", "TRACKING").put("earth", "TRACKING")
                .put("earthState", "ENABLED").put("anchor", "TRACKING"))
            .put("restoredFrom", restoredFrom ?: JSONObject.NULL)
            .put("crossDeviceVerified", false)
            .put("qualityThresholdPolicy", "not-configured")
    }

    fun validateCoordinate(value: JSONObject) {
        val latitude = value.getDouble("latitude")
        val longitude = value.getDouble("longitude")
        require(latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0) { "위도·경도 범위가 올바르지 않습니다." }
    }

    fun parse(raw: String): JSONObject {
        require(raw.length <= 65536) { "기록이 너무 큽니다." }
        val record = JSONObject(raw)
        require(record.getInt("schemaVersion") == 2 && record.getString("kind") == KIND) {
            "지리 좌표 검증 기록만 사용할 수 있습니다. Viro 세션 XYZ 기록은 사용할 수 없습니다."
        }
        val tip = record.getJSONObject("tip")
        validateCoordinate(tip)
        require(abs(tip.getDouble("latitude")) < 89.9) { "극지방 근처는 이 PoC에서 지원하지 않습니다." }
        finite(tip.getDouble("altitude"))
        require(tip.getString("altitudeReference") == "WGS84_ELLIPSOID") { "고도 기준이 다릅니다." }
        val quaternion = tip.getJSONArray("eusQuaternion")
        require(quaternion.length() == 4)
        var sum = 0.0
        for (i in 0..3) { val v = finite(quaternion.getDouble(i)); sum += v * v }
        require(abs(sqrt(sum) - 1.0) < 0.01) { "방향 쿼터니언이 올바르지 않습니다." }
        val accuracy = record.getJSONObject("accuracy")
        for (field in listOf("horizontalMeters", "verticalMeters", "yawDegrees")) {
            require(finite(accuracy.getDouble(field)) >= 0.0) { "정확도 값이 올바르지 않습니다." }
        }
        require(record.getString("sessionId").isNotBlank())
        require(accuracy.getDouble("confidence") == 0.68)
        require(accuracy.getString("source").isNotBlank())
        accuracy.put("measuredError", JSONObject.NULL)
        record.put("crossDeviceVerified", false)
        require(record.getString("recordId").isNotBlank())
        Instant.parse(record.getString("confirmedAt"))
        record.optJSONObject("cameraAtCapture")?.let { validateCoordinate(it) }
        record.optJSONObject("reference")?.let {
            validateCoordinate(it)
            require(it.getString("source") in listOf("map-tap", "manual-coordinate", "surveyed"))
            require(it.getString("sourceLabel").isNotBlank())
            Instant.parse(it.getString("capturedAt"))
            require(it.has("uncertaintyMeters"))
            if (!it.isNull("uncertaintyMeters")) require(finite(it.getDouble("uncertaintyMeters")) >= 0)
        }
        record.optJSONObject("restorationEstimate")?.let {
            require(!record.isNull("restoredFrom") && record.getString("restoredFrom").isNotBlank())
            require(finite(it.getDouble("horizontalOffsetMeters")) >= 0)
            finite(it.getDouble("verticalOffsetMeters"))
            require(it.getString("method") == "arcore-plane-hit-test")
            require(it.getString("deviceLabel").isNotBlank())
            require(it.getBoolean("groundTruth") == false)
            Instant.parse(it.getString("estimatedAt"))
        }
        record.optJSONObject("restorationObservation")?.let {
            require(!record.isNull("restoredFrom") && record.getString("restoredFrom").isNotBlank())
            require(finite(it.getDouble("horizontalOffsetMeters")) >= 0)
            require(it.has("verticalOffsetMeters"))
            if (!it.isNull("verticalOffsetMeters")) finite(it.getDouble("verticalOffsetMeters"))
            require(it.getString("method").isNotBlank() && it.getString("deviceLabel").isNotBlank())
            Instant.parse(it.getString("observedAt"))
        }
        return record
    }
}