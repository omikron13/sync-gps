package com.phil.gpslog

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

/**
 * Sends the GPX track by email over Gmail SMTP using an app-specific password.
 * Must run on a background thread (called from the alarm receiver, off the main thread).
 */
object EmailSender {

    fun sendGpx(
        fromEmail: String,
        appPassword: String,
        toEmail: String,
        gpx: String,
        points: List<PointStore.Point>
    ): Boolean {
        // Try STARTTLS on 587 first, then implicit SSL on 465 (some mobile networks block one of them).
        return try {
            send(fromEmail, appPassword, toEmail, gpx, points, useSsl465 = false)
        } catch (first: Throwable) {
            try {
                send(fromEmail, appPassword, toEmail, gpx, points, useSsl465 = true)
            } catch (second: Throwable) {
                // Report the first failure (587) as the main cause, keep the second as context.
                throw RuntimeException("587: ${first.message} | 465: ${second.message}", first)
            }
        }
    }

    private fun send(
        fromEmail: String,
        appPassword: String,
        toEmail: String,
        gpx: String,
        points: List<PointStore.Point>,
        useSsl465: Boolean
    ): Boolean {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "15000")
            put("mail.smtp.writetimeout", "15000")
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
            if (useSsl465) {
                put("mail.smtp.port", "465")
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.socketFactory.port", "465")
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            } else {
                put("mail.smtp.port", "587")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(fromEmail, appPassword)
        })

        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date())

        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(fromEmail))
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
        msg.subject = "Trajet GPS — $stamp (${points.size} points)"

        val bodyPart = MimeBodyPart().apply {
            setText(buildBody(points, stamp), "UTF-8")
        }

        val attachment = MimeBodyPart().apply {
            val ds = ByteArrayDataSource(gpx.toByteArray(Charsets.UTF_8), "application/gpx+xml")
            dataHandler = DataHandler(ds)
            fileName = "trajet_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.gpx"
        }

        val multipart = MimeMultipart().apply {
            addBodyPart(bodyPart)
            addBodyPart(attachment)
        }
        msg.setContent(multipart)

        Transport.send(msg)
        return true
    }

    /** Build a Google Maps directions URL from up to 10 sampled points. */
    private fun mapsLink(points: List<PointStore.Point>): String? {
        if (points.size < 2) return null
        val maxWaypoints = 10
        val step = maxOf(1, points.size / maxWaypoints)
        val sampled = points.filterIndexed { i, _ -> i % step == 0 }.take(maxWaypoints).toMutableList()
        if (sampled.last() != points.last()) sampled[sampled.size - 1] = points.last()
        val path = sampled.joinToString("/") { "${fmt(it.lat)},${fmt(it.lon)}" }
        return "https://www.google.com/maps/dir/$path"
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun buildBody(points: List<PointStore.Point>, stamp: String): String {
        val distance = GpxBuilder.distanceKm(points)
        val sb = StringBuilder()
        sb.append("Trajet GPS enregistré le $stamp.\n\n")
        sb.append("Points enregistrés : ${points.size}\n")
        sb.append("Distance approximative : ").append(String.format(Locale.FRANCE, "%.2f", distance)).append(" km\n\n")
        mapsLink(points)?.let {
            sb.append("Aperçu rapide de l'itinéraire (échantillon de points) sur Google Maps :\n")
            sb.append(it).append("\n\n")
        }
        sb.append("Trajet complet (fichier .gpx joint) — pour l'afficher sur Google Maps :\n")
        sb.append("1. Ouvre https://www.google.com/maps/d/ (Google My Maps)\n")
        sb.append("2. « Créer une carte » puis « Importer » et choisis le fichier .gpx joint\n")
        sb.append("3. Le trajet s'affiche comme une ligne complète sur la carte.\n")
        return sb.toString()
    }
}
