package com.caicai.garden.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import com.caicai.garden.BuildConfig
import kotlinx.coroutines.Dispatchers
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
    private val _state = mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
    val state: State<UpdateUiState> = _state

    suspend fun checkForUpdate() {
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
        publish(UpdateUiState.Downloading(release, 0))
        val apk = runCatching { downloadApk(release) }.getOrElse {
            publish(UpdateUiState.Error(it.message ?: "安装包下载失败"))
            return
        }
        continueInstall(release, apk)
    }

    suspend fun continueInstall(state: UpdateUiState.InstallPermissionRequired) {
        continueInstall(state.release, state.apk)
    }

    private suspend fun continueInstall(release: GitHubRelease, apk: File) {
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

    private suspend fun downloadApk(release: GitHubRelease): File = withContext(Dispatchers.IO) {
        val updateDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        updateDir.listFiles()?.forEach { it.delete() }
        val target = File(updateDir, "caicai-garden-${release.tagName}.apk")
        val connection = openConnection(release.apkUrl)
        try {
            if (connection.responseCode !in 200..299) {
                error("安装包下载失败：HTTP ${connection.responseCode}")
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var lastProgress = -1

            DigestInputStream(connection.inputStream, digest).use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        val progress = totalBytes?.let { ((copied * 100L) / it).toInt().coerceIn(0, 100) }
                        if (progress != null && progress >= lastProgress + 2) {
                            lastProgress = progress
                            publish(UpdateUiState.Downloading(release, progress))
                        }
                    }
                }
            }

            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (release.sha256 != null && !actualSha256.equals(release.sha256, ignoreCase = true)) {
                target.delete()
                error("安装包完整性校验失败")
            }
            target
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

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

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

        private fun numericVersionParts(version: String): List<Int> {
            return Regex("\\d+").findAll(version).map { it.value.toInt() }.toList()
        }
    }
}
