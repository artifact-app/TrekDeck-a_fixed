package com.trekdeck.remote

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

fun JsonObject.str(key: String): String? =
    if (has(key) && !get(key).isJsonNull) get(key).asString else null

fun JsonObject.bool(key: String): Boolean? =
    if (has(key) && !get(key).isJsonNull) get(key).asBoolean else null

/**
 * Клиент WebSocket (ws://IP:8080/ws).
 * - первым сообщением отправляет PIN;
 * - при обрыве сам переподключается каждые 5 секунд;
 * - раз в 25 секунд шлёт ping, чтобы ПК не закрыл соединение.
 * Все методы вызываются из главного потока.
 */
class TrekDeckClient {

    interface Listener {
        fun onConnected(host: String) {}
        fun onButtons(list: List<PcButton>) {}
        fun onServerMessage(msg: JsonObject) {}
        fun onAuthFailed(error: String) {}
        fun onLost() {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val wsHttp = Net.http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    private var ws: WebSocket? = null
    private var ip = ""
    private var pin = ""
    private var wanted = false
    private var generation = 0
    private var lostNotified = false

    var connected = false
        private set
    var host = ""
        private set
    var buttons: List<PcButton> = emptyList()
        private set

    private val reconnect = Runnable { if (wanted && !connected) open() }
    private val pinger = object : Runnable {
        override fun run() {
            if (connected) send("ping")
            main.postDelayed(this, 25_000)
        }
    }

    fun addListener(l: Listener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    /** Подключиться (или переподключиться) к ПК. */
    fun connect(ip: String, pin: String) {
        this.ip = ip
        this.pin = pin
        wanted = true
        lostNotified = false
        main.removeCallbacks(reconnect)
        open()
    }

    /** Вернулись в приложение: если связь пропала, пока оно было в фоне — подключаемся сразу, не дожидаясь таймера. */
    fun resume() {
        if (wanted && !connected) {
            main.removeCallbacks(reconnect)
            open()
        }
    }

    fun disconnect() {
        wanted = false
        connected = false
        generation++
        main.removeCallbacks(reconnect)
        main.removeCallbacks(pinger)
        ws?.close(1000, null)
        ws = null
    }

    private fun open() {
        ws?.cancel()
        val gen = ++generation
        val request = try {
            Request.Builder().url("ws://$ip:${Net.PORT}/ws").build()
        } catch (e: IllegalArgumentException) {
            wanted = false
            listeners.forEach { it.onAuthFailed("Неверный IP-адрес") }
            return
        }
        ws = wsHttp.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val o = JsonObject()
                o.addProperty("cmd", "auth")
                o.addProperty("pin", pin)
                webSocket.send(o.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post { if (gen == generation) handle(text) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post { if (gen == generation) dropped() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { if (gen == generation) dropped() }
            }
        })
    }

    private fun handle(text: String) {
        val obj = try {
            JsonParser.parseString(text).asJsonObject
        } catch (e: Exception) {
            return
        }
        when (obj.str("cmd")) {
            "auth" -> {
                if (obj.bool("ok") == true) {
                    connected = true
                    lostNotified = false
                    host = obj.str("host") ?: ""
                    buttons = parseButtons(obj)
                    main.removeCallbacks(pinger)
                    main.postDelayed(pinger, 25_000)
                    listeners.forEach { it.onConnected(host) }
                    listeners.forEach { it.onButtons(buttons) }
                } else {
                    // неверный PIN / нет активации — переподключаться бессмысленно
                    val error = obj.str("error") ?: "Ошибка входа"
                    disconnect()
                    listeners.forEach { it.onAuthFailed(error) }
                }
            }
            "buttons" -> {
                buttons = parseButtons(obj)
                listeners.forEach { it.onButtons(buttons) }
            }
            "pong" -> {}
            else -> listeners.forEach { it.onServerMessage(obj) }
        }
    }

    private fun parseButtons(o: JsonObject): List<PcButton> = try {
        o.getAsJsonArray("buttons").mapNotNull { e ->
            val x = e.asJsonObject
            val id = x.str("id")
            val title = x.str("title")
            if (id != null && title != null) PcButton(id, title) else null
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun dropped() {
        connected = false
        main.removeCallbacks(pinger)
        if (!wanted) return
        if (!lostNotified) {
            lostNotified = true
            listeners.forEach { it.onLost() }
        }
        main.removeCallbacks(reconnect)
        main.postDelayed(reconnect, 5_000)
    }

    /** Отправить команду ПК. Возвращает false, если связи нет. */
    fun send(cmd: String, vararg extra: Pair<String, Any>): Boolean {
        if (!connected) return false
        val o = JsonObject()
        o.addProperty("cmd", cmd)
        for ((k, v) in extra) {
            when (v) {
                is Number -> o.addProperty(k, v)
                is Boolean -> o.addProperty(k, v)
                else -> o.addProperty(k, v.toString())
            }
        }
        return ws?.send(o.toString()) == true
    }
}

/** Один общий клиент на всё приложение. */
object TrekDeck {
    val client = TrekDeckClient()
}

/** Отправить команду, а если связи нет — показать подсказку. */
fun Fragment.sendCmd(cmd: String, vararg extra: Pair<String, Any>) {
    if (!TrekDeck.client.send(cmd, *extra)) {
        Toast.makeText(requireContext(), "Нет связи с ПК", Toast.LENGTH_SHORT).show()
    }
}
