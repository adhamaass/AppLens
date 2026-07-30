package com.applens.network

import com.applens.data.AppMetadata
import com.applens.data.ScreenInfo
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

data class AnalyzeRequest(
    @SerializedName("appName") val appName: String,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("version") val version: String,
    @SerializedName("metadata") val metadata: AppMetadata,
    @SerializedName("screens") val screens: List<ScreenInfo>
)

class ApiClient(
    private var baseUrl: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun updateBaseUrl(ip: String) {
        baseUrl = "http://$ip:3000"
    }

    fun getBaseUrl(): String = baseUrl

    /**
     * Send the extraction data to the backend and receive a ZIP file.
     * Returns the ZIP file bytes.
     */
    fun analyze(appName: String, packageName: String, version: String,
                metadata: AppMetadata, screens: List<ScreenInfo>): ByteArray {
        val request = AnalyzeRequest(appName, packageName, version, metadata, screens)
        val json = gson.toJson(request)
        val body = json.toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("$baseUrl/analyze")
            .post(body)
            .build()

        val response: Response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Server returned ${response.code}: ${response.message}")
        }
        return response.body?.bytes()
            ?: throw RuntimeException("Empty response from server")
    }

    /**
     * Health check the backend.
     */
    fun ping(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
