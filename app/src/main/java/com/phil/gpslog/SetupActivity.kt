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

    private fun saveAndStart() {
        val from = findViewById<EditText>(R.id.inputFromEmail).text.toString().trim()
        val pass = findViewById<EditText>(R.id.inputAppPassword).text.toString().trim()
        val to = findViewById<EditText>(R.id.inputToEmail).text.toString().trim()
        if (from.isEmpty() || pass.isEmpty() || to.isEmpty()) {
            toast("Remplis les 3 champs email")
            return
        }
        Prefs.save(this, from, pass, to)
        Prefs.setLastSend(this, System.currentTimeMillis())
        Scheduler.start(this)
        toast("Enregistré. Suivi démarré.")
        refreshStatus()
    }

    private fun sendTestEmail() {
        val from = findViewById<EditText>(R.id.inputFromEmail).text.toString().trim()
        val pass = findViewById<EditText>(R.id.inputAppPassword).text.toString().trim()
        val to = findViewById<EditText>(R.id.inputToEmail).text.toString().trim()
        if (from.isEmpty() || pass.isEmpty() || to.isEmpty()) {
            toast("Remplis les 3 champs email")
            return
        }
        toast("Envoi du mail de test…")
        Thread {
            val points = PointStore.readAll(this)
            val gpx = GpxBuilder.build(points)
            val ok = try {
                EmailSender.sendGpx(from, pass, to, gpx, points)
            } catch (e: Exception) {
                runOnUiThread { toast("Échec: ${e.message}") }
                false
            }
            if (ok) runOnUiThread { toast("Mail de test envoyé à $to") }
        }.start()
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
