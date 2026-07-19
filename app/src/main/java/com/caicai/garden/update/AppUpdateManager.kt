package com.caicai.garden.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import com.caicai.garden.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

data class GitHubRelease(
    val tagName: String,
    val versionName: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkName: String,
    val sha256: String?
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val detail: String) : UpdateUiState
    data class Available(val release: GitHubRelease) : UpdateUiState
    data class Downloading(val release: GitHubRelease, val progress: Int?) : UpdateUiState
    data class InstallPermissionRequired(val release: GitHubRelease, val apk: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class AppUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
    private var monitorJob: Job? = null

    val state: State<UpdateUiState> = _state

    suspend fun checkForUpdate() {
        if (resumePendingDownload()) return

        publish(UpdateUiState.Checking)
        val result = runCatching { fetchLatestRelease() }
        val release = result.getOrElse {
            publish(UpdateUiState.Error(it.message ?: "检查更新失败，请稍后重试"))
            return
        }
        when {
            release == null -> publish(UpdateUiState.UpToDate("发布仓库中还没有正式版本"))
            compareVersions(release.versionName, BuildConfig.VERSION_NAME) > 0 -> {
                publish(UpdateUiState.Available(release))
            }
            else -> publish(UpdateUiState.UpToDate("当前已是最新版本"))
        }
    }

    suspend fun downloadAndInstall(release: GitHubRelease) {
        val existing = loadPendingDownload()
        if (existing != null && existing.release.tagName == release.tagName) {
            resumePendingDownload()
            return
        }

        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir == null) {
            publish(UpdateUiState.Error("设备下载目录不可用，请稍后重试"))
            return
        }

        clearPendingDownload(removeSystemTask = true)
        downloadsDir.mkdirs()
        downloadsDir.listFiles()
            ?.filter { it.name.startsWith(DOWNLOAD_FILE_PREFIX) }
            ?.forEach(File::delete)

        val fileName = "$DOWNLOAD_FILE_PREFIX${release.tagName}.apk"
        val target = File(downloadsDir, fileName)
        target.delete()
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("菜园管家 ${release.versionName}")
            .setDescription("正在后台下载安全安装包")
            .setMimeType(APK_MIME_TYPE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )

        val downloadId = runCatching { downloadManager.enqueue(request) }.getOrElse {
            publish(UpdateUiState.Error(it.message ?: "无法启动系统下载任务"))
            return
        }
        val pending = PendingDownload(downloadId, release, target)
        savePendingDownload(pending)
        publish(UpdateUiState.Downloading(release, 0))
        startMonitoring(pending)
    }

    suspend fun continueInstall(state: UpdateUiState.InstallPermissionRequired) {
        continueInstall(state.release, state.apk)
    }

    fun close() {
        monitorJob?.cancel()
        scope.cancel()
    }

    private suspend fun continueInstall(release: GitHubRelease, apk: File) {
        if (!apk.isFile) {
            clearPendingDownload(removeSystemTask = false)
            publish(UpdateUiState.Error("安装包已被清理，请重新下载"))
            return
        }
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            publish(UpdateUiState.InstallPermissionRequired(release, apk))
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            return
        }

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        appContext.startActivity(intent)
    }

    private suspend fun resumePendingDownload(): Boolean {
        val pending = loadPendingDownload() ?: return false
        if (compareVersions(pending.release.versionName, BuildConfig.VERSION_NAME) <= 0) {
            clearPendingDownload(removeSystemTask = true)
            pending.target.delete()
            return false
        }

        val snapshot = withContext(Dispatchers.IO) {
            queryDownload(pending.downloadId)
        }
        return when (snapshot?.status) {
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED -> {
                publish(UpdateUiState.Downloading(pending.release, snapshot.progress))
                startMonitoring(pending)
                true
            }

            DownloadManager.STATUS_SUCCESSFUL -> {
                finishDownloadedApk(pending)
                true
            }

            DownloadManager.STATUS_FAILED -> {
                clearPendingDownload(removeSystemTask = false)
                publish(
                    UpdateUiState.Error(
                        "系统下载失败${snapshot.reason.takeIf { it > 0 }?.let { "（错误 $it）" } ?: ""}"
                    )
                )
                true
            }

            else -> {
                clearPendingDownload(removeSystemTask = false)
                false
            }
        }
    }

    private fun startMonitoring(pending: PendingDownload) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                val current = loadPendingDownload()
                if (current?.downloadId != pending.downloadId) return@launch

                val snapshot = withContext(Dispatchers.IO) {
                    queryDownload(pending.downloadId)
                }
                when (snapshot?.status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> {
                        publish(UpdateUiState.Downloading(pending.release, snapshot.progress))
                    }

                    DownloadManager.STATUS_SUCCESSFUL -> {
                        finishDownloadedApk(pending)
                        return@launch
                    }

                    DownloadManager.STATUS_FAILED -> {
                        clearPendingDownload(removeSystemTask = false)
                        publish(
                            UpdateUiState.Error(
                                "系统下载失败${snapshot.reason.takeIf { it > 0 }?.let { "（错误 $it）" } ?: ""}"
                            )
                        )
                        return@launch
                    }

                    else -> {
                        clearPendingDownload(removeSystemTask = false)
                        publish(UpdateUiState.Error("系统下载任务已失效，请重新下载"))
                        return@launch
                    }
                }
                delay(DOWNLOAD_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private suspend fun finishDownloadedApk(pending: PendingDownload) {
        publish(UpdateUiState.Downloading(pending.release, 100))
        val validationError = withContext(Dispatchers.IO) {
            when {
                !pending.target.isFile -> "系统显示下载完成，但没有找到安装包"
                pending.release.sha256 == null -> null
                else -> {
                    val actualSha256 = sha256(pending.target)
                    if (actualSha256.equals(pending.release.sha256, ignoreCase = true)) {
                        null
                    } else {
                        pending.target.delete()
                        "安装包完整性校验失败，请重新下载"
                    }
                }
            }
        }
        if (validationError != null) {
            clearPendingDownload(removeSystemTask = false)
            publish(UpdateUiState.Error(validationError))
            return
        }
        publish(UpdateUiState.InstallPermissionRequired(pending.release, pending.target))
    }

    private fun queryDownload(downloadId: Long): DownloadSnapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            val downloadedBytes = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val totalBytes = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            val reason = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            )
            DownloadSnapshot(
                status = status,
                progress = downloadProgress(downloadedBytes, totalBytes),
                reason = reason
            )
        }
    }

    private fun clearPendingDownload(removeSystemTask: Boolean) {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        if (removeSystemTask && downloadId != NO_DOWNLOAD_ID) {
            runCatching { downloadManager.remove(downloadId) }
        }
        preferences.edit().clear().apply()
    }

    private fun savePendingDownload(pending: PendingDownload) {
        preferences.edit()
            .putLong(KEY_DOWNLOAD_ID, pending.downloadId)
            .putString(KEY_RELEASE, encodeRelease(pending.release).toString())
            .putString(KEY_TARGET_PATH, pending.target.absolutePath)
            .apply()
    }

    private fun loadPendingDownload(): PendingDownload? {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        val releaseRaw = preferences.getString(KEY_RELEASE, null)
        val targetPath = preferences.getString(KEY_TARGET_PATH, null)
        if (downloadId == NO_DOWNLOAD_ID || releaseRaw.isNullOrBlank() || targetPath.isNullOrBlank()) {
            return null
        }
        val release = runCatching { decodeRelease(JSONObject(releaseRaw)) }.getOrNull()
            ?: return null
        return PendingDownload(downloadId, release, File(targetPath))
    }

    private suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        val endpoint = "https://api.github.com/repos/${BuildConfig.GITHUB_RELEASE_REPOSITORY}/releases/latest"
        val connection = openConnection(endpoint)
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> return@withContext null
                HttpURLConnection.HTTP_OK -> Unit
                else -> error("GitHub 返回 ${connection.responseCode}，请稍后重试")
            }

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            val apkAsset = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
                .sortedByDescending { it.optString("name").contains("caicai", ignoreCase = true) }
                .firstOrNull()
                ?: error("最新 Release 中没有 APK 安装包")

            val tagName = json.getString("tag_name")
            GitHubRelease(
                tagName = tagName,
                versionName = tagName.removePrefix("v").removePrefix("V"),
                notes = json.optString("body").ifBlank { "本次版本没有填写更新说明。" },
                pageUrl = json.getString("html_url"),
                apkUrl = apkAsset.getString("browser_download_url"),
                apkName = apkAsset.getString("name"),
                sha256 = apkAsset.optString("digest")
                    .takeIf { it.startsWith("sha256:") }
                    ?.removePrefix("sha256:")
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "CaiCaiGarden/${BuildConfig.VERSION_NAME}")
        }
    }

    private suspend fun publish(newState: UpdateUiState) {
        withContext(Dispatchers.Main.immediate) {
            _state.value = newState
        }
    }

    private data class PendingDownload(
        val downloadId: Long,
        val release: GitHubRelease,
        val target: File
    )

    private data class DownloadSnapshot(
        val status: Int,
        val progress: Int?,
        val reason: Int
    )

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val PREFERENCES_NAME = "caicai_update_download"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_RELEASE = "release"
        private const val KEY_TARGET_PATH = "target_path"
        private const val DOWNLOAD_FILE_PREFIX = "caicai-garden-"
        private const val NO_DOWNLOAD_ID = -1L
        private const val DOWNLOAD_POLL_INTERVAL_MILLIS = 700L

        internal fun compareVersions(left: String, right: String): Int {
            val leftParts = numericVersionParts(left)
            val rightParts = numericVersionParts(right)
            val count = maxOf(leftParts.size, rightParts.size)
            for (index in 0 until count) {
                val difference = (leftParts.getOrNull(index) ?: 0)
                    .compareTo(rightParts.getOrNull(index) ?: 0)
                if (difference != 0) return difference
            }
            return 0
        }

        internal fun downloadProgress(downloadedBytes: Long, totalBytes: Long): Int? {
            if (totalBytes <= 0L) return null
            return ((downloadedBytes.coerceAtLeast(0L) * 100L) / totalBytes)
                .toInt()
                .coerceIn(0, 100)
        }

        private fun numericVersionParts(version: String): List<Int> {
            return Regex("\\d+").findAll(version).map { it.value.toInt() }.toList()
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(file.inputStream(), digest).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    if (input.read(buffer) < 0) break
                    // DigestInputStream updates the digest while reading.
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun encodeRelease(release: GitHubRelease): JSONObject {
            return JSONObject()
                .put("tagName", release.tagName)
                .put("versionName", release.versionName)
                .put("notes", release.notes)
                .put("pageUrl", release.pageUrl)
                .put("apkUrl", release.apkUrl)
                .put("apkName", release.apkName)
                .put("sha256", release.sha256)
        }

        private fun decodeRelease(json: JSONObject): GitHubRelease {
            return GitHubRelease(
                tagName = json.getString("tagName"),
                versionName = json.getString("versionName"),
                notes = json.optString("notes"),
                pageUrl = json.getString("pageUrl"),
                apkUrl = json.getString("apkUrl"),
                apkName = json.getString("apkName"),
                sha256 = json.optString("sha256").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
