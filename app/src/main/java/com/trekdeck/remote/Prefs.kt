package com.trekdeck.remote

import android.content.Context

const val BUY_URL = "https://funpay.com/users/22153201/"
const val DISCORD_URL = "https://discord.gg/7prNTgHnE"

/** "http://192.168.1.5:8080/" -> "192.168.1.5" */
fun normalizeIp(s: String): String =
    s.trim().removePrefix("http://").removePrefix("https://").substringBefore("/").substringBefore(":")

/** Хранилище настроек (SharedPreferences): IP и PIN. Ключ активации на телефоне не хранится. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("trekdeck", Context.MODE_PRIVATE)

    fun ip(c: Context): String = sp(c).getString("ip", "") ?: ""
    fun pin(c: Context): String = sp(c).getString("pin", "") ?: ""

    fun saveConnection(c: Context, ip: String, pin: String) {
        sp(c).edit().putString("ip", ip).putString("pin", pin).apply()
    }
}
