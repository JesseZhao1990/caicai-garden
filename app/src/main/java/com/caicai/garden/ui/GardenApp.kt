package com.caicai.garden.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.caicai.garden.data.CropLibrary
import com.caicai.garden.data.Garden
import com.caicai.garden.data.GardenDataState
import com.caicai.garden.data.GrowingStyle
import com.caicai.garden.data.OperationRecord
import com.caicai.garden.data.OperationType
import com.caicai.garden.data.PlantingBatch
import com.caicai.garden.data.PlantingMethod
import com.caicai.garden.data.Plot
import com.caicai.garden.data.TaskPriority
import com.caicai.garden.data.TaskReminder
import com.caicai.garden.data.WeatherForecast
import com.caicai.garden.domain.PlantingInsight
import java.time.LocalDate
import java.time.LocalDateTime

private enum class MainTab(val label: String, val symbol: String) {
    TODAY("今日", "今"),
    GARDEN("菜园", "园"),
    CALENDAR("日历", "历"),
    RECORDS("记录", "记")
}

private enum class ActiveDialog {
    GARDEN_SETTINGS,
    ADD_PLOT,
    ADD_BATCH,
    ADD_OPERATION
}

private data class BatchOption(val id: String?, val label: String)

@Composable
fun GardenApp(viewModel: GardenViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.message
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    var activeDialog by remember { mutableStateOf<ActiveDialog?>(null) }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                MainTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { NavSymbol(tab.symbol, selectedTab == tab) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                MainTab.TODAY, MainTab.RECORDS -> ExtendedFloatingActionButton(
                    text = { Text("记录") },
                    icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
                    onClick = { activeDialog = ActiveDialog.ADD_OPERATION }
                )

                MainTab.GARDEN -> Unit

                MainTab.CALENDAR -> ExtendedFloatingActionButton(
                    text = { Text("加地块") },
                    icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
                    onClick = { activeDialog = ActiveDialog.ADD_PLOT }
                )
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (selectedTab) {
                MainTab.TODAY -> TodayScreen(
                    viewModel = viewModel,
                    onEditGarden = { activeDialog = ActiveDialog.GARDEN_SETTINGS },
                    onAddRecord = { activeDialog = ActiveDialog.ADD_OPERATION }
                )

                MainTab.GARDEN -> GardenScreen(
                    viewModel = viewModel,
                    onAddPlot = { activeDialog = ActiveDialog.ADD_PLOT },
                    onAddBatch = { activeDialog = ActiveDialog.ADD_BATCH }
                )

                MainTab.CALENDAR -> CalendarScreen(viewModel.scheduleTasks, viewModel::completeTask)
                MainTab.RECORDS -> RecordsScreen(viewModel.dataState, onAddRecord = { activeDialog = ActiveDialog.ADD_OPERATION })
            }
        }
    }

    when (activeDialog) {
        ActiveDialog.GARDEN_SETTINGS -> GardenSettingsDialog(
            garden = viewModel.garden,
            onDismiss = { activeDialog = null },
            onSave = { name, location, lat, lon ->
                viewModel.updateGarden(name, location, lat, lon)
                activeDialog = null
            }
        )

        ActiveDialog.ADD_PLOT -> AddPlotDialog(
            onDismiss = { activeDialog = null },
            onSave = { name, size, style, soil ->
                viewModel.addPlot(name, size, style, soil)
                activeDialog = null
            }
        )

        ActiveDialog.ADD_BATCH -> AddBatchDialog(
            plots = viewModel.dataState.plots,
            onDismiss = { activeDialog = null },
            onSave = { plotId, cropId, variety, method, startDate, quantity ->
                viewModel.addBatch(plotId, cropId, variety, method, startDate, quantity)
                activeDialog = null
            }
        )

        ActiveDialog.ADD_OPERATION -> AddOperationDialog(
            state = viewModel.dataState,
            onDismiss = { activeDialog = null },
            onSave = { batchId, type, amount, note ->
                viewModel.addOperation(batchId, type, amount, note)
                activeDialog = null
            }
        )

        null -> Unit
    }
}

@Composable
private fun NavSymbol(text: String, selected: Boolean) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(container, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = content, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TodayScreen(
    viewModel: GardenViewModel,
    onEditGarden: () -> Unit,
    onAddRecord: () -> Unit
) {
    val garden = viewModel.garden
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeaderBlock(
            title = garden?.name ?: "菜园管家",
            subtitle = garden?.let { "${it.locationName}  ${it.latitude.formatCoord()}, ${it.longitude.formatCoord()}" } ?: "未设置位置",
            actionLabel = "编辑",
            onAction = onEditGarden
        )
        WeatherPanel(viewModel.weather, viewModel.weatherLoading, viewModel::refreshWeather)
        TaskSummaryCard(viewModel.todayTasks, viewModel::completeTask)
        SectionTitle("作物状态")
        if (viewModel.insights.isEmpty()) {
            EmptyBlock("还没有种植批次")
        } else {
            viewModel.insights.take(4).forEach { insight ->
                PlantingInsightCard(insight, compact = true, onFinish = null)
            }
        }
        OutlinedButton(onClick = onAddRecord, modifier = Modifier.fillMaxWidth()) {
            Text("手动记录一次菜园操作")
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun GardenScreen(
    viewModel: GardenViewModel,
    onAddPlot: () -> Unit,
    onAddBatch: () -> Unit
) {
    var selectedPlotId by rememberSaveable { mutableStateOf<String?>(null) }
    val plots = viewModel.dataState.plots
    val selectedPlot = plots.firstOrNull { it.id == selectedPlotId } ?: plots.firstOrNull()
    val selectedInsights = viewModel.insights.filter { it.plot?.id == selectedPlot?.id }

    LaunchedEffect(selectedPlot?.id) {
        if (selectedPlot != null && selectedPlotId != selectedPlot.id) {
            selectedPlotId = selectedPlot.id
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeaderBlock(
                title = "菜园",
                subtitle = "${viewModel.dataState.plots.size} 个地块，${viewModel.insights.size} 个生长中批次",
                actionLabel = "加地块",
                onAction = onAddPlot
            )
        }
        item {
            FarmDesignerSection(
                viewModel = viewModel,
                onSelectedPlotChange = { selectedPlotId = it }
            )
        }
        item { SectionTitle("当前地块") }
        if (selectedPlot == null) {
            item { EmptyBlock("还没有地块") }
        } else {
            item { PlotCard(selectedPlot, viewModel.dataState) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onAddBatch, modifier = Modifier.weight(1f)) {
                    Text("添加种植")
                }
                OutlinedButton(onClick = onAddPlot, modifier = Modifier.weight(1f)) {
                    Text("添加地块")
                }
            }
        }
        item { SectionTitle("当前种植") }
        if (selectedPlot == null) {
            item { EmptyBlock("添加地块后可以管理种植批次") }
        } else if (selectedInsights.isEmpty()) {
            item { EmptyBlock("${selectedPlot.name} 还没有生长中的作物") }
        } else {
            items(selectedInsights, key = { it.batch.id }) { insight ->
                PlantingInsightCard(
                    insight = insight,
                    compact = false,
                    onFinish = { viewModel.finishBatch(insight.batch.id) }
                )
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun CalendarScreen(tasks: List<TaskReminder>, onComplete: (TaskReminder) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeaderBlock(title = "7 天日历", subtitle = "${tasks.size} 项计划", actionLabel = null, onAction = null)
        }
        if (tasks.isEmpty()) {
            item { EmptyBlock("未来 7 天没有计划任务") }
        } else {
            val groups = tasks.groupBy { it.dueDate }
            groups.forEach { (date, dayTasks) ->
                item { DateHeader(date) }
                items(dayTasks, key = { it.id }) { task ->
                    TaskCard(task, onComplete)
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun RecordsScreen(state: GardenDataState, onAddRecord: () -> Unit) {
    val records = state.records.sortedByDescending { it.timestamp }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeaderBlock(
                title = "记录",
                subtitle = "${records.size} 条操作记录",
                actionLabel = "新增",
                onAction = onAddRecord
            )
        }
        if (records.isEmpty()) {
            item { EmptyBlock("还没有操作记录") }
        } else {
            items(records, key = { it.id }) { record ->
                RecordCard(record, state)
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun HeaderBlock(
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun WeatherPanel(weather: WeatherForecast?, loading: Boolean, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("天气", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (loading) "更新中" else "${weather?.locationName ?: "未获取"} · ${weather?.source ?: "待刷新"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                TextButton(onClick = onRefresh) { Text("刷新") }
            }
            if (weather == null) {
                Text("暂无天气数据", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("${weather.currentTempC.oneDecimal()}°C", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text("湿度 ${weather.humidityPercent}% · 风 ${weather.windKmh.oneDecimal()} km/h", style = MaterialTheme.typography.bodySmall)
                    }
                    weather.today?.let {
                        Text(
                            "今日 ${it.minTempC.oneDecimal()}-${it.maxTempC.oneDecimal()}°C\n降雨 ${it.precipitationMm.oneDecimal()} mm",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    weather.daily.take(3).forEach { day ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                        ) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.date.shortDate(), style = MaterialTheme.typography.labelMedium)
                                Text("${day.minTempC.oneDecimal()}-${day.maxTempC.oneDecimal()}°", style = MaterialTheme.typography.bodySmall)
                                Text("${day.precipitationMm.oneDecimal()}mm", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSummaryCard(tasks: List<TaskReminder>, onComplete: (TaskReminder) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("今日待办")
        if (tasks.isEmpty()) {
            EmptyBlock("今天没有必须处理的任务")
        } else {
            tasks.forEach { task -> TaskCard(task, onComplete) }
        }
    }
}

@Composable
private fun TaskCard(task: TaskReminder, onComplete: (TaskReminder) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = task.priority.containerColor())
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriorityPill(task.priority)
                Text(task.type.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(task.dueDate.relativeLabel(), style = MaterialTheme.typography.labelMedium)
            }
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(task.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { onComplete(task) }) {
                    Text(task.actionLabel)
                }
            }
        }
    }
}

@Composable
private fun GardenMapSection(
    plots: List<Plot>,
    selectedPlotId: String?,
    insights: List<PlantingInsight>,
    onSelectedPlotChange: (String) -> Unit
) {
    if (plots.isEmpty()) {
        EmptyBlock("添加地块后会在这里生成菜地平面图")
        return
    }

    val selectedPlot = plots.firstOrNull { it.id == selectedPlotId } ?: plots.first()
    val selectedInsights = insights.filter { it.plot?.id == selectedPlot.id }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFE8F1E4)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(plots, key = { it.id }) { plot ->
                    FilterChip(
                        selected = plot.id == selectedPlot.id,
                        onClick = { onSelectedPlotChange(plot.id) },
                        label = { Text(plot.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            PlotMapTile(
                plot = selectedPlot,
                insights = selectedInsights,
                modifier = Modifier.fillMaxWidth(),
                large = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MapLegendChip("叶菜", Color(0xFF4F9A59))
                MapLegendChip("瓜豆", Color(0xFF2B8C7E))
                MapLegendChip("茄果", Color(0xFFC85C3E))
                MapLegendChip("根菜", Color(0xFFB47A39))
            }
            Text(
                "${selectedPlot.name} · ${selectedPlot.growingStyle.label} · ${selectedPlot.soilType} · ${selectedPlot.sizeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlotMapTile(
    plot: Plot,
    insights: List<PlantingInsight>,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    Surface(
        modifier = modifier.aspectRatio(if (large) 0.9f else 1.16f),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlotBedCanvas(plot, insights)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(
                                plot.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                plot.growingStyle.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        color = Color(0xFF2F6F4E).copy(alpha = 0.88f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "${insights.size} 批",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (insights.isEmpty()) {
                            Text(
                                "空地，可安排下一批种植",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            insights.take(2).forEach { insight ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(8.dp),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = cropVisualColor(insight.crop.category, insight.crop.name)
                                    ) {}
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${insight.crop.name} · ${insight.progressPercent}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (insights.size > 2) {
                                Text(
                                    "另有 ${insights.size - 2} 批",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlotBedCanvas(plot: Plot, insights: List<PlantingInsight>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val bedRadius = CornerRadius(26f, 26f)
        val edgeColor = when (plot.growingStyle) {
            GrowingStyle.OPEN_FIELD -> Color(0xFF9A6A43)
            GrowingStyle.POT -> Color(0xFF6F7D88)
            GrowingStyle.GREENHOUSE -> Color(0xFF6F8FA8)
            GrowingStyle.BALCONY -> Color(0xFF8A8174)
        }
        val soilColor = when (plot.growingStyle) {
            GrowingStyle.POT, GrowingStyle.BALCONY -> Color(0xFFB98151)
            else -> Color(0xFFC58D5F)
        }

        drawRoundRect(
            color = edgeColor,
            size = Size(size.width, size.height),
            cornerRadius = bedRadius
        )
        drawRoundRect(
            color = soilColor,
            topLeft = Offset(9f, 9f),
            size = Size(size.width - 18f, size.height - 18f),
            cornerRadius = CornerRadius(20f, 20f)
        )

        val furrowCount = 4
        repeat(furrowCount) { index ->
            val y = 24f + (size.height - 48f) * (index + 1) / (furrowCount + 1)
            drawLine(
                color = Color(0xFF7C5134).copy(alpha = 0.36f),
                start = Offset(20f, y),
                end = Offset(size.width - 20f, y),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(24f, y - 8f),
                end = Offset(size.width - 24f, y - 8f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }

        if (plot.growingStyle == GrowingStyle.GREENHOUSE) {
            drawRoundRect(
                color = Color(0xFFDBEEF8).copy(alpha = 0.34f),
                topLeft = Offset(15f, 15f),
                size = Size(size.width - 30f, size.height - 30f),
                cornerRadius = CornerRadius(22f, 22f),
                style = Stroke(width = 5f)
            )
        }

        if (insights.isEmpty()) {
            repeat(3) { row ->
                repeat(5) { column ->
                    drawCircle(
                        color = Color(0xFF845A3C).copy(alpha = 0.34f),
                        radius = 3.5f,
                        center = Offset(
                            x = 34f + column * ((size.width - 68f) / 4f),
                            y = 50f + row * ((size.height - 100f) / 2f)
                        )
                    )
                }
            }
            return@Canvas
        }

        val visible = insights.take(3)
        val stripWidth = (size.width - 34f) / visible.size
        visible.forEachIndexed { index, insight ->
            val left = 17f + stripWidth * index
            drawCropStrip(
                left = left,
                top = 30f,
                width = stripWidth - 8f,
                height = size.height - 64f,
                category = insight.crop.category,
                cropName = insight.crop.name,
                progress = insight.progressPercent / 100f
            )
        }
    }
}

private fun DrawScope.drawCropStrip(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    category: String,
    cropName: String,
    progress: Float
) {
    val main = cropVisualColor(category, cropName)
    val light = cropVisualLightColor(category, cropName)
    val clampedProgress = progress.coerceIn(0.12f, 1f)
    val isVine = category.contains("瓜") || category.contains("豆")
    val isFruit = category.contains("茄果") || cropName == "番茄" || cropName == "辣椒" || cropName == "茄子"
    val isRoot = category.contains("根菜")
    val isAlliumOrHerb = category.contains("葱") || category.contains("香辛")
    val foliage = when {
        isFruit -> Color(0xFF4D8A45)
        isRoot -> Color(0xFF5C9E48)
        else -> main
    }
    val foliageLight = when {
        isFruit || isRoot -> Color(0xFFCDEB9C)
        else -> light
    }
    val rowCount = when {
        isVine -> 3
        isFruit -> 3
        else -> 4
    }
    val plantCount = when {
        category.contains("叶菜") -> 5
        isRoot -> 5
        isVine -> 4
        else -> 4
    }
    val scale = ((width / (plantCount + 1)) * 0.42f + 10f * clampedProgress).coerceIn(12f, 42f)

    if (isVine) {
        repeat(rowCount) { row ->
            val x = left + width * (row + 1) / (rowCount + 1)
            drawLine(
                color = Color(0xFF574936).copy(alpha = 0.54f),
                start = Offset(x, top + 4f),
                end = Offset(x, top + height - 4f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.16f),
                start = Offset(x - 5f, top + 12f),
                end = Offset(x - 5f, top + height - 12f),
                strokeWidth = 1.6f,
                cap = StrokeCap.Round
            )
            repeat(plantCount) { col ->
                val y = top + height * (col + 1) / (plantCount + 1)
                drawVinePlant(Offset(x, y), scale, main, light, col)
            }
        }
    } else if (isFruit) {
        repeat(rowCount) { row ->
            val y = top + height * (row + 1) / (rowCount + 1)
            drawLine(
                color = Color(0xFF6D452C).copy(alpha = 0.2f),
                start = Offset(left + 6f, y + scale * 0.8f),
                end = Offset(left + width - 6f, y + scale * 0.8f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            repeat(plantCount) { col ->
                val x = left + width * (col + 1) / (plantCount + 1)
                drawFruitPlant(Offset(x, y), scale, foliage, foliageLight, cropName, col)
            }
        }
    } else if (isRoot) {
        repeat(rowCount) { row ->
            val y = top + height * (row + 1) / (rowCount + 1)
            drawLine(
                color = Color(0xFF6D452C).copy(alpha = 0.24f),
                start = Offset(left + 8f, y + scale * 0.34f),
                end = Offset(left + width - 8f, y + scale * 0.34f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            repeat(plantCount) { col ->
                val x = left + width * (col + 1) / (plantCount + 1)
                drawRootPlant(Offset(x, y), scale, foliage, foliageLight)
            }
        }
    } else if (isAlliumOrHerb) {
        repeat(rowCount) { row ->
            val y = top + height * (row + 1) / (rowCount + 1)
            repeat(plantCount) { col ->
                val x = left + width * (col + 1) / (plantCount + 1)
                drawHerbCluster(Offset(x, y), scale, main, light, col)
            }
        }
    } else {
        repeat(rowCount) { row ->
            val y = top + height * (row + 1) / (rowCount + 1)
            drawLine(
                color = Color(0xFF6D452C).copy(alpha = 0.22f),
                start = Offset(left + 4f, y),
                end = Offset(left + width - 4f, y),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            repeat(plantCount) { col ->
                val x = left + width * (col + 1) / (plantCount + 1)
                drawLeafRosette(Offset(x, y), scale, main, light, col)
            }
        }
    }
}

private fun DrawScope.drawLeaf(
    center: Offset,
    length: Float,
    width: Float,
    angle: Float,
    color: Color,
    highlight: Color
) {
    rotate(angle, pivot = center) {
        drawOval(
            color = color,
            topLeft = Offset(center.x - width / 2f, center.y - length / 2f),
            size = Size(width, length)
        )
        drawOval(
            color = highlight.copy(alpha = 0.44f),
            topLeft = Offset(center.x - width * 0.16f, center.y - length * 0.36f),
            size = Size(width * 0.32f, length * 0.54f)
        )
    }
}

private fun DrawScope.drawLeafRosette(
    center: Offset,
    scale: Float,
    color: Color,
    highlight: Color,
    seed: Int
) {
    drawCircle(Color(0xFF593B27).copy(alpha = 0.16f), scale * 0.72f, Offset(center.x + scale * 0.12f, center.y + scale * 0.38f))
    val swing = if (seed % 2 == 0) 8f else -8f
    val angles = listOf(-66f + swing, -34f, -8f, 18f, 48f - swing, 76f)
    angles.forEachIndexed { index, angle ->
        val leafLength = scale * (1.45f - index * 0.045f)
        val leafWidth = scale * (0.58f + (index % 2) * 0.08f)
        val offset = Offset(
            center.x + (index - 2.5f) * scale * 0.08f,
            center.y - scale * 0.04f
        )
        drawLeaf(offset, leafLength, leafWidth, angle, color, highlight)
    }
    drawCircle(highlight.copy(alpha = 0.82f), scale * 0.18f, Offset(center.x - scale * 0.12f, center.y - scale * 0.18f))
}

private fun DrawScope.drawFruitPlant(
    center: Offset,
    scale: Float,
    leafColor: Color,
    highlight: Color,
    cropName: String,
    seed: Int
) {
    val stemTop = Offset(center.x, center.y - scale * 0.98f)
    val stemBottom = Offset(center.x, center.y + scale * 0.78f)
    drawLine(Color(0xFF406D31), stemTop, stemBottom, strokeWidth = scale * 0.14f, cap = StrokeCap.Round)
    drawLine(Color(0xFF203D1E).copy(alpha = 0.26f), Offset(center.x + scale * 0.12f, stemTop.y), Offset(center.x + scale * 0.12f, stemBottom.y), strokeWidth = scale * 0.06f, cap = StrokeCap.Round)

    val leafAngles = listOf(-62f, -28f, 32f, 64f)
    leafAngles.forEachIndexed { index, angle ->
        val side = if (index % 2 == 0) -1f else 1f
        val leafCenter = Offset(center.x + side * scale * 0.45f, center.y - scale * 0.5f + index * scale * 0.34f)
        drawLine(leafColor.copy(alpha = 0.65f), center, leafCenter, strokeWidth = scale * 0.08f, cap = StrokeCap.Round)
        drawLeaf(leafCenter, scale * 0.9f, scale * 0.36f, angle, leafColor, highlight)
    }

    val fruitColor = when (cropName) {
        "辣椒" -> Color(0xFFD63E34)
        "茄子" -> Color(0xFF6F4AA1)
        else -> Color(0xFFE2573F)
    }
    val fruitCenters = listOf(
        Offset(center.x - scale * 0.32f, center.y + scale * 0.1f),
        Offset(center.x + scale * 0.36f, center.y + scale * 0.34f),
        Offset(center.x + if (seed % 2 == 0) scale * 0.12f else -scale * 0.1f, center.y - scale * 0.28f)
    )
    fruitCenters.take(if (scale > 13f) 3 else 2).forEachIndexed { index, fruitCenter ->
        if (cropName == "辣椒") {
            rotate(if (index % 2 == 0) -18f else 18f, pivot = fruitCenter) {
                drawRoundRect(
                    color = fruitColor,
                    topLeft = Offset(fruitCenter.x - scale * 0.16f, fruitCenter.y - scale * 0.42f),
                    size = Size(scale * 0.32f, scale * 0.72f),
                    cornerRadius = CornerRadius(scale * 0.16f, scale * 0.16f)
                )
            }
        } else {
            drawCircle(fruitColor, scale * 0.24f, fruitCenter)
        }
        drawCircle(Color.White.copy(alpha = 0.42f), scale * 0.07f, Offset(fruitCenter.x - scale * 0.08f, fruitCenter.y - scale * 0.08f))
    }
}

private fun DrawScope.drawVinePlant(
    center: Offset,
    scale: Float,
    color: Color,
    highlight: Color,
    seed: Int
) {
    val direction = if (seed % 2 == 0) 1f else -1f
    val vineEnd = Offset(center.x + direction * scale * 1.05f, center.y - scale * 0.74f)
    drawLine(color.copy(alpha = 0.72f), center, vineEnd, strokeWidth = scale * 0.12f, cap = StrokeCap.Round)
    drawLine(color.copy(alpha = 0.44f), center, Offset(center.x - direction * scale * 0.7f, center.y + scale * 0.42f), strokeWidth = scale * 0.08f, cap = StrokeCap.Round)
    drawLeaf(Offset(center.x + direction * scale * 0.42f, center.y - scale * 0.26f), scale * 0.96f, scale * 0.52f, 36f * direction, color, highlight)
    drawLeaf(Offset(center.x - direction * scale * 0.32f, center.y + scale * 0.18f), scale * 0.84f, scale * 0.46f, -48f * direction, color, highlight)
    drawCircle(highlight.copy(alpha = 0.5f), scale * 0.16f, Offset(center.x + direction * scale * 0.14f, center.y - scale * 0.05f))
    rotate(22f * direction, pivot = Offset(center.x + direction * scale * 0.7f, center.y + scale * 0.2f)) {
        drawRoundRect(
            color = Color(0xFF72A83B),
            topLeft = Offset(center.x + direction * scale * 0.52f - scale * 0.14f, center.y + scale * 0.02f - scale * 0.42f),
            size = Size(scale * 0.28f, scale * 0.82f),
            cornerRadius = CornerRadius(scale * 0.2f, scale * 0.2f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.28f),
            topLeft = Offset(center.x + direction * scale * 0.52f - scale * 0.06f, center.y + scale * 0.02f - scale * 0.34f),
            size = Size(scale * 0.06f, scale * 0.52f),
            cornerRadius = CornerRadius(scale * 0.05f, scale * 0.05f)
        )
    }
}

private fun DrawScope.drawRootPlant(
    center: Offset,
    scale: Float,
    color: Color,
    highlight: Color
) {
    drawOval(
        color = Color(0xFFE8A045),
        topLeft = Offset(center.x - scale * 0.28f, center.y - scale * 0.05f),
        size = Size(scale * 0.56f, scale * 0.82f)
    )
    drawOval(
        color = Color.White.copy(alpha = 0.22f),
        topLeft = Offset(center.x - scale * 0.12f, center.y + scale * 0.02f),
        size = Size(scale * 0.16f, scale * 0.42f)
    )
    drawLeaf(Offset(center.x - scale * 0.28f, center.y - scale * 0.38f), scale * 0.9f, scale * 0.28f, -36f, color, highlight)
    drawLeaf(Offset(center.x, center.y - scale * 0.48f), scale * 1.0f, scale * 0.3f, 0f, color, highlight)
    drawLeaf(Offset(center.x + scale * 0.3f, center.y - scale * 0.34f), scale * 0.88f, scale * 0.28f, 38f, color, highlight)
}

private fun DrawScope.drawHerbCluster(
    center: Offset,
    scale: Float,
    color: Color,
    highlight: Color,
    seed: Int
) {
    val blades = 6
    repeat(blades) { index ->
        val spread = (index - (blades - 1) / 2f) * scale * 0.16f
        val top = Offset(center.x + spread, center.y - scale * (0.8f + (index % 3) * 0.14f))
        drawLine(
            color = if (index % 2 == 0) color else highlight,
            start = Offset(center.x, center.y + scale * 0.42f),
            end = top,
            strokeWidth = scale * 0.09f,
            cap = StrokeCap.Round
        )
    }
    drawCircle(Color(0xFF2E5E37), scale * 0.16f, Offset(center.x, center.y + scale * 0.38f + seed * 0.02f))
}

@Composable
private fun MapLegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(9.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = color
        ) {}
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun cropVisualColor(category: String, cropName: String): Color {
    return when {
        category.contains("叶菜") || category.contains("多年生") -> Color(0xFF4F9A59)
        category.contains("瓜") || category.contains("豆") -> Color(0xFF2B8C7E)
        category.contains("茄果") || cropName == "番茄" || cropName == "辣椒" -> Color(0xFFC85C3E)
        category.contains("根菜") -> Color(0xFFB47A39)
        category.contains("葱") || category.contains("香辛") -> Color(0xFF6B8F3F)
        else -> Color(0xFF4F8F62)
    }
}

private fun cropVisualLightColor(category: String, cropName: String): Color {
    return when {
        category.contains("茄果") || cropName == "番茄" || cropName == "辣椒" -> Color(0xFFFFC07A)
        category.contains("根菜") -> Color(0xFFFFD98A)
        else -> Color(0xFFC9E8A2)
    }
}

@Composable
private fun PlotCard(plot: Plot, state: GardenDataState) {
    val batches = state.batches.filter { it.plotId == plot.id }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(plot.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${plot.growingStyle.label} · ${plot.soilType} · ${plot.sizeLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = {}, label = { Text("${batches.size} 批") })
            }
            if (batches.isEmpty()) {
                Text("空地", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    batches.take(3).forEach { batch ->
                        val crop = CropLibrary.byId(batch.cropId)
                        AssistChip(onClick = {}, label = { Text(crop.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlantingInsightCard(
    insight: PlantingInsight,
    compact: Boolean,
    onFinish: (() -> Unit)?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${insight.crop.name}${insight.batch.variety.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${insight.plot?.name ?: "未分配地块"} · 第 ${insight.rawDay} 天 · ${insight.stage.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(onClick = {}, label = { Text(insight.batch.quantityLabel) })
            }
            LinearProgressIndicator(
                progress = { insight.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(insight.nextFocus, style = MaterialTheme.typography.bodyMedium)
            if (!compact) {
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    InfoColumn("预计采摘", insight.harvestWindow)
                    InfoColumn("种植方式", insight.batch.method.label)
                    InfoColumn("进度", "${insight.progressPercent}%")
                }
                if (onFinish != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onFinish) { Text("结束批次") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(record: OperationRecord, state: GardenDataState) {
    val batch = record.batchId?.let { id -> state.batches.firstOrNull { it.id == id } }
    val crop = batch?.let { CropLibrary.byId(it.cropId) }
    val plot = record.plotId?.let { id -> state.plots.firstOrNull { it.id == id } }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(record.type.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(record.timestamp.recordDateLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                listOfNotNull(plot?.name, crop?.name, batch?.variety?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "全局记录" },
                style = MaterialTheme.typography.bodyMedium
            )
            if (record.amountLabel.isNotBlank()) {
                Text(record.amountLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (record.note.isNotBlank()) {
                Text(record.note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun GardenSettingsDialog(
    garden: Garden?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(garden?.name ?: "我的菜园") }
    var location by rememberSaveable { mutableStateOf(garden?.locationName ?: "北京") }
    var latitude by rememberSaveable { mutableStateOf(garden?.latitude?.toString() ?: "39.9042") }
    var longitude by rememberSaveable { mutableStateOf(garden?.longitude?.toString() ?: "116.4074") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑菜园") },
        text = {
            DialogColumn {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text("纬度") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("经度") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(name, location, latitude, longitude) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddPlotDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, GrowingStyle, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf("") }
    var soil by rememberSaveable { mutableStateOf("壤土") }
    var style by remember { mutableStateOf(GrowingStyle.OPEN_FIELD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加地块") },
        text = {
            DialogColumn {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("地块名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = size, onValueChange = { size = it }, label = { Text("尺寸/面积") }, modifier = Modifier.fillMaxWidth())
                SimpleDropdown(
                    label = "种植方式",
                    selectedLabel = style.label,
                    options = GrowingStyle.values().toList(),
                    optionLabel = { it.label },
                    onSelect = { style = it }
                )
                OutlinedTextField(value = soil, onValueChange = { soil = it }, label = { Text("土壤") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(name, size, style, soil) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddBatchDialog(
    plots: List<Plot>,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, PlantingMethod, String, String) -> Unit
) {
    var plot by remember { mutableStateOf(plots.firstOrNull()) }
    var crop by remember { mutableStateOf(CropLibrary.crops.first()) }
    var variety by rememberSaveable { mutableStateOf("") }
    var method by remember { mutableStateOf(PlantingMethod.SEED) }
    var startDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var quantity by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加种植") },
        text = {
            DialogColumn {
                if (plots.isEmpty()) {
                    Text("还没有地块", color = MaterialTheme.colorScheme.error)
                } else {
                    SimpleDropdown(
                        label = "地块",
                        selectedLabel = plot?.name ?: "选择地块",
                        options = plots,
                        optionLabel = { it.name },
                        onSelect = { plot = it }
                    )
                    SimpleDropdown(
                        label = "作物",
                        selectedLabel = crop.name,
                        options = CropLibrary.crops,
                        optionLabel = { "${it.name} · ${it.category}" },
                        onSelect = { crop = it }
                    )
                    OutlinedTextField(value = variety, onValueChange = { variety = it }, label = { Text("品种") }, modifier = Modifier.fillMaxWidth())
                    SimpleDropdown(
                        label = "方式",
                        selectedLabel = method.label,
                        options = PlantingMethod.values().toList(),
                        optionLabel = { it.label },
                        onSelect = { method = it }
                    )
                    OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("日期 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("数量/面积") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(plot?.id, crop.id, variety, method, startDate, quantity) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddOperationDialog(
    state: GardenDataState,
    onDismiss: () -> Unit,
    onSave: (String?, OperationType, String, String) -> Unit
) {
    val options = remember(state.batches, state.plots) {
        listOf(BatchOption(null, "不关联批次")) + state.batches.map { batch ->
            val crop = CropLibrary.byId(batch.cropId)
            val plot = state.plots.firstOrNull { it.id == batch.plotId }
            BatchOption(batch.id, "${plot?.name ?: "未分配"} · ${crop.name}")
        }
    }
    var batchOption by remember { mutableStateOf(options.first()) }
    var type by remember { mutableStateOf(OperationType.WATER) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录操作") },
        text = {
            DialogColumn {
                SimpleDropdown(
                    label = "关联批次",
                    selectedLabel = batchOption.label,
                    options = options,
                    optionLabel = { it.label },
                    onSelect = { batchOption = it }
                )
                SimpleDropdown(
                    label = "类型",
                    selectedLabel = type.label,
                    options = OperationType.values().toList(),
                    optionLabel = { it.label },
                    onSelect = { type = it }
                )
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("用量/产量") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onSave(batchOption.id, type, amount, note) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DialogColumn(content: @Composable ColumnScopeWorkaround.() -> Unit) {
    val scope = remember { ColumnScopeWorkaround }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        scope.content()
    }
}

private object ColumnScopeWorkaround

@Composable
private fun <T> SimpleDropdown(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedLabel, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("v")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
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

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyBlock(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PriorityPill(priority: TaskPriority) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = priority.pillColor()
    ) {
        Text(
            priority.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun InfoColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    Text(date.relativeLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun TaskPriority.containerColor(): Color {
    return when (this) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
        TaskPriority.LOW -> MaterialTheme.colorScheme.surface
    }
}

@Composable
private fun TaskPriority.pillColor(): Color {
    return when (this) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.error
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        TaskPriority.LOW -> MaterialTheme.colorScheme.secondary
    }
}

private fun Double.oneDecimal(): String = String.format("%.1f", this)

private fun Double.formatCoord(): String = String.format("%.4f", this)

private fun LocalDate.shortDate(): String = "${monthValue}/${dayOfMonth}"

private fun LocalDate.relativeLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.plusDays(2) -> "后天"
        else -> "${monthValue}/${dayOfMonth}"
    }
}

private fun String.recordDateLabel(): String {
    val parsed = runCatching { LocalDateTime.parse(this) }.getOrNull()
    return if (parsed == null) {
        this
    } else {
        "${parsed.monthValue}/${parsed.dayOfMonth} ${parsed.hour.toString().padStart(2, '0')}:${parsed.minute.toString().padStart(2, '0')}"
    }
}
