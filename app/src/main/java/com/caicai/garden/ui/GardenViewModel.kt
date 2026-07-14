package com.caicai.garden.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caicai.garden.data.BatchStatus
import com.caicai.garden.data.CropLibrary
import com.caicai.garden.data.FarmTile
import com.caicai.garden.data.FarmTileType
import com.caicai.garden.data.Garden
import com.caicai.garden.data.GardenDataState
import com.caicai.garden.data.GardenRepository
import com.caicai.garden.data.GrowingStyle
import com.caicai.garden.data.OperationRecord
import com.caicai.garden.data.OperationType
import com.caicai.garden.data.PlantingBatch
import com.caicai.garden.data.PlantingMethod
import com.caicai.garden.data.Plot
import com.caicai.garden.data.TaskReminder
import com.caicai.garden.data.WeatherForecast
import com.caicai.garden.data.WeatherService
import com.caicai.garden.data.defaultFarmTileRotation
import com.caicai.garden.data.moveTile
import com.caicai.garden.data.normalizeFarmTileRotation
import com.caicai.garden.domain.GardenAdvisor
import com.caicai.garden.domain.PlantingInsight
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class GardenViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GardenRepository(application)
    private val weatherService = WeatherService()

    var dataState by mutableStateOf(repository.load())
        private set

    var weather by mutableStateOf<WeatherForecast?>(null)
        private set

    var weatherLoading by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    val garden: Garden?
        get() = dataState.gardens.firstOrNull()

    val insights: List<PlantingInsight>
        get() = GardenAdvisor.insights(dataState, weather)

    val todayTasks: List<TaskReminder>
        get() = GardenAdvisor.todayTasks(dataState, weather)

    val scheduleTasks: List<TaskReminder>
        get() = GardenAdvisor.scheduleTasks(dataState, weather)

    init {
        refreshWeather()
    }

    fun refreshWeather() {
        val currentGarden = garden ?: return
        weatherLoading = true
        viewModelScope.launch {
            weather = weatherService.fetch(currentGarden)
            weatherLoading = false
        }
    }

    fun updateGarden(name: String, locationName: String, latitude: String, longitude: String) {
        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()
        if (name.isBlank() || locationName.isBlank() || lat == null || lon == null) {
            message = "菜园名称、地点和经纬度需要填写完整"
            return
        }

        val existing = garden
        val updated = if (existing == null) {
            Garden(GardenRepository.newId(), name.trim(), locationName.trim(), lat, lon)
        } else {
            existing.copy(name = name.trim(), locationName = locationName.trim(), latitude = lat, longitude = lon)
        }
        val gardens = if (existing == null) listOf(updated) else dataState.gardens.map { if (it.id == updated.id) updated else it }
        persist(dataState.copy(gardens = gardens))
        message = "菜园信息已更新"
        refreshWeather()
    }

    fun addPlot(name: String, sizeLabel: String, style: GrowingStyle, soilType: String) {
        val currentGarden = garden
        if (currentGarden == null) {
            message = "请先创建菜园"
            return
        }
        if (name.isBlank()) {
            message = "地块名称不能为空"
            return
        }
        val plot = Plot(
            id = GardenRepository.newId(),
            gardenId = currentGarden.id,
            name = name.trim(),
            sizeLabel = sizeLabel.trim().ifBlank { "未填写" },
            growingStyle = style,
            soilType = soilType.trim().ifBlank { "壤土" }
        )
        persist(dataState.copy(plots = dataState.plots + plot))
        message = "已添加地块"
    }

    fun addBatch(
        plotId: String?,
        cropId: String,
        variety: String,
        method: PlantingMethod,
        startDate: String,
        quantityLabel: String
    ) {
        if (plotId.isNullOrBlank()) {
            message = "请选择地块"
            return
        }
        val parsedDate = runCatching { LocalDate.parse(startDate) }.getOrNull()
        if (parsedDate == null) {
            message = "日期格式需要是 YYYY-MM-DD"
            return
        }
        if (parsedDate.isAfter(LocalDate.now())) {
            message = "种植日期不能晚于今天"
            return
        }
        val crop = CropLibrary.byId(cropId)
        val batch = PlantingBatch(
            id = GardenRepository.newId(),
            plotId = plotId,
            cropId = crop.id,
            variety = variety.trim(),
            method = method,
            startDate = parsedDate.toString(),
            quantityLabel = quantityLabel.trim().ifBlank { "未填写" },
            status = BatchStatus.ACTIVE
        )
        persist(dataState.copy(batches = dataState.batches + batch))
        addSystemRecord(
            batch,
            when (method) {
                PlantingMethod.SEED -> OperationType.SOW
                PlantingMethod.TRANSPLANT -> OperationType.TRANSPLANT
                PlantingMethod.CUTTING -> OperationType.CUTTING
            },
            "${method.label} · ${method.dateLabel} $parsedDate"
        )
        message = "已记录 ${crop.name}：${method.materialLabel}，${method.dateLabel} $parsedDate"
    }

    fun addOperation(batchId: String?, type: OperationType, amountLabel: String, note: String) {
        val batch = dataState.batches.firstOrNull { it.id == batchId }
        val record = OperationRecord(
            id = GardenRepository.newId(),
            batchId = batch?.id,
            plotId = batch?.plotId,
            type = type,
            timestamp = LocalDateTime.now().withSecond(0).withNano(0).toString(),
            amountLabel = amountLabel.trim(),
            note = note.trim()
        )
        persist(dataState.copy(records = dataState.records + record))
        message = "已记录${type.label}"
    }

    fun completeTask(task: TaskReminder) {
        val type = GardenAdvisor.operationForTask(task)
        val batch = task.batchId?.let { id -> dataState.batches.firstOrNull { it.id == id } }
        val record = OperationRecord(
            id = GardenRepository.newId(),
            batchId = batch?.id,
            plotId = task.plotId ?: batch?.plotId,
            type = type,
            timestamp = LocalDateTime.now().withSecond(0).withNano(0).toString(),
            amountLabel = task.actionLabel,
            note = task.title
        )
        persist(dataState.copy(records = dataState.records + record))
        message = "已完成：${task.title}"
    }

    fun finishBatch(batchId: String) {
        persist(
            dataState.copy(
                batches = dataState.batches.map {
                    if (it.id == batchId) it.copy(status = BatchStatus.FINISHED) else it
                }
            )
        )
        message = "种植批次已结束"
    }

    fun placeFarmTile(row: Int, column: Int, type: FarmTileType, batchId: String?) {
        val layout = dataState.farmLayout
        if (row !in 0 until layout.rows || column !in 0 until layout.columns) return

        val activeBatchId = if (type == FarmTileType.RAISED_BED) {
            batchId?.takeIf { id -> dataState.batches.any { it.id == id && it.status != BatchStatus.FINISHED } }
        } else {
            null
        }
        if (type == FarmTileType.RAISED_BED && activeBatchId == null) {
            message = "请选择一个正在种植的作物再摆放"
            return
        }

        val nextTiles = layout.tiles
            .filterNot { it.row == row && it.column == column }
            .let { tiles ->
                if (type == FarmTileType.GRASS) {
                    tiles
                } else {
                    tiles + FarmTile(
                        row = row,
                        column = column,
                        type = type,
                        batchId = activeBatchId,
                        rotationDegrees = defaultFarmTileRotation(row, column, type)
                    )
                }
            }
        persist(dataState.copy(farmLayout = layout.copy(tiles = nextTiles)))
    }

    fun clearFarmTile(row: Int, column: Int) {
        val layout = dataState.farmLayout
        persist(
            dataState.copy(
                farmLayout = layout.copy(
                    tiles = layout.tiles.filterNot { it.row == row && it.column == column }
                )
            )
        )
    }

    fun moveFarmTile(fromRow: Int, fromColumn: Int, toRow: Int, toColumn: Int) {
        val layout = dataState.farmLayout
        val targetWasOccupied = layout.tiles.any { it.row == toRow && it.column == toColumn }
        val nextLayout = layout.moveTile(fromRow, fromColumn, toRow, toColumn)
        if (nextLayout == layout) return
        persist(dataState.copy(farmLayout = nextLayout))
        message = if (targetWasOccupied) "目标格已有内容，已交换位置" else "已移动到新位置"
    }

    fun rotateFarmTile(row: Int, column: Int, deltaDegrees: Float) {
        val layout = dataState.farmLayout
        if (row !in 0 until layout.rows || column !in 0 until layout.columns) return
        val nextTiles = layout.tiles.map { tile ->
            if (tile.row == row && tile.column == column) {
                tile.copy(rotationDegrees = normalizeFarmTileRotation(tile.rotationDegrees + deltaDegrees))
            } else {
                tile
            }
        }
        persist(dataState.copy(farmLayout = layout.copy(tiles = nextTiles)))
    }

    fun resetFarmTileRotation(row: Int, column: Int) {
        val layout = dataState.farmLayout
        if (row !in 0 until layout.rows || column !in 0 until layout.columns) return
        val nextTiles = layout.tiles.map { tile ->
            if (tile.row == row && tile.column == column) {
                tile.copy(rotationDegrees = 0f)
            } else {
                tile
            }
        }
        persist(dataState.copy(farmLayout = layout.copy(tiles = nextTiles)))
    }

    fun resetFarmLayout() {
        persist(dataState.copy(farmLayout = repository.defaultFarmLayout(dataState.batches)))
        message = "已重置农场布局"
    }

    fun consumeMessage() {
        message = null
    }

    private fun addSystemRecord(batch: PlantingBatch, type: OperationType, note: String) {
        val record = OperationRecord(
            id = GardenRepository.newId(),
            batchId = batch.id,
            plotId = batch.plotId,
            type = type,
            timestamp = LocalDateTime.now().withSecond(0).withNano(0).toString(),
            amountLabel = batch.quantityLabel,
            note = note
        )
        persist(dataState.copy(records = dataState.records + record))
    }

    private fun persist(next: GardenDataState) {
        dataState = next
        repository.save(next)
    }
}
