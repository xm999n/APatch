package me.bmax.apatch.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
        if ((s == STAGE_POST_FS || s == STAGE_SERVICE) && p.isNotBlank()) {
            return UmountEntry(s, p)
        }
    }
    // legacy bare path → service
    return UmountEntry(STAGE_SERVICE, line.trim())
}

private fun loadEntries(): List<UmountEntry> {
    val result = rootShellForResult("cat '$UMOUNT_FILE' 2>/dev/null || true")
    return result.out.mapNotNull { parseEntry(it) }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CustomUmountScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries = remember { mutableStateListOf<UmountEntry>() }

    remember {
        scope.launch(Dispatchers.IO) {
            val loaded = loadEntries()
            withContext(Dispatchers.Main) {
                entries.clear()
                entries.addAll(loaded)
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var selectedStage by remember { mutableStateOf(STAGE_SERVICE) }

    fun save() {
        scope.launch(Dispatchers.IO) { saveEntries(context, entries.toList()) }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                inputText = ""
                selectedStage = STAGE_SERVICE
            },
            title = { Text(stringResource(R.string.umount_add_path)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text(stringResource(R.string.umount_path_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.umount_stage),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        FilterChip(
                            selected = selectedStage == STAGE_POST_FS,
                            onClick = { selectedStage = STAGE_POST_FS },
                            label = { Text("post-fs-data") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = selectedStage == STAGE_SERVICE,
                            onClick = { selectedStage = STAGE_SERVICE },
                            label = { Text("service") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = inputText.trim()
                    if (p.isNotEmpty()) {
                        val entry = UmountEntry(selectedStage, p)
                        if (!entries.any { it.stage == entry.stage && it.path == entry.path }) {
                            entries.add(entry)
                            save()
                        }
                    }
                    showAddDialog = false
                    inputText = ""
                    selectedStage = STAGE_SERVICE
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    inputText = ""
                    selectedStage = STAGE_SERVICE
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.umount_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed { navigator.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.umount_add_path))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.umount_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 48.dp)
                )
            } else {
                LazyColumn {
                    itemsIndexed(entries) { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.path,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = entry.stage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                entries.removeAt(index)
                                save()
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (index < entries.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
