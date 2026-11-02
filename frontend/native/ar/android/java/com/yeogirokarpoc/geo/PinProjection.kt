package com.yeogirokarpoc.geo

/** Projection result for a pin tip. Off-screen guidance never changes its world pose. */
data class PinProjection(
    val x: Float? = null,
    val y: Float? = null,
    val guidance: String,
    val widthPx: Float = 0f,
    val heightPx: Float = 0f,
    val cameraDepthMeters: Float = 0f,
) {
    val visible: Boolean get() = x != null && y != null
}

// A camera-facing pin 48 cm tall (3x the initial size); its bottom remains at the surface anchor.
private const val PIN_HEIGHT_METERS = 0.48f
private const val PIN_WIDTH_METERS = PIN_HEIGHT_METERS * 34f / 40f

internal fun projectPin(clip: FloatArray, width: Int, height: Int, focalX: Float, focalY: Float): PinProjection {
    if (clip.size != 4 || clip.any { !it.isFinite() } || width <= 0 || height <= 0 ||
        !focalX.isFinite() || !focalY.isFinite() || focalX <= 0f || focalY <= 0f) {
        return PinProjection(guidance = "핀 투영 대기 중")
    }
    if (clip[3] <= 0f) return PinProjection(guidance = "핀은 카메라 뒤쪽에 있습니다. 주변을 천천히 둘러보세요.")
    val x = clip[0] / clip[3]
    val y = clip[1] / clip[3]
    val z = clip[2] / clip[3]
    if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return PinProjection(guidance = "핀 투영 대기 중")
    if (z < -1f) return PinProjection(guidance = "핀이 카메라에 너무 가깝습니다. 조금 떨어져 비춰 주세요.")
    if (z > 1f) return PinProjection(guidance = "핀이 표시 거리 밖에 있습니다. 저장 위치와 거리 값을 확인하세요.")
    // clip.w is forward camera depth, so doubling depth halves the projected size.
    val widthPx = PIN_WIDTH_METERS * focalX * width / (2f * clip[3])
    val heightPx = PIN_HEIGHT_METERS * focalY * height / (2f * clip[3])
    if (!widthPx.isFinite() || !heightPx.isFinite()) return PinProjection(guidance = "핀 투영 대기 중")
    if (x in -1f..1f && y in -1f..1f) {
        return PinProjection((x + 1f) * width / 2f, (1f - y) * height / 2f, "핀: 화면 안", widthPx, heightPx, clip[3])
    }
    val directions = mutableListOf<String>()
    if (x < -1f) directions.add("왼쪽") else if (x > 1f) directions.add("오른쪽")
    if (y < -1f) directions.add("아래쪽") else if (y > 1f) directions.add("위쪽")
    return PinProjection(guidance = "핀은 화면 ${directions.joinToString("·")} 밖에 있습니다. 그 방향을 비춰 주세요.")
}
