package id.flufclip.player

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AccountHeaderRedirectTest {
    @Test fun credentialsAreRemovedOnEachHttpRedirectHop() {
        ServerSocket(0, 2).use { server ->
            server.soTimeout = 5000
            val worker = Executors.newSingleThreadExecutor()
            try {
                val observed = worker.submit<List<List<String>>> {
                    (0..1).map { hop ->
                        server.accept().use { socket ->
                            socket.soTimeout = 5000
                            val reader = socket.getInputStream().bufferedReader()
                            val lines = mutableListOf<String>()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                lines.add(line)
                            }
                            val response = if (hop == 0) "HTTP/1.1 302 Found\r\nLocation: /second\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                else "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
                            lines
                        }
                    }
                }
                val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS)
                    .addNetworkInterceptor(accountHeaderInterceptor).build()
                val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/first")
                    .header("Cookie", "synthetic=value").header("Authorization", "synthetic").build()
                client.newCall(request).execute().use { assertEquals(200, it.code) }
                val requests = observed.get(5, TimeUnit.SECONDS)
                assertEquals(2, requests.size)
                requests.forEach { lines ->
                    assertFalse(lines.any { it.startsWith("Cookie:", true) || it.startsWith("Authorization:", true) })
                }
            } finally {
                worker.shutdownNow()
            }
        }
    }
}
