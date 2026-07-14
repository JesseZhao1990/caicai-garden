package com.caicai.garden.domain

import com.caicai.garden.data.CropLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthVisualsTest {
    @Test
    fun everyCropHasFourContinuousVisualStages() {
        assertEquals(emptyList<String>(), CropVisualLibrary.validationErrors(CropLibrary.crops))
    }

    @Test
    fun everyCropGrowsEachDayUntilItsConfiguredEndDay() {
        CropLibrary.crops.forEach { crop ->
            val widths = (0..crop.harvestEndDay).map { day ->
                CropVisualLibrary.stateFor(crop, day).canopyWidthTiles
            }
            assertTrue(
                "${crop.name} should grow every day",
                widths.zipWithNext().all { (earlier, later) -> later > earlier }
            )
            val afterEnd = CropVisualLibrary.stateFor(crop, crop.harvestEndDay + 30)
            assertEquals(widths.last(), afterEnd.canopyWidthTiles, 0.0001f)
        }
    }

    @Test
    fun eggplantSwitchesToFruitAtItsOwnHarvestStart() {
        val eggplant = CropLibrary.byId("eggplant")
        val day74 = CropVisualLibrary.stateFor(eggplant, 74)
        val day75 = CropVisualLibrary.stateFor(eggplant, 75)
        val day104 = CropVisualLibrary.stateFor(eggplant, 104)

        assertEquals(CropSpriteStage.MATURE, day74.spriteStage)
        assertEquals(CropSpriteStage.HARVEST, day75.spriteStage)
        assertEquals(1.08f, day75.canopyWidthTiles, 0.0001f)
        assertEquals(CropSpriteStage.HARVEST, day104.spriteStage)
        assertEquals(1.17f, day104.canopyWidthTiles, 0.02f)
    }

    @Test
    fun keyFruitCropsUseTheirOwnHarvestBoundaries() {
        val tomato = CropLibrary.byId("tomato")
        val cucumber = CropLibrary.byId("cucumber")

        assertEquals(CropSpriteStage.MATURE, CropVisualLibrary.stateFor(tomato, 74).spriteStage)
        assertEquals(CropSpriteStage.HARVEST, CropVisualLibrary.stateFor(tomato, 75).spriteStage)
        assertEquals(CropSpriteStage.MATURE, CropVisualLibrary.stateFor(cucumber, 41).spriteStage)
        assertEquals(CropSpriteStage.HARVEST, CropVisualLibrary.stateFor(cucumber, 42).spriteStage)
    }

    @Test
    fun waterSpinachAndChiveUseIndependentAssetDirectories() {
        val waterSpinach = CropVisualLibrary.forCrop(CropLibrary.byId("water_spinach"))
        val chive = CropVisualLibrary.forCrop(CropLibrary.byId("chive"))

        assertEquals("water_spinach", waterSpinach.assetCropName)
        assertEquals("chive", chive.assetCropName)
    }
}
