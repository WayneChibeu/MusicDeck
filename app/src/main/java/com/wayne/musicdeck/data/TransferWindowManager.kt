package com.wayne.musicdeck.data

import android.content.Context
import java.net.ServerSocket
import java.net.Socket
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.*

class TransferWindowManager(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun startServer(port: Int = 8080): String? {
        if (isRunning) return getIpAddress()
        
        return try {
            serverSocket = ServerSocket(port)
            isRunning = true
            
            scope.launch {
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                }
            }
            
            getIpAddress() + ":$port"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun stopServer() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        scope.cancel()
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = PrintWriter(socket.getOutputStream())
            
            val line = reader.readLine()
            if (line != null && line.startsWith("GET")) {
                val html = """
                    HTTP/1.1 200 OK
                    Content-Type: text/html
                    
                    <html>
                    <head>
                        <title>MusicDeck Transfer Window</title>
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>
                            body { font-family: sans-serif; background: #0f172a; color: white; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                            .card { background: #1e293b; padding: 2rem; border-radius: 1rem; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); text-align: center; max-width: 400px; width: 90%; }
                            h1 { color: #38bdf8; }
                            p { color: #94a3b8; }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <h1>MusicDeck 🛡️</h1>
                            <p>Transfer Window is Open</p>
                            <p>To upload songs, please use the MusicDeck Android App interface.</p>
                            <small>Connection from: ${socket.inetAddress.hostAddress}</small>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                out.println(html)
                out.flush()
            }
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getIpAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            var ip = "127.0.0.1"
            for (intf in interfaces) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        ip = addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            ip
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }
}
