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
}
