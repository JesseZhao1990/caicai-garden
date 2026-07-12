package com.caicai.garden.ui

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caicai.garden.R
import com.caicai.garden.data.CropLibrary
import com.caicai.garden.data.Garden
import com.caicai.garden.data.GardenDataState
import com.caicai.garden.data.GrowingStyle
import com.caicai.garden.data.OperationRecord
import com.caicai.garden.data.OperationType
import com.caicai.garden.data.PlantingMethod
import com.caicai.garden.data.Plot
import com.caicai.garden.data.TaskPriority
import com.caicai.garden.data.TaskReminder
import com.caicai.garden.data.WeatherForecast
import com.caicai.garden.domain.PlantingInsight
import java.time.LocalDate
import java.time.LocalDateTime

private enum class VisualTab(val label: String, val symbol: String) {
    TODAY("今日", "今"),
    GARDEN("菜园", "园"),
    CALENDAR("日历", "历"),
    RECORDS("记录", "记")
}

private enum class VisualSheet {
    EDIT_GARDEN,
    ADD_PLOT,
    ADD_BATCH,
    ADD_OPERATION
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
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.message
    var selectedTab by rememberSaveable { mutableStateOf(VisualTab.TODAY) }
    var activeSheet by remember { mutableStateOf<VisualSheet?>(null) }
    var selectedPlotId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    val plots = viewModel.dataState.plots
    LaunchedEffect(plots.firstOrNull()?.id) {
        if (selectedPlotId == null) selectedPlotId = plots.firstOrNull()?.id
    }

    Scaffold(
        containerColor = WarmBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            when (selectedTab) {
                VisualTab.TODAY, VisualTab.RECORDS -> GardenFab("记录") { activeSheet = VisualSheet.ADD_OPERATION }
                VisualTab.GARDEN -> Unit
                VisualTab.CALENDAR -> GardenFab("加地块") { activeSheet = VisualSheet.ADD_PLOT }
            }
        },
        bottomBar = {
            GardenBottomNav(selectedTab = selectedTab, onSelectedTabChange = { selectedTab = it })
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
                .padding(padding)
        ) {
            when (selectedTab) {
                VisualTab.TODAY -> VisualTodayScreen(
                    viewModel = viewModel,
                    onOpenMap = { selectedTab = VisualTab.GARDEN },
                    onRefreshWeather = viewModel::refreshWeather
                )

                VisualTab.GARDEN -> VisualGardenScreen(
                    viewModel = viewModel,
                    onSelectedPlotChange = { selectedPlotId = it }
                )

                VisualTab.CALENDAR -> VisualCalendarScreen(
                    tasks = viewModel.scheduleTasks,
                    onComplete = viewModel::completeTask
                )

                VisualTab.RECORDS -> VisualRecordsScreen(
                    state = viewModel.dataState
                )
            }
        }
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

        VisualSheet.ADD_BATCH -> VisualAddPlantingDialog(
            plots = viewModel.dataState.plots,
            onDismiss = { activeSheet = null },
            onSave = { plotId, cropId, variety, method, date, quantity ->
                viewModel.addBatch(plotId, cropId, variety, method, date, quantity)
                activeSheet = null
            }
        )

        VisualSheet.ADD_OPERATION -> VisualRecordOperationDialog(
            state = viewModel.dataState,
            onDismiss = { activeSheet = null },
            onSave = { batchId, type, amount, note ->
                viewModel.addOperation(batchId, type, amount, note)
                activeSheet = null
            }
        )

        null -> Unit
    }
}

@Composable
private fun GardenFab(label: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = LeafDeep,
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("+", fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(label, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun GardenBottomNav(selectedTab: VisualTab, onSelectedTabChange: (VisualTab) -> Unit) {
    Surface(
        modifier = Modifier
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
    onRefreshWeather: () -> Unit
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
                    VisualTaskCard(task = task, onComplete = viewModel::completeTask)
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
private fun VisualGardenScreen(
    viewModel: GardenViewModel,
    onSelectedPlotChange: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            RealGardenHeader(viewModel = viewModel)
        }
        item {
            FarmDesignerSection(
                viewModel = viewModel,
                onSelectedPlotChange = onSelectedPlotChange
            )
        }
    }
}

@Composable
private fun RealGardenHeader(viewModel: GardenViewModel) {
    val activeCount = viewModel.insights.size
    val taskCount = viewModel.todayTasks.size
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFEAF6DE),
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("我的实体菜园", color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(
                    "拖拽摆放真实菜畦，作物随种植天数长大",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$activeCount 块", color = LeafDeep, fontWeight = FontWeight.Black)
                Text("$taskCount 待办", color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                        "${insight.crop.name} · 第 ${insight.rawDay} 天 · ${insight.progressPercent}%",
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
        item {
            GlassPanel(tint = Color(0xFFE4F3DE)) {
                Text("天气风险", fontWeight = FontWeight.Black, color = Ink)
                Text("高温或降雨会改变浇水、施肥窗口。建议每天早上看一眼日历。", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VisualRecordsScreen(state: GardenDataState) {
    val records = state.records.sortedByDescending { it.timestamp }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(kicker = "操作、照片、产量", title = "菜园记录", trailing = "+")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                RecordPhotoCard(
                    title = records.getOrNull(0)?.type?.label ?: "番茄浇透",
                    subtitle = records.getOrNull(0)?.timestamp?.recordDateLabel() ?: "今天",
                    modifier = Modifier.weight(1f),
                    crop = GardenCrop.TOMATO
                )
                RecordPhotoCard(
                    title = records.getOrNull(1)?.type?.label ?: "黄瓜搭架",
                    subtitle = records.getOrNull(1)?.timestamp?.recordDateLabel() ?: "昨天",
                    modifier = Modifier.weight(1f),
                    crop = GardenCrop.CUCUMBER
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                RecordPhotoCard("采摘记录", "小白菜 500g", Modifier.weight(1f), GardenCrop.LEAFY)
                RecordPhotoCard("地块整理", "阳台箱", Modifier.weight(1f), GardenCrop.GREENHOUSE)
            }
        }
        item {
            GlassPanel {
                Text("本周小结", fontWeight = FontWeight.Black, color = Ink)
                Text(
                    "浇水 ${records.count { it.type == OperationType.WATER }} 次，施肥 ${records.count { it.type == OperationType.FERTILIZE }} 次，采摘 ${records.count { it.type == OperationType.HARVEST }} 次。",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        items(records.take(6), key = { it.id }) { record ->
            RecordListItem(record, state)
        }
    }
}

@Composable
private fun PageHeader(kicker: String, title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(kicker, color = Muted, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(title, color = Ink, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 32.sp)
        }
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.72f),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.76f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(trailing, color = LeafDeep, fontWeight = FontWeight.Black)
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
            StatStone("${insight?.rawDay ?: 0} 天", "已种植", Modifier.weight(1f))
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
private fun RecordPhotoCard(title: String, subtitle: String, modifier: Modifier = Modifier, crop: GardenCrop) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Paper.copy(alpha = 0.95f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.76f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                GardenMapImage(Modifier.fillMaxSize(), crop = crop)
            }
            Text(title, modifier = Modifier.padding(top = 9.dp), fontWeight = FontWeight.Black, color = Ink, maxLines = 1)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall)
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
        VisualDropdown("种植方式", style, GrowingStyle.values().toList(), { it.label }) { style = it }
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
private fun VisualAddPlantingDialog(
    plots: List<Plot>,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, PlantingMethod, String, String) -> Unit
) {
    var plot by remember { mutableStateOf(plots.firstOrNull()) }
    var crop by remember { mutableStateOf(CropLibrary.crops.first()) }
    var variety by rememberSaveable { mutableStateOf("") }
    var method by remember { mutableStateOf(PlantingMethod.SEED) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var quantity by rememberSaveable { mutableStateOf("") }
    GardenDialog(title = "添加种植", onDismiss = onDismiss, onConfirm = { onSave(plot?.id, crop.id, variety, method, date, quantity) }) {
        if (plots.isEmpty()) {
            Text("还没有地块，请先添加地块", color = MaterialTheme.colorScheme.error)
        } else {
            VisualDropdown("地块", plot ?: plots.first(), plots, { it.name }) { plot = it }
            VisualDropdown("方式", method, PlantingMethod.values().toList(), { it.label }) { method = it }
            GardenField(date, { date = it }, "日期 YYYY-MM-DD")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CropLibrary.crops.take(8), key = { it.id }) { option ->
                    val selected = option.id == crop.id
                    Surface(
                        modifier = Modifier
                            .width(122.dp)
                            .height(94.dp)
                            .clickable { crop = option },
                        shape = RoundedCornerShape(22.dp),
                        color = if (selected) Color(0xFFFFF3DF) else GardenMist,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFD7A252) else Color.White.copy(alpha = 0.7f))
                    ) {
                        Box {
                            GardenMapImage(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(86.dp)
                                    .clip(RoundedCornerShape(18.dp)),
                                crop = if (option.category.contains("瓜")) GardenCrop.CUCUMBER else if (option.category.contains("茄")) GardenCrop.TOMATO else GardenCrop.LEAFY
                            )
                            Column(modifier = Modifier.padding(11.dp)) {
                                Text(option.name, fontWeight = FontWeight.Black, color = Ink)
                                Text("${option.harvestStartDay}-${option.harvestEndDay} 天", color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            GardenField(variety, { variety = it }, "品种")
            GardenField(quantity, { quantity = it }, "数量/面积")
        }
    }
}

@Composable
private fun VisualRecordOperationDialog(
    state: GardenDataState,
    onDismiss: () -> Unit,
    onSave: (String?, OperationType, String, String) -> Unit
) {
    val batches = state.batches
    var selectedBatchId by rememberSaveable { mutableStateOf(batches.firstOrNull()?.id) }
    var type by remember { mutableStateOf(OperationType.WATER) }
    var amount by rememberSaveable { mutableStateOf("浇透") }
    var note by rememberSaveable { mutableStateOf("") }
    GardenDialog(title = "记录操作", onDismiss = onDismiss, onConfirm = { onSave(selectedBatchId, type, amount, note) }) {
        if (batches.isNotEmpty()) {
            VisualDropdown(
                label = "关联批次",
                selected = batches.firstOrNull { it.id == selectedBatchId } ?: batches.first(),
                options = batches,
                labeler = { batch ->
                    val crop = CropLibrary.byId(batch.cropId)
                    val plot = state.plots.firstOrNull { it.id == batch.plotId }
                    "${plot?.name ?: "未分配"} · ${crop.name}"
                },
                onSelect = { selectedBatchId = it.id }
            )
        }
        VisualDropdown("类型", type, OperationType.values().toList(), { it.label }) { type = it }
        GardenField(amount, { amount = it }, "用量/产量")
        GardenField(note, { note = it }, "备注")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            GardenMapImage(Modifier.fillMaxSize(), GardenCrop.TOMATO)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp),
                shape = RoundedCornerShape(16.dp),
                color = Paper.copy(alpha = 0.86f)
            ) {
                Text("1 号畦 · 番茄", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Black, color = Ink)
            }
        }
    }
}

@Composable
private fun GardenDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
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
                colors = ButtonDefaults.buttonColors(containerColor = LeafDeep),
                shape = RoundedCornerShape(18.dp)
            ) { Text("保存") }
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
private fun <T> VisualDropdown(
    label: String,
    selected: T,
    options: List<T>,
    labeler: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Text(labeler(selected), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("v")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(labeler(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
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
