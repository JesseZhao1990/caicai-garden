package com.caicai.garden.domain

import com.caicai.garden.data.CropLibrary
import com.caicai.garden.data.GardenDataState
import com.caicai.garden.data.GrowingStyle
import com.caicai.garden.data.OperationRecord
import com.caicai.garden.data.OperationType
import com.caicai.garden.data.PlantingBatch
import com.caicai.garden.data.PlantingMethod
import com.caicai.garden.data.Plot
import com.caicai.garden.data.TaskType
import com.caicai.garden.data.WeatherDay
import com.caicai.garden.data.WeatherForecast
import com.caicai.garden.data.growthOffsetDays
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenAdvisorTest {
    private val plantingDate = LocalDate.of(2026, 7, 14)
    private val plot = Plot(
        id = "plot-1",
        gardenId = "garden-1",
        name = "1 号畦",
        sizeLabel = "3 平米",
        growingStyle = GrowingStyle.OPEN_FIELD,
        soilType = "壤土"
    )

    @Test
    fun seedlingAndSeedUseDifferentStartingGrowthDays() {
        val seedInsight = insight(PlantingMethod.SEED, plantingDate)
        val seedlingInsight = insight(PlantingMethod.TRANSPLANT, plantingDate)
        val tomato = CropLibrary.byId("tomato")

        assertEquals(0, seedInsight.rawDay)
        assertEquals(0, seedInsight.methodOffsetDays)
        assertEquals(0, seedInsight.adjustedDay)
        assertEquals(tomato.transplantGrowthOffsetDays, seedlingInsight.methodOffsetDays)
        assertEquals(tomato.transplantGrowthOffsetDays, seedlingInsight.adjustedDay)
        assertTrue(seedlingInsight.lifecycleProgress > seedInsight.lifecycleProgress)
        assertTrue(seedlingInsight.progressPercent > seedInsight.progressPercent)
        assertTrue(seedlingInsight.harvestWindow != seedInsight.harvestWindow)
    }

    @Test
    fun progressIncreasesAsCalendarDaysPass() {
        val dayZero = insight(PlantingMethod.SEED, plantingDate)
        val dayTen = insight(PlantingMethod.SEED, plantingDate.plusDays(10))
        val dayThirty = insight(PlantingMethod.SEED, plantingDate.plusDays(30))

        assertEquals(0, dayZero.progressPercent)
        assertTrue(dayTen.adjustedDay > dayZero.adjustedDay)
        assertTrue(dayTen.lifecycleProgress > dayZero.lifecycleProgress)
        assertTrue(dayTen.progressPercent > dayZero.progressPercent)
        assertTrue(dayThirty.lifecycleProgress > dayTen.lifecycleProgress)
        assertTrue(dayThirty.progressPercent > dayTen.progressPercent)
    }

    @Test
    fun transplantOffsetComesFromEachCropProfile() {
        val tomato = CropLibrary.byId("tomato")
        val cucumber = CropLibrary.byId("cucumber")

        assertEquals(14, PlantingMethod.TRANSPLANT.growthOffsetDays(tomato))
        assertEquals(10, PlantingMethod.TRANSPLANT.growthOffsetDays(cucumber))
        assertEquals(0, PlantingMethod.SEED.growthOffsetDays(tomato))
    }

    @Test
    fun plantingMethodsComeFromEachCropProfile() {
        assertEquals(
            listOf(PlantingMethod.TRANSPLANT, PlantingMethod.SEED),
            CropLibrary.byId("tomato").supportedPlantingMethods
        )
        assertEquals(
            listOf(PlantingMethod.SEED),
            CropLibrary.byId("spinach").supportedPlantingMethods
        )
        assertEquals(
            listOf(PlantingMethod.SEED, PlantingMethod.CUTTING),
            CropLibrary.byId("water_spinach").supportedPlantingMethods
        )
        assertTrue(
            PlantingMethod.DIVISION in CropLibrary.byId("chive").supportedPlantingMethods
        )
        assertTrue(CropLibrary.crops.all { it.supportedPlantingMethods.isNotEmpty() })
        assertFalse(
            PlantingMethod.TRANSPLANT in CropLibrary.byId("carrot").supportedPlantingMethods
        )
    }

    @Test
    fun recommendationsChangeWithCropAndGrowthStage() {
        val tomato = insight(
            method = PlantingMethod.TRANSPLANT,
            today = plantingDate.plusDays(65),
            cropId = "tomato"
        )
        val pakchoi = insight(
            method = PlantingMethod.SEED,
            today = plantingDate.plusDays(10),
            cropId = "pakchoi"
        )

        val tomatoFertilizer = GardenAdvisor.fertilizerRecommendation(tomato)
        val pakchoiFertilizer = GardenAdvisor.fertilizerRecommendation(pakchoi)
        assertTrue(tomatoFertilizer.choices.first().contains("高钾"))
        assertTrue(pakchoiFertilizer.choices.first().contains("叶菜"))
        assertTrue(
            tomatoFertilizer.defaultAmountGrams > pakchoiFertilizer.defaultAmountGrams
        )

        val cucumberWater = GardenAdvisor.waterRecommendation(
            insight(PlantingMethod.SEED, plantingDate.plusDays(5), "cucumber"),
            weather = null
        )
        val spinachWater = GardenAdvisor.waterRecommendation(
            insight(PlantingMethod.SEED, plantingDate.plusDays(5), "spinach"),
            weather = null
        )
        assertEquals("浇透", cucumberWater.amountLabel)
        assertEquals("少量补水", spinachWater.amountLabel)
    }

    @Test
    fun harvestInputUsesCropAppropriateUnit() {
        val tomato = GardenAdvisor.harvestMeasureProfile(CropLibrary.byId("tomato"))
        val lettuce = GardenAdvisor.harvestMeasureProfile(CropLibrary.byId("lettuce"))
        val radish = GardenAdvisor.harvestMeasureProfile(CropLibrary.byId("radish"))

        assertEquals("kg", tomato.unit)
        assertEquals("g", lettuce.unit)
        assertEquals("根", radish.unit)
        assertEquals("0.5 kg", tomato.amountLabel(tomato.defaultAmount))
        assertEquals("200 g", lettuce.amountLabel(lettuce.defaultAmount))
        assertEquals("1 根", radish.amountLabel(radish.defaultAmount))
    }

    @Test
    fun allBundledCropAssetsAreAvailableAsPlantableCrops() {
        assertEquals(32, CropLibrary.crops.size)
        assertEquals(32, CropLibrary.crops.map { it.id }.distinct().size)
        assertTrue(PlantingMethod.BULB in CropLibrary.byId("garlic").supportedPlantingMethods)
        assertTrue(PlantingMethod.TUBER in CropLibrary.byId("potato").supportedPlantingMethods)
    }

    @Test
    fun continuousHarvestUsesEachCropsOwnInterval() {
        val today = plantingDate.plusDays(80)
        val cucumber = batch("cucumber", plantingDate.plusDays(35), PlantingMethod.SEED)
        val tomato = batch("tomato", plantingDate, PlantingMethod.SEED)
        val yesterday = today.minusDays(1).atStartOfDay()
        val state = GardenDataState(
            plots = listOf(plot),
            batches = listOf(cucumber, tomato),
            records = listOf(
                harvestRecord(cucumber, yesterday),
                harvestRecord(tomato, yesterday)
            )
        )

        val harvestTasks = GardenAdvisor.todayTasks(state, weather = null, today = today)
            .filter { it.type == TaskType.HARVEST }

        assertTrue(harvestTasks.any { it.batchId == cucumber.id })
        assertFalse(harvestTasks.any { it.batchId == tomato.id })
    }

    @Test
    fun partialSingleHarvestKeepsRemindingUntilBatchIsFinished() {
        val today = plantingDate.plusDays(40)
        val lettuce = batch("lettuce", plantingDate, PlantingMethod.SEED)
        val state = GardenDataState(
            plots = listOf(plot),
            batches = listOf(lettuce),
            records = listOf(harvestRecord(lettuce, today.minusDays(5).atStartOfDay()))
        )

        val task = GardenAdvisor.todayTasks(state, weather = null, today = today)
            .firstOrNull { it.type == TaskType.HARVEST }

        assertTrue(task != null)
        assertTrue(task?.detail?.contains("部分采收") == true)
    }

    @Test
    fun cropPastConfiguredEndOnlyShowsLifecycleTask() {
        val tomato = batch("tomato", plantingDate, PlantingMethod.SEED)
        val today = plantingDate.plusDays(121)
        val tasks = GardenAdvisor.todayTasks(
            GardenDataState(plots = listOf(plot), batches = listOf(tomato)),
            weather = null,
            today = today
        ).filter { it.batchId == tomato.id }

        assertEquals(listOf(TaskType.LIFECYCLE), tasks.map { it.type })
        assertEquals("结束本茬", tasks.single().actionLabel)
    }

    @Test
    fun continuousHarvestAddsMaintenanceFeedingWindows() {
        val tomato = batch("tomato", plantingDate, PlantingMethod.SEED)
        val today = plantingDate.plusDays(93)
        val tasks = GardenAdvisor.todayTasks(
            GardenDataState(plots = listOf(plot), batches = listOf(tomato)),
            weather = null,
            today = today
        )

        assertTrue(
            tasks.any {
                it.batchId == tomato.id &&
                    it.type == TaskType.FERTILIZE &&
                    it.title.contains("采后恢复肥")
            }
        )
    }

    @Test
    fun temperatureAdjustmentUsesBaseTemperatureForInsightAndTasks() {
        val lettuce = batch("lettuce", plantingDate, PlantingMethod.SEED)
        val today = plantingDate.plusDays(35)
        val coldWeather = WeatherForecast(
            locationName = "测试",
            currentTempC = 4.0,
            humidityPercent = 60,
            windKmh = 5.0,
            daily = listOf(
                WeatherDay(today, maxTempC = 5.0, minTempC = 3.0, precipitationMm = 0.0)
            ),
            source = "测试"
        )
        val state = GardenDataState(plots = listOf(plot), batches = listOf(lettuce))
        val insight = GardenAdvisor.insightFor(state, lettuce, coldWeather, today)
        val tasks = GardenAdvisor.todayTasks(state, coldWeather, today)

        assertTrue(insight.adjustedDay < lettuceAge(today))
        assertFalse(tasks.any { it.batchId == lettuce.id && it.type == TaskType.HARVEST })
    }

    private fun insight(
        method: PlantingMethod,
        today: LocalDate,
        cropId: String = "tomato"
    ): PlantingInsight {
        val batch = PlantingBatch(
            id = "batch-${cropId}-${method.name}",
            plotId = plot.id,
            cropId = cropId,
            variety = "测试",
            method = method,
            startDate = plantingDate.toString(),
            quantityLabel = "1 株"
        )
        val state = GardenDataState(
            plots = listOf(plot),
            batches = listOf(batch)
        )
        return GardenAdvisor.insightFor(state, batch, weather = null, today = today)
    }

    private fun batch(
        cropId: String,
        startDate: LocalDate,
        method: PlantingMethod
    ): PlantingBatch {
        return PlantingBatch(
            id = "batch-$cropId",
            plotId = plot.id,
            cropId = cropId,
            variety = "测试",
            method = method,
            startDate = startDate.toString(),
            quantityLabel = "1 株"
        )
    }

    private fun harvestRecord(batch: PlantingBatch, timestamp: LocalDateTime): OperationRecord {
        return OperationRecord(
            id = "record-${batch.id}-$timestamp",
            batchId = batch.id,
            plotId = batch.plotId,
            type = OperationType.HARVEST,
            timestamp = timestamp.toString(),
            amountLabel = "部分采摘",
            note = "测试"
        )
    }

    private fun lettuceAge(today: LocalDate): Int {
        return java.time.temporal.ChronoUnit.DAYS.between(plantingDate, today).toInt()
    }
}
