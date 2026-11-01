package ams2.dash

import ams2.dash.model.TelemetryState
import ams2.dash.ui.DashboardView
import ams2.dash.udp.Receiver
import android.app.Activity
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : Activity() {
    private val state = TelemetryState()
    private lateinit var view: DashboardView
    private var receiver: Receiver? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val handler = Handler(Looper.getMainLooper())

    // Redessine même sans paquet, pour basculer sur l'écran d'attente quand le flux s'arrête.
    private val ticker = object : Runnable {
        override fun run() {
            view.invalidate()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = DashboardView(this, state)
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        // Sans ce verrou, certains pilotes Wi-Fi filtrent les paquets broadcast.
        multicastLock = wifi.createMulticastLock("ams2dash").apply {
            setReferenceCounted(false)
            acquire()
        }
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "ams2dash").apply {
            setReferenceCounted(false)
            acquire()
        }
        view.ipText = localIps()
        receiver = Receiver(state) { view.postInvalidateOnAnimation() }.also { it.start() }
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        receiver?.shutdown()
        receiver = null
        multicastLock?.release()
        wifiLock?.release()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    private fun localIps(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .joinToString(" / ") { it.hostAddress ?: "" }
    }.getOrDefault("")
}
