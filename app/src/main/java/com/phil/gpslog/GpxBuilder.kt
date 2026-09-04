package com.phil.gpslog

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns stored points into a GPX 1.1 file. GPX is directly importable into
 * Google My Maps (mymaps.google.com -> Import) and most mapping tools, so the
 * emailed track shows up as a line you can view on Google Maps.
 */
object GpxBuilder {

    private fun iso(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    /** Total distance of the track in kilometres (haversine). */
    fun distanceKm(points: List<PointStore.Point>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(
                points[i - 1].lat, points[i - 1].lon,
                points[i].lat, points[i].lon
            )
        }
        return total / 1000.0
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun build(points: List<PointStore.Point>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Sync\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        val sorted = points.sortedBy { it.timeMs }
        val name = if (sorted.isNotEmpty()) "Trajet ${iso(sorted.first().timeMs)}" else "Trajet"
        sb.append("  <metadata><name>").append(escape(name)).append("</name></metadata>\n")
        sb.append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n")
        for (p in sorted) {
            sb.append("      <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">")
            if (p.altitude != 0.0) sb.append("<ele>").append(p.altitude).append("</ele>")
            sb.append("<time>").append(iso(p.timeMs)).append("</time>")
            sb.append("</trkpt>\n")
        }
        sb.append("    </trkseg>\n  </trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
