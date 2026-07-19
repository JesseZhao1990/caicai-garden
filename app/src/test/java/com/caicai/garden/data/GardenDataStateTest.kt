package com.caicai.garden.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenDataStateTest {
    @Test
    fun eachPlotOwnsAnIndependentFarmLayout() {
        val firstLayout = FarmLayout(
            tiles = listOf(FarmTile(2, 1, FarmTileType.RAISED_BED, batchId = "tomato"))
        )
        val secondLayout = FarmLayout(
            tiles = listOf(FarmTile(4, 3, FarmTileType.RAISED_BED, batchId = "cucumber"))
        )
        val state = GardenDataState(
            farmLayouts = mapOf(
                "plot-1" to firstLayout,
                "plot-2" to secondLayout
            )
        )

        val movedFirstLayout = firstLayout.moveTile(2, 1, 6, 5)
        val updated = state.withFarmLayout("plot-1", movedFirstLayout)

        assertEquals(movedFirstLayout, updated.farmLayoutFor("plot-1"))
        assertEquals(secondLayout, updated.farmLayoutFor("plot-2"))
        assertTrue(updated.farmLayoutFor("missing").tiles.isEmpty())
    }

    @Test
    fun farmLayoutNeverReturnsLegacyWoodenSigns() {
        val state = GardenDataState(
            farmLayouts = mapOf(
                "plot-1" to FarmLayout(
                    tiles = listOf(
                        FarmTile(2, 4, FarmTileType.SIGN),
                        FarmTile(3, 3, FarmTileType.RAISED_BED, batchId = "tomato")
                    )
                )
            )
        )

        val layout = state.farmLayoutFor("plot-1")

        assertEquals(1, layout.tiles.size)
        assertEquals("tomato", layout.tiles.single().batchId)
    }

    @Test
    fun deletingPlotEndsItsBatchesAndKeepsHistory() {
        val plot = Plot(
            id = "plot-1",
            gardenId = "garden-1",
            name = "1 号畦",
            sizeLabel = "3 平米",
            growingStyle = GrowingStyle.OPEN_FIELD,
            soilType = "壤土"
        )
        val batch = PlantingBatch(
            id = "batch-1",
            plotId = plot.id,
            cropId = "tomato",
            variety = "",
            method = PlantingMethod.TRANSPLANT,
            startDate = "2026-07-01",
            quantityLabel = "1 株"
        )
        val record = OperationRecord(
            id = "record-1",
            batchId = batch.id,
            plotId = plot.id,
            type = OperationType.WATER,
            timestamp = "2026-07-18T08:00:00",
            amountLabel = "浇透",
            note = ""
        )
        val state = GardenDataState(
            plots = listOf(plot),
            batches = listOf(batch),
            records = listOf(record),
            farmLayouts = mapOf(plot.id to FarmLayout())
        )

        val updated = state.withoutPlot(plot.id)

        assertTrue(updated.plots.isEmpty())
        assertEquals(BatchStatus.FINISHED, updated.batches.single().status)
        assertEquals(listOf(record), updated.records)
        assertTrue(plot.id !in updated.farmLayouts)
    }
}
