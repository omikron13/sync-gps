package com.phil.gpslog

import android.content.Context
import java.io.File

/**
 * Persists GPS points as newline-delimited CSV in the app's private storage:
 *   epochMillis,lat,lon,accuracy,altitude
 * Simple, append-only, survives process death and reboots.
 */
object PointStore {

    private const val FILE_NAME = "points.csv"

    data class Point(
        val timeMs: Long,
        val lat: Double,
        val lon: Double,
        val accuracy: Float,
        val altitude: Double
    )

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun append(context: Context, p: Point) {
        file(context).appendText("${p.timeMs},${p.lat},${p.lon},${p.accuracy},${p.altitude}\n")
    }

    @Synchronized
    fun readAll(context: Context): List<Point> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val parts = line.split(",")
            if (parts.size < 3) return@mapNotNull null
            try {
                Point(
                    timeMs = parts[0].toLong(),
                    lat = parts[1].toDouble(),
                    lon = parts[2].toDouble(),
                    accuracy = parts.getOrNull(3)?.toFloatOrNull() ?: 0f,
                    altitude = parts.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    @Synchronized
    fun count(context: Context): Int {
        val f = file(context)
        if (!f.exists()) return 0
        return f.readLines().count { it.isNotBlank() }
    }

    @Synchronized
    fun clear(context: Context) {
        val f = file(context)
        if (f.exists()) f.writeText("")
    }
}
