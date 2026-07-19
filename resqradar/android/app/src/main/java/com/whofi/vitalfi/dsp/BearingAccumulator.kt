package com.whofi.vitalfi.dsp

import kotlin.math.cos
import kotlin.math.sin

fun polarToXY(distanceM: Double, bearingDeg: Double): Pair<Double, Double> {
    val rad = Math.toRadians(bearingDeg)
    val x = distanceM * sin(rad)
    val y = distanceM * cos(rad)
    return x to y
}

class BearingAccumulator(
    private val numBins: Int = 36,
    private val decay: Double = 0.92,
) {
    private val scores = DoubleArray(numBins)
    private val distances = DoubleArray(numBins)

    fun reset() {
        scores.fill(0.0)
        distances.fill(0.0)
    }

    private fun binIndex(bearingDeg: Double): Int =
        ((bearingDeg / 360.0 * numBins).toInt()) % numBins

    fun update(bearingDeg: Double, distanceM: Double, strength: Double) {
        val idx = binIndex(bearingDeg)
        for (i in scores.indices) {
            scores[i] *= decay
            distances[i] *= decay
        }
        scores[idx] += strength
        distances[idx] = if (distances[idx] > 0) {
            distances[idx] * 0.75 + distanceM * 0.25
        } else {
            distanceM
        }
    }

    fun bestEstimate(minScore: Double = 0.04): Triple<Double, Double, Double>? {
        val peak = scores.maxOrNull() ?: return null
        if (peak < minScore) return null
        val idx = scores.indices.maxByOrNull { scores[it] } ?: return null
        val bearing = (idx + 0.5) * 360.0 / numBins
        val distance = if (distances[idx] > 0) distances[idx] else 2.5
        return Triple(bearing, distance, peak)
    }

    fun strongestBearing(): Pair<Double, Double>? {
        val idx = scores.indices.maxByOrNull { scores[it] } ?: return null
        if (scores[idx] < 0.005) return null
        val bearing = (idx + 0.5) * 360.0 / numBins
        val distance = if (distances[idx] > 0) distances[idx] else 2.5
        return bearing to distance
    }

    fun heatmapPoints(): List<HeatPoint> {
        val points = mutableListOf<HeatPoint>()
        val peak = scores.maxOrNull() ?: 0.0
        for (i in scores.indices) {
            if (scores[i] < maxOf(0.005, peak * 0.1)) continue
            val bearing = (i + 0.5) * 360.0 / numBins
            val dist = if (distances[i] > 0) distances[i] else 2.0
            val (x, y) = polarToXY(dist, bearing)
            points.add(HeatPoint(x, y, scores[i]))
        }
        return points
    }

    fun depthUnderRubble(distanceM: Double, confidence: Double): Double {
        val depth = minOf(1.8, maxOf(0.3, distanceM * 0.25))
        return -depth * (0.5 + 0.5 * minOf(1.0, confidence))
    }
}

data class HeatPoint(val x: Double, val y: Double, val intensity: Double)

data class PositionEstimate(
    val x: Double,
    val y: Double,
    val bearing: Double,
    val distance: Double,
    val depth: Double,
)
