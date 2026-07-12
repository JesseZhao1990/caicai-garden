package com.caicai.garden.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class GardenRepository(context: Context) {
    private val preferences = context.getSharedPreferences("caicai_garden_store", Context.MODE_PRIVATE)

    fun load(): GardenDataState {
        val raw = preferences.getString(KEY_STATE, null)
        if (raw.isNullOrBlank()) {
            return seedState().also(::save)
        }

        return runCatching { decodeState(JSONObject(raw)) }
            .getOrElse { seedState().also(::save) }
    }

    fun save(state: GardenDataState) {
        preferences.edit().putString(KEY_STATE, encodeState(state).toString()).apply()
    }

    private fun seedState(): GardenDataState {
        val today = LocalDate.now()
        val garden = Garden(
            id = newId(),
            name = "我的菜园",
            locationName = "北京",
            latitude = 39.9042,
            longitude = 116.4074
        )
        val plots = listOf(
            Plot(newId(), garden.id, "1 号畦", "约 3 平米", GrowingStyle.OPEN_FIELD, "壤土"),
            Plot(newId(), garden.id, "2 号畦", "约 2 平米", GrowingStyle.OPEN_FIELD, "壤土"),
            Plot(newId(), garden.id, "阳台箱", "80 x 35 cm", GrowingStyle.BALCONY, "营养土")
        )
        val batches = listOf(
            PlantingBatch(
                id = newId(),
                plotId = plots[0].id,
                cropId = "tomato",
                variety = "普罗旺斯",
                method = PlantingMethod.TRANSPLANT,
                startDate = today.minusDays(43).toString(),
                quantityLabel = "6 株"
            ),
            PlantingBatch(
                id = newId(),
                plotId = plots[1].id,
                cropId = "cucumber",
                variety = "津优",
                method = PlantingMethod.SEED,
                startDate = today.minusDays(33).toString(),
                quantityLabel = "8 株"
            ),
            PlantingBatch(
                id = newId(),
                plotId = plots[2].id,
                cropId = "pakchoi",
                variety = "快菜",
                method = PlantingMethod.SEED,
                startDate = today.minusDays(27).toString(),
                quantityLabel = "1 箱"
            )
        )
        val records = listOf(
            OperationRecord(
                id = newId(),
                batchId = batches[0].id,
                plotId = plots[0].id,
                type = OperationType.FERTILIZE,
                timestamp = LocalDateTime.now().minusDays(14).toString(),
                amountLabel = "有机肥少量",
                note = "坐果前补肥"
            ),
            OperationRecord(
                id = newId(),
                batchId = batches[1].id,
                plotId = plots[1].id,
                type = OperationType.WATER,
                timestamp = LocalDateTime.now().minusDays(3).toString(),
                amountLabel = "浇透",
                note = "傍晚浇水"
            ),
            OperationRecord(
                id = newId(),
                batchId = batches[2].id,
                plotId = plots[2].id,
                type = OperationType.PHOTO,
                timestamp = LocalDateTime.now().minusDays(5).toString(),
                amountLabel = "",
                note = "叶片长势正常"
            )
        )
        return GardenDataState(
            gardens = listOf(garden),
            plots = plots,
            batches = batches,
            records = records,
            farmLayout = defaultFarmLayout(batches)
        )
    }

    private fun encodeState(state: GardenDataState): JSONObject {
        return JSONObject()
            .put("gardens", JSONArray().apply { state.gardens.forEach { put(encodeGarden(it)) } })
            .put("plots", JSONArray().apply { state.plots.forEach { put(encodePlot(it)) } })
            .put("batches", JSONArray().apply { state.batches.forEach { put(encodeBatch(it)) } })
            .put("records", JSONArray().apply { state.records.forEach { put(encodeRecord(it)) } })
            .put("farmLayout", encodeFarmLayout(state.farmLayout))
    }

    private fun decodeState(json: JSONObject): GardenDataState {
        val gardens = json.optJSONArray("gardens").toGardenList()
        val plots = json.optJSONArray("plots").toPlotList()
        val batches = json.optJSONArray("batches").toBatchList()
        val records = json.optJSONArray("records").toRecordList()
        return GardenDataState(
            gardens = gardens,
            plots = plots,
            batches = batches,
            records = records,
            farmLayout = json.optJSONObject("farmLayout").toFarmLayout(batches)
        )
    }

    private fun encodeGarden(garden: Garden): JSONObject = JSONObject()
        .put("id", garden.id)
        .put("name", garden.name)
        .put("locationName", garden.locationName)
        .put("latitude", garden.latitude)
        .put("longitude", garden.longitude)

    private fun encodePlot(plot: Plot): JSONObject = JSONObject()
        .put("id", plot.id)
        .put("gardenId", plot.gardenId)
        .put("name", plot.name)
        .put("sizeLabel", plot.sizeLabel)
        .put("growingStyle", plot.growingStyle.name)
        .put("soilType", plot.soilType)
        .put("notes", plot.notes)

    private fun encodeBatch(batch: PlantingBatch): JSONObject = JSONObject()
        .put("id", batch.id)
        .put("plotId", batch.plotId)
        .put("cropId", batch.cropId)
        .put("variety", batch.variety)
        .put("method", batch.method.name)
        .put("startDate", batch.startDate)
        .put("quantityLabel", batch.quantityLabel)
        .put("status", batch.status.name)

    private fun encodeRecord(record: OperationRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("batchId", record.batchId)
        .put("plotId", record.plotId)
        .put("type", record.type.name)
        .put("timestamp", record.timestamp)
        .put("amountLabel", record.amountLabel)
        .put("note", record.note)

    private fun encodeFarmLayout(layout: FarmLayout): JSONObject = JSONObject()
        .put("rows", layout.rows)
        .put("columns", layout.columns)
        .put("tiles", JSONArray().apply { layout.tiles.forEach { put(encodeFarmTile(it)) } })

    private fun encodeFarmTile(tile: FarmTile): JSONObject = JSONObject()
        .put("row", tile.row)
        .put("column", tile.column)
        .put("type", tile.type.name)
        .put("batchId", tile.batchId)
        .put("rotationDegrees", tile.rotationDegrees)

    private fun JSONArray?.toGardenList(): List<Garden> {
        if (this == null) return emptyList()
        return (0 until length()).map { index ->
            val item = getJSONObject(index)
            Garden(
                id = item.getString("id"),
                name = item.getString("name"),
                locationName = item.optString("locationName", "本地"),
                latitude = item.optDouble("latitude", 39.9042),
                longitude = item.optDouble("longitude", 116.4074)
            )
        }
    }

    private fun JSONArray?.toPlotList(): List<Plot> {
        if (this == null) return emptyList()
        return (0 until length()).map { index ->
            val item = getJSONObject(index)
            Plot(
                id = item.getString("id"),
                gardenId = item.getString("gardenId"),
                name = item.getString("name"),
                sizeLabel = item.optString("sizeLabel"),
                growingStyle = enumValueOf(item.optString("growingStyle", GrowingStyle.OPEN_FIELD.name)),
                soilType = item.optString("soilType", "壤土"),
                notes = item.optString("notes")
            )
        }
    }

    private fun JSONArray?.toBatchList(): List<PlantingBatch> {
        if (this == null) return emptyList()
        return (0 until length()).map { index ->
            val item = getJSONObject(index)
            PlantingBatch(
                id = item.getString("id"),
                plotId = item.getString("plotId"),
                cropId = item.getString("cropId"),
                variety = item.optString("variety"),
                method = enumValueOf(item.optString("method", PlantingMethod.SEED.name)),
                startDate = item.getString("startDate"),
                quantityLabel = item.optString("quantityLabel"),
                status = enumValueOf(item.optString("status", BatchStatus.ACTIVE.name))
            )
        }
    }

    private fun JSONArray?.toRecordList(): List<OperationRecord> {
        if (this == null) return emptyList()
        return (0 until length()).map { index ->
            val item = getJSONObject(index)
            OperationRecord(
                id = item.getString("id"),
                batchId = item.optNullableString("batchId"),
                plotId = item.optNullableString("plotId"),
                type = enumValueOf(item.optString("type", OperationType.NOTE.name)),
                timestamp = item.getString("timestamp"),
                amountLabel = item.optString("amountLabel"),
                note = item.optString("note")
            )
        }
    }

    private fun JSONObject?.toFarmLayout(batches: List<PlantingBatch>): FarmLayout {
        if (this == null) return defaultFarmLayout(batches)
        val rows = optInt("rows", 8).coerceIn(4, 14)
        val columns = optInt("columns", 8).coerceIn(4, 14)
        val batchIds = batches.map { it.id }.toSet()
        val tiles = optJSONArray("tiles").toFarmTileList(rows, columns, batchIds)
        if (isLegacyGeneratedLayout(tiles) || isSparseDefaultLayout(tiles)) {
            return defaultFarmLayout(batches)
        }
        return FarmLayout(rows = rows, columns = columns, tiles = tiles)
    }

    private fun JSONArray?.toFarmTileList(rows: Int, columns: Int, batchIds: Set<String>): List<FarmTile> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = getJSONObject(index)
            val row = item.optInt("row", -1)
            val column = item.optInt("column", -1)
            val type = runCatching {
                enumValueOf<FarmTileType>(item.optString("type", FarmTileType.GRASS.name))
            }.getOrDefault(FarmTileType.GRASS)
            if (
                row !in 0 until rows ||
                column !in 0 until columns ||
                type == FarmTileType.GRASS ||
                type == FarmTileType.FENCE ||
                type == FarmTileType.PATH ||
                type == FarmTileType.IRRIGATION
            ) {
                null
            } else {
                FarmTile(
                    row = row,
                    column = column,
                    type = type,
                    batchId = item.optNullableString("batchId")?.takeIf { it in batchIds },
                    rotationDegrees = normalizeFarmTileRotation(
                        item.optDouble(
                            "rotationDegrees",
                            defaultFarmTileRotation(row, column, type).toDouble()
                        ).toFloat()
                    )
                )
            }
        }.distinctBy { it.row to it.column }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    fun defaultFarmLayout(batches: List<PlantingBatch>): FarmLayout {
        val activeBatches = batches.filter { it.status != BatchStatus.FINISHED }
        val plantPositions = listOf(
            2 to 1,
            2 to 3,
            2 to 5,
            3 to 2,
            3 to 5,
            5 to 2,
            5 to 4,
            6 to 3
        )
        val plantTiles = if (activeBatches.isEmpty()) {
            emptyList()
        } else {
            plantPositions.mapIndexed { index, (row, column) ->
                val batch = activeBatches[index % activeBatches.size]
                FarmTile(row = row, column = column, type = FarmTileType.RAISED_BED, batchId = batch.id)
            }
        }
        val structureTiles = listOf(
            FarmTile(2, 4, FarmTileType.SIGN)
        )
        return FarmLayout(
            rows = 8,
            columns = 8,
            tiles = (structureTiles + plantTiles).distinctBy { it.row to it.column }
        )
    }

    private fun isLegacyGeneratedLayout(tiles: List<FarmTile>): Boolean {
        if (tiles.size < 12) return false
        val cells = tiles.map { it.row to it.column }.toSet()
        val horizontalPath = (0 until 8).count { column -> FarmTile(3, column, FarmTileType.PATH).let { it.row to it.column } in cells }
        val verticalPath = (1 until 7).count { row -> FarmTile(row, 4, FarmTileType.PATH).let { it.row to it.column } in cells }
        return horizontalPath >= 6 && verticalPath >= 4
    }

    private fun isSparseDefaultLayout(tiles: List<FarmTile>): Boolean {
        if (tiles.size !in 5..7) return false
        val cells = tiles.map { it.row to it.column }.toSet()
        val oldStructureCells = setOf(1 to 0, 1 to 7, 2 to 4)
        val oldPlantCells = setOf(3 to 2, 3 to 5, 5 to 2, 5 to 5)
        return oldStructureCells.all { it in cells } && cells.all { it in oldStructureCells || it in oldPlantCells }
    }

    companion object {
        private const val KEY_STATE = "state_v1"

        fun newId(): String = UUID.randomUUID().toString()
    }
}
