package com.firmmy.dashcam.feature.recorder

import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    var selectedItem by remember { mutableStateOf<MediaBrowserItem?>(null) }

    val visibleItems = remember(items, selectedType, selectedDate, selectedMode) {
        filterMediaBrowserItems(
            items = items,
            type = selectedType,
            date = selectedDate,
            mode = selectedMode,
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Media", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(
                modifier = Modifier.testTag("media_back_button"),
                onClick = onBackClick,
            ) {
                Text("Back")
            }
        }

        TabRow(selectedTabIndex = if (selectedType == MediaType.VIDEO) 0 else 1) {
            Tab(
                selected = selectedType == MediaType.VIDEO,
                onClick = {
                    selectedType = MediaType.VIDEO
                    selectedDate = null
                },
                text = { Text("Videos") },
            )
            Tab(
                selected = selectedType == MediaType.PHOTO,
                onClick = {
                    selectedType = MediaType.PHOTO
                    selectedDate = null
                },
                text = { Text("Photos") },
            )
        }

        FilterRow(
            dates = dates,
            selectedDate = selectedDate,
            selectedMode = selectedMode,
            onDateSelected = { selectedDate = it },
            onModeSelected = { selectedMode = it },
        )

        MediaList(
            items = visibleItems,
            type = selectedType,
            onItemClick = { selectedItem = it },
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
                label = { Text("All dates") },
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
                label = { Text("All modes") },
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            MediaRow(item = item, onClick = { onItemClick(item) })
        }
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = DashCamFormatters.formatTimestamp(item.createdAt),
                style = MaterialTheme.typography.titleMedium,
            )
            Text("${item.mode.label} · ${DashCamFormatters.formatFileSize(item.sizeBytes)}")
            Text(
                text = listOfNotNull(
                    item.durationMs?.let(DashCamFormatters::formatDuration),
                    if (item.locked) "Locked" else null,
                ).joinToString(" · ").ifBlank { item.type.name.lowercase() },
                style = MaterialTheme.typography.bodySmall,
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBackClick) {
                Text("Back")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.testTag(if (item.locked) "media_unlock_button" else "media_lock_button"),
                    onClick = if (item.locked) onUnlockClick else onLockClick,
                ) {
                    Text(if (item.locked) "Unlock" else "Lock")
                }
                Button(
                    modifier = Modifier.testTag("media_delete_button"),
                    onClick = onDeleteClick,
                ) {
                    Text("Delete")
                }
            }
        }

        Text(DashCamFormatters.formatTimestamp(item.createdAt), style = MaterialTheme.typography.titleMedium)

        if (item.type == MediaType.VIDEO) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
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
    }
}

@Composable
private fun PhotoPreview(path: String) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .testTag("media_photo_view"),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Text("Photo unavailable")
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun Long.dateKey(zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zoneId).format(dateFormatter)

fun filterMediaBrowserItems(
    items: List<MediaBrowserItem>,
    type: MediaType,
    date: String? = null,
    mode: RecordingMode? = null,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MediaBrowserItem> =
    items
        .filter { it.type == type }
        .filter { date == null || it.createdAt.dateKey(zoneId) == date }
        .filter { mode == null || it.mode == mode }
        .sortedByDescending { it.createdAt }

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
