package ams2.dash.udp

import ams2.dash.model.TelemetryState
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Thread qui écoute le broadcast UDP d'AMS2 et met à jour l'état. */
class Receiver(
    private val state: TelemetryState,
    private val onUpdate: () -> Unit,
) : Thread("ams2-udp") {

    @Volatile private var running = true
    @Volatile private var socket: DatagramSocket? = null

    override fun run() {
        val buf = ByteArray(2048)
        val packet = DatagramPacket(buf, buf.size)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        while (running) {
            try {
                val s = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(Packets.PORT))
                }
                socket = s
                while (running) {
                    packet.length = buf.size
                    s.receive(packet)
                    synchronized(state) { Packets.parse(bb, packet.length, state) }
                    onUpdate()
                }
            } catch (e: SocketException) {
                if (running) {
                    Log.w(TAG, "Socket UDP en erreur, nouvel essai", e)
                    socket?.close()
                    sleep(1000)
                }
            }
        }
    }

    fun shutdown() {
        running = false
        socket?.close()
    }

    private companion object {
        const val TAG = "AMS2Dash"
    }
}
