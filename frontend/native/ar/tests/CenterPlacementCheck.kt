package com.yeogirokarpoc.geo

import java.time.Instant

// Run with ArExperimentPolicy.kt using kotlinc, then execute this main.
fun main() {
    val hostedAt = Instant.parse("2026-11-02T00:00:00Z")
    check(ArExperimentPolicy.CLOUD_TTL_DAYS == 365)
    check(cloudAnchorExpiry(hostedAt, 1) == Instant.parse("2026-11-03T00:00:00Z"))
    check(cloudAnchorExpiry(hostedAt, 365) == Instant.parse("2027-11-02T00:00:00Z"))
    check(runCatching { cloudAnchorExpiry(hostedAt, 0) }.isFailure)
    check(runCatching { cloudAnchorExpiry(hostedAt, 366) }.isFailure)
    check(runCatching { cloudAnchorExpiry(hostedAt, -1) }.isFailure)
    // A displayed hit is usable only briefly, including across clock/frame boundaries.
    check(ArExperimentPolicy.freshPlacementHit(0L))
    check(ArExperimentPolicy.freshPlacementHit(500L))
    check(!ArExperimentPolicy.freshPlacementHit(-1L))
    check(!ArExperimentPolicy.freshPlacementHit(501L))
    check(!ArExperimentPolicy.freshPlacementHit(Long.MAX_VALUE))
    check(ArExperimentPolicy.acceptsHit(0.2f))
    check(ArExperimentPolicy.acceptsHit(5f))
    check(!ArExperimentPolicy.acceptsHit(0.19f))
    check(!ArExperimentPolicy.acceptsHit(5.01f))
    check(!ArExperimentPolicy.acceptsHit(Float.NaN))
    // Once three angles are captured, sufficient quality saves automatically and low quality requires confirmation.
    check(cloudQualityAllowsHost("SUFFICIENT", true, false, HostQualityPolicy.SCAN_COMPLETE))
    check(cloudQualityAllowsHost("GOOD", true, false, HostQualityPolicy.SCAN_COMPLETE))
    check(!cloudQualityAllowsHost("INSUFFICIENT", true, false, HostQualityPolicy.SCAN_COMPLETE))
    check(!cloudQualityAllowsHost("SUFFICIENT", false, false, HostQualityPolicy.SCAN_COMPLETE))
    check(cloudQualityAllowsHost("INSUFFICIENT", true, false, HostQualityPolicy.SCAN_LOW_QUALITY))
    check(!cloudQualityAllowsHost("INSUFFICIENT", false, false, HostQualityPolicy.SCAN_LOW_QUALITY))
    check(!cloudQualityAllowsHost("NOT_MEASURED", true, false, HostQualityPolicy.SCAN_LOW_QUALITY))
    check(cloudQualityAllowsHost("INSUFFICIENT", true, false, HostQualityPolicy.DIAGNOSTIC_CURRENT_QUALITY))
    check(cloudRecordQualityAllowed("NOT_MEASURED", "ON_PLACEMENT"))
    check(cloudRecordQualityAllowed("INSUFFICIENT", "ON_PLACEMENT"))
    check(!cloudRecordQualityAllowed("NOT_MEASURED", "GOOD_STABLE"))
    check(!cloudRecordQualityAllowed("INSUFFICIENT", "GOOD_STABLE"))
    check(!cloudRecordQualityAllowed("UNKNOWN", "ON_PLACEMENT"))
    check(!cloudRecordQualityAllowed("GOOD", "UNKNOWN"))
    val quality = CloudQualityGate()
    check(!quality.update(0L, true))
    check(!quality.update(500L, true))
    check(quality.update(1000L, true))
    check(cloudQualityAllowsHost("GOOD", true, quality.observedGoodMillis >= 1000L, HostQualityPolicy.GOOD_STABLE))
    quality.reset()
    check(!cloudQualityAllowsHost("GOOD", true, quality.observedGoodMillis >= 1000L, HostQualityPolicy.GOOD_STABLE))
    check(!cloudQualityAllowsHost("GOOD", false, true, HostQualityPolicy.GOOD_STABLE))
    val scan = PlacementScan()
    check(scan.observe(0L, 179.0))
    check(scan.observe(175L, 179.0))
    check(!scan.observe(350L, 179.0))
    check(scan.completedViews == 1)
    check(!scan.observe(500L, -179.0)) // The same angle across the +/-180° seam.
    check(scan.observe(600L, -164.0))
    check(!scan.observe(700L, null)) // Lost tracking or aim before finishing the view.
    check(scan.observe(1_000L, -164.0))
    check(scan.observe(1_175L, -164.0))
    check(!scan.observe(1_350L, -164.0))
    check(scan.completedViews == 2)
    check(!scan.observe(1_400L, -155.0)) // Close to the second angle.
    check(scan.observe(1_500L, 162.0))
    check(scan.observe(1_900L, 162.0)) // A frame gap must restart the hold.
    check(scan.observe(2_075L, 162.0))
    check(!scan.observe(2_250L, 162.0))
    check(scan.complete)
    scan.reset()
    check(scan.completedViews == 0 && !scan.complete)
    println("AR center placement, hosting and 1/365-day lifetime checks passed")
}
