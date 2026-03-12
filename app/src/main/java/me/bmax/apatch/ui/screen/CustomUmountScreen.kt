package me.bmax.apatch.ui.screen

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.R
import me.bmax.apatch.util.rootShellForResult
import java.io.File

private const val UMOUNT_FILE = "/data/adb/ap/umount"
private const val STAGE_POST_FS = "post-fs-data"
private const val STAGE_SERVICE = "service"

private data class UmountEntry(val stage: String, val path: String) {
    fun serialize() = "$stage:$path"
}

private fun parseEntry(line: String): UmountEntry? {
    if (line.isBlank()) return null
    val colonIdx = line.indexOf(':')
    if (colonIdx > 0) {
        val s = line.substring(0, colonIdx)
        val p = line.substring(colonIdx + 1)
        if ((s == STAGE_POST_FS || s == STAGE_SERVICE) && p.isNotBlank())
            return UmountEntry(s, p)
    }
    return UmountEntry(STAGE_SERVICE, line.trim())
}

private fun loadEntries(): List<UmountEntry> =
    rootShellForResult("cat '$UMOUNT_FILE' 2>/dev/null || true").out.mapNotNull { parseEntry(it) }

private fun saveEntries(context: Context, entries: List<UmountEntry>) {
    val tmp = File(context.cacheDir, "umount_tmp")
    tmp.writeText(entries.joinToString("\n") { it.serialize() })
    rootShellForResult(
        "mkdir -p /data/adb/ap",
        "cp '${tmp.absolutePath}' '$UMOUNT_FILE'",
        "chmod 644 '$UMOUNT_FILE'"
    )
    tmp.delete()
}

// ── Dialog for add / edit ──────────────────────────────────────────────────
@Composable
private fun EntryDialog(
    title: String,
    initialPath: String = "",
    initialStage: String = STAGE_SERVICE,
    onDismiss: () -> Unit,
    onConfirm: (path: String, stage: String) -> Unit
) {
    var path by remember { mutableStateOf(initialPath) }
    var stage by remember { mutableStateOf(initialStage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.umount_path_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.umount_stage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    FilterChip(
                        selected = stage == STAGE_POST_FS,
                        onClick = { stage = STAGE_POST_FS },
                        label = { Text("post-fs-data") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = stage == STAGE_SERVICE,
                        onClick = { stage = STAGE_SERVICE },
                        label = { Text("service") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = path.trim()
                if (p.isNotEmpty()) onConfirm(p, stage)
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

// ── Main screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Destination<RootGraph>
@Composable
fun CustomUmountScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries = remember { mutableStateListOf<UmountEntry>() }
    val selected = remember { mutableStateListOf<Int>() }   // indices of selected entries

    val inSelectMode by remember { derivedStateOf { selected.isNotEmpty() } }
    val allSelected by remember { derivedStateOf { entries.isNotEmpty() && selected.size == entries.size } }
    val partialSelected by remember { derivedStateOf { selected.isNotEmpty() && selected.size < entries.size } }

    // dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Pair<Int, UmountEntry>?>(null) }  // index + entry

    remember {
        scope.launch(Dispatchers.IO) {
            val loaded = loadEntries()
            withContext(Dispatchers.Main) { entries.addAll(loaded) }
        }
    }

    fun save() = scope.launch(Dispatchers.IO) { saveEntries(context, entries.toList()) }

    fun exitSelectMode() = selected.clear()

    fun toggleSelect(index: Int) {
        if (index in selected) selected.remove(index) else selected.add(index)
    }

    fun toggleSelectAll() {
        if (allSelected) selected.clear()
        else { selected.clear(); selected.addAll(entries.indices) }
    }

    fun deleteSelected() {
        val toRemove = selected.sortedDescending()
        toRemove.forEach { entries.removeAt(it) }
        selected.clear()
        save()
    }

    // Dialogs
    if (showAddDialog) {
        EntryDialog(
            title = stringResource(R.string.umount_add_path),
            onDismiss = { showAddDialog = false }
        ) { path, stage ->
            val e = UmountEntry(stage, path)
            if (entries.none { it == e }) { entries.add(e); save() }
            showAddDialog = false
        }
    }

    editTarget?.let { (idx, orig) ->
        EntryDialog(
            title = stringResource(R.string.umount_edit),
            initialPath = orig.path,
            initialStage = orig.stage,
            onDismiss = { editTarget = null }
        ) { path, stage ->
            val e = UmountEntry(stage, path)
            entries[idx] = e
            save()
            editTarget = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (inSelectMode)
                        Text(stringResource(R.string.umount_selected, selected.size))
                    else
                        Text(stringResource(R.string.umount_title))
                },
                navigationIcon = {
                    if (inSelectMode) {
                        IconButton(onClick = ::exitSelectMode) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    } else {
                        IconButton(onClick = dropUnlessResumed { navigator.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    AnimatedVisibility(visible = inSelectMode, enter = fadeIn(), exit = fadeOut()) {
                        Row {
                            // Select all / deselect all
                            IconButton(onClick = ::toggleSelectAll) {
                                Icon(
                                    imageVector = when {
                                        allSelected -> Icons.Filled.CheckBox
                                        partialSelected -> Icons.Filled.IndeterminateCheckBox
                                        else -> Icons.Filled.SelectAll
                                    },
                                    contentDescription = null
                                )
                            }
                            // Delete selected
                            IconButton(onClick = ::deleteSelected) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = !inSelectMode, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.umount_add_path))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.umount_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn {
                    itemsIndexed(entries, key = { i, e -> "$i-${e.serialize()}" }) { index, entry ->
                        val isSelected = index in selected
                        Surface(
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (inSelectMode) toggleSelect(index)
                                    },
                                    onLongClick = { toggleSelect(index) }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Checkbox (visible in select mode)
                                AnimatedVisibility(visible = inSelectMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { toggleSelect(index) },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.path,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = when (entry.stage) {
                                            STAGE_POST_FS -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.secondaryContainer
                                        }
                                    ) {
                                        Text(
                                            text = entry.stage,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = when (entry.stage) {
                                                STAGE_POST_FS -> MaterialTheme.colorScheme.onTertiaryContainer
                                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                                            }
                                        )
                                    }
                                }

                                // Action buttons (visible when NOT in select mode)
                                AnimatedVisibility(visible = !inSelectMode) {
                                    Row(horizontalArrangement = Arrangement.End) {
                                        IconButton(onClick = { editTarget = index to entry }) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(onClick = {
                                            entries.removeAt(index)
                                            save()
                                        }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (index < entries.lastIndex)
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}
