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
import com.caicai.garden.data.farmLayoutFor
import com.caicai.garden.data.moveTile
import com.caicai.garden.data.placeBatchAtCell
import com.caicai.garden.data.placeBatchInFirstEmptyCell
import com.caicai.garden.data.removeBatch
import com.caicai.garden.data.withFarmLayout
import com.caicai.garden.data.withoutPlot
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
        persist(
            dataState.copy(
                plots = dataState.plots + plot,
                farmLayouts = dataState.farmLayouts +
                    (plot.id to repository.defaultFarmLayout(emptyList()))
            )
        )
        message = "已添加地块"
    }

    fun deletePlot(plotId: String) {
        val plot = dataState.plots.firstOrNull { it.id == plotId }
        if (plot == null) {
            message = "未找到这个地块"
            return
        }
        persist(dataState.withoutPlot(plotId))
        message = "已删除${plot.name}，历史记录仍保留"
    }

    fun addBatch(
        plotId: String?,
        cropId: String,
        variety: String,
        method: PlantingMethod,
        startDate: String,
        quantityLabel: String,
        targetRow: Int? = null,
        targetColumn: Int? = null,
        showMessage: Boolean = true
    ) {
        val targetPlotId = plotId?.takeIf { id -> dataState.plots.any { it.id == id } }
        if (targetPlotId == null) {
            message = "请选择地块"
            return
        }
        val crop = CropLibrary.byId(cropId)
        if (method !in crop.supportedPlantingMethods) {
            message = "${crop.name}不支持${method.label}，请选择可用的种植方式"
            return
        }
        if (quantityLabel.isBlank()) {
            message = "请填写种植数量"
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
        val requestedCell = if (targetRow != null && targetColumn != null) {
            targetRow to targetColumn
        } else {
            null
        }
        val currentLayout = dataState.farmLayoutFor(targetPlotId)
        if (requestedCell != null) {
            val (row, column) = requestedCell
            if (row !in 0 until currentLayout.rows || column !in 0 until currentLayout.columns) {
                message = "所选菜地位置无效，请重新选择"
                return
            }
            if (currentLayout.tiles.any { it.row == row && it.column == column }) {
                message = "这格已经有内容，请选择空地"
                return
            }
        }
        val batch = PlantingBatch(
            id = GardenRepository.newId(),
            plotId = targetPlotId,
            cropId = crop.id,
            variety = variety.trim(),
            method = method,
            startDate = parsedDate.toString(),
            quantityLabel = quantityLabel.trim().ifBlank { "未填写" },
            status = BatchStatus.ACTIVE
        )
        val stateWithBatch = dataState.copy(batches = dataState.batches + batch)
        val nextLayout = requestedCell?.let { (row, column) ->
            currentLayout.placeBatchAtCell(batch.id, row, column)
        } ?: currentLayout.placeBatchInFirstEmptyCell(batch.id)
        val placedInGarden = nextLayout != currentLayout
        persist(stateWithBatch.withFarmLayout(targetPlotId, nextLayout))
        addSystemRecord(
            batch,
            when (method) {
                PlantingMethod.SEED -> OperationType.SOW
                PlantingMethod.TRANSPLANT -> OperationType.TRANSPLANT
                PlantingMethod.CUTTING -> OperationType.CUTTING
                PlantingMethod.DIVISION -> OperationType.DIVISION
                PlantingMethod.BULB -> OperationType.BULB_PLANT
                PlantingMethod.TUBER -> OperationType.TUBER_PLANT
            },
            "${method.label} · ${method.dateLabel} $parsedDate"
        )
        if (showMessage) {
            message = if (placedInGarden) {
                "已记录 ${crop.name}并放入菜畦：${method.materialLabel}，${method.dateLabel} $parsedDate"
            } else {
                "已记录 ${crop.name}，当前菜畦没有空位"
            }
        }
    }

    fun addOperation(
        batchId: String?,
        type: OperationType,
        amountLabel: String,
        note: String,
        showMessage: Boolean = true
    ): Boolean {
        val batch = dataState.batches.firstOrNull { it.id == batchId }
        val duplicateWindowStart = LocalDateTime.now().minusSeconds(10)
        val duplicate = dataState.records.asReversed().firstOrNull {
            it.batchId == batch?.id && it.type == type
        }?.timestamp?.let { timestamp ->
            runCatching { LocalDateTime.parse(timestamp) }.getOrNull()
        }?.isAfter(duplicateWindowStart) == true
        if (duplicate) {
            message = "${type.label}刚刚已经记录，请勿重复操作"
            return false
        }
        val record = OperationRecord(
            id = GardenRepository.newId(),
            batchId = batch?.id,
            plotId = batch?.plotId,
            type = type,
            timestamp = LocalDateTime.now().withNano(0).toString(),
            amountLabel = amountLabel.trim(),
            note = note.trim()
        )
        persist(dataState.copy(records = dataState.records + record))
        if (showMessage) message = "已记录${type.label}"
        return true
    }

    fun harvestBatch(
        batchId: String,
        amountLabel: String,
        quality: String,
        finishAndClear: Boolean
    ) {
        val batch = dataState.batches.firstOrNull {
            it.id == batchId && it.status != BatchStatus.FINISHED
        }
        if (batch == null) {
            message = "这批作物已经结束"
            return
        }
        val record = OperationRecord(
            id = GardenRepository.newId(),
            batchId = batch.id,
            plotId = batch.plotId,
            type = OperationType.HARVEST,
            timestamp = LocalDateTime.now().withNano(0).toString(),
            amountLabel = amountLabel.trim(),
            note = "品质：${quality.trim().ifBlank { "良好" }}"
        )
        var nextState = dataState.copy(records = dataState.records + record)
        if (finishAndClear) {
            nextState = nextState.copy(
                batches = nextState.batches.map {
                    if (it.id == batch.id) it.copy(status = BatchStatus.FINISHED) else it
                }
            )
            val clearedLayout = nextState.farmLayoutFor(batch.plotId).removeBatch(batch.id)
            nextState = nextState.withFarmLayout(batch.plotId, clearedLayout)
        }
        persist(nextState)
        message = if (finishAndClear) "已记录采摘并清空这批菜地" else "已记录本次采摘"
    }

    fun completeTask(task: TaskReminder) {
        val type = GardenAdvisor.operationForTask(task)
        val batch = task.batchId?.let { id -> dataState.batches.firstOrNull { it.id == id } }
        val record = OperationRecord(
            id = GardenRepository.newId(),
            batchId = batch?.id,
            plotId = task.plotId ?: batch?.plotId,
            type = type,
            timestamp = LocalDateTime.now().withNano(0).toString(),
            amountLabel = task.actionLabel,
            note = task.title
        )
        val stateWithRecord = dataState.copy(records = dataState.records + record)
        if (task.type == com.caicai.garden.data.TaskType.LIFECYCLE && batch != null) {
            persist(finishAndClearBatch(stateWithRecord, batch))
            message = "已结束本茬并清空菜格"
        } else {
            persist(stateWithRecord)
            message = "已完成：${task.title}"
        }
    }

    fun finishBatch(batchId: String) {
        val batch = dataState.batches.firstOrNull { it.id == batchId }
        if (batch == null) {
            message = "未找到这批作物"
            return
        }
        persist(finishAndClearBatch(dataState, batch))
        message = "种植批次已结束并清空菜格"
    }

    fun placeFarmTile(
        plotId: String?,
        row: Int,
        column: Int,
        type: FarmTileType,
        batchId: String?
    ) {
        val activePlotId = plotId?.takeIf { id -> dataState.plots.any { it.id == id } } ?: return
        val layout = dataState.farmLayoutFor(activePlotId)
        if (row !in 0 until layout.rows || column !in 0 until layout.columns) return

        val activeBatchId = if (type == FarmTileType.RAISED_BED) {
            batchId?.takeIf { id ->
                dataState.batches.any {
                    it.id == id &&
                        it.plotId == activePlotId &&
                        it.status != BatchStatus.FINISHED
                }
            }
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
        persist(dataState.withFarmLayout(activePlotId, layout.copy(tiles = nextTiles)))
    }

    fun clearFarmTile(plotId: String?, row: Int, column: Int) {
        val activePlotId = plotId?.takeIf { id -> dataState.plots.any { it.id == id } } ?: return
        val layout = dataState.farmLayoutFor(activePlotId)
        persist(
            dataState.withFarmLayout(
                activePlotId,
                layout.copy(tiles = layout.tiles.filterNot { it.row == row && it.column == column })
            )
        )
    }

    fun moveFarmTile(
        plotId: String?,
        fromRow: Int,
        fromColumn: Int,
        toRow: Int,
        toColumn: Int
    ) {
        val activePlotId = plotId?.takeIf { id -> dataState.plots.any { it.id == id } } ?: return
        val layout = dataState.farmLayoutFor(activePlotId)
        val targetWasOccupied = layout.tiles.any { it.row == toRow && it.column == toColumn }
        val nextLayout = layout.moveTile(fromRow, fromColumn, toRow, toColumn)
        if (nextLayout == layout) return
        persist(dataState.withFarmLayout(activePlotId, nextLayout))
        message = if (targetWasOccupied) "目标格已有内容，已交换位置" else "已移动到新位置"
    }

    fun resetFarmLayout(plotId: String?) {
        val activePlotId = plotId?.takeIf { id -> dataState.plots.any { it.id == id } } ?: return
        val plotBatches = dataState.batches.filter { it.plotId == activePlotId }
        persist(
            dataState.withFarmLayout(
                activePlotId,
                repository.defaultFarmLayout(plotBatches)
            )
        )
        message = "已重置当前地块布局"
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
            timestamp = LocalDateTime.now().withNano(0).toString(),
            amountLabel = batch.quantityLabel,
            note = note
        )
        persist(dataState.copy(records = dataState.records + record))
    }

    private fun persist(next: GardenDataState) {
        dataState = next
        repository.save(next)
    }

    private fun finishAndClearBatch(state: GardenDataState, batch: PlantingBatch): GardenDataState {
        val finishedState = state.copy(
            batches = state.batches.map {
                if (it.id == batch.id) it.copy(status = BatchStatus.FINISHED) else it
            }
        )
        return finishedState.withFarmLayout(
            batch.plotId,
            finishedState.farmLayoutFor(batch.plotId).removeBatch(batch.id)
        )
    }
}
