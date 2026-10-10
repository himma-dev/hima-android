package app.hima

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class HimaVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "app.hima.STOP"
        const val UPSTREAM = "1.1.1.1"
        @Volatile var running = false
        val blockedCount = AtomicInteger(0)
    }

    private var tun: ParcelFileDescriptor? = null
    private val pool = Executors.newCachedThreadPool()
    private var blocklist: Set<String> = emptySet()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFilter()
            stopSelf()
            return START_NOT_STICKY
        }
        startFilter()
        return START_STICKY
    }

    override fun onDestroy() {
        stopFilter()
        super.onDestroy()
    }

    private fun loadBlocklist() {
        val set = HashSet<String>()
        try {
            assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim().lowercase()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val domain = line.split(" ", "\t").last()
                    if (domain.contains(".")) set.add(domain)
                }
            }
        } catch (e: Exception) {
        }
        blocklist = set
    }

    private fun startFilter() {
        if (running) return
        loadBlocklist()
        val fd = Builder()
            .setSession("Hima")
            .addAddress("10.111.222.1", 24)
            .addDnsServer("10.111.222.2")
            .addRoute("10.111.222.2", 32)
            .establish() ?: return
        tun = fd
        running = true
        blockedCount.set(0)
        Thread { runLoop(fd) }.start()
    }

    private fun stopFilter() {
        running = false
        try {
            tun?.close()
        } catch (e: Exception) {
        }
        tun = null
    }

    private fun runLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = input.read(buf)
                if (n > 0) handlePacket(buf.copyOf(n), output)
            }
        } catch (e: Exception) {
        }
    }

    private fun handlePacket(p: ByteArray, output: FileOutputStream) {
        if (p.size < 28) return
        if (((p[0].toInt() shr 4) and 0xF) != 4) return
        val ihl = (p[0].toInt() and 0xF) * 4
        if (p[9].toInt() != 17) return
        if (p.size < ihl + 8) return
        val dstPort = ((p[ihl + 2].toInt() and 0xFF) shl 8) or (p[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return
        val udpLen = ((p[ihl + 4].toInt() and 0xFF) shl 8) or (p[ihl + 5].toInt() and 0xFF)
        val end = minOf(p.size, ihl + udpLen)
        if (end <= ihl + 8) return
        val dns = p.copyOfRange(ihl + 8, end)
        val q = parseQuestion(dns)
        if (q != null && isBlocked(q.first)) {
            blockedCount.incrementAndGet()
            val resp = dns.copyOf(q.second)
            resp[2] = 0x81.toByte()
            resp[3] = 0x83.toByte()
            for (k in 6..11) resp[k] = 0
            write(output, buildReply(p, ihl, resp))
            return
        }
        pool.execute { forward(p, ihl, dns, output) }
    }

    private fun parseQuestion(dns: ByteArray): Pair<String, Int>? {
        if (dns.size < 17) return null
        var i = 12
        val sb = StringBuilder()
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) != 0) return null
            if (i + 1 + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, i + 1, len, Charsets.US_ASCII).lowercase())
            i += len + 1
        }
        val end = i + 1 + 4
        if (i >= dns.size || end > dns.size) return null
        return Pair(sb.toString(), end)
    }

    private fun isBlocked(name: String): Boolean {
        var d = name
        while (true) {
            if (blocklist.contains(d)) return true
            val dot = d.indexOf('.')
            if (dot < 0) return false
            d = d.substring(dot + 1)
        }
    }

    private fun forward(req: ByteArray, ihl: Int, dns: ByteArray, output: FileOutputStream) {
        try {
            val s = DatagramSocket()
            protect(s)
            s.soTimeout = 4000
            s.send(DatagramPacket(dns, dns.size, InetAddress.getByName(UPSTREAM), 53))
            val rb = ByteArray(4096)
            val rp = DatagramPacket(rb, rb.size)
            s.receive(rp)
            s.close()
            write(output, buildReply(req, ihl, rb.copyOf(rp.length)))
        } catch (e: Exception) {
        }
    }

    private fun write(output: FileOutputStream, pkt: ByteArray) {
        synchronized(output) {
            try {
                output.write(pkt)
            } catch (e: Exception) {
            }
        }
    }

    private fun buildReply(req: ByteArray, ihl: Int, payload: ByteArray): ByteArray {
        val total = ihl + 8 + payload.size
        val out = ByteArray(total)
        System.arraycopy(req, 0, out, 0, ihl)
        System.arraycopy(req, 12, out, 16, 4)
        System.arraycopy(req, 16, out, 12, 4)
        out[2] = (total shr 8).toByte()
        out[3] = (total and 0xFF).toByte()
        out[4] = 0
        out[5] = 0
        out[6] = 0
        out[7] = 0
        out[8] = 64
        out[10] = 0
        out[11] = 0
        out[ihl] = req[ihl + 2]
        out[ihl + 1] = req[ihl + 3]
        out[ihl + 2] = req[ihl]
        out[ihl + 3] = req[ihl + 1]
        val udpLen = 8 + payload.size
        out[ihl + 4] = (udpLen shr 8).toByte()
        out[ihl + 5] = (udpLen and 0xFF).toByte()
        out[ihl + 6] = 0
        out[ihl + 7] = 0
        System.arraycopy(payload, 0, out, ihl + 8, payload.size)
        var sum = 0
        var i = 0
        while (i < ihl) {
            sum += ((out[i].toInt() and 0xFF) shl 8) or (out[i + 1].toInt() and 0xFF)
            i += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv() and 0xFFFF
        out[10] = (ck shr 8).toByte()
        out[11] = (ck and 0xFF).toByte()
        return out
    }
}
