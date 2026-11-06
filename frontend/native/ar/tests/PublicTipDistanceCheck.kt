package com.yeogirokarpoc.geo

// Compile with PublicTipPolicy.kt, NavigationPolicy.kt and ArExperimentPolicy.kt; execute this main.
fun main() {
    val camera = floatArrayOf(10f, 20f, 30f)
    check(PublicTipPolicy.arDistance(camera, camera) == 0.0)
    check(PublicTipPolicy.arDistance(camera, floatArrayOf(13f, 24f, 30f)) == 5.0)
    check(PublicTipPolicy.inCamera(PublicTipPolicy.arDistance(camera, floatArrayOf(10f, 25f, 30f))))
    check(!PublicTipPolicy.inCamera(PublicTipPolicy.arDistance(camera, floatArrayOf(10f, 25.01f, 30f))))
    check(!PublicTipPolicy.inCamera(PublicTipPolicy.arDistance(camera, floatArrayOf(13f, 24f, 30.1f))))
    check(PublicTipPolicy.arDistance(floatArrayOf(0f, 0f), camera) == null)
    check(PublicTipPolicy.arDistance(camera, floatArrayOf(Float.NaN, 0f, 0f)) == null)
    check(PublicTipPolicy.arDistance(floatArrayOf(Float.POSITIVE_INFINITY, 0f, 0f), camera) == null)
    check(PublicTipPolicy.inCamera(0.0))
    check(PublicTipPolicy.inCamera(5.0))
    check(!PublicTipPolicy.inCamera(5.001))
    check(!PublicTipPolicy.inCamera(null))
    check(!PublicTipPolicy.inCamera(Double.NaN))
    check(!PublicTipPolicy.inCamera(-1.0))
    check(PublicTipPolicy.inMap(5.001))
    check(PublicTipPolicy.inMap(50.0))
    check(!PublicTipPolicy.inMap(50.001))
    check(!PublicTipPolicy.inMap(null))
    check(!PublicTipPolicy.inMap(Double.POSITIVE_INFINITY))
    check(!PublicTipPolicy.needsRefresh(9.999))
    check(PublicTipPolicy.needsRefresh(10.0))
    check(PublicTipPolicy.needsRefresh(20.0))
    check(!PublicTipPolicy.needsRefresh(Double.NaN))
    val originLat = 37.5665
    val originLon = 126.978
    val nineMeters = originLat + Math.toDegrees(9.0 / 6371008.8)
    val elevenMeters = originLat + Math.toDegrees(11.0 / 6371008.8)
    check(!PublicTipPolicy.needsRefresh(NavigationPolicy.distance(originLat, originLon, nineMeters, originLon)))
    check(PublicTipPolicy.needsRefresh(NavigationPolicy.distance(originLat, originLon, elevenMeters, originLon)))
    // A successful refresh moves the origin: small subsequent movement must not query again.
    check(!PublicTipPolicy.needsRefresh(NavigationPolicy.distance(elevenMeters, originLon, elevenMeters + Math.toDegrees(9.0 / 6371008.8), originLon)))
    println("Public tip distance checks passed: camera 5m, map 50m, GPS refresh 10m")
}
