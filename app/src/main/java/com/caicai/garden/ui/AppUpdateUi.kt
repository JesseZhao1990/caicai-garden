package com.caicai.garden.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caicai.garden.BuildConfig
import com.caicai.garden.update.GitHubRelease
import com.caicai.garden.update.UpdateUiState

private val UpdatePaper = Color(0xFFFFF7E9)
private val UpdateInk = Color(0xFF203025)
private val UpdateMuted = Color(0xFF6A7768)
private val UpdateLeafDeep = Color(0xFF2F6F4E)
private val UpdateMist = Color(0xFFE4F1DA)
private val UpdateWarm = Color(0xFFFFE8C2)
private val UpdateError = Color(0xFFFFE1D8)

@Composable
fun AppUpdatePanel(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onUpdate: () -> Unit
) {
    val detail = when (state) {
        UpdateUiState.Idle -> "启动应用时会自动检查新版本"
        UpdateUiState.Checking -> "正在连接版本服务，请稍候…"
        is UpdateUiState.UpToDate -> state.detail
        is UpdateUiState.Available -> "新版本 ${state.release.versionName} 已准备好"
        is UpdateUiState.Downloading -> {
            val progress = state.progress?.let { "$it%" } ?: ""
            "正在安全下载安装包 $progress"
        }
        is UpdateUiState.InstallPermissionRequired -> "安装包已下载，授权后即可继续安装"
        is UpdateUiState.Error -> state.message
    }
    val actionLabel = when (state) {
        UpdateUiState.Idle -> "检查更新"
        UpdateUiState.Checking -> "检查中"
        is UpdateUiState.UpToDate -> "重新检查"
        is UpdateUiState.Available -> "立即升级"
        is UpdateUiState.Downloading -> "下载中"
        is UpdateUiState.InstallPermissionRequired -> "继续安装"
        is UpdateUiState.Error -> "重新检查"
    }
    val statusLabel = when (state) {
        UpdateUiState.Idle -> "自动检查"
        UpdateUiState.Checking -> "检查中"
        is UpdateUiState.UpToDate -> "已是最新"
        is UpdateUiState.Available -> "发现新版"
        is UpdateUiState.Downloading -> "下载中"
        is UpdateUiState.InstallPermissionRequired -> "等待授权"
        is UpdateUiState.Error -> "检查失败"
    }
    val statusColor = when (state) {
        is UpdateUiState.Available,
        is UpdateUiState.InstallPermissionRequired -> UpdateWarm
        is UpdateUiState.Error -> UpdateError
        else -> UpdateMist
    }
    val busy = state is UpdateUiState.Checking || state is UpdateUiState.Downloading
    val isUpdateAction = state is UpdateUiState.Available || state is UpdateUiState.InstallPermissionRequired
    val isStrongAction = isUpdateAction || state is UpdateUiState.Error

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = UpdatePaper.copy(alpha = 0.96f),
        shadowElevation = 9.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.74f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = UpdateMist,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.76f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("↑", color = UpdateLeafDeep, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "版本升级",
                        color = UpdateInk,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp
                    )
                    Text(
                        "当前版本 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = UpdateMuted
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f))
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = UpdateLeafDeep,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    detail,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = UpdateMuted
                )
                Button(
                    onClick = if (isUpdateAction) onUpdate else onCheck,
                    enabled = !busy,
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStrongAction) UpdateLeafDeep else UpdateMist,
                        contentColor = if (isStrongAction) Color.White else UpdateLeafDeep,
                        disabledContainerColor = UpdateMist.copy(alpha = 0.72f),
                        disabledContentColor = UpdateMuted
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Black)
                }
            }

            if (state is UpdateUiState.Downloading) {
                if (state.progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = UpdateLeafDeep,
                        trackColor = UpdateMist
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = UpdateLeafDeep,
                        trackColor = UpdateMist
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
        title = { Text("发现新版本 ${release.versionName}", color = UpdateInk, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("更新内容", color = UpdateInk, fontWeight = FontWeight.Bold)
                Text(release.notes, color = UpdateMuted, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "APK 来自 GitHub Releases，下载后将由 Android 系统确认安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = UpdateMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UpdateLeafDeep)
            ) {
                Text("下载并升级", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后", color = UpdateLeafDeep) }
        },
        shape = RoundedCornerShape(26.dp),
        containerColor = UpdatePaper
    )
}
