package com.phil.gpslog

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * The one-time setup screen. Walks through the permissions Android requires,
 * stores the SMTP credentials, starts tracking, and finally hides its own icon.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        status = findViewById(R.id.statusText)

        findViewById<EditText>(R.id.inputFromEmail).setText(Prefs.fromEmail(this))
        findViewById<EditText>(R.id.inputToEmail).setText(Prefs.toEmail(this))
        findViewById<EditText>(R.id.inputSmtpHost).setText(Prefs.smtpHost(this))
        findViewById<EditText>(R.id.inputSmtpPort).setText(Prefs.smtpPort(this).toString())

        findViewById<Button>(R.id.btnGrantPermissions).setOnClickListener { requestForeground() }
        findViewById<Button>(R.id.btnBackgroundPermission).setOnClickListener { requestBackground() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { requestBatteryExemption() }
        findViewById<Button>(R.id.btnExactAlarm).setOnClickListener { requestExactAlarm() }
        findViewById<Button>(R.id.btnSaveStart).setOnClickListener { saveAndStart() }
        findViewById<Button>(R.id.btnTestEmail).setOnClickListener { sendTestEmail() }
        findViewById<Button>(R.id.btnHideIcon).setOnClickListener { hideIcon() }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun requestForeground() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            1
        )
    }

    private fun requestBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                toast("Accorde d'abord l'étape A (localisation)")
                return
            }
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                2
            )
        } else {
            toast("Pas nécessaire sur cette version d'Android")
        }
    }

    private fun requestBatteryExemption() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                toast("Réglage indisponible sur cet appareil")
            }
        } else {
            toast("Pas nécessaire sur cette version d'Android")
        }
    }

    /** Password typed in the field, or the one already saved if the field was left empty. */
    private fun effectivePassword(): String {
        val typed = findViewById<EditText>(R.id.inputAppPassword).text.toString()
        return if (typed.isNotEmpty()) typed else Prefs.appPassword(this)
    }

    private data class MailConfig(
        val from: String, val pass: String, val to: String, val host: String, val port: Int
    )

    /** Reads and validates the form; returns null (and explains on screen) if incomplete. */
    private fun readMailConfig(): MailConfig? {
        val from = findViewById<EditText>(R.id.inputFromEmail).text.toString().trim()
        val pass = effectivePassword()
        val to = findViewById<EditText>(R.id.inputToEmail).text.toString().trim()
        val host = findViewById<EditText>(R.id.inputSmtpHost).text.toString().trim()
        val port = findViewById<EditText>(R.id.inputSmtpPort).text.toString().trim().toIntOrNull()
        val missing = mutableListOf<String>()
        if (from.isEmpty()) missing.add("adresse d'envoi")
        if (pass.isEmpty()) missing.add("mot de passe")
        if (to.isEmpty()) missing.add("destinataire")
        if (host.isEmpty()) missing.add("serveur SMTP")
        if (port == null || port !in 1..65535) missing.add("port (nombre, ex. 465)")
        if (missing.isNotEmpty()) {
            val msg = "Il manque : " + missing.joinToString(", ")
            toast(msg)
            status.text = msg
            return null
        }
        return MailConfig(from, pass, to, host, port!!)
    }

    private fun saveAndStart() {
        val cfg = readMailConfig() ?: return
        Prefs.save(this, cfg.from, cfg.pass, cfg.to, cfg.host, cfg.port)
        Prefs.setLastSend(this, System.currentTimeMillis())
        Scheduler.start(this)
        toast("Enregistré. Suivi démarré.")
        refreshStatus()
    }

    private fun sendTestEmail() {
        val cfg = readMailConfig() ?: return
        toast("Envoi du mail de test…")
        status.text = "Envoi du mail de test en cours via ${cfg.host}:${cfg.port}… (jusqu'à 30 s)"
        Thread {
            val points = PointStore.readAll(this)
            val gpx = GpxBuilder.build(points)
            val result: String = try {
                EmailSender.sendGpx(cfg.from, cfg.pass, cfg.to, cfg.host, cfg.port, gpx, points)
                "MAIL DE TEST ENVOYÉ à ${cfg.to} via ${cfg.host}:${cfg.port} — vérifie ta boîte (et les spams)."
            } catch (t: Throwable) {
                "ÉCHEC DE L'ENVOI :\n" + describe(t)
            }
            runOnUiThread {
                status.text = result
                toast(if (result.startsWith("MAIL")) "Mail envoyé" else "Échec — voir le détail à l'écran")
            }
        }.start()
    }

    /** Full error chain, so the exact cause can be read on screen (and screenshotted). */
    private fun describe(t: Throwable): String {
        val sb = StringBuilder()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 5) {
            sb.append(cur.javaClass.simpleName).append(": ").append(cur.message ?: "(sans message)").append("\n")
            cur = cur.cause
            depth++
        }
        val msg = sb.toString()
        val hint = when {
            msg.contains("535") || msg.contains("Username and Password not accepted", true) ->
                "→ Identifiants refusés par le serveur : vérifie l'adresse d'envoi (c'est l'identifiant SMTP, adresse complète) " +
                "et le mot de passe de cette boîte. Pour Gmail, il faudrait un mot de passe d'application."
            msg.contains("UnknownHost", true) || msg.contains("Unable to resolve host", true) ->
                "→ Pas de connexion Internet au moment du test."
            msg.contains("Could not connect", true) || msg.contains("timed out", true) || msg.contains("ECONNREFUSED", true) ->
                "→ Connexion SMTP impossible (ports 587 et 465) : le réseau bloque peut-être l'envoi. Essaie en Wi-Fi ou en 4G."
            else -> ""
        }
        return msg + hint
    }

    private fun hideIcon() {
        if (!Prefs.isEnabled(this)) {
            toast("Fais d'abord l'étape E (enregistrer et démarrer)")
            return
        }
        val alias = ComponentName(this, "com.phil.gpslog.LauncherAlias")
        packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        toast("Icône masquée. L'app tourne en arrière-plan.")
        finish()
    }

    private fun refreshStatus() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        else true
        val running = Prefs.isEnabled(this)
        val stored = PointStore.count(this)
        status.text = buildString {
            append("Localisation: ").append(if (fine) "OK" else "manquante").append("\n")
            append("Arrière-plan: ").append(if (bg) "OK" else "manquante").append("\n")
            append("Mot de passe: ").append(if (Prefs.appPassword(this@SetupActivity).isNotEmpty()) "enregistré" else "ABSENT").append("\n")
            append("Suivi actif: ").append(if (running) "OUI" else "non").append("\n")
            append("Points en attente d'envoi: ").append(stored)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            // After foreground granted, prompt for background next.
            refreshStatus()
        }
        refreshStatus()
    }
}
