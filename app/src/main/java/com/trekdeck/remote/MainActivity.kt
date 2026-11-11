package com.trekdeck.remote

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.gson.JsonObject
import com.trekdeck.remote.databinding.ActivityMainBinding

/** Главный экран: три вкладки (Тачпад / Кнопки / Настройки) и общее подключение к ПК. */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private val listener = object : TrekDeckClient.Listener {
        override fun onConnected(host: String) = toast("Подключено: $host")

        override fun onLost() = toast("Связь с ПК потеряна")

        override fun onAuthFailed(error: String) {
            toast(error)
            startActivity(
                Intent(this@MainActivity, LoginActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_NO_AUTO, true)
            )
            finish()
        }

        override fun onServerMessage(msg: JsonObject) {
            if (msg.str("cmd") == "boost") {
                toast(msg.str("msg") ?: "Готово")
            } else if (msg.bool("ok") == false) {
                toast(msg.str("error") ?: "Ошибка")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.ip(this).isEmpty() || Prefs.pin(this).isEmpty()) {
            // нет сохранённого подключения (например, данные приложения очистили) — на экран входа
            startActivity(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_NO_AUTO, true))
            finish()
            return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        TrekDeck.client.connect(Prefs.ip(this), Prefs.pin(this))

        b.nav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_touchpad -> TouchpadFragment()
                R.id.nav_buttons -> ButtonsFragment()
                else -> SettingsFragment()
            }
            supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
            true
        }
        if (savedInstanceState == null) b.nav.selectedItemId = R.id.nav_touchpad
    }

    private var wasStopped = false

    override fun onStart() {
        super.onStart()
        TrekDeck.client.addListener(listener)
        if (wasStopped) {
            wasStopped = false
            TrekDeck.client.resume()
        }
    }

    override fun onStop() {
        wasStopped = true
        TrekDeck.client.removeListener(listener)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) TrekDeck.client.disconnect()
        super.onDestroy()
    }
}
