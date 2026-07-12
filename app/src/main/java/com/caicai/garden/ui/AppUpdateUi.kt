package com.caicai.garden.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caicai.garden.BuildConfig
import com.caicai.garden.update.GitHubRelease
import com.caicai.garden.update.UpdateUiState

@Composable
fun AppUpdatePanel(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onUpdate: () -> Unit
) {
    val (detail, actionLabel) = when (state) {
        UpdateUiState.Idle -> "启动时自动检查，也可以随时手动检查" to "检查更新"
        UpdateUiState.Checking -> "正在连接 GitHub Releases…" to "检查中"
        is UpdateUiState.UpToDate -> state.detail to "重新检查"
        is UpdateUiState.Available -> "发现新版本 ${state.release.versionName}" to "立即升级"
        is UpdateUiState.Downloading -> {
            val progress = state.progress?.let { "$it%" } ?: ""
            "正在下载安装包 $progress" to "下载中"
        }
        is UpdateUiState.InstallPermissionRequired -> "安装包已下载，请授权安装未知应用" to "继续安装"
        is UpdateUiState.Error -> state.message to "重试"
    }
    val busy = state is UpdateUiState.Checking || state is UpdateUiState.Downloading
    val isUpdateAction = state is UpdateUiState.Available || state is UpdateUiState.InstallPermissionRequired

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("版本升级", fontWeight = FontWeight.Black)
                    Text(
                        "当前版本 ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = if (isUpdateAction) onUpdate else onCheck,
                    enabled = !busy
                ) {
                    Text(actionLabel)
                }
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state is UpdateUiState.Downloading) {
                if (state.progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    release: GitHubRelease,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 ${release.versionName}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("更新内容", fontWeight = FontWeight.Bold)
                Text(release.notes, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "APK 来自 GitHub Releases，下载后将由 Android 系统确认安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) { Text("下载并升级") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
}
