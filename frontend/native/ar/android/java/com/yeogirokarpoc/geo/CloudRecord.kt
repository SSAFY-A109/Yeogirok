package com.yeogirokarpoc.geo

import com.google.ar.core.Pose
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Cloud-only record: absence of Earth localization must never produce invented GPS. */
object CloudRecord {
    const val KIND = "yeogirok-cloud-anchor"
    const val LAST_RECORD = "last_cloud_record"
    fun create(id: String, hostedAt: Instant, pose: Pose, hitType: String,
               depth: Boolean, depthHitAllowed: Boolean, sessionId: String, durationMs: Long,
               hostQuality: String, qualityPolicy: String): JSONObject = JSONObject()
        .put("schemaVersion", 1).put("kind", KIND).put("recordId", UUID.randomUUID().toString())
        .put("anchorType", "CLOUD").put("anchorId", id).put("ttlDays", ArExperimentPolicy.CLOUD_TTL_DAYS)
        .put("authorizationMode", "KEYLESS")
        .put("hostSessionId", sessionId)
        .put("hostDeviceLabel", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        .put("hostedAt", hostedAt.toString())
        // Conservative local estimate from request start; NOT a queried server expiry.
        .put("expiresAtEstimate", cloudAnchorExpiry(hostedAt, ArExperimentPolicy.CLOUD_TTL_DAYS).toString())
        .put("featureMapQuality", hostQuality).put("qualityPolicy", qualityPolicy)
        .put("hitType", hitType).put("depthEnabled", depth)
        .put("depthHitAllowed", depthHitAllowed)
        .put("geospatial", JSONObject.NULL).put("crossDeviceVerified", false)
        .put("localPose", localPose(pose))
        .put("operation", operation("HOST", sessionId, durationMs))

    fun localPose(pose: Pose) = JSONObject()
        .put("frame", "native-arcore-session")
        .put("positionMeters", GeoRecord.jsonArray(pose.translation))
        .put("rotationQuaternionXYZW", GeoRecord.jsonArray(pose.rotationQuaternion))

    fun operation(type: String, sessionId: String, durationMs: Long) = JSONObject()
        .put("type", type).put("sessionId", sessionId).put("durationMs", durationMs)
        .put("recordedAt", Instant.now().toString())
        .put("deviceLabel", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")

    fun parse(raw: String, forResolve: Boolean = false): JSONObject {
        require(raw.length <= 65536) { "기록이 너무 큽니다." }
        val r = JSONObject(raw)
        if (r.optString("kind") == "yeogirok-public-cloud-anchor") {
            require(r.getInt("schemaVersion") == 1 && r.getString("anchorType") == "CLOUD")
            val id = r.getString("anchorId")
            require(id.isNotBlank() && id.length <= 255 && id.none { it.isWhitespace() || it.isISOControl() })
            val expiry = Instant.parse(r.getString("expiresAtEstimate"))
            if (forResolve) require(Instant.now().isBefore(expiry))
            val location = r.getJSONObject("discoveryLocation")
            require(NavigationPolicy.coordinates(location.getDouble("latitude"), location.getDouble("longitude")))
            return JSONObject().put("schemaVersion", 1).put("kind", "yeogirok-public-cloud-anchor")
                .put("anchorType", "CLOUD").put("anchorId", id).put("expiresAtEstimate", expiry.toString())
                .put("ttlDays", 365).put("discoveryLocation", JSONObject().put("latitude", location.getDouble("latitude"))
                    .put("longitude", location.getDouble("longitude")))
        }
        require(r.getInt("schemaVersion") == 1 && r.getString("kind") == KIND &&
            r.getString("anchorType") == "CLOUD") { "Cloud Anchor 검증 JSON이 필요합니다." }
        val id = r.getString("anchorId")
        require(id.isNotBlank() && id.length <= 255 && id.none { it.isWhitespace() || it.isISOControl() })
        require(r.get("ttlDays") is Number)
        val ttlDays = r.getInt("ttlDays")
        require(r.getDouble("ttlDays") == ttlDays.toDouble())
        if (r.has("authorizationMode")) require(r.getString("authorizationMode") == "KEYLESS")
        val policy = if (r.has("qualityPolicy")) r.getString("qualityPolicy") else "GOOD_STABLE"
        require(cloudRecordQualityAllowed(r.getString("featureMapQuality"), policy))
        r.put("qualityPolicy", policy)
        val hosted = Instant.parse(r.getString("hostedAt"))
        val expiry = Instant.parse(r.getString("expiresAtEstimate"))
        require(expiry == cloudAnchorExpiry(hosted, ttlDays))
        if (forResolve) require(Instant.now().isBefore(expiry)) { "앵커의 예상 수명이 지났습니다. 현장에서 새로 저장해 주세요." }
        require(r.getString("recordId").isNotBlank())
        require(r.getString("hostSessionId").isNotBlank() && r.getString("hostDeviceLabel").isNotBlank())
        require(r.getString("hitType") in listOf("PLANE", "DEPTH"))
        r.getBoolean("depthEnabled")
        r.getBoolean("depthHitAllowed")
        require(r.has("geospatial") && r.isNull("geospatial"))
        if (r.has("discoveryLocation")) {
            val d = r.getJSONObject("discoveryLocation")
            // A manually confirmed search location is not an Earth-localized tip Pose.
            require(d.get("latitude") is Number && d.get("longitude") is Number)
            require(NavigationPolicy.coordinates(d.getDouble("latitude"), d.getDouble("longitude")))
            require(d.getString("source") == "MAP_SELECTED")
            require(d.has("uncertaintyMeters") && d.isNull("uncertaintyMeters"))
            Instant.parse(d.getString("capturedAt"))
        }
        require(!r.has("visitLocation") || r.isNull("visitLocation") || r.optJSONObject("visitLocation") != null)
        r.optJSONObject("visitLocation")?.let { visit ->
            require(visit.getString("source") == "DEVICE_GPS")
            require(visit.get("latitude") is Number && visit.get("longitude") is Number)
            require(NavigationPolicy.coordinates(visit.getDouble("latitude"), visit.getDouble("longitude")))
            require(visit.getDouble("accuracyMeters").let { it.isFinite() && it >= 0 })
            Instant.parse(visit.getString("capturedAt"))
        }
        val op = r.getJSONObject("operation")
        require(op.getString("type") in listOf("HOST", "RESOLVE"))
        require(op.getString("sessionId").isNotBlank() && op.getString("deviceLabel").isNotBlank())
        require(op.getDouble("durationMs").let { it.isFinite() && it >= 0 })
        Instant.parse(op.getString("recordedAt"))
        val p = r.getJSONObject("localPose")
        require(p.getString("frame") == "native-arcore-session")
        for ((field, size) in listOf("positionMeters" to 3, "rotationQuaternionXYZW" to 4)) {
            val values = p.getJSONArray(field)
            require(values.length() == size)
            for (i in 0 until size) require(values.getDouble(i).isFinite())
        }
        val rotation = p.getJSONArray("rotationQuaternionXYZW")
        val normSquared = (0..3).sumOf { rotation.getDouble(it) * rotation.getDouble(it) }
        require(kotlin.math.abs(normSquared - 1.0) < 0.02)
        r.optJSONObject("restorationEstimate")?.let {
            require(op.getString("type") == "RESOLVE")
            require(it.getDouble("horizontalOffsetMeters").let { n -> n.isFinite() && n >= 0 })
            require(it.getDouble("verticalOffsetMeters").isFinite())
            require(it.getString("referenceHitType") in listOf("PLANE", "DEPTH"))
            require(!it.getBoolean("groundTruth"))
        }
        r.put("crossDeviceVerified", false)
        return r
    }
}
