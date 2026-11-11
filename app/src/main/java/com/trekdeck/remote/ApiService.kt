package com.trekdeck.remote

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class ActivateRequest(val key: String)
data class ActivateResponse(val ok: Boolean, val error: String?)
data class StatusResponse(val ok: Boolean, val licensed: Boolean, val expires: Long, val host: String?)
data class StatsResponse(val cpu: Double, val ram: Double, val ramUsedMB: Long, val ramTotalMB: Long)
data class PcButton(val id: String, val title: String)

/** REST-запросы к программе на ПК. PIN передаётся в заголовке X-Pin. */
interface ApiService {
    @GET("api/status")
    fun status(@Header("X-Pin") pin: String): Call<StatusResponse>

    @POST("api/activate")
    fun activate(@Header("X-Pin") pin: String, @Body body: ActivateRequest): Call<ActivateResponse>

    @GET("api/stats")
    fun stats(@Header("X-Pin") pin: String): Call<StatsResponse>
}

object Net {
    const val PORT = 8080

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

object Api {
    /** Бросает IllegalArgumentException, если IP написан неправильно. */
    fun create(ip: String): ApiService =
        Retrofit.Builder()
            .baseUrl("http://$ip:${Net.PORT}/")
            .client(Net.http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
}

/** Достаёт текст ошибки из ответа сервера ({"ok":false,"error":"..."}). */
fun <T> Response<T>.errorMessage(): String = try {
    val text = errorBody()?.string() ?: ""
    JsonParser.parseString(text).asJsonObject.get("error").asString
} catch (e: Exception) {
    "Ошибка ${code()}"
}
