package io.github.wingedpanther.openshelf

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.StatFs
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.NumberFormat
import java.util.Locale

data class Apk(val name: String, val abi: String, val url: String, val size: Long)
data class Release(val tag: String, val date: String)

data class AppArt(val banner: String?, val feature: String?, val icon: String?, val shots: List<String>) {
    val wall: String? get() = shots.firstOrNull() ?: feature ?: banner
    val tile: String? get() = banner ?: feature ?: shots.firstOrNull()
    val gallery: List<String> get() = (shots + listOfNotNull(feature, banner)).distinct()
}

data class App(
    val id: String,
    val repo: String,
    val name: String,
    val pkg: String,
    val versionCode: Long,
    val versionName: String?,
    val minSdk: Int?,
    val room: String,
    val note: String,
    val desc: String,
    val maker: String,
    val stars: Int,
    val licence: String?,
    val archived: Boolean,
    val streams: Boolean,
    val version: String,
    val date: String,
    val certSha256: String?,
    val art: AppArt,
    val apks: List<Apk>,
    val releases: List<Release>,
) {
    val runsHere: Boolean get() = (minSdk ?: 0) <= Build.VERSION.SDK_INT
}

object Catalog {
    var apps by mutableStateOf<List<App>>(emptyList())
        private set
    var generated: String = ""
        private set

    suspend fun load(ctx: Context) {
        if (apps.isNotEmpty()) return
        val parsed = withContext(Dispatchers.IO) {
            val root = JSONObject(ctx.assets.open("catalog.json").bufferedReader().use { it.readText() })
            generated = root.getString("generated")
            val list = root.getJSONArray("apps")
            (0 until list.length()).map { parse(list.getJSONObject(it)) }
        }
        apps = parsed
    }

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun parse(o: JSONObject): App {
        val art = o.optJSONObject("art") ?: JSONObject()
        val shots = art.optJSONArray("shots")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        return App(
            id = o.getString("id"), repo = o.getString("repo"), name = o.getString("name"),
            pkg = o.getString("package"), versionCode = o.getLong("versionCode"), versionName = o.str("versionName"),
            minSdk = if (o.has("minSdk") && !o.isNull("minSdk")) o.getInt("minSdk") else null,
            room = o.getString("room"), note = o.getString("note"), desc = o.optString("desc"),
            maker = o.getString("maker"), stars = o.getInt("stars"), licence = o.str("licence"),
            archived = o.optBoolean("archived"),
            streams = STREAMS.containsMatchIn(listOf(o.getString("name"), o.getString("note"), o.optString("desc"), o.getString("repo"), o.getString("package"), o.optJSONArray("topics")?.toString().orEmpty()).joinToString(" ")),
            version = o.getString("version"), date = o.getString("date"),
            certSha256 = o.str("certSha256"),
            art = AppArt(art.str("banner"), art.str("feature"), art.str("icon"), shots),
            apks = o.getJSONArray("apks").objects().map { Apk(it.getString("name"), it.getString("abi"), it.getString("url"), it.getLong("size")) },
            releases = o.getJSONArray("releases").objects().map { Release(it.getString("tag"), it.getString("date")) },
        )
    }

    const val MIN_STARS = 10

    private val STREAMS = Regex("iptv|\\bm3u8?\\b|xtream|debrid|torbox|torrserv|stremio|cloudstream|live ?tv\\b|电视直播|直播源|tvbox", RegexOption.IGNORE_CASE)

    fun shown(a: App) = !(Prefs.hideStreams && a.streams)

    val visible: List<App> get() = apps.filter { it.stars >= MIN_STARS && shown(it) }

    fun byId(id: String): App? = apps.firstOrNull { it.id == id }
}

fun pickApk(app: App): Apk {
    for (abi in Build.SUPPORTED_ABIS) app.apks.firstOrNull { it.abi == abi }?.let { return it }
    return app.apks.firstOrNull { it.abi == "universal" } ?: app.apks.firstOrNull { it.abi == "any" } ?: app.apks.first()
}

fun abiLabel(abi: String): String = when (abi) {
    "arm64-v8a" -> "ARM 64-bit"
    "armeabi-v7a" -> "ARM 32-bit"
    "x86_64" -> "x86 64-bit"
    "x86" -> "x86"
    else -> abi
}

private val androidNames = mapOf(
    21 to "5", 22 to "5.1", 23 to "6", 24 to "7", 25 to "7.1", 26 to "8", 27 to "8.1", 28 to "9",
    29 to "10", 30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16",
)

fun androidName(sdk: Int) = "Android ${androidNames[sdk] ?: "API $sdk"}"

object Fmt {
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private fun parts(d: String) = d.split("-").map { it.toInt() }
    fun date(d: String) = parts(d).let { (y, m, dd) -> "$dd ${months[m - 1]} $y" }
    fun shortDate(d: String) = parts(d).let { (_, m, dd) -> "$dd ${months[m - 1]}" }
    fun monthYear(d: String) = parts(d).let { (y, m, _) -> "${months[m - 1]} $y" }
    fun mb(bytes: Long): String = String.format(Locale.UK, "%.1f MB", bytes / 1_000_000.0)
    fun gb(bytes: Long): String = String.format(Locale.UK, "%.1f GB", bytes / 1_000_000_000.0)
    fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)
    fun stars(n: Int): String = if (n >= 1000) String.format(Locale.UK, "%.1fk", n / 1000.0).replace(".0k", "k") else "$n"
}

object Images {
    private val memory = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val locks = HashMap<String, Mutex>()
    private lateinit var dir: File

    fun init(ctx: Context) { dir = File(ctx.cacheDir, "img").apply { mkdirs() } }

    fun cached(url: String, maxWidth: Int): ImageBitmap? = memory.get("$maxWidth|$url")

    suspend fun load(url: String, maxWidth: Int): ImageBitmap? {
        val key = "$maxWidth|$url"
        memory.get(key)?.let { return it }
        val lock = synchronized(locks) { locks.getOrPut(url) { Mutex() } }
        return lock.withLock {
            memory.get(key) ?: withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(dir, hash(url))
                    if (!file.exists()) download(url, file)
                    decode(file, maxWidth)?.also { memory.put(key, it) }
                }.getOrNull()
            }
        }
    }

    private fun download(url: String, file: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "OpenShelf (+https://github.com/wingedpanther/openshelf)")
        }
        try {
            if (conn.responseCode !in 200..299) return
            val part = File(file.path + ".part")
            conn.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            part.renameTo(file)
        } finally {
            conn.disconnect()
        }
    }

    private fun decode(file: File, maxWidth: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
    }

    private fun hash(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class Installed(val versionCode: Long, val versionName: String?, val certs: Set<String>)

object Device {
    var tick by mutableIntStateOf(0)

    private var snapshotTick = -1
    private var snapshot: Map<String, Installed> = emptyMap()

    fun all(ctx: Context): Map<String, Installed> {
        if (snapshotTick != tick) {
            snapshot = ctx.packageManager.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES)
                .associate { it.packageName to Installed(it.longVersionCode, it.versionName, certs(it)) }
            snapshotTick = tick
        }
        return snapshot
    }

    fun installed(ctx: Context, pkg: String): Installed? = all(ctx)[pkg]

    fun archiveCerts(ctx: Context, file: File): Set<String> {
        val pm = ctx.packageManager
        val pi = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        val viaSigning = pi?.let { certs(it) }.orEmpty()
        if (viaSigning.isNotEmpty()) return viaSigning
        @Suppress("DEPRECATION")
        val old = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        return old?.signatures?.map { sha256(it.toByteArray()) }?.toSet().orEmpty()
    }

    private fun certs(pi: PackageInfo): Set<String> {
        val si = pi.signingInfo ?: return emptySet()
        val all = (si.apkContentsSigners.orEmpty().toList() + si.signingCertificateHistory.orEmpty().toList())
        return all.map { sha256(it.toByteArray()) }.toSet()
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    fun freeBytes(ctx: Context): Long = StatFs(ctx.filesDir.path).availableBytes
    fun totalBytes(ctx: Context): Long = StatFs(ctx.filesDir.path).totalBytes

    fun cacheDir(ctx: Context) = File(ctx.cacheDir, "apks")
    fun cacheBytes(ctx: Context): Long = cacheDir(ctx).listFiles()?.sumOf { it.length() } ?: 0L
    fun clearCache(ctx: Context) { cacheDir(ctx).listFiles()?.forEach { it.delete() } }
}

object Prefs {
    val accents = listOf(
        Triple("Sage", 0xFF3F6F55, 0xFF7DB592),
        Triple("Gallery red", 0xFFC23B2E, 0xFFE8735F),
        Triple("Ochre", 0xFF9A6412, 0xFFE0A84A),
        Triple("Plum", 0xFF5B4B8A, 0xFFA597E0),
        Triple("None", 0xFF8E8E93, 0xFFAEAEB2),
    )
    var accent by mutableIntStateOf(0)
        private set

    var hideStreams by mutableStateOf(true)
        private set

    fun toggleStreams() {
        hideStreams = !hideStreams
        file.edit().putBoolean("hideStreams", hideStreams).apply()
    }

    val themes = listOf("Match TV", "Light", "Dark")
    var theme by mutableIntStateOf(0)
        private set
    var systemDark by mutableStateOf(false)
    val dark: Boolean get() = when (theme) { 1 -> false; 2 -> true; else -> systemDark }
    private lateinit var file: android.content.SharedPreferences

    fun init(ctx: Context) {
        file = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        accent = file.getInt("accent", 0).coerceIn(accents.indices)
        theme = file.getInt("theme", 0).coerceIn(themes.indices)
        hideStreams = file.getBoolean("hideStreams", true)
    }

    fun nextTheme() {
        theme = (theme + 1) % themes.size
        file.edit().putInt("theme", theme).apply()
    }

    fun nextAccent() {
        accent = (accent + 1) % accents.size
        file.edit().putInt("accent", accent).apply()
    }
}
