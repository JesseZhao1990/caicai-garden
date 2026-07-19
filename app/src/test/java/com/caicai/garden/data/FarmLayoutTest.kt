package com.caicai.garden.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FarmLayoutTest {
    @Test
    fun movingToEmptyCellKeepsTileData() {
        val tomato = FarmTile(
            row = 2,
            column = 1,
            type = FarmTileType.RAISED_BED,
            batchId = "tomato",
            rotationDegrees = 15f
        )
        val layout = FarmLayout(tiles = listOf(tomato))

        val moved = layout.moveTile(2, 1, 0, 0)

        assertEquals(listOf(tomato.copy(row = 0, column = 0)), moved.tiles)
    }

    @Test
    fun movingToOccupiedCellSwapsWithoutLosingEitherTile() {
        val tomato = FarmTile(
            row = 2,
            column = 1,
            type = FarmTileType.RAISED_BED,
            batchId = "tomato",
            rotationDegrees = 15f
        )
        val cucumber = FarmTile(
            row = 2,
            column = 3,
            type = FarmTileType.RAISED_BED,
            batchId = "cucumber",
            rotationDegrees = -15f
        )
        val sign = FarmTile(row = 2, column = 4, type = FarmTileType.SIGN)
        val layout = FarmLayout(tiles = listOf(sign, tomato, cucumber))

        val moved = layout.moveTile(2, 1, 2, 3)

        assertEquals(3, moved.tiles.size)
        assertEquals(
            tomato.copy(row = 2, column = 3),
            moved.tiles.single { it.batchId == "tomato" }
        )
        assertEquals(
            cucumber.copy(row = 2, column = 1),
            moved.tiles.single { it.batchId == "cucumber" }
        )
        assertEquals(sign, moved.tiles.single { it.type == FarmTileType.SIGN })
    }

    @Test
    fun invalidMoveReturnsSameLayout() {
        val layout = FarmLayout(
            tiles = listOf(FarmTile(2, 1, FarmTileType.RAISED_BED, batchId = "tomato"))
        )

        assertSame(layout, layout.moveTile(2, 1, 8, 0))
        assertSame(layout, layout.moveTile(0, 0, 1, 1))
        assertSame(layout, layout.moveTile(2, 1, 2, 1))
    }

    @Test
    fun newBatchIsPlacedInAnEmptyCellWithoutReplacingExistingTiles() {
        val sign = FarmTile(3, 3, FarmTileType.SIGN)
        val layout = FarmLayout(tiles = listOf(sign))

        val updated = layout.placeBatchInFirstEmptyCell("tomato")

        assertEquals(2, updated.tiles.size)
        assertEquals(sign, updated.tiles.single { it.type == FarmTileType.SIGN })
        assertEquals(
            "tomato",
            updated.tiles.single { it.type == FarmTileType.RAISED_BED }.batchId
        )
    }

    @Test
    fun fullLayoutDoesNotDiscardAnExistingTile() {
        val layout = FarmLayout(
            rows = 1,
            columns = 1,
            tiles = listOf(FarmTile(0, 0, FarmTileType.SIGN))
        )

        assertSame(layout, layout.placeBatchInFirstEmptyCell("tomato"))
    }

    @Test
    fun mapPlantingUsesTheCellChosenByTheUser() {
        val sign = FarmTile(2, 4, FarmTileType.SIGN)
        val layout = FarmLayout(tiles = listOf(sign))

        val updated = layout.placeBatchAtCell("tomato", row = 5, column = 1)

        assertEquals(2, updated.tiles.size)
        assertEquals(
            FarmTile(5, 1, FarmTileType.RAISED_BED, batchId = "tomato"),
            updated.tiles.single { it.batchId == "tomato" }
        )
        assertSame(layout, layout.placeBatchAtCell("tomato", row = 2, column = 4))
    }

    @Test
    fun finishingHarvestClearsEveryCellFromTheSameBatchOnly() {
        val layout = FarmLayout(
            tiles = listOf(
                FarmTile(2, 1, FarmTileType.RAISED_BED, batchId = "tomato"),
                FarmTile(3, 2, FarmTileType.RAISED_BED, batchId = "tomato"),
                FarmTile(5, 4, FarmTileType.RAISED_BED, batchId = "cucumber"),
                FarmTile(2, 4, FarmTileType.SIGN)
            )
        )

        val updated = layout.removeBatch("tomato")

        assertEquals(2, updated.tiles.size)
        assertEquals(setOf("cucumber"), updated.tiles.mapNotNull { it.batchId }.toSet())
        assertEquals(1, updated.tiles.count { it.type == FarmTileType.SIGN })
    }
}
