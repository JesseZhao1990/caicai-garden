package com.caicai.garden.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.caicai.garden.R
import com.caicai.garden.data.BatchStatus
import com.caicai.garden.data.CropLibrary
import com.caicai.garden.data.FarmTile
import com.caicai.garden.data.FarmTileType
import com.caicai.garden.data.PlantingBatch
import com.caicai.garden.domain.PlantingInsight
import kotlin.math.roundToInt

private sealed class FarmTool {
    data object Move : FarmTool()
    data class Plant(val batchId: String) : FarmTool()
    data class Structure(val type: FarmTileType) : FarmTool()
    data object Clear : FarmTool()
}

private enum class FarmGestureMode {
    TileDrag,
    ViewportPan,
    Ignore
}

private val fixedFarmSceneryTypes = setOf(FarmTileType.TOOL_SHED, FarmTileType.GREENHOUSE)

private const val FARM_VIEWPORT_MIN_SCALE = 1f
private const val FARM_VIEWPORT_MAX_SCALE = 2.8f

private data class FarmIsoMetrics(
    val centerX: Float,
    val top: Float,
    val xStep: Float,
    val yStep: Float
)

private fun farmIsoMetrics(rows: Int, columns: Int, widthToHeight: Float): FarmIsoMetrics {
    val span = (rows + columns - 2).coerceAtLeast(1).toFloat()
    val xStep = 0.88f / (span + 2f)
    val yStep = xStep * widthToHeight.coerceIn(0.42f, 1.35f) * 0.50f
    val fieldHeight = span * yStep
    return FarmIsoMetrics(
        centerX = 0.5f,
        top = (0.52f - fieldHeight / 2f).coerceIn(0.24f, 0.40f),
        xStep = xStep,
        yStep = yStep
    )
}

@Composable
fun FarmDesignerSection(
    viewModel: GardenViewModel,
    onSelectedPlotChange: (String) -> Unit
) {
    val state = viewModel.dataState
    val layout = state.farmLayout
    val activeBatches = state.batches.filter { it.status != BatchStatus.FINISHED }
    val insightsByBatch = viewModel.insights.associateBy { it.batch.id }
    val tilesByCell = layout.tiles
        .filterNot { it.type in fixedFarmSceneryTypes }
        .associateBy { it.row to it.column }
    var selectedTool by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCell by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    var pendingMoveCell by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }

    val selectedTile = selectedCell?.let { tilesByCell[it] }
    val selectedInsight = selectedTile?.batchId?.let { insightsByBatch[it] }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val boardHeight = (screenHeight * 0.64f).coerceIn(540.dp, 760.dp)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFE9F5D7)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("真实菜畦布局", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${layout.rows} x ${layout.columns} 对应实体菜园 · 拖拽移动 · 捏合缩放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = viewModel::resetFarmLayout) {
                    Text("重置")
                }
            }

            FarmAssetBoard(
                rows = layout.rows,
                columns = layout.columns,
                tilesByCell = tilesByCell,
                insightsByBatch = insightsByBatch,
                selectedCell = pendingMoveCell ?: selectedCell,
                interactionKey = selectedTool,
                boardHeight = boardHeight,
                onTileMove = { fromRow, fromColumn, toRow, toColumn ->
                    viewModel.moveFarmTile(fromRow, fromColumn, toRow, toColumn)
                    pendingMoveCell = null
                    selectedCell = toRow to toColumn
                },
                onCellClick = { row, column ->
                    selectedCell = row to column
                    val currentTile = tilesByCell[row to column]
                    when (val currentTool = selectedTool?.toFarmTool()) {
                        null -> {
                            pendingMoveCell = null
                            currentTile?.batchId
                                ?.let { id -> state.batches.firstOrNull { it.id == id } }
                                ?.plotId
                                ?.let(onSelectedPlotChange)
                        }

                        FarmTool.Move -> {
                            val fromCell = pendingMoveCell?.takeIf { tilesByCell[it] != null }
                            if (fromCell == null) {
                                pendingMoveCell = currentTile?.let { row to column }
                            } else {
                                viewModel.moveFarmTile(fromCell.first, fromCell.second, row, column)
                                pendingMoveCell = null
                            }
                        }

                        FarmTool.Clear -> {
                            pendingMoveCell = null
                            viewModel.clearFarmTile(row, column)
                        }

                        is FarmTool.Plant -> {
                            pendingMoveCell = null
                            viewModel.placeFarmTile(row, column, FarmTileType.RAISED_BED, currentTool.batchId)
                        }

                        is FarmTool.Structure -> {
                            pendingMoveCell = null
                            viewModel.placeFarmTile(row, column, currentTool.type, null)
                        }
                    }
                },
                onTileSelect = { row, column ->
                    pendingMoveCell = null
                    selectedCell = row to column
                }
            )

            if (selectedTile != null) {
                FarmSelectionPanel(
                    tile = selectedTile,
                    insight = selectedInsight,
                    onRotateLeft = selectedCell?.let { (row, column) ->
                        { viewModel.rotateFarmTile(row, column, -15f) }
                    },
                    onRotateRight = selectedCell?.let { (row, column) ->
                        { viewModel.rotateFarmTile(row, column, 15f) }
                    },
                    onResetRotation = selectedCell?.let { (row, column) ->
                        { viewModel.resetFarmTileRotation(row, column) }
                    }
                )
            }

            FarmToolPicker(
                activeBatches = activeBatches,
                selectedTool = selectedTool,
                onSelectedToolChange = {
                    pendingMoveCell = null
                    selectedTool = it
                }
            )
        }
    }
}

@Composable
private fun FarmBoard(
    rows: Int,
    columns: Int,
    tilesByCell: Map<Pair<Int, Int>, FarmTile>,
    insightsByBatch: Map<String, PlantingInsight>,
    selectedCell: Pair<Int, Int>?,
    interactionKey: String?,
    boardHeight: Dp,
    onCellClick: (Int, Int) -> Unit,
    onTileMove: (Int, Int, Int, Int) -> Unit,
    onTileSelect: (Int, Int) -> Unit
) {
    var draggingTileKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPreviewCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var viewportScale by rememberSaveable { mutableStateOf(FARM_VIEWPORT_MIN_SCALE) }
    var viewportOffsetX by rememberSaveable { mutableStateOf(0f) }
    var viewportOffsetY by rememberSaveable { mutableStateOf(0f) }
    val currentInteractionKey by rememberUpdatedState(interactionKey)
    val currentOnCellClick by rememberUpdatedState(onCellClick)
    val currentOnTileMove by rememberUpdatedState(onTileMove)
    val currentOnTileSelect by rememberUpdatedState(onTileSelect)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(boardHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFF7FCA4F), MaterialTheme.shapes.medium)
            .pointerInput(rows, columns, interactionKey, tilesByCell.values.map { it.overlayKey }) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var activePointerId = down.id
                    var gestureMode: FarmGestureMode? = null
                    var accumulatedDrag = Offset.Zero
                    val touchSlop = viewConfiguration.touchSlop
                    val boardWidthPx = size.width.toFloat()
                    val boardHeightPx = size.height.toFloat()
                    val zoomControlWidthPx = 72.dp.toPx()
                    val zoomControlHeightPx = 132.dp.toPx()
                    if (start.x >= boardWidthPx - zoomControlWidthPx && start.y <= zoomControlHeightPx) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                        return@awaitEachGesture
                    }
                    val moveToolSelected = currentInteractionKey == FarmTool.Move.toToolKey()
                    val startScenePosition = boardToFarmSceneOffset(
                        position = start,
                        viewportOffset = Offset(viewportOffsetX, viewportOffsetY),
                        viewportScale = viewportScale
                    )
                    val startTile = hitTestFarmTile(
                        position = startScenePosition,
                        tiles = tilesByCell.values,
                        rows = rows,
                        columns = columns,
                        boardWidthPx = boardWidthPx,
                        boardHeightPx = boardHeightPx,
                        density = this
                    )
                    val assetDragEnabled = startTile != null && (moveToolSelected || currentInteractionKey == null)

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }
                        if (pressedChanges.isEmpty()) break

                        if (pressedChanges.size > 1) {
                            gestureMode = FarmGestureMode.ViewportPan
                            draggingTileKey = null
                            dragOffset = Offset.Zero
                            dragPreviewCell = null
                            val transformChanges = pressedChanges.filter { it.previousPressed }
                            if (transformChanges.size > 1) {
                                val previousCentroid = pointerCentroid(transformChanges, usePrevious = true)
                                val currentCentroid = pointerCentroid(transformChanges, usePrevious = false)
                                val previousDistance = pointerAverageDistance(
                                    changes = transformChanges,
                                    centroid = previousCentroid,
                                    usePrevious = true
                                )
                                val currentDistance = pointerAverageDistance(
                                    changes = transformChanges,
                                    centroid = currentCentroid,
                                    usePrevious = false
                                )
                                val zoomChange = if (previousDistance > 0f) currentDistance / previousDistance else 1f
                                val oldScale = viewportScale
                                val newScale = (oldScale * zoomChange)
                                    .coerceIn(FARM_VIEWPORT_MIN_SCALE, FARM_VIEWPORT_MAX_SCALE)
                                val sceneFocus = boardToFarmSceneOffset(
                                    position = previousCentroid,
                                    viewportOffset = Offset(viewportOffsetX, viewportOffsetY),
                                    viewportScale = oldScale
                                )
                                val nextOffset = clampFarmViewportOffset(
                                    offset = Offset(
                                        x = currentCentroid.x - sceneFocus.x * newScale,
                                        y = currentCentroid.y - sceneFocus.y * newScale
                                    ),
                                    scale = newScale,
                                    boardWidthPx = boardWidthPx,
                                    boardHeightPx = boardHeightPx
                                )
                                viewportScale = newScale
                                viewportOffsetX = nextOffset.x
                                viewportOffsetY = nextOffset.y
                            }
                            event.changes.forEach { change ->
                                if (change.pressed) change.consume()
                            }
                            continue
                        }

                        val activeChange = pressedChanges.firstOrNull { it.id == activePointerId }
                        if (activeChange == null) {
                            if (gestureMode == FarmGestureMode.TileDrag) break
                            activePointerId = pressedChanges.first().id
                            continue
                        }
                        val change = activeChange
                        val delta = change.positionChange()
                        if (delta == Offset.Zero) continue

                        when (gestureMode) {
                            null -> {
                                accumulatedDrag += delta
                                if (accumulatedDrag.getDistance() > touchSlop) {
                                    gestureMode = when {
                                        assetDragEnabled -> FarmGestureMode.TileDrag
                                        viewportScale > FARM_VIEWPORT_MIN_SCALE + 0.01f -> FarmGestureMode.ViewportPan
                                        else -> FarmGestureMode.Ignore
                                    }
                                    when (gestureMode) {
                                        FarmGestureMode.TileDrag -> {
                                            change.consume()
                                            draggingTileKey = startTile?.overlayKey
                                            dragOffset += accumulatedDrag / viewportScale
                                            if (startTile != null) {
                                                val anchor = farmAnchor(
                                                    startTile.row,
                                                    startTile.column,
                                                    rows,
                                                    columns,
                                                    boardWidthPx / boardHeightPx.coerceAtLeast(1f)
                                                )
                                                dragPreviewCell = gridCellForDraggedAnchor(
                                                    anchorX = anchor.first,
                                                    anchorY = anchor.second,
                                                    dragX = dragOffset.x,
                                                    dragY = dragOffset.y,
                                                    boardWidthPx = boardWidthPx,
                                                    boardHeightPx = boardHeightPx,
                                                    rows = rows,
                                                    columns = columns
                                                )
                                            }
                                        }

                                        FarmGestureMode.ViewportPan -> {
                                            change.consume()
                                            val nextOffset = clampFarmViewportOffset(
                                                offset = Offset(
                                                    x = viewportOffsetX + accumulatedDrag.x,
                                                    y = viewportOffsetY + accumulatedDrag.y
                                                ),
                                                scale = viewportScale,
                                                boardWidthPx = boardWidthPx,
                                                boardHeightPx = boardHeightPx
                                            )
                                            viewportOffsetX = nextOffset.x
                                            viewportOffsetY = nextOffset.y
                                        }

                                        FarmGestureMode.Ignore, null -> Unit
                                    }
                                }
                            }

                            FarmGestureMode.TileDrag -> {
                                change.consume()
                                dragOffset += delta / viewportScale
                                if (startTile != null) {
                                    val anchor = farmAnchor(
                                        startTile.row,
                                        startTile.column,
                                        rows,
                                        columns,
                                        boardWidthPx / boardHeightPx.coerceAtLeast(1f)
                                    )
                                    dragPreviewCell = gridCellForDraggedAnchor(
                                        anchorX = anchor.first,
                                        anchorY = anchor.second,
                                        dragX = dragOffset.x,
                                        dragY = dragOffset.y,
                                        boardWidthPx = boardWidthPx,
                                        boardHeightPx = boardHeightPx,
                                        rows = rows,
                                        columns = columns
                                    )
                                }
                            }

                            FarmGestureMode.ViewportPan -> {
                                change.consume()
                                val nextOffset = clampFarmViewportOffset(
                                    offset = Offset(
                                        x = viewportOffsetX + delta.x,
                                        y = viewportOffsetY + delta.y
                                    ),
                                    scale = viewportScale,
                                    boardWidthPx = boardWidthPx,
                                    boardHeightPx = boardHeightPx
                                )
                                viewportOffsetX = nextOffset.x
                                viewportOffsetY = nextOffset.y
                            }

                            FarmGestureMode.Ignore -> Unit
                        }
                    }

                    if (gestureMode == null) {
                        val tapPosition = boardToFarmSceneOffset(
                            position = start,
                            viewportOffset = Offset(viewportOffsetX, viewportOffsetY),
                            viewportScale = viewportScale
                        )
                        if (currentInteractionKey != null) {
                            val target = gridCellForBoardOffset(
                                x = tapPosition.x,
                                y = tapPosition.y,
                                boardWidthPx = boardWidthPx,
                                boardHeightPx = boardHeightPx,
                                rows = rows,
                                columns = columns
                            )
                            currentOnCellClick(target.first, target.second)
                        } else if (startTile != null) {
                            currentOnTileSelect(startTile.row, startTile.column)
                        } else {
                            val target = gridCellForBoardOffset(
                                x = tapPosition.x,
                                y = tapPosition.y,
                                boardWidthPx = boardWidthPx,
                                boardHeightPx = boardHeightPx,
                                rows = rows,
                                columns = columns
                            )
                            currentOnCellClick(target.first, target.second)
                        }
                    } else if (gestureMode == FarmGestureMode.TileDrag && startTile != null) {
                        val anchor = farmAnchor(
                            startTile.row,
                            startTile.column,
                            rows,
                            columns,
                            boardWidthPx / boardHeightPx.coerceAtLeast(1f)
                        )
                        val target = gridCellForDraggedAnchor(
                            anchorX = anchor.first,
                            anchorY = anchor.second,
                            dragX = dragOffset.x,
                            dragY = dragOffset.y,
                            boardWidthPx = boardWidthPx,
                            boardHeightPx = boardHeightPx,
                            rows = rows,
                            columns = columns
                        )
                        currentOnTileMove(startTile.row, startTile.column, target.first, target.second)
                        currentOnTileSelect(target.first, target.second)
                        draggingTileKey = null
                        dragOffset = Offset.Zero
                        dragPreviewCell = null
                    } else {
                        draggingTileKey = null
                        dragOffset = Offset.Zero
                        dragPreviewCell = null
                    }
                }
            }
    ) {
        val boardWidth = maxWidth
        val sceneHeight = maxHeight
        val density = LocalDensity.current
        val boardWidthPxForZoom = with(density) { maxWidth.toPx() }
        val boardHeightPxForZoom = with(density) { maxHeight.toPx() }
        fun zoomViewport(targetScale: Float) {
            val oldScale = viewportScale
            val newScale = targetScale.coerceIn(FARM_VIEWPORT_MIN_SCALE, FARM_VIEWPORT_MAX_SCALE)
            val center = Offset(boardWidthPxForZoom / 2f, boardHeightPxForZoom / 2f)
            val sceneFocus = boardToFarmSceneOffset(
                position = center,
                viewportOffset = Offset(viewportOffsetX, viewportOffsetY),
                viewportScale = oldScale
            )
            val nextOffset = clampFarmViewportOffset(
                offset = Offset(
                    x = center.x - sceneFocus.x * newScale,
                    y = center.y - sceneFocus.y * newScale
                ),
                scale = newScale,
                boardWidthPx = boardWidthPxForZoom,
                boardHeightPx = boardHeightPxForZoom
            )
            viewportScale = newScale
            viewportOffsetX = nextOffset.x
            viewportOffsetY = nextOffset.y
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = viewportScale
                    scaleY = viewportScale
                    translationX = viewportOffsetX
                    translationY = viewportOffsetY
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            FarmIsometricGround(
                rows = rows,
                columns = columns,
                tilesByCell = tilesByCell,
                selectedCell = selectedCell
            )
            tilesByCell.values.forEach { tile ->
                key(tile.overlayKey) {
                    val insight = tile.batchId?.let { insightsByBatch[it] }
                    FarmPlacementOverlay(
                        tile = tile,
                        insight = insight,
                        rows = rows,
                        columns = columns,
                        boardWidth = boardWidth,
                        boardHeight = sceneHeight,
                        selected = selectedCell == tile.row to tile.column,
                        plotHighlighted = false,
                        dragOffset = if (draggingTileKey == tile.overlayKey) dragOffset else Offset.Zero
                    )
                }
            }
            (dragPreviewCell ?: selectedCell)?.let { (row, column) ->
                FarmTargetMarker(
                    row = row,
                    column = column,
                    rows = rows,
                    columns = columns,
                    boardWidth = boardWidth,
                    boardHeight = sceneHeight
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .zIndex(30f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FarmZoomButton(label = "+", onClick = { zoomViewport(viewportScale * 1.25f) })
            FarmZoomButton(label = "-", onClick = { zoomViewport(viewportScale / 1.25f) })
        }
    }
}

@Composable
private fun FarmZoomButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFFFF8E7).copy(alpha = 0.90f),
        shadowElevation = 5.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color(0xFF2F6F4E), fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun FarmIsometricGround(
    rows: Int,
    columns: Int,
    tilesByCell: Map<Pair<Int, Int>, FarmTile>,
    selectedCell: Pair<Int, Int>?
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF9ED8F1),
                    Color(0xFFC9EB9A),
                    Color(0xFF82CF50),
                    Color(0xFF58A73B)
                )
            )
        )
        drawCircle(
            color = Color(0xFFFFF2B0).copy(alpha = 0.66f),
            radius = size.minDimension * 0.12f,
            center = Offset(size.width * 0.16f, size.height * 0.10f)
        )
        val backHill = Path().apply {
            moveTo(0f, size.height * 0.28f)
            cubicTo(size.width * 0.20f, size.height * 0.18f, size.width * 0.33f, size.height * 0.34f, size.width * 0.52f, size.height * 0.24f)
            cubicTo(size.width * 0.72f, size.height * 0.14f, size.width * 0.84f, size.height * 0.30f, size.width, size.height * 0.22f)
            lineTo(size.width, size.height * 0.48f)
            lineTo(0f, size.height * 0.48f)
            close()
        }
        drawPath(backHill, Color(0xFF6CBF72).copy(alpha = 0.58f))
        val frontHill = Path().apply {
            moveTo(0f, size.height * 0.38f)
            cubicTo(size.width * 0.18f, size.height * 0.28f, size.width * 0.36f, size.height * 0.46f, size.width * 0.54f, size.height * 0.34f)
            cubicTo(size.width * 0.72f, size.height * 0.24f, size.width * 0.88f, size.height * 0.42f, size.width, size.height * 0.32f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(frontHill, Color(0xFF92D75A).copy(alpha = 0.74f))
        drawOval(
            color = Color(0xFF2D6D35).copy(alpha = 0.16f),
            topLeft = Offset(size.width * 0.02f, size.height * 0.18f),
            size = Size(size.width * 0.24f, size.height * 0.28f)
        )
        drawOval(
            color = Color(0xFF2D6D35).copy(alpha = 0.14f),
            topLeft = Offset(size.width * 0.72f, size.height * 0.18f),
            size = Size(size.width * 0.26f, size.height * 0.30f)
        )

        val widthToHeight = size.width / size.height.coerceAtLeast(1f)
        val metrics = farmIsoMetrics(rows, columns, widthToHeight)
        val halfTileWidth = size.width * metrics.xStep
        val halfTileHeight = size.height * metrics.yStep
        val sideDepth = halfTileHeight * 0.38f

        val cornerAnchors = listOf(
            farmAnchor(0, 0, rows, columns, widthToHeight),
            farmAnchor(0, columns - 1, rows, columns, widthToHeight),
            farmAnchor(rows - 1, columns - 1, rows, columns, widthToHeight),
            farmAnchor(rows - 1, 0, rows, columns, widthToHeight)
        )
        val topPoint = Offset(size.width * cornerAnchors[0].first, size.height * cornerAnchors[0].second - halfTileHeight)
        val rightPoint = Offset(size.width * cornerAnchors[1].first + halfTileWidth, size.height * cornerAnchors[1].second)
        val bottomPoint = Offset(size.width * cornerAnchors[2].first, size.height * cornerAnchors[2].second + halfTileHeight)
        val leftPoint = Offset(size.width * cornerAnchors[3].first - halfTileWidth, size.height * cornerAnchors[3].second)
        val fieldTopPath = farmQuadPath(topPoint, rightPoint, bottomPoint, leftPoint)
        val fieldShadowPath = farmQuadPath(
            topPoint + Offset(0f, sideDepth * 1.7f),
            rightPoint + Offset(0f, sideDepth * 1.7f),
            bottomPoint + Offset(0f, sideDepth * 1.7f),
            leftPoint + Offset(0f, sideDepth * 1.7f)
        )

        drawPath(fieldShadowPath, Color(0xFF24522C).copy(alpha = 0.32f))
        drawPath(
            farmQuadPath(leftPoint, bottomPoint, bottomPoint + Offset(0f, sideDepth), leftPoint + Offset(0f, sideDepth)),
            Color(0xFF4B813A)
        )
        drawPath(
            farmQuadPath(bottomPoint, rightPoint, rightPoint + Offset(0f, sideDepth), bottomPoint + Offset(0f, sideDepth)),
            Color(0xFF3D7334)
        )
        drawPath(fieldTopPath, Color(0xFF6DBD45))

        val lastLayer = rows + columns - 2
        for (layer in 0..lastLayer) {
            for (row in 0 until rows) {
                val column = layer - row
                if (column !in 0 until columns) continue
                val tile = tilesByCell[row to column]
                val anchor = farmAnchor(row, column, rows, columns, widthToHeight)
                val center = Offset(size.width * anchor.first, size.height * anchor.second)
                val tilePath = farmDiamondPath(center, halfTileWidth, halfTileHeight)
                drawPath(
                    path = tilePath,
                    color = farmGroundTileColor(tile?.type, row, column)
                )
                drawFarmTileFurrows(
                    center = center,
                    halfWidth = halfTileWidth,
                    halfHeight = halfTileHeight,
                    type = tile?.type
                )
                drawPath(
                    path = tilePath,
                    color = Color.White.copy(alpha = 0.30f),
                    style = Stroke(width = 1.1.dp.toPx())
                )
                drawPath(
                    path = tilePath,
                    color = Color(0xFF5A7B34).copy(alpha = 0.26f),
                    style = Stroke(width = 0.8.dp.toPx())
                )
                if (selectedCell == row to column) {
                    drawPath(
                        path = tilePath,
                        color = Color(0xFFFFF0A5),
                        style = Stroke(width = 3.2.dp.toPx())
                    )
                }
            }
        }

        drawPath(
            path = fieldTopPath,
            color = Color(0xFF2E6B32).copy(alpha = 0.72f),
            style = Stroke(width = 2.4.dp.toPx())
        )

        repeat(18) { index ->
            val side = index % 3
            val x = when (side) {
                0 -> size.width * (0.03f + (index % 5) * 0.035f)
                1 -> size.width * (0.82f + (index % 5) * 0.035f)
                else -> size.width * (0.10f + (index % 7) * 0.13f)
            }
            val y = when (side) {
                0 -> size.height * (0.34f + (index % 5) * 0.10f)
                1 -> size.height * (0.30f + (index % 6) * 0.09f)
                else -> size.height * (0.88f + (index % 2) * 0.04f)
            }
            drawCircle(
                color = Color(0xFF2E7A37).copy(alpha = 0.24f),
                radius = size.minDimension * (0.020f + (index % 3) * 0.006f),
                center = Offset(x, y)
            )
        }
    }
}

private fun farmQuadPath(first: Offset, second: Offset, third: Offset, fourth: Offset): Path {
    return Path().apply {
        moveTo(first.x, first.y)
        lineTo(second.x, second.y)
        lineTo(third.x, third.y)
        lineTo(fourth.x, fourth.y)
        close()
    }
}

private fun farmDiamondPath(center: Offset, halfWidth: Float, halfHeight: Float): Path {
    return Path().apply {
        moveTo(center.x, center.y - halfHeight)
        lineTo(center.x + halfWidth, center.y)
        lineTo(center.x, center.y + halfHeight)
        lineTo(center.x - halfWidth, center.y)
        close()
    }
}

private fun farmDiamondLeftSidePath(center: Offset, halfWidth: Float, halfHeight: Float, depth: Float): Path {
    return farmQuadPath(
        Offset(center.x - halfWidth, center.y),
        Offset(center.x, center.y + halfHeight),
        Offset(center.x, center.y + halfHeight + depth),
        Offset(center.x - halfWidth, center.y + depth)
    )
}

private fun farmDiamondRightSidePath(center: Offset, halfWidth: Float, halfHeight: Float, depth: Float): Path {
    return farmQuadPath(
        Offset(center.x, center.y + halfHeight),
        Offset(center.x + halfWidth, center.y),
        Offset(center.x + halfWidth, center.y + depth),
        Offset(center.x, center.y + halfHeight + depth)
    )
}

private fun farmGroundTileColor(type: FarmTileType?, row: Int, column: Int): Color {
    return when (type) {
        FarmTileType.RAISED_BED -> if ((row + column) % 2 == 0) Color(0xFF7A4B2C) else Color(0xFF6E4328)
        FarmTileType.GREENHOUSE, FarmTileType.TOOL_SHED, FarmTileType.SIGN -> Color(0xFF7FCA4F)
        else -> if ((row + column) % 2 == 0) Color(0xFF8ED35C) else Color(0xFF7EC64F)
    }
}

private fun farmGroundTileSideColor(type: FarmTileType?, row: Int, column: Int, isRight: Boolean): Color {
    return when (type) {
        FarmTileType.GREENHOUSE, FarmTileType.TOOL_SHED, FarmTileType.SIGN -> {
            if (isRight) Color(0xFF4E8C3D) else Color(0xFF609F45)
        }

        FarmTileType.RAISED_BED -> {
            if (isRight) Color(0xFF5A351F) else Color(0xFF684025)
        }

        else -> {
            if ((row + column) % 2 == 0) {
                if (isRight) Color(0xFF5B963E) else Color(0xFF68A848)
            } else {
                if (isRight) Color(0xFF528D39) else Color(0xFF609D43)
            }
        }
    }
}

private fun DrawScope.drawFarmTileFurrows(
    center: Offset,
    halfWidth: Float,
    halfHeight: Float,
    type: FarmTileType?
) {
    if (type == FarmTileType.GREENHOUSE || type == FarmTileType.TOOL_SHED || type == FarmTileType.SIGN) {
        drawLine(
            color = Color.White.copy(alpha = 0.18f),
            start = Offset(center.x - halfWidth * 0.44f, center.y),
            end = Offset(center.x, center.y + halfHeight * 0.45f),
            strokeWidth = 1.4f,
            cap = StrokeCap.Round
        )
        return
    }
    val lineColor = Color(0xFF6D4428).copy(alpha = 0.26f)
    listOf(-0.36f, -0.10f, 0.16f).forEach { offset ->
        drawLine(
            color = lineColor,
            start = Offset(center.x - halfWidth * 0.42f, center.y + halfHeight * offset),
            end = Offset(center.x + halfWidth * 0.18f, center.y + halfHeight * (offset + 0.50f)),
            strokeWidth = 1.4f,
            cap = StrokeCap.Round
        )
    }
}

private fun boardToFarmSceneOffset(
    position: Offset,
    viewportOffset: Offset,
    viewportScale: Float
): Offset {
    if (viewportScale <= 0f) return position
    return Offset(
        x = (position.x - viewportOffset.x) / viewportScale,
        y = (position.y - viewportOffset.y) / viewportScale
    )
}

private fun clampFarmViewportOffset(
    offset: Offset,
    scale: Float,
    boardWidthPx: Float,
    boardHeightPx: Float
): Offset {
    if (scale <= FARM_VIEWPORT_MIN_SCALE || boardWidthPx <= 0f || boardHeightPx <= 0f) {
        return Offset.Zero
    }
    val minX = boardWidthPx * (1f - scale)
    val minY = boardHeightPx * (1f - scale)
    return Offset(
        x = offset.x.coerceIn(minX, 0f),
        y = offset.y.coerceIn(minY, 0f)
    )
}

private fun pointerCentroid(
    changes: List<PointerInputChange>,
    usePrevious: Boolean
): Offset {
    if (changes.isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    changes.forEach { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        x += position.x
        y += position.y
    }
    return Offset(x / changes.size, y / changes.size)
}

private fun pointerAverageDistance(
    changes: List<PointerInputChange>,
    centroid: Offset,
    usePrevious: Boolean
): Float {
    if (changes.isEmpty()) return 0f
    var totalDistance = 0f
    changes.forEach { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        totalDistance += (position - centroid).getDistance()
    }
    return totalDistance / changes.size
}

private val FarmTile.overlayKey: String
    get() = listOf(row, column, type.name, batchId.orEmpty()).joinToString(":")

@Composable
private fun FarmPlacementOverlay(
    tile: FarmTile,
    insight: PlantingInsight?,
    rows: Int,
    columns: Int,
    boardWidth: Dp,
    boardHeight: Dp,
    selected: Boolean,
    plotHighlighted: Boolean,
    dragOffset: Offset
) {
    val density = LocalDensity.current
    val anchor = farmAnchor(
        tile.row,
        tile.column,
        rows,
        columns,
        boardWidth.value / boardHeight.value.coerceAtLeast(1f)
    )
    val asset = farmAssetFor(tile, insight)
    val assetWidth = farmAssetWidth(tile.type)
    val assetHeight = farmAssetHeight(tile.type)
    val touchWidth = farmTouchWidth(tile.type)
    val touchHeight = farmTouchHeight(tile.type)
    val touchInsetX = (touchWidth - assetWidth) / 2
    val touchInsetY = (touchHeight - assetHeight) / 2
    val dragXDp = with(density) { dragOffset.x.toDp() }
    val dragYDp = with(density) { dragOffset.y.toDp() }
    val baseZ = 2f + (tile.row + tile.column) * 0.12f

    if (asset != null) {
        Box(
            modifier = Modifier
                .offset(
                    x = boardWidth * anchor.first - assetWidth / 2 - touchInsetX + dragXDp,
                    y = boardHeight * anchor.second - farmAssetAnchorOffset(tile.type) - touchInsetY + dragYDp
                )
                .width(touchWidth)
                .height(touchHeight)
                .zIndex(if (selected || plotHighlighted || dragOffset != Offset.Zero) 12f else baseZ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(assetWidth)
                    .height(assetHeight),
                contentAlignment = Alignment.Center
            ) {
                if (selected || plotHighlighted) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawOval(
                            color = Color(0xFFFFF8E7).copy(alpha = 0.66f),
                            topLeft = Offset(size.width * 0.08f, size.height * 0.6f),
                            size = Size(size.width * 0.84f, size.height * 0.28f)
                        )
                        drawOval(
                            color = Color(0xFF2F6F4E).copy(alpha = 0.84f),
                            topLeft = Offset(size.width * 0.08f, size.height * 0.6f),
                            size = Size(size.width * 0.84f, size.height * 0.28f),
                            style = Stroke(width = 3f)
                        )
                    }
                }
                if (tile.type == FarmTileType.RAISED_BED) {
                    GrowthCropPlotAsset(
                        insight = insight,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = tile.rotationDegrees
                            }
                    )
                } else if (
                    tile.type == FarmTileType.GREENHOUSE ||
                    tile.type == FarmTileType.TOOL_SHED ||
                    tile.type == FarmTileType.SIGN
                ) {
                    IsometricStructureAsset(
                        type = tile.type,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = tile.rotationDegrees
                            }
                    )
                } else {
                    Image(
                        painter = painterResource(asset),
                        contentDescription = insight?.crop?.name ?: tile.type.label,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = tile.rotationDegrees
                            },
                        contentScale = ContentScale.Fit
                    )
                }
                if (insight != null && (selected || plotHighlighted)) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = Color(0xFFFFF8E7).copy(alpha = 0.94f),
                        shadowElevation = 3.dp
                    ) {
                        Text(
                            "${insight.crop.name} ${insight.progressPercent}%",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF315437),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        FarmStructureOverlay(
            type = tile.type,
            x = boardWidth * anchor.first - 20.dp,
            y = boardHeight * anchor.second - 16.dp,
            selected = selected
        )
    }
}

@DrawableRes
private fun farmAssetFor(tile: FarmTile, insight: PlantingInsight?): Int? {
    return when (tile.type) {
        FarmTileType.RAISED_BED -> insight?.let { cropBedAsset(it) } ?: R.drawable.farm_asset_bed_leafy
        FarmTileType.PATH -> R.drawable.farm_asset_path
        FarmTileType.GREENHOUSE -> R.drawable.farm_asset_greenhouse
        FarmTileType.TOOL_SHED -> R.drawable.farm_asset_tool_shed
        FarmTileType.FENCE -> R.drawable.farm_asset_fence
        FarmTileType.IRRIGATION -> R.drawable.farm_asset_irrigation
        FarmTileType.SIGN -> R.drawable.farm_asset_sign
        FarmTileType.GRASS -> null
    }
}

@DrawableRes
private fun cropBedAsset(insight: PlantingInsight): Int {
    val category = insight.crop.category
    val cropName = insight.crop.name
    return when {
        category.contains("瓜") || category.contains("豆") || cropName == "黄瓜" -> R.drawable.farm_asset_bed_vine
        category.contains("根菜") || cropName == "胡萝卜" || cropName == "萝卜" -> R.drawable.farm_asset_bed_root
        category.contains("茄果") || cropName == "番茄" || cropName == "辣椒" || cropName == "茄子" -> R.drawable.farm_asset_bed_tomato
        else -> R.drawable.farm_asset_bed_leafy
    }
}

private fun farmAssetWidth(type: FarmTileType) = when (type) {
    FarmTileType.RAISED_BED -> 88.dp
    FarmTileType.GREENHOUSE -> 82.dp
    FarmTileType.TOOL_SHED -> 78.dp
    FarmTileType.PATH -> 54.dp
    FarmTileType.IRRIGATION -> 66.dp
    FarmTileType.FENCE -> 52.dp
    FarmTileType.SIGN -> 46.dp
    FarmTileType.GRASS -> 42.dp
}

private fun farmAssetHeight(type: FarmTileType) = when (type) {
    FarmTileType.RAISED_BED -> 68.dp
    FarmTileType.GREENHOUSE -> 72.dp
    FarmTileType.TOOL_SHED -> 74.dp
    FarmTileType.PATH -> 40.dp
    FarmTileType.IRRIGATION -> 30.dp
    FarmTileType.FENCE -> 40.dp
    FarmTileType.SIGN -> 58.dp
    FarmTileType.GRASS -> 42.dp
}

private fun farmTouchWidth(type: FarmTileType) = when (type) {
    FarmTileType.RAISED_BED -> 104.dp
    FarmTileType.GREENHOUSE -> 100.dp
    FarmTileType.TOOL_SHED -> 96.dp
    FarmTileType.PATH -> 96.dp
    FarmTileType.IRRIGATION -> 150.dp
    FarmTileType.FENCE -> 84.dp
    FarmTileType.SIGN -> 82.dp
    FarmTileType.GRASS -> 42.dp
}

private fun farmTouchHeight(type: FarmTileType) = when (type) {
    FarmTileType.RAISED_BED -> 82.dp
    FarmTileType.GREENHOUSE -> 86.dp
    FarmTileType.TOOL_SHED -> 88.dp
    FarmTileType.PATH -> 72.dp
    FarmTileType.IRRIGATION -> 96.dp
    FarmTileType.FENCE -> 64.dp
    FarmTileType.SIGN -> 88.dp
    FarmTileType.GRASS -> 42.dp
}

private fun farmAssetAnchorOffset(type: FarmTileType) = when (type) {
    FarmTileType.RAISED_BED -> 42.dp
    FarmTileType.GREENHOUSE -> 48.dp
    FarmTileType.TOOL_SHED -> 50.dp
    FarmTileType.PATH -> 22.dp
    FarmTileType.IRRIGATION -> 15.dp
    FarmTileType.FENCE -> 27.dp
    FarmTileType.SIGN -> 42.dp
    FarmTileType.GRASS -> 20.dp
}

@Composable
private fun GrowthCropPlotAsset(
    insight: PlantingInsight?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawIsometricCropPlot(insight)
    }
}

@Composable
private fun IsometricStructureAsset(
    type: FarmTileType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (type) {
            FarmTileType.GREENHOUSE -> drawIsometricGreenhouseAsset()
            FarmTileType.TOOL_SHED -> drawIsometricToolShedAsset()
            FarmTileType.SIGN -> drawIsometricSignAsset()
            else -> Unit
        }
    }
}

@Composable
private fun FarmStructureOverlay(
    type: FarmTileType,
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    selected: Boolean
) {
    val overlayAlpha = when (type) {
        FarmTileType.PATH, FarmTileType.IRRIGATION, FarmTileType.FENCE -> 0.72f
        FarmTileType.GREENHOUSE, FarmTileType.TOOL_SHED, FarmTileType.SIGN -> 0.92f
        else -> 0.72f
    }
    Canvas(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(if (type == FarmTileType.GREENHOUSE || type == FarmTileType.TOOL_SHED) 46.dp else 38.dp)
    ) {
        if (selected) {
            drawCircle(Color(0xFFFFF8E7).copy(alpha = 0.84f), size.minDimension * 0.58f, center)
        }
        when (type) {
            FarmTileType.PATH -> drawStonePath()
            FarmTileType.IRRIGATION -> drawIrrigation()
            FarmTileType.GREENHOUSE -> drawGreenhouse()
            FarmTileType.TOOL_SHED -> drawToolShed()
            FarmTileType.FENCE -> drawFence()
            FarmTileType.SIGN -> drawVegetableSign()
            FarmTileType.RAISED_BED -> drawRaisedBed(null)
            FarmTileType.GRASS -> drawGrassDetails()
        }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f * overlayAlpha),
            size = size,
            cornerRadius = CornerRadius(14f, 14f)
        )
    }
}

@Composable
private fun FarmTargetMarker(
    row: Int,
    column: Int,
    rows: Int,
    columns: Int,
    boardWidth: androidx.compose.ui.unit.Dp,
    boardHeight: androidx.compose.ui.unit.Dp
) {
    val anchor = farmAnchor(
        row,
        column,
        rows,
        columns,
        boardWidth.value / boardHeight.value.coerceAtLeast(1f)
    )
    Canvas(
        modifier = Modifier
            .offset(
                x = boardWidth * anchor.first - 24.dp,
                y = boardHeight * anchor.second - 16.dp
            )
            .size(width = 48.dp, height = 32.dp)
    ) {
        val path = farmDiamondPath(center, size.width * 0.48f, size.height * 0.44f)
        drawPath(path, Color(0xFFFFF8E7).copy(alpha = 0.76f))
        drawPath(
            path,
            Color(0xFF2F6F4E),
            style = Stroke(width = 3f)
        )
    }
}

private fun farmAnchor(
    row: Int,
    column: Int,
    rows: Int,
    columns: Int,
    widthToHeight: Float
): Pair<Float, Float> {
    val metrics = farmIsoMetrics(rows, columns, widthToHeight)
    val x = metrics.centerX + (column - row) * metrics.xStep
    val y = metrics.top + (column + row) * metrics.yStep
    return x to y
}

private fun hitTestFarmTile(
    position: Offset,
    tiles: Collection<FarmTile>,
    rows: Int,
    columns: Int,
    boardWidthPx: Float,
    boardHeightPx: Float,
    density: Density
): FarmTile? {
    if (boardWidthPx <= 0f || boardHeightPx <= 0f) return null
    return tiles
        .filter { farmAssetFor(it, null) != null }
        .mapNotNull { tile ->
            val anchor = farmAnchor(
                tile.row,
                tile.column,
                rows,
                columns,
                boardWidthPx / boardHeightPx.coerceAtLeast(1f)
            )
            val assetWidthPx = with(density) { farmAssetWidth(tile.type).toPx() }
            val assetHeightPx = with(density) { farmAssetHeight(tile.type).toPx() }
            val touchWidthPx = with(density) { farmTouchWidth(tile.type).toPx() }
            val touchHeightPx = with(density) { farmTouchHeight(tile.type).toPx() }
            val touchInsetX = (touchWidthPx - assetWidthPx) / 2f
            val touchInsetY = (touchHeightPx - assetHeightPx) / 2f
            val left = boardWidthPx * anchor.first - assetWidthPx / 2f - touchInsetX
            val top = boardHeightPx * anchor.second - with(density) { farmAssetAnchorOffset(tile.type).toPx() } - touchInsetY
            val right = left + touchWidthPx
            val bottom = top + touchHeightPx
            if (position.x in left..right && position.y in top..bottom) {
                val centerX = (left + right) / 2f
                val centerY = (top + bottom) / 2f
                val distanceScore = -((position.x - centerX) * (position.x - centerX) + (position.y - centerY) * (position.y - centerY))
                tile to (farmInteractionPriority(tile.type) * 100000f + (tile.row + tile.column) * 100f + distanceScore / 10000f)
            } else {
                null
            }
        }
        .maxByOrNull { it.second }
        ?.first
}

private fun farmInteractionPriority(type: FarmTileType) = when (type) {
    FarmTileType.SIGN -> 7
    FarmTileType.IRRIGATION -> 6
    FarmTileType.GREENHOUSE, FarmTileType.TOOL_SHED -> 5
    FarmTileType.RAISED_BED -> 4
    FarmTileType.FENCE -> 3
    FarmTileType.PATH -> 2
    FarmTileType.GRASS -> 0
}

private fun gridCellForBoardOffset(
    x: Float,
    y: Float,
    boardWidthPx: Float,
    boardHeightPx: Float,
    rows: Int,
    columns: Int
): Pair<Int, Int> {
    if (boardWidthPx <= 0f || boardHeightPx <= 0f) return 0 to 0
    val metrics = farmIsoMetrics(rows, columns, boardWidthPx / boardHeightPx.coerceAtLeast(1f))
    val normalizedX = x / boardWidthPx
    val normalizedY = y / boardHeightPx
    val sum = (normalizedY - metrics.top) / metrics.yStep
    val diff = (normalizedX - metrics.centerX) / metrics.xStep
    val row = ((sum - diff) / 2f).roundToInt().coerceIn(0, rows - 1)
    val column = ((sum + diff) / 2f).roundToInt().coerceIn(0, columns - 1)
    return row to column
}

private fun gridCellForDraggedAnchor(
    anchorX: Float,
    anchorY: Float,
    dragX: Float,
    dragY: Float,
    boardWidthPx: Float,
    boardHeightPx: Float,
    rows: Int,
    columns: Int
): Pair<Int, Int> {
    if (boardWidthPx <= 0f || boardHeightPx <= 0f) return 0 to 0
    val y = anchorY + dragY / boardHeightPx
    val x = anchorX + dragX / boardWidthPx
    val metrics = farmIsoMetrics(rows, columns, boardWidthPx / boardHeightPx.coerceAtLeast(1f))
    val sum = (y - metrics.top) / metrics.yStep
    val diff = (x - metrics.centerX) / metrics.xStep
    val row = ((sum - diff) / 2f).roundToInt().coerceIn(0, rows - 1)
    val column = ((sum + diff) / 2f).roundToInt().coerceIn(0, columns - 1)
    return row to column
}

@Composable
private fun FarmCellCanvas(
    tile: FarmTile?,
    insight: PlantingInsight?,
    selected: Boolean,
    plotHighlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawCellBase(selected = selected, plotHighlighted = plotHighlighted)
        when (tile?.type) {
            null, FarmTileType.GRASS -> drawGrassDetails()
            FarmTileType.PATH -> drawStonePath()
            FarmTileType.RAISED_BED -> drawRaisedBed(insight)
            FarmTileType.GREENHOUSE -> drawGreenhouse()
            FarmTileType.TOOL_SHED -> drawToolShed()
            FarmTileType.FENCE -> drawFence()
            FarmTileType.IRRIGATION -> drawIrrigation()
            FarmTileType.SIGN -> drawVegetableSign()
        }
    }
}

private fun DrawScope.drawCellBase(selected: Boolean, plotHighlighted: Boolean) {
    val radius = CornerRadius(12f, 12f)
    drawRoundRect(
        color = Color(0xFF66B957),
        size = size,
        cornerRadius = radius
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.08f),
        topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
        size = Size(size.width * 0.84f, size.height * 0.84f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    if (plotHighlighted) {
        drawRoundRect(
            color = Color(0xFFFFF1B8).copy(alpha = 0.52f),
            size = size,
            cornerRadius = radius
        )
    }
    if (selected) {
        drawRoundRect(
            color = Color(0xFFFFF8E7),
            topLeft = Offset(2f, 2f),
            size = Size(size.width - 4f, size.height - 4f),
            cornerRadius = radius,
            style = Stroke(width = 4f)
        )
    }
}

private fun DrawScope.drawGrassDetails() {
    val bladeColor = Color(0xFF4F9F46).copy(alpha = 0.55f)
    repeat(4) { index ->
        val x = size.width * (0.18f + index * 0.18f)
        val y = size.height * (0.62f + (index % 2) * 0.12f)
        drawLine(bladeColor, Offset(x, y), Offset(x + 3f, y - 9f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawStonePath() {
    val stones = listOf(
        Offset(size.width * 0.18f, size.height * 0.38f),
        Offset(size.width * 0.46f, size.height * 0.28f),
        Offset(size.width * 0.58f, size.height * 0.58f)
    )
    stones.forEachIndexed { index, offset ->
        drawRoundRect(
            color = if (index == 1) Color(0xFFE5D4B1) else Color(0xFFD9C69F),
            topLeft = offset,
            size = Size(size.width * 0.28f, size.height * 0.18f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = offset + Offset(3f, 2f),
            size = Size(size.width * 0.16f, size.height * 0.05f),
            cornerRadius = CornerRadius(6f, 6f)
        )
    }
}

private fun DrawScope.drawRaisedBed(insight: PlantingInsight?) {
    drawRoundRect(
        color = Color(0xFF8B5B35),
        topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
        size = Size(size.width * 0.76f, size.height * 0.66f),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRoundRect(
        color = Color(0xFF5F3A24).copy(alpha = 0.45f),
        topLeft = Offset(size.width * 0.12f, size.height * 0.66f),
        size = Size(size.width * 0.76f, size.height * 0.16f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = Color(0xFF9C633B),
        topLeft = Offset(size.width * 0.18f, size.height * 0.22f),
        size = Size(size.width * 0.64f, size.height * 0.46f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    if (insight == null) {
        repeat(5) { index ->
            drawCircle(
                color = Color(0xFF5F3A24).copy(alpha = 0.32f),
                radius = 2.5f,
                center = Offset(size.width * (0.28f + index * 0.11f), size.height * 0.45f)
            )
        }
    } else {
        drawCropOnPlot(insight)
    }
}

private fun DrawScope.drawIsometricCropPlot(insight: PlantingInsight?) {
    drawOval(
        color = Color(0xFF1D2D17).copy(alpha = 0.10f),
        topLeft = Offset(size.width * 0.18f, size.height * 0.54f),
        size = Size(size.width * 0.64f, size.height * 0.18f)
    )

    if (insight == null) {
        repeat(5) { index ->
            val x = size.width * (0.32f + index * 0.09f)
            val y = size.height * (0.42f + (index % 2) * 0.08f)
            drawCircle(Color(0xFFB0703E).copy(alpha = 0.62f), size.minDimension * 0.030f, Offset(x, y))
        }
    } else {
        drawCropOnPlot(insight)
    }
}

private fun DrawScope.drawCropOnPlot(insight: PlantingInsight) {
    val progress = (insight.progressPercent / 100f).coerceIn(0.08f, 1f)
    val category = insight.crop.category
    val cropName = insight.crop.name
    val leaf = cropColor(category, cropName)
    val light = cropLightColor(category, cropName)
    val fruit = fruitColor(cropName)
    val baseY = size.height * 0.50f
    val plantRadius = (size.minDimension * (0.060f + progress * 0.120f)).coerceIn(5.0f, 17f)
    val stemHeight = size.height * (0.10f + progress * 0.30f)
    val plantPositions = listOf(0.24f, 0.37f, 0.50f, 0.63f, 0.76f)
    val plantCount = when {
        progress < 0.22f -> 2
        progress < 0.45f -> 3
        progress < 0.68f -> 4
        else -> 5
    }

    when {
        category.contains("茄果") || cropName == "番茄" || cropName == "辣椒" || cropName == "茄子" -> {
            plantPositions.take(plantCount).forEach { ratio ->
                val x = size.width * ratio
                drawLine(
                    Color(0xFF315B2C),
                    Offset(x, baseY + stemHeight * 0.54f),
                    Offset(x, baseY - stemHeight * 0.46f),
                    strokeWidth = (1.6f + progress * 2.4f),
                    cap = StrokeCap.Round
                )
                drawLeafCluster(Offset(x, baseY - stemHeight * 0.18f), plantRadius, leaf, light)
                if (progress > 0.58f) {
                    drawCircle(fruit, plantRadius * 0.36f, Offset(x + plantRadius * 0.6f, baseY - plantRadius * 0.1f))
                    drawCircle(Color.White.copy(alpha = 0.35f), plantRadius * 0.11f, Offset(x + plantRadius * 0.48f, baseY - plantRadius * 0.2f))
                }
            }
        }

        category.contains("瓜") || category.contains("豆") -> {
            val vineEnd = size.width * (0.28f + progress * 0.50f)
            drawLine(leaf, Offset(size.width * 0.22f, baseY + 10f), Offset(vineEnd, baseY - 9f), strokeWidth = 2.2f + progress * 2f, cap = StrokeCap.Round)
            plantPositions.take(plantCount).forEachIndexed { index, ratio ->
                val x = size.width * ratio
                val y = baseY + if (index % 2 == 0) 5f else -5f
                drawLeafCluster(Offset(x, y), plantRadius, leaf, light)
            }
        }

        category.contains("根菜") -> {
            plantPositions.take(plantCount).forEach { ratio ->
                val x = size.width * ratio
                if (progress > 0.34f) {
                    drawOval(
                        color = Color(0xFFE9A247),
                        topLeft = Offset(x - plantRadius * 0.42f, baseY + 3f),
                        size = Size(plantRadius * 0.84f, plantRadius * (1f + progress))
                    )
                }
                drawLeafCluster(Offset(x, baseY - 2f), plantRadius * 0.72f, leaf, light)
            }
        }

        else -> {
            plantPositions.take(plantCount).forEachIndexed { index, ratio ->
                val x = size.width * ratio
                val y = baseY + if (index % 2 == 0) -4f else 7f
                drawLeafCluster(Offset(x, y), plantRadius, leaf, light)
            }
        }
    }
}

private fun DrawScope.drawLeafCluster(center: Offset, radius: Float, color: Color, highlight: Color) {
    drawCircle(Color(0xFF283E21).copy(alpha = 0.12f), radius * 1.05f, center + Offset(radius * 0.2f, radius * 0.32f))
    val offsets = listOf(
        Offset(-0.45f, 0f),
        Offset(0f, -0.42f),
        Offset(0.45f, 0f),
        Offset(-0.16f, 0.34f),
        Offset(0.2f, 0.34f)
    )
    offsets.forEachIndexed { index, offset ->
        drawOval(
            color = if (index == 1) highlight else color,
            topLeft = center + Offset(offset.x * radius * 1.3f - radius * 0.42f, offset.y * radius * 1.2f - radius * 0.34f),
            size = Size(radius * 0.84f, radius * 0.68f)
        )
    }
}

private fun DrawScope.drawIsometricGreenhouseAsset() {
    drawOval(
        color = Color(0xFF223A28).copy(alpha = 0.16f),
        topLeft = Offset(size.width * 0.10f, size.height * 0.68f),
        size = Size(size.width * 0.78f, size.height * 0.20f)
    )
    val baseTop = Offset(size.width * 0.50f, size.height * 0.44f)
    val baseRight = Offset(size.width * 0.88f, size.height * 0.58f)
    val baseBottom = Offset(size.width * 0.50f, size.height * 0.78f)
    val baseLeft = Offset(size.width * 0.12f, size.height * 0.58f)
    val roofTop = Offset(size.width * 0.50f, size.height * 0.12f)
    val roofLeft = Offset(size.width * 0.18f, size.height * 0.38f)
    val roofRight = Offset(size.width * 0.82f, size.height * 0.38f)

    drawPath(farmQuadPath(baseLeft, baseBottom, baseRight, baseTop), Color(0xFFBFEFFF).copy(alpha = 0.34f))
    drawPath(farmQuadPath(roofTop, roofRight, baseRight, baseTop), Color(0xFFE5FAFF).copy(alpha = 0.58f))
    drawPath(farmQuadPath(roofTop, baseTop, baseLeft, roofLeft), Color(0xFFD6F7FF).copy(alpha = 0.48f))
    val frame = Color(0xFF6F8178)
    listOf(
        roofTop to roofLeft,
        roofTop to roofRight,
        roofLeft to baseLeft,
        roofRight to baseRight,
        baseLeft to baseBottom,
        baseBottom to baseRight,
        baseLeft to baseTop,
        baseTop to baseRight
    ).forEach { (start, end) ->
        drawLine(frame, start, end, strokeWidth = 2.1f, cap = StrokeCap.Round)
    }
    listOf(0.36f, 0.50f, 0.64f).forEach { x ->
        drawLine(
            Color.White.copy(alpha = 0.58f),
            Offset(size.width * x, size.height * 0.24f),
            Offset(size.width * x, size.height * 0.70f),
            strokeWidth = 1.2f
        )
    }
    drawCircle(Color(0xFFFFB94F).copy(alpha = 0.68f), size.minDimension * 0.045f, Offset(size.width * 0.64f, size.height * 0.58f))
    drawLeafCluster(Offset(size.width * 0.42f, size.height * 0.62f), size.minDimension * 0.07f, Color(0xFF4E9C48), Color(0xFF9BD967))
}

private fun DrawScope.drawIsometricToolShedAsset() {
    drawOval(
        color = Color(0xFF223A28).copy(alpha = 0.18f),
        topLeft = Offset(size.width * 0.12f, size.height * 0.68f),
        size = Size(size.width * 0.74f, size.height * 0.20f)
    )
    val frontTopLeft = Offset(size.width * 0.30f, size.height * 0.42f)
    val frontTopRight = Offset(size.width * 0.66f, size.height * 0.52f)
    val frontBottomRight = Offset(size.width * 0.66f, size.height * 0.76f)
    val frontBottomLeft = Offset(size.width * 0.30f, size.height * 0.66f)
    val sideTopRight = Offset(size.width * 0.82f, size.height * 0.42f)
    val sideBottomRight = Offset(size.width * 0.82f, size.height * 0.66f)
    val roofPeak = Offset(size.width * 0.52f, size.height * 0.18f)
    val roofLeft = Offset(size.width * 0.20f, size.height * 0.38f)
    val roofFront = Offset(size.width * 0.66f, size.height * 0.52f)
    val roofRight = Offset(size.width * 0.84f, size.height * 0.38f)

    drawPath(farmQuadPath(frontTopLeft, frontTopRight, frontBottomRight, frontBottomLeft), Color(0xFFA46B3D))
    drawPath(farmQuadPath(frontTopRight, sideTopRight, sideBottomRight, frontBottomRight), Color(0xFF875735))
    drawPath(farmQuadPath(roofLeft, roofPeak, roofFront, frontTopLeft), Color(0xFF799274))
    drawPath(farmQuadPath(roofPeak, roofRight, sideTopRight, roofFront), Color(0xFF5F7D67))
    drawLine(Color(0xFF6D4529), Offset(size.width * 0.48f, size.height * 0.47f), Offset(size.width * 0.48f, size.height * 0.72f), strokeWidth = 2.3f)
    drawLine(Color(0xFFC9965D).copy(alpha = 0.6f), Offset(size.width * 0.34f, size.height * 0.49f), Offset(size.width * 0.61f, size.height * 0.57f), strokeWidth = 1.4f)
    drawCircle(Color(0xFFE7CFA1), size.minDimension * 0.018f, Offset(size.width * 0.56f, size.height * 0.62f))
}

private fun DrawScope.drawIsometricSignAsset() {
    drawOval(
        color = Color(0xFF223A28).copy(alpha = 0.14f),
        topLeft = Offset(size.width * 0.28f, size.height * 0.72f),
        size = Size(size.width * 0.46f, size.height * 0.14f)
    )
    drawLine(
        color = Color(0xFF8A5B35),
        start = Offset(size.width * 0.50f, size.height * 0.46f),
        end = Offset(size.width * 0.50f, size.height * 0.78f),
        strokeWidth = 5f,
        cap = StrokeCap.Round
    )
    val board = farmQuadPath(
        Offset(size.width * 0.24f, size.height * 0.24f),
        Offset(size.width * 0.72f, size.height * 0.32f),
        Offset(size.width * 0.72f, size.height * 0.54f),
        Offset(size.width * 0.24f, size.height * 0.46f)
    )
    drawPath(board, Color(0xFFFFE9AF))
    drawPath(board, Color(0xFF9A6B3D), style = Stroke(width = 2.3f))
    drawCircle(Color(0xFFE9573F), size.minDimension * 0.070f, Offset(size.width * 0.46f, size.height * 0.38f))
    drawOval(
        color = Color(0xFF4F9A59),
        topLeft = Offset(size.width * 0.52f, size.height * 0.28f),
        size = Size(size.width * 0.14f, size.height * 0.08f)
    )
}

private fun DrawScope.drawGreenhouse() {
    drawRoundRect(
        color = Color(0xFF6A7D70),
        topLeft = Offset(size.width * 0.14f, size.height * 0.52f),
        size = Size(size.width * 0.72f, size.height * 0.26f),
        cornerRadius = CornerRadius(7f, 7f)
    )
    drawRoundRect(
        color = Color(0xFFDDF5FF).copy(alpha = 0.78f),
        topLeft = Offset(size.width * 0.17f, size.height * 0.23f),
        size = Size(size.width * 0.66f, size.height * 0.42f),
        cornerRadius = CornerRadius(18f, 18f),
        style = Stroke(width = 5f)
    )
    drawRoundRect(
        color = Color(0xFFDDF5FF).copy(alpha = 0.42f),
        topLeft = Offset(size.width * 0.2f, size.height * 0.28f),
        size = Size(size.width * 0.6f, size.height * 0.35f),
        cornerRadius = CornerRadius(16f, 16f)
    )
    drawLine(Color.White.copy(alpha = 0.7f), Offset(size.width * 0.5f, size.height * 0.26f), Offset(size.width * 0.5f, size.height * 0.65f), strokeWidth = 2f)
}

private fun DrawScope.drawToolShed() {
    drawRoundRect(
        color = Color(0xFF9B6B3D),
        topLeft = Offset(size.width * 0.22f, size.height * 0.34f),
        size = Size(size.width * 0.56f, size.height * 0.38f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = Color(0xFF6F8A79),
        topLeft = Offset(size.width * 0.18f, size.height * 0.22f),
        size = Size(size.width * 0.64f, size.height * 0.18f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawLine(Color(0xFF6D4529), Offset(size.width * 0.5f, size.height * 0.36f), Offset(size.width * 0.5f, size.height * 0.72f), strokeWidth = 3f)
}

private fun DrawScope.drawFence() {
    repeat(4) { index ->
        val x = size.width * (0.17f + index * 0.18f)
        drawRoundRect(
            color = Color(0xFFAA7441),
            topLeft = Offset(x, size.height * 0.24f),
            size = Size(size.width * 0.09f, size.height * 0.52f),
            cornerRadius = CornerRadius(5f, 5f)
        )
    }
    drawLine(Color(0xFF875B32), Offset(size.width * 0.14f, size.height * 0.4f), Offset(size.width * 0.86f, size.height * 0.4f), strokeWidth = 5f, cap = StrokeCap.Round)
    drawLine(Color(0xFF875B32), Offset(size.width * 0.14f, size.height * 0.6f), Offset(size.width * 0.86f, size.height * 0.6f), strokeWidth = 5f, cap = StrokeCap.Round)
}

private fun DrawScope.drawIrrigation() {
    drawLine(
        Color(0xFF2D7FA7),
        Offset(size.width * 0.15f, size.height * 0.58f),
        Offset(size.width * 0.86f, size.height * 0.36f),
        strokeWidth = 5f,
        cap = StrokeCap.Round
    )
    repeat(4) { index ->
        val x = size.width * (0.24f + index * 0.16f)
        val y = size.height * (0.55f - index * 0.04f)
        drawCircle(Color(0xFFDDF5FF), 5f, Offset(x, y))
        drawCircle(Color(0xFF4AAAD0), 2.8f, Offset(x, y))
    }
}

private fun DrawScope.drawVegetableSign() {
    drawRoundRect(
        color = Color(0xFFFFE6A8),
        topLeft = Offset(size.width * 0.22f, size.height * 0.25f),
        size = Size(size.width * 0.56f, size.height * 0.36f),
        cornerRadius = CornerRadius(9f, 9f)
    )
    drawRoundRect(
        color = Color(0xFF98643A),
        topLeft = Offset(size.width * 0.47f, size.height * 0.58f),
        size = Size(size.width * 0.06f, size.height * 0.24f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawCircle(Color(0xFFE9573F), size.minDimension * 0.07f, Offset(size.width * 0.46f, size.height * 0.43f))
    drawOval(
        color = Color(0xFF4F9A59),
        topLeft = Offset(size.width * 0.52f, size.height * 0.32f),
        size = Size(size.width * 0.12f, size.height * 0.08f)
    )
}

@Composable
private fun FarmToolPicker(
    activeBatches: List<PlantingBatch>,
    selectedTool: String?,
    onSelectedToolChange: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedTool == null,
                    onClick = { onSelectedToolChange(null) },
                    label = { Text("查看") }
                )
            }
            item {
                val key = FarmTool.Move.toToolKey()
                FilterChip(
                    selected = selectedTool == key,
                    onClick = { onSelectedToolChange(key) },
                    label = { Text("移动") }
                )
            }
            item {
                FilterChip(
                    selected = selectedTool == FarmTool.Clear.toToolKey(),
                    onClick = { onSelectedToolChange(FarmTool.Clear.toToolKey()) },
                    label = { Text("清空") }
                )
            }
            items(activeBatches, key = { it.id }) { batch ->
                val crop = CropLibrary.byId(batch.cropId)
                FilterChip(
                    selected = selectedTool == FarmTool.Plant(batch.id).toToolKey(),
                    onClick = { onSelectedToolChange(FarmTool.Plant(batch.id).toToolKey()) },
                    label = {
                        Text(
                            "${crop.name}${batch.variety.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val structureTools = listOf(
                FarmTileType.SIGN
            )
            items(structureTools, key = { it.name }) { type ->
                val key = FarmTool.Structure(type).toToolKey()
                FilterChip(
                    selected = selectedTool == key,
                    onClick = { onSelectedToolChange(key) },
                    label = { Text(type.label) }
                )
            }
        }
    }
}

@Composable
private fun FarmSelectionPanel(
    tile: FarmTile?,
    insight: PlantingInsight?,
    onRotateLeft: (() -> Unit)?,
    onRotateRight: (() -> Unit)?,
    onResetRotation: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Color(0xFFFFF8E7)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (insight != null) {
                        Text(
                            "${insight.crop.name}${insight.batch.variety.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${insight.plot?.name ?: "未分配地块"} · 第 ${insight.rawDay} 天 · ${insight.stage.name} · ${insight.progressPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            tile?.type?.label ?: "草地",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "拖拽可移动，使用下方按钮调整角度",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (tile != null) {
                    AssistChip(onClick = {}, label = { Text(tile.type.label) })
                }
            }
            if (tile != null && onRotateLeft != null && onRotateRight != null && onResetRotation != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onRotateLeft, modifier = Modifier.weight(1f)) {
                        Text("左转")
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("${tile.rotationDegrees.roundToInt()}°") }
                    )
                    OutlinedButton(onClick = onRotateRight, modifier = Modifier.weight(1f)) {
                        Text("右转")
                    }
                    OutlinedButton(onClick = onResetRotation) {
                        Text("归零")
                    }
                }
            }
        }
    }
}

private fun FarmTool.toToolKey(): String = when (this) {
    FarmTool.Move -> "move"
    FarmTool.Clear -> "clear"
    is FarmTool.Plant -> "plant:$batchId"
    is FarmTool.Structure -> "structure:${type.name}"
}

private fun String.toFarmTool(): FarmTool? {
    return when {
        this == "move" -> FarmTool.Move
        this == "clear" -> FarmTool.Clear
        startsWith("plant:") -> FarmTool.Plant(removePrefix("plant:"))
        startsWith("structure:") -> {
            val type = runCatching {
                enumValueOf<FarmTileType>(removePrefix("structure:"))
            }.getOrNull() ?: return null
            FarmTool.Structure(type)
        }

        else -> null
    }
}

private fun cropColor(category: String, cropName: String): Color {
    return when {
        category.contains("叶菜") || cropName == "卷心菜" -> Color(0xFF62A84D)
        category.contains("瓜") || category.contains("豆") -> Color(0xFF2D8A68)
        category.contains("茄果") || cropName == "番茄" || cropName == "辣椒" || cropName == "茄子" -> Color(0xFF3F8A45)
        category.contains("根菜") -> Color(0xFF5B9B42)
        category.contains("葱") || category.contains("香辛") -> Color(0xFF76A646)
        else -> Color(0xFF4F9A59)
    }
}

private fun cropLightColor(category: String, cropName: String): Color {
    return when {
        category.contains("叶菜") || cropName == "卷心菜" -> Color(0xFFCBEA79)
        category.contains("茄果") || cropName == "番茄" || cropName == "辣椒" || cropName == "茄子" -> Color(0xFFBDE66B)
        category.contains("根菜") -> Color(0xFFA7D66F)
        else -> Color(0xFFC8E98E)
    }
}

private fun fruitColor(cropName: String): Color {
    return when (cropName) {
        "茄子" -> Color(0xFF68439A)
        "辣椒" -> Color(0xFFD9362F)
        "南瓜", "胡萝卜" -> Color(0xFFE99532)
        "草莓" -> Color(0xFFD8424B)
        else -> Color(0xFFE9573F)
    }
}
