package com.caicai.garden.data

import java.time.LocalDate
import kotlin.math.abs

enum class GrowingStyle(val label: String) {
    OPEN_FIELD("露地"),
    POT("盆栽"),
    GREENHOUSE("大棚"),
    BALCONY("阳台")
}

enum class PlantingMethod(
    val label: String,
    val materialLabel: String,
    val dateLabel: String,
    val predictionHint: String,
    val quantityUnit: String,
    val maxQuantity: Int
) {
    SEED("种子播种", "种子", "播种日期", "从发芽第 0 天开始估算", "粒", 999),
    TRANSPLANT("种苗移栽", "种苗", "移栽日期", "按该作物常见苗龄估算起始长势", "株", 99),
    CUTTING("插条扦插", "插条", "扦插日期", "从生根缓苗阶段开始估算", "根", 99),
    DIVISION("分株定植", "分株", "分株日期", "从分株缓苗阶段开始估算", "丛", 99),
    BULB("鳞茎定植", "鳞茎/蒜瓣", "定植日期", "从鳞茎萌芽阶段开始估算", "瓣", 99),
    TUBER("块茎种植", "种薯/块茎", "种植日期", "从块茎萌芽阶段开始估算", "块", 99)
}

enum class BatchStatus(val label: String) {
    ACTIVE("生长中"),
    ATTENTION("需关注"),
    FINISHED("已结束")
}

enum class OperationType(val label: String) {
    WATER("浇水"),
    FERTILIZE("施肥"),
    SOW("播种"),
    TRANSPLANT("移栽"),
    CUTTING("扦插"),
    DIVISION("分株"),
    BULB_PLANT("鳞茎定植"),
    TUBER_PLANT("块茎种植"),
    WEED("除草"),
    PRUNE("修剪"),
    SUPPORT("搭架"),
    PEST_CONTROL("防虫"),
    HARVEST("采摘"),
    PHOTO("拍照"),
    NOTE("记录")
}

enum class TaskType(val label: String) {
    WATER("浇水"),
    FERTILIZE("施肥"),
    HARVEST("采摘"),
    WEATHER("天气"),
    CHECK("检查"),
    PHOTO("拍照"),
    LIFECYCLE("收尾")
}

enum class TaskPriority(val label: String) {
    HIGH("高"),
    MEDIUM("中"),
    LOW("低")
}

enum class FarmTileType(val label: String) {
    GRASS("草地"),
    PATH("石板路"),
    RAISED_BED("菜畦"),
    GREENHOUSE("小温室"),
    TOOL_SHED("农具棚"),
    FENCE("木栅栏"),
    IRRIGATION("灌溉"),
    SIGN("标识牌")
}

data class Garden(
    val id: String,
    val name: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double
)

data class Plot(
    val id: String,
    val gardenId: String,
    val name: String,
    val sizeLabel: String,
    val growingStyle: GrowingStyle,
    val soilType: String,
    val notes: String = ""
)

enum class StageWaterDemand {
    NORMAL,
    HIGH
}

data class GrowthStage(
    val name: String,
    val fromDay: Int,
    val toDay: Int,
    val focus: String,
    val waterDemand: StageWaterDemand = StageWaterDemand.NORMAL
)

data class CropProfile(
    val id: String,
    val name: String,
    val category: String,
    val baseTemperature: Double,
    val minGoodTemperature: Double,
    val maxGoodTemperature: Double,
    val waterNeed: Int,
    val feedingDays: List<Int>,
    val harvestStartDay: Int,
    val harvestEndDay: Int,
    val continuousHarvest: Boolean,
    val harvestIntervalDays: Int,
    val maintenanceFeedingIntervalDays: Int? = null,
    val supportedPlantingMethods: List<PlantingMethod>,
    val stages: List<GrowthStage>,
    val careTips: List<String>,
    val transplantGrowthOffsetDays: Int = 10,
    val cuttingGrowthOffsetDays: Int = 5,
    val divisionGrowthOffsetDays: Int = 7,
    val bulbGrowthOffsetDays: Int = 5,
    val tuberGrowthOffsetDays: Int = 5
) {
    init {
        require(supportedPlantingMethods.isNotEmpty()) {
            "$name 至少需要一种可用的种植方式"
        }
        require(supportedPlantingMethods.distinct().size == supportedPlantingMethods.size) {
            "$name 的种植方式不能重复"
        }
        require(harvestIntervalDays > 0) {
            "$name 的采摘提醒间隔必须大于 0"
        }
        require(maintenanceFeedingIntervalDays == null || maintenanceFeedingIntervalDays >= 7) {
            "$name 的连续采收追肥间隔不能小于 7 天"
        }
        require(stages.size == 4 && stages.first().fromDay == 0) {
            "$name 必须从第 0 天开始配置四个连续生长阶段"
        }
        require(stages.zipWithNext().all { (earlier, later) -> earlier.toDay + 1 == later.fromDay }) {
            "$name 的生长阶段日期必须连续"
        }
        require(stages.last().fromDay == harvestStartDay && stages.last().toDay == harvestEndDay) {
            "$name 的最后生长阶段必须与采摘窗口一致"
        }
        require(feedingDays == feedingDays.distinct().sorted() && feedingDays.all { it in 1..harvestEndDay }) {
            "$name 的施肥节点必须有序、唯一并位于生命周期内"
        }
        require(!continuousHarvest || maintenanceFeedingIntervalDays != null) {
            "$name 是连续采收作物，需要配置采收期追肥间隔"
        }
    }
}

fun PlantingMethod.growthOffsetDays(crop: CropProfile): Int {
    return when (this) {
        PlantingMethod.SEED -> 0
        PlantingMethod.TRANSPLANT -> crop.transplantGrowthOffsetDays
        PlantingMethod.CUTTING -> crop.cuttingGrowthOffsetDays
        PlantingMethod.DIVISION -> crop.divisionGrowthOffsetDays
        PlantingMethod.BULB -> crop.bulbGrowthOffsetDays
        PlantingMethod.TUBER -> crop.tuberGrowthOffsetDays
    }
}

data class PlantingBatch(
    val id: String,
    val plotId: String,
    val cropId: String,
    val variety: String,
    val method: PlantingMethod,
    val startDate: String,
    val quantityLabel: String,
    val status: BatchStatus = BatchStatus.ACTIVE
) {
    fun startLocalDate(): LocalDate = LocalDate.parse(startDate)
}

data class OperationRecord(
    val id: String,
    val batchId: String?,
    val plotId: String?,
    val type: OperationType,
    val timestamp: String,
    val amountLabel: String,
    val note: String
)

data class FarmTile(
    val row: Int,
    val column: Int,
    val type: FarmTileType,
    val batchId: String? = null,
    val rotationDegrees: Float = defaultFarmTileRotation(row, column, type)
)

@Suppress("UNUSED_PARAMETER")
fun defaultFarmTileRotation(row: Int, column: Int, type: FarmTileType): Float {
    return 0f
}

fun normalizeFarmTileRotation(degrees: Float): Float {
    val normalized = degrees % 360f
    return if (normalized < -180f) normalized + 360f else if (normalized > 180f) normalized - 360f else normalized
}

data class FarmLayout(
    val rows: Int = 8,
    val columns: Int = 8,
    val tiles: List<FarmTile> = emptyList()
)

fun FarmLayout.moveTile(
    fromRow: Int,
    fromColumn: Int,
    toRow: Int,
    toColumn: Int
): FarmLayout {
    if (fromRow !in 0 until rows || fromColumn !in 0 until columns) return this
    if (toRow !in 0 until rows || toColumn !in 0 until columns) return this
    if (fromRow == toRow && fromColumn == toColumn) return this

    val movingIndex = tiles.indexOfFirst { it.row == fromRow && it.column == fromColumn }
    if (movingIndex == -1) return this
    val targetIndex = tiles.indexOfFirst { it.row == toRow && it.column == toColumn }

    return copy(
        tiles = tiles.mapIndexed { index, tile ->
            when (index) {
                movingIndex -> tile.copy(row = toRow, column = toColumn)
                targetIndex -> tile.copy(row = fromRow, column = fromColumn)
                else -> tile
            }
        }
    )
}

fun FarmLayout.placeBatchInFirstEmptyCell(batchId: String): FarmLayout {
    if (batchId.isBlank() || rows <= 0 || columns <= 0) return this
    val occupiedCells = tiles.mapTo(mutableSetOf()) { it.row to it.column }
    val centerRow = (rows - 1) / 2f
    val centerColumn = (columns - 1) / 2f
    val targetCell = (0 until rows)
        .flatMap { row -> (0 until columns).map { column -> row to column } }
        .asSequence()
        .filterNot(occupiedCells::contains)
        .minWithOrNull(
            compareBy<Pair<Int, Int>> {
                abs(it.first - centerRow) + abs(it.second - centerColumn)
            }.thenBy { it.first + it.second }
                .thenBy { it.first }
                .thenBy { it.second }
        )
        ?: return this

    return copy(
        tiles = tiles + FarmTile(
            row = targetCell.first,
            column = targetCell.second,
            type = FarmTileType.RAISED_BED,
            batchId = batchId
        )
    )
}

fun FarmLayout.placeBatchAtCell(batchId: String, row: Int, column: Int): FarmLayout {
    if (batchId.isBlank()) return this
    if (row !in 0 until rows || column !in 0 until columns) return this
    if (tiles.any { it.row == row && it.column == column }) return this
    return copy(
        tiles = tiles + FarmTile(
            row = row,
            column = column,
            type = FarmTileType.RAISED_BED,
            batchId = batchId
        )
    )
}

fun FarmLayout.removeBatch(batchId: String): FarmLayout {
    if (batchId.isBlank()) return this
    val nextTiles = tiles.filterNot { it.batchId == batchId }
    return if (nextTiles.size == tiles.size) this else copy(tiles = nextTiles)
}

data class GardenDataState(
    val gardens: List<Garden> = emptyList(),
    val plots: List<Plot> = emptyList(),
    val batches: List<PlantingBatch> = emptyList(),
    val records: List<OperationRecord> = emptyList(),
    val farmLayouts: Map<String, FarmLayout> = emptyMap()
)

fun GardenDataState.farmLayoutFor(plotId: String?): FarmLayout {
    return plotId?.let(farmLayouts::get) ?: FarmLayout()
}

fun GardenDataState.withFarmLayout(plotId: String, layout: FarmLayout): GardenDataState {
    return copy(farmLayouts = farmLayouts + (plotId to layout))
}

data class WeatherDay(
    val date: LocalDate,
    val maxTempC: Double,
    val minTempC: Double,
    val precipitationMm: Double
)

data class WeatherForecast(
    val locationName: String,
    val currentTempC: Double,
    val humidityPercent: Int,
    val windKmh: Double,
    val daily: List<WeatherDay>,
    val source: String,
    val isFallback: Boolean = false
) {
    val today: WeatherDay?
        get() = daily.firstOrNull()
}

data class TaskReminder(
    val id: String,
    val batchId: String?,
    val plotId: String?,
    val type: TaskType,
    val priority: TaskPriority,
    val dueDate: LocalDate,
    val title: String,
    val detail: String,
    val actionLabel: String
)

object CropLibrary {
    val crops: List<CropProfile> = listOf(
        CropProfile(
            id = "tomato",
            name = "番茄",
            category = "茄果类",
            baseTemperature = 10.0,
            minGoodTemperature = 18.0,
            maxGoodTemperature = 32.0,
            waterNeed = 4,
            feedingDays = listOf(18, 35, 55, 75),
            harvestStartDay = 75,
            harvestEndDay = 120,
            continuousHarvest = true,
            harvestIntervalDays = 3,
            maintenanceFeedingIntervalDays = 18,
            supportedPlantingMethods = listOf(PlantingMethod.TRANSPLANT, PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("缓苗/幼苗期", 0, 20, "保持土壤微湿，避免大水漫灌"),
                GrowthStage("营养生长期", 21, 40, "促进根系和枝叶生长，及时搭架"),
                GrowthStage("开花坐果期", 41, 74, "稳定水分，减少落花，注意补充磷钾", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 75, 120, "成熟果及时采收，维持水肥供应", StageWaterDemand.HIGH)
            ),
            careTips = listOf("坐果期避免忽干忽湿", "雨后注意通风和病害检查"),
            transplantGrowthOffsetDays = 14
        ),
        CropProfile(
            id = "cucumber",
            name = "黄瓜",
            category = "瓜类",
            baseTemperature = 12.0,
            minGoodTemperature = 20.0,
            maxGoodTemperature = 32.0,
            waterNeed = 5,
            feedingDays = listOf(12, 25, 38, 52),
            harvestStartDay = 42,
            harvestEndDay = 75,
            continuousHarvest = true,
            harvestIntervalDays = 1,
            maintenanceFeedingIntervalDays = 12,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("幼苗期", 0, 14, "少量多次补水，防止徒长"),
                GrowthStage("伸蔓期", 15, 30, "及时搭架引蔓，保持土壤湿润"),
                GrowthStage("开花结果期", 31, 41, "需水需肥增加，关注高温和授粉", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 42, 75, "1-2 天巡查一次，避免瓜条过老", StageWaterDemand.HIGH)
            ),
            careTips = listOf("结果期缺水会影响口感", "高温天优先安排傍晚浇水"),
            transplantGrowthOffsetDays = 10
        ),
        CropProfile(
            id = "pepper",
            name = "辣椒",
            category = "茄果类",
            baseTemperature = 12.0,
            minGoodTemperature = 20.0,
            maxGoodTemperature = 32.0,
            waterNeed = 3,
            feedingDays = listOf(20, 40, 60),
            harvestStartDay = 70,
            harvestEndDay = 120,
            continuousHarvest = true,
            harvestIntervalDays = 3,
            maintenanceFeedingIntervalDays = 18,
            supportedPlantingMethods = listOf(PlantingMethod.TRANSPLANT, PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("缓苗/幼苗期", 0, 20, "控制浇水，促进根系恢复"),
                GrowthStage("分枝期", 21, 45, "适度追肥，保持通风"),
                GrowthStage("开花结果期", 46, 69, "避免高温干旱，关注落花", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 70, 120, "成熟果分批采摘，减少植株负担", StageWaterDemand.HIGH)
            ),
            careTips = listOf("忌长期积水", "结果期可少量多次追肥"),
            transplantGrowthOffsetDays = 16
        ),
        CropProfile(
            id = "eggplant",
            name = "茄子",
            category = "茄果类",
            baseTemperature = 12.0,
            minGoodTemperature = 20.0,
            maxGoodTemperature = 33.0,
            waterNeed = 4,
            feedingDays = listOf(22, 42, 62),
            harvestStartDay = 75,
            harvestEndDay = 130,
            continuousHarvest = true,
            harvestIntervalDays = 3,
            maintenanceFeedingIntervalDays = 18,
            supportedPlantingMethods = listOf(PlantingMethod.TRANSPLANT, PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("缓苗期", 0, 18, "少量补水，避免伤根"),
                GrowthStage("营养生长期", 19, 45, "促进枝叶和根系生长"),
                GrowthStage("开花坐果期", 46, 74, "保持水肥均衡，减少落花", StageWaterDemand.HIGH),
                GrowthStage("采收期", 75, 130, "果实膨大期保持稳定水分", StageWaterDemand.HIGH)
            ),
            careTips = listOf("门茄坐住后需肥量上升", "干旱后猛浇水容易裂果"),
            transplantGrowthOffsetDays = 14
        ),
        CropProfile(
            id = "lettuce",
            name = "生菜",
            category = "叶菜",
            baseTemperature = 5.0,
            minGoodTemperature = 12.0,
            maxGoodTemperature = 25.0,
            waterNeed = 4,
            feedingDays = listOf(12, 24),
            harvestStartDay = 35,
            harvestEndDay = 55,
            continuousHarvest = false,
            harvestIntervalDays = 4,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("发芽期", 0, 6, "保持表土湿润"),
                GrowthStage("幼苗期", 7, 18, "适度控水，避免徒长"),
                GrowthStage("莲座期", 19, 34, "保持水肥充足，叶片快速展开", StageWaterDemand.HIGH),
                GrowthStage("采收期", 35, 55, "达到食用大小即可采收", StageWaterDemand.HIGH)
            ),
            careTips = listOf("高温容易抽薹", "叶菜采前避免过量施肥"),
            transplantGrowthOffsetDays = 12
        ),
        CropProfile(
            id = "pakchoi",
            name = "小白菜",
            category = "叶菜",
            baseTemperature = 5.0,
            minGoodTemperature = 15.0,
            maxGoodTemperature = 28.0,
            waterNeed = 4,
            feedingDays = listOf(10, 22),
            harvestStartDay = 30,
            harvestEndDay = 45,
            continuousHarvest = false,
            harvestIntervalDays = 3,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("发芽期", 0, 5, "保持土壤湿润"),
                GrowthStage("幼苗期", 6, 15, "间苗后轻水轻肥"),
                GrowthStage("旺长期", 16, 29, "保持水肥充足，促进叶片生长", StageWaterDemand.HIGH),
                GrowthStage("采收期", 30, 45, "可分批采收外层叶或整株采收", StageWaterDemand.HIGH)
            ),
            careTips = listOf("干旱会影响叶片口感", "雨后注意菜心积水"),
            transplantGrowthOffsetDays = 10
        ),
        CropProfile(
            id = "spinach",
            name = "菠菜",
            category = "叶菜",
            baseTemperature = 4.0,
            minGoodTemperature = 10.0,
            maxGoodTemperature = 24.0,
            waterNeed = 3,
            feedingDays = listOf(14, 28),
            harvestStartDay = 35,
            harvestEndDay = 55,
            continuousHarvest = false,
            harvestIntervalDays = 4,
            supportedPlantingMethods = listOf(PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("发芽期", 0, 7, "保持湿润，避免板结"),
                GrowthStage("幼苗期", 8, 20, "间苗并保持通风"),
                GrowthStage("旺长期", 21, 34, "适度追肥，保持叶片生长", StageWaterDemand.HIGH),
                GrowthStage("采收期", 35, 55, "叶片达到食用大小即可采收", StageWaterDemand.HIGH)
            ),
            careTips = listOf("高温季节容易抽薹", "采收前保持清洁浇水"),
            transplantGrowthOffsetDays = 10
        ),
        CropProfile(
            id = "water_spinach",
            name = "空心菜",
            category = "叶菜",
            baseTemperature = 12.0,
            minGoodTemperature = 22.0,
            maxGoodTemperature = 35.0,
            waterNeed = 5,
            feedingDays = listOf(12, 25, 38),
            harvestStartDay = 30,
            harvestEndDay = 80,
            continuousHarvest = true,
            harvestIntervalDays = 7,
            maintenanceFeedingIntervalDays = 14,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.CUTTING),
            stages = listOf(
                GrowthStage("缓苗/发芽期", 0, 10, "保持湿润，促进快速生根"),
                GrowthStage("快速生长期", 11, 20, "水肥充足，促进嫩梢生长", StageWaterDemand.HIGH),
                GrowthStage("成熟旺长期", 21, 29, "维持水肥，促进侧枝和可采嫩梢形成", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 30, 80, "采嫩梢后补水补肥，促发侧枝", StageWaterDemand.HIGH)
            ),
            careTips = listOf("喜水耐热", "采后及时追肥可延长采收期"),
            transplantGrowthOffsetDays = 8,
            cuttingGrowthOffsetDays = 6
        ),
        CropProfile(
            id = "cilantro",
            name = "香菜",
            category = "香辛菜",
            baseTemperature = 5.0,
            minGoodTemperature = 15.0,
            maxGoodTemperature = 25.0,
            waterNeed = 3,
            feedingDays = listOf(18, 32),
            harvestStartDay = 40,
            harvestEndDay = 60,
            continuousHarvest = false,
            harvestIntervalDays = 4,
            supportedPlantingMethods = listOf(PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("发芽期", 0, 12, "出苗慢，保持表土湿润"),
                GrowthStage("幼苗期", 13, 25, "适度间苗，避免过密"),
                GrowthStage("旺长期", 26, 39, "保持水肥均衡", StageWaterDemand.HIGH),
                GrowthStage("采收期", 40, 60, "株高合适即可采收", StageWaterDemand.HIGH)
            ),
            careTips = listOf("高温容易抽薹", "种子可轻搓破壳提高出苗"),
            transplantGrowthOffsetDays = 12
        ),
        CropProfile(
            id = "scallion",
            name = "葱",
            category = "葱蒜类",
            baseTemperature = 5.0,
            minGoodTemperature = 15.0,
            maxGoodTemperature = 28.0,
            waterNeed = 3,
            feedingDays = listOf(20, 40, 60),
            harvestStartDay = 60,
            harvestEndDay = 110,
            continuousHarvest = true,
            harvestIntervalDays = 7,
            maintenanceFeedingIntervalDays = 20,
            supportedPlantingMethods = listOf(
                PlantingMethod.TRANSPLANT,
                PlantingMethod.SEED,
                PlantingMethod.DIVISION
            ),
            stages = listOf(
                GrowthStage("缓苗期", 0, 14, "保持土壤微湿"),
                GrowthStage("分蘖期", 15, 45, "适度追肥，促进分蘖"),
                GrowthStage("旺长期", 46, 59, "保持水肥，适当培土", StageWaterDemand.HIGH),
                GrowthStage("采收期", 60, 110, "按需采收，采后促发新叶", StageWaterDemand.HIGH)
            ),
            careTips = listOf("不耐长期积水", "采收后可轻追肥"),
            transplantGrowthOffsetDays = 14
        ),
        CropProfile(
            id = "chive",
            name = "韭菜",
            category = "多年生叶菜",
            baseTemperature = 5.0,
            minGoodTemperature = 15.0,
            maxGoodTemperature = 28.0,
            waterNeed = 3,
            feedingDays = listOf(20, 45, 70),
            harvestStartDay = 60,
            harvestEndDay = 150,
            continuousHarvest = true,
            harvestIntervalDays = 20,
            maintenanceFeedingIntervalDays = 25,
            supportedPlantingMethods = listOf(
                PlantingMethod.DIVISION,
                PlantingMethod.TRANSPLANT,
                PlantingMethod.SEED
            ),
            stages = listOf(
                GrowthStage("缓苗期", 0, 20, "少割少动，促进根系建立"),
                GrowthStage("快速生长期", 21, 40, "保持水肥，促进叶片健壮", StageWaterDemand.HIGH),
                GrowthStage("成熟蓄养期", 41, 59, "继续养根壮叶，为首次采收积累长势"),
                GrowthStage("连续采收期", 60, 150, "割后追肥补水，留足恢复时间", StageWaterDemand.HIGH)
            ),
            careTips = listOf("割后不要立刻大水漫灌", "长期采收需补充有机肥"),
            transplantGrowthOffsetDays = 14,
            divisionGrowthOffsetDays = 7
        ),
        CropProfile(
            id = "radish",
            name = "萝卜",
            category = "根菜",
            baseTemperature = 5.0,
            minGoodTemperature = 15.0,
            maxGoodTemperature = 28.0,
            waterNeed = 3,
            feedingDays = listOf(18, 35),
            harvestStartDay = 55,
            harvestEndDay = 80,
            continuousHarvest = false,
            harvestIntervalDays = 5,
            supportedPlantingMethods = listOf(PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("发芽期", 0, 6, "保持土壤湿润"),
                GrowthStage("幼苗期", 7, 20, "间苗定苗，避免过密"),
                GrowthStage("肉质根膨大期", 21, 54, "水分稳定，少施过量氮肥", StageWaterDemand.HIGH),
                GrowthStage("采收期", 55, 80, "达到品种大小后及时采收")
            ),
            careTips = listOf("忽干忽湿易裂根", "过量氮肥会影响根膨大"),
            transplantGrowthOffsetDays = 8
        ),
        CropProfile(
            id = "carrot",
            name = "胡萝卜",
            category = "根菜",
            baseTemperature = 5.0,
            minGoodTemperature = 16.0,
            maxGoodTemperature = 26.0,
            waterNeed = 3,
            feedingDays = listOf(25, 45),
            harvestStartDay = 80,
            harvestEndDay = 110,
            continuousHarvest = false,
            harvestIntervalDays = 7,
            supportedPlantingMethods = listOf(PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("发芽期", 0, 12, "出苗较慢，保持表土湿润"),
                GrowthStage("幼苗期", 13, 35, "间苗并保持土壤疏松"),
                GrowthStage("肉质根膨大期", 36, 79, "水分稳定，避免板结", StageWaterDemand.HIGH),
                GrowthStage("采收期", 80, 110, "根肩明显膨大后采收")
            ),
            careTips = listOf("土壤过硬会影响根形", "忌鲜肥过多"),
            transplantGrowthOffsetDays = 10
        ),
        CropProfile(
            id = "yardlong_bean",
            name = "豆角",
            category = "豆类",
            baseTemperature = 12.0,
            minGoodTemperature = 20.0,
            maxGoodTemperature = 32.0,
            waterNeed = 4,
            feedingDays = listOf(20, 38, 55),
            harvestStartDay = 55,
            harvestEndDay = 95,
            continuousHarvest = true,
            harvestIntervalDays = 2,
            maintenanceFeedingIntervalDays = 18,
            supportedPlantingMethods = listOf(PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("幼苗期", 0, 18, "控制水分，促根壮苗"),
                GrowthStage("伸蔓期", 19, 35, "及时搭架，引蔓上架"),
                GrowthStage("开花结荚期", 36, 54, "保持水分稳定，少量追肥", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 55, 95, "嫩荚及时采摘，促进继续开花", StageWaterDemand.HIGH)
            ),
            careTips = listOf("开花期避免大水冲灌", "采收不及时会影响后续结荚"),
            transplantGrowthOffsetDays = 12
        ),
        CropProfile(
            id = "zucchini",
            name = "西葫芦",
            category = "瓜类",
            baseTemperature = 10.0,
            minGoodTemperature = 18.0,
            maxGoodTemperature = 30.0,
            waterNeed = 4,
            feedingDays = listOf(18, 32, 48),
            harvestStartDay = 45,
            harvestEndDay = 80,
            continuousHarvest = true,
            harvestIntervalDays = 2,
            maintenanceFeedingIntervalDays = 14,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("幼苗期", 0, 16, "少量多次补水，避免低温积水"),
                GrowthStage("营养生长期", 17, 32, "保持水肥，促进株型展开"),
                GrowthStage("开花结果期", 33, 44, "关注授粉和水分稳定", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 45, 80, "嫩瓜及时采收，防止过大消耗", StageWaterDemand.HIGH)
            ),
            careTips = listOf("连阴雨需关注授粉", "采摘越及时越利于连续结果"),
            transplantGrowthOffsetDays = 12
        ),
        CropProfile(
            id = "basil",
            name = "罗勒",
            category = "香草",
            baseTemperature = 10.0,
            minGoodTemperature = 18.0,
            maxGoodTemperature = 30.0,
            waterNeed = 3,
            feedingDays = listOf(18, 35),
            harvestStartDay = 40,
            harvestEndDay = 120,
            continuousHarvest = true,
            harvestIntervalDays = 7,
            maintenanceFeedingIntervalDays = 21,
            supportedPlantingMethods = listOf(
                PlantingMethod.SEED,
                PlantingMethod.TRANSPLANT,
                PlantingMethod.CUTTING
            ),
            stages = listOf(
                GrowthStage("发芽/缓苗期", 0, 10, "保持温暖湿润，避免积水"),
                GrowthStage("幼苗期", 11, 25, "见干见湿，促进根系生长"),
                GrowthStage("分枝旺长期", 26, 39, "及时摘心，促进侧枝", StageWaterDemand.HIGH),
                GrowthStage("连续采叶期", 40, 120, "分批采叶，保留足够叶片恢复", StageWaterDemand.HIGH)
            ),
            careTips = listOf("摘心可促进分枝", "开花前采叶香气更浓"),
            transplantGrowthOffsetDays = 12,
            cuttingGrowthOffsetDays = 7
        ),
        CropProfile(
            id = "bell_pepper",
            name = "甜椒",
            category = "茄果类",
            baseTemperature = 12.0,
            minGoodTemperature = 20.0,
            maxGoodTemperature = 30.0,
            waterNeed = 4,
            feedingDays = listOf(20, 40, 60),
            harvestStartDay = 75,
            harvestEndDay = 125,
            continuousHarvest = true,
            harvestIntervalDays = 3,
            maintenanceFeedingIntervalDays = 18,
            supportedPlantingMethods = listOf(PlantingMethod.TRANSPLANT, PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("缓苗/幼苗期", 0, 20, "保持土壤微湿，促进缓苗"),
                GrowthStage("分枝生长期", 21, 45, "适度整枝，保持通风"),
                GrowthStage("开花坐果期", 46, 74, "稳定水肥，减少落花落果", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 75, 125, "果实达到食用大小后分批采收", StageWaterDemand.HIGH)
            ),
            careTips = listOf("结果期避免忽干忽湿", "果实转色后可及时采收"),
            transplantGrowthOffsetDays = 16
        ),
        CropProfile(
            id = "bitter_melon",
            name = "苦瓜",
            category = "瓜类",
            baseTemperature = 12.0,
            minGoodTemperature = 22.0,
            maxGoodTemperature = 33.0,
            waterNeed = 4,
            feedingDays = listOf(18, 35, 52),
            harvestStartDay = 60,
            harvestEndDay = 105,
            continuousHarvest = true,
            harvestIntervalDays = 2,
            maintenanceFeedingIntervalDays = 14,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("幼苗期", 0, 16, "保持温暖，控制幼苗期水分"),
                GrowthStage("伸蔓期", 17, 35, "及时搭架引蔓"),
                GrowthStage("开花坐果期", 36, 59, "保持水分稳定，关注授粉", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 60, 105, "果实充分长大前及时采摘", StageWaterDemand.HIGH)
            ),
            careTips = listOf("需搭架并及时引蔓", "老熟果会影响后续坐果"),
            transplantGrowthOffsetDays = 12
        ),
        CropProfile(
            id = "cabbage",
            name = "甘蓝",
            category = "叶菜",
            baseTemperature = 5.0,
            minGoodTemperature = 15.0,
            maxGoodTemperature = 25.0,
            waterNeed = 3,
            feedingDays = listOf(18, 35),
            harvestStartDay = 65,
            harvestEndDay = 90,
            continuousHarvest = false,
            harvestIntervalDays = 5,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("发芽/缓苗期", 0, 12, "保持土壤微湿"),
                GrowthStage("莲座期", 13, 35, "促进外叶生长，保持通风"),
                GrowthStage("结球期", 36, 64, "保持水肥均衡，防止裂球", StageWaterDemand.HIGH),
                GrowthStage("采收期", 65, 90, "叶球紧实后及时采收")
            ),
            careTips = listOf("结球期避免忽干忽湿", "注意菜青虫和软腐"),
            transplantGrowthOffsetDays = 15
        ),
        CropProfile(
            id = "celery",
            name = "芹菜",
            category = "叶柄菜",
            baseTemperature = 5.0,
            minGoodTemperature = 15.0,
            maxGoodTemperature = 24.0,
            waterNeed = 4,
            feedingDays = listOf(25, 50, 75),
            harvestStartDay = 90,
            harvestEndDay = 130,
            continuousHarvest = false,
            harvestIntervalDays = 7,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("发芽/缓苗期", 0, 20, "出苗较慢，保持表土湿润"),
                GrowthStage("幼苗期", 21, 45, "保持凉爽和土壤湿润"),
                GrowthStage("叶柄膨大期", 46, 89, "水肥均衡，促进叶柄肥厚", StageWaterDemand.HIGH),
                GrowthStage("采收期", 90, 130, "叶柄达到食用大小后采收", StageWaterDemand.HIGH)
            ),
            careTips = listOf("不耐干旱和高温", "采收前保持水分稳定"),
            transplantGrowthOffsetDays = 20
        ),
        CropProfile(
            id = "cherry_tomato",
            name = "圣女果",
            category = "茄果类",
            baseTemperature = 10.0,
            minGoodTemperature = 18.0,
            maxGoodTemperature = 32.0,
            waterNeed = 4,
            feedingDays = listOf(18, 35, 52, 70),
            harvestStartDay = 65,
            harvestEndDay = 120,
            continuousHarvest = true,
            harvestIntervalDays = 2,
            maintenanceFeedingIntervalDays = 16,
            supportedPlantingMethods = listOf(PlantingMethod.TRANSPLANT, PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("缓苗/幼苗期", 0, 18, "保持土壤微湿"),
                GrowthStage("营养生长期", 19, 38, "及时搭架并整理侧枝"),
                GrowthStage("开花坐果期", 39, 64, "保持水肥稳定，促进坐果", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 65, 120, "果实转色后分批采收", StageWaterDemand.HIGH)
            ),
            careTips = listOf("成熟果及时采收", "雨后注意裂果和病害"),
            transplantGrowthOffsetDays = 14
        ),
        CropProfile(
            id = "corn",
            name = "玉米",
            category = "谷物",
            baseTemperature = 10.0,
            minGoodTemperature = 20.0,
            maxGoodTemperature = 32.0,
            waterNeed = 3,
            feedingDays = listOf(20, 40, 60),
            harvestStartDay = 85,
            harvestEndDay = 110,
            continuousHarvest = false,
            harvestIntervalDays = 5,
            supportedPlantingMethods = listOf(PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("幼苗期", 0, 20, "适度控水，促进根系"),
                GrowthStage("拔节期", 21, 50, "及时培土并补充养分"),
                GrowthStage("抽雄灌浆期", 51, 84, "保证授粉和水分供应", StageWaterDemand.HIGH),
                GrowthStage("采收期", 85, 110, "籽粒达到适宜成熟度后采收")
            ),
            careTips = listOf("授粉期避免严重缺水", "植株高大时注意防倒伏")
        ),
        CropProfile(
            id = "garlic",
            name = "大蒜",
            category = "葱蒜类",
            baseTemperature = 4.0,
            minGoodTemperature = 12.0,
            maxGoodTemperature = 25.0,
            waterNeed = 3,
            feedingDays = listOf(25, 55, 85),
            harvestStartDay = 120,
            harvestEndDay = 160,
            continuousHarvest = false,
            harvestIntervalDays = 7,
            supportedPlantingMethods = listOf(PlantingMethod.BULB),
            stages = listOf(
                GrowthStage("萌芽期", 0, 20, "保持土壤微湿，促进出苗"),
                GrowthStage("叶片生长期", 21, 65, "适度追肥，促进叶片和根系"),
                GrowthStage("蒜头膨大期", 66, 119, "保持水分稳定，后期逐步控水", StageWaterDemand.HIGH),
                GrowthStage("采收期", 120, 160, "叶片开始转黄后择期采收")
            ),
            careTips = listOf("蒜头膨大期避免缺水", "采收前适度控水便于贮藏"),
            bulbGrowthOffsetDays = 5
        ),
        CropProfile(
            id = "kale",
            name = "羽衣甘蓝",
            category = "叶菜",
            baseTemperature = 5.0,
            minGoodTemperature = 12.0,
            maxGoodTemperature = 25.0,
            waterNeed = 3,
            feedingDays = listOf(20, 40),
            harvestStartDay = 55,
            harvestEndDay = 120,
            continuousHarvest = true,
            harvestIntervalDays = 7,
            maintenanceFeedingIntervalDays = 21,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("发芽/缓苗期", 0, 12, "保持表土湿润"),
                GrowthStage("幼苗期", 13, 30, "保持通风，促进根系"),
                GrowthStage("莲座旺长期", 31, 54, "保持水肥，促进叶片展开", StageWaterDemand.HIGH),
                GrowthStage("连续采叶期", 55, 120, "由下向上采收外叶", StageWaterDemand.HIGH)
            ),
            careTips = listOf("采叶时保留顶部生长点", "冷凉条件下口感更好"),
            transplantGrowthOffsetDays = 14
        ),
        CropProfile(
            id = "mint",
            name = "薄荷",
            category = "香草",
            baseTemperature = 8.0,
            minGoodTemperature = 16.0,
            maxGoodTemperature = 28.0,
            waterNeed = 4,
            feedingDays = listOf(20, 45),
            harvestStartDay = 45,
            harvestEndDay = 150,
            continuousHarvest = true,
            harvestIntervalDays = 14,
            maintenanceFeedingIntervalDays = 25,
            supportedPlantingMethods = listOf(
                PlantingMethod.CUTTING,
                PlantingMethod.DIVISION,
                PlantingMethod.SEED
            ),
            stages = listOf(
                GrowthStage("生根/发芽期", 0, 12, "保持湿润，促进生根出苗"),
                GrowthStage("幼苗期", 13, 28, "保持通风，防止徒长"),
                GrowthStage("分枝旺长期", 29, 44, "适度摘心，促进侧枝", StageWaterDemand.HIGH),
                GrowthStage("连续采叶期", 45, 150, "分批修剪，采后补水恢复", StageWaterDemand.HIGH)
            ),
            careTips = listOf("生长扩张快，建议限制根系范围", "采后保留基部芽点"),
            cuttingGrowthOffsetDays = 7,
            divisionGrowthOffsetDays = 10
        ),
        CropProfile(
            id = "okra",
            name = "秋葵",
            category = "果菜",
            baseTemperature = 12.0,
            minGoodTemperature = 22.0,
            maxGoodTemperature = 35.0,
            waterNeed = 3,
            feedingDays = listOf(20, 40, 60),
            harvestStartDay = 55,
            harvestEndDay = 110,
            continuousHarvest = true,
            harvestIntervalDays = 2,
            maintenanceFeedingIntervalDays = 18,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("幼苗期", 0, 18, "保持温暖，避免积水"),
                GrowthStage("营养生长期", 19, 38, "促进茎叶健壮生长"),
                GrowthStage("开花结荚期", 39, 54, "保持水分稳定", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 55, 110, "嫩荚及时采收，避免纤维化", StageWaterDemand.HIGH)
            ),
            careTips = listOf("嫩荚生长快，需勤巡查", "采摘时注意植株绒毛"),
            transplantGrowthOffsetDays = 12
        ),
        CropProfile(
            id = "onion",
            name = "洋葱",
            category = "葱蒜类",
            baseTemperature = 5.0,
            minGoodTemperature = 15.0,
            maxGoodTemperature = 25.0,
            waterNeed = 3,
            feedingDays = listOf(25, 50, 75),
            harvestStartDay = 100,
            harvestEndDay = 140,
            continuousHarvest = false,
            harvestIntervalDays = 7,
            supportedPlantingMethods = listOf(
                PlantingMethod.SEED,
                PlantingMethod.TRANSPLANT,
                PlantingMethod.BULB
            ),
            stages = listOf(
                GrowthStage("发芽/缓苗期", 0, 20, "保持土壤微湿"),
                GrowthStage("叶片生长期", 21, 55, "促进叶片和根系生长"),
                GrowthStage("鳞茎膨大期", 56, 99, "水分稳定，后期逐步控水", StageWaterDemand.HIGH),
                GrowthStage("采收期", 100, 140, "叶片倒伏后择期采收")
            ),
            careTips = listOf("鳞茎膨大期保持水分稳定", "采收前减少浇水"),
            transplantGrowthOffsetDays = 18,
            bulbGrowthOffsetDays = 8
        ),
        CropProfile(
            id = "potato",
            name = "土豆",
            category = "薯类",
            baseTemperature = 5.0,
            minGoodTemperature = 15.0,
            maxGoodTemperature = 25.0,
            waterNeed = 3,
            feedingDays = listOf(25, 45, 65),
            harvestStartDay = 90,
            harvestEndDay = 120,
            continuousHarvest = false,
            harvestIntervalDays = 7,
            supportedPlantingMethods = listOf(PlantingMethod.TUBER),
            stages = listOf(
                GrowthStage("萌芽期", 0, 18, "保持土壤疏松，避免积水"),
                GrowthStage("茎叶生长期", 19, 45, "及时培土，促进植株生长"),
                GrowthStage("块茎膨大期", 46, 89, "保持水分稳定，避免块茎见光", StageWaterDemand.HIGH),
                GrowthStage("采收期", 90, 120, "茎叶衰老后择期采收")
            ),
            careTips = listOf("块茎膨大期避免忽干忽湿", "及时培土防止薯块变绿"),
            tuberGrowthOffsetDays = 7
        ),
        CropProfile(
            id = "pumpkin",
            name = "南瓜",
            category = "瓜类",
            baseTemperature = 10.0,
            minGoodTemperature = 18.0,
            maxGoodTemperature = 32.0,
            waterNeed = 3,
            feedingDays = listOf(20, 40, 60),
            harvestStartDay = 90,
            harvestEndDay = 130,
            continuousHarvest = false,
            harvestIntervalDays = 7,
            supportedPlantingMethods = listOf(PlantingMethod.SEED, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("幼苗期", 0, 18, "控制水分，促进壮苗"),
                GrowthStage("伸蔓期", 19, 45, "整理藤蔓，合理引蔓"),
                GrowthStage("开花膨果期", 46, 89, "保证授粉和果实膨大", StageWaterDemand.HIGH),
                GrowthStage("采收期", 90, 130, "果皮硬化、果柄木栓化后采收")
            ),
            careTips = listOf("藤蔓占地较大，注意留足空间", "坐果后避免水分剧烈波动"),
            transplantGrowthOffsetDays = 12
        ),
        CropProfile(
            id = "snow_pea",
            name = "荷兰豆",
            category = "豆类",
            baseTemperature = 5.0,
            minGoodTemperature = 12.0,
            maxGoodTemperature = 24.0,
            waterNeed = 3,
            feedingDays = listOf(20, 40),
            harvestStartDay = 55,
            harvestEndDay = 90,
            continuousHarvest = true,
            harvestIntervalDays = 2,
            maintenanceFeedingIntervalDays = 20,
            supportedPlantingMethods = listOf(PlantingMethod.SEED),
            stages = listOf(
                GrowthStage("幼苗期", 0, 16, "保持凉爽，避免积水"),
                GrowthStage("伸蔓期", 17, 35, "及时搭架引蔓"),
                GrowthStage("开花结荚期", 36, 54, "保持水分稳定", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 55, 90, "嫩荚及时采摘，促进继续开花", StageWaterDemand.HIGH)
            ),
            careTips = listOf("喜冷凉，忌持续高温", "嫩荚需勤采避免纤维化")
        ),
        CropProfile(
            id = "strawberry",
            name = "草莓",
            category = "浆果",
            baseTemperature = 5.0,
            minGoodTemperature = 15.0,
            maxGoodTemperature = 26.0,
            waterNeed = 4,
            feedingDays = listOf(20, 40, 60),
            harvestStartDay = 60,
            harvestEndDay = 150,
            continuousHarvest = true,
            harvestIntervalDays = 2,
            maintenanceFeedingIntervalDays = 18,
            supportedPlantingMethods = listOf(PlantingMethod.TRANSPLANT, PlantingMethod.DIVISION),
            stages = listOf(
                GrowthStage("缓苗期", 0, 18, "保持土壤微湿，促进新根"),
                GrowthStage("叶片生长期", 19, 38, "整理匍匐茎，保持通风"),
                GrowthStage("开花膨果期", 39, 59, "稳定水肥，保持果面干燥", StageWaterDemand.HIGH),
                GrowthStage("连续采收期", 60, 150, "果实充分转色后分批采摘", StageWaterDemand.HIGH)
            ),
            careTips = listOf("浇水尽量避开花果", "成熟果及时采摘防止腐烂"),
            transplantGrowthOffsetDays = 14,
            divisionGrowthOffsetDays = 10
        ),
        CropProfile(
            id = "sweet_potato",
            name = "红薯",
            category = "薯类",
            baseTemperature = 10.0,
            minGoodTemperature = 20.0,
            maxGoodTemperature = 32.0,
            waterNeed = 2,
            feedingDays = listOf(20, 45, 70),
            harvestStartDay = 100,
            harvestEndDay = 140,
            continuousHarvest = false,
            harvestIntervalDays = 7,
            supportedPlantingMethods = listOf(PlantingMethod.CUTTING, PlantingMethod.TRANSPLANT),
            stages = listOf(
                GrowthStage("生根缓苗期", 0, 18, "保持土壤微湿，促进生根"),
                GrowthStage("蔓叶生长期", 19, 50, "控制过旺，促进根系"),
                GrowthStage("块根膨大期", 51, 99, "水分稳定，避免后期过湿", StageWaterDemand.HIGH),
                GrowthStage("采收期", 100, 140, "块根达到大小后择期采收")
            ),
            careTips = listOf("避免氮肥过多导致徒长", "采收前适当控水"),
            transplantGrowthOffsetDays = 12,
            cuttingGrowthOffsetDays = 8
        )
    )

    fun byId(id: String): CropProfile = crops.firstOrNull { it.id == id } ?: crops.first()

    fun byName(name: String): CropProfile? = crops.firstOrNull { it.name == name }
}
