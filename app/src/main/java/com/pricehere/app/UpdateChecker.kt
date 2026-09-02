package com.pricehere.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val downloadUrl: String?,
)

/**
 * GitHub Releases에서 최신 버전을 확인한다.
 * 실패하면 조용히 null을 돌려준다 — 업데이트 확인 때문에 앱이 멈추면 안 된다.
 */
object UpdateChecker {

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/wjdals988/pricehere/releases/latest"

    suspend fun fetch(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val body = try {
                if (conn.responseCode !in 200..299) return@withContext null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }

            val json = JSONObject(body)
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null

            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isBlank()) return@withContext null

            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")

            UpdateInfo(
                latestVersion = tag,
                releaseUrl = json.optString("html_url"),
                downloadUrl = apk,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** "1.10.0" > "1.9.0" 처럼 자리수가 달라도 맞게 비교한다. */
    fun isNewerThan(candidate: String, current: String): Boolean {
        val a = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
