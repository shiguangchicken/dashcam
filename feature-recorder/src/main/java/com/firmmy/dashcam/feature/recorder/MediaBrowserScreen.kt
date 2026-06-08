package com.firmmy.dashcam.feature.recorder

import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.firmmy.dashcam.core.common.DashCamFormatters
import com.firmmy.dashcam.core.common.MediaType
import com.firmmy.dashcam.core.common.RecordingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MediaBrowserItem(
    val id: Long,
    val type: MediaType,
    val mode: RecordingMode,
    val path: String,
    val thumbnailPath: String? = null,
    val createdAt: Long,
    val durationMs: Long? = null,
    val sizeBytes: Long,
    val locked: Boolean = false,
)

@Composable
fun MediaBrowserScreen(
    items: List<MediaBrowserItem>,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onDeleteClick: (MediaBrowserItem) -> Unit = {},
    onLockClick: (MediaBrowserItem) -> Unit = {},
    onUnlockClick: (MediaBrowserItem) -> Unit = {},
) {
    var selectedType by remember { mutableStateOf(MediaType.VIDEO) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var selectedMode by remember { mutableStateOf<RecordingMode?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<MediaBrowserItem?>(null) }

    val visibleItems = remember(items, selectedType, selectedDate, selectedMode, searchQuery) {
        filterMediaBrowserItems(
            items = items,
            type = selectedType,
            date = selectedDate,
            mode = selectedMode,
            query = searchQuery,
        )
    }
    val dates = remember(items, selectedType) {
        items
            .filter { it.type == selectedType }
            .map { it.createdAt.dateKey() }
            .distinct()
            .sortedDescending()
    }

    selectedItem?.let { item ->
        MediaDetailScreen(
            item = item,
            onBackClick = { selectedItem = null },
            onDeleteClick = {
                onDeleteClick(item)
                selectedItem = null
            },
            onLockClick = {
                onLockClick(item)
                selectedItem = null
            },
            onUnlockClick = {
                onUnlockClick(item)
                selectedItem = null
            },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrowserTopBar(onBackClick = onBackClick)
        SearchAndFilters(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            dates = dates,
            selectedDate = selectedDate,
            selectedMode = selectedMode,
            onDateSelected = { selectedDate = it },
            onModeSelected = { selectedMode = it },
        )
        MediaTabs(
            selectedType = selectedType,
            videoCount = items.count { it.type == MediaType.VIDEO },
            photoCount = items.count { it.type == MediaType.PHOTO },
            onSelected = {
                selectedType = it
                selectedDate = null
            },
        )
        MediaList(
            items = visibleItems,
            type = selectedType,
            onItemClick = { selectedItem = it },
        )
    }
}

@Composable
private fun BrowserTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                modifier = Modifier.testTag("media_back_button"),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                contentPadding = PaddingValues(0.dp),
                onClick = onBackClick,
            ) {
                Text("<", color = SafetyOrange)
            }
            Text("DroidDash", color = SafetyOrange, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange, contentColor = Color(0xFF351000)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            onClick = {},
        ) {
            Text("Bulk Manage", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SearchAndFilters(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    dates: List<String>,
    selectedDate: String?,
    selectedMode: RecordingMode?,
    onDateSelected: (String?) -> Unit,
    onModeSelected: (RecordingMode?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("media_search_field"),
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            label = { Text("Search local files") },
            leadingIcon = { Text("SEARCH", fontSize = 10.sp, color = MutedText) },
        )
        FilterRow(
            dates = dates,
            selectedDate = selectedDate,
            selectedMode = selectedMode,
            onDateSelected = onDateSelected,
            onModeSelected = onModeSelected,
        )
    }
}

@Composable
private fun MediaTabs(
    selectedType: MediaType,
    videoCount: Int,
    photoCount: Int,
    onSelected: (MediaType) -> Unit,
) {
    TabRow(
        selectedTabIndex = if (selectedType == MediaType.VIDEO) 0 else 1,
        containerColor = Surface,
        contentColor = SafetyOrange,
    ) {
        Tab(
            selected = selectedType == MediaType.VIDEO,
            onClick = { onSelected(MediaType.VIDEO) },
            text = { Text("VIDEOS ($videoCount)") },
        )
        Tab(
            selected = selectedType == MediaType.PHOTO,
            onClick = { onSelected(MediaType.PHOTO) },
            text = { Text("PHOTOS ($photoCount)") },
        )
    }
}

@Composable
private fun FilterRow(
    dates: List<String>,
    selectedDate: String?,
    selectedMode: RecordingMode?,
    onDateSelected: (String?) -> Unit,
    onModeSelected: (RecordingMode?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("media_filter_date"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedDate == null,
                onClick = { onDateSelected(null) },
                label = { Text("Date: All") },
            )
            dates.forEach { date ->
                FilterChip(
                    selected = selectedDate == date,
                    onClick = { onDateSelected(date) },
                    label = { Text(date) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("media_filter_mode"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedMode == null,
                onClick = { onModeSelected(null) },
                label = { Text("Mode: All") },
            )
            listOf(RecordingMode.DRIVING, RecordingMode.PARKING, RecordingMode.EVENT, RecordingMode.MANUAL)
                .forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.label) },
                    )
                }
        }
    }
}

@Composable
private fun MediaList(
    items: List<MediaBrowserItem>,
    type: MediaType,
    onItemClick: (MediaBrowserItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(if (type == MediaType.VIDEO) "media_video_list" else "media_photo_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (items.isEmpty()) {
            item {
                EmptyMediaState(type)
            }
        }
        items(items, key = { it.id }) { item ->
            MediaRow(item = item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun EmptyMediaState(type: MediaType) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        Text("No ${type.name.lowercase()} found", color = Foreground, fontWeight = FontWeight.Bold)
        Text("Recorded clips and photos appear here after the recorder indexes local storage.", color = MutedText)
    }
}

@Composable
private fun MediaRow(
    item: MediaBrowserItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("media_item_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaThumbnail(item)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.displayName(),
                        color = Foreground,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.locked) {
                        Spacer(Modifier.width(8.dp))
                        Text("LOCKED", color = SafetyOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "${item.mode.label} / ${DashCamFormatters.formatFileSize(item.sizeBytes)}",
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
                Text(
                    text = listOfNotNull(
                        DashCamFormatters.formatTimestamp(item.createdAt),
                        item.durationMs?.let(DashCamFormatters::formatDuration),
                    ).joinToString(" / "),
                    color = MutedText,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun MediaThumbnail(item: MediaBrowserItem) {
    val bitmap = remember(item.thumbnailPath, item.path, item.type) {
        BitmapFactory.decodeFile(item.thumbnailPath ?: item.path)
    }
    Box(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF172331), Color(0xFF0A0E14)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Text(
                text = if (item.type == MediaType.VIDEO) "VIDEO" else "PHOTO",
                color = MutedText,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun MediaDetailScreen(
    item: MediaBrowserItem,
    onBackClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onLockClick: () -> Unit,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayerTopBar(onBackClick)
        PlayerSurface(item)
        PlayerActions(
            item = item,
            onDeleteClick = onDeleteClick,
            onLockClick = onLockClick,
            onUnlockClick = onUnlockClick,
        )
        PlayerMetadata(item)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PlayerTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                contentPadding = PaddingValues(0.dp),
                onClick = onBackClick,
            ) {
                Text("<", color = SafetyOrange)
            }
            Text("DroidDash", color = SafetyOrange, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniChip("CAM", MutedText)
            MiniChip("GPS", SafetyOrange)
        }
    }
}

@Composable
private fun PlayerSurface(item: MediaBrowserItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)),
    ) {
        if (item.type == MediaType.VIDEO) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("media_video_player"),
                factory = { context ->
                    VideoView(context).apply {
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoPath(item.path)
                        setOnPreparedListener { start() }
                    }
                },
                update = { view ->
                    view.setVideoPath(item.path)
                },
            )
        } else {
            PhotoPreview(path = item.path)
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniChip("${item.mode.label.uppercase()} MODE", SafetyOrange)
            MiniChip(DashCamFormatters.formatTimestamp(item.createdAt), MutedText)
        }
    }
}

@Composable
private fun PlayerActions(
    item: MediaBrowserItem,
    onDeleteClick: () -> Unit,
    onLockClick: () -> Unit,
    onUnlockClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            onClick = {},
        ) {
            Text("Share")
        }
        OutlinedButton(
            modifier = Modifier
                .weight(1f)
                .testTag(if (item.locked) "media_unlock_button" else "media_lock_button"),
            border = BorderStroke(1.dp, if (item.locked) SafetyOrange else Color(0x22FFFFFF)),
            onClick = if (item.locked) onUnlockClick else onLockClick,
        ) {
            Text(if (item.locked) "Unlock" else "Lock")
        }
        Button(
            modifier = Modifier
                .weight(1f)
                .testTag("media_delete_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF93000A)),
            onClick = onDeleteClick,
        ) {
            Text("Delete")
        }
    }
}

@Composable
private fun PlayerMetadata(item: MediaBrowserItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassPanel(modifier = Modifier.weight(1f)) {
            Text("FILE DETAILS", color = MutedText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            MetadataRow("Path", item.path.substringAfterLast('/').ifBlank { item.path })
            MetadataRow("Size", DashCamFormatters.formatFileSize(item.sizeBytes))
            MetadataRow("Type", item.type.name.lowercase())
        }
        GlassPanel(modifier = Modifier.weight(1f)) {
            Text("TELEMETRY STATS", color = MutedText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            MetadataRow("Mode", item.mode.label)
            MetadataRow("Duration", item.durationMs?.let(DashCamFormatters::formatDuration) ?: "Photo")
            MetadataRow("Protection", if (item.locked) "Locked" else "Normal")
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MutedText, fontSize = 12.sp)
        Text(
            value,
            color = Foreground,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PhotoPreview(path: String) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .heightIn(min = 220.dp)
            .testTag("media_photo_view"),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Text("Photo unavailable", color = MutedText)
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun MiniChip(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x99000000))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = color,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x77181C22))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private fun MediaBrowserItem.displayName(): String =
    path.substringAfterLast('/').ifBlank { DashCamFormatters.formatTimestamp(createdAt) }

private fun Long.dateKey(zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zoneId).format(dateFormatter)

fun filterMediaBrowserItems(
    items: List<MediaBrowserItem>,
    type: MediaType,
    date: String? = null,
    mode: RecordingMode? = null,
    query: String = "",
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MediaBrowserItem> =
    items
        .filter { it.type == type }
        .filter { date == null || it.createdAt.dateKey(zoneId) == date }
        .filter { mode == null || it.mode == mode }
        .filter { item ->
            val normalizedQuery = query.trim()
            normalizedQuery.isBlank() ||
                item.path.contains(normalizedQuery, ignoreCase = true) ||
                item.mode.label.contains(normalizedQuery, ignoreCase = true)
        }
        .sortedByDescending { it.createdAt }

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val Background = Color(0xFF10141A)
private val Surface = Color(0xFF181C22)
private val Foreground = Color(0xFFDFE2EB)
private val MutedText = Color(0xFFE2BFB0)
private val SafetyOrange = Color(0xFFFF6B00)
