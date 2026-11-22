package com.example.integrations

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.security.KeystoreVault
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

data class WebpageContent(
    val url: String,
    val title: String,
    val extractedText: String,
    val linkCount: Int
)

data class SeoAuditReport(
    val baseUrl: String,
    val robotsTxtStatus: String,
    val sitemapStatus: String,
    val pageTitle: String,
    val metaDescription: String,
    val h1Count: Int,
    val totalLinks: Int
)

data class WeatherForecastDay(
    val date: String,
    val maxTempC: Double,
    val minTempC: Double,
    val precipProb: Int
)

data class WeatherReport(
    val locationLabel: String,
    val temperatureC: Double,
    val windKmh: Double,
    val conditionSummary: String,
    val forecast: List<WeatherForecastDay>
)

class ResearchAndSeoIntegration(
    private val context: Context,
    private val vault: KeystoreVault
) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun searchDuckDuckGo(query: String): Result<List<WebSearchResult>> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Search query cannot be empty."))

        // If user configured SerpAPI key, use SerpAPI first
        val serpKey = vault.getSecret(KeystoreVault.KEY_SERPAPI)
        if (!serpKey.isNullOrBlank()) {
            val serpRes = searchSerpApi(clean, serpKey)
            if (serpRes.isSuccess) return@withContext serpRes
        }

        return@withContext try {
            val url = "https://html.duckduckgo.com/html/?q=${Uri.encode(clean)}"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 16) MayaAI/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("DuckDuckGo HTTP ${resp.code}"))
                }
                val results = parseDuckDuckGoHtml(html)
                if (results.isNotEmpty()) {
                    Result.success(results)
                } else {
                    // Fallback to Instant Answer JSON API
                    fetchDuckDuckGoInstant(clean)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun searchSerpApi(query: String, apiKey: String): Result<List<WebSearchResult>> {
        return try {
            val url = "https://serpapi.com/search.json?q=${Uri.encode(query)}&api_key=${Uri.encode(apiKey)}"
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return Result.failure(IllegalStateException("SerpAPI HTTP ${resp.code}"))
                val json = JSONObject(resp.body?.string().orEmpty())
                val arr = json.optJSONArray("organic_results") ?: return Result.success(emptyList())
                val out = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    WebSearchResult(
                        title = o.optString("title"),
                        url = o.optString("link"),
                        snippet = o.optString("snippet")
                    )
                }
                Result.success(out)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchDuckDuckGoInstant(query: String): Result<List<WebSearchResult>> {
        val url = "https://api.duckduckgo.com/?q=${Uri.encode(query)}&format=json&no_html=1"
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val json = JSONObject(raw)
            val list = mutableListOf<WebSearchResult>()
            val absText = json.optString("AbstractText")
            if (absText.isNotBlank()) {
                list.add(WebSearchResult(json.optString("Heading", query), json.optString("AbstractURL"), absText))
            }
            val topics = json.optJSONArray("RelatedTopics")
            if (topics != null) {
                for (i in 0 until minOf(topics.length(), 8)) {
                    val t = topics.optJSONObject(i) ?: continue
                    val txt = t.optString("Text")
                    val firstUrl = t.optString("FirstURL")
                    if (txt.isNotBlank()) list.add(WebSearchResult(txt.take(60), firstUrl, txt))
                }
            }
            return Result.success(list)
        }
    }

    suspend fun fetchWebpageText(rawUrl: String): Result<WebpageContent> = withContext(Dispatchers.IO) {
        val url = normalizeHttpsUrl(rawUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("Enter a valid http(s) URL."))
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 16) MayaAI/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(IllegalStateException("HTTP ${resp.code} fetching $url"))
                val html = resp.body?.string().orEmpty()
                val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.get(1)?.trim().orEmpty()
                val linkCount = Regex("<a\\s+[^>]*href=", RegexOption.IGNORE_CASE).findAll(html).count()
                val text = html
                    .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                    .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                Result.success(WebpageContent(url, title, text.take(12000), linkCount))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runSeoAudit(rawUrl: String): Result<SeoAuditReport> = withContext(Dispatchers.IO) {
        val normalized = normalizeHttpsUrl(rawUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("Enter a valid website URL."))
        try {
            val parsed = Uri.parse(normalized)
            val origin = "${parsed.scheme}://${parsed.host}"

            val robotsStatus = runCatching {
                client.newCall(Request.Builder().url("$origin/robots.txt").get().build()).execute().use { r ->
                    if (r.isSuccessful) "Found (${r.body?.string()?.lines()?.size ?: 0} lines)" else "HTTP ${r.code}"
                }
            }.getOrElse { "Error: ${it.localizedMessage}" }

            val sitemapStatus = runCatching {
                client.newCall(Request.Builder().url("$origin/sitemap.xml").get().build()).execute().use { r ->
                    if (r.isSuccessful) "Found (${r.body?.string()?.length ?: 0} bytes)" else "HTTP ${r.code}"
                }
            }.getOrElse { "Error: ${it.localizedMessage}" }

            client.newCall(Request.Builder().url(normalized).get().build()).execute().use { r ->
                val html = r.body?.string().orEmpty()
                val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "(Missing <title>)" }
                val metaDesc = Regex("<meta[^>]*name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "(Missing meta description)" }
                val h1Count = Regex("<h1\\b", RegexOption.IGNORE_CASE).findAll(html).count()
                val linkCount = Regex("<a\\s+[^>]*href=", RegexOption.IGNORE_CASE).findAll(html).count()
                Result.success(SeoAuditReport(origin, robotsStatus, sitemapStatus, title, metaDesc, h1Count, linkCount))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWeatherForCity(city: String): Result<WeatherReport> = withContext(Dispatchers.IO) {
        val clean = city.trim().ifBlank { return@withContext Result.failure(IllegalArgumentException("Enter a city name.")) }
        try {
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(clean)}&count=1&language=en&format=json"
            val (lat, lon, label) = client.newCall(Request.Builder().url(geoUrl).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Geocoding HTTP ${resp.code}")
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results")
                    ?: throw IllegalStateException("Location '$clean' not found.")
                if (arr.length() == 0) throw IllegalStateException("Location '$clean' not found.")
                val first = arr.getJSONObject(0)
                Triple(
                    first.getDouble("latitude"),
                    first.getDouble("longitude"),
                    "${first.optString("name")}, ${first.optString("country")}"
                )
            }
            fetchWeatherByCoords(lat, lon, label)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchWeatherForDeviceLocation(): Result<WeatherReport> {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("Location permission required for local weather."))
        }
        return try {
            val clientLoc = LocationServices.getFusedLocationProviderClient(appContext)
            val loc = clientLoc.lastLocation.await()
                ?: return Result.failure(IllegalStateException("Device last location unavailable. Try searching a city."))
            fetchWeatherByCoords(loc.latitude, loc.longitude, "Current Location (${String.format("%.2f", loc.latitude)}, ${String.format("%.2f", loc.longitude)})")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchWeatherByCoords(lat: Double, lon: Double, label: String): Result<WeatherReport> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,wind_speed_10m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto"
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(IllegalStateException("Open-Meteo HTTP ${resp.code}"))
                val json = JSONObject(resp.body?.string().orEmpty())
                val current = json.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val wind = current.getDouble("wind_speed_10m")
                val code = current.optInt("weather_code", 0)

                val daily = json.getJSONObject("daily")
                val dates = daily.getJSONArray("time")
                val maxs = daily.getJSONArray("temperature_2m_max")
                val mins = daily.getJSONArray("temperature_2m_min")
                val pops = daily.getJSONArray("precipitation_probability_max")
                val forecast = (0 until minOf(dates.length(), 7)).map { i ->
                    WeatherForecastDay(
                        date = dates.getString(i),
                        maxTempC = maxs.getDouble(i),
                        minTempC = mins.getDouble(i),
                        precipProb = pops.optInt(i, 0)
                    )
                }
                Result.success(WeatherReport(label, temp, wind, mapWmoCode(code), forecast))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openMapsSearch(query: String): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            Result.success("Opened Maps for '$query'.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeHttpsUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        val uri = Uri.parse(withScheme)
        return if (uri.host.isNullOrBlank()) null else withScheme
    }

    private fun parseDuckDuckGoHtml(html: String): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        val anchorRegex = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val snippetRegex = Regex("""<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val anchors = anchorRegex.findAll(html).toList()
        val snippets = snippetRegex.findAll(html).toList()
        for (i in anchors.indices) {
            if (results.size >= 8) break
            val href = anchors[i].groupValues[1]
            val title = anchors[i].groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            val snippet = snippets.getOrNull(i)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
            if (title.isNotBlank()) {
                results.add(WebSearchResult(title, href, snippet))
            }
        }
        return results
    }

    private fun mapWmoCode(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Condition code $code"
    }
}
