package com.caicai.garden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.caicai.garden.data.FarmTile
import com.caicai.garden.data.FarmTileType
import com.caicai.garden.domain.PlantingInsight
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import io.github.sceneview.rememberOnGestureListener

@Composable
fun FarmScene3DBoard(
    rows: Int,
    columns: Int,
    tilesByCell: Map<Pair<Int, Int>, FarmTile>,
    insightsByBatch: Map<String, PlantingInsight>,
    selectedCell: Pair<Int, Int>?,
    interactionKey: String?,
    boardHeight: Dp,
    onCellClick: (Int, Int) -> Unit,
    onTileSelect: (Int, Int) -> Unit
) {
    val currentInteractionKey by rememberUpdatedState(interactionKey)
    val currentTilesByCell by rememberUpdatedState(tilesByCell)
    val currentOnCellClick by rememberUpdatedState(onCellClick)
    val currentOnTileSelect by rememberUpdatedState(onTileSelect)
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val centerNode = rememberNode(engine) {
        position = Position(y = 0.0f)
    }
    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 6.4f, y = 6.4f, z = 9.0f)
        lookAt(centerNode)
    }
    val sceneSignature = remember(rows, columns, tilesByCell, insightsByBatch, selectedCell) {
        buildString {
            append(rows).append(':').append(columns).append(':')
            tilesByCell.values.sortedWith(compareBy<FarmTile> { it.row }.thenBy { it.column }).forEach { tile ->
                append(tile.row).append(',').append(tile.column).append(',')
                    .append(tile.type.name).append(',').append(tile.batchId).append(',')
                    .append(tile.rotationDegrees).append(';')
                tile.batchId?.let { batchId ->
                    insightsByBatch[batchId]?.let { append(it.progressPercent).append(',').append(it.crop.id).append(';') }
                }
            }
            append("selected=").append(selectedCell)
        }
    }
    val childNodes = remember(sceneSignature) {
        buildFarm3DNodes(
            rows = rows,
            columns = columns,
            tilesByCell = tilesByCell,
            insightsByBatch = insightsByBatch,
            selectedCell = selectedCell,
            modelLoader = modelLoader,
            centerNode = centerNode
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(boardHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFBEE8F7), Color(0xFFB8E17D), Color(0xFF69B944))
                )
            )
    ) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = cameraNode.worldPosition,
                targetPosition = centerNode.worldPosition
            ),
            mainLightNode = rememberMainLightNode(engine),
            childNodes = childNodes,
            environment = rememberEnvironment(environmentLoader, isOpaque = false),
            isOpaque = false,
            onFrame = {
                cameraNode.lookAt(centerNode)
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { _, node: Node? ->
                    node.findFarmCell()?.let { (row, column) ->
                        if (currentInteractionKey == null && currentTilesByCell[row to column] != null) {
                            currentOnTileSelect(row, column)
                        } else {
                            currentOnCellClick(row, column)
                        }
                    }
                }
            )
        )
    }
}

private fun buildFarm3DNodes(
    rows: Int,
    columns: Int,
    tilesByCell: Map<Pair<Int, Int>, FarmTile>,
    insightsByBatch: Map<String, PlantingInsight>,
    selectedCell: Pair<Int, Int>?,
    modelLoader: io.github.sceneview.loaders.ModelLoader,
    centerNode: Node
): List<Node> {
    val nodes = mutableListOf<Node>(centerNode)
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val cell = row to column
            nodes += farmModelNode(
                modelLoader = modelLoader,
                asset = "models/tile_grass.glb",
                name = "cell:$row:$column",
                position = farm3DPosition(row, column, rows, columns).copy(y = -0.035f),
                scaleToUnits = 0.72f,
                touchable = true,
            )
            if (selectedCell == cell) {
                nodes += farmModelNode(
                    modelLoader = modelLoader,
                    asset = "models/tile_selection.glb",
                    name = "selection:$row:$column",
                    position = farm3DPosition(row, column, rows, columns).copy(y = 0.0f),
                    scaleToUnits = 0.78f,
                    touchable = false,
                )
            }
        }
    }

    tilesByCell.values.sortedWith(compareBy<FarmTile> { it.row + it.column }.thenBy { it.row }).forEach { tile ->
        val insight = tile.batchId?.let { insightsByBatch[it] }
        val asset = farm3DAssetFor(tile, insight) ?: return@forEach
        val position = farm3DPosition(tile.row, tile.column, rows, columns).copy(y = 0.0f)
        nodes += farmModelNode(
            modelLoader = modelLoader,
            asset = asset,
            name = "tile:${tile.row}:${tile.column}",
            position = position,
            scaleToUnits = farm3DScale(tile.type),
            touchable = true,
        ).apply {
            rotation = Rotation(y = tile.rotationDegrees)
        }
    }
    return nodes
}

private fun farmModelNode(
    modelLoader: io.github.sceneview.loaders.ModelLoader,
    asset: String,
    name: String,
    position: Position,
    scaleToUnits: Float,
    touchable: Boolean
): ModelNode {
    return ModelNode(
        modelInstance = modelLoader.createModelInstance(assetFileLocation = asset),
        scaleToUnits = scaleToUnits
    ).apply {
        this.name = name
        this.position = position
        isTouchable = touchable
        isEditable = false
        nodes.forEach { it.isTouchable = false }
        isShadowCaster = true
        isShadowReceiver = true
    }
}

private fun Node?.findFarmCell(): Pair<Int, Int>? {
    var current = this
    repeat(4) {
        val name = current?.name
        if (name != null) {
            val parts = name.split(':')
            if ((parts.firstOrNull() == "cell" || parts.firstOrNull() == "tile") && parts.size >= 3) {
                val row = parts[1].toIntOrNull()
                val column = parts[2].toIntOrNull()
                if (row != null && column != null) return row to column
            }
        }
        current = current?.parent
    }
    return null
}

private fun farm3DPosition(row: Int, column: Int, rows: Int, columns: Int): Position {
    val spacing = 0.74f
    val x = (column - (columns - 1) / 2.0f) * spacing
    val z = (row - (rows - 1) / 2.0f) * spacing
    return Position(x = x, y = 0.0f, z = z)
}

private fun farm3DScale(type: FarmTileType): Float {
    return when (type) {
        FarmTileType.RAISED_BED -> 0.82f
        FarmTileType.GREENHOUSE -> 1.02f
        FarmTileType.TOOL_SHED -> 0.92f
        FarmTileType.SIGN -> 0.66f
        else -> 0.75f
    }
}

private fun farm3DAssetFor(tile: FarmTile, insight: PlantingInsight?): String? {
    return when (tile.type) {
        FarmTileType.RAISED_BED -> insight?.let(::crop3DAssetFor)
        FarmTileType.GREENHOUSE -> "models/greenhouse.glb"
        FarmTileType.TOOL_SHED -> "models/tool_shed.glb"
        FarmTileType.SIGN -> "models/sign.glb"
        else -> null
    }
}

private fun crop3DAssetFor(insight: PlantingInsight): String {
    val stage = when {
        insight.progressPercent < 30 -> "early"
        insight.progressPercent < 70 -> "mid"
        else -> "late"
    }
    val cropName = insight.crop.name
    val category = insight.crop.category
    val kind = when {
        category.contains("瓜") || category.contains("豆") || cropName == "黄瓜" -> "vine"
        category.contains("根菜") || cropName == "胡萝卜" || cropName == "萝卜" -> "root"
        category.contains("茄果") || cropName == "番茄" || cropName == "辣椒" || cropName == "茄子" -> "tomato"
        else -> "leafy"
    }
    return "models/crop_${kind}_$stage.glb"
}
