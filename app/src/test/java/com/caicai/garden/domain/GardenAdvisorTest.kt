package com.caicai.garden.domain

import com.caicai.garden.data.CropLibrary
import com.caicai.garden.data.FarmLayout
import com.caicai.garden.data.GardenDataState
import com.caicai.garden.data.GrowingStyle
import com.caicai.garden.data.PlantingBatch
import com.caicai.garden.data.PlantingMethod
import com.caicai.garden.data.Plot
import com.caicai.garden.data.growthOffsetDays
import java.time.LocalDate
import org.junit.Assert.assertEquals
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

    private fun insight(method: PlantingMethod, today: LocalDate): PlantingInsight {
        val batch = PlantingBatch(
            id = "batch-${method.name}",
            plotId = plot.id,
            cropId = "tomato",
            variety = "测试",
            method = method,
            startDate = plantingDate.toString(),
            quantityLabel = "1 株"
        )
        val state = GardenDataState(
            plots = listOf(plot),
            batches = listOf(batch),
            farmLayout = FarmLayout()
        )
        return GardenAdvisor.insightFor(state, batch, weather = null, today = today)
    }
}
