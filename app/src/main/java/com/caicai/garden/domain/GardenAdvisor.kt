package com.caicai.garden.domain

import com.caicai.garden.data.BatchStatus
import com.caicai.garden.data.CropLibrary
import com.caicai.garden.data.CropProfile
import com.caicai.garden.data.GardenDataState
import com.caicai.garden.data.GrowingStyle
import com.caicai.garden.data.GrowthStage
import com.caicai.garden.data.OperationRecord
import com.caicai.garden.data.OperationType
import com.caicai.garden.data.PlantingBatch
import com.caicai.garden.data.Plot
import com.caicai.garden.data.TaskPriority
import com.caicai.garden.data.TaskReminder
import com.caicai.garden.data.TaskType
import com.caicai.garden.data.WeatherForecast
import com.caicai.garden.data.growthOffsetDays
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PlantingInsight(
    val batch: PlantingBatch,
    val plot: Plot?,
    val crop: CropProfile,
    val rawDay: Int,
    val methodOffsetDays: Int,
    val adjustedDay: Int,
    val lifecycleProgress: Float,
    val stage: GrowthStage,
    val visual: CropVisualState,
    val progressPercent: Int,
    val harvestWindow: String,
    val nextFocus: String
)

object GardenAdvisor {
    fun insights(
        state: GardenDataState,
        weather: WeatherForecast?,
        today: LocalDate = LocalDate.now()
    ): List<PlantingInsight> {
        return state.batches
            .filter { it.status != BatchStatus.FINISHED }
            .sortedBy { it.startDate }
            .map { batch -> insightFor(state, batch, weather, today) }
    }

    fun insightFor(
        state: GardenDataState,
        batch: PlantingBatch,
        weather: WeatherForecast?,
        today: LocalDate = LocalDate.now()
    ): PlantingInsight {
        val crop = CropLibrary.byId(batch.cropId)
        val rawDay = daysSince(batch.startLocalDate(), today)
        val methodOffsetDays = batch.method.growthOffsetDays(crop)
        val growthDay = rawDay + methodOffsetDays
        val adjustedDay = adjustedGrowthDay(growthDay, crop, weather)
        val visual = CropVisualLibrary.stateFor(crop, adjustedDay)
        val stage = crop.stages[visual.stageIndex]
        val totalDays = max(crop.harvestEndDay, crop.stages.maxOf { it.toDay })
        val lifecycleProgress = (growthDay.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
        val progress = ((adjustedDay.toDouble() / totalDays.toDouble()) * 100).roundToInt().coerceIn(0, 100)
        val harvestStart = batch.startLocalDate()
            .plusDays((crop.harvestStartDay - methodOffsetDays).coerceAtLeast(0).toLong())
        val harvestEnd = batch.startLocalDate()
            .plusDays((crop.harvestEndDay - methodOffsetDays).coerceAtLeast(0).toLong())

        return PlantingInsight(
            batch = batch,
            plot = state.plots.firstOrNull { it.id == batch.plotId },
            crop = crop,
            rawDay = rawDay,
            methodOffsetDays = methodOffsetDays,
            adjustedDay = adjustedDay,
            lifecycleProgress = lifecycleProgress,
            stage = stage,
            visual = visual,
            progressPercent = progress,
            harvestWindow = "${harvestStart.monthValue}/${harvestStart.dayOfMonth} - ${harvestEnd.monthValue}/${harvestEnd.dayOfMonth}",
            nextFocus = stage.focus
        )
    }

    fun todayTasks(
        state: GardenDataState,
        weather: WeatherForecast?,
        today: LocalDate = LocalDate.now()
    ): List<TaskReminder> {
        return buildList {
            addAll(weatherHazards(weather, today))
            state.batches
                .filter { it.status != BatchStatus.FINISHED }
                .forEach { batch ->
                    addAll(batchTasks(state, batch, weather, today))
                }
        }.distinctBy { it.id }
            .sortedWith(compareBy<TaskReminder> { it.priority.sortWeight() }.thenBy { it.dueDate }.thenBy { it.title })
    }

    fun scheduleTasks(
        state: GardenDataState,
        weather: WeatherForecast?,
        today: LocalDate = LocalDate.now()
    ): List<TaskReminder> {
        val todayItems = todayTasks(state, weather, today)
        val futureItems = buildList {
            state.batches
                .filter { it.status != BatchStatus.FINISHED }
                .forEach { batch ->
                    addAll(futureBatchTasks(state, batch, today))
                }
        }
        return (todayItems + futureItems)
            .filter { !it.dueDate.isBefore(today) && !it.dueDate.isAfter(today.plusDays(7)) }
            .distinctBy { it.id }
            .sortedWith(compareBy<TaskReminder> { it.dueDate }.thenBy { it.priority.sortWeight() })
    }

    fun operationForTask(task: TaskReminder): OperationType {
        return when (task.type) {
            TaskType.WATER -> OperationType.WATER
            TaskType.FERTILIZE -> OperationType.FERTILIZE
            TaskType.HARVEST -> OperationType.HARVEST
            TaskType.PHOTO -> OperationType.PHOTO
            TaskType.CHECK, TaskType.WEATHER -> OperationType.NOTE
        }
    }

    private fun batchTasks(
        state: GardenDataState,
        batch: PlantingBatch,
        weather: WeatherForecast?,
        today: LocalDate
    ): List<TaskReminder> {
        val crop = CropLibrary.byId(batch.cropId)
        val plot = state.plots.firstOrNull { it.id == batch.plotId }
        val rawDay = daysSince(batch.startLocalDate(), today)
        val growthDay = rawDay + batch.method.growthOffsetDays(crop)
        val adjustedDay = adjustedGrowthDay(growthDay, crop, weather)
        val stage = stageFor(crop, adjustedDay)

        return listOfNotNull(
            waterTask(state, batch, crop, plot, stage, weather, today),
            fertilizeTask(state, batch, crop, plot, growthDay, weather, today),
            harvestTask(state, batch, crop, plot, growthDay, today),
            photoTask(state, batch, crop, plot, today),
            cropWeatherCheckTask(batch, crop, plot, weather, today)
        )
    }

    private fun futureBatchTasks(
        state: GardenDataState,
        batch: PlantingBatch,
        today: LocalDate
    ): List<TaskReminder> {
        val crop = CropLibrary.byId(batch.cropId)
        val plot = state.plots.firstOrNull { it.id == batch.plotId }
        val rawDay = daysSince(batch.startLocalDate(), today)
        val growthDay = rawDay + batch.method.growthOffsetDays(crop)
        val plotName = plot?.name ?: "未分配地块"

        return buildList {
            val nextFeed = crop.feedingDays.firstOrNull { it > growthDay && it - growthDay <= 7 }
            if (nextFeed != null) {
                val due = today.plusDays((nextFeed - growthDay).toLong())
                add(
                    TaskReminder(
                        id = "future-feed-${batch.id}-$nextFeed",
                        batchId = batch.id,
                        plotId = batch.plotId,
                        type = TaskType.FERTILIZE,
                        priority = TaskPriority.MEDIUM,
                        dueDate = due,
                        title = "${plotName} ${crop.name} 进入追肥窗口",
                        detail = "预计第 $nextFeed 天适合追肥，先看长势和天气再决定用量。",
                        actionLabel = "记录施肥"
                    )
                )
            }

            val daysToHarvest = crop.harvestStartDay - growthDay
            if (daysToHarvest in 1..7) {
                val due = today.plusDays(daysToHarvest.toLong())
                add(
                    TaskReminder(
                        id = "future-harvest-${batch.id}",
                        batchId = batch.id,
                        plotId = batch.plotId,
                        type = TaskType.HARVEST,
                        priority = TaskPriority.MEDIUM,
                        dueDate = due,
                        title = "${plotName} ${crop.name} 快到采摘期",
                        detail = "预计 $daysToHarvest 天后进入采收窗口，可提前巡查大小和成熟度。",
                        actionLabel = "记录采摘"
                    )
                )
            }

            val lastPhoto = lastRecordDate(state.records, batch.id, OperationType.PHOTO) ?: batch.startLocalDate()
            val nextPhoto = lastPhoto.plusDays(7)
            if (nextPhoto.isAfter(today) && !nextPhoto.isAfter(today.plusDays(7))) {
                add(
                    TaskReminder(
                        id = "future-photo-${batch.id}",
                        batchId = batch.id,
                        plotId = batch.plotId,
                        type = TaskType.PHOTO,
                        priority = TaskPriority.LOW,
                        dueDate = nextPhoto,
                        title = "${plotName} ${crop.name} 拍照留档",
                        detail = "每周留一张照片，后面判断生长快慢会更准。",
                        actionLabel = "记录照片"
                    )
                )
            }
        }
    }

    private fun waterTask(
        state: GardenDataState,
        batch: PlantingBatch,
        crop: CropProfile,
        plot: Plot?,
        stage: GrowthStage,
        weather: WeatherForecast?,
        today: LocalDate
    ): TaskReminder? {
        val todayWeather = weather?.today
        val todayRain = todayWeather?.precipitationMm ?: 0.0
        val tomorrowRain = weather?.daily?.getOrNull(1)?.precipitationMm ?: 0.0
        val maxTemp = todayWeather?.maxTempC ?: weather?.currentTempC ?: 28.0
        val plotName = plot?.name ?: "未分配地块"

        if (todayRain >= 12.0) {
            return TaskReminder(
                id = "drainage-${batch.id}-$today",
                batchId = batch.id,
                plotId = batch.plotId,
                type = TaskType.CHECK,
                priority = TaskPriority.HIGH,
                dueDate = today,
                title = "${plotName} ${crop.name} 雨后查积水",
                detail = "今天预计降雨 ${todayRain.oneDecimal()} mm，${plotName} 需要看排水和叶心积水。",
                actionLabel = "记录检查"
            )
        }

        val lastWaterDate = lastRecordDate(state.records, batch.id, OperationType.WATER)
        val referenceDate = lastWaterDate ?: batch.startLocalDate()
        val daysSinceWater = daysSince(referenceDate, today)
        val gap = waterGap(crop, plot, stage, maxTemp)
        val nextRain = todayRain + tomorrowRain

        if (daysSinceWater >= gap && nextRain < 4.0) {
            val priority = if (maxTemp >= 32.0 || daysSinceWater >= gap + 2) TaskPriority.HIGH else TaskPriority.MEDIUM
            return TaskReminder(
                id = "water-${batch.id}-$today",
                batchId = batch.id,
                plotId = batch.plotId,
                type = TaskType.WATER,
                priority = priority,
                dueDate = today,
                title = "${plotName} ${crop.name} 建议浇水",
                detail = "距上次浇水约 $daysSinceWater 天，当前为${stage.name}，未来 24 小时有效降雨不足。",
                actionLabel = "完成浇水"
            )
        }

        if (maxTemp >= 34.0 && crop.waterNeed >= 4 && nextRain < 6.0) {
            return TaskReminder(
                id = "hot-water-${batch.id}-$today",
                batchId = batch.id,
                plotId = batch.plotId,
                type = TaskType.WATER,
                priority = TaskPriority.HIGH,
                dueDate = today,
                title = "${plotName} ${crop.name} 高温补水",
                detail = "今天最高约 ${maxTemp.oneDecimal()}°C，${crop.name} 喜水，建议傍晚检查土壤湿度。",
                actionLabel = "记录浇水"
            )
        }

        return null
    }

    private fun fertilizeTask(
        state: GardenDataState,
        batch: PlantingBatch,
        crop: CropProfile,
        plot: Plot?,
        growthDay: Int,
        weather: WeatherForecast?,
        today: LocalDate
    ): TaskReminder? {
        val dueWindow = crop.feedingDays.firstOrNull { growthDay in it..(it + 4) } ?: return null
        val lastFertilize = lastRecordDate(state.records, batch.id, OperationType.FERTILIZE)
        val daysSinceFertilize = lastFertilize?.let { daysSince(it, today) } ?: 99
        if (daysSinceFertilize < 10) return null

        val plotName = plot?.name ?: "未分配地块"
        val tomorrowRain = weather?.daily?.getOrNull(1)?.precipitationMm ?: 0.0
        val rainAdvice = if (tomorrowRain >= 8.0) "明天雨量偏大，建议雨后 1 天再追肥。" else "天气窗口可以，追肥后结合少量浇水。"

        return TaskReminder(
            id = "fertilize-${batch.id}-$dueWindow",
            batchId = batch.id,
            plotId = batch.plotId,
            type = TaskType.FERTILIZE,
            priority = TaskPriority.MEDIUM,
            dueDate = today,
            title = "${plotName} ${crop.name} 进入追肥窗口",
            detail = "当前估算生长第 $growthDay 天，接近第 $dueWindow 天追肥节点。$rainAdvice",
            actionLabel = "记录施肥"
        )
    }

    private fun harvestTask(
        state: GardenDataState,
        batch: PlantingBatch,
        crop: CropProfile,
        plot: Plot?,
        growthDay: Int,
        today: LocalDate
    ): TaskReminder? {
        if (growthDay < crop.harvestStartDay) return null

        val lastHarvest = lastRecordDate(state.records, batch.id, OperationType.HARVEST)
        if (!crop.continuousHarvest && lastHarvest != null) return null
        if (crop.continuousHarvest && lastHarvest != null && daysSince(lastHarvest, today) < 2) return null

        val plotName = plot?.name ?: "未分配地块"
        val priority = if (growthDay > crop.harvestEndDay) TaskPriority.HIGH else TaskPriority.MEDIUM
        val detail = if (crop.continuousHarvest) {
            "已经进入连续采收期，建议巡查成熟果，及时采收能促进后续生长。"
        } else {
            "已经进入采收窗口，可按食用大小和成熟度安排采摘。"
        }

        return TaskReminder(
            id = "harvest-${batch.id}-$today",
            batchId = batch.id,
            plotId = batch.plotId,
            type = TaskType.HARVEST,
            priority = priority,
            dueDate = today,
            title = "${plotName} ${crop.name} 可采摘",
            detail = detail,
            actionLabel = "记录采摘"
        )
    }

    private fun photoTask(
        state: GardenDataState,
        batch: PlantingBatch,
        crop: CropProfile,
        plot: Plot?,
        today: LocalDate
    ): TaskReminder? {
        val lastPhoto = lastRecordDate(state.records, batch.id, OperationType.PHOTO) ?: batch.startLocalDate()
        val daysSincePhoto = daysSince(lastPhoto, today)
        if (daysSincePhoto < 7) return null
        val plotName = plot?.name ?: "未分配地块"
        return TaskReminder(
            id = "photo-${batch.id}-$today",
            batchId = batch.id,
            plotId = batch.plotId,
            type = TaskType.PHOTO,
            priority = TaskPriority.LOW,
            dueDate = today,
            title = "${plotName} ${crop.name} 更新照片",
            detail = "距上次照片记录约 $daysSincePhoto 天，建议留一张当前长势照片。",
            actionLabel = "记录照片"
        )
    }

    private fun cropWeatherCheckTask(
        batch: PlantingBatch,
        crop: CropProfile,
        plot: Plot?,
        weather: WeatherForecast?,
        today: LocalDate
    ): TaskReminder? {
        val weatherDay = weather?.today ?: return null
        val plotName = plot?.name ?: "未分配地块"
        return when {
            weatherDay.maxTempC > crop.maxGoodTemperature + 3 -> TaskReminder(
                id = "crop-heat-${batch.id}-$today",
                batchId = batch.id,
                plotId = batch.plotId,
                type = TaskType.CHECK,
                priority = TaskPriority.MEDIUM,
                dueDate = today,
                title = "${plotName} ${crop.name} 高温巡查",
                detail = "今天最高约 ${weatherDay.maxTempC.oneDecimal()}°C，超过${crop.name}适宜范围，注意萎蔫和晒伤。",
                actionLabel = "记录检查"
            )

            weatherDay.minTempC < crop.minGoodTemperature - 8 -> TaskReminder(
                id = "crop-cold-${batch.id}-$today",
                batchId = batch.id,
                plotId = batch.plotId,
                type = TaskType.CHECK,
                priority = TaskPriority.MEDIUM,
                dueDate = today,
                title = "${plotName} ${crop.name} 低温巡查",
                detail = "今晚最低约 ${weatherDay.minTempC.oneDecimal()}°C，低于${crop.name}舒适温区，必要时覆盖保温。",
                actionLabel = "记录检查"
            )

            else -> null
        }
    }

    private fun weatherHazards(weather: WeatherForecast?, today: LocalDate): List<TaskReminder> {
        val weatherDay = weather?.today ?: return emptyList()
        return buildList {
            if (weatherDay.maxTempC >= 35.0) {
                add(
                    TaskReminder(
                        id = "weather-heat-$today",
                        batchId = null,
                        plotId = null,
                        type = TaskType.WEATHER,
                        priority = TaskPriority.HIGH,
                        dueDate = today,
                        title = "今日高温，优先安排早晚作业",
                        detail = "最高约 ${weatherDay.maxTempC.oneDecimal()}°C，中午避免浇冷水和大幅修剪。",
                        actionLabel = "记录处理"
                    )
                )
            }
            if (weatherDay.precipitationMm >= 20.0) {
                add(
                    TaskReminder(
                        id = "weather-rain-$today",
                        batchId = null,
                        plotId = null,
                        type = TaskType.WEATHER,
                        priority = TaskPriority.HIGH,
                        dueDate = today,
                        title = "今日强降雨，检查排水",
                        detail = "预计降雨 ${weatherDay.precipitationMm.oneDecimal()} mm，低洼地块和盆栽需要重点看积水。",
                        actionLabel = "记录处理"
                    )
                )
            }
            if (weatherDay.minTempC <= 5.0) {
                add(
                    TaskReminder(
                        id = "weather-cold-$today",
                        batchId = null,
                        plotId = null,
                        type = TaskType.WEATHER,
                        priority = TaskPriority.HIGH,
                        dueDate = today,
                        title = "今晚低温，注意保温",
                        detail = "最低约 ${weatherDay.minTempC.oneDecimal()}°C，幼苗、瓜果类和阳台盆栽优先覆盖。",
                        actionLabel = "记录处理"
                    )
                )
            }
        }
    }

    private fun waterGap(
        crop: CropProfile,
        plot: Plot?,
        stage: GrowthStage,
        maxTemp: Double
    ): Int {
        var gap = when (crop.waterNeed) {
            5 -> 1
            4 -> 2
            3 -> 3
            else -> 4
        }
        if (plot?.growingStyle == GrowingStyle.POT || plot?.growingStyle == GrowingStyle.BALCONY) gap -= 1
        if (maxTemp >= 30.0) gap -= 1
        if (stage.name.contains("旺") || stage.name.contains("结果") || stage.name.contains("采收")) gap -= 1
        return max(1, gap)
    }

    private fun stageFor(crop: CropProfile, adjustedDay: Int): GrowthStage {
        return crop.stages.firstOrNull { adjustedDay in it.fromDay..it.toDay }
            ?: crop.stages.last()
    }

    private fun adjustedGrowthDay(
        growthDay: Int,
        crop: CropProfile,
        weather: WeatherForecast?
    ): Int {
        val today = weather?.today ?: return growthDay
        val averageTemp = (today.maxTempC + today.minTempC) / 2.0
        val multiplier = when {
            averageTemp < crop.minGoodTemperature -> 0.9
            averageTemp > crop.maxGoodTemperature -> 1.04
            averageTemp >= crop.minGoodTemperature && averageTemp <= crop.maxGoodTemperature -> 1.0
            else -> 1.0
        }
        return max(0, (growthDay * multiplier).roundToInt())
    }

    private fun lastRecordDate(
        records: List<OperationRecord>,
        batchId: String,
        type: OperationType
    ): LocalDate? {
        return records
            .asSequence()
            .filter { it.batchId == batchId && it.type == type }
            .mapNotNull { it.localDateOrNull() }
            .maxOrNull()
    }

    private fun OperationRecord.localDateOrNull(): LocalDate? {
        return runCatching { LocalDateTime.parse(timestamp).toLocalDate() }
            .recoverCatching { LocalDate.parse(timestamp) }
            .getOrNull()
    }

    private fun daysSince(start: LocalDate, today: LocalDate): Int {
        return max(0, ChronoUnit.DAYS.between(start, today).toInt())
    }

    private fun TaskPriority.sortWeight(): Int {
        return when (this) {
            TaskPriority.HIGH -> 0
            TaskPriority.MEDIUM -> 1
            TaskPriority.LOW -> 2
        }
    }

    private fun Double.oneDecimal(): String = String.format("%.1f", this)

    private fun Int.clampForPercent(): Int = min(100, max(0, this))
}
