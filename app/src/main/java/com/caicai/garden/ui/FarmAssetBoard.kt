package com.caicai.garden.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.caicai.garden.data.FarmTile
import com.caicai.garden.data.FarmTileType
import com.caicai.garden.domain.PlantingInsight
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val FARM_ASSET_MIN_SCALE = 1f
private const val FARM_ASSET_MAX_SCALE = 2.4f
private const val FARM_WIND_CYCLE_MILLIS = 9_600
private const val FARM_SOIL_TEXTURE_TILE_CELLS = 3.0f
private const val FARM_SOIL_CELL_HALF_SCALE = 0.43f
private const val FARM_TARGET_FRAME_ASSET_SCALE = 1.72f
private const val FARM_FAR_DEPTH_SCALE = 0.88f
private const val FARM_NEAR_DEPTH_SCALE = 1.08f

private enum class FarmAssetGestureMode {
    TileDrag,
    ViewportPan,
    Ignore
}

private val bitmapContentBoundsCache = mutableMapOf<Int, Rect>()
private val bitmapPrimaryContentBoundsCache = mutableMapOf<Int, Rect>()

@Composable
fun FarmAssetBoard(
    rows: Int,
    columns: Int,
    tilesByCell: Map<Pair<Int, Int>, FarmTile>,
    insightsByBatch: Map<String, PlantingInsight>,
    interactionKey: String?,
    boardHeight: Dp,
    onCellClick: (Int, Int) -> Unit,
    onTileMove: (Int, Int, Int, Int) -> Unit,
    onTileSelect: (Int, Int) -> Unit
) {
    var draggingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTargetCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var viewportScale by rememberSaveable { mutableStateOf(FARM_ASSET_MIN_SCALE) }
    var viewportOffsetX by rememberSaveable { mutableStateOf(0f) }
    var viewportOffsetY by rememberSaveable { mutableStateOf(0f) }
    val currentInteractionKey by rememberUpdatedState(interactionKey)
    val currentTilesByCell by rememberUpdatedState(tilesByCell)
    val currentOnCellClick by rememberUpdatedState(onCellClick)
    val currentOnTileMove by rememberUpdatedState(onTileMove)
    val currentOnTileSelect by rememberUpdatedState(onTileSelect)

    val assetPaths = remember(rows, columns, tilesByCell, insightsByBatch) {
        buildFarmAssetPaths(tilesByCell, insightsByBatch)
    }
    val bitmaps = rememberFarmBitmaps(assetPaths)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(boardHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEAF8C9), Color(0xFF9BDA63), Color(0xFF62B941))
                )
            )
            .pointerInput(rows, columns, interactionKey, tilesByCell.values.map { it.row to it.column }) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var activePointerId = down.id
                    var gestureMode: FarmAssetGestureMode? = null
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

                    val metrics = farmAssetMetrics(boardWidthPx, boardHeightPx, rows, columns)
                    val startScenePosition = boardToFarmAssetSceneOffset(
                        position = start,
                        viewportOffset = Offset(viewportOffsetX, viewportOffsetY),
                        viewportScale = viewportScale
                    )
                    val startCell = metrics.cellAt(startScenePosition, rows, columns)
                    val startTile = startCell?.let(currentTilesByCell::get)
                    val dragEnabled = startTile != null && (
                        currentInteractionKey == null || currentInteractionKey == "move"
                    )

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }
                        if (pressedChanges.isEmpty()) break

                        if (pressedChanges.size > 1) {
                            gestureMode = FarmAssetGestureMode.ViewportPan
                            draggingCell = null
                            dragOffset = Offset.Zero
                            dragTargetCell = null
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
                                    .coerceIn(FARM_ASSET_MIN_SCALE, FARM_ASSET_MAX_SCALE)
                                val sceneFocus = boardToFarmAssetSceneOffset(
                                    position = previousCentroid,
                                    viewportOffset = Offset(viewportOffsetX, viewportOffsetY),
                                    viewportScale = oldScale
                                )
                                val nextOffset = clampFarmAssetViewportOffset(
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
                            if (gestureMode == FarmAssetGestureMode.TileDrag) break
                            activePointerId = pressedChanges.first().id
                            continue
                        }
                        val delta = activeChange.positionChange()
                        if (delta == Offset.Zero) continue

                        when (gestureMode) {
                            null -> {
                                accumulatedDrag += delta
                                if (accumulatedDrag.getDistance() > touchSlop) {
                                    gestureMode = when {
                                        dragEnabled -> FarmAssetGestureMode.TileDrag
                                        viewportScale > FARM_ASSET_MIN_SCALE + 0.01f -> FarmAssetGestureMode.ViewportPan
                                        else -> FarmAssetGestureMode.Ignore
                                    }
                                    when (gestureMode) {
                                        FarmAssetGestureMode.TileDrag -> {
                                            activeChange.consume()
                                            draggingCell = startCell
                                            dragOffset += accumulatedDrag / viewportScale
                                            dragTargetCell = startCell?.let {
                                                metrics.cellAt(metrics.cellCenter(it.first, it.second) + dragOffset, rows, columns)
                                            }
                                        }

                                        FarmAssetGestureMode.ViewportPan -> {
                                            activeChange.consume()
                                            val nextOffset = clampFarmAssetViewportOffset(
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

                                        FarmAssetGestureMode.Ignore, null -> Unit
                                    }
                                }
                            }

                            FarmAssetGestureMode.TileDrag -> {
                                activeChange.consume()
                                dragOffset += delta / viewportScale
                                dragTargetCell = startCell?.let {
                                    metrics.cellAt(metrics.cellCenter(it.first, it.second) + dragOffset, rows, columns)
                                }
                            }

                            FarmAssetGestureMode.ViewportPan -> {
                                activeChange.consume()
                                val nextOffset = clampFarmAssetViewportOffset(
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

                            FarmAssetGestureMode.Ignore -> Unit
                        }
                    }

                    if (gestureMode == null) {
                        if (startCell != null) {
                            val (row, column) = startCell
                            if (currentInteractionKey == null && currentTilesByCell[row to column] != null) {
                                currentOnTileSelect(row, column)
                            } else {
                                currentOnCellClick(row, column)
                            }
                        }
                    } else if (gestureMode == FarmAssetGestureMode.TileDrag && startCell != null) {
                        val target = dragTargetCell ?: startCell
                        currentOnTileMove(startCell.first, startCell.second, target.first, target.second)
                        currentOnTileSelect(target.first, target.second)
                    }

                    draggingCell = null
                    dragOffset = Offset.Zero
                    dragTargetCell = null
                }
            }
    ) {
        val density = LocalDensity.current
        val boardWidthPx = with(density) { maxWidth.toPx() }
        val boardHeightPx = with(density) { maxHeight.toPx() }
        fun zoomViewport(targetScale: Float) {
            val oldScale = viewportScale
            val newScale = targetScale.coerceIn(FARM_ASSET_MIN_SCALE, FARM_ASSET_MAX_SCALE)
            val center = Offset(boardWidthPx / 2f, boardHeightPx / 2f)
            val sceneFocus = boardToFarmAssetSceneOffset(
                position = center,
                viewportOffset = Offset(viewportOffsetX, viewportOffsetY),
                viewportScale = oldScale
            )
            val nextOffset = clampFarmAssetViewportOffset(
                offset = Offset(
                    x = center.x - sceneFocus.x * newScale,
                    y = center.y - sceneFocus.y * newScale
                ),
                scale = newScale,
                boardWidthPx = boardWidthPx,
                boardHeightPx = boardHeightPx
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val metrics = farmAssetMetrics(
                    width = size.width,
                    height = size.height,
                    rows = rows,
                    columns = columns
                )
                drawFarmAssetBackground(bitmaps)
                drawFarmEnvironment(bitmaps, metrics)
            }
            FarmTerrainLayer(
                rows = rows,
                columns = columns,
                bitmaps = bitmaps,
                showGrid = interactionKey != null || draggingCell != null
            )
            FarmAnimatedTileLayer(
                rows = rows,
                columns = columns,
                bitmaps = bitmaps,
                tilesByCell = tilesByCell,
                insightsByBatch = insightsByBatch,
                draggingCell = draggingCell,
                dragOffset = dragOffset,
                dragTargetCell = dragTargetCell
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .zIndex(3f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FarmAssetZoomButton(label = "+", onClick = { zoomViewport(viewportScale * 1.25f) })
            FarmAssetZoomButton(label = "-", onClick = { zoomViewport(viewportScale / 1.25f) })
        }
    }
}

@Composable
private fun FarmTerrainLayer(
    rows: Int,
    columns: Int,
    bitmaps: Map<String, Bitmap>,
    showGrid: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val metrics = farmAssetMetrics(
            width = size.width,
            height = size.height,
            rows = rows,
            columns = columns
        )
        drawFarmTerrain(
            bitmaps = bitmaps,
            metrics = metrics,
            rows = rows,
            columns = columns,
            showGrid = showGrid
        )
    }
}

@Composable
private fun FarmAnimatedTileLayer(
    rows: Int,
    columns: Int,
    bitmaps: Map<String, Bitmap>,
    tilesByCell: Map<Pair<Int, Int>, FarmTile>,
    insightsByBatch: Map<String, PlantingInsight>,
    draggingCell: Pair<Int, Int>?,
    dragOffset: Offset,
    dragTargetCell: Pair<Int, Int>?
) {
    val windTransition = rememberInfiniteTransition(label = "farm-wind")
    val windCycle by windTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = FARM_WIND_CYCLE_MILLIS,
                easing = LinearEasing
            )
        ),
        label = "farm-wind-cycle"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val metrics = farmAssetMetrics(
            width = size.width,
            height = size.height,
            rows = rows,
            columns = columns
        )
        drawFarmTiles(
            bitmaps = bitmaps,
            metrics = metrics,
            rows = rows,
            columns = columns,
            tilesByCell = tilesByCell,
            insightsByBatch = insightsByBatch,
            draggingCell = draggingCell,
            dragOffset = dragOffset,
            windCycle = windCycle
        )
        dragTargetCell?.let { cell ->
            drawTargetCell(bitmaps, metrics, cell.first, cell.second)
        }
    }
}

@Composable
private fun FarmAssetZoomButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFFFF8E7).copy(alpha = 0.92f),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color(0xFF2F6F4E), fontSize = 24.sp)
        }
    }
}

private fun boardToFarmAssetSceneOffset(
    position: Offset,
    viewportOffset: Offset,
    viewportScale: Float
): Offset {
    return Offset(
        x = (position.x - viewportOffset.x) / viewportScale,
        y = (position.y - viewportOffset.y) / viewportScale
    )
}

private fun clampFarmAssetViewportOffset(
    offset: Offset,
    scale: Float,
    boardWidthPx: Float,
    boardHeightPx: Float
): Offset {
    if (scale <= FARM_ASSET_MIN_SCALE || boardWidthPx <= 0f || boardHeightPx <= 0f) {
        return Offset.Zero
    }
    val minX = boardWidthPx * (1f - scale)
    val minY = boardHeightPx * (1f - scale)
    return Offset(
        x = offset.x.coerceIn(minX, 0f),
        y = offset.y.coerceIn(minY, 0f)
    )
}

private fun pointerCentroid(changes: List<PointerInputChange>, usePrevious: Boolean): Offset {
    if (changes.isEmpty()) return Offset.Zero
    val sum = changes.fold(Offset.Zero) { total, change ->
        total + if (usePrevious) change.previousPosition else change.position
    }
    return sum / changes.size.toFloat()
}

private fun pointerAverageDistance(
    changes: List<PointerInputChange>,
    centroid: Offset,
    usePrevious: Boolean
): Float {
    if (changes.isEmpty()) return 0f
    return changes.sumOf { change ->
        val position = if (usePrevious) change.previousPosition else change.position
        (position - centroid).getDistance().toDouble()
    }.toFloat() / changes.size.toFloat()
}

@Composable
private fun rememberFarmBitmaps(assetPaths: Set<String>): Map<String, Bitmap> {
    val context = LocalContext.current
    val signature = remember(assetPaths) { assetPaths.sorted().joinToString("|") }
    return remember(context, signature) {
        buildMap {
            assetPaths.forEach { path ->
                val bitmap = runCatching {
                    context.assets.open("farm_v2/$path").use(BitmapFactory::decodeStream)
                }.getOrNull()
                if (bitmap != null) put(path, bitmap)
            }
        }
    }
}

private fun buildFarmAssetPaths(
    tilesByCell: Map<Pair<Int, Int>, FarmTile>,
    insightsByBatch: Map<String, PlantingInsight>
): Set<String> {
    return buildSet {
        add("backgrounds/garden-empty-background.png")
        add("sprites/terrain/continuous_soil_texture.png")
        add("sprites/terrain/target_cell_frame.png")
        add("sprites/terrain/stone_path_tile.png")
        add("sprites/terrain/watered_soil_tile.png")
        add("sprites/structures/greenhouse.png")
        add("sprites/structures/tool_shed.png")
        add("sprites/structures/blank_label_stake.png")
        add("sprites/structures/irrigation_sprinkler.png")
        add("sprites/environment/pink_flower_bush.png")
        add("sprites/environment/yellow_flower_bush.png")
        add("sprites/environment/leafy_shrub.png")
        add("sprites/environment/rock_cluster.png")
        add("sprites/environment/wheelbarrow.png")
        add("sprites/effects/growth_sparkles.png")
        add("sprites/effects/soft_shadow_blob.png")

        tilesByCell.values.forEach { tile ->
            when (tile.type) {
                FarmTileType.RAISED_BED -> {
                    val insight = tile.batchId?.let(insightsByBatch::get)
                    if (insight != null) {
                        val visual = insight.visual
                        add(
                            "sprites/crops_no_soil/${visual.assetCropName}/" +
                                "${visual.spriteStage.assetName}.png"
                        )
                    }
                }

                FarmTileType.GREENHOUSE -> add("sprites/structures/greenhouse.png")
                FarmTileType.TOOL_SHED -> add("sprites/structures/tool_shed.png")
                FarmTileType.SIGN -> add("sprites/structures/blank_label_stake.png")
                FarmTileType.IRRIGATION -> add("sprites/structures/irrigation_sprinkler.png")
                FarmTileType.PATH -> add("sprites/terrain/stone_path_tile.png")
                FarmTileType.FENCE -> add("sprites/structures/wood_fence_straight.png")
                FarmTileType.GRASS -> Unit
            }
        }
    }
}

private data class FarmCellProjection(
    val center: Offset,
    val scale: Float
)

private data class FarmAssetMetrics(
    val width: Float,
    val height: Float,
    val centerX: Float,
    val topY: Float,
    val tileWidth: Float,
    val tileHeight: Float,
    val maxDepth: Float
) {
    fun cellProjection(row: Int, column: Int): FarmCellProjection {
        val depth = (row + column).toFloat()
        val normalizedDepth = (depth / maxDepth).coerceIn(0f, 1f)
        val scale = FARM_FAR_DEPTH_SCALE +
            (FARM_NEAR_DEPTH_SCALE - FARM_FAR_DEPTH_SCALE) * normalizedDepth
        val depthOffset = tileHeight * 0.5f * (
            FARM_FAR_DEPTH_SCALE * depth +
                (FARM_NEAR_DEPTH_SCALE - FARM_FAR_DEPTH_SCALE) * depth * depth /
                (2f * maxDepth)
            )
        return FarmCellProjection(
            center = Offset(
                x = centerX + (column - row) * tileWidth * 0.5f * scale,
                y = topY + depthOffset
            ),
            scale = scale
        )
    }

    fun cellCenter(row: Int, column: Int): Offset = cellProjection(row, column).center

    fun cellAt(offset: Offset, rows: Int, columns: Int): Pair<Int, Int>? {
        var bestCell: Pair<Int, Int>? = null
        var bestScore = Float.POSITIVE_INFINITY
        var bestDepth = -1
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val projection = cellProjection(row, column)
                val halfWidth = tileWidth * 0.5f * projection.scale
                val halfHeight = tileHeight * 0.5f * projection.scale
                val score = abs(offset.x - projection.center.x) / halfWidth +
                    abs(offset.y - projection.center.y) / halfHeight
                val depth = row + column
                val isBetter = score < bestScore - 0.001f ||
                    (abs(score - bestScore) <= 0.001f && depth > bestDepth)
                if (score <= 1.12f && isBetter) {
                    bestCell = row to column
                    bestScore = score
                    bestDepth = depth
                }
            }
        }
        return bestCell
    }
}

private fun farmAssetMetrics(width: Float, height: Float, rows: Int, columns: Int): FarmAssetMetrics {
    val gridSpan = max(rows, columns).coerceAtLeast(1).toFloat()
    val tileWidth = min(width * 1.00f / gridSpan, height * 0.68f / gridSpan / 0.56f)
    val tileHeight = tileWidth * 0.56f
    return FarmAssetMetrics(
        width = width,
        height = height,
        centerX = width * 0.50f,
        topY = height * 0.31f,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        maxDepth = (rows + columns - 2).coerceAtLeast(1).toFloat()
    )
}

private fun DrawScope.drawFarmAssetBackground(bitmaps: Map<String, Bitmap>) {
    val bitmap = bitmaps["backgrounds/garden-empty-background.png"] ?: return
    drawCroppedBitmap(bitmap, RectF(0f, 0f, size.width, size.height))
}

private fun DrawScope.drawFarmEnvironment(bitmaps: Map<String, Bitmap>, metrics: FarmAssetMetrics) {
    val items = listOf(
        DecorationAsset("sprites/environment/pink_flower_bush.png", 0.15f, 0.22f, 0.22f),
        DecorationAsset("sprites/environment/yellow_flower_bush.png", 0.86f, 0.24f, 0.20f),
        DecorationAsset("sprites/environment/leafy_shrub.png", 0.13f, 0.76f, 0.24f),
        DecorationAsset("sprites/environment/rock_cluster.png", 0.86f, 0.76f, 0.18f),
        DecorationAsset("sprites/environment/wheelbarrow.png", 0.22f, 0.86f, 0.20f),
        DecorationAsset("sprites/structures/greenhouse.png", 0.80f, 0.24f, 0.18f),
    )
    items.forEach { item ->
        drawBitmapCentered(
            bitmap = bitmaps[item.path],
            center = Offset(metrics.width * item.x, metrics.height * item.y),
            width = metrics.width * item.size,
            height = metrics.width * item.size,
            alpha = 0.94f
        )
    }
}

private data class DecorationAsset(val path: String, val x: Float, val y: Float, val size: Float)

private fun DrawScope.drawFarmTerrain(
    bitmaps: Map<String, Bitmap>,
    metrics: FarmAssetMetrics,
    rows: Int,
    columns: Int,
    showGrid: Boolean
) {
    drawFarmBoundary(bitmaps, metrics, rows, columns)
    for (diagonal in 0 until rows + columns - 1) {
        for (row in 0 until rows) {
            val column = diagonal - row
            if (column !in 0 until columns) continue
            val projection = metrics.cellProjection(row, column)
            drawFarmSoilCell(
                metrics = metrics,
                projection = projection,
                emphasized = showGrid
            )
        }
    }
}

private fun DrawScope.drawFarmBoundary(
    bitmaps: Map<String, Bitmap>,
    metrics: FarmAssetMetrics,
    rows: Int,
    columns: Int
) {
    if (rows <= 0 || columns <= 0) return

    fun rightPoint(row: Int, column: Int): Offset {
        val projection = metrics.cellProjection(row, column)
        return projection.center.copy(
            x = projection.center.x + metrics.tileWidth * 0.5f * projection.scale
        )
    }

    fun bottomPoint(row: Int, column: Int): Offset {
        val projection = metrics.cellProjection(row, column)
        return projection.center.copy(
            y = projection.center.y + metrics.tileHeight * 0.5f * projection.scale
        )
    }

    fun leftPoint(row: Int, column: Int): Offset {
        val projection = metrics.cellProjection(row, column)
        return projection.center.copy(
            x = projection.center.x - metrics.tileWidth * 0.5f * projection.scale
        )
    }

    fun topPoint(row: Int, column: Int): Offset {
        val projection = metrics.cellProjection(row, column)
        return projection.center.copy(
            y = projection.center.y - metrics.tileHeight * 0.5f * projection.scale
        )
    }

    val start = topPoint(0, 0)
    val right = rightPoint(0, columns - 1)
    val bottom = bottomPoint(rows - 1, columns - 1)
    val left = leftPoint(rows - 1, 0)
    val boundary = Path().apply {
        moveTo(start.x, start.y)
        for (column in 0 until columns) {
            val point = rightPoint(0, column)
            lineTo(point.x, point.y)
        }
        for (row in 1 until rows) {
            val point = rightPoint(row, columns - 1)
            lineTo(point.x, point.y)
        }
        bottomPoint(rows - 1, columns - 1).let { lineTo(it.x, it.y) }
        for (column in columns - 1 downTo 0) {
            val point = leftPoint(rows - 1, column)
            lineTo(point.x, point.y)
        }
        for (row in rows - 1 downTo 0) {
            val point = topPoint(row, 0)
            lineTo(point.x, point.y)
        }
        close()
    }

    clipPath(boundary) {
        bitmaps["sprites/terrain/continuous_soil_texture.png"]?.let { texture ->
            drawRepeatedSoilTexture(
                bitmap = texture,
                destination = RectF(left.x, start.y, right.x, bottom.y),
                tileSize = metrics.tileWidth * FARM_SOIL_TEXTURE_TILE_CELLS
            )
        }
        drawPath(
            path = boundary,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFD89A).copy(alpha = 0.035f),
                    Color(0xFF3F2818).copy(alpha = 0.075f)
                ),
                startY = start.y,
                endY = bottom.y
            )
        )
        drawPath(
            path = boundary,
            color = Color(0xFF2D1B11).copy(alpha = 0.13f)
        )
    }
    drawPath(
        path = boundary,
        color = Color(0xFF4F351F).copy(alpha = 0.34f),
        style = Stroke(
            width = (metrics.tileWidth * 0.065f).coerceAtLeast(2f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    drawPath(
        path = boundary,
        color = Color(0xFFD7A26B).copy(alpha = 0.42f),
        style = Stroke(
            width = (metrics.tileWidth * 0.016f).coerceAtLeast(1f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawRepeatedSoilTexture(
    bitmap: Bitmap,
    destination: RectF,
    tileSize: Float
) {
    val scale = tileSize / bitmap.width.coerceAtLeast(1).toFloat()
    val localMatrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate(destination.left, destination.top)
    }
    val bitmapShader = BitmapShader(
        bitmap,
        Shader.TileMode.REPEAT,
        Shader.TileMode.REPEAT
    ).apply {
        setLocalMatrix(localMatrix)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
        shader = bitmapShader
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(destination, paint)
    }
}

private fun DrawScope.drawFarmSoilCell(
    metrics: FarmAssetMetrics,
    projection: FarmCellProjection,
    emphasized: Boolean
) {
    val halfWidth = metrics.tileWidth * FARM_SOIL_CELL_HALF_SCALE * projection.scale
    val halfHeight = metrics.tileHeight * FARM_SOIL_CELL_HALF_SCALE * projection.scale
    val center = projection.center
    val cell = Path().apply {
        moveTo(center.x, center.y - halfHeight)
        lineTo(center.x + halfWidth, center.y)
        lineTo(center.x, center.y + halfHeight)
        lineTo(center.x - halfWidth, center.y)
        close()
    }
    drawPath(
        path = cell,
        color = Color(0xFFFFB968).copy(alpha = if (emphasized) 0.14f else 0.10f)
    )
    drawPath(
        path = cell,
        color = Color(0xFF2B190F).copy(alpha = if (emphasized) 0.42f else 0.30f),
        style = Stroke(
            width = (metrics.tileWidth * 0.014f * projection.scale).coerceAtLeast(1.5f)
        )
    )
    drawPath(
        path = cell,
        color = Color(0xFFFFD6A0).copy(alpha = if (emphasized) 0.25f else 0.13f),
        style = Stroke(
            width = (metrics.tileWidth * 0.006f * projection.scale).coerceAtLeast(1f)
        )
    )
}

private fun DrawScope.drawFarmTiles(
    bitmaps: Map<String, Bitmap>,
    metrics: FarmAssetMetrics,
    rows: Int,
    columns: Int,
    tilesByCell: Map<Pair<Int, Int>, FarmTile>,
    insightsByBatch: Map<String, PlantingInsight>,
    draggingCell: Pair<Int, Int>?,
    dragOffset: Offset,
    windCycle: Float
) {
    var draggingTile: FarmTile? = null
    for (diagonal in 0 until rows + columns - 1) {
        val rowRange = 0 until rows
        for (row in rowRange) {
            val column = diagonal - row
            if (column !in 0 until columns) continue
            val tile = tilesByCell[row to column] ?: continue
            if (draggingCell == row to column) {
                draggingTile = tile
                continue
            }
            val projection = metrics.cellProjection(row, column)
            drawFarmTileContent(
                bitmaps = bitmaps,
                metrics = metrics,
                center = projection.center,
                cellScale = projection.scale,
                tile = tile,
                insightsByBatch = insightsByBatch,
                windCycle = windCycle
            )
        }
    }
    draggingTile?.let { tile ->
        val projection = metrics.cellProjection(tile.row, tile.column)
        val center = projection.center + dragOffset
        drawBitmapCentered(
            bitmap = bitmaps["sprites/effects/soft_shadow_blob.png"],
            center = center.copy(y = center.y + metrics.tileHeight * 0.24f * projection.scale),
            width = metrics.tileWidth * 1.36f * projection.scale,
            height = metrics.tileHeight * 1.12f * projection.scale,
            alpha = 0.40f
        )
        drawFarmTileContent(
            bitmaps = bitmaps,
            metrics = metrics,
            center = center,
            cellScale = projection.scale,
            tile = tile,
            insightsByBatch = insightsByBatch,
            windCycle = windCycle
        )
    }
}

private fun DrawScope.drawFarmTileContent(
    bitmaps: Map<String, Bitmap>,
    metrics: FarmAssetMetrics,
    center: Offset,
    cellScale: Float,
    tile: FarmTile,
    insightsByBatch: Map<String, PlantingInsight>,
    windCycle: Float
) {
    when (tile.type) {
        FarmTileType.RAISED_BED -> drawRaisedBedCrop(
            bitmaps,
            metrics,
            center,
            cellScale,
            tile,
            insightsByBatch,
            windCycle
        )
        FarmTileType.GREENHOUSE -> Unit
        FarmTileType.TOOL_SHED -> Unit
        FarmTileType.SIGN -> drawStructure(bitmaps, metrics, center, cellScale, "sprites/structures/blank_label_stake.png", tile.rotationDegrees, 1.18f)
        FarmTileType.IRRIGATION -> drawStructure(bitmaps, metrics, center, cellScale, "sprites/structures/irrigation_sprinkler.png", tile.rotationDegrees, 1.08f)
        FarmTileType.PATH -> drawStructure(bitmaps, metrics, center, cellScale, "sprites/terrain/stone_path_tile.png", tile.rotationDegrees, 1.32f)
        FarmTileType.FENCE -> drawStructure(bitmaps, metrics, center, cellScale, "sprites/structures/wood_fence_straight.png", tile.rotationDegrees, 1.30f)
        FarmTileType.GRASS -> Unit
    }
}

private fun DrawScope.drawRaisedBedCrop(
    bitmaps: Map<String, Bitmap>,
    metrics: FarmAssetMetrics,
    center: Offset,
    cellScale: Float,
    tile: FarmTile,
    insightsByBatch: Map<String, PlantingInsight>,
    windCycle: Float
) {
    val insight = tile.batchId?.let(insightsByBatch::get) ?: return
    val visual = insight.visual
    val stage = visual.spriteStage.assetName
    val scaledTileWidth = metrics.tileWidth * cellScale
    val scaledTileHeight = metrics.tileHeight * cellScale
    val soilContactY = center.y + scaledTileHeight * 0.03f
    val cropWidth = scaledTileWidth * visual.canopyWidthTiles
    val windRotation = cropWindRotationDegrees(
        cycle = windCycle,
        row = tile.row,
        column = tile.column,
        stage = stage,
        windFlex = visual.windFlex
    )
    drawBitmapCentered(
        bitmap = bitmaps["sprites/effects/soft_shadow_blob.png"],
        center = center.copy(y = soilContactY + scaledTileHeight * 0.04f),
        width = cropWidth * visual.shadowWidthFactor,
        height = scaledTileHeight * 0.18f,
        alpha = 0.18f
    )
    drawBitmapBottomAligned(
        bitmap = bitmaps["sprites/crops_no_soil/${visual.assetCropName}/$stage.png"],
        centerX = center.x,
        bottomY = soilContactY,
        contentWidth = cropWidth,
        heightScale = visual.heightScale,
        rotationDegrees = tile.rotationDegrees + windRotation,
        primaryContentOnly = true
    )
}

private fun cropWindRotationDegrees(
    cycle: Float,
    row: Int,
    column: Int,
    stage: String,
    windFlex: Float
): Float {
    val waveDelay = (row + column) * 0.014f + ((row * 13 + column * 7) % 5) * 0.006f
    val localCycle = (cycle - waveDelay + 1f) % 1f
    val gust = when {
        localCycle < 0.16f -> 0f
        localCycle < 0.30f -> smoothStep((localCycle - 0.16f) / 0.14f)
        localCycle < 0.56f -> 1f
        localCycle < 0.72f -> 1f - smoothStep((localCycle - 0.56f) / 0.16f)
        else -> 0f
    }
    val rebound = when {
        localCycle < 0.68f || localCycle > 0.86f -> 0f
        localCycle < 0.77f -> smoothStep((localCycle - 0.68f) / 0.09f)
        else -> 1f - smoothStep((localCycle - 0.77f) / 0.09f)
    }
    val flutterPhase = localCycle * 7f * (2f * PI.toFloat()) + (row * 0.41f + column * 0.67f)
    val flutter = sin(flutterPhase) * 0.22f
    val normalizedSway = gust * (0.78f + flutter) - rebound * 0.16f

    val stageAmplitude = when (stage) {
        "seedling" -> 1.4f
        "young" -> 2.1f
        "mature" -> 2.8f
        else -> 3.2f
    }
    val plantVariation = 0.92f + ((row * 11 + column * 17) % 7) * 0.025f
    return normalizedSway * stageAmplitude * windFlex * plantVariation
}

private fun smoothStep(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

private fun DrawScope.drawStructure(
    bitmaps: Map<String, Bitmap>,
    metrics: FarmAssetMetrics,
    center: Offset,
    cellScale: Float,
    path: String,
    rotationDegrees: Float,
    scale: Float
) {
    drawBitmapCentered(
        bitmap = bitmaps[path],
        center = center.copy(y = center.y - metrics.tileHeight * 0.32f * cellScale),
        width = metrics.tileWidth * scale * cellScale,
        height = metrics.tileWidth * scale * cellScale,
        rotationDegrees = rotationDegrees
    )
}

private fun DrawScope.drawTargetCell(
    bitmaps: Map<String, Bitmap>,
    metrics: FarmAssetMetrics,
    row: Int,
    column: Int
) {
    val projection = metrics.cellProjection(row, column)
    drawBitmapCentered(
        bitmap = bitmaps["sprites/terrain/target_cell_frame.png"],
        center = projection.center,
        width = metrics.tileWidth * FARM_TARGET_FRAME_ASSET_SCALE * projection.scale,
        height = metrics.tileWidth * FARM_TARGET_FRAME_ASSET_SCALE * projection.scale,
        alpha = 0.88f
    )
}

private fun DrawScope.drawBitmapCentered(
    bitmap: Bitmap?,
    center: Offset,
    width: Float,
    height: Float,
    rotationDegrees: Float = 0f,
    alpha: Float = 1f
) {
    if (bitmap == null) return
    val destination = RectF(
        center.x - width * 0.5f,
        center.y - height * 0.5f,
        center.x + width * 0.5f,
        center.y + height * 0.5f
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
        this.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    }
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        nativeCanvas.save()
        if (rotationDegrees != 0f) {
            nativeCanvas.rotate(rotationDegrees, center.x, center.y)
        }
        nativeCanvas.drawBitmap(bitmap, null, destination, paint)
        nativeCanvas.restore()
    }
}

private fun DrawScope.drawBitmapBottomAligned(
    bitmap: Bitmap?,
    centerX: Float,
    bottomY: Float,
    contentWidth: Float,
    heightScale: Float = 1f,
    rotationDegrees: Float = 0f,
    alpha: Float = 1f,
    primaryContentOnly: Boolean = false
) {
    if (bitmap == null) return
    val source = if (primaryContentOnly) bitmap.alphaPrimaryContentBounds() else bitmap.alphaContentBounds()
    val contentHeight = contentWidth * source.height().toFloat() /
        source.width().coerceAtLeast(1) * heightScale
    val destination = RectF(
        centerX - contentWidth * 0.5f,
        bottomY - contentHeight,
        centerX + contentWidth * 0.5f,
        bottomY
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
        this.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    }
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        nativeCanvas.save()
        if (rotationDegrees != 0f) {
            nativeCanvas.rotate(rotationDegrees, centerX, bottomY)
        }
        nativeCanvas.drawBitmap(bitmap, source, destination, paint)
        nativeCanvas.restore()
    }
}

private fun Bitmap.alphaContentBounds(): Rect {
    val key = System.identityHashCode(this)
    bitmapContentBoundsCache[key]?.let { return Rect(it) }
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    val row = IntArray(width)
    for (y in 0 until height) {
        getPixels(row, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            if ((row[x] ushr 24) > 12) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    val rect = if (right >= left && bottom >= top) {
        Rect(left, top, right + 1, bottom + 1)
    } else {
        Rect(0, 0, width, height)
    }
    bitmapContentBoundsCache[key] = Rect(rect)
    return rect
}

private fun Bitmap.alphaPrimaryContentBounds(): Rect {
    val key = System.identityHashCode(this)
    bitmapPrimaryContentBoundsCache[key]?.let { return Rect(it) }
    val fullBounds = alphaContentBounds()
    val boundsWidth = fullBounds.width()
    val boundsHeight = fullBounds.height()
    if (boundsWidth <= 0 || boundsHeight <= 0) return fullBounds

    val solid = BooleanArray(boundsWidth * boundsHeight)
    val visited = BooleanArray(boundsWidth * boundsHeight)
    val row = IntArray(boundsWidth)
    for (y in 0 until boundsHeight) {
        getPixels(row, 0, boundsWidth, fullBounds.left, fullBounds.top + y, boundsWidth, 1)
        for (x in 0 until boundsWidth) {
            solid[y * boundsWidth + x] = (row[x] ushr 24) > 18
        }
    }
    val queue = IntArray(boundsWidth * boundsHeight)
    var bestCount = 0
    var bestLeft = 0
    var bestTop = 0
    var bestRight = boundsWidth
    var bestBottom = boundsHeight

    for (start in solid.indices) {
        if (!solid[start] || visited[start]) continue
        var head = 0
        var tail = 0
        queue[tail++] = start
        visited[start] = true
        var count = 0
        var left = boundsWidth
        var top = boundsHeight
        var right = -1
        var bottom = -1
        while (head < tail) {
            val index = queue[head++]
            val x = index % boundsWidth
            val y = index / boundsWidth
            count += 1
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y

            if (x > 0) {
                val next = index - 1
                if (solid[next] && !visited[next]) {
                    visited[next] = true
                    queue[tail++] = next
                }
            }
            if (x < boundsWidth - 1) {
                val next = index + 1
                if (solid[next] && !visited[next]) {
                    visited[next] = true
                    queue[tail++] = next
                }
            }
            if (y > 0) {
                val next = index - boundsWidth
                if (solid[next] && !visited[next]) {
                    visited[next] = true
                    queue[tail++] = next
                }
            }
            if (y < boundsHeight - 1) {
                val next = index + boundsWidth
                if (solid[next] && !visited[next]) {
                    visited[next] = true
                    queue[tail++] = next
                }
            }
        }
        if (count > bestCount) {
            bestCount = count
            bestLeft = left
            bestTop = top
            bestRight = right + 1
            bestBottom = bottom + 1
        }
    }
    val rect = if (bestCount > 0) {
        Rect(
            fullBounds.left + bestLeft,
            fullBounds.top + bestTop,
            fullBounds.left + bestRight,
            fullBounds.top + bestBottom
        )
    } else {
        fullBounds
    }
    bitmapPrimaryContentBoundsCache[key] = Rect(rect)
    return rect
}

private fun DrawScope.drawCroppedBitmap(bitmap: Bitmap, destination: RectF) {
    val srcAspect = bitmap.width.toFloat() / bitmap.height
    val dstAspect = destination.width() / destination.height()
    val src = if (srcAspect > dstAspect) {
        val cropWidth = (bitmap.height * dstAspect).roundToInt()
        val left = (bitmap.width - cropWidth) / 2
        Rect(left, 0, left + cropWidth, bitmap.height)
    } else {
        val cropHeight = (bitmap.width / dstAspect).roundToInt()
        val top = (bitmap.height - cropHeight) / 2
        Rect(0, top, bitmap.width, top + cropHeight)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(bitmap, src, destination, paint)
    }
}
