package com.yeogirokarpoc.geo

import android.os.SystemClock
import android.util.Log
import com.google.ar.core.*
import java.time.Instant

/** Access on the render thread, or after GLSurfaceView.onPause() has joined it. */
class CloudAnchorExperiment {
    private var host: HostCloudAnchorFuture? = null
    private var resolve: ResolveCloudAnchorFuture? = null
    private var resolved: Anchor? = null
    private val gate = CloudQualityGate()
    private var lastQualityAt = 0L
    private var startedAt = 0L
    var attempted = false; private set
    var busy = false; private set
    var failed = false; private set
    var authorizationFailed = false; private set
    var quality = "아직 측정하지 않음"; private set
    var qualityFresh = false; private set
    var qualityErrorType: String? = null; private set
    var qualitySampleCount = 0L; private set
    val goodHoldMillis: Long get() = gate.observedGoodMillis
    var canHost = false; private set
    var message = "주변을 여러 각도에서 비춘 뒤 터치로 배치하세요."; private set
    var hostedId: String? = null; private set
    var hostedAt: Instant? = null; private set
    var durationMs = 0L; private set
    var hostQuality = ""; private set
    var hostQualityPolicy = "GOOD_STABLE"; private set

    fun canRequestHost(diagnostic: Boolean, onPlacement: Boolean = false) = !busy && !failed && hostedId == null &&
        cloudQualityAllowsHost(quality, qualityFresh, canHost, diagnostic, onPlacement)

    fun placementChanged() {
        gate.reset(); canHost = false; lastQualityAt = 0L
        qualityFresh = false; qualityErrorType = null
    }

    fun reset() {
        if (busy) Log.i("YeogirokCloud", "OPERATION_CANCELLED durationMs=${SystemClock.elapsedRealtime() - startedAt}")
        host?.cancel(); host = null
        resolve?.let { future ->
            // A completed result that was never transferred still owns an anchor.
            if (future.state == FutureState.DONE && future.resultCloudAnchorState == Anchor.CloudAnchorState.SUCCESS)
                future.resultAnchor?.detach()
            else future.cancel()
        }
        resolve = null
        resolved?.detach(); resolved = null
        busy = false; failed = false; authorizationFailed = false; attempted = false
        hostedId = null; hostedAt = null; durationMs = 0L
        hostQuality = ""; hostQualityPolicy = "GOOD_STABLE"
        quality = "아직 측정하지 않음"
        qualitySampleCount = 0L
        message = "주변을 여러 각도에서 비춘 뒤 터치로 배치하세요."
        placementChanged()
    }

    fun startResolve(session: Session, id: String) {
        if (attempted) return
        attempted = true; startedAt = SystemClock.elapsedRealtime()
        try {
            resolve = session.resolveCloudAnchorAsync(id, null)
            Log.i("YeogirokCloud", "RESOLVE_STARTED") // Never log the shared anchor ID.
            busy = true; message = "공간 복원 중. 작성자가 보았던 주변을 천천히 비춰 주세요."
        } catch (error: Exception) { fail("복원 시작 실패: ${error.javaClass.simpleName}") }
    }

    fun startHost(session: Session, anchor: Anchor, diagnostic: Boolean, onPlacement: Boolean = false) {
        if (!canRequestHost(diagnostic, onPlacement) || anchor.trackingState != TrackingState.TRACKING) return
        startedAt = SystemClock.elapsedRealtime()
        hostedAt = Instant.now()
        hostQuality = if (qualityFresh) quality else "NOT_MEASURED"
        hostQualityPolicy = if (onPlacement) "ON_PLACEMENT" else if (diagnostic) "DIAGNOSTIC_CURRENT_QUALITY" else "GOOD_STABLE"
        try {
            host = session.hostCloudAnchorAsync(anchor, ArExperimentPolicy.CLOUD_TTL_DAYS, null)
            Log.i("YeogirokCloud", "HOST_STARTED auth=KEYLESS ttlDays=${ArExperimentPolicy.CLOUD_TTL_DAYS} quality=$hostQuality policy=$hostQualityPolicy")
            busy = true; canHost = false
            message = "Cloud 저장 중. 완료될 때까지 주변을 계속 비춰 주세요."
        } catch (error: Exception) { fail("저장 시작 실패: ${error.javaClass.simpleName}") }
    }

    fun update(session: Session, frame: Frame, anchor: Anchor?, dragging: Boolean, hosting: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val eligible = hosting && !busy && !failed && hostedId == null && !dragging &&
            frame.camera.trackingState == TrackingState.TRACKING && anchor?.trackingState == TrackingState.TRACKING
        if (!eligible) {
            gate.reset(); canHost = false
            qualityFresh = false; qualityErrorType = null
        }
        else if (now - lastQualityAt >= 250L) {
            lastQualityAt = now
            qualitySampleCount++
            val value = try {
                session.estimateFeatureMapQualityForHosting(frame.camera.pose).also { qualityErrorType = null }
            } catch (error: Exception) {
                val type = error.javaClass.simpleName
                if (qualityErrorType != type) Log.w("YeogirokCloud", "FEATURE_QUALITY_ERROR=$type")
                qualityErrorType = type
                null
            }
            qualityFresh = value != null
            val nextQuality = value?.name ?: "측정 오류"
            if (quality != nextQuality) Log.i("YeogirokCloud", "FEATURE_QUALITY=$nextQuality")
            quality = nextQuality
            canHost = gate.update(now, value == Session.FeatureMapQuality.GOOD)
        }
        if (busy && now - startedAt >= ArExperimentPolicy.CLOUD_TIMEOUT_MS) {
            // Do not substitute a GPS/local pose after timeout.
            host?.cancel(); host = null
            resolve?.let {
                if (it.state == FutureState.DONE && it.resultCloudAnchorState == Anchor.CloudAnchorState.SUCCESS)
                    it.resultAnchor?.detach()
                else it.cancel()
            }
            resolve = null
            fail("60초 시간 초과. 다시 배치 또는 복원 재시도를 눌러 주세요.")
            return
        }
        host?.takeIf { it.state == FutureState.DONE }?.let {
            host = null; busy = false; durationMs = now - startedAt
            if (it.resultCloudAnchorState == Anchor.CloudAnchorState.SUCCESS && !it.resultCloudAnchorId.isNullOrBlank()) {
                hostedId = it.resultCloudAnchorId
                Log.i("YeogirokCloud", "HOST_SUCCESS durationMs=$durationMs")
                message = "Cloud 저장 완료. 현재 추적이 안정되면 기록을 반환합니다."
            } else fail("Cloud 저장 실패: ${it.resultCloudAnchorState}. 다시 배치해 주세요.",
                it.resultCloudAnchorState == Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED)
        }
        resolve?.takeIf { it.state == FutureState.DONE }?.let {
            resolve = null; busy = false; durationMs = now - startedAt
            if (it.resultCloudAnchorState == Anchor.CloudAnchorState.SUCCESS && it.resultAnchor != null) {
                resolved = it.resultAnchor
                Log.i("YeogirokCloud", "RESOLVE_SUCCESS durationMs=$durationMs")
                message = "복원 응답 완료. 실제 원래 지점을 터치해 차이를 비교하세요."
            } else fail("Cloud 복원 실패: ${it.resultCloudAnchorState}. 복원 재시도를 눌러 주세요.",
                it.resultCloudAnchorState == Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED)
        }
    }

    fun takeResolvedAnchor(): Anchor? = resolved.also { resolved = null }
    private fun fail(reason: String, authorization: Boolean = false) {
        authorizationFailed = authorization
        failed = true; busy = false; canHost = false; gate.reset(); message = reason
        durationMs = SystemClock.elapsedRealtime() - startedAt
        // reason is built only from fixed text, enum names, or exception class names.
        Log.w("YeogirokCloud", "OPERATION_FAILED durationMs=$durationMs $reason")
    }
}
