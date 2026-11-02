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
    // Product placement submits immediately; diagnostic quality gates remain available.
    check(cloudQualityAllowsHost("INSUFFICIENT", true, false, false, onPlacement = true))
    check(cloudQualityAllowsHost("NOT_MEASURED", false, false, false, onPlacement = true))
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
    quality.reset()
    check(!cloudQualityAllowsHost("GOOD", true, quality.observedGoodMillis >= 1000L, false))
    check(!cloudQualityAllowsHost("GOOD", false, true, false))
    println("AR center placement, hosting and 1/365-day lifetime checks passed")
}
