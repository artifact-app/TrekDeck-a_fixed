package com.trekdeck.remote

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.trekdeck.remote.databinding.FragmentSettingsBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** Настройки: мониторинг (Retrofit), бусты, выключение монитора, IP/PIN. */
class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    private val handler = Handler(Looper.getMainLooper())
    private var statsCall: Call<StatsResponse>? = null
    private var pinShown = false

    private val poll = object : Runnable {
        override fun run() {
            fetchStats()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        b.ipInput.setText(Prefs.ip(ctx))
        b.pinInput.setText(Prefs.pin(ctx))
        b.pinInput.transformationMethod = PasswordTransformationMethod.getInstance()

        b.boostRamBtn.setOnClickListener { boost("ram") }
        b.boostCpuBtn.setOnClickListener { boost("cpu") }
        b.boostFpsBtn.setOnClickListener { boost("fps") }
        b.monitorOffBtn.setOnClickListener { sendCmd("monitor_off") }

        b.togglePinBtn.setOnClickListener {
            pinShown = !pinShown
            b.pinInput.transformationMethod =
                if (pinShown) HideReturnsTransformationMethod.getInstance()
                else PasswordTransformationMethod.getInstance()
            b.pinInput.setSelection(b.pinInput.text?.length ?: 0)
            b.togglePinBtn.text = if (pinShown) "Скрыть PIN" else "Показать PIN"
        }

        b.reconnectBtn.setOnClickListener {
            val ip = normalizeIp(b.ipInput.text.toString())
            val pin = b.pinInput.text.toString().trim()
            if (ip.isEmpty() || pin.isEmpty()) {
                Toast.makeText(ctx, "Введите IP-адрес и PIN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                Api.create(ip)
            } catch (e: IllegalArgumentException) {
                Toast.makeText(ctx, "Неверный IP-адрес", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.saveConnection(ctx, ip, pin)
            handler.removeCallbacks(poll)
            handler.post(poll)
            TrekDeck.client.connect(ip, pin)
            Toast.makeText(ctx, "Подключаюсь…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun boost(type: String) {
        if (TrekDeck.client.connected) {
            Toast.makeText(requireContext(), "Выполняется…", Toast.LENGTH_SHORT).show()
        }
        sendCmd("boost", "type" to type)
    }

    private fun fetchStats() {
        val ctx = context ?: return
        val api = try {
            Api.create(Prefs.ip(ctx))
        } catch (e: IllegalArgumentException) {
            return
        }
        val call = api.stats(Prefs.pin(ctx))
        statsCall = call
        call.enqueue(object : Callback<StatsResponse> {
            override fun onResponse(c: Call<StatsResponse>, r: Response<StatsResponse>) {
                val bd = _b ?: return
                val s = r.body()
                if (r.code() == 401 || r.code() == 429) {
                    handler.removeCallbacks(poll) // PIN устарел или IP заблокирован — не долбим ПК каждые 2 секунды
                }
                bd.statsText.text = if (r.isSuccessful && s != null) {
                    "ЦП ${s.cpu.toInt()}%   •   ОЗУ ${s.ram.toInt()}% " +
                        "(${"%.1f".format(s.ramUsedMB / 1024.0)} / ${"%.1f".format(s.ramTotalMB / 1024.0)} ГБ)"
                } else {
                    r.errorMessage()
                }
            }

            override fun onFailure(c: Call<StatsResponse>, t: Throwable) {
                if (c.isCanceled) return
                _b?.statsText?.text = "Нет данных (нет связи с ПК)"
            }
        })
    }

    override fun onStart() {
        super.onStart()
        handler.post(poll)
    }

    override fun onStop() {
        handler.removeCallbacks(poll)
        statsCall?.cancel()
        super.onStop()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
