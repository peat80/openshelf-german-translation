package io.github.wingedpanther.openshelf

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface Job {
    data class Downloading(val done: Long, val total: Long) : Job {
        val fraction get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data object Checking : Job
    data object Installing : Job
    data class Failed(val message: String) : Job
    data class Held(val message: String) : Job
}

object Installer {
    val jobs = mutableStateMapOf<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun busy(id: String) = jobs[id].let { it is Job.Downloading || it == Job.Checking || it == Job.Installing }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
            .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    fun start(ctx: Context, app: App) {
        val appCtx = ctx.applicationContext
        if (busy(app.id)) return
        if (!canInstall(appCtx)) {
            jobs[app.id] = Job.Failed("Allow Open Shelf to install apps, then press Install again.")
            openInstallPermission(ctx)
            return
        }
        val apk = pickApk(app)
        val file = File(Device.cacheDir(appCtx), "${app.id}-${app.version}-${apk.abi}.apk")
        jobs[app.id] = Job.Downloading(0, apk.size)
        scope.launch {
            try {
                download(apk, file) { done, total -> jobs[app.id] = Job.Downloading(done, total) }
                jobs[app.id] = Job.Checking
                val held = withContext(Dispatchers.IO) { checkSignature(appCtx, app, file) }
                if (held != null) {
                    jobs[app.id] = Job.Held(held)
                    return@launch
                }
                jobs[app.id] = Job.Installing
                try {
                    withContext(Dispatchers.IO) { commit(appCtx, app, file) }
                } catch (e: Exception) {
                    if (!handOff(appCtx, app.id, file)) throw e
                }
            } catch (e: Exception) {
                jobs[app.id] = Job.Failed(e.message ?: "Something went wrong. Try again.")
            }
        }
    }

    private suspend fun download(apk: Apk, file: File, progress: (Long, Long) -> Unit) {
        if (file.exists() && file.length() == apk.size) return
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val conn = (URL(apk.url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "OpenShelf (+https://github.com/wingedpanther/openshelf)")
            }
            try {
                if (conn.responseCode !in 200..299) throw IOException("Download failed (HTTP ${conn.responseCode}). Try again.")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: apk.size
                val part = File(file.path + ".part")
                var done = 0L
                var lastShown = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - lastShown > 256 * 1024) {
                                lastShown = done
                                val d = done
                                withContext(Dispatchers.Main) { progress(d, total) }
                            }
                        }
                    }
                }
                if (!part.renameTo(file)) throw IOException("Couldn't save the download. Free some space and try again.")
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun checkSignature(ctx: Context, app: App, file: File): String? {
        val fileCerts = Device.archiveCerts(ctx, file)
        if (fileCerts.isNotEmpty() && app.certSha256 != null && app.certSha256 !in fileCerts) {
            return "This file is signed with a different key than the one Open Shelf recorded for ${app.name}. Held for review."
        }
        val installed = Device.installed(ctx, app.pkg)
        if (installed != null && installed.certs.isNotEmpty() && fileCerts.isNotEmpty() && installed.certs.intersect(fileCerts).isEmpty()) {
            return "The ${app.name} on this TV came from someone else's build. Android won't update it with this file."
        }
        return null
    }

    private fun commit(ctx: Context, app: App, file: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.pkg)
            setSize(file.length())
        }
        val sessionId = pi.createSession(params)
        pi.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java).putExtra("appId", app.id).putExtra("apk", file.path)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(ctx, sessionId, intent, flags).intentSender)
        }
    }

    fun handOff(ctx: Context, id: String, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.apks", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(intent)
        jobs.remove(id)
    }.isSuccess

    fun finished(id: String) {
        jobs.remove(id)
        Device.tick++
    }

    fun failed(id: String, message: String) {
        jobs[id] = Job.Failed(message)
        Device.tick++
    }

    fun cancelled(id: String) {
        jobs.remove(id)
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("appId") ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                val shown = confirm != null && runCatching { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                if (!shown) {
                    val apk = intent.getStringExtra("apk")?.let(::File)
                    if (apk == null || !Installer.handOff(ctx, id, apk)) {
                        Installer.failed(id, "This TV blocked Android's install screen. Try again, or install the APK from a computer with adb.")
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Installer.finished(id)
            PackageInstaller.STATUS_FAILURE_ABORTED -> Installer.cancelled(id)
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                Installer.failed(id, "The installed copy was signed by someone else. Uninstall it first, then install from here.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> Installer.failed(id, "Not enough space on this TV.")
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Installer.failed(id, "This build doesn't run on this TV.")
            else -> Installer.failed(id, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed. Try again.")
        }
    }
}
