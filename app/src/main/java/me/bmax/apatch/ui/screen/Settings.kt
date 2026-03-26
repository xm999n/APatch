package me.bmax.apatch.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RemoveFromQueue
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.CustomUmountScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.SwitchItem
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.theme.refreshTheme
import me.bmax.apatch.util.APatchKeyHelper
import me.bmax.apatch.util.getBugreportFile
import me.bmax.apatch.util.isForceUsingOverlayFS
import me.bmax.apatch.util.isGlobalNamespaceEnabled
import me.bmax.apatch.util.isLiteModeEnabled
import me.bmax.apatch.util.outputStream
import me.bmax.apatch.util.overlayFsAvailable
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.setForceUsingOverlayFS
import me.bmax.apatch.util.setGlobalNamespaceEnabled
import me.bmax.apatch.util.setLiteMode
import me.bmax.apatch.util.ui.APDialogBlurBehindUtils
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.bmax.apatch.util.ui.NavigationBarsSpacer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
@Destination<RootGraph>
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingScreen(navigator: DestinationsNavigator) {
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val kPatchReady = state != APApplication.State.UNKNOWN_STATE
    val aPatchReady =
        (state == APApplication.State.ANDROIDPATCH_INSTALLING || state == APApplication.State.ANDROIDPATCH_INSTALLED || state == APApplication.State.ANDROIDPATCH_NEED_UPDATE)

    var isGlobalNamespaceEnabled by rememberSaveable { mutableStateOf(false) }
    var isLiteModeEnabled by rememberSaveable { mutableStateOf(false) }
    var forceUsingOverlayFS by rememberSaveable { mutableStateOf(false) }
    var bSkipStoreSuperKey by rememberSaveable { mutableStateOf(APatchKeyHelper.shouldSkipStoreSuperKey()) }
    val isOverlayFSAvailable by rememberSaveable { mutableStateOf(overlayFsAvailable()) }

    if (kPatchReady && aPatchReady) {
        isGlobalNamespaceEnabled = isGlobalNamespaceEnabled()
        isLiteModeEnabled = isLiteModeEnabled()
        forceUsingOverlayFS = isForceUsingOverlayFS()
    }

    val snackBarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()
    val clearKeyDialog = rememberConfirmDialog(
        onConfirm = {
            APatchKeyHelper.clearConfigKey()
            APApplication.superKey = ""
        }
    )

    val showLanguageDialog = rememberSaveable { mutableStateOf(false) }
    LanguageDialog(showLanguageDialog)

    val showResetSuPathDialog = remember { mutableStateOf(false) }
    if (showResetSuPathDialog.value) {
        ResetSUPathDialog(showResetSuPathDialog)
    }

    val showThemeChooseDialog = remember { mutableStateOf(false) }
    if (showThemeChooseDialog.value) {
        ThemeChooseDialog(showThemeChooseDialog)
    }

    var showLogBottomSheet by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = APApplication.sharedPreferences
    val logSavedMessage = stringResource(R.string.log_saved)

    val exportBugreportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                loadingDialog.show()
                uri.outputStream().use { output ->
                    getBugreportFile(context).inputStream().use {
                        it.copyTo(output)
                    }
                }
                loadingDialog.hide()
                snackBarHost.showSnackbar(message = logSavedMessage)
            }
        }
    }

    var coreExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var securityExpanded by rememberSaveable { mutableStateOf(false) }

    var checkUpdate by rememberSaveable { mutableStateOf(prefs.getBoolean("check_update", true)) }
    var nightFollowSystem by rememberSaveable { mutableStateOf(prefs.getBoolean("night_mode_follow_sys", true)) }
    var nightThemeEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("night_mode_enabled", false)) }
    val isDynamicColorSupport = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var useSystemDynamicColor by rememberSaveable {
        mutableStateOf(if (isDynamicColorSupport) prefs.getBoolean("use_system_color_theme", false) else false)
    }
    var amoledMode by rememberSaveable { mutableStateOf(prefs.getBoolean("amoled_mode", false)) }
    var enableWebDebugging by rememberSaveable { mutableStateOf(prefs.getBoolean("enable_web_debugging", false)) }
    var biometricLockEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean(APApplication.PREF_SECURITY_BIOMETRIC_LOCK, false))
    }
    var screenshotBlockEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean(APApplication.PREF_SECURITY_BLOCK_SCREENSHOT, false))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
            )
        },
        snackbarHost = { SnackbarHost(snackBarHost) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExpandableSettingsSection(
                title = stringResource(id = R.string.settings_advanced),
                icon = Icons.Filled.Key,
                expanded = coreExpanded,
                onExpandedChange = { coreExpanded = it }
            ) {
                if (kPatchReady) {
                    val clearKeyDialogTitle = stringResource(id = R.string.clear_super_key)
                    val clearKeyDialogContent = stringResource(id = R.string.settings_clear_super_key_dialog)
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Filled.Key, stringResource(id = R.string.super_key))
                        },
                        headlineContent = { Text(stringResource(id = R.string.clear_super_key)) },
                        modifier = Modifier.clickable {
                            clearKeyDialog.showConfirm(
                                title = clearKeyDialogTitle,
                                content = clearKeyDialogContent,
                                markdown = false,
                            )
                        })
                }

                SwitchItem(
                    icon = Icons.Filled.Key,
                    title = stringResource(id = R.string.settings_donot_store_superkey),
                    summary = stringResource(id = R.string.settings_donot_store_superkey_summary),
                    checked = bSkipStoreSuperKey,
                    onCheckedChange = {
                        bSkipStoreSuperKey = it
                        APatchKeyHelper.setShouldSkipStoreSuperKey(bSkipStoreSuperKey)
                    }
                )

                if (kPatchReady && aPatchReady) {
                    SwitchItem(
                        icon = Icons.Filled.Engineering,
                        title = stringResource(id = R.string.settings_global_namespace_mode),
                        summary = stringResource(id = R.string.settings_global_namespace_mode_summary),
                        checked = isGlobalNamespaceEnabled,
                        onCheckedChange = {
                            setGlobalNamespaceEnabled(if (isGlobalNamespaceEnabled) "0" else "1")
                            isGlobalNamespaceEnabled = it
                        }
                    )

                    SwitchItem(
                        icon = Icons.Filled.RemoveFromQueue,
                        title = stringResource(id = R.string.settings_lite_mode),
                        summary = stringResource(id = R.string.settings_lite_mode_mode_summary),
                        checked = isLiteModeEnabled,
                        onCheckedChange = {
                            setLiteMode(it)
                            isLiteModeEnabled = it
                        }
                    )
                }

                if (kPatchReady && aPatchReady && isOverlayFSAvailable) {
                    SwitchItem(
                        icon = Icons.Filled.FilePresent,
                        title = stringResource(id = R.string.settings_force_overlayfs_mode),
                        summary = stringResource(id = R.string.settings_force_overlayfs_mode_summary),
                        checked = forceUsingOverlayFS,
                        onCheckedChange = {
                            setForceUsingOverlayFS(it)
                            forceUsingOverlayFS = it
                        }
                    )
                }
                if (aPatchReady) {
                    SwitchItem(
                        icon = Icons.Filled.DeveloperMode,
                        title = stringResource(id = R.string.enable_web_debugging),
                        summary = stringResource(id = R.string.enable_web_debugging_summary),
                        checked = enableWebDebugging
                    ) {
                        APApplication.sharedPreferences.edit {
                            putBoolean("enable_web_debugging", it)
                        }
                        enableWebDebugging = it
                    }
                }

                ListItem(
                    leadingContent = { Icon(Icons.Filled.RemoveFromQueue, null) },
                    headlineContent = { Text(stringResource(R.string.umount_title)) },
                    supportingContent = { Text(stringResource(R.string.umount_summary)) },
                    modifier = Modifier.clickable {
                        navigator.navigate(CustomUmountScreenDestination)
                    }
                )

                if (kPatchReady) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Filled.Commit,
                                stringResource(id = R.string.setting_reset_su_path)
                            )
                        },
                        headlineContent = { Text(stringResource(id = R.string.setting_reset_su_path)) },
                        modifier = Modifier.clickable {
                            showResetSuPathDialog.value = true
                        }
                    )
                }
            }

            ExpandableSettingsSection(
                title = stringResource(id = R.string.settings_custom_color_theme),
                icon = Icons.Filled.ColorLens,
                expanded = appearanceExpanded,
                onExpandedChange = { appearanceExpanded = it }
            ) {
                SwitchItem(
                    icon = Icons.Filled.Update,
                    title = stringResource(id = R.string.settings_check_update),
                    summary = stringResource(id = R.string.settings_check_update_summary),
                    checked = checkUpdate
                ) {
                    prefs.edit { putBoolean("check_update", it) }
                    checkUpdate = it
                }

                SwitchItem(
                    icon = Icons.Filled.InvertColors,
                    title = stringResource(id = R.string.settings_night_mode_follow_sys),
                    summary = stringResource(id = R.string.settings_night_mode_follow_sys_summary),
                    checked = nightFollowSystem
                ) {
                    prefs.edit { putBoolean("night_mode_follow_sys", it) }
                    nightFollowSystem = it
                    refreshTheme.value = true
                }

                if (!nightFollowSystem) {
                    SwitchItem(
                        icon = Icons.Filled.DarkMode,
                        title = stringResource(id = R.string.settings_night_theme_enabled),
                        checked = nightThemeEnabled
                    ) {
                        prefs.edit { putBoolean("night_mode_enabled", it) }
                        nightThemeEnabled = it
                        refreshTheme.value = true
                    }
                }

                if (isDynamicColorSupport) {
                    SwitchItem(
                        icon = Icons.Filled.ColorLens,
                        title = stringResource(id = R.string.settings_use_system_color_theme),
                        summary = stringResource(id = R.string.settings_use_system_color_theme_summary),
                        checked = useSystemDynamicColor
                    ) {
                        prefs.edit { putBoolean("use_system_color_theme", it) }
                        useSystemDynamicColor = it
                        refreshTheme.value = true
                    }
                }

                if (!isDynamicColorSupport || !useSystemDynamicColor) {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_custom_color_theme))
                        },
                        modifier = Modifier.clickable {
                            showThemeChooseDialog.value = true
                        },
                        supportingContent = {
                            val colorMode = prefs.getString("custom_color", "blue")
                            Text(
                                text = stringResource(colorNameToString(colorMode.toString())),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.FormatColorFill, null) }
                    )
                }

                SwitchItem(
                    icon = Icons.Filled.InvertColors,
                    title = stringResource(id = R.string.settings_amoled_mode),
                    summary = stringResource(id = R.string.settings_amoled_mode_summary),
                    checked = amoledMode
                ) {
                    prefs.edit { putBoolean("amoled_mode", it) }
                    amoledMode = it
                    refreshTheme.value = true
                }
            }
            ExpandableSettingsSection(
                title = stringResource(id = R.string.settings_security),
                icon = Icons.Filled.Security,
                expanded = securityExpanded,
                onExpandedChange = { securityExpanded = it }
            ) {
                SwitchItem(
                    icon = Icons.Filled.Fingerprint,
                    title = stringResource(id = R.string.settings_security_biometric_lock),
                    summary = stringResource(id = R.string.settings_security_biometric_lock_summary),
                    checked = biometricLockEnabled
                ) { enabled ->
                    val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    } else {
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                    }
                    val canAuthenticate = BiometricManager.from(context).canAuthenticate(authenticators)
                    if (enabled && canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
                        Toast.makeText(context, R.string.settings_security_biometric_unavailable, Toast.LENGTH_SHORT).show()
                        return@SwitchItem
                    }
                    prefs.edit { putBoolean(APApplication.PREF_SECURITY_BIOMETRIC_LOCK, enabled) }
                    biometricLockEnabled = enabled
                }

                SwitchItem(
                    icon = Icons.Filled.VisibilityOff,
                    title = stringResource(id = R.string.settings_security_block_screenshot),
                    summary = stringResource(id = R.string.settings_security_block_screenshot_summary),
                    checked = screenshotBlockEnabled
                ) { enabled ->
                    prefs.edit { putBoolean(APApplication.PREF_SECURITY_BLOCK_SCREENSHOT, enabled) }
                    screenshotBlockEnabled = enabled
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headlineContent = { Text(text = stringResource(id = R.string.settings_app_language)) },
                    modifier = Modifier.clickable {
                        showLanguageDialog.value = true
                    },
                    supportingContent = {
                        Text(
                            text = AppCompatDelegate.getApplicationLocales()[0]?.displayLanguage?.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                            } ?: stringResource(id = R.string.system_default),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Translate, null) }
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Filled.BugReport, stringResource(id = R.string.send_log))
                    },
                    headlineContent = { Text(stringResource(id = R.string.send_log)) },
                    modifier = Modifier.clickable {
                        showLogBottomSheet = true
                    }
                )
            }

            NavigationBarsSpacer()
        }

        if (showLogBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLogBottomSheet = false },
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
                content = {
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.CenterHorizontally)

                    ) {
                        Box {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .clickable {
                                        scope.launch {
                                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
                                            val current = LocalDateTime.now().format(formatter)
                                            exportBugreportLauncher.launch("APatch_bugreport_${current}.tar.gz")
                                            showLogBottomSheet = false
                                        }
                                    }
                            ) {
                                Icon(
                                    Icons.Filled.Save,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                                Text(
                                    text = stringResource(id = R.string.save_log),
                                    modifier = Modifier.padding(top = 16.dp),
                                    textAlign = TextAlign.Center.also {
                                        LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    }

                                )
                            }

                        }
                        Box {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .clickable {
                                        scope.launch {
                                            val bugreport = loadingDialog.withLoading {
                                                withContext(Dispatchers.IO) {
                                                    getBugreportFile(context)
                                                }
                                            }

                                            val uri: Uri = FileProvider.getUriForFile(
                                                context,
                                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                                bugreport
                                            )

                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                setDataAndType(uri, "application/gzip")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }

                                            context.startActivity(
                                                Intent.createChooser(
                                                    shareIntent,
                                                    context.getString(R.string.send_log)
                                                )
                                            )
                                            showLogBottomSheet = false
                                        }
                                    }) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                                Text(
                                    text = stringResource(id = R.string.send_log),
                                    modifier = Modifier.padding(top = 16.dp),
                                    textAlign = TextAlign.Center.also {
                                        LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    }

                                )
                            }

                        }
                    }
                    NavigationBarsSpacer()
                })
        }
    }
}

@Composable
private fun ExpandableSettingsSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            ListItem(
                leadingContent = { Icon(icon, null) },
                headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
                trailingContent = {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onExpandedChange(!expanded) },
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeChooseDialog(showDialog: MutableState<Boolean>) {
    val prefs = APApplication.sharedPreferences
    var selectedColorName by rememberSaveable {
        mutableStateOf(prefs.getString("custom_color", "blue") ?: "blue")
    }

    BasicAlertDialog(
        onDismissRequest = { showDialog.value = false }, properties = DialogProperties(
            decorFitsSystemWindows = true,
            usePlatformDefaultWidth = false,
        )
    ) {
        Surface(
            modifier = Modifier
                .width(332.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(30.dp),
            tonalElevation = AlertDialogDefaults.TonalElevation,
            color = AlertDialogDefaults.containerColor,
        ) {
            LazyColumn {
                items(colorsList()) { color ->
                    val selected = color.name == selectedColorName
                    ListItem(
                        headlineContent = { Text(text = stringResource(color.nameId)) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(color.previewColor, CircleShape)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                            )
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            } else {
                                Color.Transparent
                            }
                        ),
                        modifier = Modifier.clickable {
                            selectedColorName = color.name
                            showDialog.value = false
                            prefs.edit { putString("custom_color", color.name) }
                            refreshTheme.value = true
                        })
                }

            }

            val dialogWindowProvider = LocalView.current.parent as DialogWindowProvider
            APDialogBlurBehindUtils.setupWindowBlurListener(dialogWindowProvider.window)
        }
    }

}

private data class APColor(
    val name: String,
    @param:StringRes val nameId: Int,
    val previewColor: Color
)

private fun colorsList(): List<APColor> {
    return listOf(
        APColor("amber", R.string.amber_theme, Color(0xFFE3A018)),
        APColor("blue_grey", R.string.blue_grey_theme, Color(0xFF546E7A)),
        APColor("blue", R.string.blue_theme, Color(0xFF1565C0)),
        APColor("brown", R.string.brown_theme, Color(0xFF6D4C41)),
        APColor("cyan", R.string.cyan_theme, Color(0xFF00838F)),
        APColor("deep_orange", R.string.deep_orange_theme, Color(0xFFE64A19)),
        APColor("deep_purple", R.string.deep_purple_theme, Color(0xFF5E35B1)),
        APColor("green", R.string.green_theme, Color(0xFF2E7D32)),
        APColor("indigo", R.string.indigo_theme, Color(0xFF3949AB)),
        APColor("light_blue", R.string.light_blue_theme, Color(0xFF039BE5)),
        APColor("light_green", R.string.light_green_theme, Color(0xFF7CB342)),
        APColor("lime", R.string.lime_theme, Color(0xFF9E9D24)),
        APColor("orange", R.string.orange_theme, Color(0xFFFB8C00)),
        APColor("pink", R.string.pink_theme, Color(0xFFD81B60)),
        APColor("purple", R.string.purple_theme, Color(0xFF8E24AA)),
        APColor("red", R.string.red_theme, Color(0xFFC62828)),
        APColor("sakura", R.string.sakura_theme, Color(0xFFD06B95)),
        APColor("teal", R.string.teal_theme, Color(0xFF00796B)),
        APColor("yellow", R.string.yellow_theme, Color(0xFFFBC02D)),
    )
}
@Composable
private fun colorNameToString(colorName: String): Int {
    return colorsList().find { it.name == colorName }?.nameId ?: R.string.blue_theme
}

val suPathChecked: (path: String) -> Boolean = {
    it.startsWith("/") && it.trim().length > 1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetSUPathDialog(showDialog: MutableState<Boolean>) {
    val context = LocalContext.current
    var suPath by remember { mutableStateOf(Natives.suPath()) }
    BasicAlertDialog(
        onDismissRequest = { showDialog.value = false }, properties = DialogProperties(
            decorFitsSystemWindows = true,
            usePlatformDefaultWidth = false,
        )
    ) {
        Surface(
            modifier = Modifier
                .width(310.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(30.dp),
            tonalElevation = AlertDialogDefaults.TonalElevation,
            color = AlertDialogDefaults.containerColor,
        ) {
            Column(modifier = Modifier.padding(PaddingValues(all = 24.dp))) {
                Box(
                    Modifier
                        .padding(PaddingValues(bottom = 16.dp))
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.setting_reset_su_path),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Box(
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .padding(PaddingValues(bottom = 12.dp))
                        .align(Alignment.Start)
                ) {
                    OutlinedTextField(
                        value = suPath,
                        onValueChange = {
                            suPath = it
                        },
                        label = { Text(stringResource(id = R.string.setting_reset_su_new_path)) },
                        visualTransformation = VisualTransformation.None,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showDialog.value = false }) {

                        Text(stringResource(id = android.R.string.cancel))
                    }

                    Button(enabled = suPathChecked(suPath), onClick = {
                        showDialog.value = false
                        val success = Natives.resetSuPath(suPath)
                        Toast.makeText(
                            context,
                            if (success) R.string.success else R.string.failure,
                            Toast.LENGTH_SHORT
                        ).show()
                        rootShellForResult("echo $suPath > ${APApplication.SU_PATH_FILE}")
                    }) {
                        Text(stringResource(id = android.R.string.ok))
                    }
                }
            }
            val dialogWindowProvider = LocalView.current.parent as DialogWindowProvider
            APDialogBlurBehindUtils.setupWindowBlurListener(dialogWindowProvider.window)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDialog(showLanguageDialog: MutableState<Boolean>) {

    val languages = stringArrayResource(id = R.array.languages)
    val languagesValues = stringArrayResource(id = R.array.languages_values)

    if (showLanguageDialog.value) {
        BasicAlertDialog(
            onDismissRequest = { showLanguageDialog.value = false }
        ) {
            Surface(
                modifier = Modifier
                    .width(150.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = AlertDialogDefaults.TonalElevation,
                color = AlertDialogDefaults.containerColor,
            ) {
                LazyColumn {
                    itemsIndexed(languages) { index, item ->
                        ListItem(
                            headlineContent = { Text(item) },
                            modifier = Modifier.clickable {
                                showLanguageDialog.value = false
                                if (index == 0) {
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.getEmptyLocaleList()
                                    )
                                } else {
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.forLanguageTags(
                                            languagesValues[index]
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
            val dialogWindowProvider = LocalView.current.parent as DialogWindowProvider
            APDialogBlurBehindUtils.setupWindowBlurListener(dialogWindowProvider.window)
        }
    }
}
