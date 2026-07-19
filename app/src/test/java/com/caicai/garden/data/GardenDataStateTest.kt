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
}
