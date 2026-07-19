package com.caicai.garden.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.caicai.garden.R
import com.caicai.garden.data.CropLibrary
import com.caicai.garden.data.CropProfile
import com.caicai.garden.data.Garden
import com.caicai.garden.data.GardenDataState
import com.caicai.garden.data.GrowingStyle
import com.caicai.garden.data.OperationRecord
import com.caicai.garden.data.OperationType
import com.caicai.garden.data.Plot
import com.caicai.garden.data.TaskPriority
import com.caicai.garden.data.TaskReminder
import com.caicai.garden.data.TaskType
import com.caicai.garden.data.WeatherForecast
import com.caicai.garden.domain.PlantingInsight
import com.caicai.garden.update.AppUpdateManager
import com.caicai.garden.update.UpdateUiState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

private enum class VisualTab(val label: String, val symbol: String) {
    TODAY("今日", "今"),
    GARDEN("菜园", "园"),
    CALENDAR("日历", "历"),
    PROFILE("我的", "我")
}

private enum class VisualSheet {
    EDIT_GARDEN,
    ADD_PLOT
}

private val WarmBackground = Color(0xFFF6F5E8)
private val GardenMist = Color(0xFFE6F1DD)
private val Leaf = Color(0xFF4F8F45)
private val LeafDeep = Color(0xFF2F6F4E)
private val Wood = Color(0xFFA36B3F)
private val Soil = Color(0xFF9A6240)
private val Paper = Color(0xFFFFF7E9)
private val Ink = Color(0xFF203025)
private val Muted = Color(0xFF6A7768)
private val Stone = Color(0xFFD7D0BC)

@Composable
fun VisualGardenApp(viewModel: GardenViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val updateManager = remember { AppUpdateManager(context) }
    val updateState by updateManager.state
    val coroutineScope = rememberCoroutineScope()
    val message = viewModel.message
    var selectedTab by rememberSaveable { mutableStateOf(VisualTab.TODAY) }
    var activeSheet by remember { mutableStateOf<VisualSheet?>(null) }
    var selectedPlotId by rememberSaveable { mutableStateOf<String?>(null) }
    var dismissedReleaseTag by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLifecycleTask by remember { mutableStateOf<TaskReminder?>(null) }

    val startUpdate: () -> Unit = {
        coroutineScope.launch {
            when (val state = updateState) {
                is UpdateUiState.Available -> updateManager.downloadAndInstall(state.release)
                is UpdateUiState.InstallPermissionRequired -> updateManager.continueInstall(state)
                else -> updateManager.checkForUpdate()
            }
        }
    }

    DisposableEffect(updateManager) {
        onDispose { updateManager.close() }
    }

    LaunchedEffect(Unit) {
        updateManager.checkForUpdate()
    }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    val plots = viewModel.dataState.plots
    val plotIds = plots.map { it.id }
    LaunchedEffect(plotIds) {
        if (selectedPlotId !in plotIds) {
            selectedPlotId = plots.firstOrNull()?.id
        }
    }

    Scaffold(
        containerColor = WarmBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            GardenBottomNav(
                selectedTab = selectedTab,
                onSelectedTabChange = { selectedTab = it }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFFE7A2).copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(220f, 60f),
                        radius = 520f
                    )
                )
                .then(
                    if (selectedTab == VisualTab.GARDEN) {
                        Modifier.padding(top = padding.calculateTopPadding())
                    } else {
                        Modifier.padding(padding)
                    }
                )
        ) {
            when (selectedTab) {
                VisualTab.TODAY -> VisualTodayScreen(
                    viewModel = viewModel,
                    onOpenMap = { selectedTab = VisualTab.GARDEN },
                    onRefreshWeather = viewModel::refreshWeather,
                    onCompleteTask = { task ->
                        if (task.type == TaskType.LIFECYCLE) {
                            pendingLifecycleTask = task
                        } else {
                            viewModel.completeTask(task)
                        }
                    }
                )

                VisualTab.GARDEN -> VisualGardenScreen(
                    viewModel = viewModel,
                    selectedPlotId = selectedPlotId,
                    onSelectedPlotChange = { selectedPlotId = it },
                    onAddPlot = { activeSheet = VisualSheet.ADD_PLOT }
                )

                VisualTab.CALENDAR -> VisualCalendarScreen(
                    tasks = viewModel.scheduleTasks,
                    onComplete = { task ->
                        if (task.type == TaskType.LIFECYCLE) {
                            pendingLifecycleTask = task
                        } else {
                            viewModel.completeTask(task)
                        }
                    }
                )

                VisualTab.PROFILE -> VisualProfileScreen(
                    state = viewModel.dataState,
                    updateState = updateState,
                    onCheckUpdate = { coroutineScope.launch { updateManager.checkForUpdate() } },
                    onStartUpdate = startUpdate
                )
            }

        }
    }

    pendingLifecycleTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingLifecycleTask = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = Paper,
            title = {
                Text("结束这茬作物？", color = Ink, fontWeight = FontWeight.Black)
            },
            text = {
                Text(
                    "${task.title}\n\n确认后会停止后续养护提醒并清空地图上的菜格，历史记录仍会保留。",
                    color = Muted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.completeTask(task)
                        pendingLifecycleTask = null
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LeafDeep)
                ) {
                    Text("确认结束", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLifecycleTask = null }) {
                    Text("暂不结束", color = Muted, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    when (activeSheet) {
        VisualSheet.EDIT_GARDEN -> VisualGardenSettingsDialog(
            garden = viewModel.garden,
            onDismiss = { activeSheet = null },
            onSave = { name, location, lat, lon ->
                viewModel.updateGarden(name, location, lat, lon)
                activeSheet = null
            }
        )

        VisualSheet.ADD_PLOT -> VisualAddPlotDialog(
            onDismiss = { activeSheet = null },
            onSave = { name, size, style, soil ->
                viewModel.addPlot(name, size, style, soil)
                activeSheet = null
            }
        )

        null -> Unit
    }

    val availableUpdate = updateState as? UpdateUiState.Available
    if (availableUpdate != null && dismissedReleaseTag != availableUpdate.release.tagName) {
        AppUpdateDialog(
            release = availableUpdate.release,
            onDismiss = { dismissedReleaseTag = availableUpdate.release.tagName },
            onUpdate = startUpdate
        )
    }
}

@Composable
private fun GardenBottomNav(
    selectedTab: VisualTab,
    onSelectedTabChange: (VisualTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = Paper.copy(alpha = 0.94f),
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            VisualTab.values().forEach { tab ->
                val active = selectedTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (active) Color(0xFFDFEBD5) else Color.Transparent)
                        .clickable { onSelectedTabChange(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(tab.symbol, fontWeight = FontWeight.Black, color = if (active) LeafDeep else Muted)
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (active) LeafDeep else Muted)
                }
            }
        }
    }
}

@Composable
private fun VisualTodayScreen(
    viewModel: GardenViewModel,
    onOpenMap: () -> Unit,
    onRefreshWeather: () -> Unit,
    onCompleteTask: (TaskReminder) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                kicker = "早上好，菜园状态不错",
                title = "今日菜园",
                trailing = "晴"
            )
        }
        item {
            GardenHeroPreview(
                subtitle = "${viewModel.dataState.plots.size} 块地 · ${viewModel.todayTasks.size} 个待办",
                onOpenMap = onOpenMap
            )
        }
        item {
            WeatherGlassCard(viewModel.weather, viewModel.weatherLoading, onRefreshWeather)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                viewModel.todayTasks.take(3).forEach { task ->
                    VisualTaskCard(task = task, onComplete = onCompleteTask)
                }
                if (viewModel.todayTasks.isEmpty()) {
                    GlassPanel {
                        Text("今天没有必须处理的任务", fontWeight = FontWeight.Bold, color = Ink)
                        Text("可以拍一张菜地照片，给后续判断长势做参考。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualProfileScreen(
    state: GardenDataState,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onStartUpdate: () -> Unit
) {
    val records = state.records.sortedByDescending { it.timestamp }
    val weekStart = LocalDate.now().minusDays(6)
    val weeklyRecords = records.filter { record ->
        runCatching { LocalDateTime.parse(record.timestamp).toLocalDate() }
            .getOrNull()
            ?.isBefore(weekStart) == false
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                kicker = null,
                title = "我的",
                trailing = null
            )
        }
        item {
            GardenFootprintSummary(
                weeklyRecords = weeklyRecords,
                totalRecordCount = records.size
            )
        }
        item {
            Text(
                "最近足迹",
                color = Ink,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (records.isEmpty()) {
            item {
                GlassPanel {
                    Text("还没有菜园足迹", color = Ink, fontWeight = FontWeight.Black)
                    Text(
                        "在菜园里完成种植、浇水、施肥或采摘后，会自动出现在这里。",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            items(records.take(6), key = { it.id }) { record ->
                RecordListItem(record, state)
            }
        }
        item {
            Text(
                "应用服务",
                color = Ink,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium
            )
        }
        item {
            AppUpdatePanel(
                state = updateState,
                onCheck = onCheckUpdate,
                onUpdate = onStartUpdate
            )
        }
    }
}

@Composable
private fun GardenFootprintSummary(
    weeklyRecords: List<OperationRecord>,
    totalRecordCount: Int
) {
    GlassPanel(tint = Color(0xFFE8F3DF)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("菜园足迹", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    "近 7 天 · 累计 $totalRecordCount 条",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("自然发生，自动留下", color = LeafDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FootprintMetric(
                label = "种植",
                count = weeklyRecords.count {
                    it.type == OperationType.SOW ||
                        it.type == OperationType.TRANSPLANT ||
                        it.type == OperationType.CUTTING ||
                        it.type == OperationType.DIVISION
                },
                modifier = Modifier.weight(1f)
            )
            FootprintMetric(
                label = "浇水",
                count = weeklyRecords.count { it.type == OperationType.WATER },
                modifier = Modifier.weight(1f)
            )
            FootprintMetric(
                label = "施肥",
                count = weeklyRecords.count { it.type == OperationType.FERTILIZE },
                modifier = Modifier.weight(1f)
            )
            FootprintMetric(
                label = "采摘",
                count = weeklyRecords.count { it.type == OperationType.HARVEST },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FootprintMetric(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Paper.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count 次", color = LeafDeep, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VisualGardenScreen(
    viewModel: GardenViewModel,
    selectedPlotId: String?,
    onSelectedPlotChange: (String) -> Unit,
    onAddPlot: () -> Unit
) {
    val plots = viewModel.dataState.plots
    val selectedPlot = plots.firstOrNull { it.id == selectedPlotId } ?: plots.firstOrNull()
    var pendingDeletePlot by remember { mutableStateOf<Plot?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        FarmDesignerSection(
            viewModel = viewModel,
            selectedPlotId = selectedPlot?.id,
            onSelectedPlotChange = onSelectedPlotChange,
            modifier = Modifier.fillMaxSize(),
            immersive = true,
            bottomContentPadding = 84.dp
        )
        RealGardenHeader(
            plots = plots,
            selectedPlotId = selectedPlot?.id,
            insights = viewModel.insights,
            tasks = viewModel.todayTasks,
            onSelectedPlotChange = onSelectedPlotChange,
            onAddPlot = onAddPlot,
            onDeletePlot = { pendingDeletePlot = it },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .zIndex(10f)
        )
    }

    pendingDeletePlot?.let { plot ->
        val activeCount = viewModel.insights.count { it.plot?.id == plot.id }
        AlertDialog(
            onDismissRequest = { pendingDeletePlot = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = Paper,
            title = {
                Text("删除${plot.name}？", color = Ink, fontWeight = FontWeight.Black)
            },
            text = {
                Text(
                    if (activeCount > 0) {
                        "该地块还有 $activeCount 种作物。删除后地图布局会移除，相关作物停止养护提醒；菜园足迹仍会保留。"
                    } else {
                        "删除后该地块及地图布局会移除，菜园足迹仍会保留。"
                    },
                    color = Muted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlot(plot.id)
                        pendingDeletePlot = null
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB34A3C))
                ) {
                    Text("确认删除", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePlot = null }) {
                    Text("取消", color = Muted, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun RealGardenHeader(
    plots: List<Plot>,
    selectedPlotId: String?,
    insights: List<PlantingInsight>,
    tasks: List<TaskReminder>,
    onSelectedPlotChange: (String) -> Unit,
    onAddPlot: () -> Unit,
    onDeletePlot: (Plot) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedPlot = plots.firstOrNull { it.id == selectedPlotId } ?: plots.firstOrNull()
    val selectedIndex = plots.indexOfFirst { it.id == selectedPlot?.id }
    val activeCount = insights.count { it.plot?.id == selectedPlot?.id }
    val taskCount = tasks.count { it.plotId == selectedPlot?.id }

    if (!expanded) {
        Surface(
            modifier = modifier
                .width(174.dp)
                .height(52.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(19.dp),
            color = Color(0xFFF0F7E8).copy(alpha = 0.97f),
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.82f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = LeafDeep
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("园", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
                Text(
                    selectedPlot?.name ?: "添加地块",
                    modifier = Modifier.weight(1f),
                    color = Ink,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (selectedIndex >= 0) "${selectedIndex + 1}/${plots.size}" else "0/0",
                    color = LeafDeep,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
                Text("⌄", color = LeafDeep, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFF0F7E8).copy(alpha = 0.99f),
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.80f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = LeafDeep
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("园", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        selectedPlot?.name ?: "还没有地块",
                        color = Ink,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        selectedPlot?.let {
                            "${it.growingStyle.label} · ${it.soilType} · $activeCount 种植 · $taskCount 待办"
                        } ?: "展开地块管理后添加第一块菜地",
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Paper.copy(alpha = 0.84f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.76f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            if (selectedIndex >= 0) "${selectedIndex + 1}/${plots.size}" else "0/0",
                            color = LeafDeep,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                        Text(
                            if (expanded) "⌃" else "⌄",
                            color = LeafDeep,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Stone.copy(alpha = 0.45f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "地块管理",
                                color = Ink,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                            Text(
                                "切换、添加或删除地块",
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            modifier = Modifier.clickable(onClick = onAddPlot),
                            shape = RoundedCornerShape(14.dp),
                            color = LeafDeep,
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text("添加地块", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                    if (plots.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onAddPlot),
                            shape = RoundedCornerShape(16.dp),
                            color = Paper.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f))
                        ) {
                            Text(
                                "还没有地块，点击这里添加第一块菜地",
                                modifier = Modifier.padding(14.dp),
                                color = LeafDeep,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        GardenPlotSwitcher(
                            plots = plots,
                            selectedPlotId = selectedPlot?.id,
                            insights = insights,
                            onSelectedPlotChange = { plotId ->
                                onSelectedPlotChange(plotId)
                                expanded = false
                            },
                            onDeletePlot = onDeletePlot
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GardenPlotSwitcher(
    plots: List<Plot>,
    selectedPlotId: String?,
    insights: List<PlantingInsight>,
    onSelectedPlotChange: (String) -> Unit,
    onDeletePlot: (Plot) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 234.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        plots.forEach { plot ->
            val selected = plot.id == selectedPlotId
            val plotActiveCount = insights.count { it.plot?.id == plot.id }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { onSelectedPlotChange(plot.id) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) LeafDeep else Paper.copy(alpha = 0.80f),
                shadowElevation = if (selected) 3.dp else 0.dp,
                border = BorderStroke(
                    1.dp,
                    if (selected) Color.White.copy(alpha = 0.38f) else Stone.copy(alpha = 0.52f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                plot.name,
                                color = if (selected) Color.White else Ink,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selected) {
                                Text(
                                    "当前",
                                    color = Color.White.copy(alpha = 0.80f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            "${plot.growingStyle.label} · ${plot.soilType} · $plotActiveCount 种植",
                            color = if (selected) Color.White.copy(alpha = 0.76f) else Muted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .height(34.dp)
                            .clickable { onDeletePlot(plot) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            Color.White.copy(alpha = 0.16f)
                        } else {
                            Color(0xFFFFE5DE)
                        }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "删除",
                                color = if (selected) Color.White else Color(0xFFB34A3C),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealGardenGrowthSummary(insights: List<PlantingInsight>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Paper.copy(alpha = 0.96f),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.70f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("生长概览", color = Ink, fontWeight = FontWeight.Black)
            insights.take(3).forEach { insight ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.width((36 + insight.progressPercent / 2).dp).height(10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF74B84B)
                    ) {}
                    Text(
                        "${insight.crop.name} · ${insight.batch.method.materialLabel} ${insight.batch.startDate} · ${insight.progressPercent}%",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (insights.isEmpty()) {
                Text("还没有生长中的作物，可以先添加种植批次。", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VisualCalendarScreen(tasks: List<TaskReminder>, onComplete: (TaskReminder) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeader(kicker = "未来 7 天", title = "菜园日历", trailing = "▣") }
        if (tasks.isEmpty()) {
            item { GlassPanel { Text("未来 7 天没有计划任务", fontWeight = FontWeight.Bold) } }
        } else {
            items(tasks.take(6), key = { it.id }) { task ->
                StoneTimelineItem(task, onComplete)
            }
        }
    }
}

@Composable
private fun PageHeader(kicker: String?, title: String, trailing: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            kicker?.let {
                Text(it, color = Muted, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Text(title, color = Ink, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 32.sp)
        }
        trailing?.let {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.72f),
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.76f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(it, color = LeafDeep, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun GardenHeroPreview(subtitle: String, onOpenMap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onOpenMap)
    ) {
        GardenMapImage(Modifier.fillMaxSize(), crop = GardenCrop.CENTER)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF1B341E).copy(alpha = 0.42f))
                    )
                )
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Paper.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(subtitle, fontWeight = FontWeight.Black, color = Ink)
                Text("查看地图", fontWeight = FontWeight.Black, color = LeafDeep)
            }
        }
    }
}

@Composable
private fun WeatherGlassCard(weather: WeatherForecast?, loading: Boolean, onRefresh: () -> Unit) {
    GlassPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (weather == null) "--°" else "${weather.currentTempC.oneDecimal()}°",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink
                )
                Text(
                    text = if (loading) "更新中" else "湿度 ${weather?.humidityPercent ?: "--"}% · ${weather?.source ?: "待刷新"}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(16.dp)) {
                Text("刷新")
            }
        }
    }
}

@Composable
private fun VisualTaskCard(task: TaskReminder, onComplete: (TaskReminder) -> Unit) {
    GlassPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (task.priority == TaskPriority.HIGH) Color(0xFFFFE7DE) else Color(0xFFE0EFD2)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(task.type.label.take(1), fontWeight = FontWeight.Black, color = LeafDeep)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Black, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(task.detail, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { onComplete(task) }) {
                Text(task.actionLabel)
            }
        }
    }
}

@Composable
private fun ImmersiveGardenMap(
    plots: List<Plot>,
    selectedPlot: Plot?,
    insights: List<PlantingInsight>,
    tasks: List<TaskReminder>,
    onSelectedPlotChange: (String) -> Unit,
    onAddPlot: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = GardenMist,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    MapChip("全园", selectedPlot == null) {
                        plots.firstOrNull()?.id?.let(onSelectedPlotChange)
                    }
                }
                items(plots, key = { it.id }) { plot ->
                    MapChip(plot.name, selectedPlot?.id == plot.id) { onSelectedPlotChange(plot.id) }
                }
                item { MapChip("+ 地块", false, onAddPlot) }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 520.dp)
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(26.dp))
            ) {
                GardenMapImage(Modifier.fillMaxSize(), crop = GardenCrop.FULL)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF142218).copy(alpha = 0.38f)),
                                startY = 260f
                            )
                        )
                )
                PlotHighlight(
                    modifier = Modifier
                        .offset(x = maxWidth * 0.36f, y = maxHeight * 0.29f)
                        .width(maxWidth * 0.32f)
                        .height(maxHeight * 0.17f)
                )
                MapPin(
                    title = plots.getOrNull(0)?.name ?: "1 号畦",
                    subtitle = insights.getOrNull(0)?.let { "${it.crop.name} · ${it.progressPercent}%" } ?: "番茄 · 坐果期",
                    active = selectedPlot?.id == plots.getOrNull(0)?.id,
                    modifier = Modifier.offset(x = maxWidth * 0.42f, y = maxHeight * 0.38f)
                )
                MapPin(
                    title = plots.getOrNull(1)?.name ?: "2 号畦",
                    subtitle = "黄瓜 · 结果期",
                    active = selectedPlot?.id == plots.getOrNull(1)?.id,
                    modifier = Modifier.offset(x = maxWidth * 0.12f, y = maxHeight * 0.55f)
                )
                MapPin(
                    title = plots.getOrNull(2)?.name ?: "叶菜床",
                    subtitle = "生菜 · 可采摘",
                    active = selectedPlot?.id == plots.getOrNull(2)?.id,
                    modifier = Modifier.offset(x = maxWidth * 0.58f, y = maxHeight * 0.57f)
                )
                MapTaskPanel(
                    task = tasks.firstOrNull(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun MapChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) LeafDeep else Paper.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
        shadowElevation = 4.dp
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            color = if (selected) Color.White else Ink,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun PlotHighlight(modifier: Modifier) {
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = Color(0xFFFFE676).copy(alpha = 0.15f),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFFFF2A6).copy(alpha = 0.95f),
            style = Stroke(width = 2.dp.toPx()),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
        )
    }
}

@Composable
private fun MapPin(title: String, subtitle: String, active: Boolean, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (active) LeafDeep.copy(alpha = 0.92f) else Paper.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Text(title, color = if (active) Color.White else Ink, fontWeight = FontWeight.Black, maxLines = 1)
            Text(
                subtitle,
                color = if (active) Color.White.copy(alpha = 0.78f) else Muted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MapTaskPanel(task: TaskReminder?, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Paper.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)),
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task?.title ?: "今日优先：查看菜地状态", fontWeight = FontWeight.Black, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(task?.detail ?: "根据天气和作物阶段安排今日任务", color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Surface(shape = RoundedCornerShape(15.dp), color = LeafDeep) {
                Text(
                    task?.actionLabel ?: "去处理",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun PlotDetailPanel(plot: Plot, insight: PlantingInsight?, tasks: List<TaskReminder>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(kicker = "${plot.growingStyle.label} · ${plot.soilType} · ${plot.sizeLabel}", title = insight?.crop?.name?.let { "${it}地块" } ?: plot.name, trailing = "⋯")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(244.dp)
                .clip(RoundedCornerShape(26.dp))
                .border(6.dp, Wood.copy(alpha = 0.72f), RoundedCornerShape(26.dp))
        ) {
            GardenMapImage(Modifier.fillMaxSize(), crop = GardenCrop.TOMATO)
            PlotHighlight(
                Modifier
                    .offset(x = 118.dp, y = 76.dp)
                    .width(150.dp)
                    .height(78.dp)
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(18.dp),
                color = Paper.copy(alpha = 0.86f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${insight?.crop?.name ?: "空地"} · ${insight?.stage?.name ?: "可安排种植"}", fontWeight = FontWeight.Black, color = Ink)
                    Text("${insight?.progressPercent ?: 0}%", fontWeight = FontWeight.Black, color = LeafDeep)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatStone("${insight?.progressPercent ?: 0}%", "生长进度", Modifier.weight(1f))
            StatStone(
                "${insight?.rawDay ?: 0} 天",
                insight?.let { "${it.batch.method.materialLabel} · ${it.batch.startDate.drop(5)}" } ?: "种植记录",
                Modifier.weight(1f)
            )
            StatStone(insight?.harvestWindow ?: "--", "预计采收", Modifier.weight(1f))
        }
        GlassPanel {
            Text(tasks.firstOrNull { it.plotId == plot.id }?.title ?: "下次管理", fontWeight = FontWeight.Black, color = Ink)
            Text(
                tasks.firstOrNull { it.plotId == plot.id }?.detail ?: (insight?.nextFocus ?: "这块地暂无待办，可记录一次照片。"),
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatStone(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Paper.copy(alpha = 0.9f),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, color = Ink, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StoneTimelineItem(task: TaskReminder, onComplete: (TaskReminder) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.size(width = 46.dp, height = 38.dp),
            shape = RoundedCornerShape(18.dp, 16.dp, 18.dp, 14.dp),
            color = Stone,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, Color(0xFF8F7F63).copy(alpha = 0.18f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(task.dueDate.relativeLabel(), fontWeight = FontWeight.Black, color = Ink, fontSize = 12.sp)
            }
        }
        GlassPanel(modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Black, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(task.detail, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(task.priority.label, fontWeight = FontWeight.Black, color = priorityColor(task.priority))
                TextButton(onClick = { onComplete(task) }) {
                    Text(task.actionLabel, color = LeafDeep, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun RecordListItem(record: OperationRecord, state: GardenDataState) {
    val batch = record.batchId?.let { id -> state.batches.firstOrNull { it.id == id } }
    val crop = batch?.let { CropLibrary.byId(it.cropId) }
    GlassPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(42.dp), shape = CircleShape, color = Color(0xFFE0EFD2)) {
                Box(contentAlignment = Alignment.Center) { Text(record.type.label.take(1), color = LeafDeep, fontWeight = FontWeight.Black) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(record.type.label, fontWeight = FontWeight.Black, color = Ink)
                Text(listOfNotNull(crop?.name, record.amountLabel.takeIf { it.isNotBlank() }).joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(record.timestamp.recordDateLabel(), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    tint: Color = Paper,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = tint.copy(alpha = 0.88f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.68f))
    ) {
        Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

private enum class GardenCrop {
    FULL,
    CENTER,
    TOMATO,
    CUCUMBER,
    LEAFY,
    GREENHOUSE
}

private fun gardenCropFor(crop: CropProfile?): GardenCrop {
    return when {
        crop == null -> GardenCrop.CENTER
        crop.category.contains("瓜") -> GardenCrop.CUCUMBER
        crop.category.contains("茄果") -> GardenCrop.TOMATO
        else -> GardenCrop.LEAFY
    }
}

@Composable
private fun GardenMapImage(modifier: Modifier = Modifier, crop: GardenCrop) {
    val alignment = when (crop) {
        GardenCrop.FULL -> Alignment.Center
        GardenCrop.CENTER -> Alignment.Center
        GardenCrop.TOMATO -> BiasAlignment(0.05f, -0.35f)
        GardenCrop.CUCUMBER -> BiasAlignment(-0.75f, 0.1f)
        GardenCrop.LEAFY -> BiasAlignment(0.55f, 0.05f)
        GardenCrop.GREENHOUSE -> BiasAlignment(-0.95f, -0.92f)
    }
    Image(
        painter = painterResource(R.drawable.garden_map_isometric_v1),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        alignment = alignment
    )
}

@Composable
private fun VisualGardenSettingsDialog(
    garden: Garden?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(garden?.name ?: "我的菜园") }
    var location by rememberSaveable { mutableStateOf(garden?.locationName ?: "北京") }
    var latitude by rememberSaveable { mutableStateOf(garden?.latitude?.toString() ?: "39.9042") }
    var longitude by rememberSaveable { mutableStateOf(garden?.longitude?.toString() ?: "116.4074") }
    GardenDialog(title = "编辑菜园", onDismiss = onDismiss, onConfirm = { onSave(name, location, latitude, longitude) }) {
        GardenField(name, { name = it }, "名称")
        GardenField(location, { location = it }, "地点")
        GardenField(latitude, { latitude = it }, "纬度", KeyboardType.Decimal)
        GardenField(longitude, { longitude = it }, "经度", KeyboardType.Decimal)
    }
}

@Composable
private fun VisualAddPlotDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, GrowingStyle, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("西侧菜床") }
    var size by rememberSaveable { mutableStateOf("2.4m x 1.2m") }
    var soil by rememberSaveable { mutableStateOf("壤土") }
    var style by remember { mutableStateOf(GrowingStyle.OPEN_FIELD) }
    GardenDialog(title = "添加地块", onDismiss = onDismiss, onConfirm = { onSave(name, size, style, soil) }) {
        GardenField(name, { name = it }, "名称")
        GrowingStylePicker(selected = style, onSelect = { style = it })
        GardenField(soil, { soil = it }, "土壤")
        GardenField(size, { size = it }, "尺寸")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            GardenMapImage(Modifier.fillMaxSize(), GardenCrop.LEAFY)
            PlotHighlight(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(140.dp)
                    .height(74.dp)
            )
        }
    }
}

@Composable
private fun GardenDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmLabel: String = "保存",
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Black, color = Ink) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = LeafDeep),
                shape = RoundedCornerShape(18.dp)
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = Paper
    )
}

@Composable
private fun GardenField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun GrowingStylePicker(
    selected: GrowingStyle,
    onSelect: (GrowingStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "种植方式",
            color = Muted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GrowingStyle.values().forEach { option ->
                val isSelected = selected == option
                Surface(
                    onClick = { onSelect(option) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = if (isSelected) LeafDeep else GardenMist,
                    contentColor = if (isSelected) Color.White else LeafDeep,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) {
                            Color.White.copy(alpha = 0.34f)
                        } else {
                            Stone.copy(alpha = 0.62f)
                        }
                    ),
                    shadowElevation = if (isSelected) 3.dp else 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun priorityColor(priority: TaskPriority): Color {
    return when (priority) {
        TaskPriority.HIGH -> Color(0xFFD65D3A)
        TaskPriority.MEDIUM -> Color(0xFFB47A2F)
        TaskPriority.LOW -> LeafDeep
    }
}

private fun Double.oneDecimal(): String = String.format("%.1f", this)

private fun LocalDate.relativeLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "今"
        today.plusDays(1) -> "明"
        today.plusDays(2) -> "后"
        else -> "${monthValue}/${dayOfMonth}"
    }
}

private fun String.recordDateLabel(): String {
    val parsed = runCatching { LocalDateTime.parse(this) }.getOrNull()
    return if (parsed == null) {
        this
    } else {
        "${parsed.monthValue}/${parsed.dayOfMonth}"
    }
}
