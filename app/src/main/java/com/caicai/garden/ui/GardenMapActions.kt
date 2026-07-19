package com.caicai.garden.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caicai.garden.data.CropLibrary
import com.caicai.garden.data.CropProfile
import com.caicai.garden.data.PlantingMethod
import com.caicai.garden.domain.GardenAdvisor
import com.caicai.garden.domain.PlantingInsight
import java.time.LocalDate

internal enum class GardenMapSheet {
    PLANT,
    FERTILIZE,
    HARVEST
}

internal data class MapPlantingDraft(
    val cropId: String,
    val method: PlantingMethod,
    val date: String,
    val quantity: String
)

internal data class MapFertilizerDraft(
    val amountLabel: String,
    val note: String
)

internal data class MapHarvestDraft(
    val amountLabel: String,
    val quality: String,
    val finishAndClear: Boolean
)

private val MapPaper = androidx.compose.ui.graphics.Color(0xFFFFF7E9)
private val MapInk = androidx.compose.ui.graphics.Color(0xFF203025)
private val MapMuted = androidx.compose.ui.graphics.Color(0xFF6A7768)
private val MapLeaf = androidx.compose.ui.graphics.Color(0xFF4F8F45)
private val MapLeafDeep = androidx.compose.ui.graphics.Color(0xFF2F6F4E)
private val MapSelectedMist = androidx.compose.ui.graphics.Color(0xFFDDEFD2)
private val MapWater = androidx.compose.ui.graphics.Color(0xFF2D7FA7)
private val MapFertilizer = androidx.compose.ui.graphics.Color(0xFFD7A252)
private val MapHarvest = androidx.compose.ui.graphics.Color(0xFFE69A3A)

internal fun gardenCellLabel(cell: Pair<Int, Int>): String {
    val columnLabel = ('A'.code + cell.second.coerceIn(0, 25)).toChar()
    return "$columnLabel${cell.first + 1}"
}

@Composable
internal fun GardenMapActionDock(
    selectedCell: Pair<Int, Int>,
    insight: PlantingInsight?,
    lastWaterLabel: String,
    onPlant: () -> Unit,
    onWater: () -> Unit,
    onFertilize: () -> Unit,
    onHarvest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MapPaper.copy(alpha = 0.97f),
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (insight == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${gardenCellLabel(selectedCell)} 空闲菜格",
                            color = MapInk,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                        Text(
                            "空闲可种植 · 继续选择作物和方式",
                            color = MapMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onPlant,
                        modifier = Modifier
                            .width(132.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MapLeafDeep)
                    ) {
                        Text("种植", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${insight.crop.name}${insight.batch.variety.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                            color = MapInk,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (lastWaterLabel == "今天已浇水") {
                                "今日已浇水 · 土壤湿度良好"
                            } else {
                                "${insight.stage.name} · 生长进度 ${insight.progressPercent}%"
                            },
                            color = MapMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MapSelectedMist
                    ) {
                        Text(
                            "${insight.progressPercent}%",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            color = MapLeafDeep,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFFE3DDC9))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MapActionIconButton(
                        label = if (lastWaterLabel == "今天已浇水") "已浇水" else "浇水",
                        icon = MapActionIcon.WATER,
                        color = MapWater,
                        enabled = lastWaterLabel != "今天已浇水",
                        onClick = onWater
                    )
                    MapActionIconButton(
                        label = if (insight.lastFertilizeDate == LocalDate.now()) "已施肥" else "施肥",
                        icon = MapActionIcon.FERTILIZE,
                        color = MapFertilizer,
                        enabled = insight.lastFertilizeDate != LocalDate.now(),
                        onClick = onFertilize
                    )
                    MapActionIconButton(
                        label = "采摘",
                        icon = MapActionIcon.HARVEST,
                        color = MapHarvest,
                        onClick = onHarvest
                    )
                }
            }
        }
    }
}

private enum class MapActionIcon {
    WATER,
    FERTILIZE,
    HARVEST
}

@Composable
private fun MapActionIconButton(
    label: String,
    icon: MapActionIcon,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = color.copy(alpha = if (enabled) 1f else 0.38f)
        ) {
            Canvas(modifier = Modifier.padding(9.dp)) {
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                when (icon) {
                    MapActionIcon.WATER -> {
                        val droplet = Path().apply {
                            moveTo(size.width * 0.50f, size.height * 0.05f)
                            cubicTo(
                                size.width * 0.34f,
                                size.height * 0.30f,
                                size.width * 0.20f,
                                size.height * 0.48f,
                                size.width * 0.20f,
                                size.height * 0.65f
                            )
                            cubicTo(
                                size.width * 0.20f,
                                size.height * 0.92f,
                                size.width * 0.80f,
                                size.height * 0.92f,
                                size.width * 0.80f,
                                size.height * 0.65f
                            )
                            cubicTo(
                                size.width * 0.80f,
                                size.height * 0.48f,
                                size.width * 0.66f,
                                size.height * 0.30f,
                                size.width * 0.50f,
                                size.height * 0.05f
                            )
                            close()
                        }
                        drawPath(droplet, androidx.compose.ui.graphics.Color.White, style = stroke)
                    }

                    MapActionIcon.FERTILIZE -> {
                        drawRoundRect(
                            color = androidx.compose.ui.graphics.Color.White,
                            topLeft = Offset(size.width * 0.20f, size.height * 0.32f),
                            size = Size(size.width * 0.60f, size.height * 0.56f),
                            style = stroke
                        )
                        drawLine(
                            color = androidx.compose.ui.graphics.Color.White,
                            start = Offset(size.width * 0.28f, size.height * 0.28f),
                            end = Offset(size.width * 0.72f, size.height * 0.28f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = androidx.compose.ui.graphics.Color.White,
                            radius = size.minDimension * 0.08f,
                            center = center
                        )
                    }

                    MapActionIcon.HARVEST -> {
                        drawRoundRect(
                            color = androidx.compose.ui.graphics.Color.White,
                            topLeft = Offset(size.width * 0.16f, size.height * 0.42f),
                            size = Size(size.width * 0.68f, size.height * 0.44f),
                            style = stroke
                        )
                        val handle = Path().apply {
                            moveTo(size.width * 0.32f, size.height * 0.44f)
                            quadraticTo(
                                size.width * 0.50f,
                                size.height * 0.05f,
                                size.width * 0.68f,
                                size.height * 0.44f
                            )
                        }
                        drawPath(handle, androidx.compose.ui.graphics.Color.White, style = stroke)
                    }
                }
            }
        }
        Text(
            label,
            color = if (enabled) MapInk else MapMuted,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp
        )
    }
}

@Composable
internal fun GardenMapZoomRail(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(44.dp)
            .height(88.dp),
        shape = RoundedCornerShape(17.dp),
        color = MapPaper.copy(alpha = 0.94f),
        shadowElevation = 7.dp,
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(onClick = onZoomIn),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+",
                    color = MapLeafDeep,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(1.dp)
                    .background(MapMuted.copy(alpha = 0.18f))
                    .align(Alignment.CenterHorizontally)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(onClick = onZoomOut),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "−",
                    color = MapLeafDeep,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
internal fun GardenMapResetLayout(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = MapPaper.copy(alpha = 0.95f),
        shadowElevation = 7.dp,
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("↺", color = MapLeafDeep, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("重置布局", color = MapLeafDeep, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun GardenWaterFeedback(
    cropName: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MapPaper.copy(alpha = 0.97f),
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.80f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = MapWater) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✓", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black)
                }
            }
            Column {
                Text("浇水完成", color = MapInk, fontWeight = FontWeight.Black)
                Text("$cropName · $detail", color = MapMuted, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GardenMapPlantSheet(
    cell: Pair<Int, Int>,
    onDismiss: () -> Unit,
    onConfirm: (MapPlantingDraft) -> Unit
) {
    val crops = remember { CropLibrary.crops }
    var selectedCropId by remember { mutableStateOf(crops.firstOrNull()?.id.orEmpty()) }
    var selectedMethod by remember {
        mutableStateOf(crops.first().supportedPlantingMethods.first())
    }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf(1) }
    var selectedCategory by remember { mutableStateOf("全部") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedCrop = crops.firstOrNull { it.id == selectedCropId } ?: crops.first()
    val categories = remember(crops) {
        listOf("全部") + crops.map(::cropFilterGroup).distinct()
    }
    val visibleCrops = remember(crops, selectedCategory) {
        if (selectedCategory == "全部") crops else crops.filter { cropFilterGroup(it) == selectedCategory }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MapPaper,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { MapSheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "种植到 ${gardenCellLabel(cell)}",
                    modifier = Modifier.weight(1f),
                    color = MapInk,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Surface(shape = RoundedCornerShape(14.dp), color = MapSelectedMist) {
                    Text(
                        "空闲菜格",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MapLeafDeep,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "选择作物",
                    modifier = Modifier.weight(1f),
                    color = MapMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Text("${crops.size} 种", color = MapLeafDeep, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(categories, key = { it }) { category ->
                    MapChoicePill(
                        label = category,
                        selected = selectedCategory == category,
                        modifier = Modifier.widthIn(min = 58.dp),
                        onClick = {
                            selectedCategory = category
                            val firstVisible = crops.firstOrNull {
                                category == "全部" || cropFilterGroup(it) == category
                            }
                            if (firstVisible != null && cropFilterGroup(selectedCrop) != category && category != "全部") {
                                selectedCropId = firstVisible.id
                                selectedMethod = firstVisible.supportedPlantingMethods.first()
                                quantity = quantity.coerceAtMost(selectedMethod.maxQuantity)
                            }
                        }
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(visibleCrops, key = { it.id }) { crop ->
                    CropChoiceCard(
                        crop = crop,
                        selected = crop.id == selectedCropId,
                        onClick = {
                            selectedCropId = crop.id
                            if (selectedMethod !in crop.supportedPlantingMethods) {
                                selectedMethod = crop.supportedPlantingMethods.first()
                                quantity = quantity.coerceAtMost(selectedMethod.maxQuantity)
                            }
                        }
                    )
                }
            }
            Text("种植方式", color = MapMuted, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                selectedCrop.supportedPlantingMethods.forEach { method ->
                    MapChoicePill(
                        label = method.materialLabel,
                        selected = selectedMethod == method,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedMethod = method
                            quantity = quantity.coerceAtMost(method.maxQuantity)
                        }
                    )
                }
            }
            MapValueRow(
                label = selectedMethod.dateLabel,
                value = plantingDateDisplay(selectedDate),
                onClick = { showDatePicker = true }
            )
            MapStepperRow(
                label = "种植数量",
                value = "$quantity ${selectedMethod.quantityUnit}",
                onDecrease = { if (quantity > 1) quantity -= 1 },
                onIncrease = {
                    if (quantity < selectedMethod.maxQuantity) quantity += 1
                }
            )
            MapPrimaryButton(
                label = "确认种植${selectedCrop.name}",
                color = MapLeafDeep,
                enabled = selectedCropId.isNotBlank(),
                onClick = {
                    onConfirm(
                        MapPlantingDraft(
                            cropId = selectedCropId,
                            method = selectedMethod,
                            date = selectedDate.toString(),
                            quantity = "$quantity ${selectedMethod.quantityUnit}"
                        )
                    )
                }
            )
        }
    }

    if (showDatePicker) {
        val latestSelectableDateMillis = remember {
            LocalDate.now().toEpochDay() * MILLIS_PER_DAY
        }
        val selectableDates = remember(latestSelectableDateMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= latestSelectableDateMillis
                }

                override fun isSelectableYear(year: Int): Boolean {
                    return year <= LocalDate.now().year
                }
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toEpochDay() * MILLIS_PER_DAY,
            selectableDates = selectableDates
        )
        val datePickerColors = DatePickerDefaults.colors(
            containerColor = MapPaper,
            titleContentColor = MapMuted,
            headlineContentColor = MapInk,
            weekdayContentColor = MapMuted,
            subheadContentColor = MapInk,
            navigationContentColor = MapLeafDeep,
            currentYearContentColor = MapLeafDeep,
            selectedYearContentColor = androidx.compose.ui.graphics.Color.White,
            selectedYearContainerColor = MapLeafDeep,
            dayContentColor = MapInk,
            selectedDayContentColor = androidx.compose.ui.graphics.Color.White,
            selectedDayContainerColor = MapLeafDeep,
            todayContentColor = MapLeafDeep,
            todayDateBorderColor = MapLeafDeep,
            dividerColor = androidx.compose.ui.graphics.Color(0xFFE0D9C7)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = datePickerColors,
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            selectedDate = LocalDate.ofEpochDay(
                                Math.floorDiv(selectedMillis, MILLIS_PER_DAY)
                            )
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定", color = MapLeafDeep, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消", color = MapMuted, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        "选择日期",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                        fontWeight = FontWeight.Bold
                    )
                },
                showModeToggle = false,
                colors = datePickerColors
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GardenMapFertilizerSheet(
    insight: PlantingInsight,
    onDismiss: () -> Unit,
    onConfirm: (MapFertilizerDraft) -> Unit
) {
    val recommendation = remember(insight) {
        GardenAdvisor.fertilizerRecommendation(insight)
    }
    var selectedFertilizer by remember(insight.batch.id) {
        mutableStateOf(recommendation.choices.first())
    }
    var amount by remember(insight.batch.id) {
        mutableStateOf(recommendation.defaultAmountGrams)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MapPaper,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { MapSheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("给${insight.crop.name}施肥", color = MapInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("选择肥料", color = MapMuted, fontSize = 13.sp, fontWeight = FontWeight.Black)
            recommendation.choices.forEach { choice ->
                MapListChoice(
                    label = choice,
                    selected = selectedFertilizer == choice,
                    onClick = { selectedFertilizer = choice }
                )
            }
            Text("用量", color = MapMuted, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                recommendation.amountOptionsGrams.forEach { option ->
                    MapChoicePill(
                        label = "$option g",
                        selected = amount == option,
                        modifier = Modifier.weight(1f),
                        onClick = { amount = option }
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = androidx.compose.ui.graphics.Color(0xFFF3F0DF)
            ) {
                Text(
                    "${recommendation.hint} · 请以肥料包装说明为准",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    color = MapMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            MapPrimaryButton(
                label = "确认施肥 $amount g",
                color = MapLeafDeep,
                enabled = amount > 0,
                onClick = {
                    onConfirm(
                        MapFertilizerDraft(
                            amountLabel = "$amount g",
                            note = "$selectedFertilizer · 地图快捷操作"
                        )
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GardenMapHarvestSheet(
    insight: PlantingInsight,
    onDismiss: () -> Unit,
    onConfirm: (MapHarvestDraft) -> Unit
) {
    val measureProfile = remember(insight.crop.id) {
        GardenAdvisor.harvestMeasureProfile(insight.crop)
    }
    var amount by remember(insight.batch.id) {
        mutableStateOf(measureProfile.defaultAmount)
    }
    var quality by remember { mutableStateOf("良好") }
    var finishAndClear by remember(insight.batch.id) {
        mutableStateOf(!insight.crop.continuousHarvest)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MapPaper,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { MapSheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("记录本次采摘", color = MapInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(
                "${insight.crop.name}${insight.batch.variety.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                color = MapMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                GardenAdvisor.harvestReadinessHint(insight),
                color = MapMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            MapStepperRow(
                label = if (measureProfile.unit == "根") "采摘数量" else "采摘重量",
                value = measureProfile.amountLabel(amount),
                onDecrease = {
                    amount = (amount - measureProfile.step)
                        .coerceAtLeast(measureProfile.minAmount)
                },
                onIncrease = {
                    amount = (amount + measureProfile.step)
                        .coerceAtMost(measureProfile.maxAmount)
                }
            )
            Text("品质", color = MapMuted, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("良好", "一般", "有损").forEach { option ->
                    MapChoicePill(
                        label = option,
                        selected = quality == option,
                        modifier = Modifier.weight(1f),
                        onClick = { quality = option }
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { finishAndClear = !finishAndClear },
                shape = RoundedCornerShape(18.dp),
                color = if (finishAndClear) MapSelectedMist else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.54f),
                border = BorderStroke(1.dp, if (finishAndClear) MapLeaf else androidx.compose.ui.graphics.Color(0xFFD7D0BC))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("采摘后结束并清空菜地", color = MapInk, fontWeight = FontWeight.Black)
                        Text(
                            if (insight.crop.continuousHarvest) {
                                "该作物可连续采收，确认已拔除时再开启"
                            } else {
                                "该作物通常整批采收，已默认开启"
                            },
                            color = MapMuted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = finishAndClear,
                        onCheckedChange = { finishAndClear = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = MapLeafDeep)
                    )
                }
            }
            MapPrimaryButton(
                label = if (finishAndClear) "完成采摘并清空" else "记录本次采摘",
                color = MapLeafDeep,
                enabled = amount > 0,
                onClick = {
                    onConfirm(
                        MapHarvestDraft(
                            amountLabel = measureProfile.amountLabel(amount),
                            quality = quality,
                            finishAndClear = finishAndClear
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun MapSheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 12.dp)
            .width(42.dp)
            .height(5.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color(0xFFB9B7A9))
    )
}

@Composable
private fun MapValueRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(16.dp),
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFE0D9C7))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), color = MapMuted, fontWeight = FontWeight.Bold)
            Text(value, color = MapLeafDeep, fontWeight = FontWeight.Black)
            if (onClick != null) {
                Text(
                    "›",
                    modifier = Modifier.padding(start = 8.dp),
                    color = MapMuted,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun plantingDateDisplay(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    return if (date == today) {
        "今天  ·  ${date.toString().drop(5)}"
    } else {
        date.toString()
    }
}

@Composable
private fun MapStepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFE0D9C7))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), color = MapMuted, fontWeight = FontWeight.Bold)
            MapStepperButton(label = "−", filled = false, onClick = onDecrease)
            Text(
                value,
                modifier = Modifier.width(74.dp),
                color = MapInk,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            MapStepperButton(label = "+", filled = true, onClick = onIncrease)
        }
    }
}

@Composable
private fun MapStepperButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (filled) MapLeafDeep else MapSelectedMist
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (filled) androidx.compose.ui.graphics.Color.White else MapLeafDeep,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun CropChoiceCard(crop: CropProfile, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(74.dp)
            .height(86.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MapSelectedMist else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.56f),
        border = BorderStroke(2.dp, if (selected) MapLeafDeep else androidx.compose.ui.graphics.Color.Transparent)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = cropChoiceColor(crop)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        crop.name.take(1),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            }
            Text(crop.name, color = MapInk, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
        }
    }
}

private fun cropFilterGroup(crop: CropProfile): String {
    return when {
        crop.id == "chive" -> "葱蒜"
        crop.category.contains("瓜") -> "瓜类"
        crop.category.contains("茄果") || crop.category.contains("果菜") -> "果菜"
        crop.category.contains("叶") -> "叶菜"
        crop.category.contains("香") -> "香草"
        crop.category.contains("葱蒜") -> "葱蒜"
        crop.category.contains("根") || crop.category.contains("薯") -> "根茎"
        crop.category.contains("豆") -> "豆类"
        crop.category.contains("谷") -> "谷物"
        crop.category.contains("浆果") -> "浆果"
        else -> "其他"
    }
}

private fun cropChoiceColor(crop: CropProfile): androidx.compose.ui.graphics.Color {
    return when {
        crop.category.contains("茄果") || crop.category.contains("果菜") ->
            androidx.compose.ui.graphics.Color(0xFFE76D55)
        crop.category.contains("浆果") -> androidx.compose.ui.graphics.Color(0xFFCE6C7D)
        crop.category.contains("瓜") -> androidx.compose.ui.graphics.Color(0xFFE4B847)
        crop.category.contains("根菜") || crop.category.contains("薯") ->
            androidx.compose.ui.graphics.Color(0xFFD88955)
        crop.category.contains("豆") -> androidx.compose.ui.graphics.Color(0xFF709D56)
        crop.category.contains("葱蒜") || crop.category.contains("香") || crop.id == "chive" ->
            androidx.compose.ui.graphics.Color(0xFF5D9672)
        else -> androidx.compose.ui.graphics.Color(0xFF6FA767)
    }
}

@Composable
private fun MapChoicePill(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MapLeafDeep else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, if (selected) MapLeafDeep else androidx.compose.ui.graphics.Color(0xFFD7D0BC))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) androidx.compose.ui.graphics.Color.White else MapInk,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MapListChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) androidx.compose.ui.graphics.Color(0xFFFFECD0) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, if (selected) MapFertilizer else androidx.compose.ui.graphics.Color(0xFFD7D0BC))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), color = MapInk, fontWeight = FontWeight.Bold)
            Text(if (selected) "✓" else "", color = MapLeafDeep, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MapPrimaryButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(label, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}
