package com.trekdeck.remote

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.trekdeck.remote.databinding.ActivityLoginBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Экран подключения и активации.
 * 1) IP + PIN -> GET /api/status (проверяет PIN и узнаёт, активирован ли ПК);
 * 2) если ПК не активирован -> ввод ключа -> POST /api/activate.
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NO_AUTO = "no_auto"
    }

    private lateinit var b: ActivityLoginBinding
    private var ipUsed = ""
    private var pinUsed = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.ipInput.setText(Prefs.ip(this))
        b.pinInput.setText(Prefs.pin(this))

        b.connectBtn.setOnClickListener { checkConnection() }
        b.activateBtn.setOnClickListener { activate() }
        b.buyBtn.setOnClickListener { openUrl(BUY_URL) }
        b.discordBtn.setOnClickListener { openUrl(DISCORD_URL) }

        // Автоподключение с сохранёнными данными
        val auto = !intent.getBooleanExtra(EXTRA_NO_AUTO, false)
        if (auto && Prefs.ip(this).isNotEmpty() && Prefs.pin(this).isNotEmpty()) {
            checkConnection()
        }
    }

    private fun checkConnection() {
        val ip = normalizeIp(b.ipInput.text.toString())
        val pin = b.pinInput.text.toString().trim()
        if (ip.isEmpty() || pin.isEmpty()) {
            showError("Введите IP-адрес и PIN")
            return
        }
        val api = try {
            Api.create(ip)
        } catch (e: IllegalArgumentException) {
            showError("Неверный IP-адрес")
            return
        }
        showError("")
        setBusy(true)
        ipUsed = ip
        pinUsed = pin

        api.status(pin).enqueue(object : Callback<StatusResponse> {
            override fun onResponse(call: Call<StatusResponse>, response: Response<StatusResponse>) {
                if (isDestroyed) return
                setBusy(false)
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    showError(response.errorMessage())
                    return
                }
                Prefs.saveConnection(this@LoginActivity, ip, pin)
                if (body.licensed) {
                    goMain()
                } else {
                    b.keyBlock.visibility = View.VISIBLE
                    b.infoText.text = "ПК «${body.host ?: ip}» найден, но не активирован. Введите ключ."
                    b.keyInput.requestFocus()
                }
            }

            override fun onFailure(call: Call<StatusResponse>, t: Throwable) {
                if (isDestroyed) return
                setBusy(false)
                showError("Не удалось связаться с ПК. Проверьте IP, Wi-Fi и что TrekDeck запущен на ПК.")
            }
        })
    }

    private fun activate() {
        val key = b.keyInput.text.toString().trim().uppercase()
        if (key.isEmpty()) {
            showError("Введите ключ")
            return
        }
        showError("")
        setBusy(true)
        Api.create(ipUsed).activate(pinUsed, ActivateRequest(key)).enqueue(object : Callback<ActivateResponse> {
            override fun onResponse(call: Call<ActivateResponse>, response: Response<ActivateResponse>) {
                if (isDestroyed) return
                setBusy(false)
                val body = response.body()
                if (response.isSuccessful && body?.ok == true) {
                    goMain()
                } else {
                    showError(body?.error ?: response.errorMessage())
                }
            }

            override fun onFailure(call: Call<ActivateResponse>, t: Throwable) {
                if (isDestroyed) return
                setBusy(false)
                showError("Нет связи с ПК")
            }
        })
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showError(text: String) {
        b.errorText.text = text
    }

    private fun setBusy(busy: Boolean) {
        b.progress.visibility = if (busy) View.VISIBLE else View.GONE
        b.connectBtn.isEnabled = !busy
        b.activateBtn.isEnabled = !busy
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            showError("Не найден браузер для открытия ссылки")
        }
    }
}
