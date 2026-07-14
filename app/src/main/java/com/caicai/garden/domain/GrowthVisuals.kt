package com.caicai.garden.domain

import com.caicai.garden.data.CropProfile
import kotlin.math.abs
import kotlin.math.pow

enum class CropSpriteStage(val assetName: String) {
    SEEDLING("seedling"),
    YOUNG("young"),
    MATURE("mature"),
    HARVEST("harvest")
}

enum class VisualGrowthCurve {
    SMOOTH,
    FAST_EARLY,
    ROSETTE,
    LATE_SWELL,
    UPRIGHT;

    fun transform(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return when (this) {
            SMOOTH -> t * t * (3f - 2f * t)
            FAST_EARLY -> 1f - (1f - t).pow(1.65f)
            ROSETTE -> 1f - (1f - t).pow(1.35f)
            LATE_SWELL -> t * t
            UPRIGHT -> t
        }
    }
}

data class CropStageVisual(
    val spriteStage: CropSpriteStage,
    val startCanopyTiles: Float,
    val endCanopyTiles: Float
)

data class CropVisualProfile(
    val cropId: String,
    val assetCropName: String,
    val heightScale: Float,
    val shadowWidthFactor: Float,
    val windFlex: Float,
    val growthCurve: VisualGrowthCurve,
    val stages: List<CropStageVisual>
)

data class CropVisualState(
    val assetCropName: String,
    val spriteStage: CropSpriteStage,
    val stageIndex: Int,
    val stageProgress: Float,
    val canopyWidthTiles: Float,
    val heightScale: Float,
    val shadowWidthFactor: Float,
    val windFlex: Float
)

object CropVisualLibrary {
    private val stageOrder = CropSpriteStage.values().toList()

    private fun profile(
        cropId: String,
        assetCropName: String,
        heightScale: Float,
        shadowWidthFactor: Float,
        windFlex: Float,
        growthCurve: VisualGrowthCurve,
        widths: List<Pair<Float, Float>>
    ): CropVisualProfile {
        require(widths.size == stageOrder.size) { "$cropId must define four visual stages" }
        return CropVisualProfile(
            cropId = cropId,
            assetCropName = assetCropName,
            heightScale = heightScale,
            shadowWidthFactor = shadowWidthFactor,
            windFlex = windFlex,
            growthCurve = growthCurve,
            stages = stageOrder.zip(widths).map { (spriteStage, range) ->
                CropStageVisual(
                    spriteStage = spriteStage,
                    startCanopyTiles = range.first,
                    endCanopyTiles = range.second
                )
            }
        )
    }

    val profiles: Map<String, CropVisualProfile> = listOf(
        profile(
            "tomato", "tomato", 1.15f, 0.62f, 1.08f, VisualGrowthCurve.SMOOTH,
            listOf(0.30f to 0.48f, 0.48f to 0.78f, 0.78f to 1.05f, 1.05f to 1.20f)
        ),
        profile(
            "cucumber", "cucumber", 1.20f, 0.60f, 1.08f, VisualGrowthCurve.FAST_EARLY,
            listOf(0.28f to 0.45f, 0.45f to 0.78f, 0.78f to 1.05f, 1.05f to 1.20f)
        ),
        profile(
            "pepper", "chili_pepper", 1.05f, 0.62f, 0.92f, VisualGrowthCurve.SMOOTH,
            listOf(0.28f to 0.44f, 0.44f to 0.72f, 0.72f to 0.98f, 0.98f to 1.12f)
        ),
        profile(
            "eggplant", "eggplant", 1.10f, 0.64f, 0.92f, VisualGrowthCurve.SMOOTH,
            listOf(0.30f to 0.48f, 0.48f to 0.80f, 0.80f to 1.08f, 1.08f to 1.25f)
        ),
        profile(
            "lettuce", "lettuce", 0.90f, 0.72f, 0.78f, VisualGrowthCurve.ROSETTE,
            listOf(0.22f to 0.30f, 0.30f to 0.50f, 0.50f to 0.78f, 0.78f to 0.95f)
        ),
        profile(
            "pakchoi", "bok_choy", 0.90f, 0.72f, 0.78f, VisualGrowthCurve.ROSETTE,
            listOf(0.22f to 0.30f, 0.30f to 0.48f, 0.48f to 0.74f, 0.74f to 0.90f)
        ),
        profile(
            "spinach", "spinach", 0.85f, 0.72f, 0.78f, VisualGrowthCurve.ROSETTE,
            listOf(0.20f to 0.28f, 0.28f to 0.44f, 0.44f to 0.68f, 0.68f to 0.82f)
        ),
        profile(
            "water_spinach", "water_spinach", 1.00f, 0.68f, 0.88f, VisualGrowthCurve.FAST_EARLY,
            listOf(0.24f to 0.38f, 0.38f to 0.58f, 0.58f to 0.80f, 0.80f to 1.05f)
        ),
        profile(
            "cilantro", "cilantro", 1.05f, 0.66f, 0.86f, VisualGrowthCurve.SMOOTH,
            listOf(0.20f to 0.28f, 0.28f to 0.44f, 0.44f to 0.65f, 0.65f to 0.78f)
        ),
        profile(
            "scallion", "scallion", 1.35f, 0.55f, 0.82f, VisualGrowthCurve.UPRIGHT,
            listOf(0.18f to 0.28f, 0.28f to 0.45f, 0.45f to 0.60f, 0.60f to 0.72f)
        ),
        profile(
            "chive", "chive", 1.20f, 0.60f, 0.82f, VisualGrowthCurve.UPRIGHT,
            listOf(0.20f to 0.30f, 0.30f to 0.48f, 0.48f to 0.65f, 0.65f to 0.82f)
        ),
        profile(
            "radish", "daikon", 0.95f, 0.66f, 0.82f, VisualGrowthCurve.LATE_SWELL,
            listOf(0.20f to 0.28f, 0.28f to 0.46f, 0.46f to 0.72f, 0.72f to 0.86f)
        ),
        profile(
            "carrot", "carrot", 1.05f, 0.60f, 0.82f, VisualGrowthCurve.LATE_SWELL,
            listOf(0.18f to 0.26f, 0.26f to 0.42f, 0.42f to 0.64f, 0.64f to 0.75f)
        ),
        profile(
            "yardlong_bean", "green_bean", 1.20f, 0.60f, 1.08f, VisualGrowthCurve.FAST_EARLY,
            listOf(0.28f to 0.45f, 0.45f to 0.78f, 0.78f to 1.05f, 1.05f to 1.20f)
        ),
        profile(
            "zucchini", "zucchini", 0.85f, 0.72f, 0.84f, VisualGrowthCurve.FAST_EARLY,
            listOf(0.32f to 0.50f, 0.50f to 0.84f, 0.84f to 1.12f, 1.12f to 1.25f)
        )
    ).associateBy(CropVisualProfile::cropId)

    fun forCrop(crop: CropProfile): CropVisualProfile {
        return requireNotNull(profiles[crop.id]) { "Missing visual profile for ${crop.id}" }
    }

    fun stateFor(crop: CropProfile, effectiveGrowthDay: Int): CropVisualState {
        val profile = forCrop(crop)
        require(crop.stages.size == profile.stages.size) {
            "${crop.id} has ${crop.stages.size} growth stages but ${profile.stages.size} visual stages"
        }
        val day = effectiveGrowthDay.coerceAtLeast(0)
        val stageIndex = crop.stages.indexOfFirst { day <= it.toDay }
            .takeIf { it >= 0 }
            ?: crop.stages.lastIndex
        val growthStage = crop.stages[stageIndex]
        val visualStage = profile.stages[stageIndex]
        val visualStageEndDay = if (stageIndex < crop.stages.lastIndex) {
            crop.stages[stageIndex + 1].fromDay
        } else {
            growthStage.toDay
        }
        val stageDuration = (visualStageEndDay - growthStage.fromDay).coerceAtLeast(1)
        val rawProgress = ((day - growthStage.fromDay).toFloat() / stageDuration.toFloat())
            .coerceIn(0f, 1f)
        val transformedProgress = profile.growthCurve.transform(rawProgress)
        val canopyWidth = visualStage.startCanopyTiles +
            (visualStage.endCanopyTiles - visualStage.startCanopyTiles) * transformedProgress

        return CropVisualState(
            assetCropName = profile.assetCropName,
            spriteStage = visualStage.spriteStage,
            stageIndex = stageIndex,
            stageProgress = rawProgress,
            canopyWidthTiles = canopyWidth,
            heightScale = profile.heightScale,
            shadowWidthFactor = profile.shadowWidthFactor,
            windFlex = profile.windFlex
        )
    }

    fun validationErrors(crops: List<CropProfile>): List<String> {
        return buildList {
            val cropIds = crops.map(CropProfile::id).toSet()
            val profileIds = profiles.keys
            (cropIds - profileIds).forEach { add("Missing visual profile for $it") }
            (profileIds - cropIds).forEach { add("Visual profile has no crop: $it") }

            crops.forEach { crop ->
                val profile = profiles[crop.id] ?: return@forEach
                if (crop.stages.size != 4) add("${crop.id} must define four growth stages")
                if (profile.stages.size != crop.stages.size) {
                    add("${crop.id} growth/visual stage count mismatch")
                }
                crop.stages.zipWithNext().forEach { (earlier, later) ->
                    if (earlier.toDay + 1 != later.fromDay) add("${crop.id} has a stage-day gap")
                }
                profile.stages.forEachIndexed { index, stage ->
                    if (stage.startCanopyTiles <= 0f || stage.endCanopyTiles < stage.startCanopyTiles) {
                        add("${crop.id} visual stage $index is not monotonic")
                    }
                    if (stage.endCanopyTiles > 1.25f) add("${crop.id} exceeds the 1.25 tile limit")
                }
                profile.stages.zipWithNext().forEachIndexed { index, (earlier, later) ->
                    if (abs(earlier.endCanopyTiles - later.startCanopyTiles) > 0.0001f) {
                        add("${crop.id} jumps in width between visual stages $index and ${index + 1}")
                    }
                }
            }
        }
    }
}
