package me.bmax.apatch.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

private fun loadPaths(): List<String> {
    val result = rootShellForResult("cat '$UMOUNT_FILE' 2>/dev/null || true")
    return result.out.filter { it.isNotBlank() }
}

private fun savePaths(context: Context, paths: List<String>) {
    val tmp = File(context.cacheDir, "umount_tmp")
    tmp.writeText(paths.filter { it.isNotBlank() }.joinToString("\n"))
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
    val paths = remember { mutableStateListOf<String>() }

    remember {
        scope.launch(Dispatchers.IO) {
            val loaded = loadPaths()
            withContext(Dispatchers.Main) {
                paths.clear()
                paths.addAll(loaded)
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    fun save() {
        scope.launch(Dispatchers.IO) { savePaths(context, paths.toList()) }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                inputText = ""
            },
            title = { Text(stringResource(R.string.umount_add_path)) },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text(stringResource(R.string.umount_path_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = inputText.trim()
                    if (p.isNotEmpty() && !paths.contains(p)) {
                        paths.add(p)
                        save()
                    }
                    showAddDialog = false
                    inputText = ""
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    inputText = ""
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
            if (paths.isEmpty()) {
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
                    itemsIndexed(paths) { index, path ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                paths.removeAt(index)
                                save()
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (index < paths.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
