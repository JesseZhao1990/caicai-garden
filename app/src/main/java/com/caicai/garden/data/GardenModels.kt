package com.caicai.garden.data

import java.time.LocalDate

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
    val predictionHint: String
) {
    SEED("种子播种", "种子", "播种日期", "从发芽第 0 天开始估算"),
    TRANSPLANT("种苗移栽", "种苗", "移栽日期", "按该作物常见苗龄估算起始长势"),
    CUTTING("插条扦插", "插条", "扦插日期", "从生根缓苗阶段开始估算")
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
    PHOTO("拍照")
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

data class GrowthStage(
    val name: String,
    val fromDay: Int,
    val toDay: Int,
    val focus: String
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
    val stages: List<GrowthStage>,
    val careTips: List<String>,
    val transplantGrowthOffsetDays: Int = 10,
    val cuttingGrowthOffsetDays: Int = 5
)

fun PlantingMethod.growthOffsetDays(crop: CropProfile): Int {
    return when (this) {
        PlantingMethod.SEED -> 0
        PlantingMethod.TRANSPLANT -> crop.transplantGrowthOffsetDays
        PlantingMethod.CUTTING -> crop.cuttingGrowthOffsetDays
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

data class GardenDataState(
    val gardens: List<Garden> = emptyList(),
    val plots: List<Plot> = emptyList(),
    val batches: List<PlantingBatch> = emptyList(),
    val records: List<OperationRecord> = emptyList(),
    val farmLayout: FarmLayout = FarmLayout()
)

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
            stages = listOf(
                GrowthStage("缓苗/幼苗期", 0, 20, "保持土壤微湿，避免大水漫灌"),
                GrowthStage("营养生长期", 21, 40, "促进根系和枝叶生长，及时搭架"),
                GrowthStage("开花坐果期", 41, 74, "稳定水分，减少落花，注意补充磷钾"),
                GrowthStage("连续采收期", 75, 120, "成熟果及时采收，维持水肥供应")
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
            stages = listOf(
                GrowthStage("幼苗期", 0, 14, "少量多次补水，防止徒长"),
                GrowthStage("伸蔓期", 15, 30, "及时搭架引蔓，保持土壤湿润"),
                GrowthStage("开花结果期", 31, 41, "需水需肥增加，关注高温和授粉"),
                GrowthStage("连续采收期", 42, 75, "1-2 天巡查一次，避免瓜条过老")
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
            stages = listOf(
                GrowthStage("缓苗/幼苗期", 0, 20, "控制浇水，促进根系恢复"),
                GrowthStage("分枝期", 21, 45, "适度追肥，保持通风"),
                GrowthStage("开花结果期", 46, 69, "避免高温干旱，关注落花"),
                GrowthStage("连续采收期", 70, 120, "成熟果分批采摘，减少植株负担")
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
            stages = listOf(
                GrowthStage("缓苗期", 0, 18, "少量补水，避免伤根"),
                GrowthStage("营养生长期", 19, 45, "促进枝叶和根系生长"),
                GrowthStage("开花坐果期", 46, 74, "保持水肥均衡，减少落花"),
                GrowthStage("采收期", 75, 130, "果实膨大期保持稳定水分")
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
            stages = listOf(
                GrowthStage("发芽期", 0, 6, "保持表土湿润"),
                GrowthStage("幼苗期", 7, 18, "适度控水，避免徒长"),
                GrowthStage("莲座期", 19, 34, "保持水肥充足，叶片快速展开"),
                GrowthStage("采收期", 35, 55, "达到食用大小即可采收")
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
            stages = listOf(
                GrowthStage("发芽期", 0, 5, "保持土壤湿润"),
                GrowthStage("幼苗期", 6, 15, "间苗后轻水轻肥"),
                GrowthStage("旺长期", 16, 29, "保持水肥充足，促进叶片生长"),
                GrowthStage("采收期", 30, 45, "可分批采收外层叶或整株采收")
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
            stages = listOf(
                GrowthStage("发芽期", 0, 7, "保持湿润，避免板结"),
                GrowthStage("幼苗期", 8, 20, "间苗并保持通风"),
                GrowthStage("旺长期", 21, 34, "适度追肥，保持叶片生长"),
                GrowthStage("采收期", 35, 55, "叶片达到食用大小即可采收")
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
            stages = listOf(
                GrowthStage("缓苗/发芽期", 0, 10, "保持湿润，促进快速生根"),
                GrowthStage("快速生长期", 11, 20, "水肥充足，促进嫩梢生长"),
                GrowthStage("成熟旺长期", 21, 29, "维持水肥，促进侧枝和可采嫩梢形成"),
                GrowthStage("连续采收期", 30, 80, "采嫩梢后补水补肥，促发侧枝")
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
            stages = listOf(
                GrowthStage("发芽期", 0, 12, "出苗慢，保持表土湿润"),
                GrowthStage("幼苗期", 13, 25, "适度间苗，避免过密"),
                GrowthStage("旺长期", 26, 39, "保持水肥均衡"),
                GrowthStage("采收期", 40, 60, "株高合适即可采收")
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
            stages = listOf(
                GrowthStage("缓苗期", 0, 14, "保持土壤微湿"),
                GrowthStage("分蘖期", 15, 45, "适度追肥，促进分蘖"),
                GrowthStage("旺长期", 46, 59, "保持水肥，适当培土"),
                GrowthStage("采收期", 60, 110, "按需采收，采后促发新叶")
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
            stages = listOf(
                GrowthStage("缓苗期", 0, 20, "少割少动，促进根系建立"),
                GrowthStage("快速生长期", 21, 40, "保持水肥，促进叶片健壮"),
                GrowthStage("成熟蓄养期", 41, 59, "继续养根壮叶，为首次采收积累长势"),
                GrowthStage("连续采收期", 60, 150, "割后追肥补水，留足恢复时间")
            ),
            careTips = listOf("割后不要立刻大水漫灌", "长期采收需补充有机肥"),
            transplantGrowthOffsetDays = 14,
            cuttingGrowthOffsetDays = 7
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
            stages = listOf(
                GrowthStage("发芽期", 0, 6, "保持土壤湿润"),
                GrowthStage("幼苗期", 7, 20, "间苗定苗，避免过密"),
                GrowthStage("肉质根膨大期", 21, 54, "水分稳定，少施过量氮肥"),
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
            stages = listOf(
                GrowthStage("发芽期", 0, 12, "出苗较慢，保持表土湿润"),
                GrowthStage("幼苗期", 13, 35, "间苗并保持土壤疏松"),
                GrowthStage("肉质根膨大期", 36, 79, "水分稳定，避免板结"),
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
            stages = listOf(
                GrowthStage("幼苗期", 0, 18, "控制水分，促根壮苗"),
                GrowthStage("伸蔓期", 19, 35, "及时搭架，引蔓上架"),
                GrowthStage("开花结荚期", 36, 54, "保持水分稳定，少量追肥"),
                GrowthStage("连续采收期", 55, 95, "嫩荚及时采摘，促进继续开花")
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
            stages = listOf(
                GrowthStage("幼苗期", 0, 16, "少量多次补水，避免低温积水"),
                GrowthStage("营养生长期", 17, 32, "保持水肥，促进株型展开"),
                GrowthStage("开花结果期", 33, 44, "关注授粉和水分稳定"),
                GrowthStage("连续采收期", 45, 80, "嫩瓜及时采收，防止过大消耗")
            ),
            careTips = listOf("连阴雨需关注授粉", "采摘越及时越利于连续结果"),
            transplantGrowthOffsetDays = 12
        )
    )

    fun byId(id: String): CropProfile = crops.firstOrNull { it.id == id } ?: crops.first()

    fun byName(name: String): CropProfile? = crops.firstOrNull { it.name == name }
}
